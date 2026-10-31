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

import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.impl.security.Sha1;
import org.teavm.classlib.java.util.TRandom;
import org.teavm.interop.Address;
import org.teavm.interop.wasi.Wasi;

/**
 * SecureRandom with the JDK's SUN algorithms: NativePRNG (the default, from the platform's random source, which a
 * seed only supplements) and SHA1PRNG, which is deterministic when seeded before first use.
 */
public class TSecureRandom extends TRandom {
    private static final int DIGEST_SIZE = 20;

    private final String algorithm;
    private final boolean sha1Prng;
    private Sha1 digest;
    private byte[] state;
    private byte[] remainder;
    private int remainderCount;

    public TSecureRandom() {
        this("NativePRNG", false);
    }

    public TSecureRandom(byte[] seed) {
        this("NativePRNG", false);
        setSeed(seed);
    }

    private TSecureRandom(String algorithm, boolean sha1Prng) {
        super(0L);
        this.algorithm = algorithm;
        this.sha1Prng = sha1Prng;
        if (sha1Prng) {
            digest = new Sha1();
        }
    }

    public static TSecureRandom getInstance(String algorithm) throws TNoSuchAlgorithmException {
        if (algorithm == null) {
            throw new NullPointerException("null algorithm name");
        }
        switch (algorithm.toUpperCase()) {
            case "SHA1PRNG":
                return new TSecureRandom(algorithm, true);
            case "NATIVEPRNG":
            case "NATIVEPRNGBLOCKING":
            case "NATIVEPRNGNONBLOCKING":
            case "DRBG":
                return new TSecureRandom(algorithm, false);
            default:
                throw new TNoSuchAlgorithmException(algorithm + " SecureRandom not available");
        }
    }

    public static TSecureRandom getInstanceStrong() {
        return new TSecureRandom("NativePRNGBlocking", false);
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setSeed(byte[] seed) {
        if (!sha1Prng) {
            return;
        }
        if (state != null) {
            digest.update(state, 0, state.length);
            for (int i = 0; i < state.length; ++i) {
                state[i] = 0;
            }
        }
        digest.update(seed, 0, seed.length);
        state = digest.digest();
        remainderCount = 0;
    }

    @Override
    public void setSeed(long seed) {
        // The Random constructor passes 0, which the JDK ignores as well.
        if (seed != 0L && algorithm != null) {
            // Least significant byte first, as SecureRandom.longToByteArray.
            byte[] bytes = new byte[8];
            for (int i = 0; i < 8; ++i) {
                bytes[i] = (byte) (seed >> (8 * i));
            }
            setSeed(bytes);
        }
    }

    @Override
    public void nextBytes(byte[] bytes) {
        if (sha1Prng) {
            sha1NextBytes(bytes);
        } else {
            platformBytes(bytes);
        }
    }

    public byte[] generateSeed(int numBytes) {
        if (numBytes < 0) {
            throw new IllegalArgumentException("numBytes cannot be negative");
        }
        byte[] seed = new byte[numBytes];
        platformBytes(seed);
        return seed;
    }

    public static byte[] getSeed(int numBytes) {
        byte[] seed = new byte[numBytes];
        platformBytes(seed);
        return seed;
    }

    @Override
    protected final int next(int numBits) {
        int numBytes = (numBits + 7) / 8;
        byte[] bytes = new byte[numBytes];
        nextBytes(bytes);
        int next = 0;
        for (int i = 0; i < numBytes; ++i) {
            next = (next << 8) + (bytes[i] & 0xFF);
        }
        return next >>> (numBytes * 8 - numBits);
    }

    private static void platformBytes(byte[] bytes) {
        if (bytes.length == 0) {
            return;
        }
        if (PlatformDetector.isWebAssembly()) {
            short errno = Wasi.randomGet(Address.ofData(bytes), bytes.length);
            if (errno != Wasi.ERRNO_SUCCESS) {
                throw new IllegalStateException(Wasi.errnoMessage("random_get", errno));
            }
        } else {
            for (int i = 0; i < bytes.length; ++i) {
                bytes[i] = (byte) (Math.random() * 256);
            }
        }
    }

    // sun.security.provider.SecureRandom: the output is SHA-1 of the state, and the state advances by adding that
    // output plus one.
    private void sha1NextBytes(byte[] result) {
        if (state == null) {
            byte[] seed = new byte[DIGEST_SIZE];
            platformBytes(seed);
            digest.update(seed, 0, seed.length);
            state = digest.digest();
        }
        byte[] output = remainder;
        int index = 0;
        int used = remainderCount;
        if (used > 0) {
            int todo = Math.min(result.length - index, DIGEST_SIZE - used);
            for (int i = 0; i < todo; ++i) {
                result[i] = output[used];
                output[used++] = 0;
            }
            remainderCount += todo;
            index += todo;
        }
        while (index < result.length) {
            digest.update(state, 0, state.length);
            output = digest.digest();
            updateState(state, output);
            int todo = Math.min(result.length - index, DIGEST_SIZE);
            for (int i = 0; i < todo; ++i) {
                result[index++] = output[i];
                output[i] = 0;
            }
            remainderCount += todo;
        }
        remainder = output;
        remainderCount %= DIGEST_SIZE;
    }

    private static void updateState(byte[] state, byte[] output) {
        int last = 1;
        boolean changed = false;
        for (int i = 0; i < state.length; ++i) {
            int v = state[i] + output[i] + last;
            byte t = (byte) v;
            changed |= state[i] != t;
            state[i] = t;
            last = v >> 8;
        }
        if (!changed) {
            state[0]++;
        }
    }
}
