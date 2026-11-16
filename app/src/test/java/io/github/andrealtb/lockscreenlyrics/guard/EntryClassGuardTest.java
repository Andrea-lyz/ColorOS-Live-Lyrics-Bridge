package io.github.andrealtb.lockscreenlyrics.guard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * E-0 guards (plan §15.3, D11 hard gate for 5.0).
 *
 * <p>① The entry class may not grow: lines, top-level fields, methods and nested-type lines stay
 * at or below {@code guard/entry-class-budget.properties}, and the budget must follow the code
 * down (a move that frees more than the headroom has to lower it in the same change).
 *
 * <p>③ Hook IDs, log events and hook install order are pinned to baseline files, so a
 * behavior-preserving move (E-1 to E-7) shows up as "no change" and anything else is visible.
 *
 * <p>On a mismatch the current values are written to {@code app/build/e0-guard/*.actual}; copy
 * them over the baseline only when the change is intended, and say why in the commit.
 */
public final class EntryClassGuardTest {
    private static final String RESOURCES = "app/src/test/resources/guard/";
    private static final String ENTRY_TYPE = "LockscreenLyricsModule";

    @Test
    public void entryClassStaysWithinItsBudget() throws IOException {
        Map<String, Integer> actual = entryMetrics();
        write("entry-class-budget.properties.actual", actual.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n", "", "\n")));
        Properties budget = new Properties();
        budget.load(new StringReader(BridgeSourceMap.readProjectFile(RESOURCES + "entry-class-budget.properties")));
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Integer> metric : actual.entrySet()) {
            String key = metric.getKey();
            int limit = Integer.parseInt(budget.getProperty(key).trim());
            int headroom = Integer.parseInt(budget.getProperty(key + ".headroom", "0").trim());
            if (metric.getValue() > limit) {
                problems.add(key + " " + metric.getValue() + " is over the budget " + limit
                        + ": put new code in its own class instead of the entry class");
            } else if (limit - metric.getValue() > headroom) {
                problems.add(key + " " + metric.getValue() + " is " + (limit - metric.getValue())
                        + " under the budget " + limit + ": lower the budget (it only goes down)");
            }
        }
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    /** E-0 ②: every file the source map points at exists, so a move cannot silently empty a test. */
    @Test
    public void sourceMapPointsAtExistingFiles() {
        for (BridgeSourceMap.Area area : BridgeSourceMap.Area.values()) {
            for (String path : BridgeSourceMap.files(area)) {
                assertTrue(area + " -> " + path, BridgeSourceMap.locate(path).isFile());
            }
        }
    }

    @Test
    public void hookIdsMatchTheBaseline() throws IOException {
        assertBaseline("hook-ids.txt", hookIds());
    }

    @Test
    public void logEventsMatchTheBaseline() throws IOException {
        assertBaseline("log-events.txt", logEvents());
    }

    @Test
    public void hookInstallOrderMatchesTheBaseline() throws IOException {
        assertBaseline("hook-install-order.txt", installOrder());
    }

    @Test
    public void outlineFindsMembersOfASmallSample() {
        JavaOutline outline = new JavaOutline(String.join("\n",
                "package p;",
                "final class A {",
                "    private static final String S = \"{ not a brace }\"; // }",
                "    @SuppressWarnings(value = \"x\") private int a, b;",
                "    private final Runnable r = new Runnable() { public void run() { int x = 1; } };",
                "    static { int y = 0; }",
                "    A() { }",
                "    <T> T m(T t) throws Exception { if (t == null) { return null; } return t; }",
                "    enum E { ONE(1) { }, TWO(2); E(int v) { } }",
                "    interface I { void f(); }",
                "}"));
        Map<JavaOutline.Kind, List<String>> byKind = new TreeMap<>();
        for (JavaOutline.Member m : outline.members) {
            byKind.computeIfAbsent(m.kind, k -> new ArrayList<>()).add(m.name);
        }
        assertEquals(List.of("A", "E", "I"), byKind.get(JavaOutline.Kind.TYPE));
        assertEquals(List.of("A", "m", "E"), byKind.get(JavaOutline.Kind.METHOD));
        assertEquals(List.of("S", "b", "r"), byKind.get(JavaOutline.Kind.FIELD));
        assertEquals(List.of("static"), byKind.get(JavaOutline.Kind.INITIALIZER));
    }

    // ---- metrics -------------------------------------------------------------------------

    static Map<String, Integer> entryMetrics() throws IOException {
        String source = BridgeSourceMap.readProjectFile(BridgeSourceMap.ENTRY_CLASS);
        JavaOutline outline = new JavaOutline(source);
        int top = -1;
        for (int i = 0; i < outline.members.size(); i++) {
            JavaOutline.Member m = outline.members.get(i);
            if (m.kind == JavaOutline.Kind.TYPE && m.parent < 0 && m.name.equals(ENTRY_TYPE)) top = i;
        }
        assertTrue("entry type not found", top >= 0);
        int fields = 0, methods = 0, nestedTypes = 0, nestedTypeLines = 0;
        for (int i = 0; i < outline.members.size(); i++) {
            JavaOutline.Member m = outline.members.get(i);
            boolean direct = m.parent == top;
            if (direct && m.kind == JavaOutline.Kind.FIELD) fields++;
            if (direct && m.kind == JavaOutline.Kind.METHOD) methods++;
            boolean extraType = m.kind == JavaOutline.Kind.TYPE && (direct || (m.parent < 0 && i != top));
            if (extraType) {
                nestedTypes++;
                nestedTypeLines += outline.lines(m);
            }
        }
        Map<String, Integer> metrics = new LinkedHashMap<>();
        metrics.put("lines", (int) source.chars().filter(c -> c == '\n').count() + (source.endsWith("\n") ? 0 : 1));
        metrics.put("fields", fields);
        metrics.put("methods", methods);
        metrics.put("nestedTypes", nestedTypes);
        metrics.put("nestedTypeLines", nestedTypeLines);
        return metrics;
    }

    // ---- baselines -----------------------------------------------------------------------

    private static final Pattern HOOK_ID_DEFINITION =
            Pattern.compile("\\b(HOOK_ID_\\w+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern EVENT_CONSTANT = Pattern.compile("\\bBridgeEvents\\.(\\w+)");
    private static final Pattern EVENT_DEFINITION =
            Pattern.compile("public static final String (\\w+) = \"([^\"]*)\";");
    private static final Pattern LITERAL_EVENT = Pattern.compile("BridgeDebugArea\\.\\w+,\\s*\"([^\"]+)\"");
    private static final Pattern INTERCEPT = Pattern.compile("\\.intercept\\(");
    /** {@code this::onX}, {@code owner::onX} or {@code Outer.this::onX}: the receiver is ignored. */
    private static final Pattern METHOD_REFERENCE = Pattern.compile("\\s*[\\w$.]+::([\\w$]+)");

    static String hookIds() throws IOException {
        Map<String, String> values = new TreeMap<>();
        Map<String, JavaOutline> outlines = outlines();
        for (JavaOutline outline : outlines.values()) {
            Matcher m = HOOK_ID_DEFINITION.matcher(outline.source);
            while (m.find()) values.put(m.group(1), m.group(2));
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> id : values.entrySet()) {
            int uses = 0;
            Pattern word = Pattern.compile("\\b" + id.getKey() + "\\b");
            for (JavaOutline outline : outlines.values()) {
                Matcher m = word.matcher(outline.code);
                while (m.find()) uses++;
            }
            out.append(id.getKey()).append(" = \"").append(id.getValue()).append("\" uses=").append(uses - 1).append('\n');
        }
        return out.toString();
    }

    static String logEvents() throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        StringBuilder out = new StringBuilder("# BridgeEvents definitions\n");
        for (Map.Entry<String, JavaOutline> file : outlines().entrySet()) {
            JavaOutline outline = file.getValue();
            if (file.getKey().endsWith("/diagnostics/BridgeEvents.java")) {
                Matcher d = EVENT_DEFINITION.matcher(outline.source);
                while (d.find()) out.append(d.group(1)).append(" = \"").append(d.group(2)).append("\"\n");
                continue;
            }
            Matcher c = EVENT_CONSTANT.matcher(outline.code);
            while (c.find()) counts.merge("BridgeEvents." + c.group(1), 1, Integer::sum);
            Matcher l = LITERAL_EVENT.matcher(outline.source);
            while (l.find()) counts.merge("\"" + l.group(1) + "\"", 1, Integer::sum);
        }
        out.append("# Uses outside BridgeEvents\n");
        counts.forEach((k, v) -> out.append(k).append(" x").append(v).append('\n'));
        return out.toString();
    }

    static String installOrder() throws IOException {
        Map<String, List<String>> byMethod = new TreeMap<>();
        for (JavaOutline outline : outlines().values()) {
            Matcher m = INTERCEPT.matcher(outline.code);
            while (m.find()) {
                int at = m.start();
                JavaOutline.Member method = outline.enclosingMethod(at);
                // Keyed by method name only, never by the declaring class, so moving an installer
                // to another class (E-2 to E-7) leaves the baseline unchanged.
                String key = method == null ? "<field-initializer>" : method.name;
                int statement = Math.max(outline.code.lastIndexOf(';', at),
                        Math.max(outline.code.lastIndexOf('{', at), outline.code.lastIndexOf('}', at)));
                String setId = setIdArgument(outline.source.substring(statement + 1, at));
                String rest = outline.source.substring(m.end(), Math.min(outline.source.length(), m.end() + 120));
                Matcher reference = METHOD_REFERENCE.matcher(rest);
                String callback = reference.lookingAt() ? reference.group(1) : "<lambda>";
                byMethod.computeIfAbsent(key, k -> new ArrayList<>()).add(setId + " -> " + callback);
            }
        }
        StringBuilder out = new StringBuilder();
        byMethod.forEach((method, hooks) -> {
            out.append(method).append('\n');
            for (String hook : hooks) out.append("  ").append(hook).append('\n');
        });
        return out.toString();
    }

    /** The argument of the last {@code .setId(...)} in a hook chain, with balanced parentheses. */
    private static String setIdArgument(String chain) {
        int open = chain.lastIndexOf(".setId(");
        if (open < 0) return "-";
        int start = open + ".setId(".length();
        int depth = 1;
        for (int i = start; i < chain.length(); i++) {
            char c = chain.charAt(i);
            if (c == '(') depth++;
            if (c == ')' && --depth == 0) return chain.substring(start, i).trim().replaceAll("\\s+", " ");
        }
        return "-";
    }

    /** Every main Java source, keyed by repository-relative path (forward slashes), sorted. */
    private static Map<String, JavaOutline> outlines() throws IOException {
        File root = BridgeSourceMap.repositoryRoot();
        File main = new File(root, "app/src/main/java");
        Map<String, JavaOutline> result = new TreeMap<>();
        try (Stream<java.nio.file.Path> paths = Files.walk(main.toPath())) {
            for (java.nio.file.Path path : paths.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                String relative = root.toPath().relativize(path).toString().replace(File.separatorChar, '/');
                result.put(relative, new JavaOutline(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)));
            }
        }
        return result;
    }

    private static void assertBaseline(String name, String actual) throws IOException {
        write(name + ".actual", actual);
        String expected = BridgeSourceMap.readProjectFile(RESOURCES + name).replace("\r\n", "\n");
        if (!expected.equals(actual)) {
            fail(name + " changed. If intended, copy app/build/e0-guard/" + name + ".actual over "
                    + RESOURCES + name + " and explain the change in the commit.");
        }
    }

    private static void write(String name, String text) throws IOException {
        File dir = new File(BridgeSourceMap.repositoryRoot(), "app/build/e0-guard");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        Files.write(new File(dir, name).toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
