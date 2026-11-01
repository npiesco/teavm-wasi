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
package org.teavm.model.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Where the JDK running the compiler defines a class: its module and class loader, as runtime messages and stack
 * traces report them. Classes the JDK does not define, including every class of the compiled program, belong to the
 * unnamed module of the application class loader.
 */
public final class HostJdk {
    private static final Map<String, Class<?>> CLASSES = new HashMap<>();

    private HostJdk() {
    }

    /** The module of a platform class, or "" for any other class. Array classes are in their element's module. */
    public static String moduleName(String className) {
        Class<?> cls = platformClass(className);
        return cls != null ? invokeString(invoke(cls, Class.class, "getModule"), "getName") : "";
    }

    /** The JDK's description, as in "module java.base of loader 'bootstrap'", or null for application classes. */
    public static String moduleDescription(String className) {
        Class<?> cls = platformClass(className);
        if (cls == null) {
            return null;
        }
        String module = invokeString(invoke(cls, Class.class, "getModule"), "getName");
        ClassLoader loader = cls.getClassLoader();
        String loaderName = loader == null ? "bootstrap" : invokeString(loader, "getName");
        return "module " + module + " of loader '" + loaderName + "'";
    }

    // Accepts a class name or an array class name as Class.getName returns it.
    private static synchronized Class<?> platformClass(String className) {
        String element = className;
        while (element.startsWith("[")) {
            element = element.substring(1);
        }
        if (element.startsWith("L") && element.endsWith(";")) {
            element = element.substring(1, element.length() - 1);
        } else if (element.length() != className.length()) {
            element = "java.lang.Object";
        }
        return CLASSES.computeIfAbsent(element, HostJdk::lookup);
    }

    private static Class<?> lookup(String className) {
        try {
            ClassLoader platform = (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
            Class<?> cls = Class.forName(className, false, platform);
            return cls.getClassLoader() == null || cls.getClassLoader() == platform ? cls : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Object invoke(Object target, Class<?> type, String method) {
        try {
            return type.getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String invokeString(Object target, String method) {
        try {
            return (String) target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
