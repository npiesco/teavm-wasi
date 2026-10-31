/*
 *  Copyright 2018 Alexey Andreev.
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

public final class DoubleAnalyzer {
    private DoubleAnalyzer() {
    }

    public static final int DECIMAL_PRECISION = 18;
    public static final long DOUBLE_MAX_POS = 100000000000000000L;

    // The JDK's Double.toString digits (ShortestDecimal), padded to DECIMAL_PRECISION digits.
    public static void analyze(double d, Result result) {
        long bits = Double.doubleToLongBits(d);
        result.sign = (bits & (1L << 63)) != 0;
        long mantissa = bits & ((1L << 52) - 1);
        int exponent = (int) (bits >> 52) & ((1 << 11) - 1);
        if (mantissa == 0 && exponent == 0) {
            result.mantissa = 0;
            result.exponent = 0;
            return;
        }
        ShortestDecimal decimal = new ShortestDecimal();
        if (exponent == 0) {
            decimal.compute(mantissa, -1074, false);
        } else {
            decimal.compute(mantissa | (1L << 52), exponent - 1075, mantissa == 0 && exponent > 1);
        }
        long scaled = decimal.digits;
        for (int i = decimal.length; i < DECIMAL_PRECISION; ++i) {
            scaled *= 10;
        }
        result.mantissa = scaled;
        result.exponent = decimal.exponent;
    }

    // Multiply two longs and shft result right by 64-shift bits.
    public static class Result {
        public long mantissa;
        public int exponent;
        public boolean sign;
    }
}
