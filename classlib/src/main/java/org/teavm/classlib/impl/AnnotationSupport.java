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

import java.util.Arrays;

/**
 * Runtime behaviour of generated annotation implementations, as specified for annotation instances on the JDK:
 * {@code toString} in source-like form, member-wise {@code equals}, and {@code hashCode} as the sum over members
 * of {@code (127 * name.hashCode()) ^ valueHash}. Member values arrive boxed, arrays as is.
 */
public final class AnnotationSupport {
    private AnnotationSupport() {
    }

    // order: member names in the JDK's member-value order (defaults first, in the order of the JDK's defaults map,
    // then explicitly given members in class-file order); names/values: every member, aligned.
    public static String toString(String canonicalName, String[] order, String[] names, Object[] values) {
        StringBuilder sb = new StringBuilder("@").append(canonicalName).append('(');
        boolean lone = order.length == 1;
        for (int i = 0; i < order.length; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            if (!lone || !order[i].equals("value")) {
                sb.append(order[i]).append('=');
            }
            sb.append(memberValueToString(values[indexOf(names, order[i])]));
        }
        return sb.append(')').toString();
    }

    private static int indexOf(String[] names, String name) {
        for (int i = 0; i < names.length; ++i) {
            if (names[i].equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException(name);
    }

    public static int hashCode(String[] names, Object[] values) {
        int result = 0;
        for (int i = 0; i < names.length; ++i) {
            result += (127 * names[i].hashCode()) ^ memberValueHashCode(values[i]);
        }
        return result;
    }

    public static boolean memberValueEquals(Object a, Object b) {
        if (!a.getClass().isArray()) {
            return a.equals(b);
        }
        if (a instanceof Object[] && b instanceof Object[]) {
            return Arrays.equals((Object[]) a, (Object[]) b);
        }
        if (b == null || b.getClass() != a.getClass()) {
            return false;
        }
        if (a instanceof byte[]) {
            return Arrays.equals((byte[]) a, (byte[]) b);
        } else if (a instanceof char[]) {
            return Arrays.equals((char[]) a, (char[]) b);
        } else if (a instanceof double[]) {
            return Arrays.equals((double[]) a, (double[]) b);
        } else if (a instanceof float[]) {
            return Arrays.equals((float[]) a, (float[]) b);
        } else if (a instanceof int[]) {
            return Arrays.equals((int[]) a, (int[]) b);
        } else if (a instanceof long[]) {
            return Arrays.equals((long[]) a, (long[]) b);
        } else if (a instanceof short[]) {
            return Arrays.equals((short[]) a, (short[]) b);
        }
        return Arrays.equals((boolean[]) a, (boolean[]) b);
    }

    private static int memberValueHashCode(Object value) {
        if (!value.getClass().isArray()) {
            return value.hashCode();
        }
        if (value instanceof byte[]) {
            return Arrays.hashCode((byte[]) value);
        } else if (value instanceof char[]) {
            return Arrays.hashCode((char[]) value);
        } else if (value instanceof double[]) {
            return Arrays.hashCode((double[]) value);
        } else if (value instanceof float[]) {
            return Arrays.hashCode((float[]) value);
        } else if (value instanceof int[]) {
            return Arrays.hashCode((int[]) value);
        } else if (value instanceof long[]) {
            return Arrays.hashCode((long[]) value);
        } else if (value instanceof short[]) {
            return Arrays.hashCode((short[]) value);
        } else if (value instanceof boolean[]) {
            return Arrays.hashCode((boolean[]) value);
        }
        return Arrays.hashCode((Object[]) value);
    }

    private static String memberValueToString(Object value) {
        if (!value.getClass().isArray()) {
            return scalarToString(value);
        }
        StringBuilder sb = new StringBuilder("{");
        int length = java.lang.reflect.Array.getLength(value);
        for (int i = 0; i < length; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            Object item = java.lang.reflect.Array.get(value, i);
            if (value instanceof short[] || value instanceof int[] || value instanceof boolean[]) {
                sb.append(item);
            } else {
                sb.append(scalarToString(item));
            }
        }
        return sb.append('}').toString();
    }

    private static String scalarToString(Object value) {
        if (value instanceof Class) {
            String name = ((Class<?>) value).getCanonicalName();
            return (name != null ? name : "<no canonical name>") + ".class";
        } else if (value instanceof String) {
            StringBuilder sb = new StringBuilder("\"");
            String s = (String) value;
            for (int i = 0; i < s.length(); ++i) {
                quote(sb, s.charAt(i));
            }
            return sb.append('"').toString();
        } else if (value instanceof Character) {
            StringBuilder sb = new StringBuilder("'");
            quote(sb, (Character) value);
            return sb.append('\'').toString();
        } else if (value instanceof Double) {
            double d = (Double) value;
            if (Double.isNaN(d)) {
                return "0.0/0.0";
            } else if (Double.isInfinite(d)) {
                return d < 0 ? "-1.0/0.0" : "1.0/0.0";
            }
            return Double.toString(d);
        } else if (value instanceof Float) {
            float f = (Float) value;
            if (Float.isNaN(f)) {
                return "0.0f/0.0f";
            } else if (Float.isInfinite(f)) {
                return f < 0 ? "-1.0f/0.0f" : "1.0f/0.0f";
            }
            return Float.toString(f) + "f";
        } else if (value instanceof Long) {
            return value + "L";
        } else if (value instanceof Byte) {
            int b = (Byte) value & 0xFF;
            return "(byte)0x" + Character.forDigit(b >> 4, 16) + Character.forDigit(b & 0xF, 16);
        } else if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return value.toString();
    }

    private static void quote(StringBuilder sb, char c) {
        switch (c) {
            case '\b':
                sb.append("\\b");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            case '\'':
                sb.append("\\'");
                break;
            case '"':
                sb.append("\\\"");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            default:
                if (c >= ' ' && c <= '~') {
                    sb.append(c);
                } else {
                    sb.append("\\u");
                    String hex = Integer.toHexString(c);
                    for (int i = hex.length(); i < 4; ++i) {
                        sb.append('0');
                    }
                    sb.append(hex);
                }
                break;
        }
    }
}