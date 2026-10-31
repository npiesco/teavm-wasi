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
 * Double.parseDouble and Float.parseFloat: the JDK's syntax (surrounding whitespace, an optional sign, NaN, Infinity,
 * decimal and hexadecimal significands with exponents, an optional f/F/d/D suffix) and its result, the value nearest
 * to the exact one (ties to even), computed exactly; a float is rounded once, directly from the decimal.
 */
public final class FloatingDecimalParser {
    private static final double[] POWERS_OF_TEN = { 1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
            1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22 };
    private static final float[] FLOAT_POWERS_OF_TEN = { 1e0f, 1e1f, 1e2f, 1e3f, 1e4f, 1e5f, 1e6f, 1e7f, 1e8f, 1e9f,
            1e10f };

    private FloatingDecimalParser() {
    }

    public static double parseDouble(String s) {
        return parse(s, false);
    }

    public static float parseFloat(String s) {
        return (float) parse(s, true);
    }

    private static double parse(String s, boolean single) {
        String in = s.trim();
        if (in.isEmpty()) {
            throw new NumberFormatException("empty String");
        }
        int i = 0;
        boolean negative = false;
        char first = in.charAt(0);
        if (first == '-' || first == '+') {
            negative = first == '-';
            i++;
        }
        String rest = in.substring(i);
        if (rest.equals("NaN")) {
            return Double.NaN;
        }
        if (rest.equals("Infinity")) {
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        double magnitude;
        if (rest.length() > 1 && rest.charAt(0) == '0' && (rest.charAt(1) == 'x' || rest.charAt(1) == 'X')) {
            magnitude = parseHex(in, rest.substring(2), single);
        } else {
            magnitude = parseDecimal(in, rest, single);
        }
        return negative ? -magnitude : magnitude;
    }

    private static NumberFormatException invalid(String in) {
        return new NumberFormatException("For input string: \"" + in + "\"");
    }

    private static int suffixEnd(String text) {
        int end = text.length();
        if (end > 0) {
            char c = text.charAt(end - 1);
            if (c == 'f' || c == 'F' || c == 'd' || c == 'D') {
                end--;
            }
        }
        return end;
    }

    private static double parseDecimal(String in, String text, boolean single) {
        int end = suffixEnd(text);
        StringBuilder digits = new StringBuilder();
        int pointExponent = 0;
        boolean seenPoint = false;
        boolean anyDigit = false;
        int i = 0;
        for (; i < end; ++i) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                anyDigit = true;
                if (digits.length() > 0 || c != '0') {
                    digits.append(c);
                }
                if (seenPoint) {
                    pointExponent--;
                }
            } else if (c == '.') {
                if (seenPoint) {
                    throw new NumberFormatException("multiple points");
                }
                seenPoint = true;
            } else {
                break;
            }
        }
        if (!anyDigit) {
            throw invalid(in);
        }
        long exponent = 0;
        if (i < end) {
            char c = text.charAt(i);
            if (c != 'e' && c != 'E') {
                throw invalid(in);
            }
            i++;
            boolean negativeExponent = false;
            if (i < end && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
                negativeExponent = text.charAt(i) == '-';
                i++;
            }
            if (i == end) {
                throw invalid(in);
            }
            for (; i < end; ++i) {
                c = text.charAt(i);
                if (c < '0' || c > '9') {
                    throw invalid(in);
                }
                if (exponent < 100000000) {
                    exponent = exponent * 10 + (c - '0');
                }
            }
            if (negativeExponent) {
                exponent = -exponent;
            }
        }
        // Trailing zeros of the significand only move the exponent.
        int length = digits.length();
        while (length > 0 && digits.charAt(length - 1) == '0') {
            length--;
            pointExponent++;
        }
        if (length == 0) {
            return 0.0;
        }
        String significand = digits.substring(0, length);
        long e = exponent + pointExponent;
        if (e + length > 400) {
            return Double.POSITIVE_INFINITY;
        }
        if (e + length < -400) {
            return 0.0;
        }
        int decimalExponent = (int) e;
        if (!single && length <= 15 && Math.abs(decimalExponent) <= 22) {
            double m = Long.parseLong(significand);
            return decimalExponent >= 0 ? m * POWERS_OF_TEN[decimalExponent] : m / POWERS_OF_TEN[-decimalExponent];
        }
        if (single && length <= 7 && Math.abs(decimalExponent) <= 10) {
            float m = Long.parseLong(significand);
            return decimalExponent >= 0 ? m * FLOAT_POWERS_OF_TEN[decimalExponent]
                    : m / FLOAT_POWERS_OF_TEN[-decimalExponent];
        }
        BigInteger value = new BigInteger(significand);
        BigInteger num = decimalExponent >= 0 ? value.multiply(BigInteger.TEN.pow(decimalExponent)) : value;
        BigInteger den = decimalExponent < 0 ? BigInteger.TEN.pow(-decimalExponent) : BigInteger.ONE;
        return round(num, den, single);
    }

    private static double parseHex(String in, String text, boolean single) {
        int end = suffixEnd(text);
        int p = -1;
        for (int i = 0; i < end; ++i) {
            char c = text.charAt(i);
            if (c == 'p' || c == 'P') {
                p = i;
                break;
            }
        }
        if (p < 0) {
            throw invalid(in);
        }
        BigInteger value = BigInteger.ZERO;
        int fractionDigits = 0;
        boolean seenPoint = false;
        boolean anyDigit = false;
        for (int i = 0; i < p; ++i) {
            char c = text.charAt(i);
            if (c == '.') {
                if (seenPoint) {
                    throw invalid(in);
                }
                seenPoint = true;
                continue;
            }
            int digit = Character.digit(c, 16);
            if (digit < 0) {
                throw invalid(in);
            }
            anyDigit = true;
            value = value.shiftLeft(4).add(BigInteger.valueOf(digit));
            if (seenPoint) {
                fractionDigits++;
            }
        }
        if (!anyDigit) {
            throw invalid(in);
        }
        int i = p + 1;
        boolean negativeExponent = false;
        if (i < end && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
            negativeExponent = text.charAt(i) == '-';
            i++;
        }
        if (i == end) {
            throw invalid(in);
        }
        long exponent = 0;
        for (; i < end; ++i) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                throw invalid(in);
            }
            if (exponent < 100000000) {
                exponent = exponent * 10 + (c - '0');
            }
        }
        if (negativeExponent) {
            exponent = -exponent;
        }
        if (value.signum() == 0) {
            return 0.0;
        }
        long binaryExponent = exponent - 4L * fractionDigits;
        if (binaryExponent + value.bitLength() > 2000) {
            return Double.POSITIVE_INFINITY;
        }
        if (binaryExponent + value.bitLength() < -2000) {
            return 0.0;
        }
        BigInteger num = binaryExponent >= 0 ? value.shiftLeft((int) binaryExponent) : value;
        BigInteger den = binaryExponent < 0 ? BigInteger.ONE.shiftLeft((int) -binaryExponent) : BigInteger.ONE;
        return round(num, den, single);
    }

    // num / den (positive) rounded to the nearest double or float, ties to even, subnormals and overflow included.
    private static double round(BigInteger num, BigInteger den, boolean single) {
        int precision = single ? 24 : 53;
        int minExponent = single ? -126 : -1022;
        int maxExponent = single ? 127 : 1023;
        int k = num.bitLength() - den.bitLength();
        if (k >= 0 ? num.compareTo(den.shiftLeft(k)) < 0 : num.shiftLeft(-k).compareTo(den) < 0) {
            k--;
        }
        int exponent = Math.max(k, minExponent);
        int shift = precision - 1 - exponent;
        BigInteger n = shift >= 0 ? num.shiftLeft(shift) : num;
        BigInteger d = shift < 0 ? den.shiftLeft(-shift) : den;
        BigInteger[] qr = n.divideAndRemainder(d);
        BigInteger q = qr[0];
        int half = qr[1].shiftLeft(1).compareTo(d);
        if (half > 0 || half == 0 && q.testBit(0)) {
            q = q.add(BigInteger.ONE);
        }
        if (q.bitLength() > precision) {
            q = q.shiftRight(1);
            exponent++;
        }
        if (exponent > maxExponent) {
            return Double.POSITIVE_INFINITY;
        }
        long significand = q.longValue();
        boolean normal = q.bitLength() == precision;
        if (single) {
            int bits = normal ? ((exponent + 127) << 23) | (int) (significand & ((1 << 23) - 1)) : (int) significand;
            return Float.intBitsToFloat(bits);
        }
        long bits = normal ? ((long) (exponent + 1023) << 52) | (significand & ((1L << 52) - 1)) : significand;
        return Double.longBitsToDouble(bits);
    }
}
