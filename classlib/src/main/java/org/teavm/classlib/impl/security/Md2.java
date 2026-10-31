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
 * MD2 as specified by RFC 1319.
 */
public final class Md2 extends DigestEngine {
    // The RFC 1319 substitution table, a permutation of 0..255 derived from the digits of pi.
    private static final int[] S = {
        41, 46, 67, 201, 162, 216, 124, 1, 61, 54, 84, 161, 236, 240, 6, 19, 98, 167, 5, 243, 192, 199, 115, 140,
        152, 147, 43, 217, 188, 76, 130, 202, 30, 155, 87, 60, 253, 212, 224, 22, 103, 66, 111, 24, 138, 23, 229, 18,
        190, 78, 196, 214, 218, 158, 222, 73, 160, 251, 245, 142, 187, 47, 238, 122, 169, 104, 121, 145, 21, 178, 7,
        63, 148, 194, 16, 137, 11, 34, 95, 33, 128, 127, 93, 154, 90, 144, 50, 39, 53, 62, 204, 231, 191, 247, 151,
        3, 255, 25, 48, 179, 72, 165, 181, 209, 215, 94, 146, 42, 172, 86, 170, 198, 79, 184, 56, 210, 150, 164, 125,
        182, 118, 252, 107, 226, 156, 116, 4, 241, 69, 157, 112, 89, 100, 113, 135, 32, 134, 91, 207, 101, 230, 45,
        168, 2, 27, 96, 37, 173, 174, 176, 185, 246, 28, 70, 97, 105, 52, 64, 126, 15, 85, 71, 163, 35, 221, 81, 175,
        58, 195, 92, 249, 206, 186, 197, 234, 38, 44, 83, 13, 110, 133, 40, 132, 9, 211, 223, 205, 244, 65, 129, 77,
        82, 106, 220, 55, 200, 108, 193, 171, 250, 36, 225, 123, 8, 12, 189, 177, 74, 120, 136, 149, 139, 227, 99,
        232, 109, 233, 203, 213, 254, 59, 0, 29, 57, 242, 239, 183, 14, 102, 88, 208, 228, 166, 119, 114, 248, 235,
        117, 75, 10, 49, 68, 80, 180, 143, 237, 31, 26, 219, 153, 141, 51, 159, 17, 131, 20
    };

    private final int[] state = new int[48];
    private final int[] checksum = new int[16];
    private final byte[] buffer = new byte[16];
    private int bufferLength;

    @Override
    public int length() {
        return 16;
    }

    @Override
    public void update(byte input) {
        buffer[bufferLength++] = input;
        if (bufferLength == 16) {
            process(buffer);
            bufferLength = 0;
        }
    }

    private void process(byte[] data) {
        int last = checksum[15];
        for (int j = 0; j < 16; ++j) {
            checksum[j] ^= S[(data[j] & 0xFF) ^ last];
            last = checksum[j];
        }
        for (int j = 0; j < 16; ++j) {
            state[16 + j] = data[j] & 0xFF;
            state[32 + j] = state[16 + j] ^ state[j];
        }
        int t = 0;
        for (int j = 0; j < 18; ++j) {
            for (int k = 0; k < 48; ++k) {
                state[k] ^= S[t];
                t = state[k];
            }
            t = (t + j) & 0xFF;
        }
    }

    @Override
    public byte[] digest() {
        int pad = 16 - bufferLength;
        for (int i = 0; i < pad; ++i) {
            update((byte) pad);
        }
        byte[] check = new byte[16];
        for (int i = 0; i < 16; ++i) {
            check[i] = (byte) checksum[i];
        }
        process(check);
        byte[] result = new byte[16];
        for (int i = 0; i < 16; ++i) {
            result[i] = (byte) state[i];
        }
        reset();
        return result;
    }

    @Override
    public void reset() {
        for (int i = 0; i < 48; ++i) {
            state[i] = 0;
        }
        for (int i = 0; i < 16; ++i) {
            checksum[i] = 0;
        }
        bufferLength = 0;
    }

    @Override
    public Md2 copy() {
        Md2 copy = new Md2();
        System.arraycopy(state, 0, copy.state, 0, 48);
        System.arraycopy(checksum, 0, copy.checksum, 0, 16);
        System.arraycopy(buffer, 0, copy.buffer, 0, 16);
        copy.bufferLength = bufferLength;
        return copy;
    }
}
