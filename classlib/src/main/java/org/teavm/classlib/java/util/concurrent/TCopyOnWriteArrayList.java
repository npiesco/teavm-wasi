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
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TCloneable;
import org.teavm.classlib.java.util.TAbstractList;
import org.teavm.classlib.java.util.TArrayList;
import org.teavm.classlib.java.util.TCollection;
import org.teavm.classlib.java.util.TComparator;
import org.teavm.classlib.java.util.TIterator;
import org.teavm.classlib.java.util.TList;
import org.teavm.classlib.java.util.TListIterator;
import org.teavm.classlib.java.util.TRandomAccess;
import org.teavm.classlib.java.util.function.TUnaryOperator;

/**
 * {@code java.util.concurrent.CopyOnWriteArrayList}: every mutation installs a fresh array, so iterators traverse the
 * array that was current when they were created and never throw {@code ConcurrentModificationException}.
 */
public class TCopyOnWriteArrayList<E> implements TList<E>, TRandomAccess, TCloneable, TSerializable {
    private Object[] array;

    public TCopyOnWriteArrayList() {
        array = new Object[0];
    }

    public TCopyOnWriteArrayList(TCollection<? extends E> c) {
        array = c.getClass() == TCopyOnWriteArrayList.class
                ? ((TCopyOnWriteArrayList<?>) c).array
                : objectArray(c.toArray());
    }

    public TCopyOnWriteArrayList(E[] toCopyIn) {
        array = objectArray(toCopyIn);
    }

    private static Object[] objectArray(Object[] source) {
        Object[] result = new Object[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private static String outOfBounds(int index, int size) {
        return "Index: " + index + ", Size: " + size;
    }

    @SuppressWarnings("unchecked")
    static <E> E elementAt(Object[] a, int index) {
        return (E) a[index];
    }

    private static int indexOfRange(Object o, Object[] es, int from, int to) {
        for (int i = from; i < to; i++) {
            if (Objects.equals(o, es[i])) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOfRange(Object o, Object[] es, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            if (Objects.equals(o, es[i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int size() {
        return array.length;
    }

    @Override
    public boolean isEmpty() {
        return array.length == 0;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public int indexOf(Object o) {
        Object[] es = array;
        return indexOfRange(o, es, 0, es.length);
    }

    public int indexOf(E e, int index) {
        Object[] es = array;
        return indexOfRange(e, es, index, es.length);
    }

    @Override
    public int lastIndexOf(Object o) {
        Object[] es = array;
        return lastIndexOfRange(o, es, 0, es.length);
    }

    public int lastIndexOf(E e, int index) {
        return lastIndexOfRange(e, array, 0, index + 1);
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    @Override
    public Object[] toArray() {
        return array.clone();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        Object[] es = array;
        int len = es.length;
        if (a.length < len) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), len);
        }
        System.arraycopy(es, 0, a, 0, len);
        if (a.length > len) {
            a[len] = null;
        }
        return a;
    }

    @Override
    public E get(int index) {
        return elementAt(array, index);
    }

    @Override
    public E set(int index, E element) {
        Object[] es = array;
        E oldValue = elementAt(es, index);
        if (oldValue != element) {
            es = es.clone();
            es[index] = element;
        }
        array = es;
        return oldValue;
    }

    @Override
    public boolean add(E e) {
        Object[] es = array;
        int len = es.length;
        es = Arrays.copyOf(es, len + 1);
        es[len] = e;
        array = es;
        return true;
    }

    @Override
    public void add(int index, E element) {
        Object[] es = array;
        int len = es.length;
        if (index > len || index < 0) {
            throw new IndexOutOfBoundsException(outOfBounds(index, len));
        }
        Object[] newElements = new Object[len + 1];
        System.arraycopy(es, 0, newElements, 0, index);
        System.arraycopy(es, index, newElements, index + 1, len - index);
        newElements[index] = element;
        array = newElements;
    }

    @Override
    public E remove(int index) {
        Object[] es = array;
        int len = es.length;
        E oldValue = elementAt(es, index);
        Object[] newElements = new Object[len - 1];
        System.arraycopy(es, 0, newElements, 0, index);
        System.arraycopy(es, index + 1, newElements, index, len - index - 1);
        array = newElements;
        return oldValue;
    }

    @Override
    public boolean remove(Object o) {
        int index = indexOf(o);
        if (index < 0) {
            return false;
        }
        remove(index);
        return true;
    }

    void removeRange(int fromIndex, int toIndex) {
        Object[] es = array;
        int len = es.length;
        if (fromIndex < 0 || toIndex > len || toIndex < fromIndex) {
            throw new IndexOutOfBoundsException();
        }
        Object[] newElements = new Object[len - (toIndex - fromIndex)];
        System.arraycopy(es, 0, newElements, 0, fromIndex);
        System.arraycopy(es, toIndex, newElements, fromIndex, len - toIndex);
        array = newElements;
    }

    public boolean addIfAbsent(E e) {
        Object[] es = array;
        if (indexOfRange(e, es, 0, es.length) >= 0) {
            return false;
        }
        return add(e);
    }

    @Override
    public boolean containsAll(TCollection<?> c) {
        Object[] es = array;
        for (TIterator<?> it = c.iterator(); it.hasNext();) {
            if (indexOfRange(it.next(), es, 0, es.length) < 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean removeAll(TCollection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(c::contains);
    }

    @Override
    public boolean retainAll(TCollection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> !c.contains(e));
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        return bulkRemove(filter);
    }

    private boolean bulkRemove(Predicate<? super E> filter) {
        Object[] es = array;
        Object[] kept = new Object[es.length];
        int n = 0;
        for (Object e : es) {
            @SuppressWarnings("unchecked")
            E element = (E) e;
            if (!filter.test(element)) {
                kept[n++] = e;
            }
        }
        if (n == es.length) {
            return false;
        }
        array = Arrays.copyOf(kept, n);
        return true;
    }

    public int addAllAbsent(TCollection<? extends E> c) {
        Object[] cs = c.toArray();
        if (c.getClass() != TArrayList.class) {
            cs = cs.clone();
        }
        if (cs.length == 0) {
            return 0;
        }
        Object[] es = array;
        int len = es.length;
        int added = 0;
        for (int i = 0; i < cs.length; ++i) {
            Object e = cs[i];
            if (indexOfRange(e, es, 0, len) < 0 && indexOfRange(e, cs, 0, added) < 0) {
                cs[added++] = e;
            }
        }
        if (added > 0) {
            Object[] newElements = Arrays.copyOf(es, len + added);
            System.arraycopy(cs, 0, newElements, len, added);
            array = newElements;
        }
        return added;
    }

    @Override
    public void clear() {
        array = new Object[0];
    }

    @Override
    public boolean addAll(TCollection<? extends E> c) {
        Object[] cs = c.getClass() == TCopyOnWriteArrayList.class
                ? ((TCopyOnWriteArrayList<?>) c).array
                : c.toArray();
        if (cs.length == 0) {
            return false;
        }
        Object[] es = array;
        int len = es.length;
        Object[] newElements = Arrays.copyOf(es, len + cs.length);
        System.arraycopy(cs, 0, newElements, len, cs.length);
        array = newElements;
        return true;
    }

    @Override
    public boolean addAll(int index, TCollection<? extends E> c) {
        Object[] cs = c.toArray();
        Object[] es = array;
        int len = es.length;
        if (index > len || index < 0) {
            throw new IndexOutOfBoundsException(outOfBounds(index, len));
        }
        if (cs.length == 0) {
            return false;
        }
        Object[] newElements = new Object[len + cs.length];
        System.arraycopy(es, 0, newElements, 0, index);
        System.arraycopy(cs, 0, newElements, index, cs.length);
        System.arraycopy(es, index, newElements, index + cs.length, len - index);
        array = newElements;
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        for (Object x : array) {
            action.accept((E) x);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceAll(TUnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
        Object[] es = array.clone();
        for (int i = 0; i < es.length; i++) {
            es[i] = operator.apply((E) es[i]);
        }
        array = es;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void sort(TComparator<? super E> c) {
        Object[] es = array.clone();
        Arrays.sort(es, (java.util.Comparator<Object>) (Object) c);
        array = es;
    }

    @Override
    public String toString() {
        return Arrays.toString(array);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof TList)) {
            return false;
        }
        TIterator<?> it = ((TList<?>) o).iterator();
        for (Object element : array) {
            if (!it.hasNext() || !Objects.equals(element, it.next())) {
                return false;
            }
        }
        return !it.hasNext();
    }

    private static int hashCodeOfRange(Object[] es, int from, int to) {
        int hashCode = 1;
        for (int i = from; i < to; i++) {
            Object x = es[i];
            hashCode = 31 * hashCode + (x == null ? 0 : x.hashCode());
        }
        return hashCode;
    }

    @Override
    public int hashCode() {
        Object[] es = array;
        return hashCodeOfRange(es, 0, es.length);
    }

    @Override
    public TIterator<E> iterator() {
        return new COWIterator<>(array, 0);
    }

    @Override
    public TListIterator<E> listIterator() {
        return new COWIterator<>(array, 0);
    }

    @Override
    public TListIterator<E> listIterator(int index) {
        Object[] es = array;
        int len = es.length;
        if (index < 0 || index > len) {
            throw new IndexOutOfBoundsException(outOfBounds(index, len));
        }
        return new COWIterator<>(es, index);
    }

    static final class COWIterator<E> implements TListIterator<E> {
        private final Object[] snapshot;
        private final int start;
        private final int end;
        private int cursor;

        COWIterator(Object[] snapshot, int initialCursor) {
            this(snapshot, 0, snapshot.length, initialCursor);
        }

        COWIterator(Object[] snapshot, int start, int end, int initialCursor) {
            this.snapshot = snapshot;
            this.start = start;
            this.end = end;
            cursor = start + initialCursor;
        }

        @Override
        public boolean hasNext() {
            return cursor < end;
        }

        @Override
        public boolean hasPrevious() {
            return cursor > start;
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return elementAt(snapshot, cursor++);
        }

        @Override
        public E previous() {
            if (!hasPrevious()) {
                throw new NoSuchElementException();
            }
            return elementAt(snapshot, --cursor);
        }

        @Override
        public int nextIndex() {
            return cursor - start;
        }

        @Override
        public int previousIndex() {
            return cursor - start - 1;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(E e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(E e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            while (cursor < end) {
                action.accept(elementAt(snapshot, cursor++));
            }
        }
    }

    @Override
    public TList<E> subList(int fromIndex, int toIndex) {
        Object[] es = array;
        int len = es.length;
        if (fromIndex < 0 || toIndex > len || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException();
        }
        return new COWSubList(es, fromIndex, toIndex);
    }

    /**
     * A view of a range that is valid while the list still holds the array it last saw; any other change to the
     * list makes it throw {@code ConcurrentModificationException}.
     */
    private class COWSubList extends TAbstractList<E> implements TRandomAccess {
        private final int offset;
        private int size;
        private Object[] expectedArray;

        COWSubList(Object[] es, int offset, int toIndex) {
            expectedArray = es;
            this.offset = offset;
            size = toIndex - offset;
        }

        private void checkForComodification() {
            if (array != expectedArray) {
                throw new ConcurrentModificationException();
            }
        }

        private void rangeCheck(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(outOfBounds(index, size));
            }
        }

        private void rangeCheckForAdd(int index) {
            if (index < 0 || index > size) {
                throw new IndexOutOfBoundsException(outOfBounds(index, size));
            }
        }

        @Override
        public E set(int index, E element) {
            rangeCheck(index);
            checkForComodification();
            E x = TCopyOnWriteArrayList.this.set(offset + index, element);
            expectedArray = array;
            return x;
        }

        @Override
        public E get(int index) {
            rangeCheck(index);
            checkForComodification();
            return elementAt(array, offset + index);
        }

        @Override
        public int size() {
            checkForComodification();
            return size;
        }

        @Override
        public boolean add(E element) {
            checkForComodification();
            TCopyOnWriteArrayList.this.add(offset + size, element);
            expectedArray = array;
            size++;
            return true;
        }

        @Override
        public void add(int index, E element) {
            checkForComodification();
            rangeCheckForAdd(index);
            TCopyOnWriteArrayList.this.add(offset + index, element);
            expectedArray = array;
            size++;
        }

        @Override
        public void clear() {
            checkForComodification();
            TCopyOnWriteArrayList.this.removeRange(offset, offset + size);
            expectedArray = array;
            size = 0;
        }

        @Override
        public E remove(int index) {
            rangeCheck(index);
            checkForComodification();
            E result = TCopyOnWriteArrayList.this.remove(offset + index);
            expectedArray = array;
            size--;
            return result;
        }

        @Override
        public TIterator<E> iterator() {
            return listIterator(0);
        }

        @Override
        public TListIterator<E> listIterator(int index) {
            checkForComodification();
            rangeCheckForAdd(index);
            return new COWIterator<>(expectedArray, offset, offset + size, index);
        }

        @Override
        public TList<E> subList(int fromIndex, int toIndex) {
            checkForComodification();
            if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) {
                throw new IndexOutOfBoundsException();
            }
            return new COWSubList(expectedArray, offset + fromIndex, offset + toIndex);
        }

        @Override
        public int hashCode() {
            checkForComodification();
            return hashCodeOfRange(expectedArray, offset, offset + size);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof TList)) {
                return false;
            }
            TIterator<?> it = ((TList<?>) o).iterator();
            checkForComodification();
            Object[] es = expectedArray;
            int end = offset + size;
            for (int i = offset; i < end; i++) {
                if (!it.hasNext() || !Objects.equals(es[i], it.next())) {
                    return false;
                }
            }
            return !it.hasNext();
        }
    }
}