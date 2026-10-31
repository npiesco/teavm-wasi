/*
 *  Copyright 2015 Alexey Andreev.
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

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformAnnotationProvider;
import org.teavm.platform.PlatformClass;

public class AnnotationDependencyListener extends AbstractDependencyListener {
    private static final MethodReference GET_ANNOTATIONS_METHOD = new MethodReference(
            Platform.class, "getAnnotations", PlatformClass.class, Annotation[].class);
    private static final String ANNOTATIONS_READER_SUFFIX = "$$__annotations__$$";
    private static final ValueType CLASS = ValueType.object("java.lang.Class");
    private static final ValueType ANNOTATIONS = ValueType.parse(Annotation[].class);
    // WebAssembly natives of java.lang.Class, generated here (Platform.getAnnotations is JavaScript-only).
    private static final MethodReference WASM_DECLARED = new MethodReference("java.lang.Class",
            "wasmDeclaredAnnotations", CLASS, ANNOTATIONS);
    private static final MethodReference WASM_INHERITED = new MethodReference("java.lang.Class",
            "wasmInheritedAnnotation", CLASS, ValueType.BOOLEAN);
    private static final MethodReference WASM_CONTAINED = new MethodReference("java.lang.Class",
            "wasmContainedAnnotations", ValueType.parse(Annotation.class), ANNOTATIONS);
    private MethodDependency wasmDeclaredDep;
    private final Map<String, MethodDependency> readers = new LinkedHashMap<>();
    private final Set<String> annotationTypes = new LinkedHashSet<>();

    private String getAnnotationImplementor(DependencyAgent agent, String annotationType) {
        String implementorName = annotationType + "$$_impl";
        if (agent.getClassSource().get(implementorName) == null) {
            ClassHolder implementor = createImplementor(agent, annotationType, implementorName);
            agent.submitClass(implementor);
        }
        return implementorName;
    }

    private ClassHolder createImplementor(DependencyAgent agent, String annotationType, String implementorName) {
        ClassHierarchy hierarchy = agent.getClassHierarchy();
        ClassHolder implementor = new ClassHolder(implementorName);
        implementor.setParent("java.lang.Object");
        implementor.getInterfaces().add(annotationType);
        implementor.getModifiers().add(ElementModifier.FINAL);
        implementor.setLevel(AccessLevel.PUBLIC);

        ClassReader annotation = hierarchy.getClassSource().get(annotationType);
        if (annotation == null) {
            return implementor;
        }

        List<ValueType> ctorSignature = new ArrayList<>();
        for (MethodReader methodDecl : annotation.getMethods()) {
            if (methodDecl.hasModifier(ElementModifier.STATIC)) {
                continue;
            }
            FieldHolder field = new FieldHolder("$" + methodDecl.getName());
            field.setType(methodDecl.getResultType());
            field.setLevel(AccessLevel.PRIVATE);
            implementor.addField(field);

            MethodHolder accessor = new MethodHolder(methodDecl.getDescriptor());
            ProgramEmitter pe = ProgramEmitter.create(accessor, hierarchy);
            ValueEmitter thisVal = pe.var(0, implementor);
            ValueEmitter result = thisVal.getField(field.getName(), field.getType());
            if (field.getType() instanceof ValueType.Array) {
                result = result.cloneArray();
            }
            result.returnValue();
            implementor.addMethod(accessor);

            ctorSignature.add(field.getType());
        }
        ctorSignature.add(ORDER_TYPE);
        ctorSignature.add(ValueType.VOID);
        FieldHolder orderField = new FieldHolder(ORDER_FIELD);
        orderField.setType(ORDER_TYPE);
        orderField.setLevel(AccessLevel.PRIVATE);
        implementor.addField(orderField);

        MethodHolder ctor = new MethodHolder("<init>", ctorSignature.toArray(new ValueType[0]));
        ProgramEmitter pe = ProgramEmitter.create(ctor, hierarchy);
        ValueEmitter thisVar = pe.var(0, implementor);
        thisVar.invokeSpecial(Object.class, "<init>");
        int index = 1;
        for (MethodReader methodDecl : annotation.getMethods()) {
            if (methodDecl.hasModifier(ElementModifier.STATIC)) {
                continue;
            }
            ValueEmitter param = pe.var(index++, methodDecl.getResultType());
            thisVar.setField("$" + methodDecl.getName(), param);
        }
        thisVar.setField(ORDER_FIELD, pe.var(index, ORDER_TYPE));
        pe.exit();
        implementor.addMethod(ctor);

        List<MethodReader> members = new ArrayList<>();
        for (MethodReader methodDecl : annotation.getMethods()) {
            if (!methodDecl.hasModifier(ElementModifier.STATIC)) {
                members.add(methodDecl);
            }
        }
        implementor.addMethod(createToString(hierarchy, implementor, members, canonicalName(agent, annotation)));
        implementor.addMethod(createHashCode(hierarchy, implementor, members));
        implementor.addMethod(createEquals(hierarchy, implementor, annotationType, members));

        MethodHolder annotTypeMethod = new MethodHolder("annotationType", ValueType.parse(Class.class));
        pe = ProgramEmitter.create(annotTypeMethod, hierarchy);
        pe.constant(ValueType.object(annotationType)).returnValue();
        implementor.addMethod(annotTypeMethod);

        return implementor;
    }

    private static final String ORDER_FIELD = "$$order";
    private static final ValueType ORDER_TYPE = ValueType.parse(String[].class);
    private static final String SUPPORT = "org.teavm.classlib.impl.AnnotationSupport";

    private static ValueEmitter[] memberArrays(ProgramEmitter pe, ValueEmitter thisVar, List<MethodReader> members) {
        ValueEmitter names = pe.constructArray(String.class, members.size());
        ValueEmitter values = pe.constructArray(Object.class, members.size());
        for (int i = 0; i < members.size(); ++i) {
            MethodReader member = members.get(i);
            names.setElement(i, pe.constant(member.getName()));
            values.setElement(i, box(pe, thisVar.getField("$" + member.getName(), member.getResultType()),
                    member.getResultType()));
        }
        return new ValueEmitter[] { names, values };
    }

    private static ValueEmitter box(ProgramEmitter pe, ValueEmitter value, ValueType type) {
        if (!(type instanceof ValueType.Primitive)) {
            return value.cast(Object.class);
        }
        Class<?> wrapper;
        Class<?> primitive;
        switch (((ValueType.Primitive) type).getKind()) {
            case BOOLEAN:
                wrapper = Boolean.class;
                primitive = boolean.class;
                break;
            case BYTE:
                wrapper = Byte.class;
                primitive = byte.class;
                break;
            case SHORT:
                wrapper = Short.class;
                primitive = short.class;
                break;
            case CHARACTER:
                wrapper = Character.class;
                primitive = char.class;
                break;
            case INTEGER:
                wrapper = Integer.class;
                primitive = int.class;
                break;
            case LONG:
                wrapper = Long.class;
                primitive = long.class;
                break;
            case FLOAT:
                wrapper = Float.class;
                primitive = float.class;
                break;
            default:
                wrapper = Double.class;
                primitive = double.class;
                break;
        }
        return pe.invoke(new MethodReference(wrapper, "valueOf", primitive, wrapper), value).cast(Object.class);
    }

    private static MethodHolder createToString(ClassHierarchy hierarchy, ClassHolder implementor,
            List<MethodReader> members, String canonicalName) {
        MethodHolder method = new MethodHolder("toString", ValueType.parse(String.class));
        method.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(method, hierarchy);
        ValueEmitter thisVar = pe.var(0, implementor);
        ValueEmitter[] arrays = memberArrays(pe, thisVar, members);
        pe.invoke(SUPPORT, "toString", ValueType.parse(String.class), pe.constant(canonicalName),
                thisVar.getField(ORDER_FIELD, ORDER_TYPE), arrays[0], arrays[1]).returnValue();
        return method;
    }

    private static MethodHolder createHashCode(ClassHierarchy hierarchy, ClassHolder implementor,
            List<MethodReader> members) {
        MethodHolder method = new MethodHolder("hashCode", ValueType.INTEGER);
        method.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(method, hierarchy);
        ValueEmitter[] arrays = memberArrays(pe, pe.var(0, implementor), members);
        pe.invoke(SUPPORT, "hashCode", ValueType.INTEGER, arrays[0], arrays[1]).returnValue();
        return method;
    }

    // As on the JDK: equal to any instance of the annotation type whose members all have equal values.
    private static MethodHolder createEquals(ClassHierarchy hierarchy, ClassHolder implementor, String annotationType,
            List<MethodReader> members) {
        MethodHolder method = new MethodHolder("equals", ValueType.parse(Object.class), ValueType.BOOLEAN);
        method.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(method, hierarchy);
        ValueEmitter thisVar = pe.var(0, implementor);
        ValueEmitter other = pe.var(1, Object.class);
        pe.when(other.isSame(thisVar.cast(Object.class))).thenDo(() -> pe.constant(1).returnValue());
        pe.when(other.instanceOf(ValueType.object(annotationType)).isFalse())
                .thenDo(() -> pe.constant(0).returnValue());
        ValueEmitter typed = other.cast(ValueType.object(annotationType));
        for (MethodReader member : members) {
            ValueEmitter ours = box(pe, thisVar.getField("$" + member.getName(), member.getResultType()),
                    member.getResultType());
            ValueEmitter theirs = box(pe, typed.invokeVirtual(member.getName(), member.getResultType()),
                    member.getResultType());
            pe.when(pe.invoke(SUPPORT, "memberValueEquals", ValueType.BOOLEAN, ours, theirs).isFalse())
                    .thenDo(() -> pe.constant(0).returnValue());
        }
        pe.constant(1).returnValue();
        return method;
    }

    private static String canonicalName(DependencyAgent agent, ClassReader annotation) {
        Class<?> host = hostClass(agent, annotation.getName());
        if (host != null && host.getCanonicalName() != null) {
            return host.getCanonicalName();
        }
        return annotation.getName().replace('$', '.');
    }

    private static Class<?> hostClass(DependencyAgent agent, String name) {
        try {
            return Class.forName(name, false, agent.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    // The JDK keeps an annotation's member values in a LinkedHashMap seeded from the annotation type's defaults,
    // themselves a HashMap; values given explicitly follow in class-file order. Rebuilding those maps yields the
    // JDK's member order. The JDK fills the defaults map in getDeclaredMethods() order, which HotSpot derives from
    // symbol addresses of the running JVM; it only matters for members sharing a hash bucket, and declaration order
    // stands in for it here.
    private static String[] memberOrder(AnnotationReader annotation, ClassReader annotationClass) {
        Map<String, Boolean> defaults = new HashMap<>(0);
        for (MethodReader member : annotationClass.getMethods()) {
            if (member.parameterCount() == 0 && !member.hasModifier(ElementModifier.STATIC)
                    && member.getAnnotationDefault() != null) {
                defaults.put(member.getName(), Boolean.TRUE);
            }
        }
        Map<String, Boolean> values = new LinkedHashMap<>(defaults);
        for (String name : annotation.getAvailableFields()) {
            values.put(name, Boolean.TRUE);
        }
        return values.keySet().toArray(new String[0]);
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method) {
        ValueType type = method.getMethod().getResultType();
        while (type instanceof ValueType.Array) {
            type = ((ValueType.Array) type).getItemType();
        }
        if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            ClassReader cls = agent.getClassSource().get(className);
            if (cls != null && cls.hasModifier(ElementModifier.ANNOTATION)) {
                agent.linkClass(className);
            }
        }

        if (method.getMethod().hasModifier(ElementModifier.STATIC)
                && method.getMethod().getName().equals("$$__readAnnotations__$$")) {
            ClassReader cls = agent.getClassSource().get(method.getReference().getClassName());
            if (cls != null) {
                for (AnnotationReader annotation : cls.getAnnotations().all()) {
                    agent.linkClass(annotation.getType());
                }
            }
        }

        MethodReference methodRef = method.getMethod().getReference();
        if (methodRef.getClassName().equals("java.lang.Class") && methodRef.getName().equals("getAnnotations")) {
            reachGetAnnotations(agent, method.getVariable(0));
        }
        if (methodRef.equals(WASM_DECLARED)) {
            wasmDeclaredDep = method;
            method.getResult().propagate(agent.getType("[Ljava/lang/annotation/Annotation;"));
            for (MethodDependency reader : readers.values()) {
                reader.getResult().getArrayItem().connect(method.getResult().getArrayItem());
            }
            reachGetAnnotations(agent, method.getVariable(1));
        } else if (methodRef.equals(WASM_CONTAINED)) {
            method.getResult().propagate(agent.getType("[Ljava/lang/annotation/Annotation;"));
        }
    }

    private void reachGetAnnotations(DependencyAgent agent, DependencyNode node) {
        node.getClassValueNode().addConsumer(type -> {
            String className = type.getName();
            if (className.endsWith(ANNOTATIONS_READER_SUFFIX)) {
                return;
            }

            ClassReader cls = agent.getClassSource().get(className);
            if (cls == null) {
                return;
            }

            for (AnnotationReader annotation : cls.getAnnotations().all()) {
                agent.linkClass(annotation.getType());
                annotationTypes.add(annotation.getType());
            }

            createAnnotationClass(agent, className);
        });
    }

    private void createAnnotationClass(DependencyAgent agent, String className) {
        String readerClassName = className + ANNOTATIONS_READER_SUFFIX;
        if (agent.getClassSource().get(readerClassName) != null) {
            return;
        }

        ClassHolder cls = new ClassHolder(readerClassName);
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setOwnerName("java.lang.Object");
        cls.getInterfaces().add(PlatformAnnotationProvider.class.getName());

        MethodHolder ctor = new MethodHolder("<init>", ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(ctor, agent.getClassHierarchy());
        ValueEmitter thisVar = pe.var(0, cls);
        thisVar.invokeSpecial(Object.class, "<init>").exit();

        ClassReader annotatedClass = agent.getClassSource().get(className);
        cls.addMethod(ctor);
        MethodHolder reader = addReader(agent, annotatedClass);
        cls.addMethod(reader);

        agent.submitClass(cls);

        MethodDependency ctorDep = agent.linkMethod(ctor.getReference());
        ctorDep.getVariable(0).propagate(agent.getType(readerClassName));
        ctorDep.use();

        MethodDependency annotationsDep = agent.linkMethod(GET_ANNOTATIONS_METHOD);
        MethodDependency readerDep = agent.linkMethod(reader.getReference());
        readerDep.getVariable(0).propagate(agent.getType(readerClassName));
        readerDep.getResult().getArrayItem().connect(annotationsDep.getResult().getArrayItem());
        readerDep.use();
        readers.put(className, readerDep);
        if (wasmDeclaredDep != null) {
            readerDep.getResult().getArrayItem().connect(wasmDeclaredDep.getResult().getArrayItem());
        }
    }

    @Override
    public void completing(DependencyAgent agent) {
        if (agent.getMethod(WASM_DECLARED) != null) {
            ProgramEmitter pe = ProgramEmitter.create(WASM_DECLARED.getDescriptor(), agent.getClassHierarchy());
            ValueEmitter cls = pe.var(1, CLASS);
            for (String className : readers.keySet()) {
                // A class declaring no annotation reads as the empty array the dispatcher already defaults to, so
                // only annotated classes get a case (every class reaching getAnnotations has a reader).
                ClassReader annotated = agent.getClassSource().get(className);
                if (annotated == null || !annotated.getAnnotations().all().iterator().hasNext()) {
                    continue;
                }
                String reader = className + ANNOTATIONS_READER_SUFFIX;
                pe.when(cls.isSame(pe.constant(ValueType.object(className)))).thenDo(() ->
                        pe.construct(reader).invokeVirtual("getAnnotations", ANNOTATIONS).returnValue());
            }
            pe.constructArray(Annotation.class, 0).returnValue();
            agent.submitMethod(WASM_DECLARED, pe.getProgram());
        }
        if (agent.getMethod(WASM_INHERITED) != null) {
            ProgramEmitter pe = ProgramEmitter.create(WASM_INHERITED.getDescriptor(), agent.getClassHierarchy());
            ValueEmitter type = pe.var(1, CLASS);
            for (String annotationType : annotationTypes) {
                ClassReader cls = agent.getClassSource().get(annotationType);
                if (cls != null && cls.getAnnotations().get(Inherited.class.getName()) != null) {
                    pe.when(type.isSame(pe.constant(ValueType.object(annotationType))))
                            .thenDo(() -> pe.constant(1).returnValue());
                }
            }
            pe.constant(0).returnValue();
            agent.submitMethod(WASM_INHERITED, pe.getProgram());
        }
        if (agent.getMethod(WASM_CONTAINED) != null) {
            // A container annotation (the target of @Repeatable) holds its repeated annotations in value().
            ProgramEmitter pe = ProgramEmitter.create(WASM_CONTAINED.getDescriptor(), agent.getClassHierarchy());
            ValueEmitter container = pe.var(1, Annotation.class);
            for (String annotationType : annotationTypes) {
                ValueType resultType = containedType(agent, annotationType);
                if (resultType == null) {
                    continue;
                }
                ValueType memberType = resultType;
                pe.when(container.instanceOf(ValueType.object(annotationType)).isTrue()).thenDo(() ->
                        container.cast(ValueType.object(annotationType)).invokeVirtual("value", memberType)
                                .cast(ANNOTATIONS).returnValue());
            }
            pe.constantNull(ANNOTATIONS).returnValue();
            agent.submitMethod(WASM_CONTAINED, pe.getProgram());
        }
    }

    // The type of value() when it is an array of annotations, which is what makes an annotation type a container.
    private static ValueType containedType(DependencyAgent agent, String annotationType) {
        ClassReader cls = agent.getClassSource().get(annotationType);
        if (cls == null) {
            return null;
        }
        for (MethodReader member : cls.getMethods()) {
            if (member.getName().equals("value") && member.parameterCount() == 0
                    && member.getResultType() instanceof ValueType.Array) {
                ValueType item = ((ValueType.Array) member.getResultType()).getItemType();
                if (item instanceof ValueType.Object) {
                    ClassReader itemClass = agent.getClassSource().get(((ValueType.Object) item).getClassName());
                    if (itemClass != null && itemClass.hasModifier(ElementModifier.ANNOTATION)) {
                        return member.getResultType();
                    }
                }
            }
        }
        return null;
    }

    private MethodHolder addReader(DependencyAgent agent, ClassReader cls) {
        MethodHolder readerMethod = new MethodHolder("getAnnotations", ValueType.parse(Annotation[].class));
        readerMethod.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(readerMethod, agent.getClassHierarchy());

        List<AnnotationReader> annotations = new ArrayList<>();
        for (AnnotationReader annot : cls.getAnnotations().all()) {
            ClassReader annotType = agent.getClassSource().get(annot.getType());
            if (annotType == null) {
                continue;
            }

            AnnotationReader retention = annotType.getAnnotations().get(Retention.class.getName());
            if (retention != null) {
                String retentionPolicy = retention.getValue("value").getEnumValue().getFieldName();
                if (retentionPolicy.equals("RUNTIME")) {
                    annotations.add(annot);
                }
            }
        }

        ValueEmitter array = pe.constructArray(Annotation.class, annotations.size());
        for (int i = 0; i < annotations.size(); ++i) {
            array.setElement(i, generateAnnotationInstance(agent, pe, annotations.get(i)));
        }

        array.returnValue();

        return readerMethod;
    }

    private ValueEmitter generateAnnotationInstance(DependencyAgent agent, ProgramEmitter pe,
            AnnotationReader annotation) {
        ClassReader annotationClass = agent.getClassSource().get(annotation.getType());
        if (annotationClass == null) {
            return pe.constantNull(ValueType.object(annotation.getType()));
        }

        String className = getAnnotationImplementor(agent, annotation.getType());
        List<ValueEmitter> params = new ArrayList<>();
        for (MethodReader methodDecl : annotationClass.getMethods()) {
            if (methodDecl.hasModifier(ElementModifier.STATIC)) {
                continue;
            }
            AnnotationValue value = annotation.getValue(methodDecl.getName());
            if (value == null) {
                value = methodDecl.getAnnotationDefault();
            }
            params.add(generateAnnotationValue(agent, pe, methodDecl.getResultType(), value)
                    .cast(methodDecl.getResultType()));
        }

        String[] order = memberOrder(annotation, annotationClass);
        ValueEmitter orderArray = pe.constructArray(String.class, order.length);
        for (int i = 0; i < order.length; ++i) {
            orderArray.setElement(i, pe.constant(order[i]));
        }
        params.add(orderArray);
        return pe.construct(className, params.toArray(new ValueEmitter[0]));
    }

    private ValueEmitter generateAnnotationValue(DependencyAgent agent, ProgramEmitter pe, ValueType type,
            AnnotationValue value) {
        switch (value.getType()) {
            case AnnotationValue.BOOLEAN:
                return pe.constant(value.getBoolean() ? 1 : 0);
            case AnnotationValue.CHAR:
                return pe.constant(value.getChar());
            case AnnotationValue.BYTE:
                return pe.constant(value.getByte());
            case AnnotationValue.SHORT:
                return pe.constant(value.getShort());
            case AnnotationValue.INT:
                return pe.constant(value.getInt());
            case AnnotationValue.LONG:
                return pe.constant(value.getLong());
            case AnnotationValue.FLOAT:
                return pe.constant(value.getFloat());
            case AnnotationValue.DOUBLE:
                return pe.constant(value.getDouble());
            case AnnotationValue.STRING:
                return pe.constant(value.getString());
            case AnnotationValue.LIST: {
                List<AnnotationValue> list = value.getList();
                ValueType itemType = ((ValueType.Array) type).getItemType();
                ValueEmitter array = pe.constructArray(itemType, list.size());
                for (int i = 0; i < list.size(); ++i) {
                    array.setElement(i, generateAnnotationValue(agent, pe, itemType, list.get(i)));
                }
                return array;
            }
            case AnnotationValue.ENUM:
                pe.initClass(value.getEnumValue().getClassName());
                return pe.getField(value.getEnumValue(), type);
            case AnnotationValue.CLASS:
                return pe.constant(value.getJavaClass());
            case AnnotationValue.ANNOTATION:
                return generateAnnotationInstance(agent, pe, value.getAnnotation());
            default:
                throw new IllegalArgumentException("Unknown annotation value type: " + value.getType());
        }
    }
}
