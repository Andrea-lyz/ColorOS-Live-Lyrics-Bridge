package io.github.andrealtb.lockscreenlyrics;

/** Turns a SystemUI artwork status code into the explanation the settings page shows. */
final class ArtworkSettingsStatus {
    enum Kind {
        OFF, WAITING, RESOLVING, PLAYING, PLAYED, NO_MOTION, MOTION_UNSUPPORTED, UNMATCHED, SOURCE_UNREADABLE,
        SOURCE_DISABLED, NETWORK,
        TEST_ONLY, PROVIDER_REJECTED, PROVIDER_UNAVAILABLE, PROVIDER_FAILED, PLAYBACK_FAILED, FAILED
    }

    private ArtworkSettingsStatus() {
    }

    static Kind classify(String state) {
        String code = state == null ? "" : state;
        String status = code.contains(":") ? code.substring(0, code.indexOf(':')) : code;
        String reason = code.contains(":") ? code.substring(code.indexOf(':') + 1) : "";
        switch (status) {
            case "off":
                return Kind.OFF;
            case "waiting":
            case "":
                return Kind.WAITING;
            case "resolving":
                return Kind.RESOLVING;
            case "playing":
                return Kind.PLAYING;
            case "played":
                return Kind.PLAYED;
            case "no_motion":
                return Kind.NO_MOTION;
            case "no_match":
            case "ambiguous":
                return Kind.UNMATCHED;
            case "retry_later":
                if (reason.equals("web_schema_changed") || reason.equals("catalog_schema_changed")) return Kind.SOURCE_UNREADABLE;
                return reason.startsWith("catalog_") ? Kind.UNMATCHED : Kind.NETWORK;
            case "network_blocked":
                return reason.equals("provider_disabled") ? Kind.SOURCE_DISABLED : Kind.NETWORK;
            case "provider_mode_mismatch":
                return Kind.TEST_ONLY;
            case "artwork_caller_denied":
                return Kind.PROVIDER_REJECTED;
            case "selection_failed":
            case "selection_timeout":
            case "provider_unavailable":
            case "provider_identity_changed":
                return Kind.PROVIDER_UNAVAILABLE;
            case "bind_failed":
            case "handshake_timeout":
            case "request_timeout":
            case "provider_died":
            case "provider_disconnected":
            case "binding_died":
            case "null_binding":
            case "connection_lost":
            case "ipc_budget_exhausted":
            case "provider_or_asset_failed":
                return Kind.PROVIDER_FAILED;
            case "unsupported":
                if (reason.equals("no_square_motion_asset")) return Kind.NO_MOTION;
                return reason.equals("motion_asset_unrecognized") ? Kind.MOTION_UNSUPPORTED : Kind.PLAYBACK_FAILED;
            case "mount_unsupported":
            case "decode_failed":
            case "prepare_failed":
            case "first_frame_timeout":
            case "surface_handover_failed":
            case "geometry_changed":
            case "consumer_failed":
                return Kind.PLAYBACK_FAILED;
            default:
                return Kind.FAILED;
        }
    }
}
