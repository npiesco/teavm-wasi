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
package org.teavm.tooling.builder;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Class loader over the compiled program's classpath.
 *
 * <p>TeaVM reads the bytecode it compiles as resources ({@code Foo.class}). With
 * ordinary parent-first delegation, a class that also exists in the build tool's
 * own class loader (for example the commons-lang3 3.4 that the Maven plugin
 * realm carries through htmlunit) is read from the tool instead of from the
 * program's resolved dependency, so the program is compiled against the wrong
 * library version. Resources are therefore resolved from the program classpath
 * first. Class loading keeps parent-first delegation so TeaVM plugin classes
 * share a single identity with the compiler that hosts them.
 */
public class ProjectFirstResourceClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    public ProjectFirstResourceClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    public URL getResource(String name) {
        URL own = findResource(name);
        return own != null ? own : super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        LinkedHashSet<URL> ordered = new LinkedHashSet<>(Collections.list(findResources(name)));
        ClassLoader parent = getParent();
        List<URL> inherited = parent != null
                ? Collections.list(parent.getResources(name))
                : Collections.list(ClassLoader.getSystemResources(name));
        ordered.addAll(inherited);
        return Collections.enumeration(ordered);
    }
}
