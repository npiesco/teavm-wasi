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

public final class FloatAnalyzer {
    public static final int PRECISION = 9;
    public static final int MAX_POS = 100000000;

    private FloatAnalyzer() {
    }

    // The JDK's Float.toString digits (ShortestDecimal), padded to PRECISION digits.
    public static void analyze(float d, Result result) {
        int bits = Float.floatToIntBits(d);
        result.sign = (bits & (1 << 31)) != 0;
        int mantissa = bits & ((1 << 23) - 1);
        int exponent = (bits >> 23) & ((1 << 8) - 1);
        if (mantissa == 0 && exponent == 0) {
            result.mantissa = 0;
            result.exponent = 0;
            return;
        }
        ShortestDecimal decimal = new ShortestDecimal();
        if (exponent == 0) {
            decimal.compute(mantissa, -149, false);
        } else {
            decimal.compute(mantissa | (1 << 23), exponent - 150, mantissa == 0 && exponent > 1);
        }
        long scaled = decimal.digits;
        for (int i = decimal.length; i < PRECISION; ++i) {
            scaled *= 10;
        }
        result.mantissa = (int) scaled;
        result.exponent = decimal.exponent;
    }

    public static class Result {
        public int mantissa;
        public int exponent;
        public boolean sign;
    }
}
