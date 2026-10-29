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
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.AccessLevel;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
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
    // Mirrors TConstructor.WASM_CLASS_ABSTRACT / WASM_CLASS_ENUM.
    private static final int CLASS_ABSTRACT = 1 << 20;
    private static final int CLASS_ENUM = 1 << 21;

    private final Map<String, List<MethodReader>> constructors = new LinkedHashMap<>();
    private Characteristics characteristics;
    private MethodDependency newInstanceDep;
    private MethodDependency parameterTypesDep;

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
        if (agent.getMethod(COUNT) != null) {
            ProgramEmitter pe = ProgramEmitter.create(COUNT.getDescriptor(), agent.getClassHierarchy());
            ValueEmitter cls = pe.var(1, CLASS);
            for (Map.Entry<String, List<MethodReader>> entry : snapshot.entrySet()) {
                int count = entry.getValue().size();
                pe.when(cls.isSame(pe.constant(ValueType.object(entry.getKey()))))
                        .thenDo(() -> pe.constant(count).returnValue());
            }
            pe.constant(0).returnValue();
            agent.submitMethod(COUNT, pe.getProgram());
        }
        submit(agent, MODIFIERS, ValueType.INTEGER, snapshot, true, (pe, owner, ctor, args) ->
                pe.constant(modifiers(owner, ctor)).returnValue());
        submit(agent, PARAMETER_TYPES, ValueType.arrayOf(CLASS), snapshot, true, (pe, owner, ctor, args) -> {
            ValueType[] parameters = ctor.getParameterTypes();
            ValueEmitter array = pe.constructArray(CLASS, parameters.length);
            for (int i = 0; i < parameters.length; ++i) {
                array.setElement(i, pe.constant(parameters[i]));
            }
            array.returnValue();
        });
        submit(agent, NEW_INSTANCE, OBJECT, snapshot, false, (pe, owner, ctor, args) -> {
            ValueType[] parameters = ctor.getParameterTypes();
            ValueEmitter[] values = new ValueEmitter[parameters.length];
            for (int i = 0; i < parameters.length; ++i) {
                values[i] = unbox(args.getElement(i), parameters[i]);
            }
            pe.construct(owner.getName(), values).returnValue();
        });
    }

    private interface ConstructorEmitter {
        void emit(ProgramEmitter pe, ClassReader owner, MethodReader constructor, ValueEmitter arguments);
    }

    private void submit(DependencyAgent agent, MethodReference method, ValueType resultType,
            Map<String, List<MethodReader>> snapshot, boolean metadata, ConstructorEmitter emitter) {
        if (agent.getMethod(method) == null) {
            return;
        }
        ProgramEmitter pe = ProgramEmitter.create(method.getDescriptor(), agent.getClassHierarchy());
        ValueEmitter cls = pe.var(1, CLASS);
        ValueEmitter index = pe.var(2, ValueType.INTEGER);
        ValueEmitter arguments = method.parameterCount() > 2 ? pe.var(3, ValueType.arrayOf(OBJECT)) : null;
        for (Map.Entry<String, List<MethodReader>> entry : snapshot.entrySet()) {
            ClassReader owner = agent.getClassSource().get(entry.getKey());
            if (!metadata && !isInstantiable(owner)) {
                continue;
            }
            List<MethodReader> declared = entry.getValue();
            pe.when(cls.isSame(pe.constant(ValueType.object(entry.getKey())))).thenDo(() -> {
                for (int i = 0; i < declared.size(); ++i) {
                    MethodReader ctor = declared.get(i);
                    pe.when(index.isEqualTo(pe.constant(i))).thenDo(() -> emitter.emit(pe, owner, ctor, arguments));
                }
            });
        }
        if (resultType instanceof ValueType.Primitive) {
            pe.constant(0).returnValue();
        } else {
            pe.constantNull(resultType).returnValue();
        }
        agent.submitMethod(method, pe.getProgram());
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
