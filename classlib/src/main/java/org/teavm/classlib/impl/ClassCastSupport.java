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

import org.teavm.interop.Address;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformClass;
import org.teavm.runtime.RuntimeClass;

/**
 * The ClassCastException of a failed reference cast, with the JDK's message naming both classes and where they are
 * defined. WebAssembly code calls {@link #fail} where a checked cast fails.
 */
public final class ClassCastSupport {
    private static final String APPLICATION_MODULE = "unnamed module of loader 'app'";

    private ClassCastSupport() {
    }

    public static void fail(Object object, PlatformClass target) {
        RuntimeClass sourceClass = RuntimeClass.getClass(Address.ofObject(object).toStructure());
        RuntimeClass targetClass = Address.ofObject(target).toStructure();
        String source = object.getClass().getName();
        // The class record is the Class instance, as TClass.getClass finds it; referring to TClass from here would
        // compile a separate, unrenamed copy of it.
        String targetName = Platform.asJavaClass(target.getJavaClass()).getName();
        String sourceModule = describe(sourceClass);
        String targetModule = describe(targetClass);
        String location = sourceModule.equals(targetModule)
                ? source + " and " + targetName + " are in " + sourceModule
                : source + " is in " + sourceModule + "; " + targetName + " is in " + targetModule;
        throw new ClassCastException("class " + source + " cannot be cast to class " + targetName + " ("
                + location + ")");
    }

    private static String describe(RuntimeClass cls) {
        String description = cls.moduleDescription;
        return description != null ? description : APPLICATION_MODULE;
    }
}
