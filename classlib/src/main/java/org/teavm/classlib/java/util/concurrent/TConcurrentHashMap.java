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

import org.teavm.classlib.java.util.THashMap;
import org.teavm.classlib.java.util.TMap;

/**
 * Emulation of {@code java.util.concurrent.ConcurrentHashMap}. TeaVM targets a
 * single-threaded runtime, so the concurrency guarantees of ConcurrentHashMap
 * are behaviorally moot; a plain {@link THashMap} subclass is a
 * semantics-preserving emulation of the (single-threaded) observable behavior.
 */
public class TConcurrentHashMap<K, V> extends THashMap<K, V> implements TConcurrentMap<K, V> {
    public TConcurrentHashMap() {
        super();
    }

    public TConcurrentHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    public TConcurrentHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public TConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        super(initialCapacity, loadFactor);
        if (concurrencyLevel <= 0) {
            throw new IllegalArgumentException("concurrencyLevel must be positive");
        }
    }

    public TConcurrentHashMap(TMap<? extends K, ? extends V> m) {
        super(m);
    }
}
