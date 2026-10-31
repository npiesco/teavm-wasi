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
 * SHA-1 as specified by FIPS 180-4.
 */
public final class Sha1 extends BlockDigest {
    private final int[] state = new int[5];
    private final int[] words = new int[80];

    public Sha1() {
        super(64, true);
        resetState();
    }

    @Override
    public int length() {
        return 20;
    }

    @Override
    void resetState() {
        state[0] = 0x67452301;
        state[1] = 0xEFCDAB89;
        state[2] = 0x98BADCFE;
        state[3] = 0x10325476;
        state[4] = 0xC3D2E1F0;
    }

    @Override
    void transform() {
        for (int i = 0; i < 16; ++i) {
            words[i] = (block[i * 4] & 0xFF) << 24 | (block[i * 4 + 1] & 0xFF) << 16 | (block[i * 4 + 2] & 0xFF) << 8
                    | (block[i * 4 + 3] & 0xFF);
        }
        for (int i = 16; i < 80; ++i) {
            words[i] = Integer.rotateLeft(words[i - 3] ^ words[i - 8] ^ words[i - 14] ^ words[i - 16], 1);
        }
        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        int e = state[4];
        for (int i = 0; i < 80; ++i) {
            int f;
            int k;
            if (i < 20) {
                f = (b & c) | (~b & d);
                k = 0x5A827999;
            } else if (i < 40) {
                f = b ^ c ^ d;
                k = 0x6ED9EBA1;
            } else if (i < 60) {
                f = (b & c) | (b & d) | (c & d);
                k = 0x8F1BBCDC;
            } else {
                f = b ^ c ^ d;
                k = 0xCA62C1D6;
            }
            int temp = Integer.rotateLeft(a, 5) + f + e + k + words[i];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = temp;
        }
        state[0] += a;
        state[1] += b;
        state[2] += c;
        state[3] += d;
        state[4] += e;
    }

    @Override
    byte[] output() {
        byte[] result = new byte[20];
        for (int i = 0; i < 20; ++i) {
            result[i] = (byte) (state[i / 4] >>> (24 - 8 * (i % 4)));
        }
        return result;
    }

    @Override
    public Sha1 copy() {
        Sha1 copy = new Sha1();
        copyBlockTo(copy);
        System.arraycopy(state, 0, copy.state, 0, state.length);
        return copy;
    }
}
