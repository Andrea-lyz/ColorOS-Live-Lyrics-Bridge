package io.github.andrealtb.artwork.am;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

/** Only unencrypted finite single-file byte-range VOD can become a v1 local asset. */
final class AmHls {
    record Variant(URI uri, int width, int height, String codec, double fps, long bitrate, String id) {}
    record FilePlan(URI uri, long bytes, double durationSeconds) {}
    private static final Pattern ATTRIBUTE = Pattern.compile("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)");
    static Map<String, String> attributes(String line) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(line);
        while (matcher.find()) {
            String value = matcher.group(2);
            if (value.startsWith("\"")) value = value.substring(1, value.length() - 1);
            values.put(matcher.group(1), value);
        }
        return values;
    }
    static List<Variant> variants(URI master, String text, ArtworkQuery query) throws AmFailure {
        try {
            if (!text.startsWith("#EXTM3U")) throw new IllegalArgumentException();
            List<Variant> variants = new ArrayList<>();
            Map<String, String> pending = null;
            for (String raw : text.split("\\r?\\n")) {
                String line = raw.trim();
                if (line.startsWith("#EXT-X-STREAM-INF:")) pending = attributes(line.substring(line.indexOf(':') + 1));
                else if (!line.isEmpty() && !line.startsWith("#") && pending != null) {
                    String[] size = pending.getOrDefault("RESOLUTION", "").split("x");
                    if (size.length != 2) throw new IllegalArgumentException();
                    int width = Integer.parseInt(size[0]), height = Integer.parseInt(size[1]);
                    String codec = pending.getOrDefault("CODECS", "");
                    double fps = Double.parseDouble(pending.getOrDefault("FRAME-RATE", "0"));
                    long bitrate = Long.parseLong(pending.getOrDefault("AVERAGE-BANDWIDTH", pending.getOrDefault("BANDWIDTH", "0")));
                    // Initial production path chooses native AVC SDR; HEVC profile capability is not in v1.
                    if (codec.matches("avc1\\.[a-zA-Z0-9]+") && "SDR".equals(pending.get("VIDEO-RANGE"))
                            && width > 0 && height > 0 && width == height && width <= query.maxWidth && height <= query.maxHeight
                            && Double.isFinite(fps) && fps > 0 && fps <= 60 && bitrate > 0) {
                        variants.add(new Variant(mediaUri(master.resolve(line)), width, height, codec, fps, bitrate,
                                pending.getOrDefault("STABLE-VARIANT-ID", "")));
                    }
                    pending = null;
                }
            }
            int target = Math.min(Math.max(query.displayWidthPx, query.displayHeightPx), Math.min(query.maxWidth, query.maxHeight));
            // Sufficient smallest size first; if no sufficient size fits the budget, allow the largest lower size.
            variants.sort(Comparator.<Variant>comparingInt(v -> v.width() >= target ? 0 : 1)
                    .thenComparingInt(v -> v.width() >= target ? v.width() : -v.width()).thenComparingLong(Variant::bitrate));
            if (variants.isEmpty()) throw new AmFailure(Status.UNSUPPORTED, "no_avc_square_variant");
            if (variants.size() > 64) throw new IllegalArgumentException();
            return variants;
        } catch (RuntimeException error) { throw new AmFailure(Status.UNSUPPORTED, "invalid_hls_master"); }
    }

    /** After a stalled small rendition, reach another size before exhausting same-size bitrate twins. */
    static void transportFallback(List<Variant> remaining, Variant failed) {
        remaining.sort(Comparator.<Variant>comparingInt(v -> v.width() == failed.width() ? 1 : 0)
                .thenComparing(Comparator.comparingInt(Variant::width).reversed())
                .thenComparingLong(Variant::bitrate));
    }

    static FilePlan filePlan(URI child, String text, long limit) throws AmFailure {
        try {
            if (!text.startsWith("#EXTM3U") || !text.contains("#EXT-X-PLAYLIST-TYPE:VOD") || !text.contains("#EXT-X-ENDLIST")) throw new IllegalArgumentException();
            URI file = null;
            long next = 0;
            String range = null;
            double duration = 0;
            boolean mapped = false, pendingDuration = false;
            int count = 0;
            for (String raw : text.split("\\r?\\n")) {
                String line = raw.trim();
                if (line.startsWith("#EXT-X-KEY:") || line.startsWith("#EXT-X-SESSION-KEY:")
                        || line.startsWith("#EXT-X-DISCONTINUITY") || line.startsWith("#EXT-X-GAP")
                        || line.startsWith("#EXT-X-STREAM-INF")) throw new IllegalArgumentException();
                if (line.startsWith("#EXT-X-MAP:")) {
                    if (mapped || count != 0) throw new IllegalArgumentException();
                    Map<String, String> map = attributes(line.substring(line.indexOf(':') + 1));
                    file = mediaUri(child.resolve(map.get("URI")));
                    next = rangeEnd(map.get("BYTERANGE"), 0);
                    mapped = true;
                } else if (line.startsWith("#EXTINF:")) {
                    if (pendingDuration) throw new IllegalArgumentException();
                    double seconds = Double.parseDouble(line.substring(8).split(",", 2)[0]);
                    if (!Double.isFinite(seconds) || seconds <= 0) throw new IllegalArgumentException();
                    duration += seconds;
                    pendingDuration = true;
                } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                    if (range != null) throw new IllegalArgumentException();
                    range = line.substring(17);
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    if (!mapped || !pendingDuration || range == null || !file.equals(mediaUri(child.resolve(line)))) throw new IllegalArgumentException();
                    next = rangeEnd(range, next);
                    if (next > limit || ++count > 1000) throw new IllegalArgumentException();
                    pendingDuration = false;
                    range = null;
                }
            }
            if (!mapped || count == 0 || range != null || pendingDuration || duration > 120 || next > limit) throw new IllegalArgumentException();
            return new FilePlan(file, next, duration);
        } catch (RuntimeException error) { throw new AmFailure(Status.UNSUPPORTED, "unsupported_hls_layout"); }
    }
    private static long rangeEnd(String range, long expected) {
        String[] parts = range.split("@", -1);
        if (parts.length > 2) throw new IllegalArgumentException();
        long size = Long.parseLong(parts[0]), start = parts.length == 2 ? Long.parseLong(parts[1]) : expected;
        if (size <= 0 || start != expected || size > Long.MAX_VALUE - start) throw new IllegalArgumentException();
        return start + size;
    }
    static URI mediaUri(URI uri) {
        if (!"https".equals(uri.getScheme()) || !"mvod.itunes.apple.com".equals(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getFragment() != null) throw new IllegalArgumentException();
        return uri;
    }
}
