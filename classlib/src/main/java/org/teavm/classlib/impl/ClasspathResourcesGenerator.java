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
import java.util.Base64;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.teavm.model.MethodReference;
import org.teavm.platform.metadata.MetadataGenerator;
import org.teavm.platform.metadata.MetadataGeneratorContext;
import org.teavm.platform.metadata.Resource;
import org.teavm.platform.metadata.ResourceArray;
import org.teavm.platform.metadata.ResourceMap;
import org.teavm.platform.metadata.StringResource;

/**
 * Embeds the program's classpath resources (every non-class entry, Base64 encoded, per classpath entry
 * in which it occurs) for {@link ClasspathResources}. Directories come first, then archives, which is
 * the order Maven puts the project's output and its dependencies on the JVM classpath. Entries that
 * carry {@code org/teavm/} classes are the compiler's own toolchain, not the program, and are skipped.
 */
public class ClasspathResourcesGenerator implements MetadataGenerator {
    private static final String CLASS_SUFFIX = ".class";
    private static final String TOOLCHAIN_PREFIX = "org/teavm/";

    @Override
    public Resource generateMetadata(MetadataGeneratorContext context, MethodReference method) {
        Map<String, List<byte[]>> found = new LinkedHashMap<>();
        List<File> directories = new ArrayList<>();
        List<File> archives = new ArrayList<>();
        for (File entry : classpath(context.getClassLoader())) {
            (entry.isDirectory() ? directories : archives).add(entry);
        }
        try {
            for (File directory : directories) {
                collectDirectory(directory.toPath(), found);
            }
            for (File archive : archives) {
                collectArchive(archive, found);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ResourceMap<ResourceArray<StringResource>> result = context.createResourceMap();
        for (Map.Entry<String, List<byte[]>> entry : found.entrySet()) {
            ResourceArray<StringResource> occurrences = context.createResourceArray();
            for (byte[] data : entry.getValue()) {
                StringResource encoded = context.createResource(StringResource.class);
                encoded.setValue(Base64.getEncoder().encodeToString(data));
                occurrences.add(encoded);
            }
            result.put(entry.getKey(), occurrences);
        }
        return result;
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

    private static void collectDirectory(Path root, Map<String, List<byte[]>> found) throws IOException {
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
            if (!name.endsWith(CLASS_SUFFIX)) {
                found.computeIfAbsent(name, k -> new ArrayList<>()).add(Files.readAllBytes(file));
            }
        }
    }

    private static String relativeName(Path root, Path file) {
        return root.relativize(file).toString().replace(File.separatorChar, '/');
    }

    private static void collectArchive(File archive, Map<String, List<byte[]>> found) throws IOException {
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
                if (entry.isDirectory() || entry.getName().endsWith(CLASS_SUFFIX)) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    found.computeIfAbsent(entry.getName(), k -> new ArrayList<>()).add(IOUtils.toByteArray(input));
                }
            }
        }
    }
}