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
 */package org.teavm.classlib.impl.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.classlib.impl.Base46;
import org.teavm.classlib.impl.CharFlow;

// A rule set's collation elements, as CollationMetadataGenerator encodes them.
public final class CollationTable {
    public static final int FRENCH_SECONDARY = 1;
    public static final int SE_ASIAN_SWAPPING = 2;

    public final boolean frenchSecondary;
    public final boolean seAsianSwapping;
    public final int strength;
    public final int decomposition;
    public final int maxSecOrder;
    public final int maxTerOrder;
    // Every table maps most of the BMP and all decoded tables stay cached, so BMP mappings are indexed directly by
    // char rather than boxed into a map.
    private final int[][] bmpMappings = new int[0x10000][];
    private final Map<Integer, int[]> supplementaryMappings = new HashMap<>();
    // Contracting sequences by their first character, longest first.
    public final Map<Integer, List<String>> contractionKeys = new HashMap<>();
    public final Map<String, int[]> contractions = new HashMap<>();

    private CollationTable(CharFlow flow) {
        int flags = next(flow);
        frenchSecondary = (flags & FRENCH_SECONDARY) != 0;
        seAsianSwapping = (flags & SE_ASIAN_SWAPPING) != 0;
        strength = next(flow);
        decomposition = next(flow);
        maxSecOrder = next(flow);
        maxTerOrder = next(flow);
        int mappingCount = next(flow);
        for (int i = 0; i < mappingCount; ++i) {
            int cp = next(flow);
            int[] orders = orders(flow);
            if (cp < bmpMappings.length) {
                bmpMappings[cp] = orders;
            } else {
                supplementaryMappings.put(cp, orders);
            }
        }
        int contractionCount = next(flow);
        for (int i = 0; i < contractionCount; ++i) {
            int length = next(flow);
            char[] chars = new char[length];
            for (int j = 0; j < length; ++j) {
                chars[j] = (char) next(flow);
            }
            int[] orders = orders(flow);
            String key = new String(chars);
            contractions.put(key, orders);
            List<String> keys = contractionKeys.computeIfAbsent(key.codePointAt(0), k -> new ArrayList<>());
            int at = 0;
            while (at < keys.size() && keys.get(at).length() >= key.length()) {
                at++;
            }
            keys.add(at, key);
        }
    }

    // Collation elements use all 32 bits, so every value is written as an unsigned 32-bit number.
    public static String encode(int[] data) {
        StringBuilder sb = new StringBuilder();
        Base46.encodeUnsigned(sb, data.length);
        for (int value : data) {
            Base46.encodeUnsigned(sb, value & 0xFFFFFFFFL);
        }
        return sb.toString();
    }

    // The elements of code point cp alone, or null when the rules do not map it.
    public int[] mapping(int cp) {
        return cp < bmpMappings.length ? bmpMappings[cp] : supplementaryMappings.get(cp);
    }

    private static int next(CharFlow flow) {
        return (int) Base46.decodeUnsignedLong(flow);
    }

    private static int[] orders(CharFlow flow) {
        int[] orders = new int[next(flow)];
        for (int i = 0; i < orders.length; ++i) {
            orders[i] = next(flow);
        }
        return orders;
    }

    public static CollationTable decode(String encoded) {
        CharFlow flow = new CharFlow(encoded.toCharArray());
        // The leading total length lets encode stay a flat array; the structure itself carries every count.
        Base46.decodeUnsigned(flow);
        return new CollationTable(flow);
    }
}