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
package org.teavm.classlib.java.lang;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashMap;
import java.util.Map;
import org.teavm.classlib.impl.PackageInfo;

public class TPackage implements AnnotatedElement {
    private static Map<String, TPackage> packages = new HashMap<>();
    private String name;

    TPackage(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // A package is defined once a class of it has been loaded (here: asked for its package) or when it is a
    // package of the program's classpath.
    @Deprecated
    public static TPackage getPackage(String name) {
        TPackage pkg = packages.get(name);
        if (pkg == null && PackageInfo.isClasspathPackage(name)) {
            pkg = definePackage(name);
        }
        return pkg;
    }

    static TPackage definePackage(String name) {
        return packages.computeIfAbsent(name, TPackage::new);
    }

    // Attributes come from the manifest of the classpath entry defining the package (URLClassLoader.definePackage).
    public String getSpecificationTitle() {
        return PackageInfo.attribute(name, "Specification-Title");
    }

    public String getSpecificationVersion() {
        return PackageInfo.attribute(name, "Specification-Version");
    }

    public String getSpecificationVendor() {
        return PackageInfo.attribute(name, "Specification-Vendor");
    }

    public String getImplementationTitle() {
        return PackageInfo.attribute(name, "Implementation-Title");
    }

    public String getImplementationVersion() {
        return PackageInfo.attribute(name, "Implementation-Version");
    }

    public String getImplementationVendor() {
        return PackageInfo.attribute(name, "Implementation-Vendor");
    }

    public boolean isSealed() {
        return "true".equalsIgnoreCase(PackageInfo.attribute(name, "Sealed"));
    }

    // JDK Package.isCompatibleWith: dotted decimal comparison against the specification version.
    public boolean isCompatibleWith(String desired) {
        String version = getSpecificationVersion();
        if (version == null || version.length() < 1) {
            throw new NumberFormatException("Empty version string");
        }
        String[] have = version.split("\\.", -1);
        int[] haveNumbers = new int[have.length];
        for (int i = 0; i < have.length; ++i) {
            haveNumbers[i] = Integer.parseInt(have[i]);
            if (haveNumbers[i] < 0) {
                throw new NumberFormatException("For input string: \"" + haveNumbers[i] + "\"");
            }
        }
        String[] want = desired.split("\\.", -1);
        int[] wantNumbers = new int[want.length];
        for (int i = 0; i < want.length; ++i) {
            wantNumbers[i] = Integer.parseInt(want[i]);
            if (wantNumbers[i] < 0) {
                throw new NumberFormatException("For input string: \"" + wantNumbers[i] + "\"");
            }
        }
        int length = Math.max(wantNumbers.length, haveNumbers.length);
        for (int i = 0; i < length; ++i) {
            int d = i < wantNumbers.length ? wantNumbers[i] : 0;
            int s = i < haveNumbers.length ? haveNumbers[i] : 0;
            if (s < d) {
                return false;
            }
            if (s > d) {
                return true;
            }
        }
        return true;
    }

    // One Package per name, so equality is identity (as Object.equals on the JDK).
    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        String spec = getSpecificationTitle();
        String version = getSpecificationVersion();
        spec = spec != null && !spec.isEmpty() ? ", " + spec : "";
        version = version != null && !version.isEmpty() ? ", version " + version : "";
        return "package " + name + spec + version;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

    @Override
    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }
}