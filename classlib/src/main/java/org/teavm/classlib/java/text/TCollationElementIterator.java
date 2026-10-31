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
 */package org.teavm.classlib.java.text;

import java.text.Normalizer;
import java.util.List;
import org.teavm.classlib.impl.text.CollationTable;

/**
 * Collation elements of a text, as java.text.CollationElementIterator yields them: the longest contracting sequence
 * the rules declare at each position, otherwise the code point's own elements; a code point the rules do not map
 * yields UNMAPPED_CHAR_VALUE followed by the code point (its UTF-16 units for a supplementary one) shifted into the
 * primary order; Thai and Lao prevowels are ordered after the consonant following them when the rules ask for it,
 * and elements are masked to the collator's strength.
 */
public final class TCollationElementIterator {
    public static final int NULLORDER = 0xffffffff;
    static final int UNMAPPED_CHAR_VALUE = 0x7FFF0000;

    private final TRuleBasedCollator owner;
    private final CollationTable table;
    private String source;
    private String text;
    private int textMode = -1;
    private int pos;
    private int[] buffer;
    private int expIndex;
    private int swapOrder;

    TCollationElementIterator(String source, TRuleBasedCollator owner) {
        this.owner = owner;
        this.table = owner.table;
        setText(source);
    }

    public void reset() {
        pos = 0;
        buffer = null;
        expIndex = 0;
        swapOrder = 0;
    }

    public void setText(String source) {
        this.source = source;
        this.text = null;
        reset();
    }

    public int getOffset() {
        return pos;
    }

    public int next() {
        int mode = owner.getDecomposition();
        if (text == null || mode != textMode) {
            textMode = mode;
            text = normalize(source, mode);
        }
        if (buffer != null) {
            if (expIndex < buffer.length) {
                return strengthOrder(buffer[expIndex++]);
            }
            buffer = null;
            expIndex = 0;
        } else if (swapOrder != 0) {
            if (Character.isSupplementaryCodePoint(swapOrder)) {
                char[] chars = Character.toChars(swapOrder);
                swapOrder = chars[1];
                return chars[0] << 16;
            }
            int order = swapOrder << 16;
            swapOrder = 0;
            return order;
        }
        if (pos >= text.length()) {
            return NULLORDER;
        }
        int ch = text.codePointAt(pos);
        pos += Character.charCount(ch);
        int[] orders = lookup(ch);
        if (orders == null) {
            swapOrder = ch;
            return UNMAPPED_CHAR_VALUE;
        }
        int value;
        if (orders.length == 0) {
            value = NULLORDER;
        } else {
            value = orders[0];
            if (orders.length > 1) {
                buffer = orders;
                expIndex = 1;
            }
        }
        if (table.seAsianSwapping && (isThaiPreVowel(ch) || isLaoPreVowel(ch)) && pos < text.length()) {
            int consonant = text.codePointAt(pos);
            if (isThaiPreVowel(ch) ? isThaiBaseConsonant(consonant) : isLaoBaseConsonant(consonant)) {
                pos += Character.charCount(consonant);
                int[] first = lookup(consonant);
                int[] last = buffer != null ? buffer : new int[] { value };
                if (first == null) {
                    first = new int[] { -1 };
                }
                int[] reordered = new int[first.length + last.length];
                System.arraycopy(first, 0, reordered, 0, first.length);
                System.arraycopy(last, 0, reordered, first.length, last.length);
                buffer = reordered;
                value = reordered[0];
                expIndex = 1;
            }
        }
        return strengthOrder(value);
    }

    // The elements of the longest contracting sequence starting with ch at the current position (consuming the
    // rest of it), else of ch alone; null when the rules do not map ch.
    private int[] lookup(int ch) {
        List<String> keys = table.contractionKeys.get(ch);
        if (keys != null) {
            int start = pos - Character.charCount(ch);
            for (String key : keys) {
                if (text.startsWith(key, start)) {
                    pos = start + key.length();
                    return table.contractions.get(key);
                }
            }
        }
        return table.mapping(ch);
    }

    private int strengthOrder(int order) {
        int s = owner.getStrength();
        if (s == TCollator.PRIMARY) {
            order &= 0xffff0000;
        } else if (s == TCollator.SECONDARY) {
            order &= 0xffffff00;
        }
        return order;
    }

    private static String normalize(String text, int mode) {
        if (mode == TCollator.CANONICAL_DECOMPOSITION) {
            return Normalizer.normalize(text, Normalizer.Form.NFD);
        } else if (mode == TCollator.FULL_DECOMPOSITION) {
            return Normalizer.normalize(text, Normalizer.Form.NFKD);
        }
        return text;
    }

    public static int primaryOrder(int order) {
        return (order & 0xffff0000) >>> 16;
    }

    public static short secondaryOrder(int order) {
        return (short) ((order & 0x0000ff00) >> 8);
    }

    public static short tertiaryOrder(int order) {
        return (short) (order & 0x000000ff);
    }

    static boolean isIgnorable(int order) {
        return primaryOrder(order) == 0;
    }

    private static boolean isThaiPreVowel(int ch) {
        return ch >= 0x0e40 && ch <= 0x0e44;
    }

    private static boolean isThaiBaseConsonant(int ch) {
        return ch >= 0x0e01 && ch <= 0x0e2e;
    }

    private static boolean isLaoPreVowel(int ch) {
        return ch >= 0x0ec0 && ch <= 0x0ec4;
    }

    private static boolean isLaoBaseConsonant(int ch) {
        return ch >= 0x0e81 && ch <= 0x0eae;
    }
}