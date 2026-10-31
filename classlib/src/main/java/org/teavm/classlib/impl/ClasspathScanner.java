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
package org.teavm.classlib.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;

/**
 * Reads the program's classpath at build time: every non-class entry, per classpath entry in which it occurs.
 * Directories come first, then archives, which is the order Maven puts the project's output and its
 * dependencies on the JVM classpath. Entries that carry {@code org/teavm/} classes are the compiler's own
 * toolchain, not the program, and are skipped.
 */
final class ClasspathScanner {
    private static final String CLASS_SUFFIX = ".class";
    private static final String TOOLCHAIN_PREFIX = "org/teavm/";

    private ClasspathScanner() {
    }

    static Map<String, List<byte[]>> resources(ClassLoader loader, Predicate<String> accept) {
        Map<String, List<byte[]>> found = new LinkedHashMap<>();
        List<File> directories = new ArrayList<>();
        List<File> archives = new ArrayList<>();
        for (File entry : classpath(loader)) {
            (entry.isDirectory() ? directories : archives).add(entry);
        }
        try {
            for (File directory : directories) {
                collectDirectory(directory.toPath(), accept, found);
            }
            for (File archive : archives) {
                collectArchive(archive, accept, found);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    /**
     * Package attributes as URLClassLoader.definePackage derives them: the first classpath entry (in classpath
     * order) holding a class of the package defines it; for an archive, its manifest's per-package section
     * ({@code pkg/path/}) overrides the main attributes; a directory has no manifest, so every attribute is absent.
     */
    static Map<String, Map<String, String>> packages(ClassLoader loader) {
        Map<String, Map<String, String>> packages = new LinkedHashMap<>();
        List<File> directories = new ArrayList<>();
        List<File> archives = new ArrayList<>();
        for (File entry : classpath(loader)) {
            (entry.isDirectory() ? directories : archives).add(entry);
        }
        try {
            for (File directory : directories) {
                Path root = directory.toPath();
                List<Path> files;
                try (Stream<Path> walk = Files.walk(root)) {
                    files = walk.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
                }
                if (files.stream().anyMatch(file -> relativeName(root, file).startsWith(TOOLCHAIN_PREFIX))) {
                    continue;
                }
                for (Path file : files) {
                    String name = relativeName(root, file);
                    if (name.endsWith(CLASS_SUFFIX)) {
                        packages.putIfAbsent(packageOf(name), new LinkedHashMap<>());
                    }
                }
            }
            for (File archive : archives) {
                try (JarFile jar = new JarFile(archive)) {
                    List<String> classes = new ArrayList<>();
                    boolean toolchain = false;
                    for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                        String name = e.nextElement().getName();
                        toolchain |= name.startsWith(TOOLCHAIN_PREFIX);
                        if (name.endsWith(CLASS_SUFFIX)) {
                            classes.add(name);
                        }
                    }
                    if (toolchain) {
                        continue;
                    }
                    Manifest manifest = jar.getManifest();
                    for (String name : classes) {
                        String pkg = packageOf(name);
                        if (!packages.containsKey(pkg)) {
                            packages.put(pkg, packageAttributes(manifest, pkg));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return packages;
    }

    private static final String[] PACKAGE_ATTRIBUTES = { "Specification-Title", "Specification-Version",
            "Specification-Vendor", "Implementation-Title", "Implementation-Version", "Implementation-Vendor",
            "Sealed" };

    private static Map<String, String> packageAttributes(Manifest manifest, String pkg) {
        Map<String, String> result = new LinkedHashMap<>();
        if (manifest == null) {
            return result;
        }
        Attributes section = manifest.getAttributes(pkg.replace('.', '/') + "/");
        Attributes main = manifest.getMainAttributes();
        for (String key : PACKAGE_ATTRIBUTES) {
            String value = section != null ? section.getValue(key) : null;
            if (value == null) {
                value = main.getValue(key);
            }
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static String packageOf(String classFile) {
        int slash = classFile.lastIndexOf('/');
        return slash < 0 ? "" : classFile.substring(0, slash).replace('/', '.');
    }

    private static List<File> classpath(ClassLoader loader) {
        List<File> entries = new ArrayList<>();
        if (!(loader instanceof URLClassLoader)) {
            return entries;
        }
        for (URL url : ((URLClassLoader) loader).getURLs()) {
            if (!url.getProtocol().equals("file")) {
                continue;
            }
            try {
                File file = new File(url.toURI());
                if (file.exists()) {
                    entries.add(file);
                }
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(url.toString(), e);
            }
        }
        return entries;
    }

    private static void collectDirectory(Path root, Predicate<String> accept, Map<String, List<byte[]>> found)
            throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        }
        for (Path file : files) {
            if (relativeName(root, file).startsWith(TOOLCHAIN_PREFIX)) {
                return;
            }
        }
        for (Path file : files) {
            String name = relativeName(root, file);
            if (!name.endsWith(CLASS_SUFFIX) && accept.test(name)) {
                found.computeIfAbsent(name, k -> new ArrayList<>()).add(Files.readAllBytes(file));
            }
        }
    }

    private static String relativeName(Path root, Path file) {
        return root.relativize(file).toString().replace(File.separatorChar, '/');
    }

    private static void collectArchive(File archive, Predicate<String> accept, Map<String, List<byte[]>> found)
            throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            List<ZipEntry> entries = new ArrayList<>();
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                ZipEntry entry = e.nextElement();
                if (entry.getName().startsWith(TOOLCHAIN_PREFIX)) {
                    return;
                }
                entries.add(entry);
            }
            for (ZipEntry entry : entries) {
                if (entry.isDirectory() || entry.getName().endsWith(CLASS_SUFFIX) || !accept.test(entry.getName())) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    found.computeIfAbsent(entry.getName(), k -> new ArrayList<>()).add(IOUtils.toByteArray(input));
                }
            }
        }
    }
}