/*
 *  Copyright 2013 Alexey Andreev.
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
package org.teavm.classlib.java.lang;

import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.backend.javascript.spi.GeneratedBy;
import org.teavm.backend.javascript.spi.InjectedBy;
import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.impl.reflection.Flags;
import org.teavm.classlib.impl.reflection.JSClass;
import org.teavm.classlib.impl.reflection.JSField;
import org.teavm.classlib.impl.reflection.JSMethodMember;
import org.teavm.classlib.java.lang.annotation.TAnnotation;
import org.teavm.classlib.java.lang.reflect.TAnnotatedElement;
import org.teavm.classlib.java.lang.reflect.TConstructor;
import org.teavm.classlib.java.lang.reflect.TField;
import org.teavm.classlib.java.lang.reflect.TInvocationTargetException;
import org.teavm.classlib.java.lang.reflect.TMethod;
import org.teavm.classlib.java.lang.reflect.TModifier;
import org.teavm.classlib.java.lang.reflect.TType;
import org.teavm.classlib.java.net.TURL;
import org.teavm.dependency.PluggableDependency;
import org.teavm.interop.Address;
import org.teavm.interop.DelegateTo;
import org.teavm.interop.NoSideEffects;
import org.teavm.interop.Unmanaged;
import org.teavm.jso.core.JSArray;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformClass;
import org.teavm.platform.PlatformSequence;
import org.teavm.runtime.Mutator;
import org.teavm.runtime.RuntimeClass;
import org.teavm.runtime.RuntimeObject;

public class TClass<T> extends TObject implements TAnnotatedElement, TType {
    String name;
    String simpleName;
    String canonicalName;
    private PlatformClass platformClass;
    private TAnnotation[] annotationsCache;
    private Map<TClass<?>, TAnnotation> annotationsByType;
    private TField[] declaredFields;
    private TField[] fields;
    private TConstructor<T>[] declaredConstructors;
    private TMethod[] declaredMethods;
    private static boolean reflectionInitialized;

    private TClass(PlatformClass platformClass) {
        this.platformClass = platformClass;
        platformClass.setJavaClass(Platform.getPlatformObject(this));
    }

    public static TClass<?> getClass(PlatformClass cls) {
        if (cls == null) {
            return null;
        }
        TClass<?> result = (TClass<?>) (Object) Platform.asJavaClass(cls.getJavaClass());
        if (result == null) {
            result = new TClass<>(cls);
        }
        return result;
    }

    @Override
    public String toString() {
        return (isInterface() ? "interface " : (isPrimitive() ? "" : "class ")) + getName();
    }

    private String obfuscatedToString() {
        return "javaClass@" + identity();
    }

    public PlatformClass getPlatformClass() {
        return platformClass;
    }

    @DelegateTo("isInstanceLowLevel")
    public boolean isInstance(TObject obj) {
        return Platform.isInstance(Platform.getPlatformObject(obj), platformClass);
    }

    @Unmanaged
    private boolean isInstanceLowLevel(RuntimeObject obj) {
        return obj != null && isAssignableFromLowLevel(RuntimeClass.getClass(obj));
    }

    @DelegateTo("isAssignableFromLowLevel")
    public boolean isAssignableFrom(TClass<?> obj) {
        return Platform.isAssignable(obj.getPlatformClass(), platformClass);
    }

    @Unmanaged
    private boolean isAssignableFromLowLevel(RuntimeClass other) {
        return Address.ofObject(this).<RuntimeClass>toStructure().isSupertypeOf.apply(other);
    }

    @Unmanaged
    public String getName() {
        if (PlatformDetector.isLowLevel()) {
            String result = getNameCache(this);
            if (result == null) {
                result = Platform.getName(platformClass);
                if (result == null) {
                    if (isArray()) {
                        TClass<?> componentType = getComponentType();
                        String componentName = componentType.getName();
                        if (componentName != null) {
                            result = componentType.isArray() ? "[" + componentName : "[L" + componentName + ";";
                        }
                    }
                }
                setNameCache(this, result);
            }
            return result;
        } else {
            if (name == null) {
                name = Platform.getName(platformClass);
            }
            return name;
        }
    }

    public String getSimpleName() {
        String simpleName = getSimpleNameCache(this);
        if (simpleName == null) {
            if (isArray()) {
                simpleName = getComponentType().getSimpleName() + "[]";
            } else if (getEnclosingClass() != null) {
                simpleName = Platform.getSimpleName(platformClass);
                if (simpleName == null) {
                    simpleName = "";
                }
            } else {
                String name = Platform.getName(platformClass);
                int lastDollar = name.lastIndexOf('$');
                if (lastDollar != -1) {
                    name = name.substring(lastDollar + 1);
                    if (name.charAt(0) >= '0' && name.charAt(0) <= '9') {
                        name = "";
                    }
                } else {
                    int lastDot = name.lastIndexOf('.');
                    if (lastDot != -1) {
                        name = name.substring(lastDot + 1);
                    }
                }
                simpleName = name;
            }
            setSimpleNameCache(this, simpleName);
        }
        return simpleName;
    }

    @DelegateTo("getSimpleNameCacheLowLevel")
    private static String getSimpleNameCache(TClass<?> self) {
        return self.simpleName;
    }

    @Unmanaged
    @PluggableDependency(ClassDependencyListener.class)
    private static RuntimeObject getSimpleNameCacheLowLevel(RuntimeClass self) {
        return self.simpleNameCache;
    }

    @DelegateTo("setSimpleNameCacheLowLevel")
    private static void setSimpleNameCache(TClass<?> self, String value) {
        self.simpleName = value;
    }

    @Unmanaged
    private static void setSimpleNameCacheLowLevel(RuntimeClass self, RuntimeObject object) {
        self.simpleNameCache = object;
    }

    @DelegateTo("getNameCacheLowLevel")
    private static String getNameCache(TClass<?> self) {
        return self.name;
    }

    @Unmanaged
    @PluggableDependency(ClassDependencyListener.class)
    private static RuntimeObject getNameCacheLowLevel(RuntimeClass self) {
        return self.nameCache;
    }

    @DelegateTo("setNameCacheLowLevel")
    private static void setNameCache(TClass<?> self, String value) {
        self.name = value;
    }

    @Unmanaged
    private static void setNameCacheLowLevel(RuntimeClass self, RuntimeObject object) {
        self.nameCache = object;
    }

    public String getCanonicalName() {
        String result = getCanonicalNameCache();
        if (result == null) {
            if (isArray()) {
                String componentName = getComponentType().getCanonicalName();
                if (componentName == null) {
                    return null;
                }
                result = componentName + "[]";
            } else if (getEnclosingClass() != null) {
                if (getDeclaringClass() == null || isSynthetic()) {
                    return null;
                }
                String enclosingName = getDeclaringClass().getCanonicalName();
                if (enclosingName == null) {
                    return null;
                }
                result = enclosingName + "." + getSimpleName();
            } else {
                result = getName();
            }
            setCanonicalNameCache(result);
        }
        return result;
    }

    private boolean isSynthetic() {
        if (PlatformDetector.isJavaScript()) {
            return (platformClass.getMetadata().getAccessLevel() & Flags.SYNTHETIC) != 0;
        } else {
            return (RuntimeClass.getClass(Address.ofObject(this).toStructure()).flags & RuntimeClass.SYNTHETIC) != 0;
        }
    }

    @DelegateTo("getCanonicalNameCacheLowLevel")
    private String getCanonicalNameCache() {
        return canonicalName;
    }

    @Unmanaged
    @PluggableDependency(ClassDependencyListener.class)
    private RuntimeObject getCanonicalNameCacheLowLevel() {
        return Address.ofObject(this).<RuntimeClass>toStructure().canonicalName;
    }

    @DelegateTo("setCanonicalNameCacheLowLevel")
    private void setCanonicalNameCache(String value) {
        canonicalName = value;
    }

    @Unmanaged
    private void setCanonicalNameCacheLowLevel(RuntimeObject object) {
        Address.ofObject(this).<RuntimeClass>toStructure().canonicalName = object;
    }

    // JDK Class.getTypeName: arrays read as their component type name followed by "[]" per dimension.
    public String getTypeName() {
        if (isArray()) {
            TClass<?> component = this;
            StringBuilder dimensions = new StringBuilder();
            while (component.isArray()) {
                dimensions.append("[]");
                component = component.getComponentType();
            }
            return component.getName() + dimensions;
        }
        return getName();
    }

    public boolean isPrimitive() {
        return Platform.isPrimitive(platformClass);
    }

    public boolean isArray() {
        return Platform.getArrayItem(platformClass) != null;
    }

    public boolean isEnum() {
        return Platform.isEnum(platformClass);
    }

    public boolean isInterface() {
        if (PlatformDetector.isWebAssembly()) {
            return isInterfaceLowLevel();
        }
        return (platformClass.getMetadata().getFlags() & Flags.INTERFACE) != 0;
    }

    @Unmanaged
    private boolean isInterfaceLowLevel() {
        return (Address.ofObject(this).<RuntimeClass>toStructure().flags & RuntimeClass.INTERFACE) != 0;
    }

    public boolean isLocalClass() {
        return (platformClass.getMetadata().getFlags() & Flags.SYNTHETIC) != 0
                && getEnclosingClass() != null;
    }

    public boolean isMemberClass() {
        return getDeclaringClass() != null;
    }

    @PluggableDependency(ClassGenerator.class)
    public TClass<?> getComponentType() {
        return getClass(Platform.getArrayItem(platformClass));
    }

    public TField[] getDeclaredFields() throws TSecurityException {
        if (isPrimitive() || isArray()) {
            return new TField[0];
        }
        // On WebAssembly a Class is the runtime class record, which has no slots for these Java-level caches,
        // so every call builds fresh copies (as the JDK returns copies anyway).
        if (PlatformDetector.isWebAssembly()) {
            return TField.wasmDeclaredFields(this);
        }
        if (declaredFields == null) {
            initReflection();
            JSClass jsClass = (JSClass) getPlatformClass().getMetadata();
            JSArray<JSField> jsFields = jsClass.getFields();
            declaredFields = new TField[jsFields.getLength()];
            for (int i = 0; i < jsFields.getLength(); ++i) {
                JSField jsField = jsFields.get(i);
                declaredFields[i] = new TField(this, jsField.getName(), jsField.getModifiers(),
                        jsField.getAccessLevel(), TClass.getClass(jsField.getType()), jsField.getGetter(),
                        jsField.getSetter());
            }
        }
        return declaredFields.clone();
    }

    private static void initReflection() {
        if (!reflectionInitialized) {
            reflectionInitialized = true;
            createMetadata();
        }
    }

    @GeneratedBy(ClassGenerator.class)
    @NoSideEffects
    private static native void createMetadata();

    public TField[] getFields() throws TSecurityException {
        if (isPrimitive() || isArray()) {
            return new TField[0];
        }

        TField[] result = PlatformDetector.isWebAssembly() ? null : fields;
        if (result == null) {
            List<TField> fieldList = new ArrayList<>();
            TClass<?> cls = this;

            Set<TClass<?>> visitedInterfaces = new HashSet<>();
            if (cls.isInterface()) {
                getFieldsOfInterfaces(cls, fieldList, visitedInterfaces);
            } else {
                while (cls != null) {
                    for (TField field : cls.getDeclaredFields()) {
                        if (Modifier.isPublic(field.getModifiers())) {
                            fieldList.add(field);
                        }
                    }
                    for (TClass<?> iface : cls.getInterfaces()) {
                        getFieldsOfInterfaces(iface, fieldList, visitedInterfaces);
                    }
                    cls = cls.getSuperclass();
                }
            }

            result = fieldList.toArray(new TField[fieldList.size()]);
            if (!PlatformDetector.isWebAssembly()) {
                fields = result;
            }
        }
        return result.clone();
    }

    public TField getDeclaredField(String name) throws TNoSuchFieldException {
        for (TField field : getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        throw new TNoSuchFieldException(name);
    }

    public TField getField(String name) throws TNoSuchFieldException {
        TField result = findField(name, new HashSet<>());
        if (result == null) {
            throw new TNoSuchFieldException(name);
        }
        return result;
    }

    // JLS 8.3 / JDK Class.getField: this class's public fields, then superinterfaces, then the superclass.
    private TField findField(String name, Set<TClass<?>> visited) {
        if (!visited.add(this)) {
            return null;
        }

        for (TField field : getDeclaredFields()) {
            if (TModifier.isPublic(field.getModifiers()) && field.getName().equals(name)) {
                return field;
            }
        }

        for (TClass<?> iface : getInterfaces()) {
            TField field = iface.findField(name, visited);
            if (field != null) {
                return field;
            }
        }

        TClass<?> superclass = getSuperclass();
        if (superclass != null) {
            TField field = superclass.findField(name, visited);
            if (field != null) {
                return field;
            }
        }

        return null;
    }

    @InjectedBy(ClassGenerator.class)
    @PluggableDependency(ClassGenerator.class)
    public native <T> T newEmptyInstance();

    @SuppressWarnings({ "raw", "unchecked" })
    public TConstructor<?>[] getDeclaredConstructors() throws TSecurityException {
        if (isPrimitive() || isArray()) {
            return new TConstructor<?>[0];
        }

        if (PlatformDetector.isWebAssembly()) {
            return TConstructor.wasmDeclaredConstructors(this);
        }
        if (declaredConstructors == null) {
            initReflection();
            JSClass jsClass = (JSClass) getPlatformClass().getMetadata();
            JSArray<JSMethodMember> jsMethods = jsClass.getMethods();
            declaredConstructors = new TConstructor[jsMethods.getLength()];
            int count = 0;
            for (int i = 0; i < jsMethods.getLength(); ++i) {
                JSMethodMember jsMethod = jsMethods.get(i);
                if (!jsMethod.getName().equals("<init>")) {
                    continue;
                }
                PlatformSequence<PlatformClass> jsParameterTypes = jsMethod.getParameterTypes();
                TClass<?>[] parameterTypes = new TClass<?>[jsParameterTypes.getLength()];
                for (int j = 0; j < parameterTypes.length; ++j) {
                    parameterTypes[j] = getClass(jsParameterTypes.get(j));
                }
                declaredConstructors[count++] = new TConstructor<T>(this, jsMethod.getName(),
                        jsMethod.getModifiers(), jsMethod.getAccessLevel(), parameterTypes,
                        jsMethod.getCallable());
            }
            declaredConstructors = Arrays.copyOf(declaredConstructors, count);
        }
        return declaredConstructors.clone();
    }

    public TConstructor<?>[] getConstructors() throws TSecurityException {
        TConstructor<?>[] declaredConstructors = getDeclaredConstructors();
        TConstructor<?>[] constructors = new TConstructor<?>[declaredConstructors.length];

        int sz = 0;
        for (TConstructor<?> constructor : declaredConstructors) {
            if (TModifier.isPublic(constructor.getModifiers())) {
                constructors[sz++] = constructor;
            }
        }

        if (sz < constructors.length) {
            constructors = Arrays.copyOf(constructors, sz);
        }

        return constructors;
    }

    @SuppressWarnings({ "raw", "unchecked" })
    public TConstructor<T> getDeclaredConstructor(TClass<?>... parameterTypes)
            throws TSecurityException, TNoSuchMethodException {
        for (TConstructor<?> constructor : getDeclaredConstructors()) {
            if (Arrays.equals(constructor.getParameterTypes(), parameterTypes)) {
                return (TConstructor<T>) constructor;
            }
        }
        throw new TNoSuchMethodException(constructorToString(parameterTypes));
    }

    private String constructorToString(TClass<?>[] parameterTypes) {
        return methodToString("<init>", parameterTypes);
    }

    // JDK Class.methodToString, used in NoSuchMethodException messages.
    private String methodToString(String name, TClass<?>[] parameterTypes) {
        StringBuilder sb = new StringBuilder(getName()).append('.').append(name).append('(');
        if (parameterTypes != null) {
            for (int i = 0; i < parameterTypes.length; ++i) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(parameterTypes[i] == null ? "null" : parameterTypes[i].getName());
            }
        }
        return sb.append(')').toString();
    }

    @SuppressWarnings({ "raw", "unchecked" })
    public TConstructor<T> getConstructor(TClass<?>... parameterTypes)
            throws TSecurityException, TNoSuchMethodException {
        for (TConstructor<?> constructor : getDeclaredConstructors()) {
            if (TModifier.isPublic(constructor.getModifiers())
                    && Arrays.equals(constructor.getParameterTypes(), parameterTypes)) {
                return (TConstructor<T>) constructor;
            }
        }
        throw new TNoSuchMethodException(constructorToString(parameterTypes));
    }

    private static void getFieldsOfInterfaces(TClass<?> iface, List<TField> fields, Set<TClass<?>> visited) {
        if (!visited.add(iface)) {
            return;
        }
        for (TField field : iface.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                fields.add(field);
            }
        }
        for (TClass<?> superInterface : iface.getInterfaces()) {
            getFieldsOfInterfaces(superInterface, fields, visited);
        }
    }

    public TMethod[] getDeclaredMethods() {
        if (isPrimitive() || isArray()) {
            return new TMethod[0];
        }
        if (PlatformDetector.isWebAssembly()) {
            return TMethod.wasmDeclaredMethods(this);
        }
        if (declaredMethods == null) {
            initReflection();
            JSClass jsClass = (JSClass) getPlatformClass().getMetadata();
            JSArray<JSMethodMember> jsMethods = jsClass.getMethods();
            declaredMethods = new TMethod[jsMethods.getLength()];
            int count = 0;
            for (int i = 0; i < jsMethods.getLength(); ++i) {
                JSMethodMember jsMethod = jsMethods.get(i);
                if (jsMethod.getName().equals("<init>") || jsMethod.getName().equals("<clinit>")) {
                    continue;
                }
                PlatformSequence<PlatformClass> jsParameterTypes = jsMethod.getParameterTypes();
                TClass<?>[] parameterTypes = new TClass<?>[jsParameterTypes.getLength()];
                for (int j = 0; j < parameterTypes.length; ++j) {
                    parameterTypes[j] = getClass(jsParameterTypes.get(j));
                }
                TClass<?> returnType = getClass(jsMethod.getReturnType());
                declaredMethods[count++] = new TMethod(this, jsMethod.getName(), jsMethod.getModifiers(),
                        jsMethod.getAccessLevel(), returnType, parameterTypes, jsMethod.getCallable());
            }
            declaredMethods = Arrays.copyOf(declaredMethods, count);
        }
        return declaredMethods.clone();
    }

    // JDK Class.searchMethods: among declared methods with this signature, the one with the most specific
    // return type (several exist only when the compiler emitted bridges).
    public TMethod getDeclaredMethod(String name, TClass<?>... parameterTypes) throws TNoSuchMethodException,
            TSecurityException {
        TMethod result = null;
        for (TMethod method : getDeclaredMethods()) {
            if (method.getName().equals(name) && sameParameters(method, parameterTypes)
                    && (result == null || (result.getReturnType() != method.getReturnType()
                    && result.getReturnType().isAssignableFrom(method.getReturnType())))) {
                result = method;
            }
        }
        if (result == null) {
            throw new TNoSuchMethodException(methodToString(name, parameterTypes));
        }
        return result;
    }

    private static boolean sameParameters(TMethod method, TClass<?>[] parameterTypes) {
        TClass<?>[] actual = method.getParameterTypes();
        int expected = parameterTypes == null ? 0 : parameterTypes.length;
        if (actual.length != expected) {
            return false;
        }
        for (int i = 0; i < expected; ++i) {
            if (actual[i] != parameterTypes[i]) {
                return false;
            }
        }
        return true;
    }

    // JDK Class.privateGetPublicMethods: public declared methods, then the superclass's public methods, then the
    // non-static public methods of direct superinterfaces, merged per PublicMethods.MethodList.merge.
    public TMethod[] getMethods() throws TSecurityException {
        List<TMethod> result = new ArrayList<>();
        for (List<TMethod> methods : publicMethods().values()) {
            result.addAll(methods);
        }
        return result.toArray(new TMethod[result.size()]);
    }

    private Map<String, List<TMethod>> publicMethods() {
        Map<String, List<TMethod>> methods = new LinkedHashMap<>();
        for (TMethod method : getDeclaredMethods()) {
            if (TModifier.isPublic(method.getModifiers())) {
                mergeInto(methods, method);
            }
        }
        TClass<?> superclass = getSuperclass();
        if (superclass != null) {
            for (List<TMethod> inherited : superclass.publicMethods().values()) {
                for (TMethod method : inherited) {
                    mergeInto(methods, method);
                }
            }
        }
        for (TClass<?> iface : getInterfaces()) {
            for (List<TMethod> inherited : iface.publicMethods().values()) {
                for (TMethod method : inherited) {
                    if (!TModifier.isStatic(method.getModifiers())) {
                        mergeInto(methods, method);
                    }
                }
            }
        }
        return methods;
    }

    private static void mergeInto(Map<String, List<TMethod>> methods, TMethod method) {
        StringBuilder key = new StringBuilder(method.getName()).append('(');
        for (TClass<?> parameter : method.getParameterTypes()) {
            key.append(parameter.getName()).append(',');
        }
        methods.computeIfAbsent(key.toString(), k -> new ArrayList<>());
        merge(methods.get(key.toString()), method);
    }

    // JDK PublicMethods.MethodList.merge for methods with the same name and parameter types: of two methods with
    // the same return type, the one declared by the more specific type wins, and class methods win over interface
    // methods.
    private static List<TMethod> merge(List<TMethod> list, TMethod method) {
        if (list == null) {
            list = new ArrayList<>();
        }
        TClass<?> declaring = method.getDeclaringClass();
        for (int i = 0; i < list.size(); ++i) {
            TMethod existing = list.get(i);
            if (existing.getReturnType() != method.getReturnType()) {
                continue;
            }
            TClass<?> existingDeclaring = existing.getDeclaringClass();
            if (declaring.isInterface() == existingDeclaring.isInterface()) {
                if (declaring.isAssignableFrom(existingDeclaring)) {
                    return list;
                }
                if (existingDeclaring.isAssignableFrom(declaring)) {
                    list.set(i, method);
                    return list;
                }
            } else if (declaring.isInterface()) {
                return list;
            } else {
                list.set(i, method);
                return list;
            }
        }
        list.add(method);
        return list;
    }

    // JDK Class.getMethod: getMethodsRecursive, then the candidate with the most specific return type.
    public TMethod getMethod(String name, TClass<?>... parameterTypes) throws TNoSuchMethodException,
            TSecurityException {
        List<TMethod> candidates = getMethodsRecursive(name, parameterTypes, true);
        if (candidates == null) {
            throw new TNoSuchMethodException(methodToString(name, parameterTypes));
        }
        TMethod result = candidates.get(0);
        for (int i = 1; i < candidates.size(); ++i) {
            TMethod candidate = candidates.get(i);
            if (candidate.getReturnType() != result.getReturnType()
                    && result.getReturnType().isAssignableFrom(candidate.getReturnType())) {
                result = candidate;
            }
        }
        return result;
    }

    private List<TMethod> getMethodsRecursive(String name, TClass<?>[] parameterTypes, boolean includeStatic) {
        List<TMethod> result = null;
        for (TMethod method : getDeclaredMethods()) {
            if (TModifier.isPublic(method.getModifiers()) && method.getName().equals(name)
                    && sameParameters(method, parameterTypes)
                    && (includeStatic || !TModifier.isStatic(method.getModifiers()))) {
                if (result == null) {
                    result = new ArrayList<>();
                }
                result.add(method);
            }
        }
        if (result != null) {
            return result;
        }
        TClass<?> superclass = getSuperclass();
        if (superclass != null) {
            result = superclass.getMethodsRecursive(name, parameterTypes, includeStatic);
        }
        for (TClass<?> iface : getInterfaces()) {
            List<TMethod> inherited = iface.getMethodsRecursive(name, parameterTypes, false);
            if (inherited != null) {
                for (TMethod method : inherited) {
                    result = merge(result, method);
                }
            }
        }
        return result;
    }
    public int getModifiers() {
        int flags = platformClass.getMetadata().getFlags();
        int accessLevel = platformClass.getMetadata().getAccessLevel();
        return Flags.getModifiers(flags, accessLevel);
    }

    public boolean desiredAssertionStatus() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @PluggableDependency(ClassGenerator.class)
    public TClass<? super T> getSuperclass() {
        return (TClass<? super T>) getClass(platformClass.getMetadata().getSuperclass());
    }

    @SuppressWarnings("unchecked")
    @PluggableDependency(ClassGenerator.class)
    public TClass<? super T>[] getInterfaces() {
        if (PlatformDetector.isLowLevel()) {
            TClass<? super T>[] result = (TClass<? super T>[]) new TClass<?>[interfaceCount(this)];
            for (int i = 0; i < result.length; ++i) {
                result[i] = (TClass<? super T>) interfaceAt(this, i);
            }
            return result;
        }
        PlatformSequence<PlatformClass> supertypes = platformClass.getMetadata().getSupertypes();

        TClass<? super T>[] filteredSupertypes = (TClass<? super T>[]) new TClass<?>[supertypes.getLength()];
        int j = 0;
        for (int i = 0; i < supertypes.getLength(); ++i) {
            if (supertypes.get(i) != platformClass.getMetadata().getSuperclass()) {
                filteredSupertypes[j++] = (TClass<? super T>) getClass(supertypes.get(i));
            }
        }

        if (filteredSupertypes.length > j) {
            filteredSupertypes = Arrays.copyOf(filteredSupertypes, j);
        }
        return filteredSupertypes;
    }

    @SuppressWarnings("unchecked")
    public T[] getEnumConstants() {
        if (!isEnum()) {
            return null;
        }
        Platform.initClass(platformClass);
        return (T[]) Platform.getEnumConstants(platformClass).clone();
    }

    @SuppressWarnings("unchecked")
    public T cast(TObject obj) {
        if (obj != null && !isAssignableFrom((TClass<?>) (Object) obj.getClass())) {
            throw new TClassCastException(obj.getClass().getName() + " is not subtype of " + getName());
        }
        return (T) obj;
    }

    public TClassLoader getClassLoader() {
        return TClassLoader.getSystemClassLoader();
    }

    public static TClass<?> forName(TString name) throws TClassNotFoundException {
        if (PlatformDetector.isWebAssembly()) {
            TClass<?> found = lookupLowLevel(name.toString());
            if (found == null) {
                throw new TClassNotFoundException(name.toString());
            }
            found.initialize();
            return found;
        }
        PlatformClass cls = Platform.lookupClass(name.toString());
        if (cls == null) {
            throw new TClassNotFoundException();
        }
        return getClass(cls);
    }

    // WebAssembly: search the module's runtime class table, which holds a record for every
    // class compiled into the module (TeaVM is closed-world, so a class that was never compiled
    // in cannot be found, as with any AOT compiler). Primitive types have no binary name and are
    // never returned by Class.forName.
    private static TClass<?> lookupLowLevel(String name) {
        int count = Mutator.getClassCount();
        for (int i = 0; i < count; ++i) {
            TClass<?> cls = classAt(i);
            if (!cls.isPrimitive() && name.equals(cls.getName())) {
                return cls;
            }
        }
        return null;
    }

    @DelegateTo("interfaceCountLowLevel")
    private static int interfaceCount(TClass<?> self) {
        return 0;
    }

    @Unmanaged
    @SuppressWarnings("unused")
    private static int interfaceCountLowLevel(RuntimeClass self) {
        return self.superinterfaceCount;
    }

    @DelegateTo("interfaceAtLowLevel")
    @PluggableDependency(ClassDependencyListener.class)
    private static TClass<?> interfaceAt(TClass<?> self, int index) {
        return null;
    }

    @Unmanaged
    @SuppressWarnings("unused")
    @PluggableDependency(ClassDependencyListener.class)
    private static RuntimeClass interfaceAtLowLevel(RuntimeClass self, int index) {
        return self.superinterfaces.toAddress().add(Address.sizeOf() * index).getAddress().toStructure();
    }

    @DelegateTo("classAtLowLevel")
    @PluggableDependency(ClassDependencyListener.class)
    private static TClass<?> classAt(int index) {
        return null;
    }

    @Unmanaged
    @SuppressWarnings("unused")
    @PluggableDependency(ClassDependencyListener.class)
    private static RuntimeClass classAtLowLevel(int index) {
        return Mutator.getClasses().add(Address.sizeOf() * index).getAddress().toStructure();
    }

    @SuppressWarnings("unused")
    public static TClass<?> forName(TString name, boolean initialize, TClassLoader loader)
            throws TClassNotFoundException {
        if (PlatformDetector.isWebAssembly()) {
            TClass<?> found = lookupLowLevel(name.toString());
            if (found == null) {
                throw new TClassNotFoundException(name.toString());
            }
            if (initialize) {
                found.initialize();
            }
            return found;
        }
        return forName(name);
    }

    @PluggableDependency(ClassDependencyListener.class)
    void initialize() {
        Platform.initClass(platformClass);
    }

    @SuppressWarnings({ "unchecked", "unused" })
    public T newInstance() throws TInstantiationException, TIllegalAccessException {
        if (PlatformDetector.isWebAssembly()) {
            return newInstanceViaConstructor();
        }
        Object instance = Platform.newInstance(platformClass);
        if (instance == null) {
            throw new TInstantiationException();
        }
        return (T) instance;
    }

    // JDK semantics: the no-arg constructor runs and anything it throws, checked or not, propagates as is.
    private T newInstanceViaConstructor() throws TInstantiationException, TIllegalAccessException {
        if (isPrimitive() || isArray() || isInterface()) {
            throw new TInstantiationException(getName());
        }
        TConstructor<T> constructor;
        try {
            constructor = getDeclaredConstructor();
        } catch (TNoSuchMethodException e) {
            TInstantiationException failure = new TInstantiationException(getName());
            failure.initCause(e);
            throw failure;
        }
        try {
            return constructor.newInstance();
        } catch (TInvocationTargetException e) {
            throw TClass.<RuntimeException>rethrow(e.getTargetException());
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E rethrow(Object throwable) throws E {
        throw (E) throwable;
    }

    public TClass<?> getDeclaringClass() {
        PlatformClass result = Platform.getDeclaringClass(getPlatformClass());
        return result != null ? getClass(result) : null;
    }

    public TClass<?> getEnclosingClass() {
        PlatformClass result = Platform.getEnclosingClass(getPlatformClass());
        return result != null ? getClass(result) : null;
    }

    @SuppressWarnings("unchecked")
    public <U> TClass<? extends U> asSubclass(TClass<U> clazz) {
        if (!clazz.isAssignableFrom(this)) {
            throw new TClassCastException();
        }
        return (TClass<? extends U>) this;
    }

    @Override
    public boolean isAnnotationPresent(TClass<? extends TAnnotation> annotationClass) {
        ensureAnnotationsByType();
        return annotationsByType.containsKey(annotationClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends TAnnotation> S getAnnotation(TClass<S> annotationClass) {
        ensureAnnotationsByType();
        return (S) annotationsByType.get(annotationClass);
    }

    @Override
    public TAnnotation[] getAnnotations() {
        if (annotationsCache == null) {
            annotationsCache = (TAnnotation[]) Platform.getAnnotations(getPlatformClass());
        }
        return annotationsCache.clone();
    }

    @Override
    public TAnnotation[] getDeclaredAnnotations() {
        return getAnnotations();
    }

    private void ensureAnnotationsByType() {
        if (annotationsByType != null) {
            return;
        }
        annotationsByType = new HashMap<>();
        for (TAnnotation annot : getAnnotations()) {
            annotationsByType.put((TClass<?>) (Object) annot.annotationType(), annot);
        }
    }

    public InputStream getResourceAsStream(String name) {
        return getClassLoader().getResourceAsStream(resolveResourceName(name));
    }

    public TURL getResource(String name) {
        return getClassLoader().getResource(resolveResourceName(name));
    }

    private String resolveResourceName(String name) {
        if (name.startsWith("/")) {
            return name.substring(1);
        }

        TClass<?> cls = this;
        while (cls.isArray()) {
            cls = cls.getComponentType();
        }
        String prefix = cls.getName().toString();
        int index = prefix.lastIndexOf('.');
        if (index >= 0) {
            name = prefix.substring(0, index + 1).replace('.', '/') + name;
        }
        return name;
    }

    public TPackage getPackage() {
        String name = (String) (Object) getName();
        name = name.substring(0, name.lastIndexOf('.') + 1);
        return TPackage.getPackage(name);
    }
}
