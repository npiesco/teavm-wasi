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
import org.teavm.classlib.java.lang.TIllegalStateException;

/**
 * java.util.LinkedHashMap: a {@link THashMap} whose entries are also kept in a doubly linked list, in insertion or
 * (with accessOrder) access order, maintained through THashMap's node hooks as on the JDK.
 */
public class TLinkedHashMap<K, V> extends THashMap<K, V> implements TMap<K, V> {
    private final boolean accessOrder;
    transient LinkedHashMapEntry<K, V> head;
    transient LinkedHashMapEntry<K, V> tail;

    static final class LinkedHashMapEntry<K, V> extends HashEntry<K, V> {
        LinkedHashMapEntry<K, V> chainForward;
        LinkedHashMapEntry<K, V> chainBackward;

        LinkedHashMapEntry(K theKey, int hash) {
            super(theKey, hash);
        }
    }

    public TLinkedHashMap() {
        accessOrder = false;
    }

    public TLinkedHashMap(int capacity) {
        super(capacity);
        accessOrder = false;
    }

    public TLinkedHashMap(int capacity, float loadFactor) {
        super(capacity, loadFactor);
        accessOrder = false;
    }

    public TLinkedHashMap(int capacity, float loadFactor, boolean accessOrder) {
        super(capacity, loadFactor);
        this.accessOrder = accessOrder;
    }

    public TLinkedHashMap(TMap<? extends K, ? extends V> map) {
        accessOrder = false;
        putMapEntries(map, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    HashEntry<K, V>[] newElementArray(int s) {
        return new LinkedHashMapEntry[s];
    }

    @Override
    HashEntry<K, V> newEntry(int hash, K key, V value, HashEntry<K, V> next) {
        LinkedHashMapEntry<K, V> entry = new LinkedHashMapEntry<>(key, hash);
        entry.value = value;
        entry.next = next;
        LinkedHashMapEntry<K, V> last = tail;
        tail = entry;
        if (last == null) {
            head = entry;
        } else {
            entry.chainBackward = last;
            last.chainForward = entry;
        }
        return entry;
    }

    @Override
    void afterNodeRemoval(HashEntry<K, V> e) {
        LinkedHashMapEntry<K, V> p = (LinkedHashMapEntry<K, V>) e;
        LinkedHashMapEntry<K, V> b = p.chainBackward;
        LinkedHashMapEntry<K, V> a = p.chainForward;
        p.chainBackward = null;
        p.chainForward = null;
        if (b == null) {
            head = a;
        } else {
            b.chainForward = a;
        }
        if (a == null) {
            tail = b;
        } else {
            a.chainBackward = b;
        }
    }

    @Override
    void afterNodeInsertion(boolean evict) {
        LinkedHashMapEntry<K, V> first = head;
        if (evict && first != null && removeEldestEntry(first)) {
            K key = first.key;
            removeNode(hash(key), key, null, false, true);
        }
    }

    @Override
    void afterNodeAccess(HashEntry<K, V> e) {
        LinkedHashMapEntry<K, V> last = tail;
        if (accessOrder && last != e) {
            LinkedHashMapEntry<K, V> p = (LinkedHashMapEntry<K, V>) e;
            LinkedHashMapEntry<K, V> b = p.chainBackward;
            LinkedHashMapEntry<K, V> a = p.chainForward;
            p.chainForward = null;
            if (b == null) {
                head = a;
            } else {
                b.chainForward = a;
            }
            if (a != null) {
                a.chainBackward = b;
            } else {
                last = b;
            }
            if (last == null) {
                head = p;
            } else {
                p.chainBackward = last;
                last.chainForward = p;
            }
            tail = p;
            ++modCount;
        }
    }

    @Override
    void reinitialize() {
        super.reinitialize();
        head = null;
        tail = null;
    }

    @Override
    public boolean containsValue(Object value) {
        for (LinkedHashMapEntry<K, V> e = head; e != null; e = e.chainForward) {
            if (e.value == value || (value != null && value.equals(e.value))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V get(Object key) {
        HashEntry<K, V> e = getNode(hash(key), key);
        if (e == null) {
            return null;
        }
        if (accessOrder) {
            afterNodeAccess(e);
        }
        return e.value;
    }

    @Override
    public V getOrDefault(K key, V defaultValue) {
        HashEntry<K, V> e = getNode(hash(key), key);
        if (e == null) {
            return defaultValue;
        }
        if (accessOrder) {
            afterNodeAccess(e);
        }
        return e.value;
    }

    @Override
    public void clear() {
        super.clear();
        head = null;
        tail = null;
    }

    protected boolean removeEldestEntry(@SuppressWarnings("unused") Entry<K, V> eldest) {
        return false;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        int mc = modCount;
        for (LinkedHashMapEntry<K, V> e = head; e != null; e = e.chainForward) {
            action.accept(e.key, e.value);
        }
        if (modCount != mc) {
            throw new TConcurrentModificationException();
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null) {
            throw new NullPointerException();
        }
        int mc = modCount;
        for (LinkedHashMapEntry<K, V> e = head; e != null; e = e.chainForward) {
            e.value = function.apply(e.key, e.value);
        }
        if (modCount != mc) {
            throw new TConcurrentModificationException();
        }
    }

    abstract static class LinkedHashIterator<K, V> {
        final TLinkedHashMap<K, V> map;
        LinkedHashMapEntry<K, V> next;
        LinkedHashMapEntry<K, V> current;
        int expectedModCount;

        LinkedHashIterator(TLinkedHashMap<K, V> map) {
            this.map = map;
            next = map.head;
            expectedModCount = map.modCount;
        }

        public final boolean hasNext() {
            return next != null;
        }

        final LinkedHashMapEntry<K, V> nextNode() {
            LinkedHashMapEntry<K, V> e = next;
            if (map.modCount != expectedModCount) {
                throw new TConcurrentModificationException();
            }
            if (e == null) {
                throw new TNoSuchElementException();
            }
            current = e;
            next = e.chainForward;
            return e;
        }

        public final void remove() {
            LinkedHashMapEntry<K, V> p = current;
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

    static final class LinkedEntryIterator<K, V> extends LinkedHashIterator<K, V>
            implements TIterator<TMap.Entry<K, V>> {
        LinkedEntryIterator(TLinkedHashMap<K, V> map) {
            super(map);
        }

        @Override
        public TMap.Entry<K, V> next() {
            return nextNode();
        }
    }

    static final class LinkedKeyIterator<K, V> extends LinkedHashIterator<K, V> implements TIterator<K> {
        LinkedKeyIterator(TLinkedHashMap<K, V> map) {
            super(map);
        }

        @Override
        public K next() {
            return nextNode().key;
        }
    }

    static final class LinkedValueIterator<K, V> extends LinkedHashIterator<K, V> implements TIterator<V> {
        LinkedValueIterator(TLinkedHashMap<K, V> map) {
            super(map);
        }

        @Override
        public V next() {
            return nextNode().value;
        }
    }

    static final class LinkedHashMapEntrySet<K, V> extends HashMapEntrySet<K, V> {
        LinkedHashMapEntrySet(TLinkedHashMap<K, V> map) {
            super(map);
        }

        @Override
        public TIterator<Entry<K, V>> iterator() {
            return new LinkedEntryIterator<>((TLinkedHashMap<K, V>) hashMap());
        }

        @Override
        public void forEach(Consumer<? super Entry<K, V>> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            TLinkedHashMap<K, V> map = (TLinkedHashMap<K, V>) hashMap();
            int mc = map.modCount;
            for (LinkedHashMapEntry<K, V> e = map.head; e != null; e = e.chainForward) {
                action.accept(e);
            }
            if (map.modCount != mc) {
                throw new TConcurrentModificationException();
            }
        }
    }

    @Override
    public TSet<Entry<K, V>> entrySet() {
        return new LinkedHashMapEntrySet<>(this);
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
                    TLinkedHashMap.this.clear();
                }

                @Override
                public TIterator<K> iterator() {
                    return new LinkedKeyIterator<>(TLinkedHashMap.this);
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
                    int mc = modCount;
                    for (LinkedHashMapEntry<K, V> e = head; e != null; e = e.chainForward) {
                        action.accept(e.key);
                    }
                    if (modCount != mc) {
                        throw new TConcurrentModificationException();
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
                    TLinkedHashMap.this.clear();
                }

                @Override
                public TIterator<V> iterator() {
                    return new LinkedValueIterator<>(TLinkedHashMap.this);
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
                    int mc = modCount;
                    for (LinkedHashMapEntry<K, V> e = head; e != null; e = e.chainForward) {
                        action.accept(e.value);
                    }
                    if (modCount != mc) {
                        throw new TConcurrentModificationException();
                    }
                }
            };
        }
        return cachedValues;
    }
}
