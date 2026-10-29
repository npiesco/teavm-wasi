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

import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.teavm.model.MethodReference;
import org.teavm.platform.metadata.MetadataGenerator;
import org.teavm.platform.metadata.MetadataGeneratorContext;
import org.teavm.platform.metadata.Resource;
import org.teavm.platform.metadata.ResourceArray;
import org.teavm.platform.metadata.ResourceMap;
import org.teavm.platform.metadata.StringResource;

/**
 * Embeds the program's classpath resources (see {@link ClasspathScanner}), Base64 encoded, for
 * {@link ClasspathResources}.
 */
public class ClasspathResourcesGenerator implements MetadataGenerator {
    @Override
    public Resource generateMetadata(MetadataGeneratorContext context, MethodReference method) {
        Map<String, List<byte[]>> found = ClasspathScanner.resources(context.getClassLoader(), name -> true);
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
}