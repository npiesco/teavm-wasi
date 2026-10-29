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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.teavm.platform.metadata.ResourceArray;
import org.teavm.platform.metadata.ResourceMap;
import org.teavm.platform.metadata.StringResource;

/**
 * Classpath resources on the WebAssembly target, embedded at build time by
 * {@link ClasspathResourcesGenerator}. A resource present in several classpath entries has one occurrence
 * per entry, in classpath order.
 */
public final class ClasspathResources {
    private static ResourceMap<ResourceArray<StringResource>> resources;

    private ClasspathResources() {
    }

    // Bound to ClasspathResourcesGenerator by JCLPlugin.installMetadata.
    private static native ResourceMap<ResourceArray<StringResource>> embedded();

    private static ResourceArray<StringResource> occurrences(String name) {
        if (resources == null) {
            resources = embedded();
        }
        return name != null && resources.has(name) ? resources.get(name) : null;
    }

    public static int count(String name) {
        ResourceArray<StringResource> found = occurrences(name);
        return found == null ? 0 : found.size();
    }

    public static byte[] data(String name, int occurrence) {
        ResourceArray<StringResource> found = occurrences(name);
        if (found == null || occurrence < 0 || occurrence >= found.size()) {
            return null;
        }
        return Base64Impl.decode(found.get(occurrence).getValue().getBytes(StandardCharsets.ISO_8859_1));
    }

    public static InputStream stream(String name) {
        byte[] data = data(name, 0);
        return data == null ? null : new ByteArrayInputStream(data);
    }

}
