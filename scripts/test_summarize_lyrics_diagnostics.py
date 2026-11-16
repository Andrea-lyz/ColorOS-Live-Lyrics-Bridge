import importlib.util
import io
import json
import pathlib
import tempfile
import unittest
import uuid
import zipfile

SCRIPT = pathlib.Path(__file__).with_name("summarize-lyrics-diagnostics.py")
spec = importlib.util.spec_from_file_location("diagnostics", SCRIPT)
diag = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diag)
PRIVATE = "PRIVATE_LYRIC_TOKEN_USER_PATH_93817"

def identity(digest="b", module="app", repository="Bridge"):
    return "ColorOS-Live-Lyrics-" + repository + ":" + module + ":debug:" + "a" * 40 + ":dirty:" + digest * 64 + ":resolver-1"

class DiagnosticSummaryTests(unittest.TestCase):
    def run_document(self, document):
        with tempfile.TemporaryDirectory() as temp:
            file = pathlib.Path(temp) / "input.json"
            file.write_text(json.dumps(document), encoding="utf-8")
            return diag.summarize(file)
    def feature(self):
        return {"feature": "CHAR_LIFT", "requested": "YES", "supported": "YES", "eligible": "YES",
            "selectedPath": "CHAR_LIFT", "lastOutcome": "PREPARED", "reason": "NONE", "evidenceScope": "CHAR_LIFT_GATE", "observedAt": 10}
    def bridge(self):
        return {"schema": "cll-bridge-diagnostics-v1", "runtime": {"cll_runtime_build_id": identity(),
            "cll_runtime_epoch": str(uuid.uuid4())}, "coverage": "BOUNDED_OBSERVATIONS", "capabilities": [self.feature()],
            "events": [{"event": "USER_MARK", "elapsed": 20, "marker": 1}]}
    def test_prepared_is_never_visual_acceptance(self):
        result = self.run_document(self.bridge())
        self.assertEqual("PREPARED", result["capabilities"][0]["lastOutcome"])
        self.assertEqual("NOT_ESTABLISHED", result["visualAcceptance"])
    def test_private_fields_are_not_copied_from_any_layer(self):
        document = self.bridge()
        document["appEnvironment"] = {"username": PRIVATE, "fontPath": "/storage/" + PRIVATE}
        document["rawLyric"] = PRIVATE
        document["runtime"]["token"] = PRIVATE
        document["capabilities"][0]["privateReason"] = PRIVATE
        document["events"][0]["message"] = PRIVATE
        text = json.dumps(self.run_document(document), ensure_ascii=False)
        self.assertNotIn(PRIVATE, text)
        self.assertNotIn("/storage/", text)
    def test_provider_mismatch_stays_a_self_report(self):
        result = self.run_document({"schema": "cll-provider-diagnostics-v1", "installedBuildId": identity("b", "player-salt", "Providers"),
            "processes": [{"observedBuildId": identity("c", "player-salt", "Providers"), "processEpoch": str(uuid.uuid4()),
                "configObservation": "CONSUMED", "configRevision": 20, "installerStage": "INSTALLER_RETURNED"}]})
        self.assertEqual("MISMATCH", result["processes"][0]["comparison"])
        self.assertIn("SELF_REPORT_NOT_FRAMEWORK_ATTESTATION", result["coverage"])
    def test_unknown_build_does_not_match_unknown_build(self):
        result = self.run_document({"schema": "cll-provider-diagnostics-v1", "installedBuildId": "unknown",
            "processes": [{"observedBuildId": "unknown"}]})
        self.assertEqual("unknown", result["processes"][0]["comparison"])
    def test_timeout_cannot_establish_disabled_or_unsupported(self):
        result = self.run_document({"schema": "cll-provider-diagnostics-v1", "processes": []})
        self.assertIn("TARGET_NOT_OBSERVED", result["coverage"])
        self.assertFalse(result["capabilities"])
    def test_new_schema_is_not_silently_interpreted(self):
        result = self.run_document({"schema": "cll-bridge-diagnostics-v99", "private": PRIVATE})
        self.assertIn("UNRECOGNIZED_SCHEMA", result["coverage"])
        self.assertNotIn(PRIVATE, json.dumps(result))
    def test_duplicate_json_keys_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            file = pathlib.Path(temp) / "input.json"
            file.write_text('{"schema":"cll-provider-diagnostics-v1","schema":"other"}')
            result = diag.summarize(file)
            self.assertIn("MALFORMED_JSON", result["coverage"])
    def test_identical_restart_sequences_do_not_prove_replay(self):
        summary = diag.Summary()
        line = '[CLL] component=provider/salt event=MODULE_LOADED reason="framework=LSPosed api=102 buildId=' + identity("b", "player-salt", "Providers") + '"'
        summary.log(line + "\n" + line)
        result = summary.result()
        self.assertEqual(2, result["events"]["MODULE_LOADED"])
        self.assertIn("PROCESS_EPOCH_UNKNOWN", result["coverage"])
        self.assertTrue(any("无法仅凭相同事件序列证明重放" in item for item in result["conclusions"]))
    def test_original_messages_credentials_urls_and_paths_never_echo(self):
        summary = diag.Summary()
        summary.log('[CLL] component=bridge event=frame-rejected reason=FRAME_REJECTED message="' + PRIVATE + ' https://private/?token=x /data/user/0/private"')
        result = summary.result()
        self.assertEqual(1, result["events"]["FRAME_REJECTED"])
        self.assertNotIn(PRIVATE, json.dumps(result))
    def test_unsafe_archive_names_and_nonlogs_are_not_read(self):
        with tempfile.TemporaryDirectory() as temp:
            file = pathlib.Path(temp) / "input.zip"
            with zipfile.ZipFile(file, "w") as archive:
                archive.writestr("../outside.txt", PRIVATE)
                archive.writestr("nested.apk", PRIVATE)
                archive.writestr("module.log", '[CLL] component=bridge event=HOOK_INSTALLED')
            result = diag.summarize(file)
            self.assertEqual(1, result["counts"]["unsafe_members_skipped"])
            self.assertEqual(1, result["counts"]["members_read"])
            self.assertFalse((pathlib.Path(temp).parent / "outside.txt").exists())
    def test_timestamped_log_members_and_windows_separators_are_not_mistaken_for_drive_paths(self):
        with tempfile.TemporaryDirectory() as temp:
            file = pathlib.Path(temp) / "input.zip"
            with zipfile.ZipFile(file, "w") as archive:
                archive.writestr("logs/modules_2026-11-13T06:00:01.log", "[CLL] component=bridge event=HOOK_INSTALLED")
                archive.writestr("logs\\modules_2026-11-13T06:00:02.log", "[CLL] component=bridge event=HOOK_INSTALLED")
            self.assertEqual(2, diag.summarize(file)["events"]["HOOK_INSTALLED"])
    def test_module_logs_are_prioritized_before_config_dumps(self):
        with tempfile.TemporaryDirectory() as temp:
            file = pathlib.Path(temp) / "input.zip"
            with zipfile.ZipFile(file, "w") as archive:
                for i in range(600):
                    archive.writestr(str(i) + ".txt", "non-log settings")
                archive.writestr("modules.log", "[CLL] component=bridge event=HOOK_INSTALLED")
            self.assertEqual(1, diag.summarize(file)["events"]["HOOK_INSTALLED"])
    def test_event_and_process_budgets_are_explicit(self):
        document = self.bridge(); document["events"] *= 200
        result = self.run_document(document)
        self.assertIn("EVENT_BUDGET", result["coverage"])
        self.assertEqual(128, result["events"]["USER_MARK"])
    def test_negative_omission_counts_are_not_accepted(self):
        document = self.bridge(); document["truncatedEvents"] = -1
        self.assertEqual(0, self.run_document(document)["counts"]["truncatedEvents"])
    def test_repeated_markers_do_not_create_raw_free_text(self):
        document = self.bridge(); document["events"][0]["label"] = PRIVATE
        self.assertNotIn(PRIVATE, json.dumps(self.run_document(document)))
    def test_unknown_enum_values_do_not_become_success(self):
        document = self.bridge(); document["capabilities"][0]["lastOutcome"] = "HEALTHY"
        self.assertEqual("NOT_OBSERVED", self.run_document(document)["capabilities"][0]["lastOutcome"])
    def test_oversized_input_is_rejected_without_echo(self):
        with tempfile.TemporaryDirectory() as temp:
            file = pathlib.Path(temp) / "input.log"
            file.write_bytes(b"x" * (diag.MAX_FILE + 1))
            with self.assertRaises(diag.BudgetError):
                diag.summarize(file)
    def test_output_refuses_to_overwrite_previous_report(self):
        with tempfile.TemporaryDirectory() as temp:
            file = pathlib.Path(temp) / "input.json"; file.write_text(json.dumps(self.bridge()))
            out = pathlib.Path(temp) / "out"
            self.assertEqual(0, diag.main([str(file), "--output-dir", str(out)]))
            before = (out / "diagnostic-summary.json").read_bytes()
            self.assertEqual(2, diag.main([str(file), "--output-dir", str(out)]))
            self.assertEqual(before, (out / "diagnostic-summary.json").read_bytes())
    def test_global_line_budget_is_not_incremented_by_later_files(self):
        summary = diag.Summary()
        summary.counts["lines"] = diag.MAX_LINES
        for _ in range(10):
            summary.log("[CLL] component=bridge event=HOOK_INSTALLED")
        self.assertEqual(diag.MAX_LINES, summary.counts["lines"])
        self.assertIn("LINE_BUDGET", summary.coverage)
    def test_source_truncation_remains_partial_even_with_good_feature(self):
        document = self.bridge(); document["truncatedEvents"] = 12
        self.assertIn("SOURCE_EVENT_OMISSIONS", self.run_document(document)["coverage"])
    def test_effective_config_is_typed_and_private_keys_are_ignored(self):
        document = self.bridge()
        document["effectiveConfig"] = {"charLift": True, "debugEnabled": PRIVATE, "debugRevision": 99, "password": PRIVATE}
        result = self.run_document(document)
        self.assertTrue(result["effectiveConfigs"][0]["charLift"])
        self.assertIsNone(result["effectiveConfigs"][0]["debugEnabled"])
        self.assertNotIn(PRIVATE, json.dumps(result))
    def test_public_framework_metadata_is_not_a_free_text_channel(self):
        document = self.bridge()
        document["runtime"]["cll_runtime_framework"] = PRIVATE
        result = self.run_document(document)
        self.assertEqual("unknown", result["runtime"][0]["framework"])
        self.assertNotIn(PRIVATE, json.dumps(result))
    def test_markdown_only_contains_already_sanitized_summary(self):
        summary = diag.Summary(); summary.log('[CLL] component=bridge event=HOOK_FAILED message="' + PRIVATE + '"')
        self.assertNotIn(PRIVATE, diag.markdown(summary.result()))

if __name__ == "__main__":
    unittest.main()
