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
import org.teavm.classlib.java.lang.TInstantiationException;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.classlib.java.lang.TThrowable;
import org.teavm.platform.PlatformObject;
import org.teavm.platform.PlatformSequence;

public class TConstructor<T> extends TAccessibleObject implements TMember {
    private TClass<T> declaringClass;
    private String name;
    private int modifiers;
    private int accessLevel;
    private TClass<?>[] parameterTypes;
    private JSCallable callable;
    private int wasmIndex = -1;

    // WebAssembly: constructor metadata and invocation are generated at build time by ReflectionWasmSupport.
    // Modifiers there are the JDK bits; the declaring class's ABSTRACT/ENUM state rides above them.
    static final int WASM_CLASS_ABSTRACT = 1 << 20;
    static final int WASM_CLASS_ENUM = 1 << 21;
    private static final int JDK_VARARGS = 0x80;
    private static final int JDK_SYNTHETIC = 0x1000;

    private TConstructor(TClass<T> declaringClass, int modifiers, TClass<?>[] parameterTypes, int wasmIndex) {
        this.declaringClass = declaringClass;
        this.name = declaringClass.getName();
        this.modifiers = modifiers;
        this.parameterTypes = parameterTypes;
        this.wasmIndex = wasmIndex;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> TConstructor<T>[] wasmDeclaredConstructors(TClass<T> declaringClass) {
        if (!PlatformDetector.isWebAssembly()) {
            throw new UnsupportedOperationException();
        }
        TConstructor<T>[] result = new TConstructor[wasmConstructorCount(declaringClass)];
        for (int i = 0; i < result.length; ++i) {
            result[i] = new TConstructor<>(declaringClass, wasmConstructorModifiers(declaringClass, i),
                    wasmConstructorParameterTypes(declaringClass, i), i);
        }
        return result;
    }

    private static native int wasmConstructorCount(TClass<?> declaringClass);

    private static native int wasmConstructorModifiers(TClass<?> declaringClass, int index);

    private static native TClass<?>[] wasmConstructorParameterTypes(TClass<?> declaringClass, int index);

    private static native Object wasmNewInstance(TClass<?> declaringClass, int index, Object[] arguments);

    private static native TClass<?>[] wasmConstructorExceptionTypes(TClass<?> declaringClass, int index);

    public TClass<?>[] getExceptionTypes() {
        if (wasmIndex >= 0) {
            return wasmConstructorExceptionTypes(declaringClass, wasmIndex);
        }
        return new TClass<?>[0];
    }

    public TConstructor(TClass<T> declaringClass, String name, int modifiers, int accessLevel,
            TClass<?>[] parameterTypes, JSCallable callable) {
        this.declaringClass = declaringClass;
        this.name = name;
        this.modifiers = modifiers;
        this.accessLevel = accessLevel;
        this.parameterTypes = parameterTypes;
        this.callable = callable;
    }

    @Override
    public TClass<T> getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getModifiers() {
        if (wasmIndex >= 0) {
            return modifiers & 0xFFFF;
        }
        return Flags.getModifiers(modifiers, accessLevel);
    }

    @Override
    public boolean isSynthetic() {
        if (wasmIndex >= 0) {
            return (modifiers & JDK_SYNTHETIC) != 0;
        }
        return (modifiers & Flags.SYNTHETIC) != 0;
    }

    public TClass<?>[] getParameterTypes() {
        return parameterTypes.clone();
    }

    // JDK Constructor: equal when declared by the same class with the same parameter types.
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TConstructor)) {
            return false;
        }
        TConstructor<?> other = (TConstructor<?>) obj;
        return declaringClass == other.declaringClass
                && java.util.Arrays.equals(parameterTypes, other.parameterTypes);
    }

    @Override
    public int hashCode() {
        return declaringClass.getName().hashCode();
    }
    public int getParameterCount() {
        return parameterTypes.length;
    }

    // JDK Constructor.toString: access modifiers, the declaring class's type name, parameter types and throws clause.
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        TModifier.appendModifiers(sb, getModifiers() & TModifier.constructorModifiers(), false);
        sb.append(declaringClass.getTypeName());
        TModifier.appendSignature(sb, parameterTypes, getExceptionTypes());
        return sb.toString();
    }
    @SuppressWarnings("unchecked")
    public T newInstance(Object... initargs) throws TInstantiationException, TIllegalAccessException,
            TIllegalArgumentException, TInvocationTargetException {
        if (PlatformDetector.isWebAssembly()) {
            return wasmNewInstance(initargs);
        }
        if ((modifiers & Flags.ABSTRACT) != 0) {
            throw new TInstantiationException();
        }
        if (callable == null) {
            throw new TIllegalAccessException();
        }

        if (initargs.length != parameterTypes.length) {
            throw new TIllegalArgumentException();
        }
        for (int i = 0; i < initargs.length; ++i) {
            if (!parameterTypes[i].isPrimitive() && initargs[i] != null
                    && !parameterTypes[i].isInstance((TObject) initargs[i])) {
                throw new TIllegalArgumentException();
            }
            if (parameterTypes[i].isPrimitive() && initargs[i] == null) {
                throw new TIllegalArgumentException();
            }
        }

        PlatformSequence<PlatformObject> jsArgs = Converter.arrayFromJava(initargs);
        PlatformObject instance = declaringClass.newEmptyInstance();
        callable.call(instance, jsArgs);
        return (T) Converter.toJava(instance);
    }

    @SuppressWarnings("unchecked")
    private T wasmNewInstance(Object[] initargs) throws TInstantiationException, TInvocationTargetException {
        if ((modifiers & WASM_CLASS_ENUM) != 0) {
            throw new TIllegalArgumentException("Cannot reflectively create enum objects");
        }
        if ((modifiers & WASM_CLASS_ABSTRACT) != 0) {
            throw new TInstantiationException();
        }
        int count = initargs == null ? 0 : initargs.length;
        if (count != parameterTypes.length) {
            throw new TIllegalArgumentException("wrong number of arguments: " + count + " expected: "
                    + parameterTypes.length);
        }
        Object[] arguments = new Object[count];
        for (int i = 0; i < count; ++i) {
            arguments[i] = convertArgument(parameterTypes[i], initargs[i]);
        }
        try {
            return (T) wasmNewInstance(declaringClass, wasmIndex, arguments);
        } catch (Throwable e) {
            throw new TInvocationTargetException((TThrowable) (Object) e);
        }
    }

    // Method invocation conversion (JLS 5.3): identity, unboxing and widening primitive conversion.
    static Object convertArgument(TClass<?> type, Object arg) {
        if (!type.isPrimitive()) {
            if (arg != null && !type.isInstance((TObject) arg)) {
                throw new TIllegalArgumentException("argument type mismatch");
            }
            return arg;
        }
        if (arg == null) {
            throw new TIllegalArgumentException();
        }
        Object converted = widen(type.getName(), arg);
        if (converted == null) {
            throw new TIllegalArgumentException("argument type mismatch");
        }
        return converted;
    }

    static Object widen(String target, Object arg) {
        if (arg instanceof Boolean) {
            return target.equals("boolean") ? arg : null;
        }
        if (arg instanceof Character) {
            if (target.equals("char")) {
                return arg;
            }
            return widenNumber(target, (int) (Character) arg, 3);
        }
        int rank;
        if (arg instanceof Byte) {
            rank = 1;
        } else if (arg instanceof Short) {
            rank = 2;
        } else if (arg instanceof Integer) {
            rank = 3;
        } else if (arg instanceof Long) {
            rank = 4;
        } else if (arg instanceof Float) {
            rank = 5;
        } else if (arg instanceof Double) {
            rank = 6;
        } else {
            return null;
        }
        return widenNumber(target, (Number) arg, rank);
    }

    private static Object widenNumber(String target, Number value, int sourceRank) {
        int targetRank;
        switch (target) {
            case "byte":
                targetRank = 1;
                break;
            case "short":
                targetRank = 2;
                break;
            case "int":
                targetRank = 3;
                break;
            case "long":
                targetRank = 4;
                break;
            case "float":
                targetRank = 5;
                break;
            case "double":
                targetRank = 6;
                break;
            default:
                return null;
        }
        if (targetRank < sourceRank) {
            return null;
        }
        switch (targetRank) {
            case 1:
                return value.byteValue();
            case 2:
                return value.shortValue();
            case 3:
                return value.intValue();
            case 4:
                return value.longValue();
            case 5:
                return value.floatValue();
            default:
                return value.doubleValue();
        }
    }

    public boolean isVarArgs() {
        if (wasmIndex >= 0) {
            return (modifiers & JDK_VARARGS) != 0;
        }
        return (modifiers & Flags.VARARGS) != 0;
    }
}
