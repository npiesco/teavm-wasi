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
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.teavm.classlib.PlatformDetector;

/**
 * The java.nio.file.Path implementation for the WASI target (it must live in the mapped
 * classlib tree so its TPath supertype is renamed to java.nio.file.Path).
 * A POSIX path: '/' separators, an optional single root "/",
 * redundant and trailing separators removed on construction.
 */
final class TPosixPath implements TPath {
    private final String path;
    private int[] offsets;

    private TPosixPath(String normalized) {
        this.path = normalized;
    }

    public static TPosixPath of(String first, String... more) {
        StringBuilder sb = new StringBuilder(first);
        for (String segment : more) {
            if (!segment.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(segment);
            }
        }
        return new TPosixPath(normalizeSeparators(sb.toString()));
    }

    private static String normalizeSeparators(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        char previous = 0;
        for (int i = 0; i < input.length(); ++i) {
            char c = input.charAt(i);
            if (c == '\u0000') {
                throw new TInvalidPathException(input, "Nul character not allowed");
            }
            if (c == '/' && previous == '/') {
                continue;
            }
            sb.append(c);
            previous = c;
        }
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == '/') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private int[] offsets() {
        if (offsets == null) {
            if (path.isEmpty()) {
                offsets = new int[] { 0 };
            } else {
                int count = 0;
                int i = 0;
                while (i < path.length()) {
                    if (path.charAt(i++) != '/') {
                        count++;
                        while (i < path.length() && path.charAt(i) != '/') {
                            i++;
                        }
                    }
                }
                int[] result = new int[count];
                count = 0;
                i = 0;
                while (i < path.length()) {
                    if (path.charAt(i) == '/') {
                        i++;
                    } else {
                        result[count++] = i++;
                        while (i < path.length() && path.charAt(i) != '/') {
                            i++;
                        }
                    }
                }
                offsets = result;
            }
        }
        return offsets;
    }

    private String nameAt(int index) {
        int[] o = offsets();
        int begin = o[index];
        int end = index == o.length - 1 ? path.length() : o[index + 1] - 1;
        return path.substring(begin, end);
    }

    private boolean isEmpty() {
        return path.isEmpty();
    }

    @Override
    public boolean isAbsolute() {
        return !path.isEmpty() && path.charAt(0) == '/';
    }

    @Override
    public TPath getRoot() {
        return isAbsolute() ? new TPosixPath("/") : null;
    }

    @Override
    public TPath getFileName() {
        int count = getNameCount();
        if (count == 0) {
            return null;
        }
        if (count == 1 && !isAbsolute()) {
            return this;
        }
        return new TPosixPath(nameAt(count - 1));
    }

    @Override
    public TPath getParent() {
        int count = getNameCount();
        if (count == 0) {
            return null;
        }
        int length = offsets()[count - 1] - 1;
        if (length <= 0) {
            return getRoot();
        }
        return new TPosixPath(path.substring(0, length));
    }

    @Override
    public int getNameCount() {
        return offsets().length;
    }

    @Override
    public TPath getName(int index) {
        if (index < 0 || index >= getNameCount()) {
            throw new IllegalArgumentException();
        }
        return new TPosixPath(nameAt(index));
    }

    @Override
    public TPath subpath(int beginIndex, int endIndex) {
        int count = getNameCount();
        if (beginIndex < 0 || beginIndex >= count || endIndex > count || beginIndex >= endIndex) {
            throw new IllegalArgumentException();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = beginIndex; i < endIndex; ++i) {
            if (i > beginIndex) {
                sb.append('/');
            }
            sb.append(nameAt(i));
        }
        return new TPosixPath(sb.toString());
    }

    private static TPosixPath check(TPath other) {
        if (other == null) {
            throw new NullPointerException();
        }
        if (!(other instanceof TPosixPath)) {
            throw new IllegalArgumentException("Path is not a WASI path: " + other);
        }
        return (TPosixPath) other;
    }

    @Override
    public boolean startsWith(TPath other) {
        TPosixPath that = check(other);
        if (that.isAbsolute() != isAbsolute()) {
            return false;
        }
        if (that.isEmpty()) {
            return isEmpty();
        }
        int count = that.getNameCount();
        if (count > getNameCount()) {
            return false;
        }
        for (int i = 0; i < count; ++i) {
            if (!nameAt(i).equals(that.nameAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean endsWith(TPath other) {
        TPosixPath that = check(other);
        if (that.isAbsolute()) {
            return equals(that);
        }
        if (that.isEmpty()) {
            return isEmpty();
        }
        int count = that.getNameCount();
        int own = getNameCount();
        if (count > own) {
            return false;
        }
        for (int i = 0; i < count; ++i) {
            if (!nameAt(own - count + i).equals(that.nameAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public TPath normalize() {
        int count = getNameCount();
        if (count == 0 || isEmpty()) {
            return this;
        }
        String[] kept = new String[count];
        int size = 0;
        for (int i = 0; i < count; ++i) {
            String name = nameAt(i);
            if (name.equals(".")) {
                continue;
            }
            if (name.equals("..")) {
                if (size > 0 && !kept[size - 1].equals("..")) {
                    size--;
                    continue;
                }
                if (isAbsolute()) {
                    continue;
                }
            }
            kept[size++] = name;
        }
        StringBuilder sb = new StringBuilder();
        if (isAbsolute()) {
            sb.append('/');
        }
        for (int i = 0; i < size; ++i) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(kept[i]);
        }
        String result = sb.toString();
        return result.equals(path) ? this : new TPosixPath(result);
    }

    @Override
    public TPath resolve(TPath other) {
        TPosixPath that = check(other);
        if (that.isAbsolute()) {
            return that;
        }
        if (that.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return that;
        }
        return new TPosixPath(path.equals("/") ? "/" + that.path : path + "/" + that.path);
    }

    @Override
    public TPath relativize(TPath other) {
        TPosixPath that = check(other);
        if (that.equals(this)) {
            return new TPosixPath("");
        }
        if (isAbsolute() != that.isAbsolute()) {
            throw new IllegalArgumentException("'other' is different type of Path");
        }
        if (isEmpty()) {
            return that;
        }
        int own = getNameCount();
        int count = that.getNameCount();
        int common = 0;
        while (common < own && common < count && nameAt(common).equals(that.nameAt(common))) {
            common++;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = common; i < own; ++i) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append("..");
        }
        for (int i = common; i < count; ++i) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(that.nameAt(i));
        }
        return new TPosixPath(sb.toString());
    }

    @Override
    public URI toUri() {
        String absolute = toAbsolutePath().toString();
        if (new File(absolute).isDirectory() && !absolute.endsWith("/")) {
            absolute = absolute + "/";
        }
        try {
            return new URI("file", null, absolute, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public TPath toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        return of(new File(path).getAbsolutePath());
    }

    // Symbolic links resolved per component, as the kernel's path walk does (path_resolution(7)).
    private static final int MAX_SYMLINKS = 40;

    @Override
    public TPath toRealPath(TLinkOption... options) throws IOException {
        if (!PlatformDetector.isWebAssembly()) {
            // The other targets' filesystems have no symbolic links.
            File file = new File(toAbsolutePath().toString());
            if (!file.exists()) {
                throw new TNoSuchFileException(path);
            }
            return of(file.getCanonicalPath());
        }
        boolean follow = true;
        for (TLinkOption option : options) {
            if (option == TLinkOption.NOFOLLOW_LINKS) {
                follow = false;
            }
        }
        Deque<String> pending = new ArrayDeque<>();
        TPosixPath absolute = (TPosixPath) toAbsolutePath();
        for (int i = 0; i < absolute.getNameCount(); ++i) {
            pending.addLast(absolute.nameAt(i));
        }
        List<String> resolved = new ArrayList<>();
        int links = 0;
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (name.equals(".")) {
                continue;
            }
            if (name.equals("..")) {
                if (!resolved.isEmpty()) {
                    resolved.remove(resolved.size() - 1);
                }
                continue;
            }
            resolved.add(name);
            TPath current = join(resolved);
            boolean last = pending.isEmpty();
            if ((follow || !last) && TFiles.isSymbolicLink(current)) {
                if (++links > MAX_SYMLINKS) {
                    throw new TFileSystemException(path, null, "Too many levels of symbolic links");
                }
                String target = TFiles.readSymbolicLink(current).toString();
                resolved.remove(resolved.size() - 1);
                TPosixPath targetPath = of(target);
                if (targetPath.isAbsolute()) {
                    resolved.clear();
                }
                for (int i = targetPath.getNameCount() - 1; i >= 0; --i) {
                    pending.addFirst(targetPath.nameAt(i));
                }
                continue;
            }
            if (!TFiles.exists(current, TLinkOption.NOFOLLOW_LINKS)) {
                throw new TNoSuchFileException(path);
            }
        }
        return join(resolved);
    }

    private static TPath join(List<String> names) {
        StringBuilder sb = new StringBuilder("/");
        for (int i = 0; i < names.size(); ++i) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(names.get(i));
        }
        return new TPosixPath(sb.toString());
    }

    @Override
    public int compareTo(TPath other) {
        return path.compareTo(check(other).path);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TPosixPath && ((TPosixPath) other).path.equals(path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return path;
    }
}