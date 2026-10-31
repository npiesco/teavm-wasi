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
package org.teavm.classlib.java.text;

import java.util.Arrays;
import org.teavm.classlib.impl.unicode.UnicodeHelper;
import org.teavm.platform.metadata.StringResource;

/**
 * java.text.Normalizer: the Unicode normalization forms of UAX #15 (decomposition, canonical ordering and canonical
 * composition with algorithmic Hangul), using UnicodeData tables generated at build time.
 */
public final class TNormalizer {
    public enum Form {
        NFD,
        NFC,
        NFKD,
        NFKC
    }

    private static final int S_BASE = 0xAC00;
    private static final int L_BASE = 0x1100;
    private static final int V_BASE = 0x1161;
    private static final int T_BASE = 0x11A7;
    private static final int L_COUNT = 19;
    private static final int V_COUNT = 21;
    private static final int T_COUNT = 28;
    private static final int N_COUNT = V_COUNT * T_COUNT;
    private static final int S_COUNT = L_COUNT * N_COUNT;

    private static int[] cccCodes;
    private static int[] cccValues;
    private static int[] decompositionCodes;
    private static int[] decompositionOffsets;
    private static int[] decompositionData;
    private static long[] compositionKeys;
    private static int[] compositionValues;

    private TNormalizer() {
    }

    public static String normalize(CharSequence src, Form form) {
        if (src == null || form == null) {
            throw new NullPointerException();
        }
        ensureData();
        boolean compatibility = form == Form.NFKD || form == Form.NFKC;
        int[] decomposed = decompose(src, compatibility);
        int length = decomposed.length;
        if (form == Form.NFC || form == Form.NFKC) {
            length = compose(decomposed);
        }
        return new String(decomposed, 0, length);
    }

    public static boolean isNormalized(CharSequence src, Form form) {
        return normalize(src, form).contentEquals(src);
    }

    private static int[] decompose(CharSequence src, boolean compatibility) {
        int[] buffer = new int[src.length() + 8];
        int size = 0;
        for (int i = 0; i < src.length();) {
            int cp = Character.codePointAt(src, i);
            i += Character.charCount(cp);
            int start = size;
            buffer = ensureCapacity(buffer, size + 32);
            size = decomposeCodePoint(cp, compatibility, buffer, size);
            reorder(buffer, start, size);
        }
        return Arrays.copyOf(buffer, size);
    }

    private static int[] ensureCapacity(int[] buffer, int required) {
        return required <= buffer.length ? buffer : Arrays.copyOf(buffer, Math.max(required, buffer.length * 2));
    }

    private static int decomposeCodePoint(int cp, boolean compatibility, int[] out, int size) {
        int s = cp - S_BASE;
        if (s >= 0 && s < S_COUNT) {
            out[size++] = L_BASE + s / N_COUNT;
            out[size++] = V_BASE + (s % N_COUNT) / T_COUNT;
            int t = s % T_COUNT;
            if (t != 0) {
                out[size++] = T_BASE + t;
            }
            return size;
        }
        int index = Arrays.binarySearch(decompositionCodes, cp);
        if (index >= 0) {
            int offset = decompositionOffsets[index];
            int header = decompositionData[offset];
            if ((header & 1) == 0 || compatibility) {
                int length = header >> 1;
                for (int i = 0; i < length; ++i) {
                    size = decomposeCodePoint(decompositionData[offset + 1 + i], compatibility, out, size);
                }
                return size;
            }
        }
        out[size++] = cp;
        return size;
    }

    // Canonical ordering: a stable sort of the non-starters by combining class. Everything before `start` is
    // already ordered, so only the tail that the new code point extended needs moving into place.
    private static void reorder(int[] buffer, int start, int end) {
        int from = start;
        while (from > 0 && combiningClass(buffer[from - 1]) != 0) {
            from--;
        }
        for (int i = Math.max(from + 1, start); i < end; ++i) {
            int cp = buffer[i];
            int cc = combiningClass(cp);
            if (cc == 0) {
                continue;
            }
            int j = i;
            while (j > from && combiningClass(buffer[j - 1]) > cc) {
                buffer[j] = buffer[j - 1];
                j--;
            }
            buffer[j] = cp;
        }
    }

    private static int compose(int[] chars) {
        if (chars.length == 0) {
            return 0;
        }
        int starterPos = 0;
        int starter = chars[0];
        int lastClass = combiningClass(starter);
        if (lastClass != 0) {
            lastClass = 256;
        }
        int target = 1;
        for (int i = 1; i < chars.length; ++i) {
            int ch = chars[i];
            int chClass = combiningClass(ch);
            int composite = composePair(starter, ch);
            if (composite >= 0 && (lastClass < chClass || lastClass == 0)) {
                chars[starterPos] = composite;
                starter = composite;
            } else {
                if (chClass == 0) {
                    starterPos = target;
                    starter = ch;
                }
                lastClass = chClass;
                chars[target++] = ch;
            }
        }
        return target;
    }

    private static int composePair(int first, int second) {
        int l = first - L_BASE;
        if (l >= 0 && l < L_COUNT) {
            int v = second - V_BASE;
            return v >= 0 && v < V_COUNT ? S_BASE + (l * V_COUNT + v) * T_COUNT : -1;
        }
        int s = first - S_BASE;
        if (s >= 0 && s < S_COUNT && s % T_COUNT == 0) {
            int t = second - T_BASE;
            return t > 0 && t < T_COUNT ? first + t : -1;
        }
        int index = Arrays.binarySearch(compositionKeys, (long) first << 21 | second);
        return index >= 0 ? compositionValues[index] : -1;
    }

    private static int combiningClass(int cp) {
        int index = Arrays.binarySearch(cccCodes, cp);
        return index >= 0 ? cccValues[index] : 0;
    }

    private static void ensureData() {
        if (cccCodes != null) {
            return;
        }
        int[] ccc = UnicodeHelper.decodeIntDiff(obtainCombiningClasses().getValue());
        int[] codes = new int[ccc.length / 2];
        int[] values = new int[ccc.length / 2];
        for (int i = 0; i < codes.length; ++i) {
            codes[i] = ccc[i * 2];
            values[i] = ccc[i * 2 + 1];
        }

        int[] raw = UnicodeHelper.decodeIntDiff(obtainDecompositions().getValue());
        int count = 0;
        for (int i = 0; i < raw.length; i += 2 + (raw[i + 1] >> 1)) {
            count++;
        }
        int[] decompCodes = new int[count];
        int[] decompOffsets = new int[count];
        int k = 0;
        for (int i = 0; i < raw.length; i += 2 + (raw[i + 1] >> 1)) {
            decompCodes[k] = raw[i];
            decompOffsets[k] = i + 1;
            k++;
        }

        int[] triples = UnicodeHelper.decodeIntDiff(obtainCompositions().getValue());
        int pairs = triples.length / 3;
        long[] keys = new long[pairs];
        for (int i = 0; i < pairs; ++i) {
            keys[i] = (long) triples[i * 3] << 21 | triples[i * 3 + 1];
        }
        Integer[] order = new Integer[pairs];
        for (int i = 0; i < pairs; ++i) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Long.compare(keys[a], keys[b]));
        long[] sortedKeys = new long[pairs];
        int[] sortedValues = new int[pairs];
        for (int i = 0; i < pairs; ++i) {
            sortedKeys[i] = keys[order[i]];
            sortedValues[i] = triples[order[i] * 3 + 2];
        }

        decompositionData = raw;
        decompositionCodes = decompCodes;
        decompositionOffsets = decompOffsets;
        compositionKeys = sortedKeys;
        compositionValues = sortedValues;
        cccValues = values;
        cccCodes = codes;
    }

    private static native StringResource obtainCombiningClasses();

    private static native StringResource obtainDecompositions();

    private static native StringResource obtainCompositions();
}
