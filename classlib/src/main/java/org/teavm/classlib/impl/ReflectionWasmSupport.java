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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.DependencyTypeFilter;
import org.teavm.dependency.FieldDependency;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.AccessLevel;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MemberReader;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ChooseEmitter;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.lowlevel.Characteristics;

/**
 * WebAssembly backing for {@code java.lang.reflect.Constructor}. Every class whose {@code Class} value
 * reaches {@code getDeclaredConstructors} gets all of its declared constructors, as on the JDK. The
 * metadata (count, modifiers, parameter types) and the invocation are generated as IR when dependency
 * analysis completes, so the constructors they call are linked like any other call.
 */
public class ReflectionWasmSupport extends AbstractDependencyListener {
    private static final String CONSTRUCTOR = "java.lang.reflect.Constructor";
    private static final ValueType CLASS = ValueType.object("java.lang.Class");
    private static final ValueType OBJECT = ValueType.object("java.lang.Object");
    private static final MethodReference DECLARED = new MethodReference(CONSTRUCTOR, "wasmDeclaredConstructors",
            CLASS, ValueType.arrayOf(ValueType.object(CONSTRUCTOR)));
    private static final MethodReference COUNT = new MethodReference(CONSTRUCTOR, "wasmConstructorCount",
            CLASS, ValueType.INTEGER);
    private static final MethodReference MODIFIERS = new MethodReference(CONSTRUCTOR, "wasmConstructorModifiers",
            CLASS, ValueType.INTEGER, ValueType.INTEGER);
    private static final MethodReference PARAMETER_TYPES = new MethodReference(CONSTRUCTOR,
            "wasmConstructorParameterTypes", CLASS, ValueType.INTEGER, ValueType.arrayOf(CLASS));
    private static final MethodReference NEW_INSTANCE = new MethodReference(CONSTRUCTOR, "wasmNewInstance",
            CLASS, ValueType.INTEGER, ValueType.arrayOf(OBJECT), OBJECT);
    private static final MethodReference NEW_DEFAULT_INSTANCE = new MethodReference(CONSTRUCTOR,
            "wasmNewDefaultInstance", CLASS, ValueType.INTEGER, OBJECT);
    private static final String FIELD = "java.lang.reflect.Field";
    private static final MethodReference FIELDS_DECLARED = new MethodReference(FIELD, "wasmDeclaredFields",
            CLASS, ValueType.arrayOf(ValueType.object(FIELD)));
    private static final MethodReference FIELD_COUNT = new MethodReference(FIELD, "wasmFieldCount",
            CLASS, ValueType.INTEGER);
    private static final MethodReference FIELD_MODIFIERS = new MethodReference(FIELD, "wasmFieldModifiers",
            CLASS, ValueType.INTEGER, ValueType.INTEGER);
    private static final MethodReference FIELD_NAME = new MethodReference(FIELD, "wasmFieldName",
            CLASS, ValueType.INTEGER, ValueType.object("java.lang.String"));
    private static final MethodReference FIELD_TYPE = new MethodReference(FIELD, "wasmFieldType",
            CLASS, ValueType.INTEGER, CLASS);
    private static final MethodReference FIELD_GET = new MethodReference(FIELD, "wasmFieldGet",
            CLASS, ValueType.INTEGER, OBJECT, OBJECT);
    private static final MethodReference FIELD_SET = new MethodReference(FIELD, "wasmFieldSet",
            CLASS, ValueType.INTEGER, OBJECT, OBJECT, ValueType.VOID);
    private static final MethodReference CONSTRUCTOR_EXCEPTION_TYPES = new MethodReference(CONSTRUCTOR,
            "wasmConstructorExceptionTypes", CLASS, ValueType.INTEGER, ValueType.arrayOf(CLASS));
    private static final String METHOD = "java.lang.reflect.Method";
    private static final MethodReference METHODS_DECLARED = new MethodReference(METHOD, "wasmDeclaredMethods",
            CLASS, ValueType.arrayOf(ValueType.object(METHOD)));
    private static final MethodReference METHOD_COUNT = new MethodReference(METHOD, "wasmMethodCount",
            CLASS, ValueType.INTEGER);
    private static final MethodReference METHOD_MODIFIERS = new MethodReference(METHOD, "wasmMethodModifiers",
            CLASS, ValueType.INTEGER, ValueType.INTEGER);
    private static final MethodReference METHOD_NAME = new MethodReference(METHOD, "wasmMethodName",
            CLASS, ValueType.INTEGER, ValueType.object("java.lang.String"));
    private static final MethodReference METHOD_RETURN_TYPE = new MethodReference(METHOD, "wasmMethodReturnType",
            CLASS, ValueType.INTEGER, CLASS);
    private static final MethodReference METHOD_PARAMETER_TYPES = new MethodReference(METHOD,
            "wasmMethodParameterTypes", CLASS, ValueType.INTEGER, ValueType.arrayOf(CLASS));
    private static final MethodReference METHOD_EXCEPTION_TYPES = new MethodReference(METHOD,
            "wasmMethodExceptionTypes", CLASS, ValueType.INTEGER, ValueType.arrayOf(CLASS));
    private static final MethodReference METHOD_INVOKE = new MethodReference(METHOD, "wasmInvoke",
            CLASS, ValueType.INTEGER, OBJECT, ValueType.arrayOf(OBJECT), OBJECT);
    private static final MethodReference GET_INTERFACES = new MethodReference("java.lang.Class", "getInterfaces",
            ValueType.arrayOf(CLASS));
    // Mirrors TConstructor.WASM_CLASS_ABSTRACT / WASM_CLASS_ENUM.
    private static final int CLASS_ABSTRACT = 1 << 20;
    private static final int CLASS_ENUM = 1 << 21;

    private final Map<String, List<MethodReader>> constructors = new LinkedHashMap<>();
    private Characteristics characteristics;
    private MethodDependency newInstanceDep;
    private MethodDependency parameterTypesDep;
    private final Map<String, List<FieldReader>> fields = new LinkedHashMap<>();
    private MethodDependency fieldTypeDep;
    private MethodDependency fieldGetDep;
    private MethodDependency fieldSetDep;
    private MethodDependency constructorExceptionTypesDep;
    private final Map<String, List<MethodReader>> methods = new LinkedHashMap<>();
    private final Map<MethodReference, MethodDependency> classResults = new LinkedHashMap<>();
    private MethodDependency invokeDep;
    // Receiver classes that reached Method.invoke, indexed by each of their supertypes (arrays under Object).
    private final Map<String, List<String>> receiversBySupertype = new HashMap<>();
    private final Set<MethodReference> dispatched = new HashSet<>();
    private MethodDependency newDefaultDep;
    // Reflected members per dispatcher shard: register allocation and shadow-stack lowering are super-linear in a
    // method's size, so shards are sized by the members they dispatch, not by how many classes declare them.
    private static final int SHARD_MEMBERS = 128;
    private static final MethodReference BUCKET = new MethodReference(ReflectionBuckets.class.getName(), "bucket",
            CLASS, ValueType.INTEGER, ValueType.INTEGER);
    private final Map<Object, int[]> memberTotals = new IdentityHashMap<>();
    private final Map<MethodReference, Map<String, ? extends List<?>>> memberMaps = new LinkedHashMap<>();
    private final Map<MethodReference, MethodDependency> reachedSharded = new LinkedHashMap<>();
    private final Map<MethodReference, List<MethodReference>> shards = new LinkedHashMap<>();

    @Override
    public void started(DependencyAgent agent) {
        characteristics = new Characteristics(agent.getClassSource());
        for (MethodReference ref : new MethodReference[] { MODIFIERS, PARAMETER_TYPES, NEW_INSTANCE,
                CONSTRUCTOR_EXCEPTION_TYPES, NEW_DEFAULT_INSTANCE }) {
            memberMaps.put(ref, constructors);
        }
        for (MethodReference ref : new MethodReference[] { FIELD_MODIFIERS, FIELD_NAME, FIELD_TYPE, FIELD_GET,
                FIELD_SET }) {
            memberMaps.put(ref, fields);
        }
        for (MethodReference ref : new MethodReference[] { METHOD_MODIFIERS, METHOD_NAME, METHOD_RETURN_TYPE,
                METHOD_PARAMETER_TYPES, METHOD_EXCEPTION_TYPES, METHOD_INVOKE }) {
            memberMaps.put(ref, methods);
        }
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method) {
        if (memberMaps.containsKey(method.getReference())) {
            reachedSharded.put(method.getReference(), method);
            ensureShards(agent);
            MethodDependency bucket = agent.linkMethod(BUCKET);
            bucket.getVariable(1).propagate(agent.getType("java.lang.Class"));
            bucket.use();
        }
        // What the generated bodies produce must flow during analysis: completion can't link new classes.
        if (method.getReference().equals(NEW_INSTANCE)) {
            newInstanceDep = method;
            for (Map.Entry<String, List<MethodReader>> entry : constructors.entrySet()) {
                linkInvocation(agent, entry.getKey(), entry.getValue());
            }
            return;
        }
        if (method.getReference().equals(NEW_DEFAULT_INSTANCE)) {
            newDefaultDep = method;
            for (Map.Entry<String, List<MethodReader>> entry : constructors.entrySet()) {
                linkDefaultConstructor(agent, entry.getKey(), entry.getValue());
            }
            return;
        }
        if (method.getReference().equals(PARAMETER_TYPES)) {
            parameterTypesDep = method;
            method.getResult().propagate(agent.getType("[Ljava/lang/Class;"));
            method.getResult().getArrayItem().propagate(agent.getType("java.lang.Class"));
            for (List<MethodReader> declared : constructors.values()) {
                propagateParameterTypes(agent, declared);
            }
            return;
        }
        if (reachedFieldAccess(agent, method) || reachedMethodAccess(agent, method)) {
            return;
        }
        if (method.getReference().equals(CONSTRUCTOR_EXCEPTION_TYPES)) {
            constructorExceptionTypesDep = method;
            method.getResult().propagate(agent.getType("[Ljava/lang/Class;"));
            method.getResult().getArrayItem().propagate(agent.getType("java.lang.Class"));
            for (List<MethodReader> declared : constructors.values()) {
                propagateExceptionTypes(agent, method, declared);
            }
            return;
        }
        if (!method.getReference().equals(DECLARED)) {
            return;
        }
        method.getVariable(1).getClassValueNode().addConsumer(type -> {
            String name = type.getName();
            if (name.startsWith("[") || name.startsWith("~") || constructors.containsKey(name)
                    || !characteristics.isManaged(name)) {
                return;
            }
            ClassReader cls = agent.getClassSource().get(name);
            if (cls == null) {
                return;
            }
            List<MethodReader> declared = new ArrayList<>();
            for (MethodReader candidate : cls.getMethods()) {
                if (candidate.getName().equals("<init>") && characteristics.isManaged(candidate.getReference())
                        && parametersResolve(agent, candidate) && reflectable(name, candidate)) {
                    declared.add(candidate);
                }
            }
            record(constructors, name, declared);
            ensureShards(agent);
            // Everything the generated bodies reference has to be linked before completion.
            agent.linkClass(name);
            for (MethodReader ctor : declared) {
                for (ValueType parameter : ctor.getParameterTypes()) {
                    linkType(agent, parameter);
                }
                for (String exception : ctor.getExceptionTypes()) {
                    linkType(agent, ValueType.object(exception));
                }
            }
            if (parameterTypesDep != null) {
                propagateParameterTypes(agent, declared);
            }
            if (constructorExceptionTypesDep != null) {
                propagateExceptionTypes(agent, constructorExceptionTypesDep, declared);
            }
            if (newInstanceDep != null) {
                linkInvocation(agent, name, declared);
            }
            if (newDefaultDep != null) {
                linkDefaultConstructor(agent, name, declared);
            }
        });
    }

    // Reflective arguments reach a member only through the generated checkcast, so only subtypes of the declared
    // type flow; without the filter every value passed to any reflective call would pollute every member.
    private static DependencyTypeFilter typeFilter(DependencyAgent agent, ValueType type) {
        return agent.getSuperClassFilter(type instanceof ValueType.Object
                ? ((ValueType.Object) type).getClassName() : type.toString());
    }

    private static void linkType(DependencyAgent agent, ValueType type) {
        while (type instanceof ValueType.Array) {
            type = ((ValueType.Array) type).getItemType();
        }
        if (type instanceof ValueType.Object) {
            agent.linkClass(((ValueType.Object) type).getClassName());
        }
    }

    private void propagateParameterTypes(DependencyAgent agent, List<MethodReader> declared) {
        for (MethodReader ctor : declared) {
            for (ValueType parameter : ctor.getParameterTypes()) {
                propagateClassValue(agent, parameterTypesDep.getResult().getArrayItem().getClassValueNode(),
                        parameter);
            }
        }
    }

    private void linkInvocation(DependencyAgent agent, String name, List<MethodReader> declared) {
        if (!isInstantiable(agent.getClassSource().get(name))) {
            return;
        }
        agent.linkClass(name).initClass(new CallLocation(NEW_INSTANCE));
        newInstanceDep.getResult().propagate(agent.getType(name));
        for (MethodReader ctor : declared) {
            MethodDependency dep = agent.linkMethod(ctor.getReference());
            dep.getVariable(0).propagate(agent.getType(name));
            dep.use();
            dep.getThrown().connect(newInstanceDep.getThrown());
            ValueType[] parameters = ctor.getParameterTypes();
            for (int i = 0; i < parameters.length; ++i) {
                ValueType parameter = parameters[i];
                if (!(parameter instanceof ValueType.Primitive)) {
                    newInstanceDep.getVariable(3).getArrayItem().connect(dep.getVariable(i + 1),
                            typeFilter(agent, parameter));
                } else {
                    MethodReference unboxing = unboxing((ValueType.Primitive) parameter);
                    agent.linkClass(unboxing.getClassName());
                    MethodDependency unbox = agent.linkMethod(unboxing);
                    unbox.getVariable(0).propagate(agent.getType(unboxing.getClassName()));
                    unbox.use();
                }
            }
        }
    }

    // Class.newInstance only ever runs the no-arg constructor.
    private void linkDefaultConstructor(DependencyAgent agent, String name, List<MethodReader> declared) {
        if (!isInstantiable(agent.getClassSource().get(name))) {
            return;
        }
        for (MethodReader ctor : declared) {
            if (ctor.parameterCount() == 0) {
                agent.linkClass(name).initClass(new CallLocation(NEW_DEFAULT_INSTANCE));
                newDefaultDep.getResult().propagate(agent.getType(name));
                MethodDependency dep = agent.linkMethod(ctor.getReference());
                dep.getVariable(0).propagate(agent.getType(name));
                dep.use();
                dep.getThrown().connect(newDefaultDep.getThrown());
            }
        }
    }

    private static MethodReference unboxing(ValueType.Primitive type) {
        switch (type.getKind()) {
            case BOOLEAN:
                return new MethodReference(Boolean.class, "booleanValue", boolean.class);
            case BYTE:
                return new MethodReference(Byte.class, "byteValue", byte.class);
            case SHORT:
                return new MethodReference(Short.class, "shortValue", short.class);
            case CHARACTER:
                return new MethodReference(Character.class, "charValue", char.class);
            case INTEGER:
                return new MethodReference(Integer.class, "intValue", int.class);
            case LONG:
                return new MethodReference(Long.class, "longValue", long.class);
            case FLOAT:
                return new MethodReference(Float.class, "floatValue", float.class);
            default:
                return new MethodReference(Double.class, "doubleValue", double.class);
        }
    }

    // Which members of a class correspond to members of the same class on a JVM. org.teavm.* is the compiler's
    // runtime, absent from a JVM program's classpath. java.* can only be the emulated JDK (a JVM lets no other
    // definer into java.*), whose members are TeaVM's implementation: a member mirrors the JDK exactly when the
    // JDK the compiler runs on declares it, as public or protected API, with the same signature.
    private static boolean reflectable(String owner, MemberReader member) {
        if (owner.startsWith("org.teavm.")) {
            return false;
        }
        return !owner.startsWith("java.") || jdkDeclaresApi(owner, member);
    }

    private static boolean jdkDeclaresApi(String owner, MemberReader member) {
        Class<?> cls = hostClass(ValueType.object(owner));
        if (cls == null) {
            return false;
        }
        int modifiers;
        try {
            if (member instanceof FieldReader) {
                java.lang.reflect.Field field = cls.getDeclaredField(member.getName());
                if (field.getType() != hostClass(((FieldReader) member).getType())) {
                    return false;
                }
                modifiers = field.getModifiers();
            } else {
                MethodReader method = (MethodReader) member;
                ValueType[] parameters = method.getParameterTypes();
                Class<?>[] hostParameters = new Class<?>[parameters.length];
                for (int i = 0; i < parameters.length; ++i) {
                    hostParameters[i] = hostClass(parameters[i]);
                    if (hostParameters[i] == null) {
                        return false;
                    }
                }
                if (method.getName().equals("<init>")) {
                    modifiers = cls.getDeclaredConstructor(hostParameters).getModifiers();
                } else {
                    java.lang.reflect.Method hostMethod = cls.getDeclaredMethod(method.getName(), hostParameters);
                    if (hostMethod.getReturnType() != hostClass(method.getResultType())) {
                        return false;
                    }
                    modifiers = hostMethod.getModifiers();
                }
            }
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            return false;
        }
        return java.lang.reflect.Modifier.isPublic(modifiers) || java.lang.reflect.Modifier.isProtected(modifiers);
    }

    private static Class<?> hostClass(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return boolean.class;
                case BYTE:
                    return byte.class;
                case SHORT:
                    return short.class;
                case CHARACTER:
                    return char.class;
                case INTEGER:
                    return int.class;
                case LONG:
                    return long.class;
                case FLOAT:
                    return float.class;
                default:
                    return double.class;
            }
        }
        if (type == ValueType.VOID) {
            return void.class;
        }
        if (type instanceof ValueType.Array) {
            Class<?> item = hostClass(((ValueType.Array) type).getItemType());
            return item == null ? null : java.lang.reflect.Array.newInstance(item, 0).getClass();
        }
        try {
            return Class.forName(((ValueType.Object) type).getClassName(), false, null);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static boolean parametersResolve(DependencyAgent agent, MethodReader method) {
        for (String exception : method.getExceptionTypes()) {
            if (agent.getClassSource().get(exception) == null) {
                return false;
            }
        }
        for (ValueType parameter : method.getParameterTypes()) {
            while (parameter instanceof ValueType.Array) {
                parameter = ((ValueType.Array) parameter).getItemType();
            }
            if (parameter instanceof ValueType.Object
                    && agent.getClassSource().get(((ValueType.Object) parameter).getClassName()) == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void completing(DependencyAgent agent) {
        Map<String, List<MethodReader>> snapshot = new LinkedHashMap<>(constructors);
        submitCount(agent, COUNT, snapshot);
        submit(agent, MODIFIERS, ValueType.INTEGER, snapshot, cls -> true, (pe, owner, ctor, args) ->
                pe.constant(modifiers(owner, ctor)).returnValue());
        submit(agent, PARAMETER_TYPES, ValueType.arrayOf(CLASS), snapshot, cls -> true, (pe, owner, ctor, args) -> {
            ValueType[] parameters = ctor.getParameterTypes();
            ValueEmitter array = pe.constructArray(CLASS, parameters.length);
            for (int i = 0; i < parameters.length; ++i) {
                array.setElement(i, pe.constant(parameters[i]));
            }
            array.returnValue();
        });
        submit(agent, NEW_INSTANCE, OBJECT, snapshot, ReflectionWasmSupport::isInstantiable,
                (pe, owner, ctor, args) -> {
                    ValueType[] parameters = ctor.getParameterTypes();
                    ValueEmitter[] values = new ValueEmitter[parameters.length];
                    for (int i = 0; i < parameters.length; ++i) {
                        values[i] = unbox(args[0].getElement(i), parameters[i]);
                    }
                    pe.construct(owner.getName(), values).returnValue();
                });
        submit(agent, CONSTRUCTOR_EXCEPTION_TYPES, ValueType.arrayOf(CLASS), snapshot, cls -> true,
                (pe, owner, ctor, args) -> classArray(pe, ctor.getExceptionTypes()).returnValue());
        submit(agent, NEW_DEFAULT_INSTANCE, OBJECT, snapshot, ReflectionWasmSupport::isInstantiable,
                (pe, owner, ctor, args) -> {
                    if (ctor.parameterCount() == 0) {
                        pe.construct(owner.getName()).returnValue();
                    } else {
                        pe.constantNull(OBJECT).returnValue();
                    }
                });
        completingFields(agent);
        completingMethods(agent);
    }

    private static ValueEmitter classArray(ProgramEmitter pe, String[] classNames) {
        ValueEmitter array = pe.constructArray(CLASS, classNames.length);
        for (int i = 0; i < classNames.length; ++i) {
            array.setElement(i, pe.constant(ValueType.object(classNames[i])));
        }
        return array;
    }

    private static void propagateExceptionTypes(DependencyAgent agent, MethodDependency target,
            List<MethodReader> declared) {
        for (MethodReader member : declared) {
            for (String exception : member.getExceptionTypes()) {
                target.getResult().getArrayItem().getClassValueNode().propagate(agent.getType(exception));
            }
        }
    }

    // --- java.lang.reflect.Method ------------------------------------------------------------------------

    private boolean reachedMethodAccess(DependencyAgent agent, MethodDependency method) {
        MethodReference ref = method.getReference();
        if (ref.equals(METHOD_RETURN_TYPE)) {
            method.getResult().propagate(agent.getType("java.lang.Class"));
        } else if (ref.equals(METHOD_PARAMETER_TYPES) || ref.equals(METHOD_EXCEPTION_TYPES)) {
            method.getResult().propagate(agent.getType("[Ljava/lang/Class;"));
            method.getResult().getArrayItem().propagate(agent.getType("java.lang.Class"));
        } else if (ref.equals(METHOD_NAME)) {
            method.getResult().propagate(agent.getType("java.lang.String"));
            return true;
        } else if (ref.equals(METHOD_INVOKE)) {
            invokeDep = method;
            for (Map.Entry<String, List<MethodReader>> entry : methods.entrySet()) {
                linkMethodInvocation(agent, entry.getKey(), entry.getValue());
            }
            // Instance methods dispatch on the receiver's runtime class, as invokevirtual does.
            // Only methods declared by the receiver's own supertypes can be selected, so those are the only owners
            // visited (dispatch() rejects every other owner anyway).
            method.getVariable(3).addConsumer(type -> {
                String receiver = type.getName();
                for (String owner : supertypes(agent, receiver)) {
                    receiversBySupertype.computeIfAbsent(owner, k -> new ArrayList<>()).add(receiver);
                    List<MethodReader> declared = methods.get(owner);
                    if (declared != null) {
                        for (MethodReader member : declared) {
                            dispatch(agent, receiver, owner, member);
                        }
                    }
                }
            });
            return true;
        } else if (ref.equals(METHODS_DECLARED)) {
            method.getVariable(1).getClassValueNode().addConsumer(type -> recordMethods(agent, type.getName()));
            return true;
        } else {
            return false;
        }
        classResults.put(ref, method);
        for (List<MethodReader> declared : methods.values()) {
            propagateMethodTypes(agent, ref, method, declared);
        }
        return true;
    }

    private void propagateMethodTypes(DependencyAgent agent, MethodReference ref, MethodDependency dep,
            List<MethodReader> declared) {
        for (MethodReader member : declared) {
            if (ref.equals(METHOD_RETURN_TYPE)) {
                propagateClassValue(agent, dep.getResult().getClassValueNode(), member.getResultType());
            } else if (ref.equals(METHOD_PARAMETER_TYPES)) {
                for (ValueType parameter : member.getParameterTypes()) {
                    propagateClassValue(agent, dep.getResult().getArrayItem().getClassValueNode(), parameter);
                }
            } else {
                for (String exception : member.getExceptionTypes()) {
                    dep.getResult().getArrayItem().getClassValueNode().propagate(agent.getType(exception));
                }
            }
        }
    }

    private static void propagateClassValue(DependencyAgent agent, DependencyNode node,
            ValueType type) {
        // The same class values the generated class constants carry (primitive and void classes are "~" + descriptor).
        if (type instanceof ValueType.Object) {
            node.propagate(agent.getType(((ValueType.Object) type).getClassName()));
        } else if (type instanceof ValueType.Array) {
            node.propagate(agent.getType(type.toString()));
        } else {
            node.propagate(agent.getType("~" + type));
        }
    }

    // The member walks of Class.getMethods/getFields call getInterfaces() on every class they visit, including
    // supertypes reached through getSuperclass(), whose Class values the analysis cannot see.
    private static void requireInterfaces(DependencyAgent agent, String name) {
        agent.linkMethod(GET_INTERFACES).getVariable(0).getClassValueNode().propagate(agent.getType(name));
    }

    // Class.getMethods/getMethod walk the superclass chain and superinterfaces, so they are recorded as well.
    private void recordMethods(DependencyAgent agent, String name) {
        if (name.startsWith("[") || name.startsWith("~") || methods.containsKey(name)
                || !characteristics.isManaged(name)) {
            return;
        }
        ClassReader cls = agent.getClassSource().get(name);
        if (cls == null) {
            return;
        }
        List<MethodReader> declared = new ArrayList<>();
        for (MethodReader candidate : cls.getMethods()) {
            if (!candidate.getName().equals("<init>") && !candidate.getName().equals("<clinit>")
                    && characteristics.isManaged(candidate.getReference()) && parametersResolve(agent, candidate)
                    && typeResolves(agent, candidate.getResultType()) && reflectable(name, candidate)) {
                declared.add(candidate);
            }
        }
        record(methods, name, declared);
        ensureShards(agent);
        agent.linkClass(name);
        requireInterfaces(agent, name);
        for (MethodReader member : declared) {
            linkType(agent, member.getResultType());
            for (ValueType parameter : member.getParameterTypes()) {
                linkType(agent, parameter);
            }
            for (String exception : member.getExceptionTypes()) {
                linkType(agent, ValueType.object(exception));
            }
        }
        for (Map.Entry<MethodReference, MethodDependency> entry : classResults.entrySet()) {
            propagateMethodTypes(agent, entry.getKey(), entry.getValue(), declared);
        }
        if (invokeDep != null) {
            linkMethodInvocation(agent, name, declared);
            for (String receiver : new ArrayList<>(receiversBySupertype.getOrDefault(name, List.of()))) {
                for (MethodReader member : declared) {
                    dispatch(agent, receiver, name, member);
                }
            }
        }
        if (cls.getParent() != null) {
            recordMethods(agent, cls.getParent());
        }
        for (String iface : cls.getInterfaces()) {
            recordMethods(agent, iface);
        }
    }

    private void linkMethodInvocation(DependencyAgent agent, String owner, List<MethodReader> declared) {
        CallLocation location = new CallLocation(METHOD_INVOKE);
        for (MethodReader member : declared) {
            // An instance method can only run on a receiver of a class that has instances; dispatch() links those
            // implementations as receiver types arrive. Static methods need no receiver.
            if (member.hasModifier(ElementModifier.STATIC)) {
                agent.linkClass(owner).initClass(location);
                linkTarget(agent, member);
            }
            for (ValueType parameter : member.getParameterTypes()) {
                if (parameter instanceof ValueType.Primitive) {
                    MethodReference unboxing = unboxing((ValueType.Primitive) parameter);
                    agent.linkClass(unboxing.getClassName());
                    MethodDependency unbox = agent.linkMethod(unboxing);
                    unbox.getVariable(0).propagate(agent.getType(unboxing.getClassName()));
                    unbox.use();
                }
            }
            ValueType result = member.getResultType();
            if (result instanceof ValueType.Primitive) {
                MethodReference boxing = boxing((ValueType.Primitive) result);
                agent.linkClass(boxing.getClassName());
                MethodDependency box = agent.linkMethod(boxing);
                box.use();
                box.getResult().connect(invokeDep.getResult());
            }
        }
    }

    // Links a method the generated invoker may end up executing, with its arguments and result flowing.
    private MethodDependency linkTarget(DependencyAgent agent, MethodReader target) {
        MethodDependency dep = agent.linkMethod(target.getReference());
        dep.use();
        ValueType[] parameters = target.getParameterTypes();
        for (int i = 0; i < parameters.length; ++i) {
            if (!(parameters[i] instanceof ValueType.Primitive)) {
                invokeDep.getVariable(4).getArrayItem().connect(dep.getVariable(i + 1),
                        typeFilter(agent, parameters[i]));
            }
        }
        if (!(target.getResultType() instanceof ValueType.Primitive) && target.getResultType() != ValueType.VOID) {
            dep.getResult().connect(invokeDep.getResult());
        }
        dep.getThrown().connect(invokeDep.getThrown());
        return dep;
    }

    // The receiver itself, its superclasses and every interface they implement; arrays only have Object's methods.
    private static Set<String> supertypes(DependencyAgent agent, String receiver) {
        Set<String> result = new LinkedHashSet<>();
        if (receiver.startsWith("[")) {
            result.add("java.lang.Object");
            return result;
        }
        List<String> queue = new ArrayList<>();
        queue.add(receiver);
        for (int i = 0; i < queue.size(); ++i) {
            String name = queue.get(i);
            if (!result.add(name)) {
                continue;
            }
            ClassReader cls = agent.getClassSource().get(name);
            if (cls == null) {
                continue;
            }
            if (cls.getParent() != null) {
                queue.add(cls.getParent());
            }
            queue.addAll(cls.getInterfaces());
        }
        return result;
    }

    private void dispatch(DependencyAgent agent, String receiver, String owner, MethodReader declared) {
        if (declared.hasModifier(ElementModifier.STATIC)) {
            return;
        }
        MethodReader target;
        if (declared.getLevel() == AccessLevel.PRIVATE || receiver.startsWith("[")) {
            if (receiver.startsWith("[") && !owner.equals("java.lang.Object")) {
                return;
            }
            target = declared;
        } else {
            if (!agent.getClassHierarchy().isSuperType(owner, receiver, false)) {
                return;
            }
            target = agent.getClassHierarchy().resolve(receiver, declared.getDescriptor());
            if (target == null || target.hasModifier(ElementModifier.ABSTRACT)) {
                return;
            }
        }
        if (!receiver.startsWith("[") && !agent.getClassHierarchy().isSuperType(owner, receiver, false)) {
            return;
        }
        dispatched.add(declared.getReference());
        // As VirtualCallConsumer does for the generated call: the receiver node (with its class values and array
        // items) flows into the target's this, filtered to the target's owner.
        String targetOwner = target.getOwnerName();
        invokeDep.getVariable(3).connect(linkTarget(agent, target).getVariable(0),
                agent.getSuperClassFilter(targetOwner));
    }

    private void completingMethods(DependencyAgent agent) {
        Map<String, List<MethodReader>> snapshot = new LinkedHashMap<>(methods);
        submitCount(agent, METHOD_COUNT, snapshot);
        submit(agent, METHOD_MODIFIERS, ValueType.INTEGER, snapshot, cls -> true, (pe, owner, member, args) ->
                pe.constant(methodModifiers(member)).returnValue());
        submit(agent, METHOD_NAME, ValueType.object("java.lang.String"), snapshot, cls -> true,
                (pe, owner, member, args) -> pe.constant(member.getName()).returnValue());
        submit(agent, METHOD_RETURN_TYPE, CLASS, snapshot, cls -> true, (pe, owner, member, args) ->
                pe.constant(member.getResultType()).returnValue());
        submit(agent, METHOD_PARAMETER_TYPES, ValueType.arrayOf(CLASS), snapshot, cls -> true,
                (pe, owner, member, args) -> {
                    ValueType[] parameters = member.getParameterTypes();
                    ValueEmitter array = pe.constructArray(CLASS, parameters.length);
                    for (int i = 0; i < parameters.length; ++i) {
                        array.setElement(i, pe.constant(parameters[i]));
                    }
                    array.returnValue();
                });
        submit(agent, METHOD_EXCEPTION_TYPES, ValueType.arrayOf(CLASS), snapshot, cls -> true,
                (pe, owner, member, args) -> classArray(pe, member.getExceptionTypes()).returnValue());
        submit(agent, METHOD_INVOKE, OBJECT, snapshot, cls -> true, (pe, owner, member, args) -> {
            ValueType[] parameters = member.getParameterTypes();
            ValueEmitter[] values = new ValueEmitter[parameters.length];
            for (int i = 0; i < parameters.length; ++i) {
                values[i] = unbox(args[1].getElement(i), parameters[i]);
            }
            if (!member.hasModifier(ElementModifier.STATIC) && !dispatched.contains(member.getReference())) {
                // No instance of a class declaring or inheriting it ever reached invoke: unreachable.
                pe.constantNull(OBJECT).returnValue();
                return;
            }
            ValueEmitter result;
            if (member.hasModifier(ElementModifier.STATIC)) {
                pe.initClass(owner.getName());
                result = pe.invoke(member.getReference(), values);
            } else {
                ValueEmitter receiver = args[0].cast(ValueType.object(owner.getName()));
                result = member.getLevel() == AccessLevel.PRIVATE
                        ? receiver.invokeSpecial(member.getReference(), values)
                        : receiver.invokeVirtual(member.getReference(), values);
            }
            if (member.getResultType() == ValueType.VOID) {
                pe.constantNull(OBJECT).returnValue();
            } else if (member.getResultType() instanceof ValueType.Primitive) {
                pe.invoke(boxing((ValueType.Primitive) member.getResultType()), result).returnValue();
            } else {
                result.returnValue();
            }
        });
    }

    private static int methodModifiers(MethodReader method) {
        int result = access(method.getLevel());
        int[] bits = { 0x8, 0x10, 0x20, 0x40, 0x80, 0x100, 0x400, 0x800, 0x1000 };
        ElementModifier[] modifiers = { ElementModifier.STATIC, ElementModifier.FINAL, ElementModifier.SYNCHRONIZED,
                ElementModifier.BRIDGE, ElementModifier.VARARGS, ElementModifier.NATIVE, ElementModifier.ABSTRACT,
                ElementModifier.STRICT, ElementModifier.SYNTHETIC };
        for (int i = 0; i < bits.length; ++i) {
            if (method.hasModifier(modifiers[i])) {
                result |= bits[i];
            }
        }
        return result;
    }

    private interface MemberEmitter<T> {
        void emit(ProgramEmitter pe, ClassReader owner, T member, ValueEmitter[] arguments);
    }

    // The flows of every generated dispatcher are declared while analysis runs (linked targets, results, thrown
    // exceptions, class values), so its body is installed without re-analysis: a dispatcher has a case per
    // reflected member, and analyzing tens of thousands of call sites again costs far more than the closure itself.
    private static DependencyNode submitDeclared(DependencyAgent agent, MethodReference method, ProgramEmitter pe) {
        MethodDependency dep = agent.linkMethod(method);
        DependencyNode summary = agent.createNode();
        for (int i = 1; i <= method.parameterCount(); ++i) {
            DependencyNode parameter = dep.getVariable(i);
            if (parameter != null) {
                parameter.connect(summary);
                parameter.getArrayItem().connect(summary);
            }
        }
        agent.submitMethodWithDeclaredFlows(method, pe.getProgram(), summary, false);
        return summary;
    }

    private static void submitCount(DependencyAgent agent, MethodReference method,
            Map<String, ? extends List<?>> snapshot) {
        if (agent.getMethod(method) == null) {
            return;
        }
        ProgramEmitter pe = ProgramEmitter.create(method.getDescriptor(), agent.getClassHierarchy());
        ValueEmitter cls = pe.var(1, CLASS);
        for (Map.Entry<String, ? extends List<?>> entry : snapshot.entrySet()) {
            int count = entry.getValue().size();
            pe.when(cls.isSame(pe.constant(ValueType.object(entry.getKey()))))
                    .thenDo(() -> pe.constant(count).returnValue());
        }
        pe.constant(0).returnValue();
        submitDeclared(agent, method, pe);
    }

    // Dispatches on (class, member index); the remaining parameters of the native are passed to the emitter. The
    // native switches on the class's bucket (ReflectionBuckets, the class name's hash modulo the shard count) to the
    // shard holding it, so neither the native nor any shard grows with the number of reflected classes.
    private <T> void submit(DependencyAgent agent, MethodReference method, ValueType resultType,
            Map<String, List<T>> snapshot, Predicate<ClassReader> include, MemberEmitter<T> emitter) {
        if (agent.getMethod(method) == null) {
            return;
        }
        List<MethodReference> methodShards = shards.getOrDefault(method, new ArrayList<>());
        int shardCount = methodShards.size();
        List<List<Map.Entry<String, List<T>>>> buckets = new ArrayList<>();
        for (int k = 0; k < shardCount; ++k) {
            buckets.add(new ArrayList<>());
        }
        for (Map.Entry<String, List<T>> entry : snapshot.entrySet()) {
            buckets.get(ReflectionBuckets.bucket(entry.getKey(), shardCount)).add(entry);
        }
        ProgramEmitter pe = ProgramEmitter.create(method.getDescriptor(), agent.getClassHierarchy());
        ValueEmitter[] parameters = new ValueEmitter[method.parameterCount()];
        for (int i = 0; i < parameters.length; ++i) {
            parameters[i] = pe.var(i + 1, method.parameterType(i));
        }
        if (shardCount > 0) {
            ValueEmitter bucket = pe.invoke(BUCKET, parameters[0], pe.constant(shardCount));
            ChooseEmitter choice = pe.choice(bucket);
            for (int k = 0; k < shardCount; ++k) {
                if (buckets.get(k).isEmpty()) {
                    continue;
                }
                MethodReference shard = methodShards.get(k);
                choice.option(k, () -> {
                    ValueEmitter result = pe.invoke(shard, parameters);
                    if (resultType == ValueType.VOID) {
                        pe.exit();
                    } else {
                        result.returnValue();
                    }
                });
            }
        }
        returnDefault(pe, resultType);
        // Shards only receive the native's values, so they share its summary for every variable.
        DependencyNode summary = submitDeclared(agent, method, pe);
        for (int k = 0; k < shardCount; ++k) {
            agent.submitMethodWithDeclaredFlows(methodShards.get(k), dispatchMembers(agent, method, resultType,
                    buckets.get(k), include, emitter).getProgram(), summary, true);
        }
    }

    private static <T> ProgramEmitter dispatchMembers(DependencyAgent agent, MethodReference method,
            ValueType resultType, List<Map.Entry<String, List<T>>> entries, Predicate<ClassReader> include,
            MemberEmitter<T> emitter) {
        ProgramEmitter pe = ProgramEmitter.create(method.getDescriptor(), agent.getClassHierarchy());
        ValueEmitter cls = pe.var(1, CLASS);
        ValueEmitter index = pe.var(2, ValueType.INTEGER);
        ValueEmitter[] arguments = new ValueEmitter[method.parameterCount() - 2];
        for (int i = 0; i < arguments.length; ++i) {
            arguments[i] = pe.var(3 + i, method.parameterType(2 + i));
        }
        for (Map.Entry<String, List<T>> entry : entries) {
            ClassReader owner = agent.getClassSource().get(entry.getKey());
            if (!include.test(owner)) {
                continue;
            }
            List<T> declared = entry.getValue();
            pe.when(cls.isSame(pe.constant(ValueType.object(entry.getKey())))).thenDo(() -> {
                for (int i = 0; i < declared.size(); ++i) {
                    T member = declared.get(i);
                    pe.when(index.isEqualTo(pe.constant(i))).thenDo(() -> emitter.emit(pe, owner, member, arguments));
                }
            });
        }
        returnDefault(pe, resultType);
        return pe;
    }

    private static void returnDefault(ProgramEmitter pe, ValueType resultType) {
        if (resultType == ValueType.VOID) {
            pe.exit();
        } else if (resultType instanceof ValueType.Primitive) {
            pe.constant(0).returnValue();
        } else {
            pe.constantNull(resultType).returnValue();
        }
    }

    // Shards must exist (and be linked) before completion, so they are created as natives are reached and classes
    // are recorded. A shard is a static method with the native's descriptor in a generated class. Its flows are the
    // native's, already declared on the native's nodes, so the shard itself carries none.
    private void ensureShards(DependencyAgent agent) {
        for (Map.Entry<MethodReference, MethodDependency> entry : reachedSharded.entrySet()) {
            MethodReference nativeRef = entry.getKey();
            int[] total = memberTotals.get(memberMaps.get(nativeRef));
            int needed = Math.max(1, ((total != null ? total[0] : 0) + SHARD_MEMBERS - 1) / SHARD_MEMBERS);
            List<MethodReference> list = shards.computeIfAbsent(nativeRef, k -> new ArrayList<>());
            while (list.size() < needed) {
                list.add(createShard(agent, nativeRef));
            }
        }
    }

    // Every recorded class weighs at least one case in its shard, even when it declares no reflectable member.
    private <T> void record(Map<String, List<T>> map, String name, List<T> declared) {
        map.put(name, declared);
        memberTotals.computeIfAbsent(map, k -> new int[1])[0] += Math.max(1, declared.size());
    }

    private static MethodReference createShard(DependencyAgent agent, MethodReference nativeRef) {
        ClassHolder holder = new ClassHolder(agent.generateClassName());
        holder.setParent("java.lang.Object");
        holder.setLevel(AccessLevel.PUBLIC);
        MethodHolder shard = new MethodHolder(nativeRef.getDescriptor());
        shard.setLevel(AccessLevel.PUBLIC);
        shard.getModifiers().add(ElementModifier.STATIC);
        shard.getModifiers().add(ElementModifier.NATIVE);
        holder.addMethod(shard);
        agent.submitClass(holder);
        MethodReference shardRef = new MethodReference(holder.getName(), nativeRef.getDescriptor());
        agent.linkMethod(shardRef).use();
        return shardRef;
    }
    // --- java.lang.reflect.Field -------------------------------------------------------------------------

    private boolean reachedFieldAccess(DependencyAgent agent, MethodDependency method) {
        MethodReference ref = method.getReference();
        if (ref.equals(FIELD_TYPE)) {
            fieldTypeDep = method;
            method.getResult().propagate(agent.getType("java.lang.Class"));
            for (List<FieldReader> declared : fields.values()) {
                propagateFieldTypes(agent, declared);
            }
        } else if (ref.equals(FIELD_NAME)) {
            method.getResult().propagate(agent.getType("java.lang.String"));
        } else if (ref.equals(FIELD_GET)) {
            fieldGetDep = method;
            for (Map.Entry<String, List<FieldReader>> entry : fields.entrySet()) {
                linkFieldGet(agent, entry.getKey(), entry.getValue());
            }
        } else if (ref.equals(FIELD_SET)) {
            fieldSetDep = method;
            for (Map.Entry<String, List<FieldReader>> entry : fields.entrySet()) {
                linkFieldSet(agent, entry.getKey(), entry.getValue());
            }
        } else if (ref.equals(FIELDS_DECLARED)) {
            method.getVariable(1).getClassValueNode().addConsumer(type -> recordFields(agent, type.getName()));
        } else {
            return false;
        }
        return true;
    }

    private void recordFields(DependencyAgent agent, String name) {
        if (name.startsWith("[") || name.startsWith("~") || fields.containsKey(name)
                || !characteristics.isManaged(name)) {
            return;
        }
        ClassReader cls = agent.getClassSource().get(name);
        if (cls == null) {
            return;
        }
        List<FieldReader> declared = new ArrayList<>();
        for (FieldReader field : cls.getFields()) {
            if (typeResolves(agent, field.getType()) && reflectable(name, field)) {
                declared.add(field);
            }
        }
        record(fields, name, declared);
        ensureShards(agent);
        agent.linkClass(name);
        requireInterfaces(agent, name);
        for (FieldReader field : declared) {
            linkType(agent, field.getType());
        }
        // Class.getFields/getField walk the superclass chain and superinterfaces.
        if (cls.getParent() != null) {
            recordFields(agent, cls.getParent());
        }
        for (String iface : cls.getInterfaces()) {
            recordFields(agent, iface);
        }
        if (fieldTypeDep != null) {
            propagateFieldTypes(agent, declared);
        }
        if (fieldGetDep != null) {
            linkFieldGet(agent, name, declared);
        }
        if (fieldSetDep != null) {
            linkFieldSet(agent, name, declared);
        }
    }

    private static boolean typeResolves(DependencyAgent agent, ValueType type) {
        while (type instanceof ValueType.Array) {
            type = ((ValueType.Array) type).getItemType();
        }
        return !(type instanceof ValueType.Object)
                || agent.getClassSource().get(((ValueType.Object) type).getClassName()) != null;
    }

    private void propagateFieldTypes(DependencyAgent agent, List<FieldReader> declared) {
        for (FieldReader field : declared) {
            propagateClassValue(agent, fieldTypeDep.getResult().getClassValueNode(), field.getType());
        }
    }

    private void linkFieldGet(DependencyAgent agent, String owner, List<FieldReader> declared) {
        CallLocation location = new CallLocation(FIELD_GET);
        for (FieldReader field : declared) {
            FieldDependency dep = agent.linkField(field.getReference());
            if (field.hasModifier(ElementModifier.STATIC)) {
                agent.linkClass(owner).initClass(location);
            }
            if (field.getType() instanceof ValueType.Primitive) {
                MethodReference boxing = boxing((ValueType.Primitive) field.getType());
                agent.linkClass(boxing.getClassName());
                MethodDependency box = agent.linkMethod(boxing);
                box.use();
                box.getResult().connect(fieldGetDep.getResult());
            } else {
                dep.getValue().connect(fieldGetDep.getResult());
            }
        }
    }

    private void linkFieldSet(DependencyAgent agent, String owner, List<FieldReader> declared) {
        CallLocation location = new CallLocation(FIELD_SET);
        for (FieldReader field : declared) {
            FieldDependency dep = agent.linkField(field.getReference());
            if (field.hasModifier(ElementModifier.STATIC)) {
                agent.linkClass(owner).initClass(location);
            }
            if (field.getType() instanceof ValueType.Primitive) {
                MethodReference unboxing = unboxing((ValueType.Primitive) field.getType());
                agent.linkClass(unboxing.getClassName());
                MethodDependency unbox = agent.linkMethod(unboxing);
                unbox.getVariable(0).propagate(agent.getType(unboxing.getClassName()));
                unbox.use();
            } else {
                fieldSetDep.getVariable(4).connect(dep.getValue(), typeFilter(agent, field.getType()));
            }
        }
    }

    private void completingFields(DependencyAgent agent) {
        Map<String, List<FieldReader>> snapshot = new LinkedHashMap<>(fields);
        submitCount(agent, FIELD_COUNT, snapshot);
        submit(agent, FIELD_MODIFIERS, ValueType.INTEGER, snapshot, cls -> true, (pe, owner, field, args) ->
                pe.constant(fieldModifiers(field)).returnValue());
        submit(agent, FIELD_NAME, ValueType.object("java.lang.String"), snapshot, cls -> true,
                (pe, owner, field, args) -> pe.constant(field.getName()).returnValue());
        submit(agent, FIELD_TYPE, CLASS, snapshot, cls -> true, (pe, owner, field, args) ->
                pe.constant(field.getType()).returnValue());
        submit(agent, FIELD_GET, OBJECT, snapshot, cls -> true, (pe, owner, field, args) -> {
            ValueEmitter value = field.hasModifier(ElementModifier.STATIC)
                    ? pe.getField(owner.getName(), field.getName(), field.getType())
                    : args[0].cast(ValueType.object(owner.getName())).getField(field.getName(), field.getType());
            if (field.getType() instanceof ValueType.Primitive) {
                value = pe.invoke(boxing((ValueType.Primitive) field.getType()), value);
            }
            value.returnValue();
        });
        submit(agent, FIELD_SET, ValueType.VOID, snapshot, cls -> true, (pe, owner, field, args) -> {
            ValueEmitter value = unbox(args[1], field.getType());
            if (field.hasModifier(ElementModifier.STATIC)) {
                pe.setField(owner.getName(), field.getName(), value);
            } else {
                args[0].cast(ValueType.object(owner.getName())).setField(field.getName(), value);
            }
            pe.exit();
        });
    }

    private static int fieldModifiers(FieldReader field) {
        int result = access(field.getLevel());
        if (field.hasModifier(ElementModifier.STATIC)) {
            result |= 0x8;
        }
        if (field.hasModifier(ElementModifier.FINAL)) {
            result |= 0x10;
        }
        if (field.hasModifier(ElementModifier.VOLATILE)) {
            result |= 0x40;
        }
        if (field.hasModifier(ElementModifier.TRANSIENT)) {
            result |= 0x80;
        }
        if (field.hasModifier(ElementModifier.SYNTHETIC)) {
            result |= 0x1000;
        }
        if (field.hasModifier(ElementModifier.ENUM)) {
            result |= 0x4000;
        }
        return result;
    }

    private static int access(AccessLevel level) {
        if (level == AccessLevel.PUBLIC) {
            return 0x1;
        } else if (level == AccessLevel.PRIVATE) {
            return 0x2;
        } else if (level == AccessLevel.PROTECTED) {
            return 0x4;
        }
        return 0;
    }

    private static MethodReference boxing(ValueType.Primitive type) {
        switch (type.getKind()) {
            case BOOLEAN:
                return new MethodReference(Boolean.class, "valueOf", boolean.class, Boolean.class);
            case BYTE:
                return new MethodReference(Byte.class, "valueOf", byte.class, Byte.class);
            case SHORT:
                return new MethodReference(Short.class, "valueOf", short.class, Short.class);
            case CHARACTER:
                return new MethodReference(Character.class, "valueOf", char.class, Character.class);
            case INTEGER:
                return new MethodReference(Integer.class, "valueOf", int.class, Integer.class);
            case LONG:
                return new MethodReference(Long.class, "valueOf", long.class, Long.class);
            case FLOAT:
                return new MethodReference(Float.class, "valueOf", float.class, Float.class);
            default:
                return new MethodReference(Double.class, "valueOf", double.class, Double.class);
        }
    }

    private static boolean isInstantiable(ClassReader cls) {
        return !cls.hasModifier(ElementModifier.ABSTRACT) && !cls.hasModifier(ElementModifier.INTERFACE)
                && !cls.hasModifier(ElementModifier.ENUM);
    }

    private static int modifiers(ClassReader owner, MethodReader ctor) {
        int result;
        AccessLevel level = ctor.getLevel();
        if (level == AccessLevel.PUBLIC) {
            result = 0x1;
        } else if (level == AccessLevel.PRIVATE) {
            result = 0x2;
        } else if (level == AccessLevel.PROTECTED) {
            result = 0x4;
        } else {
            result = 0;
        }
        if (ctor.hasModifier(ElementModifier.VARARGS)) {
            result |= 0x80;
        }
        if (ctor.hasModifier(ElementModifier.SYNTHETIC)) {
            result |= 0x1000;
        }
        if (owner.hasModifier(ElementModifier.ABSTRACT) || owner.hasModifier(ElementModifier.INTERFACE)) {
            result |= CLASS_ABSTRACT;
        }
        if (owner.hasModifier(ElementModifier.ENUM)) {
            result |= CLASS_ENUM;
        }
        return result;
    }

    // TConstructor has already converted each argument to the exact wrapper of a primitive parameter.
    private static ValueEmitter unbox(ValueEmitter value, ValueType type) {
        if (!(type instanceof ValueType.Primitive)) {
            return value.cast(type);
        }
        switch (((ValueType.Primitive) type).getKind()) {
            case BOOLEAN:
                return value.cast(Boolean.class).invokeVirtual("booleanValue", boolean.class);
            case BYTE:
                return value.cast(Byte.class).invokeVirtual("byteValue", byte.class);
            case SHORT:
                return value.cast(Short.class).invokeVirtual("shortValue", short.class);
            case CHARACTER:
                return value.cast(Character.class).invokeVirtual("charValue", char.class);
            case INTEGER:
                return value.cast(Integer.class).invokeVirtual("intValue", int.class);
            case LONG:
                return value.cast(Long.class).invokeVirtual("longValue", long.class);
            case FLOAT:
                return value.cast(Float.class).invokeVirtual("floatValue", float.class);
            default:
                return value.cast(Double.class).invokeVirtual("doubleValue", double.class);
        }
    }
}
