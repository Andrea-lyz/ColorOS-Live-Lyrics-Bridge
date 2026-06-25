/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.lockscreenlyrics;

import android.text.TextUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

final class QqQrcDecrypter {
    private static final int DECRYPT = 0;
    private static final byte[] QQ_KEY =
            "!@#)(*$%123ZXC!@!@#)(NHL".getBytes(StandardCharsets.US_ASCII);

    private static final int[] BOX_1 = {
            14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
            0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
            15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13
    };
    private static final int[] BOX_2 = {
            15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
            3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
            13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9
    };
    private static final int[] BOX_3 = {
            10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
            13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
            1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12
    };
    private static final int[] BOX_4 = {
            7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
            13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
            3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14
    };
    private static final int[] BOX_5 = {
            2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
            14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
            11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3
    };
    private static final int[] BOX_6 = {
            12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
            10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
            4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13
    };
    private static final int[] BOX_7 = {
            4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
            13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
            6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12
    };
    private static final int[] BOX_8 = {
            13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
            1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
            2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11
    };

    private static final int[] KEY_RND_SHIFT =
            {1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1};
    private static final int[] KEY_PERM_C = {
            56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17,
            9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35
    };
    private static final int[] KEY_PERM_D = {
            62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21,
            13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3
    };
    private static final int[] KEY_COMPRESSION = {
            13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18,
            11, 3, 25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36,
            46, 54, 29, 39, 50, 44, 32, 47, 43, 48, 38, 55, 33,
            52, 45, 41, 49, 35, 28, 31
    };
    private static final int[] INITIAL_PERMUTATION = {
            57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
            61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7,
            56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2,
            60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6
    };
    private static final byte[][][] DECRYPT_SCHEDULE = new byte[3][16][6];

    static {
        tripleDesKeySetup(QQ_KEY, DECRYPT_SCHEDULE, DECRYPT);
    }

    private QqQrcDecrypter() {
    }

    static String decrypt(String encrypted) throws Exception {
        if (TextUtils.isEmpty(encrypted)) {
            return "";
        }
        String normalized = encrypted.trim();
        if (!isHexString(normalized)) {
            return normalized;
        }

        byte[] encryptedBytes = hexStringToByteArray(normalized);
        byte[] decryptedData = new byte[encryptedBytes.length];
        byte[] temp = new byte[8];
        byte[] inputBlock = new byte[8];
        for (int i = 0; i < encryptedBytes.length; i += 8) {
            int blockSize = Math.min(8, encryptedBytes.length - i);
            System.arraycopy(encryptedBytes, i, inputBlock, 0, blockSize);
            tripleDesCrypt(inputBlock, temp, DECRYPT_SCHEDULE);
            System.arraycopy(temp, 0, decryptedData, i, blockSize);
        }
        return decompress(decryptedData);
    }

    private static void tripleDesKeySetup(byte[] key, byte[][][] schedule, int mode) {
        keySchedule(key, 0, schedule[2], DECRYPT);
        keySchedule(key, 8, schedule[1], 1);
        keySchedule(key, 16, schedule[0], DECRYPT);
    }

    private static void tripleDesCrypt(byte[] input, byte[] output, byte[][][] key) {
        crypt(input, output, key[0]);
        crypt(output, output, key[1]);
        crypt(output, output, key[2]);
    }

    private static void keySchedule(byte[] key, int offset, byte[][] schedule, int mode) {
        int c = 0;
        int d = 0;
        for (int i = 0; i < 28; i++) {
            c |= getBitFromByteArray(key, KEY_PERM_C[i] + offset * 8, 31 - i);
            d |= getBitFromByteArray(key, KEY_PERM_D[i] + offset * 8, 31 - i);
        }
        for (int i = 0; i < 16; i++) {
            c = ((c << KEY_RND_SHIFT[i]) | (c >>> (28 - KEY_RND_SHIFT[i]))) & -0x10;
            d = ((d << KEY_RND_SHIFT[i]) | (d >>> (28 - KEY_RND_SHIFT[i]))) & -0x10;
            int toGen = mode == DECRYPT ? 15 - i : i;
            Arrays.fill(schedule[toGen], (byte) 0);
            for (int k = 0; k < 24; k++) {
                schedule[toGen][k / 8] = (byte) ((schedule[toGen][k / 8] & 0xFF)
                        | getBitFromIntR(c, KEY_COMPRESSION[k], 7 - (k % 8)));
            }
            for (int k = 24; k < 48; k++) {
                schedule[toGen][k / 8] = (byte) ((schedule[toGen][k / 8] & 0xFF)
                        | getBitFromIntR(d, KEY_COMPRESSION[k] - 27, 7 - (k % 8)));
            }
        }
    }

    private static void initialPermutation(int[] state, byte[] input) {
        state[0] = 0;
        state[1] = 0;
        for (int i = 0; i < 32; i++) {
            state[0] |= getBitFromByteArray(input, INITIAL_PERMUTATION[i], 31 - i);
            state[1] |= getBitFromByteArray(input, INITIAL_PERMUTATION[i + 32], 31 - i);
        }
    }

    private static void inverseInitialPermutation(int[] state, byte[] output) {
        int[] outIndices = {3, 2, 1, 0, 7, 6, 5, 4};
        int[] bitOffsets = {7, 6, 5, 4, 3, 2, 1, 0};
        for (int i = 0; i < 8; i++) {
            output[outIndices[i]] = (byte) (
                    getBitFromIntR(state[1], bitOffsets[i], 7)
                            | getBitFromIntR(state[0], bitOffsets[i], 6)
                            | getBitFromIntR(state[1], bitOffsets[i] + 8, 5)
                            | getBitFromIntR(state[0], bitOffsets[i] + 8, 4)
                            | getBitFromIntR(state[1], bitOffsets[i] + 16, 3)
                            | getBitFromIntR(state[0], bitOffsets[i] + 16, 2)
                            | getBitFromIntR(state[1], bitOffsets[i] + 24, 1)
                            | getBitFromIntR(state[0], bitOffsets[i] + 24, 0));
        }
    }

    private static int f(int stateIn, byte[] key) {
        int t1 = getBitFromIntL(stateIn, 31, 0)
                | ((stateIn & 0xF0000000) >>> 1)
                | getBitFromIntL(stateIn, 4, 5)
                | getBitFromIntL(stateIn, 3, 6)
                | ((stateIn & 0x0F000000) >>> 3)
                | getBitFromIntL(stateIn, 8, 11)
                | getBitFromIntL(stateIn, 7, 12)
                | ((stateIn & 0x00F00000) >>> 5)
                | getBitFromIntL(stateIn, 12, 17)
                | getBitFromIntL(stateIn, 11, 18)
                | ((stateIn & 0x000F0000) >>> 7)
                | getBitFromIntL(stateIn, 16, 23);

        int t2 = getBitFromIntL(stateIn, 15, 0)
                | ((stateIn & 0x0000F000) << 15)
                | getBitFromIntL(stateIn, 20, 5)
                | getBitFromIntL(stateIn, 19, 6)
                | ((stateIn & 0x00000F00) << 13)
                | getBitFromIntL(stateIn, 24, 11)
                | getBitFromIntL(stateIn, 23, 12)
                | ((stateIn & 0x000000F0) << 11)
                | getBitFromIntL(stateIn, 28, 17)
                | getBitFromIntL(stateIn, 27, 18)
                | ((stateIn & 0x0000000F) << 9)
                | getBitFromIntL(stateIn, 0, 23);

        int x0 = ((t1 >>> 24) & 0xFF) ^ (key[0] & 0xFF);
        int x1 = ((t1 >>> 16) & 0xFF) ^ (key[1] & 0xFF);
        int x2 = ((t1 >>> 8) & 0xFF) ^ (key[2] & 0xFF);
        int x3 = ((t2 >>> 24) & 0xFF) ^ (key[3] & 0xFF);
        int x4 = ((t2 >>> 16) & 0xFF) ^ (key[4] & 0xFF);
        int x5 = ((t2 >>> 8) & 0xFF) ^ (key[5] & 0xFF);

        int state = (BOX_1[formatSBoxInput(x0 >>> 2)] << 28)
                | (BOX_2[formatSBoxInput(((x0 & 0x03) << 4) | (x1 >>> 4))] << 24)
                | (BOX_3[formatSBoxInput(((x1 & 0x0F) << 2) | (x2 >>> 6))] << 20)
                | (BOX_4[formatSBoxInput(x2 & 0x3F)] << 16)
                | (BOX_5[formatSBoxInput(x3 >>> 2)] << 12)
                | (BOX_6[formatSBoxInput(((x3 & 0x03) << 4) | (x4 >>> 4))] << 8)
                | (BOX_7[formatSBoxInput(((x4 & 0x0F) << 2) | (x5 >>> 6))] << 4)
                | BOX_8[formatSBoxInput(x5 & 0x3F)];

        return getBitFromIntL(state, 15, 0)
                | getBitFromIntL(state, 6, 1)
                | getBitFromIntL(state, 19, 2)
                | getBitFromIntL(state, 20, 3)
                | getBitFromIntL(state, 28, 4)
                | getBitFromIntL(state, 11, 5)
                | getBitFromIntL(state, 27, 6)
                | getBitFromIntL(state, 16, 7)
                | getBitFromIntL(state, 0, 8)
                | getBitFromIntL(state, 14, 9)
                | getBitFromIntL(state, 22, 10)
                | getBitFromIntL(state, 25, 11)
                | getBitFromIntL(state, 4, 12)
                | getBitFromIntL(state, 17, 13)
                | getBitFromIntL(state, 30, 14)
                | getBitFromIntL(state, 9, 15)
                | getBitFromIntL(state, 1, 16)
                | getBitFromIntL(state, 7, 17)
                | getBitFromIntL(state, 23, 18)
                | getBitFromIntL(state, 13, 19)
                | getBitFromIntL(state, 31, 20)
                | getBitFromIntL(state, 26, 21)
                | getBitFromIntL(state, 2, 22)
                | getBitFromIntL(state, 8, 23)
                | getBitFromIntL(state, 18, 24)
                | getBitFromIntL(state, 12, 25)
                | getBitFromIntL(state, 29, 26)
                | getBitFromIntL(state, 5, 27)
                | getBitFromIntL(state, 21, 28)
                | getBitFromIntL(state, 10, 29)
                | getBitFromIntL(state, 3, 30)
                | getBitFromIntL(state, 24, 31);
    }

    private static void crypt(byte[] input, byte[] output, byte[][] key) {
        int[] state = new int[2];
        initialPermutation(state, input);
        for (int idx = 0; idx < 15; idx++) {
            int temp = state[1];
            state[1] = f(state[1], key[idx]) ^ state[0];
            state[0] = temp;
        }
        state[0] = f(state[1], key[15]) ^ state[0];
        inverseInitialPermutation(state, output);
    }

    private static int getBitFromByteArray(byte[] a, int b, int c) {
        int byteVal = a[(b / 32 * 4 + 3 - b % 32 / 8)] & 0xFF;
        return ((byteVal >>> (7 - (b % 8))) & 0x01) << c;
    }

    private static int getBitFromIntR(int a, int b, int c) {
        return ((a >>> (31 - b)) & 0x01) << c;
    }

    private static int getBitFromIntL(int a, int b, int c) {
        return ((a << b) & 0x80000000) >>> c;
    }

    private static int formatSBoxInput(int a) {
        return (a & 0x20) | ((a & 0x1F) >>> 1) | ((a & 0x01) << 4);
    }

    private static String decompress(byte[] data) throws Exception {
        try (InflaterInputStream input =
                     new InflaterInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static byte[] hexStringToByteArray(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static boolean isHexString(String input) {
        if (TextUtils.isEmpty(input) || input.length() % 2 != 0) {
            return false;
        }
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            boolean valid = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F');
            if (!valid) {
                return false;
            }
        }
        return true;
    }
}
