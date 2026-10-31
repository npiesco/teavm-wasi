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
 * SHA3-224, SHA3-256, SHA3-384 and SHA3-512 as specified by FIPS 202: Keccak-f[1600] with the SHA-3 domain padding.
 */
public final class Sha3 extends DigestEngine {
    private static final long[] ROUND_CONSTANTS = {
        0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L, 0x000000000000808BL,
        0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L, 0x000000000000008AL, 0x0000000000000088L,
        0x0000000080008009L, 0x000000008000000AL, 0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L,
        0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
        0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
    };
    // Rotation offsets by lane index x + 5y.
    private static final int[] ROTATIONS = {
        0, 1, 62, 28, 27, 36, 44, 6, 55, 20, 3, 10, 43, 25, 39, 41, 45, 15, 21, 8, 18, 2, 61, 56, 14
    };

    private final int length;
    private final int rate;
    private final long[] lanes = new long[25];
    private final long[] work = new long[25];
    private final long[] columns = new long[5];
    private final byte[] buffer;
    private int bufferLength;

    public Sha3(int length) {
        this.length = length;
        this.rate = 200 - 2 * length;
        buffer = new byte[rate];
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public void update(byte input) {
        buffer[bufferLength++] = input;
        if (bufferLength == rate) {
            absorb();
        }
    }

    @Override
    public byte[] digest() {
        for (int i = bufferLength; i < rate; ++i) {
            buffer[i] = 0;
        }
        buffer[bufferLength] ^= 0x06;
        buffer[rate - 1] ^= (byte) 0x80;
        absorb();
        byte[] result = new byte[length];
        for (int i = 0; i < length; ++i) {
            result[i] = (byte) (lanes[i / 8] >>> (8 * (i % 8)));
        }
        reset();
        return result;
    }

    @Override
    public void reset() {
        for (int i = 0; i < 25; ++i) {
            lanes[i] = 0;
        }
        bufferLength = 0;
    }

    private void absorb() {
        for (int i = 0; i < rate / 8; ++i) {
            long value = 0;
            for (int j = 7; j >= 0; --j) {
                value = (value << 8) | (buffer[i * 8 + j] & 0xFF);
            }
            lanes[i] ^= value;
        }
        bufferLength = 0;
        permute();
    }

    private void permute() {
        for (int round = 0; round < 24; ++round) {
            for (int x = 0; x < 5; ++x) {
                columns[x] = lanes[x] ^ lanes[x + 5] ^ lanes[x + 10] ^ lanes[x + 15] ^ lanes[x + 20];
            }
            for (int x = 0; x < 5; ++x) {
                long d = columns[(x + 4) % 5] ^ Long.rotateLeft(columns[(x + 1) % 5], 1);
                for (int y = 0; y < 25; y += 5) {
                    lanes[x + y] ^= d;
                }
            }
            for (int x = 0; x < 5; ++x) {
                for (int y = 0; y < 5; ++y) {
                    work[y + 5 * ((2 * x + 3 * y) % 5)] = Long.rotateLeft(lanes[x + 5 * y], ROTATIONS[x + 5 * y]);
                }
            }
            for (int x = 0; x < 5; ++x) {
                for (int y = 0; y < 25; y += 5) {
                    lanes[x + y] = work[x + y] ^ (~work[(x + 1) % 5 + y] & work[(x + 2) % 5 + y]);
                }
            }
            lanes[0] ^= ROUND_CONSTANTS[round];
        }
    }

    @Override
    public Sha3 copy() {
        Sha3 copy = new Sha3(length);
        System.arraycopy(lanes, 0, copy.lanes, 0, 25);
        System.arraycopy(buffer, 0, copy.buffer, 0, rate);
        copy.bufferLength = bufferLength;
        return copy;
    }
}
