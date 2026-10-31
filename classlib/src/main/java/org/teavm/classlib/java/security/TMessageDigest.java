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
package org.teavm.classlib.java.security;

import java.nio.ByteBuffer;
import java.util.Objects;
import org.teavm.classlib.impl.security.DigestEngine;
import org.teavm.classlib.impl.security.Digests;

public abstract class TMessageDigest extends TMessageDigestSpi {
    private static final int INITIAL = 0;
    private static final int IN_PROGRESS = 1;

    private final String algorithm;
    private int state = INITIAL;

    protected TMessageDigest(String algorithm) {
        this.algorithm = algorithm;
    }

    public static TMessageDigest getInstance(String algorithm) throws TNoSuchAlgorithmException {
        Objects.requireNonNull(algorithm, "null algorithm name");
        DigestEngine engine = Digests.create(algorithm);
        if (engine == null) {
            throw new TNoSuchAlgorithmException(algorithm + " MessageDigest not available");
        }
        return new Delegate(algorithm, engine);
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public final int getDigestLength() {
        return engineGetDigestLength();
    }

    public void update(byte input) {
        engineUpdate(input);
        state = IN_PROGRESS;
    }

    public void update(byte[] input, int offset, int len) {
        if (input == null) {
            throw new IllegalArgumentException("No input buffer given");
        }
        if (input.length - offset < len) {
            throw new IllegalArgumentException("Input buffer too short");
        }
        engineUpdate(input, offset, len);
        state = IN_PROGRESS;
    }

    public void update(byte[] input) {
        engineUpdate(input, 0, input.length);
        state = IN_PROGRESS;
    }

    public final void update(ByteBuffer input) {
        if (input == null) {
            throw new NullPointerException();
        }
        engineUpdate(input);
        state = IN_PROGRESS;
    }

    public byte[] digest() {
        byte[] result = engineDigest();
        state = INITIAL;
        return result;
    }

    public int digest(byte[] buf, int offset, int len) throws TDigestException {
        if (buf == null) {
            throw new IllegalArgumentException("No output buffer given");
        }
        if (buf.length - offset < len) {
            throw new IllegalArgumentException("Output buffer too small for specified offset and length");
        }
        int count = engineDigest(buf, offset, len);
        state = INITIAL;
        return count;
    }

    public byte[] digest(byte[] input) {
        update(input);
        return digest();
    }

    public void reset() {
        engineReset();
        state = INITIAL;
    }

    // As the JDK: constant time in the first array's length.
    public static boolean isEqual(byte[] digesta, byte[] digestb) {
        if (digesta == digestb) {
            return true;
        }
        if (digesta == null || digestb == null) {
            return false;
        }
        int lengthA = digesta.length;
        int lengthB = digestb.length;
        if (lengthB == 0) {
            return lengthA == 0;
        }
        int result = lengthA - lengthB;
        for (int i = 0; i < lengthA; ++i) {
            int indexB = ((i - lengthB) >>> 31) * i;
            result |= digesta[i] ^ digestb[indexB];
        }
        return result == 0;
    }

    @Override
    public String toString() {
        return algorithm + " Message Digest from SUN, " + (state == INITIAL ? "<initialized>" : "<in progress>")
                + System.lineSeparator();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    static final class Delegate extends TMessageDigest implements Cloneable {
        private DigestEngine engine;

        Delegate(String algorithm, DigestEngine engine) {
            super(algorithm);
            this.engine = engine;
        }

        @Override
        protected int engineGetDigestLength() {
            return engine.length();
        }

        @Override
        protected void engineUpdate(byte input) {
            engine.update(input);
        }

        @Override
        protected void engineUpdate(byte[] input, int offset, int len) {
            engine.update(input, offset, len);
        }

        @Override
        protected byte[] engineDigest() {
            return engine.digest();
        }

        @Override
        protected void engineReset() {
            engine.reset();
        }

        // As the SUN provider's DigestBase, which names the digest by its standard name.
        @Override
        protected int engineDigest(byte[] buf, int offset, int len) throws TDigestException {
            int length = engine.length();
            if (len < length) {
                throw new TDigestException("Length must be at least " + length + " for "
                        + Digests.standardName(getAlgorithm()) + "digests");
            }
            if (offset < 0 || len < 0 || offset > buf.length - len) {
                throw new TDigestException("Buffer too short to store digest");
            }
            byte[] digest = engine.digest();
            System.arraycopy(digest, 0, buf, offset, length);
            return length;
        }

        @Override
        public Object clone() {
            Delegate copy = new Delegate(getAlgorithm(), engine.copy());
            copy.copyState(this);
            return copy;
        }
    }

    final void copyState(TMessageDigest other) {
        state = other.state;
    }
}
