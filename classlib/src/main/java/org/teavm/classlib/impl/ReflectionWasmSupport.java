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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.FieldDependency;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.AccessLevel;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
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

    @Override
    public void started(DependencyAgent agent) {
        characteristics = new Characteristics(agent.getClassSource());
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method) {
        // What the generated bodies produce must flow during analysis: completion can't link new classes.
        if (method.getReference().equals(NEW_INSTANCE)) {
            newInstanceDep = method;
            for (Map.Entry<String, List<MethodReader>> entry : constructors.entrySet()) {
                linkInvocation(agent, entry.getKey(), entry.getValue());
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
        if (reachedFieldAccess(agent, method)) {
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
                        && parametersResolve(agent, candidate)) {
                    declared.add(candidate);
                }
            }
            constructors.put(name, declared);
            // Everything the generated bodies reference has to be linked before completion.
            agent.linkClass(name);
            for (MethodReader ctor : declared) {
                for (ValueType parameter : ctor.getParameterTypes()) {
                    linkType(agent, parameter);
                }
            }
            if (parameterTypesDep != null) {
                propagateParameterTypes(agent, declared);
            }
            if (newInstanceDep != null) {
                linkInvocation(agent, name, declared);
            }
        });
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
                if (parameter instanceof ValueType.Object) {
                    parameterTypesDep.getResult().getArrayItem().getClassValueNode()
                            .propagate(agent.getType(((ValueType.Object) parameter).getClassName()));
                } else if (parameter instanceof ValueType.Array) {
                    parameterTypesDep.getResult().getArrayItem().getClassValueNode()
                            .propagate(agent.getType(parameter.toString()));
                }
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
            ValueType[] parameters = ctor.getParameterTypes();
            for (int i = 0; i < parameters.length; ++i) {
                ValueType parameter = parameters[i];
                if (!(parameter instanceof ValueType.Primitive)) {
                    newInstanceDep.getVariable(3).getArrayItem().connect(dep.getVariable(i + 1));
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

    private static boolean parametersResolve(DependencyAgent agent, MethodReader method) {
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
        completingFields(agent);
    }

    private interface MemberEmitter<T> {
        void emit(ProgramEmitter pe, ClassReader owner, T member, ValueEmitter[] arguments);
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
        agent.submitMethod(method, pe.getProgram());
    }

    // Dispatches on (class, member index); the remaining parameters of the native are passed to the emitter.
    private static <T> void submit(DependencyAgent agent, MethodReference method, ValueType resultType,
            Map<String, List<T>> snapshot, Predicate<ClassReader> include, MemberEmitter<T> emitter) {
        if (agent.getMethod(method) == null) {
            return;
        }
        ProgramEmitter pe = ProgramEmitter.create(method.getDescriptor(), agent.getClassHierarchy());
        ValueEmitter cls = pe.var(1, CLASS);
        ValueEmitter index = pe.var(2, ValueType.INTEGER);
        ValueEmitter[] arguments = new ValueEmitter[method.parameterCount() - 2];
        for (int i = 0; i < arguments.length; ++i) {
            arguments[i] = pe.var(3 + i, method.parameterType(2 + i));
        }
        for (Map.Entry<String, List<T>> entry : snapshot.entrySet()) {
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
        if (resultType == ValueType.VOID) {
            pe.exit();
        } else if (resultType instanceof ValueType.Primitive) {
            pe.constant(0).returnValue();
        } else {
            pe.constantNull(resultType).returnValue();
        }
        agent.submitMethod(method, pe.getProgram());
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
            if (typeResolves(agent, field.getType())) {
                declared.add(field);
            }
        }
        fields.put(name, declared);
        agent.linkClass(name);
        for (FieldReader field : declared) {
            linkType(agent, field.getType());
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
            ValueType type = field.getType();
            if (type instanceof ValueType.Object) {
                fieldTypeDep.getResult().getClassValueNode()
                        .propagate(agent.getType(((ValueType.Object) type).getClassName()));
            } else if (type instanceof ValueType.Array) {
                fieldTypeDep.getResult().getClassValueNode().propagate(agent.getType(type.toString()));
            }
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
                fieldSetDep.getVariable(4).connect(dep.getValue());
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
