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

/**
 * {@code java.lang.reflect.Executable}: what methods and constructors share.
 */
public abstract class TExecutable extends TAccessibleObject implements TMember {
    private TParameter[] parameters;

    TExecutable() {
    }

    @Override
    public abstract TClass<?> getDeclaringClass();

    @Override
    public abstract String getName();

    @Override
    public abstract int getModifiers();

    public abstract TClass<?>[] getParameterTypes();

    public int getParameterCount() {
        return getParameterTypes().length;
    }

    public abstract TClass<?>[] getExceptionTypes();

    public abstract boolean isVarArgs();

    @Override
    public abstract boolean isSynthetic();

    // Without the MethodParameters attribute the JDK synthesizes names argN and reports them as absent.
    public TParameter[] getParameters() {
        if (parameters == null) {
            TParameter[] result = new TParameter[getParameterCount()];
            for (int i = 0; i < result.length; ++i) {
                result[i] = new TParameter("arg" + i, 0, this, i);
            }
            parameters = result;
        }
        return parameters.clone();
    }
}
