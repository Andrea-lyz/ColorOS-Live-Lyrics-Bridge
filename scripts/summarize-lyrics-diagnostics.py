#!/usr/bin/env python3
"""Offline, whitelist-only CLL diagnostic summary. Never execute, extract or replay input."""
from __future__ import annotations
import argparse
import collections
import hashlib
import json
import pathlib
import re
import shlex
import stat
import struct
import sys
import zipfile

MAX_FILE = 16 * 1024 * 1024
MAX_TOTAL = 64 * 1024 * 1024
MAX_INPUT = 256 * 1024 * 1024
MAX_LINES = 200_000
MAX_LINE = 16_384
MAX_MEMBERS = 512
FEATURES = {"CUSTOM_DRAW", "COVER_GLOW", "CHAR_LIFT", "LINE_PROGRESS", "TRANSLATION_PROGRESS", "TRANSLATION_BUTTON", "DYNAMIC_ARTWORK"}
TRUTH = {"YES", "NO", "UNKNOWN"}
PATHS = {"UNKNOWN", "NATIVE", "CUSTOM", "MANUAL_COLOR", "COVER_COLOR", "CHAR_LIFT", "DYNAMIC_ARTWORK"}
OUTCOMES = {"NOT_OBSERVED", "DISABLED", "WAITING", "FALLBACK", "REJECTED", "PREPARED", "DISPATCHED", "DRAW_COMMANDS", "PRESENTATION_ASSIGNED"}
REASONS = {"NONE", "CONFIG_DISABLED", "RAW_LYRIC_MISSING", "LINE_PROGRESS_DISABLED", "AOD_LOW_FRAME_RATE", "HOST_NOT_FOUND", "SNAPSHOT_PENDING", "PALETTE_UNAVAILABLE", "DATASET_UNSUPPORTED", "FRAME_REJECTED", "NATIVE_FALLBACK", "TARGET_NOT_OBSERVED", "RUNTIME_EXCEPTION", "FEATURE_DEGRADED"}
SCOPES = {"NOT_OBSERVED", "CONFIG", "LYRIC_ROW", "RENDER_DISPATCH", "PALETTE_SAMPLE", "CHAR_LIFT_GATE", "GLYPH_COMMANDS", "VIEW_VISIBILITY", "ARTWORK_CARD_BINDING", "ARTWORK_IMMERSIVE_BINDING", "ARTWORK_LIFECYCLE"}
MODULES = {"app", "player-salt", "player-cone", "kuwo-music", "player-lx", "player-poweramp", "player-metrolist", "player-kugou", "player-qq", "player-netease", "player-apple", "player-spotify", "player-qishui", "universal-provider", "player-readify", "artwork-provider-am"}
EVENTS = {"MODULE_LOADED", "PACKAGE_READY", "HOOK_INSTALLED", "HOOK_FAILED", "HOOK_INSTALL_SUMMARY", "SYSTEMUI_BOOTSTRAP", "FEATURE_OBSERVATION", "USER_MARK", "RENDER_STATE_CHANGED", "ARTWORK_SESSION_BOUND", "ARTWORK_FALLBACK", "ARTWORK_COVER_COLOR", "ARTWORK_FLOW_REJECTED", "ARTWORK_FLOW_BOUND", "ARTWORK_HOST_MODEL", "TEXTVIEW_DRAW", "FRAME_REJECTED", "BUILD_ID", "PAYLOAD_PUBLISHED", "PAYLOAD_REJECTED", "METADATA_PUBLISHED", "ERROR", "WARN"}
COMPONENTS = {"bridge", "artwork/am"} | {"provider/" + name for name in ("salt", "cone", "kuwo", "lx", "poweramp", "metrolist", "kugou", "qq", "netease", "apple", "spotify", "qishui", "universal", "readify")}
UUID = re.compile(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\Z")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
BUILD = re.compile(r"(ColorOS-Live-Lyrics-(?:Bridge|Providers)):([^:]+):(debug|release|diagnostic):([0-9a-f]{40}|[0-9a-f]{64}|unknown):(clean|dirty|unknown):([0-9a-f]{64}):([A-Za-z0-9._-]{1,64})\Z")

class BudgetError(ValueError):
    pass

def integer(value, default=-1):
    return value if type(value) is int and -1 <= value <= 2**63 - 1 else default

def known(value, choices, default="UNKNOWN"):
    return value if isinstance(value, str) and value in choices else default

def build_id(value):
    if not isinstance(value, str) or len(value) > 1024:
        return "unknown"
    match = BUILD.fullmatch(value)
    return value if match and match[2] in MODULES else "unknown"

def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate_json_key")
        result[key] = value
    return result

class Summary:
    def __init__(self):
        self.counts = collections.Counter()
        self.events = collections.Counter()
        self.reasons = collections.Counter()
        self.builds = set()
        self.observations = []
        self.processes = []
        self.runtime_info = []
        self.effective_configs = []
        self.epochs = set()
        self.coverage = set()

    def feature(self, value):
        if not isinstance(value, dict) or value.get("feature") not in FEATURES:
            self.counts["unrecognized_feature"] += 1
            return None
        clean = {"feature": value["feature"]}
        for field in ("requested", "supported", "eligible"):
            clean[field] = known(value.get(field), TRUTH)
        clean["selectedPath"] = known(value.get("selectedPath"), PATHS)
        clean["lastOutcome"] = known(value.get("lastOutcome"), OUTCOMES, "NOT_OBSERVED")
        clean["reason"] = known(value.get("reason"), REASONS, "TARGET_NOT_OBSERVED")
        clean["evidenceScope"] = known(value.get("evidenceScope"), SCOPES, "NOT_OBSERVED")
        clean["observedAt"] = integer(value.get("observedAt", value.get("elapsed")))
        self.reasons[clean["reason"]] += 1
        return clean

    def document(self, value):
        if not isinstance(value, dict):
            self.counts["unrecognized_document"] += 1
            return
        schema = value.get("schema")
        if schema == "cll-bridge-diagnostics-v1":
            self.counts["bridge_documents"] += 1
            runtime = value.get("runtime") if isinstance(value.get("runtime"), dict) else {}
            identity = build_id(runtime.get("cll_runtime_build_id"))
            if identity != "unknown":
                self.builds.add(identity)
            epoch = runtime.get("cll_runtime_epoch")
            if isinstance(epoch, str) and UUID.fullmatch(epoch):
                self.epochs.add(epoch)
            else:
                self.coverage.add("PROCESS_EPOCH_UNKNOWN")
            framework = runtime.get("cll_runtime_framework")
            framework = framework if isinstance(framework, str) and re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]{0,48} / [0-9][0-9A-Za-z.() -]{0,80}", framework) else "unknown"
            process = runtime.get("cll_runtime_process")
            process_kind = "systemui" if process == "com.android.systemui" else "system_server" if process == "system_server" else "unknown"
            self.runtime_info.append({"buildId": identity, "framework": framework, "api": integer(runtime.get("cll_runtime_api")),
                "processKind": process_kind, "appliedUiRevision": integer(runtime.get("cll_ui_config_revision"))})
            raw_config = value.get("effectiveConfig") if isinstance(value.get("effectiveConfig"), dict) else {}
            config = {key: raw_config.get(key) if type(raw_config.get(key)) is bool else None
                for key in ("lineProgress", "translationProgress", "charLift", "coverGlow", "debugEnabled")}
            config["debugRevision"] = integer(raw_config.get("debugRevision"))
            self.effective_configs.append(config)
            self.coverage.add(known(value.get("coverage"), {"NO_SESSION", "SAMPLED_PARTIAL", "BOUNDED_OBSERVATIONS", "SERIALIZATION_FAILED"}, "UNKNOWN_DOCUMENT_COVERAGE"))
            for flag in ("truncatedEvents", "suppressedTransitions"):
                number = max(0, integer(value.get(flag), 0))
                self.counts[flag] += number
                if number:
                    self.coverage.add("SOURCE_EVENT_OMISSIONS")
            capabilities = value.get("capabilities", [])
            if isinstance(capabilities, list):
                for row in capabilities[:7]:
                    clean = self.feature(row)
                    if clean:
                        self.observations.append(clean)
                if len(capabilities) > 7:
                    self.coverage.add("CAPABILITY_BUDGET")
            recorded = value.get("events", [])
            if isinstance(recorded, list):
                if len(recorded) > 128:
                    self.coverage.add("EVENT_BUDGET")
                for row in recorded[:128]:
                    if not isinstance(row, dict):
                        self.counts["malformed_events"] += 1
                        continue
                    kind = known(row.get("event"), {"FEATURE_OBSERVATION", "USER_MARK"}, "UNRECOGNIZED_EVENT")
                    self.events[kind] += 1
                    if kind == "FEATURE_OBSERVATION":
                        self.feature(row)
            # appEnvironment, arbitrary messages and paths are intentionally not copied.
        elif schema == "cll-provider-diagnostics-v1":
            self.counts["provider_documents"] += 1
            installed = build_id(value.get("installedBuildId"))
            if installed != "unknown":
                self.builds.add(installed)
            rows = value.get("processes", [])
            if not isinstance(rows, list):
                rows = []
            if len(rows) > 16:
                self.coverage.add("PROCESS_BUDGET")
            for row in rows[:16]:
                if not isinstance(row, dict):
                    self.counts["malformed_processes"] += 1
                    continue
                observed = build_id(row.get("observedBuildId"))
                if observed != "unknown":
                    self.builds.add(observed)
                epoch = row.get("processEpoch")
                if isinstance(epoch, str) and UUID.fullmatch(epoch):
                    self.epochs.add(epoch)
                else:
                    self.coverage.add("PROCESS_EPOCH_UNKNOWN")
                comparison = "unknown" if "unknown" in (installed, observed) else "MATCH" if installed == observed else "MISMATCH"
                self.processes.append({"installedBuildId": installed, "observedBuildId": observed,
                    "comparison": comparison, "configRevision": integer(row.get("configRevision")),
                    "configObservation": known(row.get("configObservation"), {"CONSUMED", "CONSUMED_NO_REVISION", "BOOTSTRAP_NO_REVISION", "UNAVAILABLE", "NOT_OBSERVED", "NOT_APPLICABLE"}),
                    "installerStage": known(row.get("installerStage"), {"INSTALLER_RETURNED", "INSTALLER_FAILED", "NOT_OBSERVED"})})
            if not rows:
                self.coverage.add("TARGET_NOT_OBSERVED")
            self.counts["rejected_replies"] += max(0, integer(value.get("rejectedReplies"), 0))
            self.counts["omitted_replies"] += max(0, integer(value.get("omittedReplies"), 0))
            self.coverage.add("SELF_REPORT_NOT_FRAMEWORK_ATTESTATION")
        else:
            self.counts["unrecognized_document"] += 1
            self.coverage.add("UNRECOGNIZED_SCHEMA")

    def log(self, text):
        self.coverage.add("LEGACY_LOG_NOT_VISUAL_ACCEPTANCE")
        for line in text.splitlines():
            if self.counts["lines"] >= MAX_LINES:
                self.coverage.add("LINE_BUDGET")
                return
            self.counts["lines"] += 1
            if len(line) > MAX_LINE:
                self.counts["oversize_lines"] += 1
                self.coverage.add("OVERSIZE_LINE_OMITTED")
                continue
            index = line.find("[CLL]")
            if index < 0:
                continue
            try:
                tokens = shlex.split(line[index + 5:], posix=True)
                fields = {}
                for token in tokens[:64]:
                    if "=" in token:
                        key, val = token.split("=", 1)
                        if key in fields:
                            raise ValueError("duplicate_log_field")
                        fields[key] = val
            except ValueError:
                self.counts["malformed_log_lines"] += 1
                continue
            self.counts["cll_lines"] += 1
            raw_event = fields.get("event", "")
            event = known(raw_event.upper().replace("-", "_"), EVENTS, "UNRECOGNIZED_EVENT")
            self.events[event] += 1
            component = known(fields.get("component"), COMPONENTS, "OTHER_COMPONENT")
            self.counts["component:" + component] += 1
            identity = fields.get("buildId")
            if identity is None:
                match = re.search(r"(?:^|\s)buildId=([^\s\"]+)", fields.get("reason", ""))
                identity = match[1] if match else None
            safe_id = build_id(identity)
            if safe_id != "unknown":
                self.builds.add(safe_id)
            epoch = fields.get("processEpoch")
            if isinstance(epoch, str) and UUID.fullmatch(epoch):
                self.epochs.add(epoch)
            reason = fields.get("reason")
            if reason in REASONS:
                self.reasons[reason] += 1
            if fields.get("feature") in FEATURES:
                self.feature(fields)

    def result(self):
        if not self.epochs:
            self.coverage.add("PROCESS_EPOCH_UNKNOWN")
        if not self.builds:
            self.coverage.add("BUILD_IDENTITY_UNKNOWN")
        conclusions = []
        if any(p["comparison"] == "MISMATCH" for p in self.processes):
            conclusions.append("发现已安装与进程自报构建不一致；应核对旧注入进程，不自动重启或卸载。")
        if self.reasons["CONFIG_DISABLED"]:
            conclusions.append("包含配置关闭观测；关闭不等于能力不支持。")
        if self.reasons["PALETTE_UNAVAILABLE"]:
            conclusions.append("包含调色板不可用观测；需按该项证据范围核对手动色回退，不能外推为全部渲染失败。")
        if self.reasons["FRAME_REJECTED"]:
            conclusions.append("包含局部帧拒绝观测；需结合当前绑定，不证明整个功能失效。")
        if self.reasons["TARGET_NOT_OBSERVED"] or "TARGET_NOT_OBSERVED" in self.coverage:
            conclusions.append("有目标未观测，无法区分未运行、旧协议、时机或权限；不据此判模块未启用。")
        conclusions.append("PREPARED、DISPATCHED、DRAW_COMMANDS、PRESENTATION_ASSIGNED 均不构成实机视觉验收。")
        if "PROCESS_EPOCH_UNKNOWN" in self.coverage:
            conclusions.append("缺少进程纪元，无法仅凭相同事件序列证明重放，也不按时间戳推断跨进程因果。")
        return {"schema": "cll-offline-summary-v1", "counts": dict(sorted(self.counts.items())),
            "events": dict(sorted(self.events.items())), "reasons": dict(sorted(self.reasons.items())),
            "buildIdentities": sorted(self.builds), "processEpochCount": len(self.epochs),
            "processes": self.processes[:256], "runtime": self.runtime_info[:128],
            "effectiveConfigs": self.effective_configs[:128], "capabilities": self.observations[:128],
            "coverage": sorted(self.coverage), "conclusions": conclusions,
            "visualAcceptance": "NOT_ESTABLISHED", "privacy": "WHITELIST_ONLY_NO_ORIGINAL_LINES"}

def safe_member(info):
    name = info.filename.replace("\\", "/")
    path = pathlib.PurePosixPath(name)
    return not path.is_absolute() and ".." not in path.parts and not re.match(r"^[A-Za-z]:", name) and not stat.S_ISLNK(info.external_attr >> 16)

def summarize(input_path):
    path = pathlib.Path(input_path)
    if path.stat().st_size > MAX_INPUT:
        raise BudgetError("input_budget")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            digest.update(chunk)
    summary = Summary()
    def consume(raw, name):
        if len(raw) > MAX_FILE:
            raise BudgetError("file_budget")
        text = raw.decode("utf-8", errors="replace")
        if "\ufffd" in text:
            summary.coverage.add("DECODE_REPLACEMENTS")
        if name.lower().endswith(".json"):
            try:
                summary.document(json.loads(text, object_pairs_hook=unique_object))
            except (ValueError, RecursionError):
                summary.counts["malformed_json"] += 1
                summary.coverage.add("MALFORMED_JSON")
        else:
            summary.log(text)
    if path.suffix.lower() == ".zip":
        total = 0
        # Bound directory allocation before ZipFile constructs ZipInfo objects. ZIP64 is explicit unknown.
        with path.open("rb") as raw_archive:
            raw_archive.seek(max(0, path.stat().st_size - 65557))
            end = raw_archive.read()
        marker = end.rfind(b"PK\x05\x06")
        if marker < 0 or len(end) - marker < 22:
            raise ValueError("invalid_zip_directory")
        count = struct.unpack_from("<H", end, marker + 10)[0]
        if count == 65535 or count > 10000:
            raise BudgetError("archive_directory_budget")
        with zipfile.ZipFile(path) as archive:
            members = [item for item in archive.infolist() if not item.is_dir() and item.filename.lower().endswith((".log", ".txt", ".json"))]
            def priority(item):
                name = item.filename.lower()
                return (0 if any(word in name for word in ("module", "verbose", "lsposed", "lspd", "diagnostic")) else 1 if name.endswith(".log") else 2)
            members.sort(key=priority)
            if len(members) > MAX_MEMBERS:
                summary.coverage.add("MEMBER_BUDGET")
            for info in members[:MAX_MEMBERS]:
                if not safe_member(info):
                    summary.counts["unsafe_members_skipped"] += 1
                    continue
                if info.is_dir() or not info.filename.lower().endswith((".log", ".txt", ".json")):
                    continue
                if info.flag_bits & 1:
                    summary.coverage.add("ENCRYPTED_MEMBER_NOT_READ")
                    continue
                if info.file_size > MAX_FILE or total + info.file_size > MAX_TOTAL:
                    summary.coverage.add("DECODED_BYTE_BUDGET")
                    continue
                try:
                    with archive.open(info) as stream:
                        raw = stream.read(MAX_FILE + 1)
                    total += len(raw)
                    consume(raw, info.filename)
                    summary.counts["members_read"] += 1
                except (OSError, RuntimeError, zipfile.BadZipFile, BudgetError):
                    summary.coverage.add("MEMBER_UNREADABLE")
    else:
        if path.stat().st_size > MAX_FILE:
            raise BudgetError("file_budget")
        consume(path.read_bytes(), path.name)
    result = summary.result()
    result["inputSha256"] = digest.hexdigest()
    result["inputKind"] = "zip" if path.suffix.lower() == ".zip" else "json" if path.suffix.lower() == ".json" else "text"
    return result

def markdown(result):
    lines = ["# CLL 离线诊断摘要", "", "只列白名单元数据；不含原始日志行。不是实机视觉验收。", "",
        "## 覆盖与缺口", "", ", ".join(result["coverage"]), "", "## 有证据支持的提示", ""]
    lines += ["- " + item for item in result["conclusions"]]
    lines += ["", "## 事件计数", "", "| 事件 | 次数 |", "| --- | --- |"]
    lines += ["| " + key + " | " + str(value) + " |" for key, value in result["events"].items()]
    lines += ["", "## 最终原因计数", "", "| 原因 | 次数 |", "| --- | --- |"]
    lines += ["| " + key + " | " + str(value) + " |" for key, value in result["reasons"].items()]
    return "\n".join(lines) + "\n"

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input")
    parser.add_argument("--output-dir")
    args = parser.parse_args(argv)
    try:
        result = summarize(args.input)
        encoded = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
        if args.output_dir:
            directory = pathlib.Path(args.output_dir)
            directory.mkdir(parents=True, exist_ok=True)
            for name in ("diagnostic-summary.json", "diagnostic-summary.md"):
                if (directory / name).exists():
                    raise ValueError("output_exists")
            (directory / "diagnostic-summary.json").write_text(encoded, encoding="utf-8")
            (directory / "diagnostic-summary.md").write_text(markdown(result), encoding="utf-8")
            print("SUMMARY_WRITTEN_WHITELIST_ONLY")
        else:
            print(encoded, end="")
        return 0
    except (OSError, ValueError, zipfile.BadZipFile, RecursionError):
        # Do not print exception messages: those can include private absolute filenames.
        print("DIAGNOSTIC_INPUT_OR_OUTPUT_UNAVAILABLE", file=sys.stderr)
        return 2

if __name__ == "__main__":
    raise SystemExit(main())
