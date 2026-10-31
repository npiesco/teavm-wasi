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

import java.text.CollationElementIterator;
import java.text.Collator;
import java.text.Normalizer;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.teavm.classlib.impl.text.CollationTable;
import org.teavm.classlib.impl.unicode.CLDRReader;
import org.teavm.model.MethodReference;
import org.teavm.platform.metadata.MetadataGenerator;
import org.teavm.platform.metadata.MetadataGeneratorContext;
import org.teavm.platform.metadata.Resource;
import org.teavm.platform.metadata.ResourceMap;
import org.teavm.platform.metadata.StringResource;

/**
 * Collation tables of the JDK's RuleBasedCollator, one per distinct rule set among the configured locales, taken from
 * the collator of the JDK running the compiler: what its collation element iterator yields for every code point
 * that its rules map and for every contracting sequence its rules declare, the rules' French secondary ('@') and
 * Thai/Lao reordering ('!') modifiers, the default strength and decomposition, and the secondary and tertiary
 * offsets its collation keys give ignorable elements.
 */
public class CollationMetadataGenerator implements MetadataGenerator {
    static final int FRENCH_SECONDARY = 1;
    static final int SE_ASIAN_SWAPPING = 2;
    private static final int UNMAPPED_CHAR_VALUE = 0x7FFF0000;

    private Map<String, Integer> localeTables;
    private List<String> rules;
    private List<String> tables;

    @Override
    public Resource generateMetadata(MetadataGeneratorContext context, MethodReference method) {
        build(context);
        ResourceMap<StringResource> map = context.createResourceMap();
        switch (method.getName()) {
            case "collationLocales":
                for (Map.Entry<String, Integer> entry : localeTables.entrySet()) {
                    map.put(entry.getKey(), string(context, String.valueOf(entry.getValue())));
                }
                break;
            case "collationTables":
                for (int i = 0; i < tables.size(); ++i) {
                    map.put(String.valueOf(i), string(context, tables.get(i)));
                }
                break;
            case "collationRules":
                for (int i = 0; i < rules.size(); ++i) {
                    map.put(String.valueOf(i), string(context, rules.get(i)));
                }
                break;
            default:
                return null;
        }
        return map;
    }

    private static StringResource string(MetadataGeneratorContext context, String value) {
        StringResource resource = context.createResource(StringResource.class);
        resource.setValue(value);
        return resource;
    }

    private void build(MetadataGeneratorContext context) {
        if (localeTables != null) {
            return;
        }
        localeTables = new LinkedHashMap<>();
        rules = new ArrayList<>();
        tables = new ArrayList<>();
        Map<String, Integer> byRules = new LinkedHashMap<>();
        localeTables.put("root", table(Locale.ROOT, byRules));
        CLDRReader reader = context.getService(CLDRReader.class);
        for (String tag : reader.getAvailableLocales()) {
            Locale locale = Locale.forLanguageTag(tag.replace('_', '-'));
            localeTables.put(tag.replace('_', '-'), table(locale, byRules));
            register(locale, byRules);
        }
        // The JDK's own collator locales include ones (such as zh_TW) that CLDR only lists with a script and no
        // country, so they would otherwise fall back to the bare language's rules.
        for (Locale locale : Collator.getAvailableLocales()) {
            register(locale, byRules);
        }
    }

    // Collator.getInstance looks a locale up by language-country, then language, so those keys are registered,
    // each with the JDK's own collator for exactly that locale.
    private void register(Locale locale, Map<String, Integer> byRules) {
        if (locale.getLanguage().isEmpty()) {
            return;
        }
        if (!locale.getCountry().isEmpty()) {
            String key = locale.getLanguage() + "-" + locale.getCountry();
            if (!localeTables.containsKey(key)) {
                localeTables.put(key, table(new Locale(locale.getLanguage(), locale.getCountry()), byRules));
            }
        }
        if (!localeTables.containsKey(locale.getLanguage())) {
            localeTables.put(locale.getLanguage(), table(new Locale(locale.getLanguage()), byRules));
        }
    }

    private int table(Locale locale, Map<String, Integer> byRules) {
        RuleBasedCollator collator = (RuleBasedCollator) Collator.getInstance(locale);
        String ruleText = collator.getRules();
        Integer index = byRules.get(ruleText);
        if (index == null) {
            index = rules.size();
            byRules.put(ruleText, index);
            rules.add(ruleText);
            tables.add(CollationTable.encode(encode(collator, ruleText)));
        }
        return index;
    }

    // flags, strength, decomposition, maxSecOrder, maxTerOrder, mapping count, (code point, n, orders) per mapping,
    // contraction count, (length, chars, n, orders) per contraction.
    private static int[] encode(RuleBasedCollator collator, String ruleText) {
        List<Integer> out = new ArrayList<>();
        Set<String> entries = new LinkedHashSet<>();
        int flags = parse(ruleText, entries);
        // The JDK builds its table from canonically decomposed rules, so an entry such as U+00C5 contracts its
        // decomposition. A sequence is kept when its elements differ from those of its code points one by one.
        Set<String> contractions = new LinkedHashSet<>();
        for (String entry : entries) {
            for (String key : new String[] { entry, Normalizer.normalize(entry, Normalizer.Form.NFD) }) {
                if (key.codePointCount(0, key.length()) > 1 && !Arrays.equals(orders(collator, key),
                        separateOrders(collator, key))) {
                    contractions.add(key);
                }
            }
        }
        out.add(flags);
        out.add(collator.getStrength());
        out.add(collator.getDecomposition());
        int sizeIndex = out.size();
        out.add(0);
        out.add(0);

        List<int[]> mappings = new ArrayList<>();
        Set<Integer> codePoints = new LinkedHashSet<>();
        for (int cp = 0; cp < 0x10000; ++cp) {
            codePoints.add(cp);
        }
        for (String entry : contractions) {
            codePoints.add(entry.codePointAt(0));
        }
        int maxSec = -1;
        int maxTer = -1;
        for (int cp : codePoints) {
            String text = new String(Character.toChars(cp));
            int[] orders = orders(collator, text);
            if (orders.length == 2 + (cp > 0xFFFF ? 1 : 0) && orders[0] == UNMAPPED_CHAR_VALUE) {
                continue;
            }
            mappings.add(new int[] { cp });
            mappings.add(orders);
            if (orders.length == 1 && (orders[0] >>> 16) == 0) {
                int sec = (orders[0] >> 8) & 0xFF;
                int ter = orders[0] & 0xFF;
                char[] key = keyChars(collator, text);
                if (maxSec < 0 && sec != 0 && key.length > 1) {
                    maxSec = key[1] - sec - 1;
                }
                if (maxTer < 0 && sec != 0 && ter != 0 && key.length > 3) {
                    maxTer = key[3] - ter - 1;
                }
            }
        }
        out.set(sizeIndex, Math.max(maxSec, 0));
        out.set(sizeIndex + 1, Math.max(maxTer, 0));
        out.add(mappings.size() / 2);
        for (int i = 0; i < mappings.size(); i += 2) {
            out.add(mappings.get(i)[0]);
            int[] orders = mappings.get(i + 1);
            out.add(orders.length);
            for (int order : orders) {
                out.add(order);
            }
        }
        out.add(contractions.size());
        for (String entry : contractions) {
            out.add(entry.length());
            for (int i = 0; i < entry.length(); ++i) {
                out.add((int) entry.charAt(i));
            }
            int[] orders = orders(collator, entry);
            out.add(orders.length);
            for (int order : orders) {
                out.add(order);
            }
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = out.get(i);
        }
        return result;
    }

    private static int[] separateOrders(RuleBasedCollator collator, String text) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            for (int order : orders(collator, new String(Character.toChars(cp)))) {
                list.add(order);
            }
        }
        int[] result = new int[list.size()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = list.get(i);
        }
        return result;
    }

    private static int[] orders(RuleBasedCollator collator, String text) {
        CollationElementIterator it = collator.getCollationElementIterator(text);
        List<Integer> list = new ArrayList<>();
        for (int order = it.next(); order != CollationElementIterator.NULLORDER; order = it.next()) {
            list.add(order);
        }
        int[] result = new int[list.size()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = list.get(i);
        }
        return result;
    }

    private static char[] keyChars(RuleBasedCollator collator, String text) {
        byte[] bytes = collator.getCollationKey(text).toByteArray();
        char[] chars = new char[bytes.length / 2];
        for (int i = 0; i < chars.length; ++i) {
            chars[i] = (char) (((bytes[i * 2] & 0xFF) << 8) | (bytes[i * 2 + 1] & 0xFF));
        }
        return chars;
    }

    // The rule syntax of java.text.RuleBasedCollator: relations '<' ';' ',' '=' and resets '&' separate entries,
    // quotes make syntax characters literal ("'''" is a quote), whitespace is ignored, text after '/' is an expansion
    // of the entry rather than part of it, and '@' / '!' are modifiers. Entries longer than one code point are
    // candidate contracting sequences; reset text only positions the next entry.
    static int parse(String ruleText, Set<String> contractions) {
        int flags = 0;
        StringBuilder entry = new StringBuilder();
        boolean inQuote = false;
        boolean extension = false;
        boolean reset = false;
        for (int i = 0; i < ruleText.length(); ++i) {
            char c = ruleText.charAt(i);
            if (inQuote) {
                if (c == '\'') {
                    inQuote = false;
                } else if (!extension) {
                    entry.append(c);
                }
                continue;
            }
            if (c == '\'') {
                // As the JDK's PatternEntry parser: the character after an opening quote is literal, even a quote.
                inQuote = true;
                if (++i < ruleText.length() && !extension) {
                    entry.append(ruleText.charAt(i));
                }
                continue;
            }
            switch (c) {
                case '<':
                case ';':
                case ',':
                case '=':
                case '&':
                    if (!reset) {
                        addContraction(entry, contractions);
                    }
                    entry.setLength(0);
                    extension = false;
                    reset = c == '&';
                    break;
                case '/':
                    extension = true;
                    break;
                case '@':
                    flags |= FRENCH_SECONDARY;
                    break;
                case '!':
                    flags |= SE_ASIAN_SWAPPING;
                    break;
                default:
                    if (!Character.isWhitespace(c) && !extension) {
                        entry.append(c);
                    }
                    break;
            }
        }
        if (!reset) {
            addContraction(entry, contractions);
        }
        return flags;
    }

    private static void addContraction(StringBuilder entry, Set<String> contractions) {
        if (entry.length() > 0) {
            contractions.add(entry.toString());
        }
    }
}
