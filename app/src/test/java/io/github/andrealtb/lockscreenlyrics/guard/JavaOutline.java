package io.github.andrealtb.lockscreenlyrics.guard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brace-depth outline of one Java source file for the E-0 guards (plan §15.3). Not a parser:
 * comments and string/char literals are blanked first, then type bodies are scanned for
 * fields, methods/constructors, initializer blocks and nested types. Good enough to count
 * members and to find the method that encloses an offset; exact numbers are pinned by the
 * budget file, not by this class.
 */
public final class JavaOutline {
    public enum Kind { TYPE, METHOD, FIELD, INITIALIZER }

    public static final class Member {
        public final Kind kind;
        public final String name;
        /** Index into {@link #members} of the enclosing type, or -1 at file level. */
        public final int parent;
        public final int start;
        public int end;

        Member(Kind kind, String name, int parent, int start, int end) {
            this.kind = kind;
            this.name = name;
            this.parent = parent;
            this.start = start;
            this.end = end;
        }
    }

    public final String source;
    /** Same length as {@link #source}; comments and literal contents replaced by spaces. */
    public final String code;
    public final List<Member> members = new ArrayList<>();

    private static final Pattern TYPE_HEADER =
            Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern LAST_IDENTIFIER = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*$");

    private enum Frame { TYPE, ENUM_TYPE, OTHER, FIELD_INIT }

    public JavaOutline(String source) {
        this.source = source;
        this.code = blank(source);
        scan();
    }

    /** Lines [start, end] of a member, 1-based and inclusive. */
    public int lines(Member member) {
        return lineOf(member.end) - lineOf(member.start) + 1;
    }

    public int lineOf(int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < source.length(); i++) if (source.charAt(i) == '\n') line++;
        return line;
    }

    /** Innermost method containing {@code offset}, or null (e.g. a field initializer). */
    public Member enclosingMethod(int offset) {
        Member best = null;
        for (Member m : members) {
            if (m.kind == Kind.METHOD && m.start <= offset && offset <= m.end
                    && (best == null || m.start >= best.start)) {
                best = m;
            }
        }
        return best;
    }

    /** Innermost type containing {@code offset}, or null. */
    public Member enclosingType(int offset) {
        Member best = null;
        for (Member m : members) {
            if (m.kind == Kind.TYPE && m.start <= offset && offset <= m.end
                    && (best == null || m.start >= best.start)) {
                best = m;
            }
        }
        return best;
    }

    private void scan() {
        Deque<Frame> frames = new ArrayDeque<>();
        Deque<Integer> owners = new ArrayDeque<>(); // member index opened by each frame, or -1
        Deque<Integer> types = new ArrayDeque<>();  // enclosing type member index stack
        int statementStart = 0;
        boolean enumConstantsPending = false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            Frame top = frames.peek();
            boolean inTypeBody = top == null || top == Frame.TYPE || top == Frame.ENUM_TYPE;
            if (c == '{') {
                String header = withoutAnnotations(code.substring(statementStart, i)).trim();
                int headerStart = firstNonSpace(statementStart, i);
                int parent = types.isEmpty() ? -1 : types.peek();
                Matcher type = TYPE_HEADER.matcher(header);
                if (inTypeBody && !header.contains("=") && !header.contains("new ") && type.find()) {
                    members.add(new Member(Kind.TYPE, type.group(2), parent, headerStart, -1));
                    int index = members.size() - 1;
                    boolean isEnum = type.group(1).equals("enum");
                    frames.push(isEnum ? Frame.ENUM_TYPE : Frame.TYPE);
                    owners.push(index);
                    types.push(index);
                    enumConstantsPending = isEnum;
                    statementStart = i + 1;
                } else if (top == Frame.ENUM_TYPE && enumConstantsPending) {
                    frames.push(Frame.OTHER); // body of an enum constant
                    owners.push(-1);
                } else if (inTypeBody && header.contains("=")) {
                    frames.push(Frame.FIELD_INIT); // anonymous class, lambda or array in an initializer
                    owners.push(-1);
                } else if (inTypeBody && header.indexOf('(') >= 0) {
                    String beforeParen = header.substring(0, header.indexOf('('));
                    Matcher name = LAST_IDENTIFIER.matcher(beforeParen);
                    members.add(new Member(Kind.METHOD, name.find() ? name.group(1) : "?", parent, headerStart, -1));
                    frames.push(Frame.OTHER);
                    owners.push(members.size() - 1);
                } else if (inTypeBody) {
                    members.add(new Member(Kind.INITIALIZER, header.isEmpty() ? "{}" : header, parent, headerStart, -1));
                    frames.push(Frame.OTHER);
                    owners.push(members.size() - 1);
                } else {
                    frames.push(Frame.OTHER);
                    owners.push(-1);
                }
            } else if (c == '}') {
                if (frames.isEmpty()) continue;
                Frame closed = frames.pop();
                int owner = owners.pop();
                if (owner >= 0) members.get(owner).end = i;
                if (closed == Frame.TYPE || closed == Frame.ENUM_TYPE) types.pop();
                Frame now = frames.peek();
                boolean backInType = now == null || now == Frame.TYPE || now == Frame.ENUM_TYPE;
                // A closed initializer keeps its statement open until the ';'.
                if (backInType && closed != Frame.FIELD_INIT) statementStart = i + 1;
            } else if (c == ';' && inTypeBody) {
                String header = withoutAnnotations(code.substring(statementStart, i)).trim();
                int headerStart = firstNonSpace(statementStart, i);
                if (top == Frame.ENUM_TYPE && enumConstantsPending) {
                    enumConstantsPending = false; // end of the constant list
                } else if (!header.isEmpty() && !header.startsWith("package ") && !header.startsWith("import ")) {
                    int paren = header.indexOf('(');
                    int equals = header.indexOf('=');
                    boolean abstractMethod = paren >= 0 && (equals < 0 || paren < equals);
                    if (!abstractMethod) {
                        Matcher name = LAST_IDENTIFIER.matcher(equals >= 0 ? header.substring(0, equals) : header);
                        int parent = types.isEmpty() ? -1 : types.peek();
                        members.add(new Member(Kind.FIELD, name.find() ? name.group(1) : "?", parent, headerStart, i));
                    }
                }
                statementStart = i + 1;
            }
        }
    }

    private static final Pattern ANNOTATION = Pattern.compile("@[\\w$.]+\\s*(\\([^()]*\\))?");

    /** `@SuppressWarnings(value = "x")` must not read as an initializer or a call. */
    private static String withoutAnnotations(String header) {
        return header.indexOf('@') < 0 ? header : ANNOTATION.matcher(header).replaceAll(" ");
    }

    private int firstNonSpace(int from, int to) {
        for (int i = from; i < to; i++) if (!Character.isWhitespace(code.charAt(i))) return i;
        return to;
    }

    /** Blanks comments and the contents of string, text-block and char literals. */
    static String blank(String s) {
        StringBuilder out = new StringBuilder(s);
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            char next = i + 1 < n ? s.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < n && s.charAt(i) != '\n') out.setCharAt(i++, ' ');
            } else if (c == '/' && next == '*') {
                while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/')) {
                    if (s.charAt(i) != '\n') out.setCharAt(i, ' ');
                    i++;
                }
                if (i < n) { out.setCharAt(i++, ' '); out.setCharAt(i++, ' '); }
            } else if (c == '"' && s.startsWith("\"\"\"", i)) {
                i += 3;
                while (i < n && !s.startsWith("\"\"\"", i)) {
                    if (s.charAt(i) == '\\') { out.setCharAt(i++, ' '); }
                    if (i < n && s.charAt(i) != '\n') out.setCharAt(i, ' ');
                    i++;
                }
                i += 3;
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < n && s.charAt(i) != c && s.charAt(i) != '\n') {
                    if (s.charAt(i) == '\\' && i + 1 < n) { out.setCharAt(i++, ' '); }
                    out.setCharAt(i++, ' ');
                }
                i++;
            } else {
                i++;
            }
        }
        return out.toString();
    }
}
