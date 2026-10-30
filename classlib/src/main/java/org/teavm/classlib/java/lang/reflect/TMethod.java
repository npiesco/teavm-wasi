/*
 *  Copyright 2016 Alexey Andreev.
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

import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.impl.reflection.Converter;
import org.teavm.classlib.impl.reflection.Flags;
import org.teavm.classlib.impl.reflection.JSCallable;
import org.teavm.classlib.java.lang.TClass;
import org.teavm.classlib.java.lang.TIllegalAccessException;
import org.teavm.classlib.java.lang.TIllegalArgumentException;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.classlib.java.lang.TThrowable;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformObject;
import org.teavm.platform.PlatformSequence;

public class TMethod extends TAccessibleObject implements TMember {
    private TClass<?> declaringClass;
    private String name;
    private int flags;
    private int accessLevel;
    private TClass<?> returnType;
    private TClass<?>[] parameterTypes;
    private JSCallable callable;
    private int wasmIndex = -1;
    private TClass<?>[] exceptionTypes;

    // WebAssembly: metadata and invocation are generated at build time by ReflectionWasmSupport; modifiers are the
    // JDK bits.
    private TMethod(TClass<?> declaringClass, int index) {
        this.declaringClass = declaringClass;
        this.wasmIndex = index;
        this.name = wasmMethodName(declaringClass, index);
        this.flags = wasmMethodModifiers(declaringClass, index);
        this.returnType = wasmMethodReturnType(declaringClass, index);
        this.parameterTypes = wasmMethodParameterTypes(declaringClass, index);
        this.exceptionTypes = wasmMethodExceptionTypes(declaringClass, index);
    }

    public static TMethod[] wasmDeclaredMethods(TClass<?> declaringClass) {
        if (!PlatformDetector.isWebAssembly()) {
            throw new UnsupportedOperationException();
        }
        TMethod[] result = new TMethod[wasmMethodCount(declaringClass)];
        for (int i = 0; i < result.length; ++i) {
            result[i] = new TMethod(declaringClass, i);
        }
        return result;
    }

    private static native int wasmMethodCount(TClass<?> declaringClass);

    private static native int wasmMethodModifiers(TClass<?> declaringClass, int index);

    private static native String wasmMethodName(TClass<?> declaringClass, int index);

    private static native TClass<?> wasmMethodReturnType(TClass<?> declaringClass, int index);

    private static native TClass<?>[] wasmMethodParameterTypes(TClass<?> declaringClass, int index);

    private static native TClass<?>[] wasmMethodExceptionTypes(TClass<?> declaringClass, int index);

    private static native Object wasmInvoke(TClass<?> declaringClass, int index, Object receiver, Object[] args);

    public TMethod(TClass<?> declaringClass, String name, int flags, int accessLevel, TClass<?> returnType,
            TClass<?>[] parameterTypes, JSCallable callable) {
        this.declaringClass = declaringClass;
        this.name = name;
        this.flags = flags;
        this.accessLevel = accessLevel;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.callable = callable;
    }

    @Override
    public TClass<?> getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getModifiers() {
        if (wasmIndex >= 0) {
            return flags;
        }
        return Flags.getModifiers(flags, accessLevel);
    }

    public TClass<?>[] getExceptionTypes() {
        return exceptionTypes != null ? exceptionTypes.clone() : new TClass<?>[0];
    }

    // JDK Method.isDefault: a public, non-abstract, instance method declared by an interface.
    public boolean isDefault() {
        return (getModifiers() & (TModifier.ABSTRACT | TModifier.PUBLIC | TModifier.STATIC)) == TModifier.PUBLIC
                && declaringClass.isInterface();
    }

    // JDK Method.equals: same declaring class, name, return type and parameter types.
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TMethod)) {
            return false;
        }
        TMethod other = (TMethod) obj;
        return declaringClass == other.declaringClass && name.equals(other.name)
                && returnType == other.returnType && java.util.Arrays.equals(parameterTypes, other.parameterTypes);
    }

    @Override
    public int hashCode() {
        return declaringClass.getName().hashCode() ^ name.hashCode();
    }

    public TClass<?> getReturnType() {
        return returnType;
    }

    public TClass<?>[] getParameterTypes() {
        return parameterTypes.clone();
    }

    public int getParameterCount() {
        return parameterTypes.length;
    }

    // JDK Method.toString: method modifiers (with "default" for default methods), then the return type, the
    // qualified name, the parameter types and the throws clause, all as type names.
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        TModifier.appendModifiers(sb, getModifiers() & TModifier.methodModifiers(), isDefault());
        sb.append(getReturnType().getTypeName()).append(' ').append(declaringClass.getTypeName()).append('.')
                .append(name);
        TModifier.appendSignature(sb, parameterTypes, getExceptionTypes());
        return sb.toString();
    }

    public Object invoke(Object obj, Object... args) throws TIllegalAccessException, TIllegalArgumentException,
            TInvocationTargetException {
        if (PlatformDetector.isWebAssembly()) {
            return wasmInvoke(obj, args);
        }
        if (callable == null) {
            throw new TIllegalAccessException();
        }

        if (args.length != parameterTypes.length) {
            throw new TIllegalArgumentException();
        }

        if ((flags & Flags.STATIC) == 0) {
            if (!declaringClass.isInstance((TObject) obj)) {
                throw new TIllegalArgumentException();
            }
        } else {
            Platform.initClass(declaringClass.getPlatformClass());
        }

        for (int i = 0; i < args.length; ++i) {
            if (!parameterTypes[i].isPrimitive() && args[i] != null
                    && !parameterTypes[i].isInstance((TObject) args[i])) {
                throw new TIllegalArgumentException();
            }
            if (parameterTypes[i].isPrimitive() && args[i] == null) {
                throw new TIllegalArgumentException();
            }
        }

        PlatformSequence<PlatformObject> jsArgs = Converter.arrayFromJava(args);
        PlatformObject result = callable.call(Platform.getPlatformObject(obj), jsArgs);
        return Converter.toJava(result);
    }

    private Object wasmInvoke(Object obj, Object[] args) throws TInvocationTargetException {
        if ((flags & TModifier.STATIC) == 0) {
            if (obj == null) {
                throw new NullPointerException();
            }
            if (!declaringClass.isInstance((TObject) obj)) {
                throw new TIllegalArgumentException("object is not an instance of declaring class");
            }
        }
        int count = args == null ? 0 : args.length;
        if (count != parameterTypes.length) {
            throw new TIllegalArgumentException("wrong number of arguments: " + count + " expected: "
                    + parameterTypes.length);
        }
        Object[] arguments = new Object[count];
        for (int i = 0; i < count; ++i) {
            arguments[i] = TConstructor.convertArgument(parameterTypes[i], args[i]);
        }
        try {
            return wasmInvoke(declaringClass, wasmIndex, obj, arguments);
        } catch (Throwable e) {
            throw new TInvocationTargetException((TThrowable) (Object) e);
        }
    }

    public boolean isBridge() {
        if (wasmIndex >= 0) {
            return (flags & 0x40) != 0;
        }
        return (flags & Flags.BRIDGE) != 0;
    }

    @Override
    public boolean isSynthetic() {
        if (wasmIndex >= 0) {
            return (flags & 0x1000) != 0;
        }
        return (flags & Flags.SYNTHETIC) != 0;
    }

    public boolean isVarArgs() {
        if (wasmIndex >= 0) {
            return (flags & 0x80) != 0;
        }
        return (flags & Flags.VARARGS) != 0;
    }
}
