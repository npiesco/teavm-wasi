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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.teavm.classlib.java.util.TProperties;

/**
 * {@code java.security.Provider}: a named set of algorithm implementations, looked up by type and by algorithm name
 * or alias without regard to case.
 */
public abstract class TProvider extends TProperties {
    private static final long serialVersionUID = -4298000515446427739L;
    private final String name;
    private final String versionStr;
    private final double version;
    private final String info;
    private final Map<String, Service> services = new LinkedHashMap<>();
    private final Map<String, Service> lookup = new LinkedHashMap<>();

    @Deprecated
    protected TProvider(String name, double version, String info) {
        this(name, Double.toString(version), version, info);
    }

    protected TProvider(String name, String versionStr, String info) {
        this(name, versionStr, parseVersion(versionStr), info);
    }

    private TProvider(String name, String versionStr, double version, String info) {
        this.name = name;
        this.versionStr = versionStr;
        this.version = version;
        this.info = info;
    }

    // The JDK reads the leading digits and dot of the version string.
    private static double parseVersion(String versionStr) {
        int end = 0;
        boolean dot = false;
        while (end < versionStr.length()) {
            char c = versionStr.charAt(end);
            if (c == '.' && !dot) {
                dot = true;
            } else if (c < '0' || c > '9') {
                break;
            }
            end++;
        }
        try {
            return Double.parseDouble(versionStr.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getName() {
        return name;
    }

    public String getVersionStr() {
        return versionStr;
    }

    @Deprecated
    public double getVersion() {
        return version;
    }

    public String getInfo() {
        return info;
    }

    @Override
    public String toString() {
        return name + " version " + versionStr;
    }

    private static String key(String type, String algorithm) {
        return type + "." + algorithm.toUpperCase(Locale.ENGLISH);
    }

    protected void putService(Service s) {
        if (s == null) {
            throw new NullPointerException();
        }
        if (s.getProvider() != this) {
            throw new IllegalArgumentException("service.getProvider() must match this Provider object");
        }
        services.put(key(s.getType(), s.getAlgorithm()), s);
        lookup.put(key(s.getType(), s.getAlgorithm()), s);
        for (String alias : s.aliases) {
            lookup.put(key(s.getType(), alias), s);
        }
    }

    public Service getService(String type, String algorithm) {
        if (type == null || algorithm == null) {
            throw new NullPointerException();
        }
        return lookup.get(key(type, algorithm));
    }

    public Set<Service> getServices() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(services.values()));
    }

    public static class Service {
        private final TProvider provider;
        private final String type;
        private final String algorithm;
        private final String className;
        private final List<String> aliases;

        public Service(TProvider provider, String type, String algorithm, String className, List<String> aliases,
                Map<String, String> attributes) {
            if (provider == null || type == null || algorithm == null || className == null) {
                throw new NullPointerException();
            }
            this.provider = provider;
            this.type = type;
            this.algorithm = algorithm;
            this.className = className;
            this.aliases = aliases == null ? Collections.emptyList() : new ArrayList<>(aliases);
        }

        public final String getType() {
            return type;
        }

        public final String getAlgorithm() {
            return algorithm;
        }

        public final TProvider getProvider() {
            return provider;
        }

        public final String getClassName() {
            return className;
        }

        @Override
        public String toString() {
            String aliasString = aliases.isEmpty() ? "" : "\r\n  aliases: " + aliases.toString();
            return provider.getName() + ": " + type + "." + algorithm + " -> " + className + aliasString + "\r\n";
        }
    }
}
