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
import org.teavm.classlib.impl.text.CollationTable;

/**
 * java.text.RuleBasedCollator over a JDK rule set's collation table: the JDK's comparison of collation elements
 * (primary differences decide, then the first secondary, then the first tertiary difference; French secondary
 * rules let a later accent difference decide) and its collation keys.
 */
public class TRuleBasedCollator extends TCollator {
    private static final int COLLATION_KEY_OFFSET = 1;

    final CollationTable table;
    private final String rules;

    TRuleBasedCollator(String rules, CollationTable table) {
        this.rules = rules;
        this.table = table;
        setStrength(table.strength);
        setDecomposition(table.decomposition);
    }

    public String getRules() {
        return rules;
    }

    public TCollationElementIterator getCollationElementIterator(String source) {
        return new TCollationElementIterator(source, this);
    }

    @Override
    public synchronized int compare(String source, String target) {
        if (source == null || target == null) {
            throw new NullPointerException();
        }
        if (source.equals(target)) {
            return 0;
        }
        int result = 0;
        TCollationElementIterator sourceCursor = getCollationElementIterator(source);
        TCollationElementIterator targetCursor = getCollationElementIterator(target);
        int sOrder = 0;
        int tOrder = 0;
        boolean initialCheckSecTer = getStrength() >= SECONDARY;
        boolean checkSecTer = initialCheckSecTer;
        boolean checkTertiary = getStrength() >= TERTIARY;
        boolean gets = true;
        boolean gett = true;
        while (true) {
            if (gets) {
                sOrder = sourceCursor.next();
            } else {
                gets = true;
            }
            if (gett) {
                tOrder = targetCursor.next();
            } else {
                gett = true;
            }
            if (sOrder == TCollationElementIterator.NULLORDER || tOrder == TCollationElementIterator.NULLORDER) {
                break;
            }
            int pSOrder = TCollationElementIterator.primaryOrder(sOrder);
            int pTOrder = TCollationElementIterator.primaryOrder(tOrder);
            if (sOrder == tOrder) {
                if (table.frenchSecondary && pSOrder != 0 && !checkSecTer) {
                    // A later accent difference decides in French secondary order.
                    checkSecTer = initialCheckSecTer;
                    checkTertiary = false;
                }
                continue;
            }
            if (pSOrder != pTOrder) {
                if (sOrder == 0) {
                    gett = false;
                    continue;
                }
                if (tOrder == 0) {
                    gets = false;
                    continue;
                }
                if (pSOrder == 0) {
                    if (checkSecTer) {
                        result = 1;
                        checkSecTer = false;
                    }
                    gett = false;
                } else if (pTOrder == 0) {
                    if (checkSecTer) {
                        result = -1;
                        checkSecTer = false;
                    }
                    gets = false;
                } else {
                    return pSOrder < pTOrder ? -1 : 1;
                }
            } else if (checkSecTer) {
                short secSOrder = TCollationElementIterator.secondaryOrder(sOrder);
                short secTOrder = TCollationElementIterator.secondaryOrder(tOrder);
                if (secSOrder != secTOrder) {
                    result = secSOrder < secTOrder ? -1 : 1;
                    checkSecTer = false;
                } else if (checkTertiary) {
                    short terSOrder = TCollationElementIterator.tertiaryOrder(sOrder);
                    short terTOrder = TCollationElementIterator.tertiaryOrder(tOrder);
                    if (terSOrder != terTOrder) {
                        result = terSOrder < terTOrder ? -1 : 1;
                        checkTertiary = false;
                    }
                }
            }
        }
        if (sOrder != TCollationElementIterator.NULLORDER) {
            do {
                if (TCollationElementIterator.primaryOrder(sOrder) != 0) {
                    return 1;
                } else if (TCollationElementIterator.secondaryOrder(sOrder) != 0 && checkSecTer) {
                    result = 1;
                    checkSecTer = false;
                }
                sOrder = sourceCursor.next();
            } while (sOrder != TCollationElementIterator.NULLORDER);
        } else if (tOrder != TCollationElementIterator.NULLORDER) {
            do {
                if (TCollationElementIterator.primaryOrder(tOrder) != 0) {
                    return -1;
                } else if (TCollationElementIterator.secondaryOrder(tOrder) != 0 && checkSecTer) {
                    result = -1;
                    checkSecTer = false;
                }
                tOrder = targetCursor.next();
            } while (tOrder != TCollationElementIterator.NULLORDER);
        }
        if (result == 0 && getStrength() == IDENTICAL) {
            int mode = getDecomposition();
            if (mode == CANONICAL_DECOMPOSITION) {
                return Normalizer.normalize(source, Normalizer.Form.NFD)
                        .compareTo(Normalizer.normalize(target, Normalizer.Form.NFD));
            } else if (mode == FULL_DECOMPOSITION) {
                return Normalizer.normalize(source, Normalizer.Form.NFKD)
                        .compareTo(Normalizer.normalize(target, Normalizer.Form.NFKD));
            }
            return source.compareTo(target);
        }
        return result;
    }

    // Primary weights, then secondary weights (reversed between primaries for French secondary rules, then as a
    // whole), then tertiary weights; ignorable elements' secondary and tertiary weights are offset past every
    // non-ignorable one. IDENTICAL appends the (decomposed) source.
    @Override
    public synchronized TCollationKey getCollationKey(String source) {
        if (source == null) {
            return null;
        }
        StringBuilder primResult = new StringBuilder();
        StringBuilder secResult = new StringBuilder();
        StringBuilder terResult = new StringBuilder();
        boolean compareSec = getStrength() >= SECONDARY;
        boolean compareTer = getStrength() >= TERTIARY;
        int preSecIgnore = 0;
        TCollationElementIterator cursor = getCollationElementIterator(source);
        for (int order = cursor.next(); order != TCollationElementIterator.NULLORDER; order = cursor.next()) {
            int secOrder = TCollationElementIterator.secondaryOrder(order);
            int terOrder = TCollationElementIterator.tertiaryOrder(order);
            if (!TCollationElementIterator.isIgnorable(order)) {
                primResult.append((char) (TCollationElementIterator.primaryOrder(order) + COLLATION_KEY_OFFSET));
                if (compareSec) {
                    if (table.frenchSecondary && preSecIgnore < secResult.length()) {
                        reverse(secResult, preSecIgnore, secResult.length());
                    }
                    secResult.append((char) (secOrder + COLLATION_KEY_OFFSET));
                    preSecIgnore = secResult.length();
                }
                if (compareTer) {
                    terResult.append((char) (terOrder + COLLATION_KEY_OFFSET));
                }
            } else {
                if (compareSec && secOrder != 0) {
                    secResult.append((char) (secOrder + table.maxSecOrder + COLLATION_KEY_OFFSET));
                }
                if (compareTer && terOrder != 0) {
                    terResult.append((char) (terOrder + table.maxTerOrder + COLLATION_KEY_OFFSET));
                }
            }
        }
        if (table.frenchSecondary) {
            if (preSecIgnore < secResult.length()) {
                reverse(secResult, preSecIgnore, secResult.length());
            }
            reverse(secResult, 0, secResult.length());
        }
        primResult.append((char) 0);
        secResult.append((char) 0);
        secResult.append(terResult);
        primResult.append(secResult);
        if (getStrength() == IDENTICAL) {
            primResult.append((char) 0);
            int mode = getDecomposition();
            if (mode == CANONICAL_DECOMPOSITION) {
                primResult.append(Normalizer.normalize(source, Normalizer.Form.NFD));
            } else if (mode == FULL_DECOMPOSITION) {
                primResult.append(Normalizer.normalize(source, Normalizer.Form.NFKD));
            } else {
                primResult.append(source);
            }
        }
        return new TRuleBasedCollationKey(source, primResult.toString());
    }

    private static void reverse(StringBuilder result, int from, int to) {
        int i = from;
        int j = to - 1;
        while (i < j) {
            char swap = result.charAt(i);
            result.setCharAt(i, result.charAt(j));
            result.setCharAt(j, swap);
            i++;
            j--;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !super.equals(obj)) {
            return false;
        }
        return rules.equals(((TRuleBasedCollator) obj).rules);
    }

    @Override
    public int hashCode() {
        return rules.hashCode();
    }
}