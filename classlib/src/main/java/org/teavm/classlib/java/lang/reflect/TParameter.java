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
package org.teavm.classlib.java.lang.reflect;

import org.teavm.classlib.java.lang.TClass;
import org.teavm.classlib.java.lang.annotation.TAnnotation;

/**
 * {@code java.lang.reflect.Parameter}.
 */
public final class TParameter implements TAnnotatedElement {
    private static final int SYNTHETIC = 0x1000;
    private static final int MANDATED = 0x8000;
    private final String name;
    private final int modifiers;
    private final TExecutable executable;
    private final int index;

    TParameter(String name, int modifiers, TExecutable executable, int index) {
        this.name = name;
        this.modifiers = modifiers;
        this.executable = executable;
        this.index = index;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TParameter)) {
            return false;
        }
        TParameter other = (TParameter) obj;
        return other.executable.equals(executable) && other.index == index;
    }

    @Override
    public int hashCode() {
        return executable.hashCode() ^ index;
    }

    public boolean isNamePresent() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String typeName = getType().getTypeName();
        sb.append(TModifier.toString(modifiers));
        if (modifiers != 0) {
            sb.append(' ');
        }
        if (isVarArgs() && typeName.endsWith("[]")) {
            sb.append(typeName, 0, typeName.length() - 2).append("...");
        } else {
            sb.append(typeName);
        }
        return sb.append(' ').append(name).toString();
    }

    public TExecutable getDeclaringExecutable() {
        return executable;
    }

    public int getModifiers() {
        return modifiers;
    }

    public String getName() {
        return name;
    }

    public TClass<?> getType() {
        return executable.getParameterTypes()[index];
    }

    public boolean isImplicit() {
        return (modifiers & MANDATED) != 0;
    }

    public boolean isSynthetic() {
        return (modifiers & SYNTHETIC) != 0;
    }

    public boolean isVarArgs() {
        return executable.isVarArgs() && index == executable.getParameterCount() - 1;
    }

    @Override
    public <T extends TAnnotation> T getAnnotation(TClass<T> annotationClass) {
        return null;
    }

    @Override
    public TAnnotation[] getAnnotations() {
        return new TAnnotation[0];
    }

    @Override
    public TAnnotation[] getDeclaredAnnotations() {
        return new TAnnotation[0];
    }
}
