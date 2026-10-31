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
package org.teavm.classlib.java.util.concurrent;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.util.TAbstractMap;
import org.teavm.classlib.java.util.TCollection;
import org.teavm.classlib.java.util.TEnumeration;
import org.teavm.classlib.java.util.TIterator;
import org.teavm.classlib.java.util.TMap;
import org.teavm.classlib.java.util.TSet;

/**
 * {@code java.util.concurrent.ConcurrentHashMap}. The table follows the JDK's layout exactly, so iteration order,
 * {@code toString} and the bulk operations see entries in the same order as on the JDK: the same initial sizing and
 * resize thresholds, appending to a bin's list, prepending to a tree bin, and resizes that split each bin keeping
 * the trailing run that stays together and reversing the nodes before it. A resize leaves forwarding nodes in the
 * old table, which iterators created before it follow into the new one.
 */
public class TConcurrentHashMap<K, V> extends TAbstractMap<K, V> implements TConcurrentMap<K, V>, TSerializable {
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;
    private static final int TREEIFY_THRESHOLD = 8;
    private static final int UNTREEIFY_THRESHOLD = 6;
    private static final int MIN_TREEIFY_CAPACITY = 64;
    private static final int MOVED = -1;
    private static final int TREEBIN = -2;
    private static final int HASH_BITS = 0x7fffffff;

    static class Node<K, V> {
        final int hash;
        final K key;
        V val;
        Node<K, V> next;

        Node(int hash, K key, V val) {
            this.hash = hash;
            this.key = key;
            this.val = val;
        }

        Node(int hash, K key, V val, Node<K, V> next) {
            this(hash, key, val);
            this.next = next;
        }
    }

    static final class ForwardingNode<K, V> extends Node<K, V> {
        final Node<K, V>[] nextTable;

        ForwardingNode(Node<K, V>[] tab) {
            super(MOVED, null, null);
            nextTable = tab;
        }
    }

    // A bin that has outgrown a list: new keys are linked in front of the others.
    static final class TreeBin<K, V> extends Node<K, V> {
        Node<K, V> first;

        TreeBin(Node<K, V> first) {
            super(TREEBIN, null, null);
            this.first = first;
        }

        int count() {
            int count = 0;
            for (Node<K, V> p = first; p != null; p = p.next) {
                count++;
            }
            return count;
        }
    }

    private Node<K, V>[] table;
    private long count;
    private int sizeCtl;
    private KeySetView<K, V> keySetView;
    private ValuesView<K, V> valuesView;
    private EntrySetView<K, V> entrySetView;

    public TConcurrentHashMap() {
    }

    public TConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, LOAD_FACTOR, 1);
    }

    public TConcurrentHashMap(TMap<? extends K, ? extends V> m) {
        sizeCtl = DEFAULT_CAPACITY;
        putAll(m);
    }

    public TConcurrentHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, 1);
    }

    public TConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0) {
            throw new IllegalArgumentException();
        }
        if (initialCapacity < concurrencyLevel) {
            initialCapacity = concurrencyLevel;
        }
        long size = (long) (1.0 + (long) initialCapacity / loadFactor);
        sizeCtl = size >= (long) MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : tableSizeFor((int) size);
    }

    static int spread(int h) {
        return (h ^ (h >>> 16)) & HASH_BITS;
    }

    private static int tableSizeFor(int c) {
        int n = -1 >>> Integer.numberOfLeadingZeros(c - 1);
        return n < 0 ? 1 : n >= MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : n + 1;
    }

    private static boolean keyMatches(Node<?, ?> e, int hash, Object key) {
        Object ek = e.key;
        return e.hash == hash && (ek == key || ek != null && key.equals(ek));
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Node<K, V>[] newTable(int n) {
        return (Node<K, V>[]) new Node<?, ?>[n];
    }

    private Node<K, V>[] initTable() {
        Node<K, V>[] tab = table;
        if (tab == null || tab.length == 0) {
            int n = sizeCtl > 0 ? sizeCtl : DEFAULT_CAPACITY;
            tab = newTable(n);
            table = tab;
            sizeCtl = n - (n >>> 2);
        }
        return tab;
    }

    private void addCount(long x, int check) {
        count += x;
        if (check >= 0) {
            Node<K, V>[] tab = table;
            while (count >= (long) sizeCtl && tab != null && tab.length < MAXIMUM_CAPACITY) {
                transfer(tab);
                tab = table;
            }
        }
    }

    private void tryPresize(int size) {
        int c = size >= (MAXIMUM_CAPACITY >>> 1) ? MAXIMUM_CAPACITY : tableSizeFor(size + (size >>> 1) + 1);
        while (true) {
            int sc = sizeCtl;
            Node<K, V>[] tab = table;
            if (tab == null || tab.length == 0) {
                int n = sc > c ? sc : c;
                table = newTable(n);
                sizeCtl = n - (n >>> 2);
            } else if (c <= sc || tab.length >= MAXIMUM_CAPACITY) {
                break;
            } else {
                transfer(tab);
            }
        }
    }

    private void transfer(Node<K, V>[] tab) {
        int n = tab.length;
        Node<K, V>[] nextTab = newTable(n << 1);
        ForwardingNode<K, V> fwd = new ForwardingNode<>(nextTab);
        for (int i = n - 1; i >= 0; --i) {
            Node<K, V> f = tab[i];
            if (f instanceof TreeBin) {
                splitTreeBin((TreeBin<K, V>) f, nextTab, i, n);
            } else if (f != null) {
                int runBit = f.hash & n;
                Node<K, V> lastRun = f;
                for (Node<K, V> p = f.next; p != null; p = p.next) {
                    int b = p.hash & n;
                    if (b != runBit) {
                        runBit = b;
                        lastRun = p;
                    }
                }
                Node<K, V> ln = runBit == 0 ? lastRun : null;
                Node<K, V> hn = runBit == 0 ? null : lastRun;
                for (Node<K, V> p = f; p != lastRun; p = p.next) {
                    if ((p.hash & n) == 0) {
                        ln = new Node<>(p.hash, p.key, p.val, ln);
                    } else {
                        hn = new Node<>(p.hash, p.key, p.val, hn);
                    }
                }
                nextTab[i] = ln;
                nextTab[i + n] = hn;
            }
            tab[i] = fwd;
        }
        table = nextTab;
        sizeCtl = (n << 1) - (n >>> 1);
    }

    private static <K, V> void splitTreeBin(TreeBin<K, V> t, Node<K, V>[] nextTab, int i, int n) {
        Node<K, V> lo = null;
        Node<K, V> loTail = null;
        Node<K, V> hi = null;
        Node<K, V> hiTail = null;
        int lc = 0;
        int hc = 0;
        for (Node<K, V> e = t.first; e != null; e = e.next) {
            Node<K, V> p = new Node<>(e.hash, e.key, e.val);
            if ((e.hash & n) == 0) {
                if (loTail == null) {
                    lo = p;
                } else {
                    loTail.next = p;
                }
                loTail = p;
                ++lc;
            } else {
                if (hiTail == null) {
                    hi = p;
                } else {
                    hiTail.next = p;
                }
                hiTail = p;
                ++hc;
            }
        }
        nextTab[i] = lc <= UNTREEIFY_THRESHOLD ? lo : new TreeBin<>(lo);
        nextTab[i + n] = hc <= UNTREEIFY_THRESHOLD ? hi : new TreeBin<>(hi);
    }

    private void treeifyBin(Node<K, V>[] tab, int index) {
        if (tab.length < MIN_TREEIFY_CAPACITY) {
            tryPresize(tab.length << 1);
        } else {
            Node<K, V> b = tab[index];
            if (b != null && b.hash >= 0) {
                tab[index] = new TreeBin<>(b);
            }
        }
    }

    // A tree bin turns back into a list when a removal leaves it small. The JDK decides from the red-black tree's
    // shape; a tree of three nodes or fewer is always small enough.
    private static <K, V> void unlink(Node<K, V>[] tab, int i, Node<K, V> f, Node<K, V> pred, Node<K, V> e) {
        if (f instanceof TreeBin) {
            TreeBin<K, V> t = (TreeBin<K, V>) f;
            boolean small = t.count() <= 3;
            if (pred == null) {
                t.first = e.next;
            } else {
                pred.next = e.next;
            }
            if (small) {
                tab[i] = untreeify(t.first);
            }
        } else if (pred != null) {
            pred.next = e.next;
        } else {
            tab[i] = e.next;
        }
    }

    private static <K, V> Node<K, V> untreeify(Node<K, V> b) {
        Node<K, V> hd = null;
        Node<K, V> tl = null;
        for (Node<K, V> q = b; q != null; q = q.next) {
            Node<K, V> p = new Node<>(q.hash, q.key, q.val);
            if (tl == null) {
                hd = p;
            } else {
                tl.next = p;
            }
            tl = p;
        }
        return hd;
    }

    private static <K, V> Node<K, V> binHead(Node<K, V> f) {
        return f instanceof TreeBin ? ((TreeBin<K, V>) f).first : f;
    }

    @Override
    public int size() {
        return count < 0L ? 0 : count > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    @Override
    public boolean isEmpty() {
        return count <= 0L;
    }

    public long mappingCount() {
        return Math.max(count, 0L);
    }

    @Override
    public V get(Object key) {
        int h = spread(key.hashCode());
        Node<K, V>[] tab = table;
        if (tab == null || tab.length == 0) {
            return null;
        }
        Node<K, V> e = tab[(tab.length - 1) & h];
        while (e instanceof ForwardingNode) {
            tab = ((ForwardingNode<K, V>) e).nextTable;
            e = tab[(tab.length - 1) & h];
        }
        for (e = binHead(e); e != null; e = e.next) {
            if (keyMatches(e, h, key)) {
                return e.val;
            }
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            V v = p.val;
            if (v == value || v != null && value.equals(v)) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(Object value) {
        return containsValue(value);
    }

    @Override
    public V put(K key, V value) {
        return putVal(key, value, false);
    }

    final V putVal(K key, V value, boolean onlyIfAbsent) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        int hash = spread(key.hashCode());
        int binCount = 0;
        Node<K, V>[] tab = table;
        while (true) {
            if (tab == null || tab.length == 0) {
                tab = initTable();
                continue;
            }
            int i = (tab.length - 1) & hash;
            Node<K, V> f = tab[i];
            if (f == null) {
                tab[i] = new Node<>(hash, key, value);
                break;
            }
            if (f instanceof ForwardingNode) {
                tab = ((ForwardingNode<K, V>) f).nextTable;
                continue;
            }
            if (onlyIfAbsent && keyMatches(f, hash, key) && f.val != null) {
                return f.val;
            }
            V oldVal = null;
            if (f instanceof TreeBin) {
                TreeBin<K, V> t = (TreeBin<K, V>) f;
                binCount = 2;
                Node<K, V> p = t.first;
                while (p != null && !keyMatches(p, hash, key)) {
                    p = p.next;
                }
                if (p != null) {
                    oldVal = p.val;
                    if (!onlyIfAbsent) {
                        p.val = value;
                    }
                } else {
                    t.first = new Node<>(hash, key, value, t.first);
                }
            } else {
                binCount = 1;
                for (Node<K, V> e = f;; ++binCount) {
                    if (keyMatches(e, hash, key)) {
                        oldVal = e.val;
                        if (!onlyIfAbsent) {
                            e.val = value;
                        }
                        break;
                    }
                    Node<K, V> pred = e;
                    e = e.next;
                    if (e == null) {
                        pred.next = new Node<>(hash, key, value);
                        break;
                    }
                }
            }
            if (binCount >= TREEIFY_THRESHOLD) {
                treeifyBin(tab, i);
            }
            if (oldVal != null) {
                return oldVal;
            }
            break;
        }
        addCount(1L, binCount);
        return null;
    }

    @Override
    public void putAll(TMap<? extends K, ? extends V> m) {
        tryPresize(m.size());
        for (TIterator<? extends TMap.Entry<? extends K, ? extends V>> it = m.entrySet().iterator(); it.hasNext();) {
            TMap.Entry<? extends K, ? extends V> e = it.next();
            putVal(e.getKey(), e.getValue(), false);
        }
    }

    @Override
    public V remove(Object key) {
        return replaceNode(key, null, null);
    }

    // Replaces (value non-null) or removes the node for key, if its value matches cv when cv is non-null.
    final V replaceNode(Object key, V value, Object cv) {
        int hash = spread(key.hashCode());
        Node<K, V>[] tab = table;
        while (true) {
            if (tab == null || tab.length == 0) {
                return null;
            }
            int i = (tab.length - 1) & hash;
            Node<K, V> f = tab[i];
            if (f == null) {
                return null;
            }
            if (f instanceof ForwardingNode) {
                tab = ((ForwardingNode<K, V>) f).nextTable;
                continue;
            }
            Node<K, V> pred = null;
            for (Node<K, V> e = binHead(f); e != null; pred = e, e = e.next) {
                if (keyMatches(e, hash, key)) {
                    V ev = e.val;
                    if (cv != null && cv != ev && (ev == null || !cv.equals(ev))) {
                        return null;
                    }
                    if (value != null) {
                        e.val = value;
                    } else {
                        unlink(tab, i, f, pred, e);
                        addCount(-1L, -1);
                    }
                    return ev;
                }
            }
            return null;
        }
    }

    @Override
    public void clear() {
        long delta = 0L;
        Node<K, V>[] tab = table;
        if (tab != null) {
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K, V> p = binHead(tab[i]); p != null; p = p.next) {
                    --delta;
                }
                tab[i] = null;
            }
        }
        if (delta != 0L) {
            addCount(delta, -1);
        }
    }

    @Override
    public KeySetView<K, V> keySet() {
        KeySetView<K, V> ks = keySetView;
        if (ks == null) {
            ks = new KeySetView<>(this, null);
            keySetView = ks;
        }
        return ks;
    }

    @Override
    public TCollection<V> values() {
        ValuesView<K, V> vs = valuesView;
        if (vs == null) {
            vs = new ValuesView<>(this);
            valuesView = vs;
        }
        return vs;
    }

    @Override
    public TSet<TMap.Entry<K, V>> entrySet() {
        EntrySetView<K, V> es = entrySetView;
        if (es == null) {
            es = new EntrySetView<>(this);
            entrySetView = es;
        }
        return es;
    }

    @Override
    public int hashCode() {
        int h = 0;
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            h += p.key.hashCode() ^ p.val.hashCode();
        }
        return h;
    }

    @Override
    public String toString() {
        Traverser<K, V> it = new Traverser<>(table);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        Node<K, V> p = it.advance();
        while (p != null) {
            K k = p.key;
            V v = p.val;
            sb.append(k == this ? "(this Map)" : k);
            sb.append('=');
            sb.append(v == this ? "(this Map)" : v);
            p = it.advance();
            if (p != null) {
                sb.append(',').append(' ');
            }
        }
        return sb.append('}').toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof TMap)) {
            return false;
        }
        TMap<?, ?> m = (TMap<?, ?>) o;
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            V val = p.val;
            Object v = m.get(p.key);
            if (v == null || v != val && !v.equals(val)) {
                return false;
            }
        }
        for (TIterator<? extends TMap.Entry<?, ?>> iter = m.entrySet().iterator(); iter.hasNext();) {
            TMap.Entry<?, ?> e = iter.next();
            Object mk = e.getKey();
            Object mv = e.getValue();
            if (mk == null || mv == null) {
                return false;
            }
            Object v = get(mk);
            if (v == null || mv != v && !mv.equals(v)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return putVal(key, value, true);
    }

    @Override
    public boolean remove(Object key, Object value) {
        if (key == null) {
            throw new NullPointerException();
        }
        return value != null && replaceNode(key, null, value) != null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        if (key == null || oldValue == null || newValue == null) {
            throw new NullPointerException();
        }
        return replaceNode(key, newValue, oldValue) != null;
    }

    @Override
    public V replace(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        return replaceNode(key, value, null);
    }

    @Override
    public V getOrDefault(K key, V defaultValue) {
        V v = get(key);
        return v == null ? defaultValue : v;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            action.accept(p.key, p.val);
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            V oldValue = p.val;
            K key = p.key;
            while (oldValue != null) {
                V newValue = function.apply(key, oldValue);
                if (newValue == null) {
                    throw new NullPointerException();
                }
                if (replaceNode(key, newValue, oldValue) != null) {
                    break;
                }
                oldValue = get(key);
            }
        }
    }

    boolean removeEntryIf(Predicate<? super TMap.Entry<K, V>> function) {
        if (function == null) {
            throw new NullPointerException();
        }
        boolean removed = false;
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            K k = p.key;
            V v = p.val;
            TMap.Entry<K, V> e = new TAbstractMap.SimpleImmutableEntry<>(k, v);
            if (function.test(e) && replaceNode(k, null, v) != null) {
                removed = true;
            }
        }
        return removed;
    }

    boolean removeValueIf(Predicate<? super V> function) {
        if (function == null) {
            throw new NullPointerException();
        }
        boolean removed = false;
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            K k = p.key;
            V v = p.val;
            if (function.test(v) && replaceNode(k, null, v) != null) {
                removed = true;
            }
        }
        return removed;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (key == null || mappingFunction == null) {
            throw new NullPointerException();
        }
        int h = spread(key.hashCode());
        V val = null;
        int binCount = 0;
        Node<K, V>[] tab = table;
        while (true) {
            if (tab == null || tab.length == 0) {
                tab = initTable();
                continue;
            }
            int i = (tab.length - 1) & h;
            Node<K, V> f = tab[i];
            if (f == null) {
                binCount = 1;
                val = mappingFunction.apply(key);
                if (tab[i] != null) {
                    throw new IllegalStateException("Recursive update");
                }
                if (val != null) {
                    tab[i] = new Node<>(h, key, val);
                }
                break;
            }
            if (f instanceof ForwardingNode) {
                tab = ((ForwardingNode<K, V>) f).nextTable;
                continue;
            }
            if (keyMatches(f, h, key) && f.val != null) {
                return f.val;
            }
            boolean added = false;
            if (f instanceof TreeBin) {
                binCount = 2;
                TreeBin<K, V> t = (TreeBin<K, V>) f;
                Node<K, V> p = t.first;
                while (p != null && !keyMatches(p, h, key)) {
                    p = p.next;
                }
                if (p != null) {
                    val = p.val;
                } else {
                    val = mappingFunction.apply(key);
                    if (val != null) {
                        added = true;
                        t.first = new Node<>(h, key, val, t.first);
                    }
                }
            } else {
                binCount = 1;
                for (Node<K, V> e = f;; ++binCount) {
                    if (keyMatches(e, h, key)) {
                        val = e.val;
                        break;
                    }
                    Node<K, V> pred = e;
                    e = e.next;
                    if (e == null) {
                        val = mappingFunction.apply(key);
                        if (val != null) {
                            if (pred.next != null) {
                                throw new IllegalStateException("Recursive update");
                            }
                            added = true;
                            pred.next = new Node<>(h, key, val);
                        }
                        break;
                    }
                }
            }
            if (binCount >= TREEIFY_THRESHOLD) {
                treeifyBin(tab, i);
            }
            if (!added) {
                return val;
            }
            break;
        }
        if (val != null) {
            addCount(1L, binCount);
        }
        return val;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        int h = spread(key.hashCode());
        V val = null;
        int delta = 0;
        int binCount = 0;
        Node<K, V>[] tab = table;
        while (true) {
            if (tab == null || tab.length == 0) {
                tab = initTable();
                continue;
            }
            int i = (tab.length - 1) & h;
            Node<K, V> f = tab[i];
            if (f == null) {
                break;
            }
            if (f instanceof ForwardingNode) {
                tab = ((ForwardingNode<K, V>) f).nextTable;
                continue;
            }
            boolean tree = f instanceof TreeBin;
            binCount = tree ? 2 : 1;
            Node<K, V> pred = null;
            for (Node<K, V> e = binHead(f); e != null; pred = e, e = e.next) {
                if (keyMatches(e, h, key)) {
                    val = remappingFunction.apply(key, e.val);
                    if (val != null) {
                        e.val = val;
                    } else {
                        delta = -1;
                        unlink(tab, i, f, pred, e);
                    }
                    break;
                }
                if (!tree && e.next != null) {
                    ++binCount;
                }
            }
            break;
        }
        if (delta != 0) {
            addCount(delta, binCount);
        }
        return val;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        return computeOrMerge(key, null, remappingFunction, null);
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (key == null || value == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        return computeOrMerge(key, value, null, remappingFunction);
    }

    // compute when computeFn is given, merge (with the given value) otherwise.
    private V computeOrMerge(K key, V value, BiFunction<? super K, ? super V, ? extends V> computeFn,
            BiFunction<? super V, ? super V, ? extends V> mergeFn) {
        int h = spread(key.hashCode());
        V val = null;
        int delta = 0;
        int binCount = 0;
        Node<K, V>[] tab = table;
        while (true) {
            if (tab == null || tab.length == 0) {
                tab = initTable();
                continue;
            }
            int i = (tab.length - 1) & h;
            Node<K, V> f = tab[i];
            if (f == null) {
                if (computeFn != null) {
                    binCount = 1;
                    val = computeFn.apply(key, null);
                    if (tab[i] != null) {
                        throw new IllegalStateException("Recursive update");
                    }
                    if (val != null) {
                        delta = 1;
                        tab[i] = new Node<>(h, key, val);
                    }
                } else {
                    tab[i] = new Node<>(h, key, value);
                    delta = 1;
                    val = value;
                }
                break;
            }
            if (f instanceof ForwardingNode) {
                tab = ((ForwardingNode<K, V>) f).nextTable;
                continue;
            }
            if (f instanceof TreeBin) {
                TreeBin<K, V> t = (TreeBin<K, V>) f;
                binCount = computeFn != null ? 1 : 2;
                Node<K, V> pred = null;
                Node<K, V> p = t.first;
                while (p != null && !keyMatches(p, h, key)) {
                    pred = p;
                    p = p.next;
                }
                if (computeFn != null) {
                    val = computeFn.apply(key, p == null ? null : p.val);
                } else {
                    val = p == null ? value : mergeFn.apply(p.val, value);
                }
                if (val != null) {
                    if (p != null) {
                        p.val = val;
                    } else {
                        delta = 1;
                        t.first = new Node<>(h, key, val, t.first);
                    }
                } else if (p != null) {
                    delta = -1;
                    unlink(tab, i, t, pred, p);
                }
            } else {
                binCount = 1;
                Node<K, V> pred = null;
                for (Node<K, V> e = f;; ++binCount) {
                    if (keyMatches(e, h, key)) {
                        val = computeFn != null ? computeFn.apply(key, e.val) : mergeFn.apply(e.val, value);
                        if (val != null) {
                            e.val = val;
                        } else {
                            delta = -1;
                            unlink(tab, i, f, pred, e);
                        }
                        break;
                    }
                    pred = e;
                    e = e.next;
                    if (e == null) {
                        val = computeFn != null ? computeFn.apply(key, null) : value;
                        if (val != null) {
                            if (pred.next != null) {
                                throw new IllegalStateException("Recursive update");
                            }
                            delta = 1;
                            pred.next = new Node<>(h, key, val);
                        }
                        break;
                    }
                }
            }
            if (binCount >= TREEIFY_THRESHOLD) {
                treeifyBin(tab, i);
            }
            break;
        }
        if (delta != 0) {
            addCount(delta, binCount);
        }
        return val;
    }

    public TEnumeration<K> keys() {
        return new KeyIterator<>(table, this);
    }

    public TEnumeration<V> elements() {
        return new ValueIterator<>(table, this);
    }

    public static <K> KeySetView<K, Boolean> newKeySet() {
        return new KeySetView<>(new TConcurrentHashMap<>(), Boolean.TRUE);
    }

    public static <K> KeySetView<K, Boolean> newKeySet(int initialCapacity) {
        return new KeySetView<>(new TConcurrentHashMap<>(initialCapacity), Boolean.TRUE);
    }

    public KeySetView<K, V> keySet(V mappedValue) {
        if (mappedValue == null) {
            throw new NullPointerException();
        }
        return new KeySetView<>(this, mappedValue);
    }

    // Bulk operations. They run on the calling thread, which is a valid schedule for every parallelism threshold.

    public void forEach(long parallelismThreshold, BiConsumer<? super K, ? super V> action) {
        forEach(action);
    }

    public <U> void forEach(long parallelismThreshold, BiFunction<? super K, ? super V, ? extends U> transformer,
            Consumer<? super U> action) {
        if (transformer == null || action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            U u = transformer.apply(p.key, p.val);
            if (u != null) {
                action.accept(u);
            }
        }
    }

    public <U> U search(long parallelismThreshold, BiFunction<? super K, ? super V, ? extends U> searchFunction) {
        if (searchFunction == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            U u = searchFunction.apply(p.key, p.val);
            if (u != null) {
                return u;
            }
        }
        return null;
    }

    public <U> U reduce(long parallelismThreshold, BiFunction<? super K, ? super V, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        U r = null;
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            U u = transformer.apply(p.key, p.val);
            if (u != null) {
                r = r == null ? u : reducer.apply(r, u);
            }
        }
        return r;
    }

    public double reduceToDouble(long parallelismThreshold, ToDoubleBiFunction<? super K, ? super V> transformer,
            double basis, DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        double r = basis;
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            r = reducer.applyAsDouble(r, transformer.applyAsDouble(p.key, p.val));
        }
        return r;
    }

    public long reduceToLong(long parallelismThreshold, ToLongBiFunction<? super K, ? super V> transformer,
            long basis, LongBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        long r = basis;
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            r = reducer.applyAsLong(r, transformer.applyAsLong(p.key, p.val));
        }
        return r;
    }

    public int reduceToInt(long parallelismThreshold, ToIntBiFunction<? super K, ? super V> transformer,
            int basis, IntBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        int r = basis;
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            r = reducer.applyAsInt(r, transformer.applyAsInt(p.key, p.val));
        }
        return r;
    }

    public void forEachKey(long parallelismThreshold, Consumer<? super K> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            action.accept(p.key);
        }
    }

    public <U> void forEachKey(long parallelismThreshold, Function<? super K, ? extends U> transformer,
            Consumer<? super U> action) {
        if (transformer == null || action == null) {
            throw new NullPointerException();
        }
        forEach(parallelismThreshold, (k, v) -> transformer.apply(k), action);
    }

    public <U> U searchKeys(long parallelismThreshold, Function<? super K, ? extends U> searchFunction) {
        if (searchFunction == null) {
            throw new NullPointerException();
        }
        return search(parallelismThreshold, (k, v) -> searchFunction.apply(k));
    }

    public K reduceKeys(long parallelismThreshold, BiFunction<? super K, ? super K, ? extends K> reducer) {
        if (reducer == null) {
            throw new NullPointerException();
        }
        return reduce(parallelismThreshold, (k, v) -> k, reducer);
    }

    public <U> U reduceKeys(long parallelismThreshold, Function<? super K, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduce(parallelismThreshold, (k, v) -> transformer.apply(k), reducer);
    }

    public double reduceKeysToDouble(long parallelismThreshold, ToDoubleFunction<? super K> transformer,
            double basis, DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduceToDouble(parallelismThreshold, (k, v) -> transformer.applyAsDouble(k), basis, reducer);
    }

    public long reduceKeysToLong(long parallelismThreshold, ToLongFunction<? super K> transformer, long basis,
            LongBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduceToLong(parallelismThreshold, (k, v) -> transformer.applyAsLong(k), basis, reducer);
    }

    public int reduceKeysToInt(long parallelismThreshold, ToIntFunction<? super K> transformer, int basis,
            IntBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduceToInt(parallelismThreshold, (k, v) -> transformer.applyAsInt(k), basis, reducer);
    }

    public void forEachValue(long parallelismThreshold, Consumer<? super V> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            action.accept(p.val);
        }
    }

    public <U> void forEachValue(long parallelismThreshold, Function<? super V, ? extends U> transformer,
            Consumer<? super U> action) {
        if (transformer == null || action == null) {
            throw new NullPointerException();
        }
        forEach(parallelismThreshold, (k, v) -> transformer.apply(v), action);
    }

    public <U> U searchValues(long parallelismThreshold, Function<? super V, ? extends U> searchFunction) {
        if (searchFunction == null) {
            throw new NullPointerException();
        }
        return search(parallelismThreshold, (k, v) -> searchFunction.apply(v));
    }

    public V reduceValues(long parallelismThreshold, BiFunction<? super V, ? super V, ? extends V> reducer) {
        if (reducer == null) {
            throw new NullPointerException();
        }
        return reduce(parallelismThreshold, (k, v) -> v, reducer);
    }

    public <U> U reduceValues(long parallelismThreshold, Function<? super V, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduce(parallelismThreshold, (k, v) -> transformer.apply(v), reducer);
    }

    public double reduceValuesToDouble(long parallelismThreshold, ToDoubleFunction<? super V> transformer,
            double basis, DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduceToDouble(parallelismThreshold, (k, v) -> transformer.applyAsDouble(v), basis, reducer);
    }

    public long reduceValuesToLong(long parallelismThreshold, ToLongFunction<? super V> transformer, long basis,
            LongBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduceToLong(parallelismThreshold, (k, v) -> transformer.applyAsLong(v), basis, reducer);
    }

    public int reduceValuesToInt(long parallelismThreshold, ToIntFunction<? super V> transformer, int basis,
            IntBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduceToInt(parallelismThreshold, (k, v) -> transformer.applyAsInt(v), basis, reducer);
    }

    public void forEachEntry(long parallelismThreshold, Consumer<? super TMap.Entry<K, V>> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            action.accept(new MapEntry<>(p.key, p.val, this));
        }
    }

    public <U> void forEachEntry(long parallelismThreshold, Function<TMap.Entry<K, V>, ? extends U> transformer,
            Consumer<? super U> action) {
        if (transformer == null || action == null) {
            throw new NullPointerException();
        }
        forEach(parallelismThreshold, (k, v) -> transformer.apply(new MapEntry<>(k, v, this)), action);
    }

    public <U> U searchEntries(long parallelismThreshold, Function<TMap.Entry<K, V>, ? extends U> searchFunction) {
        if (searchFunction == null) {
            throw new NullPointerException();
        }
        return search(parallelismThreshold, (k, v) -> searchFunction.apply(new MapEntry<>(k, v, this)));
    }

    public TMap.Entry<K, V> reduceEntries(long parallelismThreshold,
            BiFunction<TMap.Entry<K, V>, TMap.Entry<K, V>, ? extends TMap.Entry<K, V>> reducer) {
        if (reducer == null) {
            throw new NullPointerException();
        }
        TMap.Entry<K, V> r = null;
        Traverser<K, V> it = new Traverser<>(table);
        for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
            TMap.Entry<K, V> e = new MapEntry<>(p.key, p.val, this);
            r = r == null ? e : reducer.apply(r, e);
        }
        return r;
    }

    public <U> U reduceEntries(long parallelismThreshold, Function<TMap.Entry<K, V>, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduce(parallelismThreshold, (k, v) -> transformer.apply(new MapEntry<>(k, v, this)), reducer);
    }

    public double reduceEntriesToDouble(long parallelismThreshold, ToDoubleFunction<TMap.Entry<K, V>> transformer,
            double basis, DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduceToDouble(parallelismThreshold,
                (k, v) -> transformer.applyAsDouble(new MapEntry<>(k, v, this)), basis, reducer);
    }

    public long reduceEntriesToLong(long parallelismThreshold, ToLongFunction<TMap.Entry<K, V>> transformer,
            long basis, LongBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduceToLong(parallelismThreshold, (k, v) -> transformer.applyAsLong(new MapEntry<>(k, v, this)),
                basis, reducer);
    }

    public int reduceEntriesToInt(long parallelismThreshold, ToIntFunction<TMap.Entry<K, V>> transformer,
            int basis, IntBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        return reduceToInt(parallelismThreshold, (k, v) -> transformer.applyAsInt(new MapEntry<>(k, v, this)),
                basis, reducer);
    }

    /**
     * Walks the bins in index order. On meeting a forwarding node it switches to the next table and visits the two
     * bins the old bin split into, then returns to the old table, as the JDK traverser does.
     */
    static class Traverser<K, V> {
        Node<K, V>[] tab;
        Node<K, V> next;
        private TableStack<K, V> stack;
        private TableStack<K, V> spare;
        private int index;
        private int baseIndex;
        private final int baseLimit;
        private final int baseSize;

        Traverser(Node<K, V>[] tab) {
            this.tab = tab;
            baseSize = tab == null ? 0 : tab.length;
            baseLimit = baseSize;
        }

        final Node<K, V> advance() {
            Node<K, V> e = next;
            if (e != null) {
                e = e.next;
            }
            while (true) {
                if (e != null) {
                    next = e;
                    return e;
                }
                Node<K, V>[] t = tab;
                int i = index;
                if (baseIndex >= baseLimit || t == null || t.length <= i || i < 0) {
                    next = null;
                    return null;
                }
                int n = t.length;
                e = t[i];
                if (e != null && e.hash < 0) {
                    if (e instanceof ForwardingNode) {
                        tab = ((ForwardingNode<K, V>) e).nextTable;
                        e = null;
                        pushState(t, i, n);
                        continue;
                    }
                    e = binHead(e);
                }
                if (stack != null) {
                    recoverState(n);
                } else {
                    index = i + baseSize;
                    if (index >= n) {
                        index = ++baseIndex;
                    }
                }
            }
        }

        private void pushState(Node<K, V>[] t, int i, int n) {
            TableStack<K, V> s = spare;
            if (s != null) {
                spare = s.next;
            } else {
                s = new TableStack<>();
            }
            s.tab = t;
            s.length = n;
            s.index = i;
            s.next = stack;
            stack = s;
        }

        private void recoverState(int n) {
            TableStack<K, V> s = stack;
            while (s != null) {
                int len = s.length;
                index += len;
                if (index < n) {
                    break;
                }
                n = len;
                index = s.index;
                tab = s.tab;
                s.tab = null;
                TableStack<K, V> following = s.next;
                s.next = spare;
                stack = following;
                spare = s;
                s = following;
            }
            if (s == null) {
                index += baseSize;
                if (index >= n) {
                    index = ++baseIndex;
                }
            }
        }
    }

    static final class TableStack<K, V> {
        int length;
        int index;
        Node<K, V>[] tab;
        TableStack<K, V> next;
    }

    static class BaseIterator<K, V> extends Traverser<K, V> {
        final TConcurrentHashMap<K, V> map;
        Node<K, V> lastReturned;

        BaseIterator(Node<K, V>[] tab, TConcurrentHashMap<K, V> map) {
            super(tab);
            this.map = map;
            advance();
        }

        public final boolean hasNext() {
            return next != null;
        }

        public final boolean hasMoreElements() {
            return next != null;
        }

        public final void remove() {
            Node<K, V> p = lastReturned;
            if (p == null) {
                throw new IllegalStateException();
            }
            lastReturned = null;
            map.replaceNode(p.key, null, null);
        }

        final Node<K, V> step() {
            Node<K, V> p = next;
            if (p == null) {
                throw new NoSuchElementException();
            }
            lastReturned = p;
            advance();
            return p;
        }
    }

    static final class KeyIterator<K, V> extends BaseIterator<K, V> implements TIterator<K>, TEnumeration<K> {
        KeyIterator(Node<K, V>[] tab, TConcurrentHashMap<K, V> map) {
            super(tab, map);
        }

        @Override
        public K next() {
            return step().key;
        }

        @Override
        public K nextElement() {
            return next();
        }
    }

    static final class ValueIterator<K, V> extends BaseIterator<K, V> implements TIterator<V>, TEnumeration<V> {
        ValueIterator(Node<K, V>[] tab, TConcurrentHashMap<K, V> map) {
            super(tab, map);
        }

        @Override
        public V next() {
            return step().val;
        }

        @Override
        public V nextElement() {
            return next();
        }
    }

    static final class EntryIterator<K, V> extends BaseIterator<K, V> implements TIterator<TMap.Entry<K, V>> {
        EntryIterator(Node<K, V>[] tab, TConcurrentHashMap<K, V> map) {
            super(tab, map);
        }

        @Override
        public TMap.Entry<K, V> next() {
            Node<K, V> p = step();
            return new MapEntry<>(p.key, p.val, map);
        }
    }

    static final class MapEntry<K, V> implements TMap.Entry<K, V> {
        final K key;
        V val;
        final TConcurrentHashMap<K, V> map;

        MapEntry(K key, V val, TConcurrentHashMap<K, V> map) {
            this.key = key;
            this.val = val;
            this.map = map;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return val;
        }

        @Override
        public int hashCode() {
            return key.hashCode() ^ val.hashCode();
        }

        @Override
        public String toString() {
            return key + "=" + val;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TMap.Entry)) {
                return false;
            }
            TMap.Entry<?, ?> e = (TMap.Entry<?, ?>) o;
            Object k = e.getKey();
            Object v = e.getValue();
            return k != null && v != null && (k == key || k.equals(key)) && (v == val || v.equals(val));
        }

        @Override
        public V setValue(V value) {
            if (value == null) {
                throw new NullPointerException();
            }
            V v = val;
            val = value;
            map.put(key, value);
            return v;
        }
    }

    abstract static class CollectionView<K, V, E> implements TCollection<E>, TSerializable {
        final TConcurrentHashMap<K, V> map;

        CollectionView(TConcurrentHashMap<K, V> map) {
            this.map = map;
        }

        public TConcurrentHashMap<K, V> getMap() {
            return map;
        }

        @Override
        public final void clear() {
            map.clear();
        }

        @Override
        public final int size() {
            return map.size();
        }

        @Override
        public final boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public final Object[] toArray() {
            Object[] r = new Object[map.size()];
            int i = 0;
            for (TIterator<E> it = iterator(); it.hasNext();) {
                if (i == r.length) {
                    r = Arrays.copyOf(r, r.length + (r.length >>> 1) + 1);
                }
                r[i++] = it.next();
            }
            return i == r.length ? r : Arrays.copyOf(r, i);
        }

        @Override
        @SuppressWarnings("unchecked")
        public final <T> T[] toArray(T[] a) {
            int m = map.size();
            T[] r = a.length >= m ? a : (T[]) Array.newInstance(a.getClass().getComponentType(), m);
            int n = r.length;
            int i = 0;
            for (TIterator<E> it = iterator(); it.hasNext();) {
                if (i == n) {
                    n += (n >>> 1) + 1;
                    r = Arrays.copyOf(r, n);
                }
                r[i++] = (T) it.next();
            }
            if (a == r && i < n) {
                r[i] = null;
                return r;
            }
            return i == n ? r : Arrays.copyOf(r, i);
        }

        @Override
        public final String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            TIterator<E> it = iterator();
            while (it.hasNext()) {
                Object e = it.next();
                sb.append(e == this ? "(this Collection)" : e);
                if (it.hasNext()) {
                    sb.append(',').append(' ');
                }
            }
            return sb.append(']').toString();
        }

        @Override
        public final boolean containsAll(TCollection<?> c) {
            if (c != this) {
                for (TIterator<?> it = c.iterator(); it.hasNext();) {
                    Object e = it.next();
                    if (e == null || !contains(e)) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public boolean removeAll(TCollection<?> c) {
            if (c == null) {
                throw new NullPointerException();
            }
            boolean modified = false;
            Node<K, V>[] t = map.table;
            if (t == null) {
                return false;
            } else if (c instanceof TSet<?> && c.size() > t.length) {
                for (TIterator<?> it = iterator(); it.hasNext();) {
                    if (c.contains(it.next())) {
                        it.remove();
                        modified = true;
                    }
                }
            } else {
                for (TIterator<?> it = c.iterator(); it.hasNext();) {
                    modified |= remove(it.next());
                }
            }
            return modified;
        }

        @Override
        public final boolean retainAll(TCollection<?> c) {
            if (c == null) {
                throw new NullPointerException();
            }
            boolean modified = false;
            for (TIterator<E> it = iterator(); it.hasNext();) {
                if (!c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }
    }

    public static final class KeySetView<K, V> extends CollectionView<K, V, K> implements TSet<K>, TSerializable {
        private final V value;

        KeySetView(TConcurrentHashMap<K, V> map, V value) {
            super(map);
            this.value = value;
        }

        public V getMappedValue() {
            return value;
        }

        @Override
        public boolean contains(Object o) {
            return map.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return map.remove(o) != null;
        }

        @Override
        public TIterator<K> iterator() {
            return new KeyIterator<>(map.table, map);
        }

        @Override
        public boolean add(K e) {
            V v = value;
            if (v == null) {
                throw new UnsupportedOperationException();
            }
            return map.putVal(e, v, true) == null;
        }

        @Override
        public boolean addAll(TCollection<? extends K> c) {
            boolean added = false;
            V v = value;
            if (v == null) {
                throw new UnsupportedOperationException();
            }
            for (TIterator<? extends K> it = c.iterator(); it.hasNext();) {
                if (map.putVal(it.next(), v, true) == null) {
                    added = true;
                }
            }
            return added;
        }

        @Override
        public int hashCode() {
            int h = 0;
            for (TIterator<K> it = iterator(); it.hasNext();) {
                h += it.next().hashCode();
            }
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TSet)) {
                return false;
            }
            TSet<?> c = (TSet<?>) o;
            return c == this || containsAll(c) && c.containsAll(this);
        }

        @Override
        public void forEach(Consumer<? super K> action) {
            map.forEachKey(Long.MAX_VALUE, action);
        }
    }

    static final class ValuesView<K, V> extends CollectionView<K, V, V> implements TSerializable {
        ValuesView(TConcurrentHashMap<K, V> map) {
            super(map);
        }

        @Override
        public boolean contains(Object o) {
            return map.containsValue(o);
        }

        @Override
        public boolean remove(Object o) {
            if (o != null) {
                for (TIterator<V> it = iterator(); it.hasNext();) {
                    if (o.equals(it.next())) {
                        it.remove();
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public TIterator<V> iterator() {
            return new ValueIterator<>(map.table, map);
        }

        @Override
        public boolean add(V e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(TCollection<? extends V> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(TCollection<?> c) {
            if (c == null) {
                throw new NullPointerException();
            }
            boolean modified = false;
            for (TIterator<V> it = iterator(); it.hasNext();) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public boolean removeIf(Predicate<? super V> filter) {
            return map.removeValueIf(filter);
        }

        @Override
        public void forEach(Consumer<? super V> action) {
            map.forEachValue(Long.MAX_VALUE, action);
        }
    }

    static final class EntrySetView<K, V> extends CollectionView<K, V, TMap.Entry<K, V>>
            implements TSet<TMap.Entry<K, V>>, TSerializable {
        EntrySetView(TConcurrentHashMap<K, V> map) {
            super(map);
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof TMap.Entry)) {
                return false;
            }
            TMap.Entry<?, ?> e = (TMap.Entry<?, ?>) o;
            Object k = e.getKey();
            if (k == null) {
                return false;
            }
            Object r = map.get(k);
            Object v = e.getValue();
            return r != null && v != null && (v == r || v.equals(r));
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof TMap.Entry)) {
                return false;
            }
            TMap.Entry<?, ?> e = (TMap.Entry<?, ?>) o;
            Object k = e.getKey();
            Object v = e.getValue();
            return k != null && v != null && map.remove(k, v);
        }

        @Override
        public TIterator<TMap.Entry<K, V>> iterator() {
            return new EntryIterator<>(map.table, map);
        }

        @Override
        public boolean add(TMap.Entry<K, V> e) {
            return map.putVal(e.getKey(), e.getValue(), false) == null;
        }

        @Override
        public boolean addAll(TCollection<? extends TMap.Entry<K, V>> c) {
            boolean added = false;
            for (TIterator<? extends TMap.Entry<K, V>> it = c.iterator(); it.hasNext();) {
                if (add(it.next())) {
                    added = true;
                }
            }
            return added;
        }

        @Override
        public boolean removeIf(Predicate<? super TMap.Entry<K, V>> filter) {
            return map.removeEntryIf(filter);
        }

        @Override
        public int hashCode() {
            int h = 0;
            Traverser<K, V> it = new Traverser<>(map.table);
            for (Node<K, V> p = it.advance(); p != null; p = it.advance()) {
                h += p.key.hashCode() ^ p.val.hashCode();
            }
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TSet)) {
                return false;
            }
            TSet<?> c = (TSet<?>) o;
            return c == this || containsAll(c) && c.containsAll(this);
        }

        @Override
        public void forEach(Consumer<? super TMap.Entry<K, V>> action) {
            map.forEachEntry(Long.MAX_VALUE, action);
        }
    }
}
