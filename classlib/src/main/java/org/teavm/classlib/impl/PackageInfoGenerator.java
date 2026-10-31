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

import java.util.Map;
import org.teavm.model.MethodReference;
import org.teavm.platform.metadata.MetadataGenerator;
import org.teavm.platform.metadata.MetadataGeneratorContext;
import org.teavm.platform.metadata.Resource;
import org.teavm.platform.metadata.ResourceMap;
import org.teavm.platform.metadata.StringResource;

/**
 * Embeds, per package of the program's classpath, the manifest attributes that define it (see
 * {@link ClasspathScanner#packages}), for {@code java.lang.Package}.
 */
public class PackageInfoGenerator implements MetadataGenerator {
    @Override
    public Resource generateMetadata(MetadataGeneratorContext context, MethodReference method) {
        ResourceMap<ResourceMap<StringResource>> result = context.createResourceMap();
        for (Map.Entry<String, Map<String, String>> entry : ClasspathScanner.packages(context.getClassLoader())
                .entrySet()) {
            ResourceMap<StringResource> attributes = context.createResourceMap();
            for (Map.Entry<String, String> attribute : entry.getValue().entrySet()) {
                StringResource value = context.createResource(StringResource.class);
                value.setValue(attribute.getValue());
                attributes.put(attribute.getKey(), value);
            }
            result.put(entry.getKey(), attributes);
        }
        return result;
    }
}