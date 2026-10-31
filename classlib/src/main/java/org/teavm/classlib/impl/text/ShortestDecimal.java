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
package org.teavm.classlib.impl.text;

import java.math.BigInteger;

/**
 * The decimal that Double.toString and Float.toString (JDK 19 and later) render for a finite non-zero value v:
 * among the decimals that round to v (round half even), those of the minimal length n (also those of length 2 when
 * n is 1); of these the closest to v, or of two equally close the one whose last digit is even. Computed exactly.
 */
public final class ShortestDecimal {
    private static final BigInteger TEN = BigInteger.TEN;

    /** The digits, without trailing zeros. */
    public long digits;
    /** How many digits there are. */
    public int length;
    /** The decimal exponent of the first digit. */
    public int exponent;

    /**
     * v = significand * 2^binaryExponent, where significand has its implicit bit (if any); narrowLowerGap is set when
     * v is a power of two above the smallest normal, so the next smaller value is half an ulp closer.
     */
    public void compute(long significand, int binaryExponent, boolean narrowLowerGap) {
        boolean inclusive = (significand & 1) == 0;
        // In units of 2^(binaryExponent - 2): v = 4c, the bounds are halfway to the neighbouring values.
        BigInteger c4 = BigInteger.valueOf(significand).shiftLeft(2);
        BigInteger lower = c4.subtract(BigInteger.valueOf(narrowLowerGap ? 1 : 2));
        BigInteger upper = c4.add(BigInteger.valueOf(2));
        int shift = binaryExponent - 2;
        BigInteger numScale = shift > 0 ? BigInteger.ONE.shiftLeft(shift) : BigInteger.ONE;
        BigInteger den = shift < 0 ? BigInteger.ONE.shiftLeft(-shift) : BigInteger.ONE;
        BigInteger value = c4.multiply(numScale);
        lower = lower.multiply(numScale);
        upper = upper.multiply(numScale);

        int e = decimalExponent(value, den, significand, binaryExponent);
        int lo = 1;
        int hi = 17;
        long[] best = null;
        int bestScale = 0;
        int minimalLength = 17;
        while (lo <= hi) {
            int n = (lo + hi) >>> 1;
            long[] found = candidate(value, lower, upper, den, inclusive, e - n + 1);
            if (found != null) {
                best = found;
                bestScale = e - n + 1;
                minimalLength = n;
                hi = n - 1;
            } else {
                lo = n + 1;
            }
        }
        if (best == null) {
            // 17 significant digits always identify a double.
            bestScale = e - 16;
            best = candidate(value, lower, upper, den, inclusive, bestScale);
        }
        if (minimalLength == 1) {
            long[] two = candidate(value, lower, upper, den, inclusive, e - 1);
            if (two != null && closer(value, den, two[0], e - 1, best[0], bestScale)) {
                best = two;
                bestScale = e - 1;
            }
        }
        long m = best[0];
        int scale = bestScale;
        while (m % 10 == 0) {
            m /= 10;
            scale++;
        }
        digits = m;
        length = numberOfDigits(m);
        exponent = scale + length - 1;
    }

    // The decimal exponent of v = value / den: 10^e <= v < 10^(e+1).
    private static int decimalExponent(BigInteger value, BigInteger den, long significand, int binaryExponent) {
        int e = (int) Math.floor(Math.log10(significand) + binaryExponent * 0.30102999566398120);
        while (compareToPowerOfTen(value, den, e) < 0) {
            e--;
        }
        while (compareToPowerOfTen(value, den, e + 1) >= 0) {
            e++;
        }
        return e;
    }

    private static int compareToPowerOfTen(BigInteger value, BigInteger den, int e) {
        if (e >= 0) {
            return value.compareTo(den.multiply(TEN.pow(e)));
        } else {
            return value.multiply(TEN.pow(-e)).compareTo(den);
        }
    }

    // The integer m closest to v / 10^scale with m * 10^scale rounding to v, as {m}, or null when there is none.
    private static long[] candidate(BigInteger value, BigInteger lower, BigInteger upper, BigInteger den,
            boolean inclusive, int scale) {
        BigInteger num = scale < 0 ? TEN.pow(-scale) : BigInteger.ONE;
        BigInteger d = scale > 0 ? den.multiply(TEN.pow(scale)) : den;
        BigInteger[] lowQr = lower.multiply(num).divideAndRemainder(d);
        BigInteger[] highQr = upper.multiply(num).divideAndRemainder(d);
        BigInteger min = lowQr[1].signum() == 0 && inclusive ? lowQr[0] : lowQr[0].add(BigInteger.ONE);
        BigInteger max = highQr[1].signum() == 0 && !inclusive ? highQr[0].subtract(BigInteger.ONE) : highQr[0];
        if (min.compareTo(max) > 0) {
            return null;
        }
        BigInteger[] qr = value.multiply(num).divideAndRemainder(d);
        int half = qr[1].shiftLeft(1).compareTo(d);
        BigInteger nearest = qr[0];
        if (half > 0 || half == 0 && qr[0].testBit(0)) {
            nearest = nearest.add(BigInteger.ONE);
        }
        if (nearest.compareTo(min) < 0) {
            nearest = min;
        } else if (nearest.compareTo(max) > 0) {
            nearest = max;
        }
        return new long[] { nearest.longValue() };
    }

    // Whether a * 10^aScale is closer to v than b * 10^bScale, or as close with an even last digit.
    private static boolean closer(BigInteger value, BigInteger den, long a, int aScale, long b, int bScale) {
        int common = Math.min(aScale, bScale);
        BigInteger scaledValue = common < 0 ? value.multiply(TEN.pow(-common)) : value;
        BigInteger scaledDen = common > 0 ? den.multiply(TEN.pow(common)) : den;
        BigInteger da = BigInteger.valueOf(a).multiply(TEN.pow(aScale - common)).multiply(scaledDen)
                .subtract(scaledValue).abs();
        BigInteger db = BigInteger.valueOf(b).multiply(TEN.pow(bScale - common)).multiply(scaledDen)
                .subtract(scaledValue).abs();
        int cmp = da.compareTo(db);
        return cmp < 0 || cmp == 0 && a % 2 == 0 && b % 2 != 0;
    }

    private static int numberOfDigits(long m) {
        int n = 1;
        while (m >= 10) {
            m /= 10;
            n++;
        }
        return n;
    }
}
