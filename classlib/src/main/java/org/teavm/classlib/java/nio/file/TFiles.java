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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.teavm.classlib.java.nio.file.attribute.TFileAttribute;

/**
 * java.nio.file.Files over the WASI filesystem that backs java.io.File on this target.
 */
public final class TFiles {
    private TFiles() {
    }

    private static File file(TPath path) {
        return new File(path.toString());
    }

    public static InputStream newInputStream(TPath path, TOpenOption... options) throws IOException {
        for (TOpenOption option : options) {
            if (option == TStandardOpenOption.APPEND || option == TStandardOpenOption.WRITE) {
                throw new IllegalArgumentException(option + " not allowed");
            }
        }
        File file = file(path);
        if (!file.exists()) {
            throw new TNoSuchFileException(path.toString());
        }
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new TFileSystemException(path.toString(), null, e.getMessage());
        }
    }

    public static OutputStream newOutputStream(TPath path, TOpenOption... options) throws IOException {
        boolean append = false;
        boolean truncate = false;
        boolean create = false;
        boolean createNew = false;
        if (options.length == 0) {
            create = true;
            truncate = true;
        }
        for (TOpenOption option : options) {
            if (option == TStandardOpenOption.APPEND) {
                append = true;
            } else if (option == TStandardOpenOption.TRUNCATE_EXISTING) {
                truncate = true;
            } else if (option == TStandardOpenOption.CREATE) {
                create = true;
            } else if (option == TStandardOpenOption.CREATE_NEW) {
                createNew = true;
            } else if (option == TStandardOpenOption.READ) {
                throw new IllegalArgumentException("READ not allowed");
            }
        }
        if (append && truncate) {
            throw new IllegalArgumentException("APPEND + TRUNCATE_EXISTING not allowed");
        }
        File file = file(path);
        boolean exists = file.exists();
        if (createNew && exists) {
            throw new TFileAlreadyExistsException(path.toString());
        }
        if (!exists && !create && !createNew) {
            throw new TNoSuchFileException(path.toString());
        }
        if (exists && file.isDirectory()) {
            throw new TFileSystemException(path.toString(), null, "Is a directory");
        }
        File parent = file.getAbsoluteFile().getParentFile();
        if (!exists && parent != null && !parent.isDirectory()) {
            throw new TNoSuchFileException(path.toString());
        }
        try {
            if (exists && !append && !truncate) {
                return new FileOutputStream(file, true);
            }
            return new FileOutputStream(file, append);
        } catch (FileNotFoundException e) {
            throw new TFileSystemException(path.toString(), null, e.getMessage());
        }
    }

    public static byte[] readAllBytes(TPath path) throws IOException {
        try (InputStream in = newInputStream(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    public static String readString(TPath path) throws IOException {
        return readString(path, StandardCharsets.UTF_8);
    }

    public static String readString(TPath path, Charset charset) throws IOException {
        return new String(readAllBytes(path), charset);
    }

    public static List<String> readAllLines(TPath path) throws IOException {
        return readAllLines(path, StandardCharsets.UTF_8);
    }

    public static List<String> readAllLines(TPath path, Charset charset) throws IOException {
        try (BufferedReader reader = newBufferedReader(path, charset)) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        }
    }

    public static BufferedReader newBufferedReader(TPath path) throws IOException {
        return newBufferedReader(path, StandardCharsets.UTF_8);
    }

    public static BufferedReader newBufferedReader(TPath path, Charset charset) throws IOException {
        return new BufferedReader(new InputStreamReader(newInputStream(path), charset));
    }

    public static BufferedWriter newBufferedWriter(TPath path, TOpenOption... options) throws IOException {
        return newBufferedWriter(path, StandardCharsets.UTF_8, options);
    }

    public static BufferedWriter newBufferedWriter(TPath path, Charset charset, TOpenOption... options)
            throws IOException {
        return new BufferedWriter(new OutputStreamWriter(newOutputStream(path, options), charset));
    }

    public static TPath write(TPath path, byte[] bytes, TOpenOption... options) throws IOException {
        try (OutputStream out = newOutputStream(path, options)) {
            out.write(bytes);
        }
        return path;
    }

    public static TPath write(TPath path, Iterable<? extends CharSequence> lines, Charset charset,
            TOpenOption... options) throws IOException {
        try (BufferedWriter writer = newBufferedWriter(path, charset, options)) {
            for (CharSequence line : lines) {
                writer.append(line);
                writer.newLine();
            }
        }
        return path;
    }

    public static TPath write(TPath path, Iterable<? extends CharSequence> lines, TOpenOption... options)
            throws IOException {
        return write(path, lines, StandardCharsets.UTF_8, options);
    }

    public static TPath writeString(TPath path, CharSequence csq, TOpenOption... options) throws IOException {
        return writeString(path, csq, StandardCharsets.UTF_8, options);
    }

    public static TPath writeString(TPath path, CharSequence csq, Charset charset, TOpenOption... options)
            throws IOException {
        return write(path, csq.toString().getBytes(charset), options);
    }

    public static boolean exists(TPath path, TLinkOption... options) {
        return file(path).exists();
    }

    public static boolean notExists(TPath path, TLinkOption... options) {
        return !file(path).exists();
    }

    public static boolean isDirectory(TPath path, TLinkOption... options) {
        return file(path).isDirectory();
    }

    public static boolean isRegularFile(TPath path, TLinkOption... options) {
        return file(path).isFile();
    }

    public static boolean isReadable(TPath path) {
        return file(path).canRead();
    }

    public static boolean isWritable(TPath path) {
        return file(path).canWrite();
    }

    public static long size(TPath path) throws IOException {
        File file = file(path);
        if (!file.exists()) {
            throw new TNoSuchFileException(path.toString());
        }
        return file.length();
    }

    public static void delete(TPath path) throws IOException {
        File file = file(path);
        if (!file.exists()) {
            throw new TNoSuchFileException(path.toString());
        }
        if (file.isDirectory()) {
            String[] children = file.list();
            if (children != null && children.length > 0) {
                throw new TDirectoryNotEmptyException(path.toString());
            }
        }
        if (!file.delete()) {
            throw new TFileSystemException(path.toString(), null, "Unable to delete");
        }
    }

    public static boolean deleteIfExists(TPath path) throws IOException {
        if (!file(path).exists()) {
            return false;
        }
        delete(path);
        return true;
    }

    public static TPath createDirectory(TPath dir, TFileAttribute<?>... attrs) throws IOException {
        File file = file(dir);
        if (file.exists()) {
            throw new TFileAlreadyExistsException(dir.toString());
        }
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory()) {
            throw new TNoSuchFileException(dir.toString());
        }
        if (!file.mkdir()) {
            throw new TFileSystemException(dir.toString(), null, "Unable to create directory");
        }
        return dir;
    }

    public static TPath createDirectories(TPath dir, TFileAttribute<?>... attrs) throws IOException {
        File file = file(dir);
        if (file.isDirectory()) {
            return dir;
        }
        if (file.exists()) {
            throw new TFileAlreadyExistsException(dir.toString());
        }
        if (!file.mkdirs() && !file.isDirectory()) {
            throw new TFileSystemException(dir.toString(), null, "Unable to create directories");
        }
        return dir;
    }

    public static TPath createFile(TPath path, TFileAttribute<?>... attrs) throws IOException {
        File file = file(path);
        if (file.exists()) {
            throw new TFileAlreadyExistsException(path.toString());
        }
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory()) {
            throw new TNoSuchFileException(path.toString());
        }
        if (!file.createNewFile()) {
            throw new TFileAlreadyExistsException(path.toString());
        }
        return path;
    }
}