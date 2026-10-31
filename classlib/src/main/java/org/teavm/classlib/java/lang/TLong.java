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
package org.teavm.classlib.java.lang;

import static org.teavm.classlib.impl.IntegerUtil.toUnsignedLogRadixString;
import org.teavm.backend.javascript.spi.GeneratedBy;
import org.teavm.classlib.impl.ParseSupport;
import org.teavm.interop.NoSideEffects;

public class TLong extends TNumber implements TComparable<TLong> {
    public static final long MIN_VALUE = -0x8000000000000000L;
    public static final long MAX_VALUE = 0x7FFFFFFFFFFFFFFFL;
    public static final Class<Long> TYPE = long.class;
    public static final int SIZE = 64;
    private long value;

    public TLong(long value) {
        this.value = value;
    }

    public TLong(String value) throws TNumberFormatException {
        this(parseLong(value));
    }

    public static TLong valueOf(long value) {
        return new TLong(value);
    }

    public static long parseLong(String s, int radix) throws TNumberFormatException {
        return ParseSupport.parseSigned(s, radix, MIN_VALUE, MAX_VALUE);
    }

    public static long parseLong(CharSequence s, int beginIndex, int endIndex, int radix)
            throws TNumberFormatException {
        return ParseSupport.parseSigned(s, beginIndex, endIndex, radix, MIN_VALUE, MAX_VALUE);
    }

    public static long parseUnsignedLong(String s, int radix) throws TNumberFormatException {
        return ParseSupport.parseUnsignedLong(s, radix);
    }

    public static long parseUnsignedLong(String s) throws TNumberFormatException {
        return parseUnsignedLong(s, 10);
    }

    public static long parseUnsignedLong(CharSequence s, int beginIndex, int endIndex, int radix)
            throws TNumberFormatException {
        return ParseSupport.parseUnsignedLong(s, beginIndex, endIndex, radix);
    }

    public static long parseLong(String s) throws TNumberFormatException {
        return parseLong(s, 10);
    }

    public static TLong valueOf(String s, int radix) throws TNumberFormatException {
        return valueOf(parseLong(s, radix));
    }

    public static TLong valueOf(String s) throws TNumberFormatException {
        return valueOf(parseLong(s));
    }

    public static TLong decode(String nm) throws TNumberFormatException {
        return valueOf(ParseSupport.decode(nm, MIN_VALUE, MAX_VALUE));
    }

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public float floatValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return value;
    }

    public static String toString(long i, int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            radix = 10;
        }
        return new TStringBuilder().insert(0, i, radix).toString();
    }

    public static String toHexString(long i) {
        return toUnsignedLogRadixString(i, 4);
    }

    public static String toOctalString(long i) {
        return toUnsignedLogRadixString(i, 3);
    }

    public static String toBinaryString(long i) {
        return toUnsignedLogRadixString(i, 1);
    }

    public static String toString(long value) {
        return new TStringBuilder().append(value).toString();
    }

    @Override
    public String toString() {
        return toString(value);
    }

    @Override
    public int hashCode() {
        return hashCode(value);
    }

    public static long sum(long a, long b) {
        return a + b;
    }

    public static long max(long a, long b) {
        return Math.max(a, b);
    }

    public static long min(long a, long b) {
        return Math.min(a, b);
    }

    public static int hashCode(long value) {
        return (int) value ^ (int) (value >>> 32);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TLong && ((TLong) other).value == value;
    }

    @NoSideEffects
    public static native int compare(long a, long b);

    @Override
    public int compareTo(TLong other) {
        return compare(value, other.value);
    }

    public static TLong getLong(String nm) {
        return getLong(nm, null);
    }

    public static TLong getLong(String nm, long val) {
        return getLong(nm, TLong.valueOf(val));
    }

    public static TLong getLong(String nm, TLong val) {
        String result = nm != null ? TSystem.getProperty(nm) : null;
        try {
            return result != null ? TLong.valueOf(result) : val;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long highestOneBit(long i) {
        return 0x8000000000000000L >>> numberOfLeadingZeros(i);
    }

    public static int numberOfLeadingZeros(long i) {
        if (i == 0) {
            return SIZE;
        }
        int n = 0;
        if (i >>> 32 != 0) {
            i >>>= 32;
            n |= 32;
        }
        if (i >>> 16 != 0) {
            i >>>= 16;
            n |= 16;
        }
        if (i >>> 8 != 0) {
            i >>>= 8;
            n |= 8;
        }
        if (i >>> 4 != 0) {
            i >>>= 4;
            n |= 4;
        }
        if (i >>> 2 != 0) {
            i >>>= 2;
            n |= 2;
        }
        if (i >>> 1 != 0) {
            i >>>= 1;
            n |= 1;
        }
        return SIZE - n - 1;
    }

    public static int numberOfTrailingZeros(long i) {
        if (i == 0) {
            return SIZE;
        }
        int n = 0;
        if (i << 32 != 0) {
            i <<= 32;
            n |= 32;
        }
        if (i << 16 != 0) {
            i <<= 16;
            n |= 16;
        }
        if (i << 8 != 0) {
            i <<= 8;
            n |= 8;
        }
        if (i << 4 != 0) {
            i <<= 4;
            n |= 4;
        }
        if (i << 2 != 0) {
            i <<= 2;
            n |= 2;
        }
        if (i << 1 != 0) {
            i <<= 1;
            n |= 1;
        }
        return SIZE - n - 1;
    }


    public static long lowestOneBit(long i) {
        return 1L << numberOfTrailingZeros(i);
    }

    public static int bitCount(long i) {
        i = ((i & 0xAAAAAAAAAAAAAAAAL) >>> 1)  + (i & 0x5555555555555555L);
        i = ((i & 0xCCCCCCCCCCCCCCCCL) >>> 2)  + (i & 0x3333333333333333L);
        i = ((i & 0x7070707070707070L) >>> 4)  + (i & 0x0707070707070707L);
        i = ((i & 0x0F000F000F000F00L) >>> 8)  + (i & 0x000F000F000F000FL);
        i = ((i & 0x001F0000001F0000L) >>> 16) + (i & 0x0000001F0000001FL);
        i = ((i & 0x0000003F00000000L) >>> 32) + (i & 0x000000000000003FL);
        return (int) i;
    }

    public static long rotateLeft(long i, int distance) {
        distance &= 0x3F;
        return (i << distance) | (i >>> (64 - distance));
    }

    public static long rotateRight(long i, int distance) {
        distance &= 0x3F;
        return (i >>> distance) | (i << (64 - distance));
    }

    public static long reverse(long i) {
        i = ((i & 0xAAAAAAAAAAAAAAAAL) >>> 1)  | ((i & 0x5555555555555555L) << 1);
        i = ((i & 0xCCCCCCCCCCCCCCCCL) >>> 2)  | ((i & 0x3333333333333333L) << 2);
        i = ((i & 0xF0F0F0F0F0F0F0F0L) >>> 4)  | ((i & 0x0F0F0F0F0F0F0F0FL) << 4);
        i = ((i & 0xFF00FF00FF00FF00L) >>> 8)  | ((i & 0x00FF00FF00FF00FFL) << 8);
        i = ((i & 0xFFFF0000FFFF0000L) >>> 16) | ((i & 0x0000FFFF0000FFFFL) << 16);
        i = ((i & 0xFFFFFFFF00000000L) >>> 32) | ((i & 0x00000000FFFFFFFFL) << 32);
        return i;
    }

    public static long reverseBytes(long i) {
        i = ((i & 0xFF00FF00FF00FF00L) >> 8)  | ((i & 0x00FF00FF00FF00FFL) << 8);
        i = ((i & 0xFFFF0000FFFF0000L) >> 16) | ((i & 0x0000FFFF0000FFFFL) << 16);
        i = (i >> 32) | (i << 32);
        return i;
    }

    public static int signum(long i) {
        return (int) ((i >> 63) | (-i >>> 63));
    }

    @GeneratedBy(LongNativeGenerator.class)
    @NoSideEffects
    public static native long divideUnsigned(long dividend, long divisor);

    @GeneratedBy(LongNativeGenerator.class)
    @NoSideEffects
    public static native long remainderUnsigned(long dividend, long divisor);

    public static int compareUnsigned(long x, long y) {
        return compare(x ^ MIN_VALUE, y ^ MIN_VALUE);
    }

    public static String toUnsignedString(long i) {
        return toUnsignedString(i, 10);
    }

    public static String toUnsignedString(long i, int radix) {
        if (i >= 0) {
            return toString(i, radix);
        }
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            radix = 10;
        }
        long quotient = divideUnsigned(i, radix);
        long remainder = i - quotient * radix;
        return toString(quotient, radix) + Character.forDigit((int) remainder, radix);
    }
}
