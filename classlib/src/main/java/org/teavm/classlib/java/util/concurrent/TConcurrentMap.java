/*
 *  Copyright 2024 Spooler contributors.
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

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.teavm.classlib.java.util.TIterator;
import org.teavm.classlib.java.util.TMap;

/**
 * {@code java.util.concurrent.ConcurrentMap}: the atomic operations are abstract, and the defaults are written in
 * terms of them as the JDK specifies, so they retry instead of assuming a single writer.
 */
public interface TConcurrentMap<K, V> extends TMap<K, V> {
    @Override
    default V getOrDefault(K key, V defaultValue) {
        V v = get(key);
        return v != null ? v : defaultValue;
    }

    @Override
    default void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        for (TIterator<TMap.Entry<K, V>> it = entrySet().iterator(); it.hasNext();) {
            TMap.Entry<K, V> entry = it.next();
            K k;
            V v;
            try {
                k = entry.getKey();
                v = entry.getValue();
            } catch (IllegalStateException e) {
                continue;
            }
            action.accept(k, v);
        }
    }

    @Override
    V putIfAbsent(K key, V value);

    @Override
    boolean remove(Object key, Object value);

    @Override
    boolean replace(K key, V oldValue, V newValue);

    @Override
    V replace(K key, V value);

    @Override
    default void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        forEach((k, v) -> {
            V current = v;
            while (!replace(k, current, function.apply(k, current))) {
                current = get(k);
                if (current == null) {
                    break;
                }
            }
        });
    }

    @Override
    default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V oldValue = get(key);
        if (oldValue != null) {
            return oldValue;
        }
        V newValue = mappingFunction.apply(key);
        if (newValue == null) {
            return null;
        }
        oldValue = putIfAbsent(key, newValue);
        return oldValue == null ? newValue : oldValue;
    }

    @Override
    default V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        for (V oldValue = get(key); oldValue != null; oldValue = get(key)) {
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue == null ? remove(key, oldValue) : replace(key, oldValue, newValue)) {
                return newValue;
            }
        }
        return null;
    }

    @Override
    default V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue = get(key);
        while (true) {
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                if (oldValue != null) {
                    if (replace(key, oldValue, newValue)) {
                        return newValue;
                    }
                    oldValue = get(key);
                } else {
                    oldValue = putIfAbsent(key, newValue);
                    if (oldValue == null) {
                        return newValue;
                    }
                }
            } else if (oldValue == null || remove(key, oldValue)) {
                return null;
            } else {
                oldValue = get(key);
            }
        }
    }

    @Override
    default V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        Objects.requireNonNull(value);
        V oldValue = get(key);
        while (true) {
            if (oldValue != null) {
                V newValue = remappingFunction.apply(oldValue, value);
                if (newValue != null) {
                    if (replace(key, oldValue, newValue)) {
                        return newValue;
                    }
                } else if (remove(key, oldValue)) {
                    return null;
                }
                oldValue = get(key);
            } else {
                oldValue = putIfAbsent(key, value);
                if (oldValue == null) {
                    return value;
                }
            }
        }
    }
}