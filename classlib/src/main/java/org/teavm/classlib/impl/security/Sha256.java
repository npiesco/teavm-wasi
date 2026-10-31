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
 * SHA-224 and SHA-256 as specified by FIPS 180-4.
 */
public final class Sha256 extends BlockDigest {
    private static final int[] K = {
        0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5, 0xD807AA98,
        0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174, 0xE49B69C1, 0xEFBE4786,
        0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA, 0x983E5152, 0xA831C66D, 0xB00327C8,
        0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967, 0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13,
        0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85, 0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819,
        0xD6990624, 0xF40E3585, 0x106AA070, 0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A,
        0x5B9CCA4F, 0x682E6FF3, 0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7,
        0xC67178F2
    };
    private static final int[] IV256 = {
        0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19
    };
    private static final int[] IV224 = {
        0xC1059ED8, 0x367CD507, 0x3070DD17, 0xF70E5939, 0xFFC00B31, 0x68581511, 0x64F98FA7, 0xBEFA4FA4
    };

    private final int length;
    private final int[] state = new int[8];
    private final int[] words = new int[64];

    public Sha256(int length) {
        super(64, true);
        this.length = length;
        resetState();
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    void resetState() {
        System.arraycopy(length == 28 ? IV224 : IV256, 0, state, 0, 8);
    }

    @Override
    void transform() {
        for (int i = 0; i < 16; ++i) {
            words[i] = (block[i * 4] & 0xFF) << 24 | (block[i * 4 + 1] & 0xFF) << 16 | (block[i * 4 + 2] & 0xFF) << 8
                    | (block[i * 4 + 3] & 0xFF);
        }
        for (int i = 16; i < 64; ++i) {
            int s0 = Integer.rotateRight(words[i - 15], 7) ^ Integer.rotateRight(words[i - 15], 18)
                    ^ (words[i - 15] >>> 3);
            int s1 = Integer.rotateRight(words[i - 2], 17) ^ Integer.rotateRight(words[i - 2], 19)
                    ^ (words[i - 2] >>> 10);
            words[i] = words[i - 16] + s0 + words[i - 7] + s1;
        }
        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        int e = state[4];
        int f = state[5];
        int g = state[6];
        int h = state[7];
        for (int i = 0; i < 64; ++i) {
            int s1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
            int choice = (e & f) ^ (~e & g);
            int temp1 = h + s1 + choice + K[i] + words[i];
            int s0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
            int majority = (a & b) ^ (a & c) ^ (b & c);
            int temp2 = s0 + majority;
            h = g;
            g = f;
            f = e;
            e = d + temp1;
            d = c;
            c = b;
            b = a;
            a = temp1 + temp2;
        }
        state[0] += a;
        state[1] += b;
        state[2] += c;
        state[3] += d;
        state[4] += e;
        state[5] += f;
        state[6] += g;
        state[7] += h;
    }

    @Override
    byte[] output() {
        byte[] result = new byte[length];
        for (int i = 0; i < length; ++i) {
            result[i] = (byte) (state[i / 4] >>> (24 - 8 * (i % 4)));
        }
        return result;
    }

    @Override
    public Sha256 copy() {
        Sha256 copy = new Sha256(length);
        copyBlockTo(copy);
        System.arraycopy(state, 0, copy.state, 0, state.length);
        return copy;
    }
}
