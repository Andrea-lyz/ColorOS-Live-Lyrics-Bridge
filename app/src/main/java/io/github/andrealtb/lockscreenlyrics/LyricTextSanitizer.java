package io.github.andrealtb.lockscreenlyrics;

final class LyricTextSanitizer {
    static final int ZERO_WIDTH_SPACE_CODE_POINT = 0x200B;
    static final int WORD_JOINER_CODE_POINT = 0x2060;
    static final int BYTE_ORDER_MARK_CODE_POINT = 0xFEFF;
    static final String ZERO_WIDTH_SPACE = codePointString(ZERO_WIDTH_SPACE_CODE_POINT);

    private LyricTextSanitizer() {
    }

    static String removeIgnorableCharacters(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder cleaned = null;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!isIgnorableCharacter(character)) {
                if (cleaned != null) {
                    cleaned.append(character);
                }
                continue;
            }
            if (cleaned == null) {
                cleaned = new StringBuilder(value.length());
                cleaned.append(value, 0, index);
            }
        }
        return cleaned == null ? value : cleaned.toString();
    }

    static boolean isIgnorableCharacter(char character) {
        return character == ZERO_WIDTH_SPACE_CODE_POINT
                || character == WORD_JOINER_CODE_POINT
                || character == BYTE_ORDER_MARK_CODE_POINT;
    }

    static String codePointString(int codePoint) {
        return new String(Character.toChars(codePoint));
    }
}
