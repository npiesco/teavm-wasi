/*
 *  Copyright 2014 Alexey Andreev.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.teavm.backend.javascript.spi.InjectedBy;
import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.impl.Base64Impl;
import org.teavm.classlib.impl.ClasspathResources;
import org.teavm.classlib.java.net.TMalformedURLException;
import org.teavm.classlib.java.net.TURL;
import org.teavm.classlib.java.net.TURLConnection;
import org.teavm.classlib.java.net.TURLStreamHandler;
import org.teavm.classlib.java.util.TArrayList;
import org.teavm.classlib.java.util.TCollections;
import org.teavm.classlib.java.util.TEnumeration;
import org.teavm.classlib.java.util.TList;
import org.teavm.interop.NoSideEffects;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSIndexer;
import org.teavm.jso.JSObject;

public abstract class TClassLoader extends TObject {
    private TClassLoader parent;
    private static TSystemClassLoader systemClassLoader = new TSystemClassLoader();
    private static ResourceContainer resources;

    protected TClassLoader() {
        this(null);
    }

    protected TClassLoader(TClassLoader parent) {
        this.parent = parent;
    }

    public TClassLoader getParent() {
        return parent;
    }

    public TClass<?> loadClass(String name) throws TClassNotFoundException {
        return loadClass(name, false);
    }

    // Classes are bound at build time, so loading delegates to the closed-world lookup without initializing.
    protected TClass<?> loadClass(String name, boolean resolve) throws TClassNotFoundException {
        return TClass.forName((TString) (Object) name, false, this);
    }

    public static TClassLoader getSystemClassLoader() {
        return systemClassLoader;
    }

    public InputStream getResourceAsStream(String name) {
        if (PlatformDetector.isWebAssembly()) {
            return ClasspathResources.stream(name);
        }
        if (resources == null) {
            resources = supplyResources();
        }
        JSObject data = resources.getResource(name);
        String dataString = resourceToString(data);
        if (dataString == null) {
            return null;
        }
        byte[] bytes = new byte[dataString.length()];
        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) dataString.charAt(i);
        }
        return new ByteArrayInputStream(Base64Impl.decode(bytes));
    }

    public TURL getResource(String name) {
        if (!PlatformDetector.isWebAssembly()) {
            throw new UnsupportedOperationException("ClassLoader.getResource is supported on the WASI target only");
        }
        return resourceUrl(name, 0);
    }

    public TEnumeration<TURL> getResources(String name) throws IOException {
        if (!PlatformDetector.isWebAssembly()) {
            throw new UnsupportedOperationException("ClassLoader.getResources is supported on the WASI target only");
        }
        TList<TURL> urls = new TArrayList<>();
        for (int i = 0; i < ClasspathResources.count(name); ++i) {
            urls.add(resourceUrl(name, i));
        }
        return TCollections.enumeration(urls);
    }

    // A resource present in several classpath entries has one occurrence per entry, in classpath order.
    private static TURL resourceUrl(String name, int occurrence) {
        if (occurrence >= ClasspathResources.count(name)) {
            return null;
        }
        try {
            return new TURL("classpath-resource", "", -1, "/" + occurrence + "/" + name, new ResourceHandler());
        } catch (TMalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    static class ResourceHandler extends TURLStreamHandler {
        @Override
        protected TURLConnection openConnection(TURL u) {
            return new ResourceConnection(u);
        }
    }

    static class ResourceConnection extends TURLConnection {
        ResourceConnection(TURL url) {
            super(url);
        }

        @Override
        public void connect() {
        }

        @Override
        public InputStream getInputStream() throws IOException {
            String path = getURL().getPath();
            int separator = path.indexOf('/', 1);
            byte[] data = ClasspathResources.data(path.substring(separator + 1),
                    Integer.parseInt(path.substring(1, separator)));
            if (data == null) {
                throw new IOException(getURL().toString());
            }
            return new ByteArrayInputStream(data);
        }
    }

    public static TURL getSystemResource(String name) {
        return getSystemClassLoader().getResource(name);
    }

    public static TEnumeration<TURL> getSystemResources(String name) throws IOException {
        return getSystemClassLoader().getResources(name);
    }

    public static InputStream getSystemResourceAsStream(String name) {
        return getSystemClassLoader().getResourceAsStream(name);
    }

    @JSBody(params = "resource", script = "return resource !== null && resource !== void 0 ? resource : null;")
    private static native String resourceToString(JSObject resource);

    @InjectedBy(ClassLoaderNativeGenerator.class)
    @NoSideEffects
    private static native ResourceContainer supplyResources();

    interface ResourceContainer extends JSObject {
        @JSIndexer
        JSObject getResource(String name);
    }
}
