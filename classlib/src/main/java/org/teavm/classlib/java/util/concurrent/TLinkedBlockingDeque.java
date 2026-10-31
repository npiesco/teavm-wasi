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
import java.util.NoSuchElementException;
import java.util.Objects;
import org.teavm.classlib.impl.concurrent.ChangeWaiter;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.util.TAbstractQueue;
import org.teavm.classlib.java.util.TCollection;
import org.teavm.classlib.java.util.TIterator;

/**
 * {@code java.util.concurrent.LinkedBlockingDeque}: an optionally bounded deque of linked nodes. Unlinked nodes keep
 * their links (a node removed at an end links to itself), so iterators stay weakly consistent as in the JDK.
 */
public class TLinkedBlockingDeque<E> extends TAbstractQueue<E> implements TBlockingDeque<E>, TSerializable {
    static final class Node<E> {
        E item;
        Node<E> prev;
        Node<E> next;

        Node(E item) {
            this.item = item;
        }
    }

    private Node<E> first;
    private Node<E> last;
    private int count;
    private final int capacity;
    private final ChangeWaiter waiter = new ChangeWaiter();

    public TLinkedBlockingDeque() {
        this(Integer.MAX_VALUE);
    }

    public TLinkedBlockingDeque(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
    }

    public TLinkedBlockingDeque(TCollection<? extends E> c) {
        this(Integer.MAX_VALUE);
        addAll(c);
    }

    private boolean linkFirst(Node<E> node) {
        if (count >= capacity) {
            return false;
        }
        Node<E> f = first;
        node.next = f;
        first = node;
        if (last == null) {
            last = node;
        } else {
            f.prev = node;
        }
        ++count;
        waiter.signal();
        return true;
    }

    private boolean linkLast(Node<E> node) {
        if (count >= capacity) {
            return false;
        }
        Node<E> l = last;
        node.prev = l;
        last = node;
        if (first == null) {
            first = node;
        } else {
            l.next = node;
        }
        ++count;
        waiter.signal();
        return true;
    }

    private E unlinkFirst() {
        Node<E> f = first;
        if (f == null) {
            return null;
        }
        Node<E> n = f.next;
        E item = f.item;
        f.item = null;
        f.next = f;
        first = n;
        if (n == null) {
            last = null;
        } else {
            n.prev = null;
        }
        --count;
        waiter.signal();
        return item;
    }

    private E unlinkLast() {
        Node<E> l = last;
        if (l == null) {
            return null;
        }
        Node<E> p = l.prev;
        E item = l.item;
        l.item = null;
        l.prev = l;
        last = p;
        if (p == null) {
            first = null;
        } else {
            p.next = null;
        }
        --count;
        waiter.signal();
        return item;
    }

    void unlink(Node<E> x) {
        Node<E> p = x.prev;
        Node<E> n = x.next;
        if (p == null) {
            unlinkFirst();
        } else if (n == null) {
            unlinkLast();
        } else {
            p.next = n;
            n.prev = p;
            x.item = null;
            --count;
            waiter.signal();
        }
    }

    private static long deadline(long timeout, TTimeUnit unit) {
        long millis = unit.toMillis(timeout);
        return millis <= 0 ? -1 : System.currentTimeMillis() + millis;
    }

    @Override
    public void addFirst(E e) {
        if (!offerFirst(e)) {
            throw new IllegalStateException("Deque full");
        }
    }

    @Override
    public void addLast(E e) {
        if (!offerLast(e)) {
            throw new IllegalStateException("Deque full");
        }
    }

    @Override
    public boolean offerFirst(E e) {
        Objects.requireNonNull(e);
        return linkFirst(new Node<>(e));
    }

    @Override
    public boolean offerLast(E e) {
        Objects.requireNonNull(e);
        return linkLast(new Node<>(e));
    }

    @Override
    public void putFirst(E e) throws InterruptedException {
        Objects.requireNonNull(e);
        Node<E> node = new Node<>(e);
        while (!linkFirst(node)) {
            waiter.await(0);
        }
    }

    @Override
    public void putLast(E e) throws InterruptedException {
        Objects.requireNonNull(e);
        Node<E> node = new Node<>(e);
        while (!linkLast(node)) {
            waiter.await(0);
        }
    }

    @Override
    public boolean offerFirst(E e, long timeout, TTimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(e);
        Node<E> node = new Node<>(e);
        long deadline = deadline(timeout, unit);
        while (!linkFirst(node)) {
            if (deadline < 0) {
                return false;
            }
            if (!waiter.await(deadline)) {
                deadline = -1;
            }
        }
        return true;
    }

    @Override
    public boolean offerLast(E e, long timeout, TTimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(e);
        Node<E> node = new Node<>(e);
        long deadline = deadline(timeout, unit);
        while (!linkLast(node)) {
            if (deadline < 0) {
                return false;
            }
            if (!waiter.await(deadline)) {
                deadline = -1;
            }
        }
        return true;
    }

    @Override
    public E removeFirst() {
        E x = pollFirst();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    @Override
    public E removeLast() {
        E x = pollLast();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    @Override
    public E pollFirst() {
        return unlinkFirst();
    }

    @Override
    public E pollLast() {
        return unlinkLast();
    }

    @Override
    public E takeFirst() throws InterruptedException {
        E x;
        while ((x = unlinkFirst()) == null) {
            waiter.await(0);
        }
        return x;
    }

    @Override
    public E takeLast() throws InterruptedException {
        E x;
        while ((x = unlinkLast()) == null) {
            waiter.await(0);
        }
        return x;
    }

    @Override
    public E pollFirst(long timeout, TTimeUnit unit) throws InterruptedException {
        long deadline = deadline(timeout, unit);
        E x;
        while ((x = unlinkFirst()) == null) {
            if (deadline < 0) {
                return null;
            }
            if (!waiter.await(deadline)) {
                deadline = -1;
            }
        }
        return x;
    }

    @Override
    public E pollLast(long timeout, TTimeUnit unit) throws InterruptedException {
        long deadline = deadline(timeout, unit);
        E x;
        while ((x = unlinkLast()) == null) {
            if (deadline < 0) {
                return null;
            }
            if (!waiter.await(deadline)) {
                deadline = -1;
            }
        }
        return x;
    }

    @Override
    public E getFirst() {
        E x = peekFirst();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    @Override
    public E getLast() {
        E x = peekLast();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    @Override
    public E peekFirst() {
        return first == null ? null : first.item;
    }

    @Override
    public E peekLast() {
        return last == null ? null : last.item;
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        if (o == null) {
            return false;
        }
        for (Node<E> p = first; p != null; p = p.next) {
            if (o.equals(p.item)) {
                unlink(p);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        if (o == null) {
            return false;
        }
        for (Node<E> p = last; p != null; p = p.prev) {
            if (o.equals(p.item)) {
                unlink(p);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean add(E e) {
        addLast(e);
        return true;
    }

    @Override
    public boolean offer(E e) {
        return offerLast(e);
    }

    @Override
    public void put(E e) throws InterruptedException {
        putLast(e);
    }

    @Override
    public boolean offer(E e, long timeout, TTimeUnit unit) throws InterruptedException {
        return offerLast(e, timeout, unit);
    }

    @Override
    public E remove() {
        return removeFirst();
    }

    @Override
    public E poll() {
        return pollFirst();
    }

    @Override
    public E take() throws InterruptedException {
        return takeFirst();
    }

    @Override
    public E poll(long timeout, TTimeUnit unit) throws InterruptedException {
        return pollFirst(timeout, unit);
    }

    @Override
    public E element() {
        return getFirst();
    }

    @Override
    public E peek() {
        return peekFirst();
    }

    @Override
    public int remainingCapacity() {
        return capacity - count;
    }

    @Override
    public int drainTo(TCollection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    @Override
    public int drainTo(TCollection<? super E> c, int maxElements) {
        Objects.requireNonNull(c);
        if (c == this) {
            throw new IllegalArgumentException();
        }
        if (maxElements <= 0) {
            return 0;
        }
        int n = Math.min(maxElements, count);
        for (int i = 0; i < n; i++) {
            c.add(first.item);
            unlinkFirst();
        }
        return n;
    }

    @Override
    public void push(E e) {
        addFirst(e);
    }

    @Override
    public E pop() {
        return removeFirst();
    }

    @Override
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    public boolean contains(Object o) {
        if (o == null) {
            return false;
        }
        for (Node<E> p = first; p != null; p = p.next) {
            if (o.equals(p.item)) {
                return true;
            }
        }
        return false;
    }

    // All elements are linked when they fit; otherwise elements are added one at a time until the deque is full.
    @Override
    public boolean addAll(TCollection<? extends E> c) {
        if (c == this) {
            throw new IllegalArgumentException();
        }
        Node<E> beg = null;
        Node<E> end = null;
        int n = 0;
        for (TIterator<? extends E> it = c.iterator(); it.hasNext();) {
            E e = it.next();
            Objects.requireNonNull(e);
            n++;
            Node<E> node = new Node<>(e);
            if (beg == null) {
                beg = node;
            } else {
                end.next = node;
                node.prev = end;
            }
            end = node;
        }
        if (beg == null) {
            return false;
        }
        if (count + n <= capacity) {
            beg.prev = last;
            if (first == null) {
                first = beg;
            } else {
                last.next = beg;
            }
            last = end;
            count += n;
            waiter.signal();
            return true;
        }
        return super.addAll(c);
    }

    @Override
    public Object[] toArray() {
        Object[] a = new Object[count];
        int k = 0;
        for (Node<E> p = first; p != null; p = p.next) {
            a[k++] = p.item;
        }
        return a;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < count) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), count);
        }
        int k = 0;
        for (Node<E> p = first; p != null; p = p.next) {
            a[k++] = (T) p.item;
        }
        if (a.length > k) {
            a[k] = null;
        }
        return a;
    }

    @Override
    public void clear() {
        for (Node<E> f = first; f != null;) {
            f.item = null;
            Node<E> n = f.next;
            f.prev = null;
            f.next = null;
            f = n;
        }
        first = null;
        last = null;
        count = 0;
        waiter.signal();
    }

    @Override
    public TIterator<E> iterator() {
        return new Itr(false);
    }

    @Override
    public TIterator<E> descendingIterator() {
        return new Itr(true);
    }

    private class Itr implements TIterator<E> {
        private final boolean descending;
        private Node<E> next;
        private E nextItem;
        private Node<E> lastRet;

        Itr(boolean descending) {
            this.descending = descending;
            next = firstNode();
            if (next != null) {
                nextItem = next.item;
            }
        }

        private Node<E> firstNode() {
            return descending ? last : first;
        }

        private Node<E> nextNode(Node<E> n) {
            return descending ? n.prev : n.next;
        }

        // A node that links to itself was unlinked at an end, so the walk restarts from that end.
        private Node<E> succ(Node<E> p) {
            Node<E> s = nextNode(p);
            return s == p ? firstNode() : s;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public E next() {
            Node<E> p = next;
            if (p == null) {
                throw new NoSuchElementException();
            }
            lastRet = p;
            E x = nextItem;
            E e = null;
            for (p = nextNode(p); p != null && (e = p.item) == null;) {
                p = succ(p);
            }
            next = p;
            nextItem = e;
            return x;
        }

        @Override
        public void remove() {
            Node<E> n = lastRet;
            if (n == null) {
                throw new IllegalStateException();
            }
            lastRet = null;
            if (n.item != null) {
                unlink(n);
            }
        }
    }
}