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
import org.teavm.classlib.impl.reflection.JSFieldGetter;
import org.teavm.classlib.impl.reflection.JSFieldSetter;
import org.teavm.classlib.java.lang.TClass;
import org.teavm.classlib.java.lang.TIllegalAccessException;
import org.teavm.classlib.java.lang.TIllegalArgumentException;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformObject;

public class TField extends TAccessibleObject implements TMember {
    // WebAssembly: metadata and access are generated at build time by ReflectionWasmSupport; modifiers are JDK bits.
    private static final int JDK_STATIC = 0x8;
    private static final int JDK_FINAL = 0x10;
    private static final int JDK_SYNTHETIC = 0x1000;
    private static final int JDK_ENUM = 0x4000;

    private TClass<?> declaringClass;
    private String name;
    private int modifiers;
    private int accessLevel;
    private TClass<?> type;
    private JSFieldGetter getter;
    private JSFieldSetter setter;
    private int wasmIndex = -1;

    public TField(TClass<?> declaringClass, String name, int modifiers, int accessLevel, TClass<?> type,
            JSFieldGetter getter, JSFieldSetter setter) {
        this.declaringClass = declaringClass;
        this.name = name;
        this.modifiers = modifiers;
        this.accessLevel = accessLevel;
        this.type = type;
        this.getter = getter;
        this.setter = setter;
    }

    private TField(TClass<?> declaringClass, int wasmIndex) {
        this.declaringClass = declaringClass;
        this.wasmIndex = wasmIndex;
        this.name = wasmFieldName(declaringClass, wasmIndex);
        this.modifiers = wasmFieldModifiers(declaringClass, wasmIndex);
        this.type = wasmFieldType(declaringClass, wasmIndex);
    }

    public static TField[] wasmDeclaredFields(TClass<?> declaringClass) {
        if (!PlatformDetector.isWebAssembly()) {
            throw new UnsupportedOperationException();
        }
        TField[] result = new TField[wasmFieldCount(declaringClass)];
        for (int i = 0; i < result.length; ++i) {
            result[i] = new TField(declaringClass, i);
        }
        return result;
    }

    private static native int wasmFieldCount(TClass<?> declaringClass);

    private static native int wasmFieldModifiers(TClass<?> declaringClass, int index);

    private static native String wasmFieldName(TClass<?> declaringClass, int index);

    private static native TClass<?> wasmFieldType(TClass<?> declaringClass, int index);

    private static native Object wasmFieldGet(TClass<?> declaringClass, int index, Object instance);

    private static native void wasmFieldSet(TClass<?> declaringClass, int index, Object instance, Object value);

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
            return modifiers;
        }
        return Flags.getModifiers(modifiers, accessLevel);
    }

    public boolean isEnumConstant() {
        if (wasmIndex >= 0) {
            return (modifiers & JDK_ENUM) != 0;
        }
        return (modifiers & Flags.ENUM) != 0;
    }

    @Override
    public boolean isSynthetic() {
        if (wasmIndex >= 0) {
            return (modifiers & JDK_SYNTHETIC) != 0;
        }
        return (modifiers & Flags.SYNTHETIC) != 0;
    }

    public TClass<?> getType() {
        return type;
    }

    // JDK Field: equal when declared by the same class with the same name and type.
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TField)) {
            return false;
        }
        TField other = (TField) obj;
        return declaringClass == other.declaringClass && name.equals(other.name) && type == other.type;
    }

    @Override
    public int hashCode() {
        return declaringClass.getName().hashCode() ^ name.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(TModifier.toString(getModifiers()));
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(getType().getName()).append(' ').append(declaringClass.getName()).append(".").append(name);
        return sb.toString();
    }

    public Object get(Object obj) throws TIllegalArgumentException, TIllegalAccessException {
        if (PlatformDetector.isWebAssembly()) {
            checkWasmInstance(obj, true, obj);
            return wasmFieldGet(declaringClass, wasmIndex, obj);
        }
        if (getter == null) {
            throw new TIllegalAccessException();
        }
        checkInstance(obj);
        PlatformObject result = getter.get(Platform.getPlatformObject(obj));
        return Converter.toJava(result);
    }

    public void set(Object obj, Object value) throws TIllegalArgumentException, TIllegalAccessException {
        if (PlatformDetector.isWebAssembly()) {
            setWasm(obj, value, value == null ? "null value" : value.getClass().getName());
            return;
        }
        if (setter == null) {
            throw new TIllegalAccessException();
        }
        checkInstance(obj);
        setter.set(Platform.getPlatformObject(obj), Converter.fromJava(value));
    }

    public boolean getBoolean(Object obj) throws TIllegalArgumentException, TIllegalAccessException {
        return (Boolean) getAs(obj, "boolean");
    }

    public byte getByte(Object obj) throws TIllegalArgumentException, TIllegalAccessException {
        return (Byte) getAs(obj, "byte");
    }

    public char getChar(Object obj) throws TIllegalArgumentException, TIllegalAccessException {
        return (Character) getAs(obj, "char");
    }

    public short getShort(Object obj) throws TIllegalArgumentException, TIllegalAccessException {
        return (Short) getAs(obj, "short");
    }

    public int getInt(Object obj) throws TIllegalArgumentException, TIllegalAccessException {
        return (Integer) getAs(obj, "int");
    }

    public long getLong(Object obj) throws TIllegalArgumentException, TIllegalAccessException {
        return (Long) getAs(obj, "long");
    }

    public float getFloat(Object obj) throws TIllegalArgumentException, TIllegalAccessException {
        return (Float) getAs(obj, "float");
    }

    public double getDouble(Object obj) throws TIllegalArgumentException, TIllegalAccessException {
        return (Double) getAs(obj, "double");
    }

    public void setBoolean(Object obj, boolean z) throws TIllegalArgumentException, TIllegalAccessException {
        setAs(obj, z, "(boolean)" + z);
    }

    public void setByte(Object obj, byte b) throws TIllegalArgumentException, TIllegalAccessException {
        setAs(obj, b, "(byte)" + b);
    }

    public void setChar(Object obj, char c) throws TIllegalArgumentException, TIllegalAccessException {
        setAs(obj, c, "(char)" + c);
    }

    public void setShort(Object obj, short s) throws TIllegalArgumentException, TIllegalAccessException {
        setAs(obj, s, "(short)" + s);
    }

    public void setInt(Object obj, int i) throws TIllegalArgumentException, TIllegalAccessException {
        setAs(obj, i, "(int)" + i);
    }

    public void setLong(Object obj, long l) throws TIllegalArgumentException, TIllegalAccessException {
        setAs(obj, l, "(long)" + l);
    }

    public void setFloat(Object obj, float f) throws TIllegalArgumentException, TIllegalAccessException {
        setAs(obj, f, "(float)" + f);
    }

    public void setDouble(Object obj, double d) throws TIllegalArgumentException, TIllegalAccessException {
        setAs(obj, d, "(double)" + d);
    }

    // Typed accessors apply widening primitive conversion (JLS 5.1.2) only, as on the JDK.
    private Object getAs(Object obj, String target) throws TIllegalAccessException {
        Object value = get(obj);
        Object converted = type.isPrimitive() ? TConstructor.widen(target, value) : null;
        if (converted == null) {
            throw new TIllegalArgumentException("Attempt to get " + type.getName() + " field \""
                    + declaringClass.getName() + "." + name + "\" with illegal data type conversion to " + target);
        }
        return converted;
    }

    private void setAs(Object obj, Object boxed, String described) throws TIllegalAccessException {
        if (!PlatformDetector.isWebAssembly()) {
            set(obj, boxed);
            return;
        }
        if (!type.isPrimitive()) {
            checkWasmInstance(obj, false, boxed);
            checkWasmWritable(described);
            throw new TIllegalArgumentException(setFailure(described));
        }
        setWasm(obj, boxed, described);
    }

    private void setWasm(Object obj, Object value, String described) throws TIllegalAccessException {
        checkWasmInstance(obj, false, value);
        checkWasmWritable(described);
        Object converted;
        if (type.isPrimitive()) {
            converted = value == null ? null : TConstructor.widen(type.getName(), value);
        } else {
            converted = value == null || type.isInstance((TObject) value) ? value : null;
            if (value == null) {
                wasmFieldSet(declaringClass, wasmIndex, obj, null);
                return;
            }
        }
        if (converted == null) {
            throw new TIllegalArgumentException(setFailure(described));
        }
        wasmFieldSet(declaringClass, wasmIndex, obj, converted);
    }

    private void checkWasmWritable(String described) throws TIllegalAccessException {
        if ((modifiers & JDK_FINAL) != 0 && ((modifiers & JDK_STATIC) != 0 || !isAccessible())) {
            throw new TIllegalAccessException(setFailure(described));
        }
    }

    private void checkWasmInstance(Object obj, boolean reading, Object value) {
        if ((modifiers & JDK_STATIC) != 0) {
            return;
        }
        if (obj == null) {
            throw new NullPointerException();
        }
        if (!declaringClass.isInstance((TObject) obj)) {
            throw new TIllegalArgumentException(reading
                    ? "Can not get " + type.getName() + " field " + declaringClass.getName() + "." + name
                            + " on " + obj.getClass().getName()
                    : setFailure(obj.getClass().getName()));
        }
    }

    private String setFailure(String described) {
        return "Can not set " + ((modifiers & JDK_STATIC) != 0 ? "static " : "")
                + ((modifiers & JDK_FINAL) != 0 ? "final " : "") + type.getName() + " field "
                + declaringClass.getName() + "." + name + " to " + described;
    }

    private void checkInstance(Object obj) {
        if ((modifiers & Flags.STATIC) == 0) {
            if (obj == null) {
                throw new NullPointerException();
            }
            if (!declaringClass.isInstance((TObject) obj)) {
                throw new TIllegalArgumentException();
            }
        }
    }
}