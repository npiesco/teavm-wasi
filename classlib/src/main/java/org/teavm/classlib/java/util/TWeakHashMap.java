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

import org.teavm.classlib.java.lang.ref.TReference;
import org.teavm.classlib.java.lang.ref.TReferenceQueue;
import org.teavm.classlib.java.lang.ref.TWeakReference;

/**
 * java.util.WeakHashMap: a hash map whose keys are held through weak references, so an entry disappears once its
 * key is collected. Keys compare with {@code equals} and {@code hashCode}, and a {@code null} key is allowed.
 */
public class TWeakHashMap<K, V> extends TAbstractMap<K, V> implements TMap<K, V> {
    private static final Object NULL_KEY = new Object();

    private final THashMap<WeakKey, V> table;
    private final TReferenceQueue<Object> queue = new TReferenceQueue<>();
    private TSet<Entry<K, V>> entrySet;

    public TWeakHashMap() {
        table = new THashMap<>();
    }

    public TWeakHashMap(int initialCapacity) {
        table = new THashMap<>(initialCapacity);
    }

    public TWeakHashMap(int initialCapacity, float loadFactor) {
        table = new THashMap<>(initialCapacity, loadFactor);
    }

    public TWeakHashMap(TMap<? extends K, ? extends V> map) {
        this();
        putAll(map);
    }

    private final class WeakKey extends TWeakReference<Object> {
        private final int hash;

        WeakKey(Object key, boolean queued) {
            super(key, queued ? queue : null);
            hash = key.hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TWeakHashMap.WeakKey)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            WeakKey other = (WeakKey) obj;
            Object mine = get();
            return hash == other.hash && mine != null && mine.equals(other.get());
        }
    }

    private static Object mask(Object key) {
        return key == null ? NULL_KEY : key;
    }

    @SuppressWarnings("unchecked")
    private static <K> K unmask(Object key) {
        return key == NULL_KEY ? null : (K) key;
    }

    private WeakKey probe(Object key) {
        return new WeakKey(mask(key), false);
    }

    private void expungeStaleEntries() {
        for (TReference<?> stale = queue.poll(); stale != null; stale = queue.poll()) {
            table.remove(stale);
        }
    }

    @Override
    public int size() {
        expungeStaleEntries();
        return table.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public V get(Object key) {
        expungeStaleEntries();
        return table.get(probe(key));
    }

    @Override
    public boolean containsKey(Object key) {
        expungeStaleEntries();
        return table.containsKey(probe(key));
    }

    @Override
    public V put(K key, V value) {
        expungeStaleEntries();
        WeakKey probe = probe(key);
        if (table.containsKey(probe)) {
            return table.put(probe, value);
        }
        table.put(new WeakKey(mask(key), true), value);
        return null;
    }

    @Override
    public V remove(Object key) {
        expungeStaleEntries();
        return table.remove(probe(key));
    }

    @Override
    public void clear() {
        while (queue.poll() != null) {
            // drop references that were already enqueued
        }
        table.clear();
    }

    @Override
    public TSet<Entry<K, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new TAbstractSet<Entry<K, V>>() {
                @Override
                public TIterator<Entry<K, V>> iterator() {
                    expungeStaleEntries();
                    return new EntryIterator(table.entrySet().iterator());
                }

                @Override
                public int size() {
                    return TWeakHashMap.this.size();
                }
            };
        }
        return entrySet;
    }

    private final class EntryIterator implements TIterator<Entry<K, V>> {
        private final TIterator<Entry<WeakKey, V>> inner;
        private Entry<WeakKey, V> pending;
        private Object pendingKey;

        EntryIterator(TIterator<Entry<WeakKey, V>> inner) {
            this.inner = inner;
        }

        // An entry whose key has been collected is skipped; holding the key strongly keeps the returned entry valid.
        @Override
        public boolean hasNext() {
            while (pending == null && inner.hasNext()) {
                Entry<WeakKey, V> candidate = inner.next();
                Object key = candidate.getKey().get();
                if (key != null) {
                    pending = candidate;
                    pendingKey = key;
                }
            }
            return pending != null;
        }

        @Override
        public Entry<K, V> next() {
            if (!hasNext()) {
                throw new TNoSuchElementException();
            }
            Entry<WeakKey, V> entry = pending;
            K key = unmask(pendingKey);
            pending = null;
            pendingKey = null;
            return new Entry<K, V>() {
                @Override
                public K getKey() {
                    return key;
                }

                @Override
                public V getValue() {
                    return entry.getValue();
                }

                @Override
                public V setValue(V value) {
                    return entry.setValue(value);
                }

                @Override
                public boolean equals(Object obj) {
                    if (!(obj instanceof Entry)) {
                        return false;
                    }
                    Entry<?, ?> other = (Entry<?, ?>) obj;
                    return TObjects.equals(key, other.getKey()) && TObjects.equals(getValue(), other.getValue());
                }

                @Override
                public int hashCode() {
                    return TObjects.hashCode(key) ^ TObjects.hashCode(getValue());
                }

                @Override
                public String toString() {
                    return key + "=" + getValue();
                }
            };
        }

        @Override
        public void remove() {
            inner.remove();
        }
    }
}