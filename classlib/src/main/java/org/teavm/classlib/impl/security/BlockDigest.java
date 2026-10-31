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
 * Merkle-Damgard padding shared by MD5, SHA-1 and SHA-2: 0x80, zeros, then the message length in bits in the last
 * bytes of the final block (8 bytes for 64-byte blocks, 16 for 128-byte blocks).
 */
abstract class BlockDigest extends DigestEngine {
    final byte[] block;
    int blockLength;
    long count;
    private final boolean bigEndian;

    BlockDigest(int blockSize, boolean bigEndian) {
        block = new byte[blockSize];
        this.bigEndian = bigEndian;
    }

    @Override
    public void update(byte input) {
        block[blockLength++] = input;
        count++;
        if (blockLength == block.length) {
            transform();
            blockLength = 0;
        }
    }

    @Override
    public byte[] digest() {
        long bits = count << 3;
        long highBits = count >>> 61;
        int lengthBytes = block.length / 8;
        update((byte) 0x80);
        while (blockLength != block.length - lengthBytes) {
            update((byte) 0);
        }
        if (bigEndian) {
            for (int i = lengthBytes - 1; i >= 8; --i) {
                update((byte) (i == 8 ? highBits : 0));
            }
            for (int i = 7; i >= 0; --i) {
                update((byte) (bits >>> (8 * i)));
            }
        } else {
            for (int i = 0; i < 8; ++i) {
                update((byte) (bits >>> (8 * i)));
            }
        }
        byte[] result = output();
        reset();
        return result;
    }

    @Override
    public void reset() {
        blockLength = 0;
        count = 0;
        resetState();
    }

    void copyBlockTo(BlockDigest other) {
        System.arraycopy(block, 0, other.block, 0, block.length);
        other.blockLength = blockLength;
        other.count = count;
    }

    abstract void resetState();

    abstract void transform();

    abstract byte[] output();
}
