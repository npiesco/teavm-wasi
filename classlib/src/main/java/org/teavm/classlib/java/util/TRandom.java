/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.classlib.java.util;

import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.classlib.java.util.stream.TDoubleStream;
import org.teavm.classlib.java.util.stream.TIntStream;
import org.teavm.classlib.java.util.stream.TLongStream;
import org.teavm.classlib.java.util.stream.doubleimpl.TSimpleDoubleStreamImpl;
import org.teavm.classlib.java.util.stream.intimpl.TSimpleIntStreamImpl;
import org.teavm.classlib.java.util.stream.longimpl.TSimpleLongStreamImpl;

/**
 * The JDK's Random: a 48-bit linear congruential generator whose algorithms are part of its specification, so a
 * seeded instance produces the JDK's sequence. Bounded values and streams follow JDK 21's RandomSupport.
 */
public class TRandom extends TObject implements TSerializable {
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;
    private static final String BAD_BOUND = "bound must be positive";
    private static final String BAD_FLOATING_BOUND = "bound must be finite and positive";
    private static final String BAD_RANGE = "bound must be greater than origin";
    private static final String BAD_SIZE = "size must be non-negative";
    private static long seedUniquifier = 8682522807148012L;

    private long seed;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    public TRandom() {
        this(nextSeedUniquifier() ^ System.nanoTime());
    }

    public TRandom(long seed) {
        this.seed = initialScramble(seed);
    }

    private static long nextSeedUniquifier() {
        seedUniquifier *= 1181783497276652981L;
        return seedUniquifier;
    }

    private static long initialScramble(long seed) {
        return (seed ^ MULTIPLIER) & MASK;
    }

    public void setSeed(long seed) {
        this.seed = initialScramble(seed);
        haveNextNextGaussian = false;
    }

    protected int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    public void nextBytes(byte[] bytes) {
        int i = 0;
        int length = bytes.length;
        while (i < length) {
            int random = nextInt();
            for (int n = Math.min(length - i, 4); n-- > 0; random >>= 8) {
                bytes[i++] = (byte) random;
            }
        }
    }

    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException(BAD_BOUND);
        }
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            return (int) ((bound * (long) r) >> 31);
        }
        int u = r;
        r = u % bound;
        while (u - r + m < 0) {
            u = next(31);
            r = u % bound;
        }
        return r;
    }

    public int nextInt(int origin, int bound) {
        checkRange(origin, bound);
        return boundedNextInt(origin, bound);
    }

    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }

    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException(BAD_BOUND);
        }
        long m = bound - 1;
        long r = nextLong();
        if ((bound & m) == 0L) {
            return r & m;
        }
        long u = r >>> 1;
        r = u % bound;
        while (u + m - r < 0L) {
            u = nextLong() >>> 1;
            r = u % bound;
        }
        return r;
    }

    public long nextLong(long origin, long bound) {
        checkRange(origin, bound);
        return boundedNextLong(origin, bound);
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }

    public float nextFloat() {
        return next(24) / (float) (1 << 24);
    }

    public float nextFloat(float bound) {
        if (!(bound > 0.0f && bound < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException(BAD_FLOATING_BOUND);
        }
        float r = nextFloat() * bound;
        return r >= bound ? Math.nextDown(bound) : r;
    }

    public float nextFloat(float origin, float bound) {
        if (!(origin < bound && bound - origin < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException(BAD_RANGE);
        }
        float r = nextFloat() * (bound - origin) + origin;
        return r >= bound ? Math.nextDown(bound) : r;
    }

    public double nextDouble() {
        return (((long) next(26) << 27) + next(27)) * 0x1.0p-53;
    }

    public double nextDouble(double bound) {
        if (!(bound > 0.0 && bound < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException(BAD_FLOATING_BOUND);
        }
        double r = nextDouble() * bound;
        return r >= bound ? Math.nextDown(bound) : r;
    }

    public double nextDouble(double origin, double bound) {
        if (!(origin < bound && bound - origin < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException(BAD_RANGE);
        }
        return boundedNextDouble(origin, bound);
    }

    public double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }
        double v1;
        double v2;
        double s;
        do {
            v1 = 2 * nextDouble() - 1;
            v2 = 2 * nextDouble() - 1;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1 || s == 0);
        double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
        nextNextGaussian = v2 * multiplier;
        haveNextNextGaussian = true;
        return v1 * multiplier;
    }

    private static void checkRange(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException(BAD_RANGE);
        }
    }

    private static void checkRange(long origin, long bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException(BAD_RANGE);
        }
    }

    private static void checkRange(double origin, double bound) {
        if (!(origin < bound && bound - origin < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException(BAD_RANGE);
        }
    }

    private static void checkStreamSize(long size) {
        if (size < 0L) {
            throw new IllegalArgumentException(BAD_SIZE);
        }
    }

    private int boundedNextInt(int origin, int bound) {
        int r = nextInt();
        if (origin < bound) {
            int n = bound - origin;
            int m = n - 1;
            if ((n & m) == 0) {
                r = (r & m) + origin;
            } else if (n > 0) {
                int u = r >>> 1;
                r = u % n;
                while (u + m - r < 0) {
                    u = nextInt() >>> 1;
                    r = u % n;
                }
                r += origin;
            } else {
                while (r < origin || r >= bound) {
                    r = nextInt();
                }
            }
        }
        return r;
    }

    private long boundedNextLong(long origin, long bound) {
        long r = nextLong();
        if (origin < bound) {
            long n = bound - origin;
            long m = n - 1;
            if ((n & m) == 0L) {
                r = (r & m) + origin;
            } else if (n > 0L) {
                long u = r >>> 1;
                r = u % n;
                while (u + m - r < 0L) {
                    u = nextLong() >>> 1;
                    r = u % n;
                }
                r += origin;
            } else {
                while (r < origin || r >= bound) {
                    r = nextLong();
                }
            }
        }
        return r;
    }

    private double boundedNextDouble(double origin, double bound) {
        double r = nextDouble();
        if (origin < bound) {
            if (bound - origin < Double.POSITIVE_INFINITY) {
                r = r * (bound - origin) + origin;
            } else {
                double halfOrigin = 0.5 * origin;
                r = (r * (0.5 * bound - halfOrigin) + halfOrigin) * 2.0;
            }
            if (r >= bound) {
                r = Math.nextDown(bound);
            }
        }
        return r;
    }

    public TIntStream ints(long streamSize) {
        checkStreamSize(streamSize);
        return intStream(streamSize, Integer.MAX_VALUE, 0);
    }

    public TIntStream ints() {
        return intStream(-1, Integer.MAX_VALUE, 0);
    }

    public TIntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
        checkStreamSize(streamSize);
        checkRange(randomNumberOrigin, randomNumberBound);
        return intStream(streamSize, randomNumberOrigin, randomNumberBound);
    }

    public TIntStream ints(int randomNumberOrigin, int randomNumberBound) {
        checkRange(randomNumberOrigin, randomNumberBound);
        return intStream(-1, randomNumberOrigin, randomNumberBound);
    }

    // size < 0 means unlimited; origin >= bound means unbounded values.
    private TIntStream intStream(long size, int origin, int bound) {
        return new TSimpleIntStreamImpl() {
            private long remaining = size;

            @Override
            public boolean next(IntPredicate consumer) {
                while (remaining != 0) {
                    if (remaining > 0) {
                        --remaining;
                    }
                    if (!consumer.test(boundedNextInt(origin, bound))) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public TLongStream longs(long streamSize) {
        checkStreamSize(streamSize);
        return longStream(streamSize, Long.MAX_VALUE, 0L);
    }

    public TLongStream longs() {
        return longStream(-1, Long.MAX_VALUE, 0L);
    }

    public TLongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
        checkStreamSize(streamSize);
        checkRange(randomNumberOrigin, randomNumberBound);
        return longStream(streamSize, randomNumberOrigin, randomNumberBound);
    }

    public TLongStream longs(long randomNumberOrigin, long randomNumberBound) {
        checkRange(randomNumberOrigin, randomNumberBound);
        return longStream(-1, randomNumberOrigin, randomNumberBound);
    }

    private TLongStream longStream(long size, long origin, long bound) {
        return new TSimpleLongStreamImpl() {
            private long remaining = size;

            @Override
            public boolean next(LongPredicate consumer) {
                while (remaining != 0) {
                    if (remaining > 0) {
                        --remaining;
                    }
                    if (!consumer.test(boundedNextLong(origin, bound))) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public TDoubleStream doubles(long streamSize) {
        checkStreamSize(streamSize);
        return doubleStream(streamSize, Double.MAX_VALUE, 0.0);
    }

    public TDoubleStream doubles() {
        return doubleStream(-1, Double.MAX_VALUE, 0.0);
    }

    public TDoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) {
        checkStreamSize(streamSize);
        checkRange(randomNumberOrigin, randomNumberBound);
        return doubleStream(streamSize, randomNumberOrigin, randomNumberBound);
    }

    public TDoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
        checkRange(randomNumberOrigin, randomNumberBound);
        return doubleStream(-1, randomNumberOrigin, randomNumberBound);
    }

    private TDoubleStream doubleStream(long size, double origin, double bound) {
        return new TSimpleDoubleStreamImpl() {
            private long remaining = size;

            @Override
            public boolean next(DoublePredicate consumer) {
                while (remaining != 0) {
                    if (remaining > 0) {
                        --remaining;
                    }
                    if (!consumer.test(boundedNextDouble(origin, bound))) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
