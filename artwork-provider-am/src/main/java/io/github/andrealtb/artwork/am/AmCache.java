package io.github.andrealtb.artwork.am;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult;

/**
 * Immutable content files; lease pins prevent eviction between READY and openAsset.
 * File I/O deliberately happens outside the monitor: a slow cache-directory read must never block
 * the single resolve worker, and holding it through writes previously stalled whole requests.
 */
final class AmCache {
    record Hit(File file, ArtworkResult failure) {}
    /** Unpinned videos beyond the budget are evicted, least recently used first. */
    static final long DEFAULT_BUDGET_BYTES = AmSettings.DEFAULT_CACHE_LIMIT_MB * 1_000_000L;
    private static AmCache instance;
    private final File root;
    private volatile long budgetBytes = DEFAULT_BUDGET_BYTES;
    private final Object pinLock = new Object();
    private final Map<String, Integer> pins = new HashMap<>();
    static synchronized AmCache get(Context context) {
        if (instance == null) instance = new AmCache(new File(context.getCacheDir(), "am-artwork-v1"));
        instance.setBudget(AmSettings.cacheLimitBytes(context));
        return instance;
    }
    long budgetBytes() { return budgetBytes; }
    /** The preference is clamped on read, so a non-positive value never means "evict everything". */
    void setBudget(long bytes) { if (bytes > 0) budgetBytes = bytes; }
    AmCache(File root) { this.root = root; if (!root.isDirectory() && !root.mkdirs()) throw new IllegalStateException("cache_unavailable"); }
    static String hash(String value) { return digest(value.getBytes(StandardCharsets.UTF_8)); }
    static String digest(byte[] value) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    static String key(ArtworkQuery query, String country) {
        // Include raw URL, limits and completed query fields: no success or negative reuse across edition changes.
        return hash("match-v3\n" + country + "\n" + AmIdentity.normalize(query.title) + "\n" + AmIdentity.normalize(query.artist)
                + "\n" + AmIdentity.normalize(query.album) + "\n" + query.durationMs + "\n" + query.appleMusicUrl
                + "\n" + query.displayWidthPx + "x" + query.displayHeightPx + "\n" + query.maxWidth + "x" + query.maxHeight + "\n" + query.maxFileBytes);
    }
    Hit lookup(String key) {
        return lookup(key, "");
    }
    Hit lookup(String key, String networkEpoch) {
        File map = new File(root, key + ".json");
        try {
            if (!map.isFile() || map.length() > 4096) return null;
            JSONObject value = new JSONObject(new String(java.nio.file.Files.readAllBytes(map.toPath()), StandardCharsets.UTF_8));
            long remaining = value.getLong("expires") - System.currentTimeMillis();
            if (remaining <= 0 || remaining > 86_400_000) { map.delete(); return null; }
            if (value.has("file")) {
                String name = value.getString("file");
                if (!name.matches("[a-f0-9]{64}\\.mp4")) return null;
                File file = new File(root, name);
                if (!file.isFile()) { map.delete(); return null; }
                pin(file);
                file.setLastModified(System.currentTimeMillis());
                return new Hit(file, null);
            }
            ArtworkResult.Status status = ArtworkResult.Status.valueOf(value.getString("status"));
            if (status == ArtworkResult.Status.RETRY_LATER && AmConnectivity.transport(value.optString("reason"))
                    && !networkEpoch.isEmpty() && !networkEpoch.equals(value.optString("networkEpoch"))) {
                map.delete(); return null;
            }
            return new Hit(null, new ArtworkResult(status, null, status == ArtworkResult.Status.RETRY_LATER ? remaining : 0, value.getString("reason")));
        } catch (Exception ignored) { map.delete(); return null; }
    }
    private static String albumKey(ArtworkQuery query, String country) {
        return hash("album-v2\n" + country + "\n" + AmIdentity.normalize(query.artist) + "\n" + AmIdentity.normalize(query.album));
    }
    static String albumAssetKey(ArtworkQuery query, String country, String albumId) {
        return hash("album-asset-v2\n" + country + "\n" + albumId + "\n" + query.displayWidthPx + "x" + query.displayHeightPx
                + "\n" + query.maxWidth + "x" + query.maxHeight + "\n" + query.maxFileBytes);
    }
    /** A user-bound album answers for the whole album at one size; changing the binding changes the key. */
    static String boundKey(ArtworkQuery query, String country, String albumId) {
        return hash("bound-v1\n" + albumAssetKey(query, country, albumId));
    }
    AmPage.Album album(ArtworkQuery query, String country) {
        if (AmIdentity.normalize(query.album).isEmpty()) return null;
        File file = new File(root, albumKey(query, country) + ".json");
        try {
            if (!file.isFile() || file.length() > 256 * 1024) return null;
            JSONObject value = new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            long remaining = value.getLong("expires") - System.currentTimeMillis();
            if (remaining <= 0 || remaining > 86_400_000) { file.delete(); return null; }
            return AmPage.snapshot(value);
        } catch (Exception error) { file.delete(); return null; }
    }
    void rememberAlbum(ArtworkQuery query, String country, AmPage.Album album) {
        if (AmIdentity.normalize(query.album).isEmpty()) return;
        File temp = null;
        try {
            AmIdentity.unique(album.tracks(), query, "", album.id());
            JSONObject value = AmPage.snapshot(album).put("expires", System.currentTimeMillis() + 86_400_000);
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 256 * 1024) return;
            temp = File.createTempFile("catalog-", ".part", root);
            try (FileOutputStream stream = new FileOutputStream(temp)) { stream.write(bytes); stream.getFD().sync(); }
            java.nio.file.Files.move(temp.toPath(), new File(root, albumKey(query, country) + ".json").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            cleanup();
        } catch (Exception ignored) { /* advisory catalog cache cannot bypass matching */ }
        finally { if (temp != null) temp.delete(); }
    }
    File temporary() throws Exception { return File.createTempFile("fetch-", ".part", root); }
    File commit(File temp) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream stream = new FileInputStream(temp)) {
            byte[] buffer = new byte[32 * 1024];
            for (int count; (count = stream.read(buffer)) != -1;) digest.update(buffer, 0, count);
        }
        File result = new File(root, hex(digest.digest()) + ".mp4");
        if (result.exists()) { if (!temp.delete()) throw new java.io.IOException("cache_temp"); }
        else if (!temp.renameTo(result)) throw new java.io.IOException("cache_commit");
        pin(result);
        return result;
    }
    void remember(String key, File file, ArtworkResult failure) {
        remember(key, file, failure, "");
    }
    void remember(String key, File file, ArtworkResult failure, String networkEpoch) {
        try {
            JSONObject value = new JSONObject();
            long ttl = 86_400_000;
            if (file != null) value.put("file", file.getName());
            else {
                if (failure == null || failure.status == ArtworkResult.Status.ERROR || failure.status == ArtworkResult.Status.NETWORK_BLOCKED) return;
                value.put("status", failure.status.name()).put("reason", failure.reason);
                if (failure.status == ArtworkResult.Status.RETRY_LATER && AmConnectivity.transport(failure.reason)) value.put("networkEpoch", networkEpoch);
                if (failure.status == ArtworkResult.Status.RETRY_LATER) ttl = failure.retryAfterMs;
                else if (failure.status != ArtworkResult.Status.NO_MOTION) ttl = 60_000;
            }
            value.put("expires", System.currentTimeMillis() + ttl);
            File temp = File.createTempFile("index-", ".part", root);
            try {
                try (FileOutputStream stream = new FileOutputStream(temp)) { stream.write(value.toString().getBytes(StandardCharsets.UTF_8)); stream.getFD().sync(); }
                java.nio.file.Files.move(temp.toPath(), new File(root, key + ".json").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally { temp.delete(); }
            cleanup();
        } catch (Exception ignored) { /* cache failure does not invalidate a verified pinned asset */ }
    }
    private static String inventoryKey(String country, String albumId) { return hash("album-videos-v1\n" + country + "\n" + albumId); }
    void rememberVideo(String country, String albumId, File file, io.github.andrealtb.artwork.contract.ArtworkAsset asset) {
        File index = new File(root, inventoryKey(country, albumId) + ".json");
        File temp = null;
        try {
            org.json.JSONArray videos = new org.json.JSONArray();
            if (index.isFile() && index.length() <= 16 * 1024) {
                org.json.JSONArray old = new JSONObject(new String(java.nio.file.Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8)).getJSONArray("videos");
                for (int i = 0; i < old.length() && videos.length() < 7; i++) {
                    JSONObject item = old.getJSONObject(i);
                    if (!file.getName().equals(item.optString("file")) && item.optLong("expires") > System.currentTimeMillis()) videos.put(item);
                }
            }
            videos.put(new JSONObject().put("file", file.getName()).put("width", asset.width).put("height", asset.height)
                    .put("bytes", asset.fileBytes).put("expires", System.currentTimeMillis() + 86_400_000));
            temp = File.createTempFile("videos-", ".part", root);
            try (FileOutputStream stream = new FileOutputStream(temp)) {
                stream.write(new JSONObject().put("videos", videos).toString().getBytes(StandardCharsets.UTF_8)); stream.getFD().sync();
            }
            java.nio.file.Files.move(temp.toPath(), index.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            cleanup();
        } catch (Exception ignored) { /* immutable video remains usable through its ordinary indexes */ }
        finally { if (temp != null) temp.delete(); }
    }
    Hit compatibleVideo(io.github.andrealtb.artwork.contract.ArtworkQuery query, String country, String albumId) {
        return compatibleVideo(query, country, albumId, false);
    }
    /**
     * Another size of the same album. With {@code sufficientOnly} a smaller video is never reused for a
     * larger host: device feedback showed the small card's 360-408 px video upscaled onto the large cover.
     * Sufficiency is capped at the query's resolution limit, the largest size that can ever be fetched.
     */
    Hit compatibleVideo(io.github.andrealtb.artwork.contract.ArtworkQuery query, String country, String albumId,
            boolean sufficientOnly) {
        File index = new File(root, inventoryKey(country, albumId) + ".json");
        try {
            if (!index.isFile() || index.length() > 16 * 1024) return null;
            org.json.JSONArray videos = new JSONObject(new String(java.nio.file.Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8)).getJSONArray("videos");
            if (videos.length() > 8) return null;
            File best = null;
            int bestSize = 0;
            int target = Math.min(Math.max(query.displayWidthPx, query.displayHeightPx), Math.min(query.maxWidth, query.maxHeight));
            for (int i = 0; i < videos.length(); i++) {
                JSONObject item = videos.getJSONObject(i);
                String name = item.getString("file");
                int width = item.getInt("width"), height = item.getInt("height");
                long size = item.getLong("bytes"), remaining = item.getLong("expires") - System.currentTimeMillis();
                if (!name.matches("[a-f0-9]{64}\\.mp4") || width <= 0 || width != height || width > query.maxWidth || height > query.maxHeight
                        || size <= 0 || size > query.maxFileBytes || remaining <= 0 || remaining > 86_400_000) continue;
                File candidate = new File(root, name);
                if (!candidate.isFile() || candidate.length() != size) continue;
                boolean sufficient = width >= target, bestSufficient = bestSize >= target;
                if (best == null || sufficient && !bestSufficient || sufficient && bestSufficient && width < bestSize
                        || !sufficient && !bestSufficient && width > bestSize) { best = candidate; bestSize = width; }
            }
            if (best == null || sufficientOnly && bestSize < target) return null;
            pin(best); best.setLastModified(System.currentTimeMillis());
            return new Hit(best, null);
        } catch (Exception ignored) { return null; }
    }
    private void pin(File file) { synchronized (pinLock) { pins.merge(file.getName(), 1, Integer::sum); } }
    void unpin(File file) {
        synchronized (pinLock) { pins.computeIfPresent(file.getName(), (name, count) -> count <= 1 ? null : count - 1); }
    }
    private boolean pinned(String name) { synchronized (pinLock) { return pins.containsKey(name); } }
    void clear() {
        File[] files = root.listFiles();
        if (files != null) for (File file : files) {
            if (!pinned(file.getName()) && (file.getName().endsWith(".mp4") || file.getName().endsWith(".json"))) file.delete();
        }
    }
    /** Cached motion covers for the settings page, most recently used first. */
    java.util.List<File> videos() {
        File[] files = root.listFiles((folder, name) -> name.matches("[a-f0-9]{64}\\.mp4"));
        if (files == null) return java.util.List.of();
        // Snapshot the times: a concurrent lookup touching a file must not change the order mid-sort.
        Map<File, Long> used = new HashMap<>();
        for (File file : files) used.put(file, file.lastModified());
        java.util.List<File> videos = new java.util.ArrayList<>(used.keySet());
        videos.sort(Comparator.comparingLong((File file) -> used.get(file)).reversed());
        return videos;
    }
    void cleanup() {
        File[] files = root.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long bytes = 0;
        int maps = 0;
        for (File file : files) { if (file.getName().endsWith(".mp4")) bytes += file.length(); if (file.getName().endsWith(".json")) maps++; }
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".mp4") && bytes > budgetBytes && !pinned(name)) {
                long size = file.length(); if (file.delete()) bytes -= size;
            } else if (name.endsWith(".json") && maps > 128) { if (file.delete()) maps--; }
            else if (name.endsWith(".part") && System.currentTimeMillis() - file.lastModified() > 3_600_000) file.delete();
        }
    }
    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }
}
