/*
 *  Copyright 2026 spooler contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.impl.security;

/**
 * MD5 as specified by RFC 1321.
 */
public final class Md5 extends BlockDigest {
    private static final int[] SHIFTS = {
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    };
    // floor(abs(sin(i + 1)) * 2^32), as listed in RFC 1321.
    private static final int[] SINES = {
        0xD76AA478, 0xE8C7B756, 0x242070DB, 0xC1BDCEEE, 0xF57C0FAF, 0x4787C62A, 0xA8304613, 0xFD469501,
        0x698098D8, 0x8B44F7AF, 0xFFFF5BB1, 0x895CD7BE, 0x6B901122, 0xFD987193, 0xA679438E, 0x49B40821,
        0xF61E2562, 0xC040B340, 0x265E5A51, 0xE9B6C7AA, 0xD62F105D, 0x02441453, 0xD8A1E681, 0xE7D3FBC8,
        0x21E1CDE6, 0xC33707D6, 0xF4D50D87, 0x455A14ED, 0xA9E3E905, 0xFCEFA3F8, 0x676F02D9, 0x8D2A4C8A,
        0xFFFA3942, 0x8771F681, 0x6D9D6122, 0xFDE5380C, 0xA4BEEA44, 0x4BDECFA9, 0xF6BB4B60, 0xBEBFBC70,
        0x289B7EC6, 0xEAA127FA, 0xD4EF3085, 0x04881D05, 0xD9D4D039, 0xE6DB99E5, 0x1FA27CF8, 0xC4AC5665,
        0xF4292244, 0x432AFF97, 0xAB9423A7, 0xFC93A039, 0x655B59C3, 0x8F0CCC92, 0xFFEFF47D, 0x85845DD1,
        0x6FA87E4F, 0xFE2CE6E0, 0xA3014314, 0x4E0811A1, 0xF7537E82, 0xBD3AF235, 0x2AD7D2BB, 0xEB86D391
    };

    private final int[] state = new int[4];
    private final int[] words = new int[16];

    public Md5() {
        super(64, false);
        resetState();
    }

    @Override
    public int length() {
        return 16;
    }

    @Override
    void resetState() {
        state[0] = 0x67452301;
        state[1] = 0xEFCDAB89;
        state[2] = 0x98BADCFE;
        state[3] = 0x10325476;
    }

    @Override
    void transform() {
        for (int i = 0; i < 16; ++i) {
            words[i] = (block[i * 4] & 0xFF) | (block[i * 4 + 1] & 0xFF) << 8 | (block[i * 4 + 2] & 0xFF) << 16
                    | (block[i * 4 + 3] & 0xFF) << 24;
        }
        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        for (int i = 0; i < 64; ++i) {
            int f;
            int g;
            if (i < 16) {
                f = (b & c) | (~b & d);
                g = i;
            } else if (i < 32) {
                f = (d & b) | (~d & c);
                g = (5 * i + 1) & 15;
            } else if (i < 48) {
                f = b ^ c ^ d;
                g = (3 * i + 5) & 15;
            } else {
                f = c ^ (b | ~d);
                g = (7 * i) & 15;
            }
            int next = d;
            d = c;
            c = b;
            b = b + Integer.rotateLeft(a + f + SINES[i] + words[g], SHIFTS[i]);
            a = next;
        }
        state[0] += a;
        state[1] += b;
        state[2] += c;
        state[3] += d;
    }

    @Override
    byte[] output() {
        byte[] result = new byte[16];
        for (int i = 0; i < 16; ++i) {
            result[i] = (byte) (state[i / 4] >>> (8 * (i % 4)));
        }
        return result;
    }

    @Override
    public Md5 copy() {
        Md5 copy = new Md5();
        copyBlockTo(copy);
        System.arraycopy(state, 0, copy.state, 0, state.length);
        return copy;
    }
}
