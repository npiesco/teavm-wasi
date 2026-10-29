/*
 *  Copyright 2026 Spooler contributors.
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
package org.teavm.classlib.java.nio.file;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.NoSuchElementException;

public interface TPath extends Comparable<TPath>, Iterable<TPath> {
    static TPath of(String first, String... more) {
        return TPosixPath.of(first, more);
    }

    static TPath of(URI uri) {
        if (!"file".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("URI scheme is not \"file\"");
        }
        return TPosixPath.of(uri.getPath());
    }

    boolean isAbsolute();

    TPath getRoot();

    TPath getFileName();

    TPath getParent();

    int getNameCount();

    TPath getName(int index);

    TPath subpath(int beginIndex, int endIndex);

    boolean startsWith(TPath other);

    default boolean startsWith(String other) {
        return startsWith(of(other));
    }

    boolean endsWith(TPath other);

    default boolean endsWith(String other) {
        return endsWith(of(other));
    }

    TPath normalize();

    TPath resolve(TPath other);

    default TPath resolve(String other) {
        return resolve(of(other));
    }

    default TPath resolveSibling(TPath other) {
        TPath parent = getParent();
        return parent == null ? other : parent.resolve(other);
    }

    default TPath resolveSibling(String other) {
        return resolveSibling(of(other));
    }

    TPath relativize(TPath other);

    URI toUri();

    TPath toAbsolutePath();

    TPath toRealPath(TLinkOption... options) throws IOException;

    default File toFile() {
        return new File(toString());
    }

    @Override
    default Iterator<TPath> iterator() {
        return new Iterator<TPath>() {
            private int index;

            @Override
            public boolean hasNext() {
                return index < getNameCount();
            }

            @Override
            public TPath next() {
                if (index >= getNameCount()) {
                    throw new NoSuchElementException();
                }
                return getName(index++);
            }
        };
    }

    @Override
    int compareTo(TPath other);
}