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
package org.teavm.classlib.java.security;

import org.teavm.classlib.sun.security.provider.TSun;

/**
 * {@code java.security.Security}, with the providers this platform implements.
 */
public final class TSecurity {
    private TSecurity() {
    }

    public static TProvider[] getProviders() {
        return new TProvider[] { TSun.INSTANCE };
    }

    public static TProvider getProvider(String name) {
        if (name == null) {
            throw new NullPointerException();
        }
        for (TProvider provider : getProviders()) {
            if (provider.getName().equals(name)) {
                return provider;
            }
        }
        return null;
    }

    // As sun.security.jca.GetInstance: the provider named, checked before the algorithm.
    static TProvider requireProvider(String name) throws TNoSuchProviderException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("missing provider");
        }
        TProvider provider = getProvider(name);
        if (provider == null) {
            throw new TNoSuchProviderException("no such provider: " + name);
        }
        return provider;
    }

    static void requireProvider(TProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("missing provider");
        }
    }
}
