/*
 *  Copyright 2026 TeaVM Contributors.
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
package org.teavm.classlib.java.util.concurrent.atomic;

import java.io.Serializable;
import java.util.Arrays;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

public class TAtomicReferenceArray<E> implements Serializable {
    private final Object[] array;

    public TAtomicReferenceArray(int length) {
        array = new Object[length];
    }

    public TAtomicReferenceArray(E[] array) {
        this.array = array.clone();
    }

    public final int length() {
        return array.length;
    }

    @SuppressWarnings("unchecked")
    public final E get(int index) {
        return (E) array[index];
    }

    public final void set(int index, E newValue) {
        array[index] = newValue;
    }

    public final void lazySet(int index, E newValue) {
        array[index] = newValue;
    }

    @SuppressWarnings("unchecked")
    public final E getAndSet(int index, E newValue) {
        E result = (E) array[index];
        array[index] = newValue;
        return result;
    }

    public final boolean compareAndSet(int index, E expectedValue, E newValue) {
        if (array[index] != expectedValue) {
            return false;
        }
        array[index] = newValue;
        return true;
    }

    public final boolean weakCompareAndSet(int index, E expectedValue, E newValue) {
        return compareAndSet(index, expectedValue, newValue);
    }

    public final E getAndUpdate(int index, UnaryOperator<E> updateFunction) {
        E current = get(index);
        set(index, updateFunction.apply(current));
        return current;
    }

    public final E updateAndGet(int index, UnaryOperator<E> updateFunction) {
        E updated = updateFunction.apply(get(index));
        set(index, updated);
        return updated;
    }

    public final E getAndAccumulate(int index, E value, BinaryOperator<E> accumulatorFunction) {
        E current = get(index);
        set(index, accumulatorFunction.apply(current, value));
        return current;
    }

    public final E accumulateAndGet(int index, E value, BinaryOperator<E> accumulatorFunction) {
        E updated = accumulatorFunction.apply(get(index), value);
        set(index, updated);
        return updated;
    }

    public final E getPlain(int index) {
        return get(index);
    }

    public final void setPlain(int index, E newValue) {
        set(index, newValue);
    }

    public final E getOpaque(int index) {
        return get(index);
    }

    public final void setOpaque(int index, E newValue) {
        set(index, newValue);
    }

    public final E getAcquire(int index) {
        return get(index);
    }

    public final void setRelease(int index, E newValue) {
        set(index, newValue);
    }

    public final E compareAndExchange(int index, E expectedValue, E newValue) {
        E current = get(index);
        if (current == expectedValue) {
            set(index, newValue);
        }
        return current;
    }

    @Override
    public String toString() {
        return Arrays.toString(array);
    }
}
