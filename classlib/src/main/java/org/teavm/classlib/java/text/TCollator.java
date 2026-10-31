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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.teavm.classlib.impl.text.CollationTable;
import org.teavm.platform.metadata.ResourceMap;
import org.teavm.platform.metadata.StringResource;

public abstract class TCollator implements Comparator<Object>, Cloneable {
    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int TERTIARY = 2;
    public static final int IDENTICAL = 3;
    public static final int NO_DECOMPOSITION = 0;
    public static final int CANONICAL_DECOMPOSITION = 1;
    public static final int FULL_DECOMPOSITION = 2;

    private static Map<String, TRuleBasedCollator> cache;

    private int strength;
    private int decmp;

    protected TCollator() {
        strength = TERTIARY;
        decmp = CANONICAL_DECOMPOSITION;
    }

    public static TCollator getInstance() {
        return getInstance(Locale.getDefault());
    }

    // As the JDK's: the rule set of the most specific of language-country and language that has one, else the
    // root rules; each call returns a fresh copy of the shared instance.
    public static TCollator getInstance(Locale desiredLocale) {
        if (cache == null) {
            cache = new HashMap<>();
        }
        String language = desiredLocale.getLanguage();
        String country = desiredLocale.getCountry();
        String table = null;
        ResourceMap<StringResource> locales = collationLocales();
        if (!language.isEmpty() && !country.isEmpty() && locales.has(language + "-" + country)) {
            table = locales.get(language + "-" + country).getValue();
        } else if (!language.isEmpty() && locales.has(language)) {
            table = locales.get(language).getValue();
        }
        if (table == null) {
            table = locales.get("root").getValue();
        }
        TRuleBasedCollator collator = cache.get(table);
        if (collator == null) {
            collator = new TRuleBasedCollator(collationRules().get(table).getValue(),
                    CollationTable.decode(collationTables().get(table).getValue()));
            cache.put(table, collator);
        }
        return (TCollator) collator.clone();
    }

    public static Locale[] getAvailableLocales() {
        String[] keys = collationLocales().keys();
        int count = 0;
        for (String key : keys) {
            if (!key.equals("root")) {
                count++;
            }
        }
        Locale[] result = new Locale[count];
        int i = 0;
        for (String key : keys) {
            if (!key.equals("root")) {
                result[i++] = Locale.forLanguageTag(key);
            }
        }
        return result;
    }

    public abstract int compare(String source, String target);

    @Override
    public int compare(Object o1, Object o2) {
        return compare((String) o1, (String) o2);
    }

    public abstract TCollationKey getCollationKey(String source);

    public boolean equals(String source, String target) {
        return compare(source, target) == 0;
    }

    public synchronized int getStrength() {
        return strength;
    }

    public synchronized void setStrength(int newStrength) {
        if (newStrength != PRIMARY && newStrength != SECONDARY && newStrength != TERTIARY
                && newStrength != IDENTICAL) {
            throw new IllegalArgumentException("Incorrect comparison level.");
        }
        strength = newStrength;
    }

    public synchronized int getDecomposition() {
        return decmp;
    }

    public synchronized void setDecomposition(int decompositionMode) {
        if (decompositionMode != NO_DECOMPOSITION && decompositionMode != CANONICAL_DECOMPOSITION
                && decompositionMode != FULL_DECOMPOSITION) {
            throw new IllegalArgumentException("Wrong decomposition mode.");
        }
        decmp = decompositionMode;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    // As the JDK's, hashCode is left to subclasses.
    //CHECKSTYLE.OFF: EqualsHashCode
    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null || getClass() != that.getClass()) {
            return false;
        }
        TCollator other = (TCollator) that;
        return strength == other.strength && decmp == other.decmp;
    }

    //CHECKSTYLE.ON: EqualsHashCode

    @Override
    public abstract int hashCode();

    private static native ResourceMap<StringResource> collationLocales();

    private static native ResourceMap<StringResource> collationTables();

    private static native ResourceMap<StringResource> collationRules();
}