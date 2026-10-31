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

import org.teavm.platform.metadata.ResourceMap;
import org.teavm.platform.metadata.StringResource;

/**
 * Manifest attributes of the program's classpath packages, embedded at build time by {@link PackageInfoGenerator}.
 */
public final class PackageInfo {
    private static ResourceMap<ResourceMap<StringResource>> packages;

    private PackageInfo() {
    }

    // Bound to PackageInfoGenerator by JCLPlugin.installMetadata.
    private static native ResourceMap<ResourceMap<StringResource>> embedded();

    private static ResourceMap<ResourceMap<StringResource>> packages() {
        if (packages == null) {
            packages = embedded();
        }
        return packages;
    }

    public static boolean isClasspathPackage(String name) {
        return packages().has(name);
    }

    public static String attribute(String pkg, String name) {
        if (!packages().has(pkg)) {
            return null;
        }
        ResourceMap<StringResource> attributes = packages().get(pkg);
        return attributes.has(name) ? attributes.get(name).getValue() : null;
    }
}