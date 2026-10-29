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
package org.teavm.classlib.java.util;

import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.java.lang.*;
import org.teavm.platform.PlatformClass;

public final class TServiceLoader<S> extends TObject implements TIterable<S> {
    private final TClass<S> service;
    private Object[] services;
    private final TList<S> instantiated = new TArrayList<>();
    private int nextProvider;

    private TServiceLoader(TClass<S> service) {
        this.service = service;
        if (!PlatformDetector.isWebAssembly()) {
            services = doLoadServices(service.getPlatformClass());
        }
    }

    @Override
    public TIterator<S> iterator() {
        if (PlatformDetector.isWebAssembly()) {
            return new LazyIterator();
        }
        return new TIterator<S>() {
            private int index;
            @Override public boolean hasNext() {
                return index < services.length;
            }
            @SuppressWarnings("unchecked") @Override public S next() {
                if (index == services.length) {
                    throw new TNoSuchElementException();
                }
                return (S) services[index++];
            }
            @Override public void remove() {
                throw new TUnsupportedOperationException();
            }
        };
    }

    // Providers are created one at a time as iteration reaches them and cached by this loader,
    // so a later iterator first replays the cached providers and then continues the lookup.
    private class LazyIterator implements TIterator<S> {
        private int index;

        @Override
        public boolean hasNext() {
            return index < instantiated.size() || nextProvider < serviceCount(service);
        }

        @SuppressWarnings("unchecked")
        @Override
        public S next() {
            if (index < instantiated.size()) {
                return instantiated.get(index++);
            }
            if (nextProvider >= serviceCount(service)) {
                throw new TNoSuchElementException();
            }
            int provider = nextProvider++;
            S instance;
            try {
                instance = (S) createService(service, provider);
            } catch (Throwable e) {
                throw new TServiceConfigurationError(service.getName() + ": Provider "
                        + providerClass(service, provider).getName() + " could not be instantiated", e);
            }
            instantiated.add(instance);
            index++;
            return instance;
        }

        @Override
        public void remove() {
            throw new TUnsupportedOperationException();
        }
    }

    public static <S> TServiceLoader<S> load(TClass<S> service) {
        return new TServiceLoader<>(service);
    }

    public static <S> TServiceLoader<S> load(TClass<S> service, @SuppressWarnings("unused") TClassLoader loader) {
        return load(service);
    }

    public static <S> TServiceLoader<S> loadInstalled(TClass<S> service) {
        return load(service);
    }

    private static Object[] doLoadServices(PlatformClass cls) {
        Object[] result = loadServices(cls);
        if (result == null) {
            result = new Object[0];
        }
        return result;
    }

    private static native Object[] loadServices(PlatformClass cls);

    // WebAssembly: bodies are generated at build time from META-INF/services by ServiceLoaderWasmSupport.
    private static native int serviceCount(TClass<?> service);

    private static native Object createService(TClass<?> service, int index);

    private static native TClass<?> providerClass(TClass<?> service, int index);

    public void reload() {
        // Eager targets bind services at build time; on WebAssembly the provider cache is cleared.
        instantiated.clear();
        nextProvider = 0;
    }
}