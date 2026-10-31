/*
 *  Copyright 2017 Alexey Andreev.
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

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

public class TSpliterators {

    private TSpliterators() {
    }

    public static <T> TSpliterator<T> spliterator(Object[] array, int additionalCharacteristics) {
        return new ArraySpliterator<>(array, 0, array.length, additionalCharacteristics);
    }

    public static <T> TSpliterator<T> spliterator(Object[] array, int fromIndex, int toIndex,
            int additionalCharacteristics) {
        checkFromToBounds(array.length, fromIndex, toIndex);
        return new ArraySpliterator<>(array, fromIndex, toIndex, additionalCharacteristics);
    }

    public static <T> TSpliterator<T> spliterator(Collection<? extends T> c, int characteristics) {
        return spliterator(c.iterator(), c.size(), characteristics);
    }

    public static TSpliterator.OfInt spliterator(int[] array, int additionalCharacteristics) {
        return new IntArraySpliterator(array, 0, array.length, additionalCharacteristics);
    }

    public static TSpliterator.OfInt spliterator(int[] array, int fromIndex, int toIndex,
            int additionalCharacteristics) {
        checkFromToBounds(array.length, fromIndex, toIndex);
        return new IntArraySpliterator(array, fromIndex, toIndex, additionalCharacteristics);
    }

    public static TSpliterator.OfLong spliterator(long[] array, int additionalCharacteristics) {
        return new LongArraySpliterator(array, 0, array.length, additionalCharacteristics);
    }

    public static TSpliterator.OfLong spliterator(long[] array, int fromIndex, int toIndex,
            int additionalCharacteristics) {
        checkFromToBounds(array.length, fromIndex, toIndex);
        return new LongArraySpliterator(array, fromIndex, toIndex, additionalCharacteristics);
    }

    public static TSpliterator.OfDouble spliterator(double[] array, int additionalCharacteristics) {
        return new DoubleArraySpliterator(array, 0, array.length, additionalCharacteristics);
    }

    public static TSpliterator.OfDouble spliterator(double[] array, int fromIndex, int toIndex,
            int additionalCharacteristics) {
        checkFromToBounds(array.length, fromIndex, toIndex);
        return new DoubleArraySpliterator(array, fromIndex, toIndex, additionalCharacteristics);
    }

    private static void checkFromToBounds(int arrayLength, int origin, int fence) {
        if (origin > fence) {
            throw new ArrayIndexOutOfBoundsException("origin(" + origin + ") > fence(" + fence + ")");
        }
        if (origin < 0) {
            throw new ArrayIndexOutOfBoundsException(origin);
        }
        if (fence > arrayLength) {
            throw new ArrayIndexOutOfBoundsException(fence);
        }
    }

    public static <T> TSpliterator<T> spliterator(Iterator<? extends T> iterator, long size, int characteristics) {
        return new TSpliterator<T>() {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public TSpliterator<T> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return size;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static <T> TSpliterator<T> spliteratorUnknownSize(Iterator<? extends T> iterator, int characteristics) {
        return new TSpliterator<T>() {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public TSpliterator<T> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfInt spliterator(PrimitiveIterator.OfInt iterator, long size, int characteristics) {
        return new TSpliterator.OfInt() {
            @Override
            public boolean tryAdvance(IntConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfInt trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return size;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfInt spliteratorUnknownSize(PrimitiveIterator.OfInt iterator, int characteristics) {
        return new TSpliterator.OfInt() {
            @Override
            public boolean tryAdvance(IntConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfInt trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfLong spliterator(PrimitiveIterator.OfLong iterator, long size, int characteristics) {
        return new TSpliterator.OfLong() {
            @Override
            public boolean tryAdvance(LongConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfLong trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return size;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfLong spliteratorUnknownSize(PrimitiveIterator.OfLong iterator, int characteristics) {
        return new TSpliterator.OfLong() {
            @Override
            public boolean tryAdvance(LongConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfLong trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfDouble spliterator(PrimitiveIterator.OfDouble iterator, long size,
            int characteristics) {
        return new TSpliterator.OfDouble() {
            @Override
            public boolean tryAdvance(DoubleConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfDouble trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return size;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfDouble spliteratorUnknownSize(PrimitiveIterator.OfDouble iterator,
            int characteristics) {
        return new TSpliterator.OfDouble() {
            @Override
            public boolean tryAdvance(DoubleConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfDouble trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    // The JDK's array spliterators: SIZED and SUBSIZED, splitting the remaining range in half.
    static final class ArraySpliterator<T> implements TSpliterator<T> {
        private final Object[] array;
        private int index;
        private final int fence;
        private final int characteristics;
        // If >= 0, the size estimate of a split of an unsized spliterator; -1 when the size is fence - index.
        private long estimatedSize;

        ArraySpliterator(Object[] array, int origin, int fence, int additionalCharacteristics) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.characteristics = additionalCharacteristics | SIZED | SUBSIZED;
            this.estimatedSize = -1;
        }

        ArraySpliterator(Object[] array, int origin, int fence, int characteristics, long estimatedSize) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.characteristics = characteristics & ~(SIZED | SUBSIZED);
            this.estimatedSize = estimatedSize;
        }

        @Override
        public TSpliterator<T> trySplit() {
            int lo = index;
            int mid = (lo + fence) >>> 1;
            if (lo >= mid) {
                return null;
            }
            index = mid;
            if (estimatedSize == -1) {
                return new ArraySpliterator<>(array, lo, mid, characteristics);
            }
            long prefixEstimatedSize = estimatedSize >>> 1;
            estimatedSize -= prefixEstimatedSize;
            return new ArraySpliterator<>(array, lo, mid, characteristics, prefixEstimatedSize);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            int i = index;
            index = fence;
            for (; i < fence; ++i) {
                action.accept((T) array[i]);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            if (index >= 0 && index < fence) {
                action.accept((T) array[index++]);
                return true;
            }
            return false;
        }

        @Override
        public long estimateSize() {
            return estimatedSize >= 0 ? estimatedSize : fence - index;
        }

        @Override
        public int characteristics() {
            return characteristics;
        }

        @Override
        public Comparator<? super T> getComparator() {
            if (hasCharacteristics(SORTED)) {
                return null;
            }
            throw new IllegalStateException();
        }
    }

    static final class IntArraySpliterator implements TSpliterator.OfInt {
        private final int[] array;
        private int index;
        private final int fence;
        private final int characteristics;
        // If >= 0, the size estimate of a split of an unsized spliterator; -1 when the size is fence - index.
        private long estimatedSize;

        IntArraySpliterator(int[] array, int origin, int fence, int additionalCharacteristics) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.characteristics = additionalCharacteristics | SIZED | SUBSIZED;
            this.estimatedSize = -1;
        }

        IntArraySpliterator(int[] array, int origin, int fence, int characteristics, long estimatedSize) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.characteristics = characteristics & ~(SIZED | SUBSIZED);
            this.estimatedSize = estimatedSize;
        }

        @Override
        public TSpliterator.OfInt trySplit() {
            int lo = index;
            int mid = (lo + fence) >>> 1;
            if (lo >= mid) {
                return null;
            }
            index = mid;
            if (estimatedSize == -1) {
                return new IntArraySpliterator(array, lo, mid, characteristics);
            }
            long prefixEstimatedSize = estimatedSize >>> 1;
            estimatedSize -= prefixEstimatedSize;
            return new IntArraySpliterator(array, lo, mid, characteristics, prefixEstimatedSize);
        }

        @Override
        public void forEachRemaining(IntConsumer action) {
            Objects.requireNonNull(action);
            int i = index;
            index = fence;
            for (; i < fence; ++i) {
                action.accept(array[i]);
            }
        }

        @Override
        public boolean tryAdvance(IntConsumer action) {
            Objects.requireNonNull(action);
            if (index >= 0 && index < fence) {
                action.accept(array[index++]);
                return true;
            }
            return false;
        }

        @Override
        public long estimateSize() {
            return estimatedSize >= 0 ? estimatedSize : fence - index;
        }

        @Override
        public int characteristics() {
            return characteristics;
        }

        @Override
        public Comparator<? super Integer> getComparator() {
            if (hasCharacteristics(SORTED)) {
                return null;
            }
            throw new IllegalStateException();
        }
    }

    static final class LongArraySpliterator implements TSpliterator.OfLong {
        private final long[] array;
        private int index;
        private final int fence;
        private final int characteristics;
        // If >= 0, the size estimate of a split of an unsized spliterator; -1 when the size is fence - index.
        private long estimatedSize;

        LongArraySpliterator(long[] array, int origin, int fence, int additionalCharacteristics) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.characteristics = additionalCharacteristics | SIZED | SUBSIZED;
            this.estimatedSize = -1;
        }

        LongArraySpliterator(long[] array, int origin, int fence, int characteristics, long estimatedSize) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.characteristics = characteristics & ~(SIZED | SUBSIZED);
            this.estimatedSize = estimatedSize;
        }

        @Override
        public TSpliterator.OfLong trySplit() {
            int lo = index;
            int mid = (lo + fence) >>> 1;
            if (lo >= mid) {
                return null;
            }
            index = mid;
            if (estimatedSize == -1) {
                return new LongArraySpliterator(array, lo, mid, characteristics);
            }
            long prefixEstimatedSize = estimatedSize >>> 1;
            estimatedSize -= prefixEstimatedSize;
            return new LongArraySpliterator(array, lo, mid, characteristics, prefixEstimatedSize);
        }

        @Override
        public void forEachRemaining(LongConsumer action) {
            Objects.requireNonNull(action);
            int i = index;
            index = fence;
            for (; i < fence; ++i) {
                action.accept(array[i]);
            }
        }

        @Override
        public boolean tryAdvance(LongConsumer action) {
            Objects.requireNonNull(action);
            if (index >= 0 && index < fence) {
                action.accept(array[index++]);
                return true;
            }
            return false;
        }

        @Override
        public long estimateSize() {
            return estimatedSize >= 0 ? estimatedSize : fence - index;
        }

        @Override
        public int characteristics() {
            return characteristics;
        }

        @Override
        public Comparator<? super Long> getComparator() {
            if (hasCharacteristics(SORTED)) {
                return null;
            }
            throw new IllegalStateException();
        }
    }

    static final class DoubleArraySpliterator implements TSpliterator.OfDouble {
        private final double[] array;
        private int index;
        private final int fence;
        private final int characteristics;
        // If >= 0, the size estimate of a split of an unsized spliterator; -1 when the size is fence - index.
        private long estimatedSize;

        DoubleArraySpliterator(double[] array, int origin, int fence, int additionalCharacteristics) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.characteristics = additionalCharacteristics | SIZED | SUBSIZED;
            this.estimatedSize = -1;
        }

        DoubleArraySpliterator(double[] array, int origin, int fence, int characteristics, long estimatedSize) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.characteristics = characteristics & ~(SIZED | SUBSIZED);
            this.estimatedSize = estimatedSize;
        }

        @Override
        public TSpliterator.OfDouble trySplit() {
            int lo = index;
            int mid = (lo + fence) >>> 1;
            if (lo >= mid) {
                return null;
            }
            index = mid;
            if (estimatedSize == -1) {
                return new DoubleArraySpliterator(array, lo, mid, characteristics);
            }
            long prefixEstimatedSize = estimatedSize >>> 1;
            estimatedSize -= prefixEstimatedSize;
            return new DoubleArraySpliterator(array, lo, mid, characteristics, prefixEstimatedSize);
        }

        @Override
        public void forEachRemaining(DoubleConsumer action) {
            Objects.requireNonNull(action);
            int i = index;
            index = fence;
            for (; i < fence; ++i) {
                action.accept(array[i]);
            }
        }

        @Override
        public boolean tryAdvance(DoubleConsumer action) {
            Objects.requireNonNull(action);
            if (index >= 0 && index < fence) {
                action.accept(array[index++]);
                return true;
            }
            return false;
        }

        @Override
        public long estimateSize() {
            return estimatedSize >= 0 ? estimatedSize : fence - index;
        }

        @Override
        public int characteristics() {
            return characteristics;
        }

        @Override
        public Comparator<? super Double> getComparator() {
            if (hasCharacteristics(SORTED)) {
                return null;
            }
            throw new IllegalStateException();
        }
    }

    static final int BATCH_UNIT = 1 << 10;
    static final int MAX_BATCH = 1 << 25;

    // The JDK's abstract spliterators: a split takes the next batch (growing by BATCH_UNIT per split, capped by the
    // size estimate and MAX_BATCH) into an array spliterator.
    public abstract static class AbstractSpliterator<T> implements TSpliterator<T> {
        private final int characteristics;
        private long est;
        private int batch;

        protected AbstractSpliterator(long est, int additionalCharacteristics) {
            this.est = est;
            this.characteristics = (additionalCharacteristics & SIZED) != 0
                    ? additionalCharacteristics | SUBSIZED
                    : additionalCharacteristics;
        }

        static final class HoldingConsumer<T> implements Consumer<T> {
            Object value;

            @Override
            public void accept(T value) {
                this.value = value;
            }
        }

        @Override
        public TSpliterator<T> trySplit() {
            HoldingConsumer<T> holder = new HoldingConsumer<>();
            long s = est;
            if (s > 1 && tryAdvance(holder)) {
                int n = batch + BATCH_UNIT;
                if (n > s) {
                    n = (int) s;
                }
                if (n > MAX_BATCH) {
                    n = MAX_BATCH;
                }
                Object[] a = new Object[n];
                int j = 0;
                do {
                    a[j] = holder.value;
                    ++j;
                } while (j < n && tryAdvance(holder));
                batch = j;
                if (est != Long.MAX_VALUE) {
                    est -= j;
                    return new ArraySpliterator<>(a, 0, j, characteristics());
                }
                return new ArraySpliterator<>(a, 0, j, characteristics(), Long.MAX_VALUE / 2);
            }
            return null;
        }

        @Override
        public long estimateSize() {
            return est;
        }

        @Override
        public int characteristics() {
            return characteristics;
        }
    }

    public abstract static class AbstractIntSpliterator implements TSpliterator.OfInt {
        private final int characteristics;
        private long est;
        private int batch;

        protected AbstractIntSpliterator(long est, int additionalCharacteristics) {
            this.est = est;
            this.characteristics = (additionalCharacteristics & SIZED) != 0
                    ? additionalCharacteristics | SUBSIZED
                    : additionalCharacteristics;
        }

        static final class HoldingIntConsumer implements IntConsumer {
            int value;

            @Override
            public void accept(int value) {
                this.value = value;
            }
        }

        @Override
        public TSpliterator.OfInt trySplit() {
            HoldingIntConsumer holder = new HoldingIntConsumer();
            long s = est;
            if (s > 1 && tryAdvance(holder)) {
                int n = batch + BATCH_UNIT;
                if (n > s) {
                    n = (int) s;
                }
                if (n > MAX_BATCH) {
                    n = MAX_BATCH;
                }
                int[] a = new int[n];
                int j = 0;
                do {
                    a[j] = holder.value;
                    ++j;
                } while (j < n && tryAdvance(holder));
                batch = j;
                if (est != Long.MAX_VALUE) {
                    est -= j;
                    return new IntArraySpliterator(a, 0, j, characteristics());
                }
                return new IntArraySpliterator(a, 0, j, characteristics(), Long.MAX_VALUE / 2);
            }
            return null;
        }

        @Override
        public long estimateSize() {
            return est;
        }

        @Override
        public int characteristics() {
            return characteristics;
        }
    }

    public abstract static class AbstractLongSpliterator implements TSpliterator.OfLong {
        private final int characteristics;
        private long est;
        private int batch;

        protected AbstractLongSpliterator(long est, int additionalCharacteristics) {
            this.est = est;
            this.characteristics = (additionalCharacteristics & SIZED) != 0
                    ? additionalCharacteristics | SUBSIZED
                    : additionalCharacteristics;
        }

        static final class HoldingLongConsumer implements LongConsumer {
            long value;

            @Override
            public void accept(long value) {
                this.value = value;
            }
        }

        @Override
        public TSpliterator.OfLong trySplit() {
            HoldingLongConsumer holder = new HoldingLongConsumer();
            long s = est;
            if (s > 1 && tryAdvance(holder)) {
                int n = batch + BATCH_UNIT;
                if (n > s) {
                    n = (int) s;
                }
                if (n > MAX_BATCH) {
                    n = MAX_BATCH;
                }
                long[] a = new long[n];
                int j = 0;
                do {
                    a[j] = holder.value;
                    ++j;
                } while (j < n && tryAdvance(holder));
                batch = j;
                if (est != Long.MAX_VALUE) {
                    est -= j;
                    return new LongArraySpliterator(a, 0, j, characteristics());
                }
                return new LongArraySpliterator(a, 0, j, characteristics(), Long.MAX_VALUE / 2);
            }
            return null;
        }

        @Override
        public long estimateSize() {
            return est;
        }

        @Override
        public int characteristics() {
            return characteristics;
        }
    }

    public abstract static class AbstractDoubleSpliterator implements TSpliterator.OfDouble {
        private final int characteristics;
        private long est;
        private int batch;

        protected AbstractDoubleSpliterator(long est, int additionalCharacteristics) {
            this.est = est;
            this.characteristics = (additionalCharacteristics & SIZED) != 0
                    ? additionalCharacteristics | SUBSIZED
                    : additionalCharacteristics;
        }

        static final class HoldingDoubleConsumer implements DoubleConsumer {
            double value;

            @Override
            public void accept(double value) {
                this.value = value;
            }
        }

        @Override
        public TSpliterator.OfDouble trySplit() {
            HoldingDoubleConsumer holder = new HoldingDoubleConsumer();
            long s = est;
            if (s > 1 && tryAdvance(holder)) {
                int n = batch + BATCH_UNIT;
                if (n > s) {
                    n = (int) s;
                }
                if (n > MAX_BATCH) {
                    n = MAX_BATCH;
                }
                double[] a = new double[n];
                int j = 0;
                do {
                    a[j] = holder.value;
                    ++j;
                } while (j < n && tryAdvance(holder));
                batch = j;
                if (est != Long.MAX_VALUE) {
                    est -= j;
                    return new DoubleArraySpliterator(a, 0, j, characteristics());
                }
                return new DoubleArraySpliterator(a, 0, j, characteristics(), Long.MAX_VALUE / 2);
            }
            return null;
        }

        @Override
        public long estimateSize() {
            return est;
        }

        @Override
        public int characteristics() {
            return characteristics;
        }
    }
}
