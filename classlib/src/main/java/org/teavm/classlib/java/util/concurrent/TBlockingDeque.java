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

import org.teavm.classlib.java.util.TDeque;
import org.teavm.classlib.java.util.TIterator;

public interface TBlockingDeque<E> extends TBlockingQueue<E>, TDeque<E> {
    @Override
    void addFirst(E e);

    @Override
    void addLast(E e);

    @Override
    boolean offerFirst(E e);

    @Override
    boolean offerLast(E e);

    void putFirst(E e) throws InterruptedException;

    void putLast(E e) throws InterruptedException;

    boolean offerFirst(E e, long timeout, TTimeUnit unit) throws InterruptedException;

    boolean offerLast(E e, long timeout, TTimeUnit unit) throws InterruptedException;

    E takeFirst() throws InterruptedException;

    E takeLast() throws InterruptedException;

    E pollFirst(long timeout, TTimeUnit unit) throws InterruptedException;

    E pollLast(long timeout, TTimeUnit unit) throws InterruptedException;

    @Override
    boolean removeFirstOccurrence(Object o);

    @Override
    boolean removeLastOccurrence(Object o);

    @Override
    boolean add(E e);

    @Override
    boolean offer(E e);

    @Override
    void put(E e) throws InterruptedException;

    @Override
    boolean offer(E e, long timeout, TTimeUnit unit) throws InterruptedException;

    @Override
    E remove();

    @Override
    E poll();

    @Override
    E take() throws InterruptedException;

    @Override
    E poll(long timeout, TTimeUnit unit) throws InterruptedException;

    @Override
    E element();

    @Override
    E peek();

    @Override
    boolean remove(Object o);

    @Override
    boolean contains(Object o);

    @Override
    int size();

    @Override
    TIterator<E> iterator();

    @Override
    void push(E e);
}