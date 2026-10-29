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

import java.util.TimeZone;
import org.teavm.backend.c.intrinsic.RuntimeInclude;
import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.java.lang.TComparable;
import org.teavm.classlib.java.lang.TSystem;
import org.teavm.interop.Import;
import org.teavm.interop.NoSideEffects;
import org.teavm.interop.Platforms;
import org.teavm.interop.Unmanaged;
import org.teavm.interop.UnsupportedOn;
import org.teavm.jso.core.JSDate;

public class TDate implements TComparable<TDate> {
    private long value;

    static {
        if (PlatformDetector.isC()) {
            initLowLevel();
        }
    }

    @Import(name = "teavm_date_init")
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    @NoSideEffects
    @Unmanaged
    private static native void initLowLevel();

    public TDate() {
        value = TSystem.currentTimeMillis();
    }

    public TDate(long date) {
        this.value = date;
    }

    @Deprecated
    public TDate(int year, int month, int date) {
        this(year, month, date, 0, 0);
    }

    @Deprecated
    public TDate(int year, int month, int date, int hrs, int min) {
        this(year, month, date, hrs, min, 0);
    }

    @Deprecated
    public TDate(int year, int month, int date, int hrs, int min, int sec) {
        this(PlatformDetector.isWebAssembly()
                ? composeUtc(year, month, date, hrs, min, sec)
                : PlatformDetector.isLowLevel()
                ? initDateLowLevel(year, month, date, hrs, min, sec)
                : (long) JSDate.create(year, month, date, hrs, min, sec).getTime());
        if (!PlatformDetector.isLowLevel()) {
            setYear(year);
        }
    }

    @Import(name = "teavm_date_create")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native long initDateLowLevel(int year, int month, int date, int hrs, int min, int sec);

    public TDate(String s) {
        this(parse(s));
    }

    @Override
    public Object clone() {
        return new TDate(value);
    }

    @Deprecated
    public static long UTC(int year, int month, int date, int hrs, int min, int sec) {
        if (PlatformDetector.isWebAssembly()) {
            return composeUtc(year, month, date, hrs, min, sec);
        }
        if (PlatformDetector.isLowLevel()) {
            return initUtcDateLowLevel(year, month, date, hrs, min, sec);
        } else {
            return (long) JSDate.UTC(year, month, date, hrs, min, sec);
        }
    }

    @Import(name = "teavm_date_createUtc")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native long initUtcDateLowLevel(int year, int month, int date, int hrs, int min, int sec);

    @Deprecated
    public static long parse(String s) {
        if (PlatformDetector.isWebAssembly()) {
            return parseUtc(s);
        }
        if (PlatformDetector.isLowLevel()) {
            return parseLowLevel(s);
        }

        double value = JSDate.parse(s);
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Can't parse date: " + s);
        }
        return (long) value;
    }

    @Import(name = "teavm_date_parse")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native long parseLowLevel(String s);

    @Deprecated
    public int getYear() {
        if (PlatformDetector.isWebAssembly()) {
            return fieldsUtc(value)[0];
        }
        if (PlatformDetector.isLowLevel()) {
            return getYearLowLevel(value);
        }
        return JSDate.create(value).getFullYear() - 1900;
    }

    @Import(name = "teavm_date_getYear")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native int getYearLowLevel(long date);

    @Deprecated
    public void setYear(int year) {
        if (PlatformDetector.isWebAssembly()) {
            setFieldUtc(0, year);
            return;
        }
        if (PlatformDetector.isLowLevel()) {
            value = setYearLowLevel(value, year);
            return;
        }
        JSDate date = JSDate.create(value);
        date.setFullYear(year + 1900);
        value = (long) date.getTime();
    }

    @Import(name = "teavm_date_setYear")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native long setYearLowLevel(long date, int year);

    @Deprecated
    public int getMonth() {
        if (PlatformDetector.isWebAssembly()) {
            return fieldsUtc(value)[1];
        }
        if (PlatformDetector.isLowLevel()) {
            return getMonthLowLevel(value);
        }
        return JSDate.create(value).getMonth();
    }

    @Import(name = "teavm_date_getMonth")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native int getMonthLowLevel(long date);

    @Deprecated
    public void setMonth(int month) {
        if (PlatformDetector.isWebAssembly()) {
            setFieldUtc(1, month);
            return;
        }
        if (PlatformDetector.isLowLevel()) {
            value = setMonthLowLevel(value, month);
            return;
        }
        JSDate date = JSDate.create(value);
        date.setMonth(month);
        value = (long) date.getTime();
    }

    @Import(name = "teavm_date_setMonth")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native long setMonthLowLevel(long date, int month);

    @Deprecated
    public int getDate() {
        if (PlatformDetector.isWebAssembly()) {
            return fieldsUtc(value)[2];
        }
        if (PlatformDetector.isLowLevel()) {
            return getDateLowLevel(value);
        }
        return JSDate.create(value).getDate();
    }

    @Import(name = "teavm_date_getDate")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native int getDateLowLevel(long date);

    @Deprecated
    public void setDate(int date) {
        if (PlatformDetector.isWebAssembly()) {
            setFieldUtc(2, date);
            return;
        }
        if (PlatformDetector.isLowLevel()) {
            value = setDateLowLevel(value, date);
            return;
        }
        JSDate d = JSDate.create(value);
        d.setDate(date);
        this.value = (long) d.getTime();
    }

    @Import(name = "teavm_date_setDate")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native int setDateLowLevel(long target, int date);

    @Deprecated
    public int getDay() {
        if (PlatformDetector.isWebAssembly()) {
            return fieldsUtc(value)[7];
        }
        if (PlatformDetector.isLowLevel()) {
            return getDayLowLevel(value);
        }
        return JSDate.create(value).getDay();
    }

    @Import(name = "teavm_date_getDay")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    @Unmanaged
    public static native int getDayLowLevel(long date);

    @Deprecated
    public int getHours() {
        if (PlatformDetector.isWebAssembly()) {
            return fieldsUtc(value)[3];
        }
        if (PlatformDetector.isLowLevel()) {
            return getHoursLowLevel(value);
        }
        return JSDate.create(value).getHours();
    }

    @Import(name = "teavm_date_getHours")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native int getHoursLowLevel(long date);

    @Deprecated
    public void setHours(int hours) {
        if (PlatformDetector.isWebAssembly()) {
            setFieldUtc(3, hours);
            return;
        }
        if (PlatformDetector.isLowLevel()) {
            value = setHoursLowLevel(value, hours);
            return;
        }
        JSDate date = JSDate.create(value);
        date.setHours(hours);
        value = (long) date.getTime();
    }

    @Import(name = "teavm_date_setHours")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native int setHoursLowLevel(long date, int hours);

    @Deprecated
    public int getMinutes() {
        if (PlatformDetector.isWebAssembly()) {
            return fieldsUtc(value)[4];
        }
        if (PlatformDetector.isLowLevel()) {
            return getMinutesLowLevel(value);
        }
        return JSDate.create(value).getMinutes();
    }

    @Import(name = "teavm_date_getMinutes")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native int getMinutesLowLevel(long date);

    @Deprecated
    public void setMinutes(int minutes) {
        if (PlatformDetector.isWebAssembly()) {
            setFieldUtc(4, minutes);
            return;
        }
        if (PlatformDetector.isLowLevel()) {
            value = setMinutesLowLevel(value, minutes);
            return;
        }
        JSDate date = JSDate.create(value);
        date.setMinutes(minutes);
        this.value = (long) date.getTime();
    }

    @Import(name = "teavm_date_setMinutes")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native int setMinutesLowLevel(long date, int minutes);

    @Deprecated
    public int getSeconds() {
        if (PlatformDetector.isWebAssembly()) {
            return fieldsUtc(value)[5];
        }
        if (PlatformDetector.isLowLevel()) {
            return getSecondsLowLevel(value);
        }
        return JSDate.create(value).getSeconds();
    }

    @Import(name = "teavm_date_getSeconds")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native int getSecondsLowLevel(long date);

    @Deprecated
    public void setSeconds(int seconds) {
        if (PlatformDetector.isWebAssembly()) {
            setFieldUtc(5, seconds);
            return;
        }
        if (PlatformDetector.isLowLevel()) {
            value = setSecondsLowLevel(value, seconds);
            return;
        }
        JSDate date = JSDate.create(value);
        date.setSeconds(seconds);
        this.value = (long) date.getTime();
    }

    @Import(name = "teavm_date_setSeconds")
    @NoSideEffects
    @Unmanaged
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native int setSecondsLowLevel(long date, int seconds);

    public long getTime() {
        return value;
    }

    public void setTime(long time) {
        value = time;
    }

    public boolean before(TDate when) {
        return value < when.value;
    }

    public boolean after(TDate when) {
        return value > when.value;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TDate)) {
            return false;
        }
        TDate other = (TDate) obj;
        return value == other.value;
    }

    @Override
    public int compareTo(TDate other) {
        return Long.compare(value, other.value);
    }

    @Override
    public int hashCode() {
        return (int) value ^ (int) (value >>> 32);
    }

    @Override
    public String toString() {
        if (PlatformDetector.isWebAssembly()) {
            int[] f = fieldsUtc(value);
            StringBuilder sb = new StringBuilder();
            sb.append(DAY_NAMES[f[7]]).append(' ').append(MONTH_NAMES[f[1]]).append(' ');
            pad2(sb, f[2]).append(' ');
            pad2(sb, f[3]).append(':');
            pad2(sb, f[4]).append(':');
            pad2(sb, f[5]).append(" UTC ").append(1900L + f[0]);
            return sb.toString();
        }
        if (PlatformDetector.isLowLevel()) {
            return toStringLowLevel(value);
        } else {
            return JSDate.create(value).stringValue();
        }
    }

    @Import(name = "teavm_date_format")
    @NoSideEffects
    @RuntimeInclude("date.h")
    @UnsupportedOn(Platforms.WEBASSEMBLY)
    private static native String toStringLowLevel(long date);

    @Deprecated
    public String toLocaleString() {
        return JSDate.create(value).toLocaleFormat("%c");
    }

    @Deprecated
    public String toGMTString() {
        if (PlatformDetector.isWebAssembly()) {
            int[] f = fieldsUtc(value);
            StringBuilder sb = new StringBuilder();
            sb.append(f[2]).append(' ').append(MONTH_NAMES[f[1]]).append(' ').append(1900L + f[0]).append(' ');
            pad2(sb, f[3]).append(':');
            pad2(sb, f[4]).append(':');
            pad2(sb, f[5]).append(" GMT");
            return sb.toString();
        }
        return JSDate.create(value).toUTCString();
    }

    @Deprecated
    public int getTimezoneOffset() {
        return -TimeZone.getDefault().getOffset(value) / (1000 * 60);
    }

    // WebAssembly (WASI) has no time-zone data: java.util.Date operates in UTC,
    // exactly as a JVM started with -Duser.timezone=UTC. Proleptic Gregorian
    // arithmetic from Howard Hinnant's days_from_civil / civil_from_days.
    private static final long MS_PER_DAY = 86400000L;
    private static final String[] DAY_NAMES = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
    private static final String[] MONTH_NAMES = { "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

    private static long daysFromCivil(long y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = Math.floorDiv(y, 400L);
        long yoe = y - era * 400;
        long doy = (153L * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    // The vocabulary Date.parse recognizes (java.util.Date#parse): AM/PM, weekdays, months, then zones.
    private static final String[] PARSE_WORDS = { "am", "pm", "monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday", "january", "february", "march", "april", "may", "june", "july",
            "august", "september", "october", "november", "december", "gmt", "ut", "utc", "est", "edt", "cst",
            "cdt", "mst", "mdt", "pst", "pdt" };
    private static final int PARSE_FIRST_MONTH = 9;
    private static final int PARSE_FIRST_UTC = 21;
    private static final int PARSE_FIRST_ZONE = 24;
    private static int parseCenturyStart;

    // Date.parse with the JDK's observable behaviour; on WASI the local zone is UTC. Words resolve against
    // the vocabulary from its end (so "Ma" is May and "Ju" is July, as on the JDK), need at least two letters,
    // and 12 AM is midnight while 12 PM stays noon.
    private static long parseUtc(String s) {
        int year = Integer.MIN_VALUE;
        int mon = -1;
        int mday = -1;
        int hour = -1;
        int min = -1;
        int sec = -1;
        int tzoffset = -1;
        char prevc = 0;
        int i = 0;
        int limit = s == null ? 0 : s.length();
        while (i < limit) {
            char c = s.charAt(i++);
            if (c <= ' ' || c == ',') {
                continue;
            }
            if (c == '(') {
                int depth = 1;
                while (i < limit) {
                    c = s.charAt(i++);
                    if (c == '(') {
                        depth++;
                    } else if (c == ')' && --depth <= 0) {
                        break;
                    }
                }
                continue;
            }
            if (c >= '0' && c <= '9') {
                int n = c - '0';
                while (i < limit && (c = s.charAt(i)) >= '0' && c <= '9') {
                    n = n * 10 + c - '0';
                    i++;
                }
                if ((prevc == '+' || prevc == '-') && year != Integer.MIN_VALUE) {
                    n = n < 24 ? n * 60 : n % 100 + n / 100 * 60;
                    if (prevc == '+') {
                        n = -n;
                    }
                    if (tzoffset != 0 && tzoffset != -1) {
                        throw parseFailure(s);
                    }
                    tzoffset = n;
                } else if (n >= 70) {
                    if (year != Integer.MIN_VALUE || !(c <= ' ' || c == ',' || c == '/' || i >= limit)) {
                        throw parseFailure(s);
                    }
                    year = n;
                } else if (c == ':') {
                    if (hour < 0) {
                        hour = n;
                    } else if (min < 0) {
                        min = n;
                    } else {
                        throw parseFailure(s);
                    }
                } else if (c == '/') {
                    if (mon < 0) {
                        mon = n - 1;
                    } else if (mday < 0) {
                        mday = n;
                    } else {
                        throw parseFailure(s);
                    }
                } else if (i < limit && c != ',' && c > ' ' && c != '-') {
                    throw parseFailure(s);
                } else if (hour >= 0 && min < 0) {
                    min = n;
                } else if (min >= 0 && sec < 0) {
                    sec = n;
                } else if (mday < 0) {
                    mday = n;
                } else if (year == Integer.MIN_VALUE && mon >= 0) {
                    year = n;
                } else {
                    throw parseFailure(s);
                }
                prevc = 0;
            } else if (c == '/' || c == ':' || c == '+' || c == '-') {
                prevc = c;
            } else {
                int start = i - 1;
                while (i < limit) {
                    c = s.charAt(i);
                    if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) {
                        break;
                    }
                    i++;
                }
                if (i <= start + 1) {
                    throw parseFailure(s);
                }
                int word = PARSE_WORDS.length;
                while (--word >= 0) {
                    if (PARSE_WORDS[word].regionMatches(true, 0, s, start, i - start)) {
                        break;
                    }
                }
                if (word < 0) {
                    throw parseFailure(s);
                }
                if (word <= 1) {
                    if (hour > 12 || hour < 1) {
                        throw parseFailure(s);
                    }
                    if (word == 0 && hour == 12) {
                        hour = 0;
                    } else if (word == 1 && hour < 12) {
                        hour += 12;
                    }
                } else if (word >= PARSE_FIRST_ZONE) {
                    int zone = word - PARSE_FIRST_ZONE;
                    tzoffset = 300 + zone / 2 * 60 - (zone % 2 == 1 ? 60 : 0);
                } else if (word >= PARSE_FIRST_UTC) {
                    tzoffset = 0;
                } else if (word >= PARSE_FIRST_MONTH) {
                    if (mon >= 0) {
                        throw parseFailure(s);
                    }
                    mon = word - PARSE_FIRST_MONTH;
                }
            }
        }
        if (year == Integer.MIN_VALUE || mon < 0 || mday < 0) {
            throw parseFailure(s);
        }
        if (year < 100) {
            if (parseCenturyStart == 0) {
                parseCenturyStart = 1900 + fieldsUtc(System.currentTimeMillis())[0] - 80;
            }
            year += parseCenturyStart / 100 * 100;
            if (year < parseCenturyStart) {
                year += 100;
            }
        }
        long time = composeUtc(year - 1900, mon, mday, Math.max(hour, 0), Math.max(min, 0), Math.max(sec, 0));
        return tzoffset != -1 ? time + tzoffset * 60000L : time;
    }

    private static IllegalArgumentException parseFailure(String s) {
        return new IllegalArgumentException("Can't parse date: " + s);
    }
    private static long composeUtc(int year, int month, int date, int hrs, int min, int sec) {
        long y = 1900L + year + Math.floorDiv(month, 12);
        int m = Math.floorMod(month, 12) + 1;
        long days = daysFromCivil(y, m, 1) + (date - 1L);
        return days * MS_PER_DAY + hrs * 3600000L + min * 60000L + sec * 1000L;
    }

    // {year - 1900, month (0-11), date, hours, minutes, seconds, millis, day-of-week (0=Sunday)}
    private static int[] fieldsUtc(long time) {
        long days = Math.floorDiv(time, MS_PER_DAY);
        long msOfDay = Math.floorMod(time, MS_PER_DAY);
        long z = days + 719468;
        long era = Math.floorDiv(z, 146097L);
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        int d = (int) (doy - (153 * mp + 2) / 5 + 1);
        int m = (int) (mp < 10 ? mp + 3 : mp - 9);
        long y = yoe + era * 400 + (m <= 2 ? 1 : 0);
        return new int[] { (int) (y - 1900), m - 1, d, (int) (msOfDay / 3600000), (int) (msOfDay / 60000 % 60),
                (int) (msOfDay / 1000 % 60), (int) (msOfDay % 1000), (int) Math.floorMod(days + 4, 7L) };
    }

    private void setFieldUtc(int index, int fieldValue) {
        int[] f = fieldsUtc(value);
        f[index] = fieldValue;
        value = composeUtc(f[0], f[1], f[2], f[3], f[4], f[5]) + f[6];
    }

    private static StringBuilder pad2(StringBuilder sb, int v) {
        if (v < 10) {
            sb.append('0');
        }
        return sb.append(v);
    }
}