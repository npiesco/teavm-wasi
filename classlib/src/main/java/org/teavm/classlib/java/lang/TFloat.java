/*
 *  Copyright 2013 Alexey Andreev.
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

import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.impl.text.FloatingDecimalParser;
import org.teavm.interop.Import;
import org.teavm.interop.NoSideEffects;
import org.teavm.interop.Unmanaged;
import org.teavm.jso.JSBody;

public class TFloat extends TNumber implements TComparable<TFloat> {
    public static final float POSITIVE_INFINITY = 1 / 0.0f;
    public static final float NEGATIVE_INFINITY = -POSITIVE_INFINITY;
    public static final float NaN = getNaN();
    public static final float MAX_VALUE = 0x1.fffffeP+127f;
    public static final float MIN_VALUE = 0x1.0p-126f;
    public static final float MIN_NORMAL = 0x0.000002P-126f;
    public static final int MAX_EXPONENT = 127;
    public static final int MIN_EXPONENT = -126;
    public static final int SIZE = 32;
    public static final Class<Float> TYPE = float.class;
    private float value;

    public TFloat(float value) {
        this.value = value;
    }

    public TFloat(double value) {
        this((float) value);
    }

    public TFloat(TString value) throws TNumberFormatException {
        this(parseFloat(value));
    }

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return (long) value;
    }

    @Override
    public float floatValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return value;
    }

    public static TFloat valueOf(float d) {
        return new TFloat(d);
    }

    public static String toString(float d) {
        return new TStringBuilder().append(d).toString();
    }

    @Override
    public String toString() {
        return toString(value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TFloat && floatToIntBits(((TFloat) other).value) == floatToIntBits(value);
    }

    @Override
    public int hashCode() {
        return hashCode(value);
    }

    public static float sum(float a, float b) {
        return a + b;
    }

    public static float max(float a, float b) {
        return Math.max(a, b);
    }

    public static float min(float a, float b) {
        return Math.min(a, b);
    }

    public static int hashCode(float f) {
        return floatToIntBits(f);
    }

    public static boolean isNaN(float v) {
        if (PlatformDetector.isWebAssembly()) {
            return v != v;
        } else {
            return nativeIsNaN(v);
        }
    }

    @JSBody(params = "v", script = "return isNaN(v);")
    @Import(module = "teavm", name = "isnan")
    @NoSideEffects
    @Unmanaged
    private static native boolean nativeIsNaN(float v);

    public static boolean isInfinite(float v) {
        if (PlatformDetector.isWebAssembly()) {
            return v == (1f / 0f) || v == (-1f / 0f);
        } else {
            return nativeIsInfinite(v);
        }
    }

    @JSBody(params = "v", script = "return !isFinite(v);")
    @Import(module = "teavm", name = "isinf")
    @NoSideEffects
    @Unmanaged
    public static native boolean nativeIsInfinite(float v);

    public static boolean isFinite(float v) {
        if (PlatformDetector.isWebAssembly()) {
            return v < (1f / 0f) && v > (-1f / 0f);
        } else {
            return nativeIsFinite(v);
        }
    }

    @JSBody(params = "v", script = "return isFinite(v);")
    @Import(module = "teavm", name = "isfinite")
    @NoSideEffects
    @Unmanaged
    public static native boolean nativeIsFinite(float v);

    public static float getNaN() {
        if (PlatformDetector.isWebAssembly()) {
            return 0f / 0f;
        } else {
            return nativeGetNaN();
        }
    }

    @JSBody(script = "return NaN;")
    @Import(module = "teavm", name = "teavm_getNaN")
    @NoSideEffects
    @Unmanaged
    private static native float nativeGetNaN();

    public static float parseFloat(TString string) throws TNumberFormatException {
        return FloatingDecimalParser.parseFloat((String) (Object) string);
    }

    private static float decimalExponent(int n) {
        double d;
        if (n < 0) {
            d = 0.1;
            n = -n;
        } else {
            d = 10;
        }
        double result = 1;
        while (n != 0) {
            if (n % 2 != 0) {
                result *= d;
            }
            d *= d;
            n /= 2;
        }
        return (float) result;
    }

    public static TFloat valueOf(TString s) throws TNumberFormatException {
        return valueOf(parseFloat(s));
    }

    public boolean isNaN() {
        return isNaN(value);
    }

    public boolean isInfinite() {
        return isInfinite(value);
    }

    @NoSideEffects
    public static int compare(float f1, float f2) {
        if (f1 < f2) {
            return -1;
        }
        if (f1 > f2) {
            return 1;
        }
        int aBits = floatToIntBits(f1);
        int bBits = floatToIntBits(f2);
        return aBits == bBits ? 0 : aBits < bBits ? -1 : 1;
    }

    @Override
    public int compareTo(TFloat other) {
        return compare(value, other.value);
    }

    public static int floatToRawIntBits(float value) {
        return floatToIntBits(value);
    }

    @JSBody(params = "value", script = "return $rt_floatToIntBits(value);")
    @Import(name = "teavm_reinterpretFloatToInt")
    @NoSideEffects
    @Unmanaged
    public static native int floatToIntBits(float value);

    @JSBody(params = "bits", script = "return $rt_intBitsToFloat(bits);")
    @Import(name = "teavm_reinterpretIntToFloat")
    @NoSideEffects
    @Unmanaged
    public static native float intBitsToFloat(int bits);

    public static String toHexString(float f) {
        if (isNaN(f)) {
            return "NaN";
        } else if (isInfinite(f)) {
            return f > 0 ? "Infinity" : "-Infinity";
        } else if (f == 0) {
            return floatToRawIntBits(f) < 0 ? "-0x0.0p0" : "0x0.0p0";
        }
        char[] buffer = new char[18];
        int sz = 0;
        int bits = floatToIntBits(f);
        boolean subNormal = false;
        int exp = ((bits >>> 23) & 0xFF) - 127;
        int mantissa = (bits & 0x7FFFFF) << 1;
        if (exp == -127) {
            ++exp;
            subNormal = true;
        }
        for (int i = 0; i < 6; ++i) {
            int digit = mantissa & 0xF;
            if (digit > 0 || sz > 0) {
                buffer[sz++] = TCharacter.forDigit(digit, 16);
            }
            mantissa >>>= 4;
        }
        if (sz == 0) {
            buffer[sz++] = '0';
        }
        buffer[sz++] = '.';

        buffer[sz++] = subNormal ? '0' : '1';
        buffer[sz++] = 'x';
        buffer[sz++] = '0';
        if ((bits & (1L << 31)) != 0) {
            buffer[sz++] = '-';
        }
        int half = sz / 2;
        for (int i = 0; i < half; ++i) {
            char tmp = buffer[i];
            buffer[i] = buffer[sz - i - 1];
            buffer[sz - i - 1] = tmp;
        }

        buffer[sz++] = 'p';
        if (exp < 0) {
            exp = -exp;
            buffer[sz++] = '-';
        }
        int pos = 100;
        boolean first = true;
        for (int i = 0; i < 3; ++i) {
            int digit = exp / pos;
            if (digit > 0 || !first) {
                buffer[sz++] = TCharacter.forDigit(digit, 10);
                first = false;
            }
            exp %= pos;
            pos /= 10;
        }
        if (first) {
            buffer[sz++] = '0';
        }

        return new String(buffer, 0, sz);
    }
}
