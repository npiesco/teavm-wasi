/*
 *  Copyright 2017 Alexey Andreev.
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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.DuplicateFormatFlagsException;
import java.util.FormatFlagsConversionMismatchException;
import java.util.IllegalFormatConversionException;
import java.util.Locale;
import java.util.UnknownFormatConversionException;
import org.teavm.classlib.impl.IntegerUtil;

public final class TFormatter implements Closeable, Flushable {
    private Locale locale;
    private Appendable out;
    private IOException ioException;

    public TFormatter() {
        this(Locale.getDefault());
    }

    public TFormatter(Appendable a) {
        this(a, Locale.getDefault());
    }

    public TFormatter(Locale l) {
        this(new StringBuilder(), l);
    }

    public TFormatter(Appendable a, Locale l) {
        out = a;
        locale = l;
    }

    public TFormatter(PrintStream ps) {
        this(new OutputStreamWriter(ps));
    }

    public TFormatter(OutputStream os) {
        this(new OutputStreamWriter(os));
    }

    public TFormatter(OutputStream os, String csn) throws UnsupportedEncodingException {
        this(new OutputStreamWriter(os, csn));
    }

    public TFormatter(OutputStream os, String csn, Locale l) throws UnsupportedEncodingException {
        this(new OutputStreamWriter(os, csn), l);
    }

    public Locale locale() {
        requireOpen();
        return locale;
    }

    public Appendable out() {
        requireOpen();
        return out;
    }

    private void requireOpen() {
        if (out == null) {
            throw new TFormatterClosedException();
        }
    }

    @Override
    public String toString() {
        requireOpen();
        return out.toString();
    }

    @Override
    public void flush() {
        requireOpen();
        if (out instanceof Flushable) {
            try {
                ((Flushable) out).flush();
            } catch (IOException e) {
                ioException = e;
            }
        }
    }

    @Override
    public void close() {
        requireOpen();
        try {
            if (out instanceof Closeable) {
                ((Closeable) out).close();
            }
        } catch (IOException e) {
            ioException = e;
        } finally {
            out = null;
        }
    }

    public IOException ioException() {
        return ioException;
    }

    public TFormatter format(String format, Object... args) {
        return format(locale, format, args);
    }

    public TFormatter format(Locale l, String format, Object... args) {
        requireOpen();
        try {
            if (args == null) {
                args = new Object[1];
            }
            new FormatWriter(this, out, l, format, args).write();
        } catch (IOException e) {
            ioException = e;
        }
        return this;
    }

    static class FormatWriter {
        private static final String FORMAT_FLAGS = "-^#+ 0,(<";
        private static final int MASK_FOR_GENERAL_FORMAT =
                TFormattableFlags.LEFT_JUSTIFY | TFormattableFlags.ALTERNATE
                        | TFormattableFlags.UPPERCASE | TFormattableFlags.PREVIOUS_ARGUMENT;
        private static final int MASK_FOR_CHAR_FORMAT =
                TFormattableFlags.LEFT_JUSTIFY | TFormattableFlags.UPPERCASE | TFormattableFlags.PREVIOUS_ARGUMENT;
        private static final int MASK_FOR_INT_DECIMAL_FORMAT = MASK_FOR_GENERAL_FORMAT ^ TFormattableFlags.ALTERNATE
                | TFormattableFlags.LEADING_SPACE | TFormattableFlags.ZERO_PADDED
                | TFormattableFlags.PARENTHESIZED_NEGATIVE | TFormattableFlags.SIGNED
                | TFormattableFlags.GROUPING_SEPARATOR;
        private static final int MASK_FOR_INT_RADIX_FORMAT = MASK_FOR_GENERAL_FORMAT
                | TFormattableFlags.ZERO_PADDED | TFormattableFlags.PARENTHESIZED_NEGATIVE;
        private TFormatter formatter;
        Appendable out;
        Locale locale;
        String format;
        Object[] args;
        int index;
        int formatSpecifierStart;
        int defaultArgumentIndex;
        int argumentIndex;
        int previousArgumentIndex;
        int width;
        int precision;
        int flags;

        FormatWriter(TFormatter formatter, Appendable out, Locale locale, String format, Object[] args) {
            this.formatter = formatter;
            this.out = out;
            this.locale = locale;
            this.format = format;
            this.args = args;
        }

        void write() throws IOException {
            while (true) {
                int next = format.indexOf('%', index);
                if (next < 0) {
                    out.append(format.substring(index));
                    break;
                }
                out.append(format.substring(index, next));
                index = next + 1;

                formatSpecifierStart = index;
                char specifier = parseFormatSpecifier();
                configureFormat();
                formatValue(specifier);
            }
        }

        private void formatValue(char specifier) throws IOException {
            switch (specifier) {
                case 'b':
                    formatBoolean(specifier, false);
                    break;
                case 'B':
                    formatBoolean(specifier, true);
                    break;
                case 'h':
                    formatHex(specifier, false);
                    break;
                case 'H':
                    formatHex(specifier, true);
                    break;
                case 's':
                    formatString(specifier, false);
                    break;
                case 'S':
                    formatString(specifier, true);
                    break;

                case 'c':
                    formatChar(specifier, false);
                    break;
                case 'C':
                    formatChar(specifier, true);
                    break;

                case 'd':
                    formatDecimalInt(specifier, false);
                    break;

                case 'o':
                    formatRadixInt(specifier, 3, false);
                    break;

                case 'x':
                    formatRadixInt(specifier, 4, false);
                    break;
                case 'X':
                    formatRadixInt(specifier, 4, true);
                    break;

                case 'e':
                case 'E':
                case 'f':
                case 'g':
                case 'G':
                case 'a':
                case 'A':
                    formatFloat(specifier);
                    break;

                case '%':
                    formatPercent();
                    break;
                case 'n':
                    formatLineSeparator();
                    break;

                default:
                    throw new UnknownFormatConversionException(String.valueOf(specifier));
            }
        }

        // JDK %%: a literal percent sign, justified to the width; only '-' is allowed.
        private void formatPercent() throws IOException {
            if (precision >= 0) {
                throw new TIllegalFormatPrecisionException(precision);
            }
            if ((flags & ~TFormattableFlags.LEFT_JUSTIFY) != 0) {
                throw new TIllegalFormatFlagsException(flagsToString(flags));
            }
            if ((flags & TFormattableFlags.LEFT_JUSTIFY) != 0 && width < 0) {
                throw new TMissingFormatWidthException(format.substring(formatSpecifierStart - 1, index));
            }
            formatGivenString(false, "%");
        }

        private void formatLineSeparator() throws IOException {
            if (precision >= 0) {
                throw new TIllegalFormatPrecisionException(precision);
            }
            if (width >= 0) {
                throw new TIllegalFormatWidthException(width);
            }
            if (flags != 0) {
                throw new TIllegalFormatFlagsException(flagsToString(flags));
            }
            out.append(System.lineSeparator());
        }

        // The JDK's floating point conversions. A float is formatted as the double it widens to; a double from its
        // shortest decimal representation (Double.toString), a BigDecimal exactly, both rounded HALF_UP. NaN and
        // infinities are never zero padded; -0.0 is negative.
        private void formatFloat(char specifier) throws IOException {
            char conversion = Character.toLowerCase(specifier);
            boolean upperCase = Character.isUpperCase(specifier);
            if (width < -1) {
                throw new TIllegalFormatWidthException(width);
            }
            if ((flags & (TFormattableFlags.LEFT_JUSTIFY | TFormattableFlags.ZERO_PADDED)) != 0 && width < 0) {
                throw new TMissingFormatWidthException(format.substring(formatSpecifierStart - 1, index));
            }
            if ((flags & TFormattableFlags.SIGNED) != 0 && (flags & TFormattableFlags.LEADING_SPACE) != 0
                    || (flags & TFormattableFlags.LEFT_JUSTIFY) != 0 && (flags & TFormattableFlags.ZERO_PADDED) != 0) {
                throw new TIllegalFormatFlagsException(flagsToString(flags));
            }
            int bad = 0;
            if (conversion == 'a') {
                bad = TFormattableFlags.PARENTHESIZED_NEGATIVE | TFormattableFlags.GROUPING_SEPARATOR;
            } else if (conversion == 'e') {
                bad = TFormattableFlags.GROUPING_SEPARATOR;
            } else if (conversion == 'g') {
                bad = TFormattableFlags.ALTERNATE;
            }
            if ((flags & bad) != 0) {
                throw new FormatFlagsConversionMismatchException(firstFlagToString(flags & bad), specifier);
            }
            Object arg = args[argumentIndex];
            if (arg == null) {
                formatGivenString(upperCase, "null");
                return;
            }
            StringBuilder sb = new StringBuilder();
            if (arg instanceof BigDecimal) {
                if (conversion == 'a') {
                    throw new IllegalFormatConversionException(specifier, arg.getClass());
                }
                BigDecimal value = (BigDecimal) arg;
                boolean negative = value.signum() < 0;
                leadingSign(sb, negative);
                formatDecimalMagnitude(sb, value.abs(), conversion, upperCase, negative);
                trailingSign(sb, negative);
            } else if (arg instanceof Double || arg instanceof Float) {
                double value = ((Number) arg).doubleValue();
                boolean negative = Double.compare(value, 0.0) < 0;
                if (Double.isNaN(value)) {
                    sb.append(upperCase ? "NAN" : "NaN");
                } else {
                    double magnitude = Math.abs(value);
                    leadingSign(sb, negative);
                    if (Double.isInfinite(magnitude)) {
                        sb.append(upperCase ? "INFINITY" : "Infinity");
                    } else if (conversion == 'a') {
                        formatHexFloat(sb, magnitude, upperCase);
                    } else {
                        formatDecimalMagnitude(sb, magnitude == 0 ? BigDecimal.ZERO
                                : new BigDecimal(Double.toString(magnitude)), conversion, upperCase, negative);
                    }
                    trailingSign(sb, negative);
                }
            } else {
                throw new IllegalFormatConversionException(specifier, arg.getClass());
            }
            justify(sb.toString());
        }

        private void leadingSign(StringBuilder sb, boolean negative) {
            if (!negative) {
                if ((flags & TFormattableFlags.SIGNED) != 0) {
                    sb.append('+');
                } else if ((flags & TFormattableFlags.LEADING_SPACE) != 0) {
                    sb.append(' ');
                }
            } else if ((flags & TFormattableFlags.PARENTHESIZED_NEGATIVE) != 0) {
                sb.append('(');
            } else {
                sb.append('-');
            }
        }

        private void trailingSign(StringBuilder sb, boolean negative) {
            if (negative && (flags & TFormattableFlags.PARENTHESIZED_NEGATIVE) != 0) {
                sb.append(')');
            }
        }

        // The width a magnitude is zero padded to: the field width less the exponent and a closing parenthesis.
        private int adjustWidth(int newWidth, boolean negative) {
            if (newWidth != -1 && negative && (flags & TFormattableFlags.PARENTHESIZED_NEGATIVE) != 0) {
                newWidth--;
            }
            return newWidth;
        }

        private void formatDecimalMagnitude(StringBuilder sb, BigDecimal value, char conversion, boolean upperCase,
                boolean negative) throws IOException {
            String exponent = null;
            String mantissa;
            if (conversion == 'e') {
                int prec = precision == -1 ? 6 : precision;
                if (value.signum() == 0) {
                    mantissa = zeros(prec);
                    exponent = "+00";
                } else {
                    BigDecimal rounded = value.round(new MathContext(prec + 1, RoundingMode.HALF_UP));
                    int exp = rounded.precision() - rounded.scale() - 1;
                    mantissa = rounded.movePointLeft(exp).setScale(prec, RoundingMode.HALF_UP).toPlainString();
                    exponent = exponentString(exp);
                }
                if ((flags & TFormattableFlags.ALTERNATE) != 0 && prec == 0) {
                    mantissa += ".";
                }
            } else if (conversion == 'f') {
                int prec = precision == -1 ? 6 : precision;
                mantissa = value.setScale(prec, RoundingMode.HALF_UP).toPlainString();
                if ((flags & TFormattableFlags.ALTERNATE) != 0 && prec == 0) {
                    mantissa += ".";
                }
            } else {
                int prec = precision == -1 ? 6 : precision == 0 ? 1 : precision;
                if (value.signum() == 0) {
                    mantissa = zeros(prec - 1);
                } else {
                    BigDecimal rounded = value.round(new MathContext(prec, RoundingMode.HALF_UP));
                    int exp = rounded.precision() - rounded.scale() - 1;
                    if (rounded.compareTo(BigDecimal.valueOf(1, 4)) >= 0 && exp < prec) {
                        mantissa = rounded.setScale(prec - exp - 1, RoundingMode.HALF_UP).toPlainString();
                    } else {
                        mantissa = rounded.movePointLeft(exp).setScale(prec - 1, RoundingMode.HALF_UP).toPlainString();
                        exponent = exponentString(exp);
                    }
                }
            }
            int newWidth = width;
            if (width != -1) {
                newWidth = adjustWidth(exponent != null ? width - exponent.length() - 1 : width, negative);
            }
            localizedMagnitude(sb, mantissa, conversion != 'e', newWidth);
            if (exponent != null) {
                sb.append(upperCase ? 'E' : 'e');
                sb.append(exponent.charAt(0));
                char zero = zeroDigit();
                for (int i = 1; i < exponent.length(); ++i) {
                    sb.append((char) (exponent.charAt(i) - '0' + zero));
                }
            }
        }

        private static String zeros(int fractionDigits) {
            StringBuilder sb = new StringBuilder("0");
            if (fractionDigits > 0) {
                sb.append('.');
                for (int i = 0; i < fractionDigits; ++i) {
                    sb.append('0');
                }
            }
            return sb.toString();
        }

        private static String exponentString(int exp) {
            String digits = Integer.toString(Math.abs(exp));
            return (exp < 0 ? "-" : "+") + (digits.length() < 2 ? "0" + digits : digits);
        }

        // Digits in the locale's zero digit, its decimal separator and (with ',') its grouping of the integer part,
        // zero padded after the sign up to the given width.
        private void localizedMagnitude(StringBuilder sb, String value, boolean groupingAllowed, int padWidth) {
            int begin = sb.length();
            char zero = zeroDigit();
            int dot = value.indexOf('.');
            int integerEnd = dot >= 0 ? dot : value.length();
            char groupingSeparator = ',';
            int groupingSize = -1;
            if (groupingAllowed && (flags & TFormattableFlags.GROUPING_SEPARATOR) != 0) {
                groupingSize = 3;
                if (locale != null && !locale.equals(Locale.US)) {
                    groupingSeparator = new DecimalFormatSymbols(locale).getGroupingSeparator();
                    groupingSize = ((DecimalFormat) NumberFormat.getNumberInstance(locale)).getGroupingSize();
                }
            }
            for (int i = 0; i < integerEnd; ++i) {
                sb.append((char) (value.charAt(i) - '0' + zero));
                int remaining = integerEnd - i - 1;
                if (groupingSize > 0 && remaining > 0 && remaining % groupingSize == 0) {
                    sb.append(groupingSeparator);
                }
            }
            if (dot >= 0) {
                char decimalSeparator = '.';
                if (locale != null && !locale.equals(Locale.US)) {
                    decimalSeparator = new DecimalFormatSymbols(locale).getDecimalSeparator();
                }
                sb.append(decimalSeparator);
                for (int i = dot + 1; i < value.length(); ++i) {
                    sb.append((char) (value.charAt(i) - '0' + zero));
                }
            }
            if (padWidth != -1 && (flags & TFormattableFlags.ZERO_PADDED) != 0) {
                for (int k = sb.length(); k < padWidth; k++) {
                    sb.insert(begin, zero);
                }
            }
        }

        private char zeroDigit() {
            if (locale == null || locale.equals(Locale.US)) {
                return '0';
            }
            return new DecimalFormatSymbols(locale).getZeroDigit();
        }

        // JDK %a: Double.toHexString, or with a precision of 1 to 12 hexadecimal digits the significand rounded
        // half-even (subnormals normalized first), zero filled to the precision.
        private void formatHexFloat(StringBuilder sb, double value, boolean upperCase) {
            int prec = precision == -1 ? 0 : precision == 0 ? 1 : precision;
            String s = hexDouble(value, prec);
            sb.append(upperCase ? "0X" : "0x");
            int p = s.indexOf('p');
            StringBuilder mantissa = new StringBuilder(s.substring(0, p));
            if (prec != 0) {
                int dot = mantissa.indexOf(".");
                int digits = dot < 0 ? 0 : mantissa.length() - dot - 1;
                if (dot < 0) {
                    mantissa.append('.');
                }
                for (int i = digits; i < prec; ++i) {
                    mantissa.append('0');
                }
            }
            if ((flags & TFormattableFlags.ZERO_PADDED) != 0) {
                int total = sb.length() + mantissa.length() + 1 + (s.length() - p - 1);
                for (int i = total; i < width; ++i) {
                    sb.append('0');
                }
            }
            sb.append(upperCase ? mantissa.toString().toUpperCase() : mantissa.toString());
            sb.append(upperCase ? 'P' : 'p');
            sb.append(s, p + 1, s.length());
        }

        private static String hexDouble(double d, int prec) {
            if (!Double.isFinite(d) || d == 0.0 || prec == 0 || prec >= 13) {
                return Double.toHexString(d).substring(2);
            }
            int exponent = Math.getExponent(d);
            boolean subnormal = exponent == Double.MIN_EXPONENT - 1;
            if (subnormal) {
                d *= Math.scalb(1.0, 54);
            }
            int shiftDistance = 53 - (1 + prec * 4);
            long bits = Double.doubleToLongBits(d);
            long significand = (bits & 0x7FFFFFFFFFFFFFFFL) >> shiftDistance;
            long roundingBits = bits & ~(~0L << shiftDistance);
            boolean leastZero = (significand & 1L) == 0L;
            boolean round = ((1L << (shiftDistance - 1)) & roundingBits) != 0L;
            boolean sticky = shiftDistance > 1 && (~(1L << (shiftDistance - 1)) & roundingBits) != 0;
            if (leastZero && round && sticky || !leastZero && round) {
                significand++;
            }
            double result = Double.longBitsToDouble((bits & 0x8000000000000000L) | (significand << shiftDistance));
            if (Double.isInfinite(result)) {
                return "1.0p1024";
            }
            String res = Double.toHexString(result).substring(2);
            if (!subnormal) {
                return res;
            }
            int p = res.indexOf('p');
            return res.substring(0, p) + "p" + (Integer.parseInt(res.substring(p + 1)) - 54);
        }

        private void justify(String str) throws IOException {
            if ((flags & TFormattableFlags.LEFT_JUSTIFY) != 0) {
                out.append(str);
                mayBeAppendSpaces(str);
            } else {
                mayBeAppendSpaces(str);
                out.append(str);
            }
        }

        private void formatBoolean(char specifier, boolean upperCase) throws IOException {
            verifyFlagsForGeneralFormat(specifier);
            Object arg = args[argumentIndex];
            String s = Boolean.toString(arg instanceof Boolean ? (Boolean) arg : arg != null);
            formatGivenString(upperCase, s);
        }

        private void formatHex(char specifier, boolean upperCase) throws IOException {
            verifyFlagsForGeneralFormat(specifier);
            Object arg = args[argumentIndex];
            String s = arg != null ? Integer.toHexString(arg.hashCode()) : "null";
            formatGivenString(upperCase, s);
        }

        private void formatString(char specifier, boolean upperCase) throws IOException {
            verifyFlagsForGeneralFormat(specifier);
            Object arg = args[argumentIndex];
            if (arg instanceof TFormattable) {
                int flagsToPass = flags & 7;
                if (upperCase) {
                    flagsToPass |= TFormattableFlags.UPPERCASE;
                }
                ((TFormattable) arg).formatTo(formatter, flagsToPass, width, precision);
            } else {
                formatGivenString(upperCase, String.valueOf(arg));
            }
        }

        private void formatChar(char specifier, boolean upperCase) throws IOException {
            verifyFlags(specifier, MASK_FOR_CHAR_FORMAT);
            Object arg = args[argumentIndex];

            if (precision >= 0) {
                throw new TIllegalFormatPrecisionException(precision);
            }

            int c;
            if (arg instanceof Character) {
                c = (Character) arg;
            } else if (arg instanceof Byte) {
                c = (char) (byte) arg;
            } else if (arg instanceof Short) {
                c = (char) (short) arg;
            } else if (arg instanceof Integer) {
                c = (int) arg;
                if (!Character.isValidCodePoint(c)) {
                    throw new TIllegalFormatCodePointException(c);
                }
            } else if (arg == null) {
                formatGivenString(upperCase, "null");
                return;
            } else {
                throw new IllegalFormatConversionException(specifier, arg.getClass());
            }

            formatGivenString(upperCase, new String(Character.toChars(c)));
        }

        private void formatDecimalInt(char specifier, boolean upperCase) throws IOException {
            verifyFlags(specifier, MASK_FOR_INT_DECIMAL_FORMAT);
            verifyIntFlags();

            String str;
            Object arg = args[argumentIndex];
            boolean negative;
            if (arg == null) {
                formatGivenString(upperCase, "null");
                return;
            }
            if (arg instanceof BigInteger) {
                BigInteger value = (BigInteger) arg;
                str = value.abs().toString();
                negative = value.signum() < 0;
            } else if (arg instanceof Long) {
                long value = (Long) arg;
                str = Long.toString(Math.abs(value));
                negative = value < 0;
            } else if (arg instanceof Integer || arg instanceof Byte || arg instanceof Short) {
                int value = ((Number) arg).intValue();
                str = Integer.toString(Math.abs(value));
                negative = value < 0;
            } else {
                throw new IllegalFormatConversionException(specifier, arg != null ? arg.getClass() : null);
            }

            int additionalSymbols = 0;
            StringBuilder sb = new StringBuilder();
            if (negative) {
                if ((flags & TFormattableFlags.PARENTHESIZED_NEGATIVE) != 0) {
                    sb.append('(');
                    additionalSymbols += 2;
                } else {
                    sb.append('-');
                    additionalSymbols++;
                }
            } else {
                if ((flags & TFormattableFlags.SIGNED) != 0) {
                    sb.append('+');
                    additionalSymbols++;
                } else if ((flags & TFormattableFlags.LEADING_SPACE) != 0) {
                    sb.append(' ');
                    additionalSymbols++;
                }
            }

            StringBuilder valueSb = new StringBuilder();
            if ((flags & TFormattableFlags.GROUPING_SEPARATOR) != 0) {
                char separator = new DecimalFormatSymbols(locale).getGroupingSeparator();
                int size = ((DecimalFormat) NumberFormat.getNumberInstance(locale)).getGroupingSize();
                int offset = str.length() % size;
                if (offset == 0) {
                    offset = size;
                }

                int prev = 0;
                for (int i = offset; i < str.length(); i += size) {
                    valueSb.append(str.substring(prev, i));
                    valueSb.append(separator);
                    prev = i;
                }
                valueSb.append(str.substring(prev));
            } else {
                valueSb.append(str);
            }

            if ((flags & TFormattableFlags.ZERO_PADDED) != 0) {
                int actual = valueSb.length() + additionalSymbols;
                for (int i = actual; i < width; ++i) {
                    sb.append(Character.forDigit(0, 10));
                }
            }
            sb.append(valueSb);

            if (negative && (flags & TFormattableFlags.PARENTHESIZED_NEGATIVE) != 0) {
                sb.append(')');
            }

            formatGivenString(upperCase, sb.toString());
        }

        private void formatRadixInt(char specifier, int radixLog2, boolean upperCase) throws IOException {
            verifyFlags(specifier, MASK_FOR_INT_RADIX_FORMAT);
            verifyIntFlags();

            String str;
            Object arg = args[argumentIndex];
            if (arg == null) {
                formatGivenString(upperCase, "null");
                return;
            }
            if (arg instanceof BigInteger) {
                // JDK: a BigInteger is signed in octal and hexadecimal too.
                BigInteger value = (BigInteger) arg;
                str = value.abs().toString(radixLog2 == 4 ? 16 : 8);
                if ((flags & TFormattableFlags.ALTERNATE) != 0) {
                    str = (radixLog2 == 4 ? "0x" : "0") + str;
                }
                StringBuilder signed = new StringBuilder();
                leadingSign(signed, value.signum() < 0);
                if ((flags & TFormattableFlags.ZERO_PADDED) != 0) {
                    int total = signed.length() + str.length() + (value.signum() < 0
                            && (flags & TFormattableFlags.PARENTHESIZED_NEGATIVE) != 0 ? 1 : 0);
                    for (int i = total; i < width; ++i) {
                        signed.append('0');
                    }
                }
                signed.append(str);
                trailingSign(signed, value.signum() < 0);
                formatGivenString(upperCase, signed.toString());
                return;
            }
            if (arg instanceof Long) {
                str = IntegerUtil.toUnsignedLogRadixString((Long) arg, radixLog2);
            } else if (arg instanceof Integer) {
                str = IntegerUtil.toUnsignedLogRadixString((Integer) arg, radixLog2);
            } else if (arg instanceof Short) {
                str = IntegerUtil.toUnsignedLogRadixString((Short) arg & 0xFFFF, radixLog2);
            } else if (arg instanceof Byte) {
                str = IntegerUtil.toUnsignedLogRadixString((Byte) arg & 0xFF, radixLog2);
            } else {
                throw new IllegalFormatConversionException(specifier, arg != null ? arg.getClass() : null);
            }

            StringBuilder sb = new StringBuilder();
            if ((flags & TFormattableFlags.ALTERNATE) != 0) {
                String prefix = radixLog2 == 4 ? "0x" : "0";
                str = prefix + str;
            }

            if ((flags & TFormattableFlags.ZERO_PADDED) != 0) {
                for (int i = str.length(); i < width; ++i) {
                    sb.append(Character.forDigit(0, 10));
                }
            }
            sb.append(str);

            formatGivenString(upperCase, sb.toString());
        }

        private void verifyIntFlags() {
            if ((flags & TFormattableFlags.SIGNED) != 0 && (flags & TFormattableFlags.LEADING_SPACE) != 0) {
                throw new TIllegalFormatFlagsException(flagsToString(flags));
            }
            if ((flags & TFormattableFlags.ZERO_PADDED) != 0 && (flags & TFormattableFlags.LEFT_JUSTIFY) != 0) {
                throw new TIllegalFormatFlagsException(flagsToString(flags));
            }
            if (precision >= 0) {
                throw new TIllegalFormatPrecisionException(precision);
            }
            if ((flags & TFormattableFlags.LEFT_JUSTIFY) != 0 && width < 0) {
                throw new TMissingFormatWidthException(format.substring(formatSpecifierStart, index));
            }
        }

        private void formatGivenString(boolean upperCase, String str) throws IOException {
            if (precision > 0) {
                str = str.substring(0, precision);
            }

            if (upperCase) {
                str = str.toUpperCase();
            }

            if ((flags & TFormattableFlags.LEFT_JUSTIFY) != 0) {
                out.append(str);
                mayBeAppendSpaces(str);
            } else {
                mayBeAppendSpaces(str);
                out.append(str);
            }
        }

        private void verifyFlagsForGeneralFormat(char conversion) {
            verifyFlags(conversion, MASK_FOR_GENERAL_FORMAT);
        }

        private void verifyFlags(char conversion, int mask) {
            if ((flags | mask) != mask) {
                throw new FormatFlagsConversionMismatchException(firstFlagToString(flags & ~mask), conversion);
            }
        }

        // Every flag set, in the JDK's Flags.toString order.
        private String flagsToString(int flags) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < FORMAT_FLAGS.length(); ++i) {
                if ((flags & (1 << i)) != 0) {
                    sb.append(FORMAT_FLAGS.charAt(i));
                }
            }
            return sb.toString();
        }

        // A mismatch names the first offending flag, as the JDK's checkBadFlags does.
        private String firstFlagToString(int flags) {
            return String.valueOf(FORMAT_FLAGS.charAt(Integer.numberOfTrailingZeros(flags)));
        }

        private void mayBeAppendSpaces(String str) throws IOException {
            if (width > str.length()) {
                int diff = width - str.length();
                StringBuilder sb = new StringBuilder(diff);
                for (int i = 0; i < diff; ++i) {
                    sb.append(' ');
                }
                out.append(sb);
            }
        }

        private void configureFormat() {
            if ((flags & TFormattableFlags.PREVIOUS_ARGUMENT) != 0) {
                argumentIndex = Math.max(0, previousArgumentIndex);
            }

            if (argumentIndex == -1) {
                argumentIndex = defaultArgumentIndex++;
            }
            previousArgumentIndex = argumentIndex;
        }

        private char parseFormatSpecifier() {
            flags = 0;
            argumentIndex = -1;
            width = -1;
            precision = -1;

            char c = format.charAt(index);

            if (c != '0' && isDigit(c)) {
                int n = readInt();
                if (index < format.length() && format.charAt(index) == '$') {
                    index++;
                    argumentIndex = n - 1;
                } else {
                    width = n;
                }
            }
            parseFlags();

            if (width < 0 && index < format.length() && isDigit(format.charAt(index))) {
                width = readInt();
            }

            if (index < format.length() && format.charAt(index) == '.') {
                index++;
                if (index >= format.length() || !isDigit(format.charAt(index))) {
                    throw new UnknownFormatConversionException(String.valueOf(format.charAt(index - 1)));
                }
                precision = readInt();
            }

            if (index >= format.length()) {
                throw new UnknownFormatConversionException(String.valueOf(format.charAt(format.length() - 1)));
            }
            return format.charAt(index++);
        }

        private void parseFlags() {
            while (index < format.length()) {
                char c = format.charAt(index);
                int flag;
                switch (c) {
                    case '-':
                        flag = TFormattableFlags.LEFT_JUSTIFY;
                        break;
                    case '#':
                        flag = TFormattableFlags.ALTERNATE;
                        break;
                    case '+':
                        flag = TFormattableFlags.SIGNED;
                        break;
                    case ' ':
                        flag = TFormattableFlags.LEADING_SPACE;
                        break;
                    case '0':
                        flag = TFormattableFlags.ZERO_PADDED;
                        break;
                    case ',':
                        flag = TFormattableFlags.GROUPING_SEPARATOR;
                        break;
                    case '(':
                        flag = TFormattableFlags.PARENTHESIZED_NEGATIVE;
                        break;
                    case '<':
                        flag = TFormattableFlags.PREVIOUS_ARGUMENT;
                        break;
                    default:
                        return;
                }
                if ((flags & flag) != 0) {
                    throw new DuplicateFormatFlagsException(String.valueOf(c));
                }
                flags |= flag;
                index++;
            }
        }

        private int readInt() {
            int result = 0;
            while (index < format.length() && isDigit(format.charAt(index))) {
                result = result * 10 + (format.charAt(index++) - '0');
            }
            return result;
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}
