/*
 *  Copyright 2015 Alexey Andreev.
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

import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.impl.ParseSupport;
import org.teavm.classlib.impl.security.Md5;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TComparable;
import org.teavm.interop.Address;
import org.teavm.interop.wasi.Wasi;

// The JDK's UUID: 128 bits as two longs, with the RFC 4122 layout for version, variant and time-based fields.
public final class TUUID implements TSerializable, TComparable<TUUID> {
    private static final String DIGITS = "0123456789abcdef";

    private final long mostSigBits;
    private final long leastSigBits;

    public TUUID(long mostSigBits, long leastSigBits) {
        this.mostSigBits = mostSigBits;
        this.leastSigBits = leastSigBits;
    }

    private TUUID(byte[] data) {
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; ++i) {
            msb = (msb << 8) | (data[i] & 0xFF);
        }
        for (int i = 8; i < 16; ++i) {
            lsb = (lsb << 8) | (data[i] & 0xFF);
        }
        mostSigBits = msb;
        leastSigBits = lsb;
    }

    // Version 4: random bits from the platform's random source.
    public static TUUID randomUUID() {
        byte[] data = new byte[16];
        if (PlatformDetector.isWebAssembly()) {
            short errno = Wasi.randomGet(Address.ofData(data), data.length);
            if (errno != Wasi.ERRNO_SUCCESS) {
                throw new IllegalStateException(Wasi.errnoMessage("random_get", errno));
            }
        } else {
            for (int i = 0; i < data.length; ++i) {
                data[i] = (byte) (Math.random() * 256);
            }
        }
        data[6] = (byte) ((data[6] & 0x0F) | 0x40);
        data[8] = (byte) ((data[8] & 0x3F) | 0x80);
        return new TUUID(data);
    }

    // Version 3: the MD5 hash of name.
    public static TUUID nameUUIDFromBytes(byte[] name) {
        Md5 md5 = new Md5();
        md5.update(name, 0, name.length);
        byte[] data = md5.digest();
        data[6] = (byte) ((data[6] & 0x0F) | 0x30);
        data[8] = (byte) ((data[8] & 0x3F) | 0x80);
        return new TUUID(data);
    }

    public static TUUID fromString(String name) {
        if (name.length() == 36 && name.charAt(8) == '-' && name.charAt(13) == '-' && name.charAt(18) == '-'
                && name.charAt(23) == '-') {
            long msb1 = nibbles(name, 0);
            long msb2 = nibbles(name, 4);
            long msb3 = nibbles(name, 9);
            long msb4 = nibbles(name, 14);
            long lsb1 = nibbles(name, 19);
            long lsb2 = nibbles(name, 24);
            long lsb3 = nibbles(name, 28);
            long lsb4 = nibbles(name, 32);
            if ((msb1 | msb2 | msb3 | msb4 | lsb1 | lsb2 | lsb3 | lsb4) >= 0) {
                return new TUUID(msb1 << 48 | msb2 << 32 | msb3 << 16 | msb4,
                        lsb1 << 48 | lsb2 << 32 | lsb3 << 16 | lsb4);
            }
        }
        return fromStringLenient(name);
    }

    // Four ASCII hex digits at offset as a value, or -1 when any is not one.
    private static long nibbles(String name, int offset) {
        long result = 0;
        for (int i = 0; i < 4; ++i) {
            char c = name.charAt(offset + i);
            int digit;
            if (c >= '0' && c <= '9') {
                digit = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                digit = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                digit = c - 'A' + 10;
            } else {
                return -1;
            }
            result = (result << 4) | digit;
        }
        return result;
    }

    // Five dash-separated hex fields of any length, each masked to its width.
    private static TUUID fromStringLenient(String name) {
        int length = name.length();
        if (length > 36) {
            throw new IllegalArgumentException("UUID string too large");
        }
        int dash1 = name.indexOf('-', 0);
        int dash2 = name.indexOf('-', dash1 + 1);
        int dash3 = name.indexOf('-', dash2 + 1);
        int dash4 = name.indexOf('-', dash3 + 1);
        int dash5 = name.indexOf('-', dash4 + 1);
        if (dash4 < 0 || dash5 >= 0) {
            throw new IllegalArgumentException("Invalid UUID string: " + name);
        }
        long msb = hex(name, 0, dash1) & 0xFFFFFFFFL;
        msb <<= 16;
        msb |= hex(name, dash1 + 1, dash2) & 0xFFFFL;
        msb <<= 16;
        msb |= hex(name, dash2 + 1, dash3) & 0xFFFFL;
        long lsb = hex(name, dash3 + 1, dash4) & 0xFFFFL;
        lsb <<= 48;
        lsb |= hex(name, dash4 + 1, length) & 0xFFFFFFFFFFFFL;
        return new TUUID(msb, lsb);
    }

    private static long hex(String name, int begin, int end) {
        return ParseSupport.parseSigned(name, begin, end, 16, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public long getLeastSignificantBits() {
        return leastSigBits;
    }

    public long getMostSignificantBits() {
        return mostSigBits;
    }

    public int version() {
        return (int) ((mostSigBits >> 12) & 0x0F);
    }

    public int variant() {
        return (int) ((leastSigBits >>> (64 - (leastSigBits >>> 62))) & (leastSigBits >> 63));
    }

    public long timestamp() {
        checkTimeBased();
        return (mostSigBits & 0x0FFFL) << 48 | ((mostSigBits >> 16) & 0x0FFFFL) << 32 | mostSigBits >>> 32;
    }

    public int clockSequence() {
        checkTimeBased();
        return (int) ((leastSigBits & 0x3FFF000000000000L) >>> 48);
    }

    public long node() {
        checkTimeBased();
        return leastSigBits & 0x0000FFFFFFFFFFFFL;
    }

    private void checkTimeBased() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }
    }

    @Override
    public String toString() {
        char[] chars = new char[36];
        digits(chars, 0, mostSigBits >>> 32, 8);
        chars[8] = '-';
        digits(chars, 9, mostSigBits >>> 16, 4);
        chars[13] = '-';
        digits(chars, 14, mostSigBits, 4);
        chars[18] = '-';
        digits(chars, 19, leastSigBits >>> 48, 4);
        chars[23] = '-';
        digits(chars, 24, leastSigBits, 12);
        return new String(chars);
    }

    private static void digits(char[] chars, int offset, long value, int count) {
        for (int i = count - 1; i >= 0; --i) {
            chars[offset + i] = DIGITS.charAt((int) (value & 0xF));
            value >>>= 4;
        }
    }

    @Override
    public int hashCode() {
        long hilo = mostSigBits ^ leastSigBits;
        return ((int) (hilo >> 32)) ^ (int) hilo;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        TUUID other = (TUUID) obj;
        return mostSigBits == other.mostSigBits && leastSigBits == other.leastSigBits;
    }

    @Override
    public int compareTo(TUUID other) {
        int most = Long.compare(mostSigBits, other.mostSigBits);
        return most != 0 ? most : Long.compare(leastSigBits, other.leastSigBits);
    }
}
