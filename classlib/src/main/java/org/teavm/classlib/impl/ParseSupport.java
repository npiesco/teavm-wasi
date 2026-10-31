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
package org.teavm.classlib.impl;

/**
 * Integral parsing with the JDK's accepted syntax and exception messages, shared by Integer, Long, Short and Byte.
 * Values accumulate negatively so that the minimum value parses without overflow.
 */
public final class ParseSupport {
    private ParseSupport() {
    }

    public static NumberFormatException forInputString(String s, int radix) {
        return new NumberFormatException("For input string: \"" + s + "\"" + (radix == 10 ? "" : " under radix "
                + radix));
    }

    public static NumberFormatException forCharSequence(CharSequence s, int begin, int end, int errorIndex) {
        return new NumberFormatException("Error at index " + (errorIndex - begin) + " in: \""
                + s.subSequence(begin, end) + "\"");
    }

    public static void checkRadix(int radix) {
        if (radix < Character.MIN_RADIX) {
            throw new NumberFormatException("radix " + radix + " less than Character.MIN_RADIX");
        }
        if (radix > Character.MAX_RADIX) {
            throw new NumberFormatException("radix " + radix + " greater than Character.MAX_RADIX");
        }
    }

    public static void checkRange(CharSequence s, int begin, int end) {
        if (s == null) {
            throw new NullPointerException();
        }
        if (begin < 0 || begin > end || end > s.length()) {
            throw new IndexOutOfBoundsException("Range [" + begin + ", " + end + ") out of bounds for length "
                    + s.length());
        }
    }

    // A signed value in [min, max] from all of s.
    public static long parseSigned(String s, int radix, long min, long max) {
        if (s == null) {
            throw new NumberFormatException("Cannot parse null string");
        }
        checkRadix(radix);
        int length = s.length();
        if (length == 0) {
            throw forInputString(s, radix);
        }
        int index = 0;
        boolean negative = false;
        long limit = -max;
        char first = s.charAt(0);
        if (first < '0') {
            if (first == '-') {
                negative = true;
                limit = min;
            } else if (first != '+') {
                throw forInputString(s, radix);
            }
            if (length == 1) {
                throw forInputString(s, radix);
            }
            index++;
        }
        long multiplyMin = limit / radix;
        long result = 0;
        while (index < length) {
            int digit = Character.digit(s.charAt(index++), radix);
            if (digit < 0 || result < multiplyMin) {
                throw forInputString(s, radix);
            }
            result *= radix;
            if (result < limit + digit) {
                throw forInputString(s, radix);
            }
            result -= digit;
        }
        return negative ? result : -result;
    }

    // A signed value in [min, max] from s[begin, end). As in the JDK, Integer reports an empty range as an input
    // string and Long with an empty message.
    public static long parseSigned(CharSequence s, int begin, int end, int radix, long min, long max) {
        checkRange(s, begin, end);
        checkRadix(radix);
        if (begin == end) {
            throw min == Integer.MIN_VALUE ? forInputString("", radix) : new NumberFormatException("");
        }
        int index = begin;
        boolean negative = false;
        long limit = -max;
        char first = s.charAt(index);
        if (first < '0') {
            if (first == '-') {
                negative = true;
                limit = min;
            } else if (first != '+') {
                throw forCharSequence(s, begin, end, index);
            }
            index++;
            if (index == end) {
                throw forCharSequence(s, begin, end, index);
            }
        }
        long multiplyMin = limit / radix;
        long result = 0;
        while (index < end) {
            int digit = Character.digit(s.charAt(index), radix);
            if (digit < 0 || result < multiplyMin) {
                throw forCharSequence(s, begin, end, index);
            }
            result *= radix;
            if (result < limit + digit) {
                throw forCharSequence(s, begin, end, index);
            }
            index++;
            result -= digit;
        }
        return negative ? result : -result;
    }

    public static int parseUnsignedInt(String s, int radix) {
        if (s == null) {
            throw new NumberFormatException("Cannot parse null string");
        }
        int length = s.length();
        if (length == 0) {
            throw forInputString(s, radix);
        }
        if (s.charAt(0) == '-') {
            throw new NumberFormatException("Illegal leading minus sign on unsigned string " + s + ".");
        }
        if (length <= 5 || (radix == 10 && length <= 9)) {
            return (int) parseSigned(s, radix, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        long value = parseSigned(s, radix, Long.MIN_VALUE, Long.MAX_VALUE);
        if ((value & 0xFFFFFFFF00000000L) != 0) {
            throw new NumberFormatException("String value " + s + " exceeds range of unsigned int.");
        }
        return (int) value;
    }

    public static int parseUnsignedInt(CharSequence s, int begin, int end, int radix) {
        checkRange(s, begin, end);
        int length = end - begin;
        if (length == 0) {
            throw new NumberFormatException("");
        }
        if (s.charAt(begin) == '-') {
            // The JDK names the whole sequence here, not just the range.
            throw new NumberFormatException("Illegal leading minus sign on unsigned string " + s + ".");
        }
        if (length <= 5 || (radix == 10 && length <= 9)) {
            return (int) parseSigned(s, begin, end, radix, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        long value = parseSigned(s, begin, end, radix, Long.MIN_VALUE, Long.MAX_VALUE);
        if ((value & 0xFFFFFFFF00000000L) != 0) {
            throw new NumberFormatException("String value " + s.subSequence(begin, end)
                    + " exceeds range of unsigned int.");
        }
        return (int) value;
    }

    public static long parseUnsignedLong(String s, int radix) {
        if (s == null) {
            throw new NumberFormatException("Cannot parse null string");
        }
        int length = s.length();
        if (length == 0) {
            throw forInputString(s, radix);
        }
        if (s.charAt(0) == '-') {
            throw new NumberFormatException("Illegal leading minus sign on unsigned string " + s + ".");
        }
        if (length <= 12 || (radix == 10 && length <= 18)) {
            return parseSigned(s, radix, Long.MIN_VALUE, Long.MAX_VALUE);
        }
        long first = parseSigned(s, 0, length - 1, radix, Long.MIN_VALUE, Long.MAX_VALUE);
        int second = Character.digit(s.charAt(length - 1), radix);
        if (second < 0) {
            throw new NumberFormatException("Bad digit at end of " + s);
        }
        return combineUnsigned(first, second, radix, s);
    }

    public static long parseUnsignedLong(CharSequence s, int begin, int end, int radix) {
        checkRange(s, begin, end);
        int length = end - begin;
        if (length == 0) {
            throw forInputString("", radix);
        }
        if (s.charAt(begin) == '-') {
            throw new NumberFormatException("Illegal leading minus sign on unsigned string " + s.subSequence(begin, end)
                    + ".");
        }
        if (length <= 12 || (radix == 10 && length <= 18)) {
            return parseSigned(s, begin, end, radix, Long.MIN_VALUE, Long.MAX_VALUE);
        }
        long first = parseSigned(s, begin, end - 1, radix, Long.MIN_VALUE, Long.MAX_VALUE);
        int second = Character.digit(s.charAt(end - 1), radix);
        if (second < 0) {
            throw new NumberFormatException("Bad digit at end of " + s.subSequence(begin, end));
        }
        return combineUnsigned(first, second, radix, s.subSequence(begin, end));
    }

    // first * radix + second, unless the true value exceeds 64 unsigned bits.
    private static long combineUnsigned(long first, int second, int radix, CharSequence text) {
        long result = first * radix + second;
        int guard = radix * (int) (first >>> 57);
        if (guard >= 128 || (result >= 0 && guard >= 128 - Character.MAX_RADIX)) {
            throw new NumberFormatException("String value " + text + " exceeds range of unsigned long.");
        }
        return result;
    }

    // Integer/Long/Short/Byte.decode: optional sign, then 0x, 0X, # (hex) or a leading 0 (octal).
    public static long decode(String nm, long min, long max) {
        if (nm.isEmpty()) {
            throw new NumberFormatException("Zero length string");
        }
        int radix = 10;
        int index = 0;
        boolean negative = false;
        char first = nm.charAt(0);
        if (first == '-') {
            negative = true;
            index++;
        } else if (first == '+') {
            index++;
        }
        if (nm.startsWith("0x", index) || nm.startsWith("0X", index)) {
            index += 2;
            radix = 16;
        } else if (nm.startsWith("#", index)) {
            index++;
            radix = 16;
        } else if (nm.startsWith("0", index) && nm.length() > 1 + index) {
            index++;
            radix = 8;
        }
        if (nm.startsWith("-", index) || nm.startsWith("+", index)) {
            throw new NumberFormatException("Sign character in wrong position");
        }
        try {
            long result = parseSigned(nm, index, nm.length(), radix, min, max);
            return negative ? -result : result;
        } catch (NumberFormatException e) {
            String constant = negative ? "-" + nm.substring(index) : nm.substring(index);
            return parseSigned(constant, radix, min, max);
        }
    }
}
