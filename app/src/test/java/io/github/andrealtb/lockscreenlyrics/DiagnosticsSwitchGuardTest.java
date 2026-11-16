package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus;
import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus.Feature;
import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus.Outcome;
import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus.Path;
import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus.Reason;
import io.github.andrealtb.lockscreenlyrics.diagnostics.ShortDiagnosticSession;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * C-05b invariant "the diagnostics switch does not change behaviour" (plan section 4 C-05,
 * AGENTS: debug must not change hooks, lyric intake, publication, rendering or AOD).
 *
 * <p>Every place that reads a diagnostics switch is pinned in
 * {@code guard/diagnostic-switch-sites.txt} by file, enclosing method and predicate. A new or
 * moved read fails until it is reviewed: it must only gate logging or bounded recording, or be
 * listed in {@link #BEHAVIOUR_EXCEPTIONS} with the reason.</p>
 */
public class DiagnosticsSwitchGuardTest {
    private static final Pattern SWITCH = Pattern.compile(
            "\\b(is[A-Za-z]*Diagnostics?Enabled|isAreaEnabled|ArtworkTrace\\.enabled"
                    + "|ShortDiagnosticSession\\.global\\(\\)\\.active)\\(");
    private static final java.util.Set<String> KEYWORDS = java.util.Set.of(
            "if", "for", "while", "switch", "catch", "synchronized", "this", "super", "return", "new", "else", "try");
    private static final Pattern METHOD = Pattern.compile(
            "^ {4,8}(?:@\\w+\\s+)*(?:(?:public|private|protected|static|final|synchronized)\\s+)*"
                    + "[\\w<>\\[\\],.? ]+\\s+(\\w+)\\s*\\([^;]*$");

    /**
     * Reviewed reads that do change runtime work. Keep each with its reason; removing the
     * exception from the code must also remove it here.
     */
    private static final Map<String, String> BEHAVIOUR_EXCEPTIONS = Map.of();

    @Test
    public void switchReadsMatchTheReviewedBaseline() throws Exception {
        String actual = render(sites());
        java.nio.file.Path build = Files.isDirectory(Paths.get("build")) ? Paths.get("build/c05")
                : Paths.get("app/build/c05");
        Files.createDirectories(build);
        Files.write(build.resolve("diagnostic-switch-sites.txt.actual"), actual.getBytes(StandardCharsets.UTF_8));
        String expected = new String(Files.readAllBytes(resource()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!expected.equals(actual)) {
            fail("diagnostic-switch-sites.txt changed. Review the new read (it must only gate logging or"
                    + " recording), then copy " + build.resolve("diagnostic-switch-sites.txt.actual")
                    + " over app/src/test/resources/guard/diagnostic-switch-sites.txt.");
        }
    }

    @Test
    public void behaviourExceptionsAreStillPresent() throws Exception {
        Map<String, Integer> sites = sites();
        for (String key : BEHAVIOUR_EXCEPTIONS.keySet()) {
            assertTrue("exception no longer in the code, drop it: " + key,
                    sites.keySet().stream().anyMatch(s -> s.startsWith(key + " ")));
        }
    }

    /** The bounded short-diagnostic recorder must not change the capability snapshot. */
    @Test
    public void shortDiagnosticSessionDoesNotChangeCapabilitySnapshots() throws Exception {
        String off = snapshotAfterScript(false);
        String on = snapshotAfterScript(true);
        assertEquals(off, on);
    }

    private static String snapshotAfterScript(boolean recording) throws Exception {
        long[] now = {1_000L};
        CompatibilityStatus status = new CompatibilityStatus(() -> now[0], 60_000L);
        String token = recording ? ShortDiagnosticSession.global().start() : null;
        try {
            Object rowA = new Object();
            Object rowB = new Object();
            status.requested(Feature.CHAR_LIFT, true);
            status.draw(rowA, Path.CUSTOM, Outcome.DISPATCHED, Reason.NONE);
            now[0] += 5;
            status.draw(rowB, Path.NATIVE, Outcome.FALLBACK, Reason.DATASET_UNSUPPORTED);
            status.palette(rowA, true, false);
            status.liftBlocked(rowA, true, false, true, false, false);
            status.degraded(Feature.CUSTOM_DRAW, rowB, Path.NATIVE, CompatibilityStatus.Scope.RENDER_DISPATCH);
            now[0] += 5;
            status.translationButton(rowA, true, 1, true);
            StringBuilder out = new StringBuilder();
            for (CompatibilityStatus.Snapshot row : status.snapshot()) out.append(fields(row)).append('\n');
            return out.toString();
        } finally {
            if (token != null) ShortDiagnosticSession.global().stop(token);
        }
    }

    private static String fields(Object value) throws IllegalAccessException {
        StringBuilder b = new StringBuilder();
        for (Field f : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            Object v = f.get(value);
            b.append(f.getName()).append('=')
                    .append(v instanceof int[] ? java.util.Arrays.toString((int[]) v) : String.valueOf(v)).append(' ');
        }
        return b.toString();
    }

    private static Map<String, Integer> sites() throws Exception {
        java.nio.file.Path root = Files.isDirectory(Paths.get("src/main/java")) ? Paths.get("src/main/java")
                : Paths.get("app/src/main/java");
        java.nio.file.Path pkg = root.resolve("io/github/andrealtb/lockscreenlyrics");
        Map<String, Integer> out = new TreeMap<>();
        List<java.nio.file.Path> files;
        try (Stream<java.nio.file.Path> walk = Files.walk(pkg)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
        for (java.nio.file.Path file : files) {
            String rel = pkg.relativize(file).toString().replace('\\', '/');
            String method = "<field>";
            List<String> lines = new ArrayList<>(
                    List.of(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\r?\n")));
            for (String line : lines) {
                Matcher m = METHOD.matcher(line);
                if (m.find() && !KEYWORDS.contains(m.group(1)) && !line.trim().startsWith("return")
                        && !line.contains(" new ") && !line.contains(" = ")) {
                    method = m.group(1);
                }
                String code = line.trim();
                if (code.startsWith("//") || code.startsWith("*")) continue;
                Matcher s = SWITCH.matcher(line);
                while (s.find()) {
                    String predicate = s.group(1);
                    // The predicate's own declaration is not a read.
                    if (line.matches(".*\\b(boolean|static)\\b.*\\b" + Pattern.quote(predicate) + "\\(.*\\{\\s*")) {
                        continue;
                    }
                    out.merge(rel + "#" + method + " " + predicate, 1, Integer::sum);
                }
            }
        }
        return out;
    }

    private static String render(Map<String, Integer> sites) {
        StringBuilder b = new StringBuilder("# C-05b: reads of a diagnostics switch, by file#method and predicate.\n"
                + "# Each read must only gate logging or bounded recording, unless DiagnosticsSwitchGuardTest\n"
                + "# lists it as a reviewed behaviour exception.\n");
        for (Map.Entry<String, Integer> e : sites.entrySet()) {
            b.append(e.getKey()).append(" x").append(e.getValue());
            String key = e.getKey().substring(0, e.getKey().indexOf(' '));
            if (BEHAVIOUR_EXCEPTIONS.containsKey(key)) b.append("  [behaviour exception]");
            b.append('\n');
        }
        return b.toString();
    }

    private static java.nio.file.Path resource() {
        java.nio.file.Path p = Paths.get("src/test/resources/guard/diagnostic-switch-sites.txt");
        return Files.exists(p) ? p : Paths.get("app").resolve(p);
    }
}
