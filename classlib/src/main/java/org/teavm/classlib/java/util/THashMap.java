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

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TCloneNotSupportedException;
import org.teavm.classlib.java.lang.TCloneable;
import org.teavm.classlib.java.lang.TIllegalArgumentException;
import org.teavm.classlib.java.lang.TIllegalStateException;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.interop.Rename;

/**
 * java.util.HashMap with the JDK's (JDK 21) observable behaviour, including iteration order: hashes are spread as
 * {@code h ^ (h >>> 16)}; the table is a power of two sized by {@code tableSizeFor}, allocated lazily and doubled
 * with order-preserving lo/hi splits; put appends to a bin, while compute/computeIfAbsent/merge insert at its head;
 * a bin reaching TREEIFY_THRESHOLD entries resizes a table smaller than MIN_TREEIFY_CAPACITY and otherwise becomes a
 * red-black tree whose node order (root moved to the front, new nodes linked after their parent) is iteration order.
 */
public class THashMap<K, V> extends TAbstractMap<K, V> implements TMap<K, V>, TCloneable, TSerializable {
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final int MAXIMUM_CAPACITY = 1 << 30;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int TREEIFY_THRESHOLD = 8;
    static final int UNTREEIFY_THRESHOLD = 6;
    static final int MIN_TREEIFY_CAPACITY = 64;

    transient int elementCount;
    // An empty array stands for the JDK's not yet allocated table.
    transient HashEntry<K, V>[] elementData;
    transient int modCount;
    final float loadFactor;
    // Before the table is allocated: the initial capacity (0 for the default).
    int threshold;

    static class HashEntry<K, V> extends TMapEntry<K, V> {
        final int origKeyHash;
        HashEntry<K, V> next;
        // Set while the entry lives in a tree bin.
        TreeLinks<K, V> tree;

        HashEntry(K theKey, int hash) {
            super(theKey, null);
            this.origKeyHash = hash;
        }
    }

    static final class TreeLinks<K, V> {
        HashEntry<K, V> parent;
        HashEntry<K, V> left;
        HashEntry<K, V> right;
        HashEntry<K, V> prev;
        boolean red;
    }

    public THashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        elementData = newElementArray(0);
    }

    public THashMap(int capacity) {
        this(capacity, DEFAULT_LOAD_FACTOR);
    }

    public THashMap(int capacity, float loadFactor) {
        if (capacity < 0) {
            throw new TIllegalArgumentException("Illegal initial capacity: " + capacity);
        }
        if (capacity > MAXIMUM_CAPACITY) {
            capacity = MAXIMUM_CAPACITY;
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new TIllegalArgumentException("Illegal load factor: " + loadFactor);
        }
        this.loadFactor = loadFactor;
        this.threshold = tableSizeFor(capacity);
        elementData = newElementArray(0);
    }

    public THashMap(TMap<? extends K, ? extends V> map) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        elementData = newElementArray(0);
        putMapEntries(map, false);
    }

    static int tableSizeFor(int capacity) {
        int n = -1 >>> Integer.numberOfLeadingZeros(capacity - 1);
        return n < 0 ? 1 : n >= MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : n + 1;
    }

    // JDK HashMap.calculateHashMapCapacity: the capacity holding numMappings without a resize.
    static int capacityFor(int numMappings) {
        return (int) Math.ceil(numMappings / (double) DEFAULT_LOAD_FACTOR);
    }

    static int hash(Object key) {
        if (key == null) {
            return 0;
        }
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    @SuppressWarnings("unchecked")
    HashEntry<K, V>[] newElementArray(int s) {
        return new HashEntry[s];
    }

    // --- hooks for LinkedHashMap ------------------------------------------------------------------------

    HashEntry<K, V> newEntry(int hash, K key, V value, HashEntry<K, V> next) {
        HashEntry<K, V> entry = new HashEntry<>(key, hash);
        entry.value = value;
        entry.next = next;
        return entry;
    }

    void afterNodeAccess(HashEntry<K, V> entry) {
    }

    void afterNodeInsertion(boolean evict) {
    }

    void afterNodeRemoval(HashEntry<K, V> entry) {
    }

    void reinitialize() {
        elementData = newElementArray(0);
        threshold = 0;
        modCount = 0;
        elementCount = 0;
        cachedKeySet = null;
        cachedValues = null;
    }

    // --- core operations ---------------------------------------------------------------------------------

    final void putMapEntries(TMap<? extends K, ? extends V> map, boolean evict) {
        int s = map.size();
        if (s > 0) {
            if (elementData.length == 0) {
                double dt = Math.ceil(s / (double) loadFactor);
                int t = dt < (double) MAXIMUM_CAPACITY ? (int) dt : MAXIMUM_CAPACITY;
                if (t > threshold) {
                    threshold = tableSizeFor(t);
                }
            } else {
                while (s > threshold && elementData.length < MAXIMUM_CAPACITY) {
                    resize();
                }
            }
            for (TIterator<? extends TMap.Entry<? extends K, ? extends V>> iter = map.entrySet().iterator();
                    iter.hasNext();) {
                TMap.Entry<? extends K, ? extends V> entry = iter.next();
                K key = entry.getKey();
                putVal(hash(key), key, entry.getValue(), false, evict);
            }
        }
    }

    final HashEntry<K, V> getEntry(Object key) {
        return getNode(hash(key), key);
    }

    final HashEntry<K, V> getNode(int hash, Object key) {
        HashEntry<K, V>[] tab = elementData;
        int n = tab.length;
        HashEntry<K, V> first = n > 0 ? tab[(n - 1) & hash] : null;
        if (first != null) {
            if (first.origKeyHash == hash && keyEquals(key, first.key)) {
                return first;
            }
            if (first.next != null) {
                if (first.tree != null) {
                    return find(root(first), hash, key, null);
                }
                HashEntry<K, V> e = first.next;
                do {
                    if (e.origKeyHash == hash && keyEquals(key, e.key)) {
                        return e;
                    }
                    e = e.next;
                } while (e != null);
            }
        }
        return null;
    }

    private static boolean keyEquals(Object key, Object nodeKey) {
        return key == nodeKey || (key != null && key.equals(nodeKey));
    }

    final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
        HashEntry<K, V>[] tab = elementData;
        int n = tab.length;
        if (n == 0) {
            tab = resize();
            n = tab.length;
        }
        int i = (n - 1) & hash;
        HashEntry<K, V> p = tab[i];
        if (p == null) {
            tab[i] = newEntry(hash, key, value, null);
        } else {
            HashEntry<K, V> e;
            if (p.origKeyHash == hash && keyEquals(key, p.key)) {
                e = p;
            } else if (p.tree != null) {
                e = putTreeVal(tab, p, hash, key, value);
            } else {
                for (int binCount = 0;; ++binCount) {
                    e = p.next;
                    if (e == null) {
                        p.next = newEntry(hash, key, value, null);
                        if (binCount >= TREEIFY_THRESHOLD - 1) {
                            treeifyBin(tab, hash);
                        }
                        break;
                    }
                    if (e.origKeyHash == hash && keyEquals(key, e.key)) {
                        break;
                    }
                    p = e;
                }
            }
            if (e != null) {
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null) {
                    e.value = value;
                }
                afterNodeAccess(e);
                return oldValue;
            }
        }
        ++modCount;
        if (++elementCount > threshold) {
            resize();
        }
        afterNodeInsertion(evict);
        return null;
    }

    final HashEntry<K, V>[] resize() {
        HashEntry<K, V>[] oldTab = elementData;
        int oldCap = oldTab.length;
        int oldThr = threshold;
        int newCap;
        int newThr = 0;
        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            newCap = oldCap << 1;
            if (newCap < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY) {
                newThr = oldThr << 1;
            }
        } else if (oldThr > 0) {
            newCap = oldThr;
        } else {
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int) (DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        if (newThr == 0) {
            float ft = (float) newCap * loadFactor;
            newThr = newCap < MAXIMUM_CAPACITY && ft < (float) MAXIMUM_CAPACITY ? (int) ft : Integer.MAX_VALUE;
        }
        threshold = newThr;
        HashEntry<K, V>[] newTab = newElementArray(newCap);
        elementData = newTab;
        for (int j = 0; j < oldCap; ++j) {
            HashEntry<K, V> e = oldTab[j];
            if (e == null) {
                continue;
            }
            oldTab[j] = null;
            if (e.next == null) {
                newTab[e.origKeyHash & (newCap - 1)] = e;
            } else if (e.tree != null) {
                split(newTab, e, j, oldCap);
            } else {
                HashEntry<K, V> loHead = null;
                HashEntry<K, V> loTail = null;
                HashEntry<K, V> hiHead = null;
                HashEntry<K, V> hiTail = null;
                HashEntry<K, V> next;
                do {
                    next = e.next;
                    if ((e.origKeyHash & oldCap) == 0) {
                        if (loTail == null) {
                            loHead = e;
                        } else {
                            loTail.next = e;
                        }
                        loTail = e;
                    } else {
                        if (hiTail == null) {
                            hiHead = e;
                        } else {
                            hiTail.next = e;
                        }
                        hiTail = e;
                    }
                    e = next;
                } while (e != null);
                if (loTail != null) {
                    loTail.next = null;
                    newTab[j] = loHead;
                }
                if (hiTail != null) {
                    hiTail.next = null;
                    newTab[j + oldCap] = hiHead;
                }
            }
        }
        return newTab;
    }

    final HashEntry<K, V> removeNode(int hash, Object key, Object value, boolean matchValue, boolean movable) {
        HashEntry<K, V>[] tab = elementData;
        int n = tab.length;
        int index = n > 0 ? (n - 1) & hash : 0;
        HashEntry<K, V> p = n > 0 ? tab[index] : null;
        if (p != null) {
            HashEntry<K, V> node = null;
            HashEntry<K, V> e = p.next;
            if (p.origKeyHash == hash && keyEquals(key, p.key)) {
                node = p;
            } else if (e != null) {
                if (p.tree != null) {
                    node = find(root(p), hash, key, null);
                } else {
                    do {
                        if (e.origKeyHash == hash && keyEquals(key, e.key)) {
                            node = e;
                            break;
                        }
                        p = e;
                        e = e.next;
                    } while (e != null);
                }
            }
            if (node != null && (!matchValue || node.value == value
                    || (value != null && value.equals(node.value)))) {
                if (node.tree != null) {
                    removeTreeNode(tab, node, movable);
                } else if (node == p) {
                    tab[index] = node.next;
                } else {
                    p.next = node.next;
                }
                ++modCount;
                --elementCount;
                afterNodeRemoval(node);
                return node;
            }
        }
        return null;
    }

    // --- tree bins -----------------------------------------------------------------------------------------

    // JDK HashMap.comparableClassFor: the class of x when it is declared as Comparable to itself.
    static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            Class<?> c = x.getClass();
            if (c == String.class || selfComparable(c)) {
                return c;
            }
        }
        return null;
    }

    // Generated by SelfComparableSupport: whether the class directly implements Comparable<itself>.
    private static native boolean selfComparable(Class<?> cls);

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static int compareComparables(Class<?> kc, Object k, Object x) {
        return x == null || x.getClass() != kc ? 0 : ((Comparable) k).compareTo(x);
    }

    static int tieBreakOrder(Object a, Object b) {
        int d = a == null || b == null ? 0 : a.getClass().getName().compareTo(b.getClass().getName());
        if (d == 0) {
            d = System.identityHashCode(a) <= System.identityHashCode(b) ? -1 : 1;
        }
        return d;
    }

    private static <K, V> HashEntry<K, V> root(HashEntry<K, V> node) {
        HashEntry<K, V> r = node;
        while (r.tree.parent != null) {
            r = r.tree.parent;
        }
        return r;
    }

    private static <K, V> HashEntry<K, V> find(HashEntry<K, V> start, int h, Object k, Class<?> kc) {
        HashEntry<K, V> p = start;
        do {
            HashEntry<K, V> pl = p.tree.left;
            HashEntry<K, V> pr = p.tree.right;
            int ph = p.origKeyHash;
            int dir;
            if (ph > h) {
                p = pl;
            } else if (ph < h) {
                p = pr;
            } else if (keyEquals(k, p.key)) {
                return p;
            } else if (pl == null) {
                p = pr;
            } else if (pr == null) {
                p = pl;
            } else {
                if (kc == null) {
                    kc = comparableClassFor(k);
                }
                dir = kc != null ? compareComparables(kc, k, p.key) : 0;
                if (dir != 0) {
                    p = dir < 0 ? pl : pr;
                    continue;
                }
                HashEntry<K, V> q = find(pr, h, k, kc);
                if (q != null) {
                    return q;
                }
                p = pl;
            }
        } while (p != null);
        return null;
    }

    final void treeifyBin(HashEntry<K, V>[] tab, int hash) {
        int n = tab.length;
        if (n < MIN_TREEIFY_CAPACITY) {
            resize();
            return;
        }
        HashEntry<K, V> head = tab[(n - 1) & hash];
        if (head == null) {
            return;
        }
        HashEntry<K, V> prev = null;
        for (HashEntry<K, V> e = head; e != null; e = e.next) {
            e.tree = new TreeLinks<>();
            e.tree.prev = prev;
            prev = e;
        }
        treeify(tab, head);
    }

    private void treeify(HashEntry<K, V>[] tab, HashEntry<K, V> head) {
        HashEntry<K, V> root = null;
        for (HashEntry<K, V> x = head; x != null; x = x.next) {
            x.tree.left = null;
            x.tree.right = null;
            if (root == null) {
                x.tree.parent = null;
                x.tree.red = false;
                root = x;
                continue;
            }
            K k = x.key;
            int h = x.origKeyHash;
            Class<?> kc = null;
            for (HashEntry<K, V> p = root;;) {
                int dir;
                int ph = p.origKeyHash;
                K pk = p.key;
                if (ph > h) {
                    dir = -1;
                } else if (ph < h) {
                    dir = 1;
                } else {
                    if (kc == null) {
                        kc = comparableClassFor(k);
                    }
                    dir = kc != null ? compareComparables(kc, k, pk) : 0;
                    if (dir == 0) {
                        dir = tieBreakOrder(k, pk);
                    }
                }
                HashEntry<K, V> xp = p;
                p = dir <= 0 ? p.tree.left : p.tree.right;
                if (p == null) {
                    x.tree.parent = xp;
                    if (dir <= 0) {
                        xp.tree.left = x;
                    } else {
                        xp.tree.right = x;
                    }
                    root = balanceInsertion(root, x);
                    break;
                }
            }
        }
        moveRootToFront(tab, root);
    }

    private static <K, V> HashEntry<K, V> untreeify(HashEntry<K, V> head) {
        for (HashEntry<K, V> e = head; e != null; e = e.next) {
            e.tree = null;
        }
        return head;
    }

    private static <K, V> void moveRootToFront(HashEntry<K, V>[] tab, HashEntry<K, V> root) {
        int n = tab.length;
        if (root == null || n == 0) {
            return;
        }
        int index = (n - 1) & root.origKeyHash;
        HashEntry<K, V> first = tab[index];
        if (root != first) {
            tab[index] = root;
            HashEntry<K, V> rp = root.tree.prev;
            HashEntry<K, V> rn = root.next;
            if (rn != null) {
                rn.tree.prev = rp;
            }
            if (rp != null) {
                rp.next = rn;
            }
            if (first != null) {
                first.tree.prev = root;
            }
            root.next = first;
            root.tree.prev = null;
        }
    }

    private HashEntry<K, V> putTreeVal(HashEntry<K, V>[] tab, HashEntry<K, V> first, int h, K k, V v) {
        Class<?> kc = null;
        boolean searched = false;
        HashEntry<K, V> root = first.tree.parent != null ? root(first) : first;
        for (HashEntry<K, V> p = root;;) {
            int dir;
            int ph = p.origKeyHash;
            K pk = p.key;
            if (ph > h) {
                dir = -1;
            } else if (ph < h) {
                dir = 1;
            } else if (keyEquals(k, pk)) {
                return p;
            } else {
                if (kc == null) {
                    kc = comparableClassFor(k);
                }
                dir = kc != null ? compareComparables(kc, k, pk) : 0;
                if (dir == 0) {
                    if (!searched) {
                        searched = true;
                        HashEntry<K, V> q = p.tree.left != null ? find(p.tree.left, h, k, kc) : null;
                        if (q == null && p.tree.right != null) {
                            q = find(p.tree.right, h, k, kc);
                        }
                        if (q != null) {
                            return q;
                        }
                    }
                    dir = tieBreakOrder(k, pk);
                }
            }
            HashEntry<K, V> xp = p;
            p = dir <= 0 ? p.tree.left : p.tree.right;
            if (p == null) {
                HashEntry<K, V> xpn = xp.next;
                HashEntry<K, V> x = newEntry(h, k, v, xpn);
                x.tree = new TreeLinks<>();
                if (dir <= 0) {
                    xp.tree.left = x;
                } else {
                    xp.tree.right = x;
                }
                xp.next = x;
                x.tree.parent = xp;
                x.tree.prev = xp;
                if (xpn != null) {
                    xpn.tree.prev = x;
                }
                moveRootToFront(tab, balanceInsertion(root, x));
                return null;
            }
        }
    }

    private void split(HashEntry<K, V>[] tab, HashEntry<K, V> bin, int index, int bit) {
        HashEntry<K, V> loHead = null;
        HashEntry<K, V> loTail = null;
        HashEntry<K, V> hiHead = null;
        HashEntry<K, V> hiTail = null;
        int lc = 0;
        int hc = 0;
        HashEntry<K, V> next;
        for (HashEntry<K, V> e = bin; e != null; e = next) {
            next = e.next;
            e.next = null;
            if ((e.origKeyHash & bit) == 0) {
                e.tree.prev = loTail;
                if (loTail == null) {
                    loHead = e;
                } else {
                    loTail.next = e;
                }
                loTail = e;
                ++lc;
            } else {
                e.tree.prev = hiTail;
                if (hiTail == null) {
                    hiHead = e;
                } else {
                    hiTail.next = e;
                }
                hiTail = e;
                ++hc;
            }
        }
        if (loHead != null) {
            if (lc <= UNTREEIFY_THRESHOLD) {
                tab[index] = untreeify(loHead);
            } else {
                tab[index] = loHead;
                if (hiHead != null) {
                    treeify(tab, loHead);
                }
            }
        }
        if (hiHead != null) {
            if (hc <= UNTREEIFY_THRESHOLD) {
                tab[index + bit] = untreeify(hiHead);
            } else {
                tab[index + bit] = hiHead;
                if (loHead != null) {
                    treeify(tab, hiHead);
                }
            }
        }
    }

    private void removeTreeNode(HashEntry<K, V>[] tab, HashEntry<K, V> node, boolean movable) {
        int n = tab.length;
        if (n == 0) {
            return;
        }
        int index = (n - 1) & node.origKeyHash;
        HashEntry<K, V> first = tab[index];
        HashEntry<K, V> root = first;
        HashEntry<K, V> succ = node.next;
        HashEntry<K, V> pred = node.tree.prev;
        if (pred == null) {
            tab[index] = succ;
            first = succ;
        } else {
            pred.next = succ;
        }
        if (succ != null) {
            succ.tree.prev = pred;
        }
        if (first == null) {
            return;
        }
        if (root.tree.parent != null) {
            root = root(root);
        }
        if (root == null || (movable && (root.tree.right == null || root.tree.left == null
                || root.tree.left.tree.left == null))) {
            tab[index] = untreeify(first);
            return;
        }
        HashEntry<K, V> p = node;
        HashEntry<K, V> pl = p.tree.left;
        HashEntry<K, V> pr = p.tree.right;
        HashEntry<K, V> replacement;
        if (pl != null && pr != null) {
            HashEntry<K, V> s = pr;
            HashEntry<K, V> sl;
            while ((sl = s.tree.left) != null) {
                s = sl;
            }
            boolean c = s.tree.red;
            s.tree.red = p.tree.red;
            p.tree.red = c;
            HashEntry<K, V> sr = s.tree.right;
            HashEntry<K, V> pp = p.tree.parent;
            if (s == pr) {
                p.tree.parent = s;
                s.tree.right = p;
            } else {
                HashEntry<K, V> sp = s.tree.parent;
                p.tree.parent = sp;
                if (sp != null) {
                    if (s == sp.tree.left) {
                        sp.tree.left = p;
                    } else {
                        sp.tree.right = p;
                    }
                }
                s.tree.right = pr;
                if (pr != null) {
                    pr.tree.parent = s;
                }
            }
            p.tree.left = null;
            p.tree.right = sr;
            if (sr != null) {
                sr.tree.parent = p;
            }
            s.tree.left = pl;
            if (pl != null) {
                pl.tree.parent = s;
            }
            s.tree.parent = pp;
            if (pp == null) {
                root = s;
            } else if (p == pp.tree.left) {
                pp.tree.left = s;
            } else {
                pp.tree.right = s;
            }
            replacement = sr != null ? sr : p;
        } else if (pl != null) {
            replacement = pl;
        } else if (pr != null) {
            replacement = pr;
        } else {
            replacement = p;
        }
        if (replacement != p) {
            HashEntry<K, V> pp = p.tree.parent;
            replacement.tree.parent = pp;
            if (pp == null) {
                root = replacement;
                replacement.tree.red = false;
            } else if (p == pp.tree.left) {
                pp.tree.left = replacement;
            } else {
                pp.tree.right = replacement;
            }
            p.tree.left = null;
            p.tree.right = null;
            p.tree.parent = null;
        }
        HashEntry<K, V> r = p.tree.red ? root : balanceDeletion(root, replacement);
        if (replacement == p) {
            HashEntry<K, V> pp = p.tree.parent;
            p.tree.parent = null;
            if (pp != null) {
                if (p == pp.tree.left) {
                    pp.tree.left = null;
                } else if (p == pp.tree.right) {
                    pp.tree.right = null;
                }
            }
        }
        if (movable) {
            moveRootToFront(tab, r);
        }
    }

    private static <K, V> boolean red(HashEntry<K, V> node) {
        return node != null && node.tree.red;
    }

    private static <K, V> HashEntry<K, V> rotateLeft(HashEntry<K, V> root, HashEntry<K, V> p) {
        HashEntry<K, V> r = p != null ? p.tree.right : null;
        if (r != null) {
            HashEntry<K, V> rl = r.tree.left;
            p.tree.right = rl;
            if (rl != null) {
                rl.tree.parent = p;
            }
            HashEntry<K, V> pp = p.tree.parent;
            r.tree.parent = pp;
            if (pp == null) {
                root = r;
                r.tree.red = false;
            } else if (pp.tree.left == p) {
                pp.tree.left = r;
            } else {
                pp.tree.right = r;
            }
            r.tree.left = p;
            p.tree.parent = r;
        }
        return root;
    }

    private static <K, V> HashEntry<K, V> rotateRight(HashEntry<K, V> root, HashEntry<K, V> p) {
        HashEntry<K, V> l = p != null ? p.tree.left : null;
        if (l != null) {
            HashEntry<K, V> lr = l.tree.right;
            p.tree.left = lr;
            if (lr != null) {
                lr.tree.parent = p;
            }
            HashEntry<K, V> pp = p.tree.parent;
            l.tree.parent = pp;
            if (pp == null) {
                root = l;
                l.tree.red = false;
            } else if (pp.tree.right == p) {
                pp.tree.right = l;
            } else {
                pp.tree.left = l;
            }
            l.tree.right = p;
            p.tree.parent = l;
        }
        return root;
    }

    private static <K, V> HashEntry<K, V> balanceInsertion(HashEntry<K, V> root, HashEntry<K, V> x) {
        x.tree.red = true;
        while (true) {
            HashEntry<K, V> xp = x.tree.parent;
            if (xp == null) {
                x.tree.red = false;
                return x;
            }
            HashEntry<K, V> xpp = xp.tree.parent;
            if (!xp.tree.red || xpp == null) {
                return root;
            }
            HashEntry<K, V> xppl = xpp.tree.left;
            if (xp == xppl) {
                HashEntry<K, V> xppr = xpp.tree.right;
                if (xppr != null && xppr.tree.red) {
                    xppr.tree.red = false;
                    xp.tree.red = false;
                    xpp.tree.red = true;
                    x = xpp;
                } else {
                    if (x == xp.tree.right) {
                        x = xp;
                        root = rotateLeft(root, x);
                        xp = x.tree.parent;
                        xpp = xp == null ? null : xp.tree.parent;
                    }
                    if (xp != null) {
                        xp.tree.red = false;
                        if (xpp != null) {
                            xpp.tree.red = true;
                            root = rotateRight(root, xpp);
                        }
                    }
                }
            } else {
                if (xppl != null && xppl.tree.red) {
                    xppl.tree.red = false;
                    xp.tree.red = false;
                    xpp.tree.red = true;
                    x = xpp;
                } else {
                    if (x == xp.tree.left) {
                        x = xp;
                        root = rotateRight(root, x);
                        xp = x.tree.parent;
                        xpp = xp == null ? null : xp.tree.parent;
                    }
                    if (xp != null) {
                        xp.tree.red = false;
                        if (xpp != null) {
                            xpp.tree.red = true;
                            root = rotateLeft(root, xpp);
                        }
                    }
                }
            }
        }
    }

    private static <K, V> HashEntry<K, V> balanceDeletion(HashEntry<K, V> root, HashEntry<K, V> x) {
        while (true) {
            if (x == null || x == root) {
                return root;
            }
            HashEntry<K, V> xp = x.tree.parent;
            if (xp == null) {
                x.tree.red = false;
                return x;
            } else if (x.tree.red) {
                x.tree.red = false;
                return root;
            }
            HashEntry<K, V> xpl = xp.tree.left;
            if (xpl == x) {
                HashEntry<K, V> xpr = xp.tree.right;
                if (red(xpr)) {
                    xpr.tree.red = false;
                    xp.tree.red = true;
                    root = rotateLeft(root, xp);
                    xp = x.tree.parent;
                    xpr = xp == null ? null : xp.tree.right;
                }
                if (xpr == null) {
                    x = xp;
                } else {
                    HashEntry<K, V> sl = xpr.tree.left;
                    HashEntry<K, V> sr = xpr.tree.right;
                    if (!red(sr) && !red(sl)) {
                        xpr.tree.red = true;
                        x = xp;
                    } else {
                        if (!red(sr)) {
                            if (sl != null) {
                                sl.tree.red = false;
                            }
                            xpr.tree.red = true;
                            root = rotateRight(root, xpr);
                            xp = x.tree.parent;
                            xpr = xp == null ? null : xp.tree.right;
                        }
                        if (xpr != null) {
                            xpr.tree.red = xp != null && xp.tree.red;
                            sr = xpr.tree.right;
                            if (sr != null) {
                                sr.tree.red = false;
                            }
                        }
                        if (xp != null) {
                            xp.tree.red = false;
                            root = rotateLeft(root, xp);
                        }
                        x = root;
                    }
                }
            } else {
                if (red(xpl)) {
                    xpl.tree.red = false;
                    xp.tree.red = true;
                    root = rotateRight(root, xp);
                    xp = x.tree.parent;
                    xpl = xp == null ? null : xp.tree.left;
                }
                if (xpl == null) {
                    x = xp;
                } else {
                    HashEntry<K, V> sl = xpl.tree.left;
                    HashEntry<K, V> sr = xpl.tree.right;
                    if (!red(sl) && !red(sr)) {
                        xpl.tree.red = true;
                        x = xp;
                    } else {
                        if (!red(sl)) {
                            if (sr != null) {
                                sr.tree.red = false;
                            }
                            xpl.tree.red = true;
                            root = rotateLeft(root, xpl);
                            xp = x.tree.parent;
                            xpl = xp == null ? null : xp.tree.left;
                        }
                        if (xpl != null) {
                            xpl.tree.red = xp != null && xp.tree.red;
                            sl = xpl.tree.left;
                            if (sl != null) {
                                sl.tree.red = false;
                            }
                        }
                        if (xp != null) {
                            xp.tree.red = false;
                            root = rotateRight(root, xp);
                        }
                        x = root;
                    }
                }
            }
        }
    }

    // --- Map API -------------------------------------------------------------------------------------------

    @Override
    public int size() {
        return elementCount;
    }

    @Override
    public boolean isEmpty() {
        return elementCount == 0;
    }

    @Override
    public V get(Object key) {
        HashEntry<K, V> e = getEntry(key);
        return e == null ? null : e.value;
    }

    @Override
    public V getOrDefault(K key, V defaultValue) {
        HashEntry<K, V> e = getEntry(key);
        return e == null ? defaultValue : e.value;
    }

    @Override
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    @Override
    public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return putVal(hash(key), key, value, true, true);
    }

    @Override
    public void putAll(TMap<? extends K, ? extends V> map) {
        putMapEntries(map, true);
    }

    @Override
    public V remove(Object key) {
        HashEntry<K, V> e = removeNode(hash(key), key, null, false, true);
        return e == null ? null : e.value;
    }

    @Override
    public boolean remove(Object key, Object value) {
        return removeNode(hash(key), key, value, true, true) != null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        HashEntry<K, V> e = getEntry(key);
        if (e != null && (e.value == oldValue || (e.value != null && e.value.equals(oldValue)))) {
            e.value = newValue;
            afterNodeAccess(e);
            return true;
        }
        return false;
    }

    @Override
    public V replace(K key, V value) {
        HashEntry<K, V> e = getEntry(key);
        if (e != null) {
            V oldValue = e.value;
            e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
        return null;
    }

    @Override
    public void clear() {
        modCount++;
        if (elementCount > 0) {
            elementCount = 0;
            HashEntry<K, V>[] tab = elementData;
            for (int i = 0; i < tab.length; ++i) {
                tab[i] = null;
            }
        }
    }

    @Override
    public boolean containsValue(Object value) {
        if (elementCount > 0) {
            for (HashEntry<K, V> e : elementData) {
                for (; e != null; e = e.next) {
                    if (e.value == value || (value != null && value.equals(e.value))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Shared start of computeIfAbsent/compute/merge: the JDK resizes before inserting when already over threshold.
    private HashEntry<K, V>[] tableForCompute() {
        HashEntry<K, V>[] tab = elementData;
        if (elementCount > threshold || tab.length == 0) {
            tab = resize();
        }
        return tab;
    }

    private HashEntry<K, V> findInBin(HashEntry<K, V> first, int hash, Object key) {
        if (first == null) {
            return null;
        }
        if (first.tree != null) {
            return find(root(first), hash, key, null);
        }
        for (HashEntry<K, V> e = first; e != null; e = e.next) {
            if (e.origKeyHash == hash && keyEquals(key, e.key)) {
                return e;
            }
        }
        return null;
    }

    private static <K, V> int binLength(HashEntry<K, V> first) {
        int count = 0;
        for (HashEntry<K, V> e = first; e != null; e = e.next) {
            ++count;
        }
        return count;
    }

    // Inserts at the head of a list bin (JDK compute family), treeifying as the JDK does.
    private void insertForCompute(HashEntry<K, V>[] tab, int i, HashEntry<K, V> first, int hash, K key, V value) {
        if (first != null && first.tree != null) {
            putTreeVal(tab, first, hash, key, value);
        } else {
            int binCount = binLength(first);
            tab[i] = newEntry(hash, key, value, first);
            if (binCount >= TREEIFY_THRESHOLD - 1) {
                treeifyBin(tab, hash);
            }
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (mappingFunction == null) {
            throw new NullPointerException();
        }
        int hash = hash(key);
        HashEntry<K, V>[] tab = tableForCompute();
        int i = (tab.length - 1) & hash;
        HashEntry<K, V> first = tab[i];
        HashEntry<K, V> old = findInBin(first, hash, key);
        if (old != null && old.value != null) {
            afterNodeAccess(old);
            return old.value;
        }
        int mc = modCount;
        V v = mappingFunction.apply(key);
        if (mc != modCount) {
            throw new TConcurrentModificationException();
        }
        if (v == null) {
            return null;
        } else if (old != null) {
            old.value = v;
            afterNodeAccess(old);
            return v;
        }
        insertForCompute(tab, i, first, hash, key, v);
        modCount = mc + 1;
        ++elementCount;
        afterNodeInsertion(true);
        return v;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null) {
            throw new NullPointerException();
        }
        int hash = hash(key);
        HashEntry<K, V> e = getNode(hash, key);
        if (e != null && e.value != null) {
            int mc = modCount;
            V v = remappingFunction.apply(key, e.value);
            if (mc != modCount) {
                throw new TConcurrentModificationException();
            }
            if (v == null) {
                removeNode(hash, key, null, false, true);
            } else {
                e.value = v;
                afterNodeAccess(e);
            }
            return v;
        }
        return null;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null) {
            throw new NullPointerException();
        }
        int hash = hash(key);
        HashEntry<K, V>[] tab = tableForCompute();
        int i = (tab.length - 1) & hash;
        HashEntry<K, V> first = tab[i];
        HashEntry<K, V> old = findInBin(first, hash, key);
        V oldValue = old == null ? null : old.value;
        int mc = modCount;
        V v = remappingFunction.apply(key, oldValue);
        if (mc != modCount) {
            throw new TConcurrentModificationException();
        }
        if (old != null) {
            if (v != null) {
                old.value = v;
                afterNodeAccess(old);
            } else {
                removeNode(hash, key, null, false, true);
            }
        } else if (v != null) {
            insertForCompute(tab, i, first, hash, key, v);
            modCount = mc + 1;
            ++elementCount;
            afterNodeInsertion(true);
        }
        return v;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (value == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        int hash = hash(key);
        HashEntry<K, V>[] tab = tableForCompute();
        int i = (tab.length - 1) & hash;
        HashEntry<K, V> first = tab[i];
        HashEntry<K, V> old = findInBin(first, hash, key);
        if (old != null) {
            V v;
            if (old.value != null) {
                int mc = modCount;
                v = remappingFunction.apply(old.value, value);
                if (mc != modCount) {
                    throw new TConcurrentModificationException();
                }
            } else {
                v = value;
            }
            if (v != null) {
                old.value = v;
                afterNodeAccess(old);
            } else {
                removeNode(hash, key, null, false, true);
            }
            return v;
        }
        insertForCompute(tab, i, first, hash, key, value);
        ++modCount;
        ++elementCount;
        afterNodeInsertion(true);
        return value;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        if (elementCount > 0) {
            int mc = modCount;
            for (HashEntry<K, V> e : elementData) {
                for (; e != null; e = e.next) {
                    action.accept(e.key, e.value);
                }
            }
            if (modCount != mc) {
                throw new TConcurrentModificationException();
            }
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null) {
            throw new NullPointerException();
        }
        if (elementCount > 0) {
            int mc = modCount;
            for (HashEntry<K, V> e : elementData) {
                for (; e != null; e = e.next) {
                    e.value = function.apply(e.key, e.value);
                }
            }
            if (modCount != mc) {
                throw new TConcurrentModificationException();
            }
        }
    }

    @Rename("clone")
    @SuppressWarnings("unchecked")
    public TObject clone0() {
        THashMap<K, V> result;
        try {
            result = (THashMap<K, V>) super.clone();
        } catch (TCloneNotSupportedException e) {
            throw new InternalError(e);
        }
        result.reinitialize();
        result.putMapEntries(this, false);
        return result;
    }

    // --- views and iteration -------------------------------------------------------------------------------

    abstract static class HashIterator<K, V> {
        final THashMap<K, V> map;
        HashEntry<K, V> next;
        HashEntry<K, V> current;
        int expectedModCount;
        int index;

        HashIterator(THashMap<K, V> map) {
            this.map = map;
            expectedModCount = map.modCount;
            if (map.elementCount > 0) {
                advance();
            }
        }

        private void advance() {
            HashEntry<K, V>[] t = map.elementData;
            while (index < t.length && (next = t[index++]) == null) {
                // skip empty bins
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        final HashEntry<K, V> nextNode() {
            HashEntry<K, V> e = next;
            if (map.modCount != expectedModCount) {
                throw new TConcurrentModificationException();
            }
            if (e == null) {
                throw new TNoSuchElementException();
            }
            current = e;
            next = e.next;
            if (next == null) {
                advance();
            }
            return e;
        }

        public final void remove() {
            HashEntry<K, V> p = current;
            if (p == null) {
                throw new TIllegalStateException();
            }
            if (map.modCount != expectedModCount) {
                throw new TConcurrentModificationException();
            }
            current = null;
            map.removeNode(p.origKeyHash, p.key, null, false, false);
            expectedModCount = map.modCount;
        }
    }

    static final class EntryIterator<K, V> extends HashIterator<K, V> implements TIterator<TMap.Entry<K, V>> {
        EntryIterator(THashMap<K, V> map) {
            super(map);
        }

        @Override
        public TMap.Entry<K, V> next() {
            return nextNode();
        }
    }

    static final class KeyIterator<K, V> extends HashIterator<K, V> implements TIterator<K> {
        KeyIterator(THashMap<K, V> map) {
            super(map);
        }

        @Override
        public K next() {
            return nextNode().key;
        }
    }

    static final class ValueIterator<K, V> extends HashIterator<K, V> implements TIterator<V> {
        ValueIterator(THashMap<K, V> map) {
            super(map);
        }

        @Override
        public V next() {
            return nextNode().value;
        }
    }

    static class HashMapEntrySet<K, V> extends TAbstractSet<TMap.Entry<K, V>> {
        private final THashMap<K, V> associatedMap;

        HashMapEntrySet(THashMap<K, V> hm) {
            associatedMap = hm;
        }

        THashMap<K, V> hashMap() {
            return associatedMap;
        }

        @Override
        public int size() {
            return associatedMap.elementCount;
        }

        @Override
        public void clear() {
            associatedMap.clear();
        }

        @Override
        public boolean remove(Object object) {
            if (object instanceof TMap.Entry) {
                TMap.Entry<?, ?> e = (TMap.Entry<?, ?>) object;
                Object key = e.getKey();
                return associatedMap.removeNode(hash(key), key, e.getValue(), true, true) != null;
            }
            return false;
        }

        @Override
        public boolean contains(Object object) {
            if (object instanceof TMap.Entry) {
                TMap.Entry<?, ?> e = (TMap.Entry<?, ?>) object;
                HashEntry<K, V> candidate = associatedMap.getEntry(e.getKey());
                return candidate != null && candidate.equals(e);
            }
            return false;
        }

        @Override
        public TIterator<TMap.Entry<K, V>> iterator() {
            return new EntryIterator<>(associatedMap);
        }

        @Override
        public void forEach(Consumer<? super TMap.Entry<K, V>> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            if (associatedMap.elementCount > 0) {
                int mc = associatedMap.modCount;
                for (HashEntry<K, V> e : associatedMap.elementData) {
                    for (; e != null; e = e.next) {
                        action.accept(e);
                    }
                }
                if (associatedMap.modCount != mc) {
                    throw new TConcurrentModificationException();
                }
            }
        }
    }

    @Override
    public TSet<TMap.Entry<K, V>> entrySet() {
        return new HashMapEntrySet<>(this);
    }

    @Override
    public TSet<K> keySet() {
        if (cachedKeySet == null) {
            cachedKeySet = new TAbstractSet<K>() {
                @Override
                public int size() {
                    return elementCount;
                }

                @Override
                public void clear() {
                    THashMap.this.clear();
                }

                @Override
                public TIterator<K> iterator() {
                    return new KeyIterator<>(THashMap.this);
                }

                @Override
                public boolean contains(Object o) {
                    return containsKey(o);
                }

                @Override
                public boolean remove(Object key) {
                    return removeNode(hash(key), key, null, false, true) != null;
                }

                @Override
                public void forEach(Consumer<? super K> action) {
                    if (action == null) {
                        throw new NullPointerException();
                    }
                    if (elementCount > 0) {
                        int mc = modCount;
                        for (HashEntry<K, V> e : elementData) {
                            for (; e != null; e = e.next) {
                                action.accept(e.key);
                            }
                        }
                        if (modCount != mc) {
                            throw new TConcurrentModificationException();
                        }
                    }
                }
            };
        }
        return cachedKeySet;
    }

    @Override
    public TCollection<V> values() {
        if (cachedValues == null) {
            cachedValues = new TAbstractCollection<V>() {
                @Override
                public int size() {
                    return elementCount;
                }

                @Override
                public void clear() {
                    THashMap.this.clear();
                }

                @Override
                public TIterator<V> iterator() {
                    return new ValueIterator<>(THashMap.this);
                }

                @Override
                public boolean contains(Object o) {
                    return containsValue(o);
                }

                @Override
                public void forEach(Consumer<? super V> action) {
                    if (action == null) {
                        throw new NullPointerException();
                    }
                    if (elementCount > 0) {
                        int mc = modCount;
                        for (HashEntry<K, V> e : elementData) {
                            for (; e != null; e = e.next) {
                                action.accept(e.value);
                            }
                        }
                        if (modCount != mc) {
                            throw new TConcurrentModificationException();
                        }
                    }
                }
            };
        }
        return cachedValues;
    }
}
