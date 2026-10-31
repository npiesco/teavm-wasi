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
import java.util.Collections;
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
import org.teavm.model.BasicBlockReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.IncomingReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.PhiReader;
import org.teavm.model.ProgramReader;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.InvocationType;

/**
 * java.lang.reflect.Proxy ahead of time: a proxy class is generated for every list of interfaces that can reach
 * Proxy.newProxyInstance. The interfaces array is evaluated symbolically in the method passing it (class constants,
 * stores at constant indexes, arrays copied in at constant offsets, static final arrays, phis); what depends on the
 * method's own parameters makes it a forwarder whose callers are evaluated in turn, until every list is constant.
 */
public class ProxySupport extends AbstractDependencyListener {
    private static final String PROXY = "java.lang.reflect.Proxy";
    private static final ValueType CLASS = ValueType.object("java.lang.Class");
    private static final ValueType CLASS_ARRAY = ValueType.arrayOf(CLASS);
    private static final ValueType OBJECT = ValueType.object("java.lang.Object");
    private static final ValueType HANDLER = ValueType.object("java.lang.reflect.InvocationHandler");
    private static final ValueType METHOD = ValueType.object("java.lang.reflect.Method");
    private static final MethodReference NEW_PROXY_INSTANCE = new MethodReference(PROXY, "newProxyInstance",
            ValueType.object("java.lang.ClassLoader"), CLASS_ARRAY, HANDLER, OBJECT);
    private static final MethodReference CREATE_PROXY = new MethodReference(PROXY, "createProxy", CLASS_ARRAY,
            HANDLER, OBJECT);
    private static final MethodReference IS_GENERATED = new MethodReference(PROXY, "isGeneratedProxyClass", CLASS,
            ValueType.BOOLEAN);
    private static final MethodReference INVOKE_HANDLER = new MethodReference(PROXY, "invokeHandler",
            ValueType.object(PROXY), METHOD, ValueType.arrayOf(OBJECT), OBJECT);
    private static final MethodReference GET_METHOD = new MethodReference("java.lang.Class", "getMethod",
            ValueType.object("java.lang.String"), CLASS_ARRAY, METHOD);
    private static final MethodReference PROXY_INIT = new MethodReference(PROXY, "<init>", HANDLER, ValueType.VOID);
    private static final int MAX_COMBINATIONS = 256;
    private static final int MAX_DEPTH = 8;

    // A template is a list of elements: "C:name" a class constant, "P:i" the i-th argument (a Class), "A:i" the
    // elements of the i-th argument (a Class[]).
    private final Map<MethodReference, Set<List<String>>> templates = new HashMap<>();
    private final Map<MethodReference, List<CallSite>> callSites = new HashMap<>();
    private final Map<List<String>, String> proxyClasses = new LinkedHashMap<>();
    private final Map<MethodReference, Definitions> definitions = new HashMap<>();
    private MethodDependency newProxyDep;
    private int proxyCounter;

    private static final class CallSite {
        final MethodReader caller;
        final List<? extends VariableReader> arguments;
        final CallLocation location;

        CallSite(MethodReader caller, List<? extends VariableReader> arguments, CallLocation location) {
            this.caller = caller;
            this.arguments = arguments;
            this.location = location;
        }
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method) {
        MethodReference ref = method.getReference();
        if (ref.equals(NEW_PROXY_INSTANCE)) {
            newProxyDep = method;
            for (String proxyClass : proxyClasses.values()) {
                linkProxy(agent, proxyClass);
            }
            addTemplate(agent, ref, Collections.singletonList("A:1"));
        }
        MethodReader reader = method.getMethod();
        if (reader == null || reader.getProgram() == null) {
            return;
        }
        ProgramReader program = reader.getProgram();
        for (BasicBlockReader block : program.getBasicBlocks()) {
            block.readAllInstructions(new AbstractInstructionReader() {
                @Override
                public void invoke(VariableReader receiver, VariableReader instance, MethodReference target,
                        List<? extends VariableReader> arguments, InvocationType type) {
                    if (!takesClasses(target)) {
                        return;
                    }
                    CallSite site = new CallSite(reader, new ArrayList<>(arguments), new CallLocation(ref));
                    callSites.computeIfAbsent(target, k -> new ArrayList<>()).add(site);
                    Set<List<String>> known = templates.get(target);
                    if (known != null) {
                        for (List<String> template : new ArrayList<>(known)) {
                            resolve(agent, site, template, 0);
                        }
                    }
                }
            });
        }
    }

    private static boolean takesClasses(MethodReference method) {
        for (ValueType parameter : method.getParameterTypes()) {
            if (parameter.equals(CLASS) || parameter.equals(CLASS_ARRAY)) {
                return true;
            }
        }
        return false;
    }

    private void addTemplate(DependencyAgent agent, MethodReference method, List<String> template) {
        Set<List<String>> set = templates.computeIfAbsent(method, k -> new LinkedHashSet<>());
        if (set.size() >= MAX_COMBINATIONS || !set.add(template)) {
            return;
        }
        for (CallSite site : new ArrayList<>(callSites.getOrDefault(method, Collections.emptyList()))) {
            resolve(agent, site, template, 0);
        }
    }

    // Substitutes the call site's arguments into a template of the callee: a constant list is a proxy to generate,
    // anything else a template of the caller.
    private void resolve(DependencyAgent agent, CallSite site, List<String> template, int depth) {
        List<List<String>> partial = new ArrayList<>();
        partial.add(new ArrayList<>());
        Definitions defs = definitions(site.caller);
        for (String element : template) {
            Set<List<String>> options;
            if (element.startsWith("C:")) {
                options = Collections.singleton(Collections.singletonList(element));
            } else {
                VariableReader argument = site.arguments.get(Integer.parseInt(element.substring(2)));
                options = element.startsWith("P:")
                        ? singletons(defs.classValues(argument.getIndex(), depth))
                        : defs.arrayValues(argument.getIndex(), depth);
            }
            if (options == null) {
                agent.getDiagnostics().warning(site.location, "Proxy interfaces passed here could not be determined "
                        + "at compile time; no proxy class is generated for them");
                return;
            }
            List<List<String>> next = new ArrayList<>();
            for (List<String> prefix : partial) {
                for (List<String> option : options) {
                    List<String> combined = new ArrayList<>(prefix);
                    combined.addAll(option);
                    next.add(combined);
                    if (next.size() > MAX_COMBINATIONS) {
                        return;
                    }
                }
            }
            partial = next;
        }
        for (List<String> list : partial) {
            boolean constant = true;
            for (String element : list) {
                if (!element.startsWith("C:")) {
                    constant = false;
                    break;
                }
            }
            if (constant) {
                registerProxy(agent, list);
            } else if (depth < MAX_DEPTH) {
                addTemplate(agent, site.caller.getReference(), list);
            }
        }
    }

    private static Set<List<String>> singletons(Set<String> values) {
        if (values == null) {
            return null;
        }
        Set<List<String>> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(Collections.singletonList(value));
        }
        return result;
    }

    private Definitions definitions(MethodReader method) {
        return definitions.computeIfAbsent(method.getReference(), k -> new Definitions(method));
    }

    // What each variable of a method's program is, as far as interface lists are concerned.
    private final class Definitions extends AbstractInstructionReader {
        final Map<Integer, String> classConstants = new HashMap<>();
        final Map<Integer, Integer> aliases = new HashMap<>();
        final Set<Integer> arrays = new LinkedHashSet<>();
        final Map<Integer, List<int[]>> stores = new HashMap<>();
        final Map<Integer, List<int[]>> copies = new HashMap<>();
        final Map<Integer, Integer> integers = new HashMap<>();
        final Map<Integer, FieldReference> staticFields = new HashMap<>();
        final Map<Integer, List<Integer>> phis = new HashMap<>();
        final Map<FieldReference, Integer> staticStores = new HashMap<>();
        final int parameterCount;

        Definitions(MethodReader method) {
            parameterCount = method.parameterCount();
            ProgramReader program = method.getProgram();
            if (program == null) {
                return;
            }
            for (BasicBlockReader block : program.getBasicBlocks()) {
                for (PhiReader phi : block.readPhis()) {
                    List<Integer> incoming = new ArrayList<>();
                    for (IncomingReader in : phi.readIncomings()) {
                        incoming.add(in.getValue().getIndex());
                    }
                    phis.put(phi.getReceiver().getIndex(), incoming);
                }
                block.readAllInstructions(this);
            }
        }

        @Override
        public void classConstant(VariableReader receiver, ValueType cst) {
            if (cst instanceof ValueType.Object) {
                classConstants.put(receiver.getIndex(), "C:" + ((ValueType.Object) cst).getClassName());
            }
        }

        @Override
        public void integerConstant(VariableReader receiver, int cst) {
            integers.put(receiver.getIndex(), cst);
        }

        @Override
        public void assign(VariableReader receiver, VariableReader assignee) {
            aliases.put(receiver.getIndex(), assignee.getIndex());
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, ValueType targetType) {
            aliases.put(receiver.getIndex(), value.getIndex());
        }

        @Override
        public void nullCheck(VariableReader receiver, VariableReader value) {
            aliases.put(receiver.getIndex(), value.getIndex());
        }

        @Override
        public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
            aliases.put(receiver.getIndex(), array.getIndex());
        }

        @Override
        public void cloneArray(VariableReader receiver, VariableReader array) {
            aliases.put(receiver.getIndex(), array.getIndex());
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
            arrays.add(receiver.getIndex());
        }

        @Override
        public void putElement(VariableReader array, VariableReader index, VariableReader value,
                ArrayElementType type) {
            stores.computeIfAbsent(root(array.getIndex()), k -> new ArrayList<>())
                    .add(new int[] { index.getIndex(), value.getIndex() });
        }

        @Override
        public void getField(VariableReader receiver, VariableReader instance, FieldReference field,
                ValueType fieldType) {
            if (instance == null) {
                staticFields.put(receiver.getIndex(), field);
            }
        }

        @Override
        public void putField(VariableReader instance, FieldReference field, VariableReader value,
                ValueType fieldType) {
            if (instance == null) {
                staticStores.put(field, value.getIndex());
            }
        }

        @Override
        public void invoke(VariableReader receiver, VariableReader instance, MethodReference method,
                List<? extends VariableReader> arguments, InvocationType type) {
            if (method.getClassName().equals("java.lang.System") && method.getName().equals("arraycopy")) {
                copies.computeIfAbsent(root(arguments.get(2).getIndex()), k -> new ArrayList<>())
                        .add(new int[] { arguments.get(0).getIndex(), arguments.get(1).getIndex(),
                                arguments.get(3).getIndex() });
            }
        }

        private int root(int variable) {
            Integer alias = aliases.get(variable);
            int guard = 0;
            while (alias != null && guard++ < 64) {
                variable = alias;
                alias = aliases.get(variable);
            }
            return variable;
        }

        private boolean isParameter(int variable) {
            return variable >= 1 && variable <= parameterCount;
        }

        Set<String> classValues(int variable, int depth) {
            variable = root(variable);
            if (classConstants.containsKey(variable)) {
                return Collections.singleton(classConstants.get(variable));
            }
            if (isParameter(variable)) {
                return Collections.singleton("P:" + (variable - 1));
            }
            List<Integer> incoming = phis.get(variable);
            if (incoming != null && depth < MAX_DEPTH) {
                Set<String> result = new LinkedHashSet<>();
                for (int value : incoming) {
                    Set<String> part = classValues(value, depth + 1);
                    if (part == null) {
                        return null;
                    }
                    result.addAll(part);
                }
                return result;
            }
            return null;
        }

        Set<List<String>> arrayValues(int variable, int depth) {
            variable = root(variable);
            if (isParameter(variable)) {
                return Collections.singleton(Collections.singletonList("A:" + (variable - 1)));
            }
            List<Integer> incoming = phis.get(variable);
            if (incoming != null && depth < MAX_DEPTH) {
                Set<List<String>> result = new LinkedHashSet<>();
                for (int value : incoming) {
                    Set<List<String>> part = arrayValues(value, depth + 1);
                    if (part == null) {
                        return null;
                    }
                    result.addAll(part);
                }
                return result;
            }
            FieldReference field = staticFields.get(variable);
            if (field != null && depth < MAX_DEPTH) {
                return staticArray(field, depth + 1);
            }
            if (!arrays.contains(variable)) {
                return null;
            }
            // Elements by position: single stores at constant indexes and arrays copied in at constant offsets.
            Map<Integer, Set<List<String>>> parts = new java.util.TreeMap<>();
            for (int[] store : stores.getOrDefault(variable, Collections.emptyList())) {
                Integer index = integers.get(root(store[0]));
                Set<List<String>> values = singletons(classValues(store[1], depth + 1));
                if (index == null || values == null) {
                    return null;
                }
                parts.put(index, values);
            }
            for (int[] copy : copies.getOrDefault(variable, Collections.emptyList())) {
                Integer sourcePosition = integers.get(root(copy[1]));
                Integer position = integers.get(root(copy[2]));
                Set<List<String>> values = arrayValues(copy[0], depth + 1);
                if (sourcePosition == null || sourcePosition != 0 || position == null || values == null) {
                    return null;
                }
                parts.put(position, values);
            }
            Set<List<String>> result = new LinkedHashSet<>();
            result.add(new ArrayList<>());
            for (Set<List<String>> options : parts.values()) {
                Set<List<String>> next = new LinkedHashSet<>();
                for (List<String> prefix : result) {
                    for (List<String> option : options) {
                        List<String> combined = new ArrayList<>(prefix);
                        combined.addAll(option);
                        next.add(combined);
                    }
                }
                if (next.size() > MAX_COMBINATIONS) {
                    return null;
                }
                result = next;
            }
            return result;
        }
    }

    private DependencyAgent currentAgent;

    // A static final array: what the owner's static initializer stores into it.
    private Set<List<String>> staticArray(FieldReference field, int depth) {
        ClassReader owner = currentAgent.getClassSource().get(field.getClassName());
        if (owner == null) {
            return null;
        }
        MethodReader clinit = owner.getMethod(new MethodDescriptor("<clinit>", ValueType.VOID));
        if (clinit == null || clinit.getProgram() == null) {
            return null;
        }
        Definitions defs = definitions(clinit);
        Integer value = defs.staticStores.get(field);
        if (value == null) {
            return null;
        }
        Set<List<String>> result = defs.arrayValues(value, depth);
        if (result != null) {
            for (List<String> list : result) {
                for (String element : list) {
                    if (!element.startsWith("C:")) {
                        return null;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void started(DependencyAgent agent) {
        currentAgent = agent;
    }

    private void registerProxy(DependencyAgent agent, List<String> combination) {
        if (proxyClasses.containsKey(combination)) {
            return;
        }
        List<String> interfaces = new ArrayList<>();
        for (String element : combination) {
            interfaces.add(element.substring(2));
        }
        for (String name : interfaces) {
            ClassReader cls = agent.getClassSource().get(name);
            if (cls == null || !cls.hasModifier(ElementModifier.INTERFACE)
                    || new LinkedHashSet<>(interfaces).size() != interfaces.size()) {
                // newProxyInstance rejects these at run time, as the JDK does.
                proxyClasses.put(combination, null);
                return;
            }
        }
        String name = proxyName(agent, interfaces);
        proxyClasses.put(combination, name);
        agent.submitClass(createProxyClass(agent.getClassHierarchy(), name, interfaces, agent));
        if (newProxyDep != null) {
            linkProxy(agent, name);
        }
    }

    // JDK naming: a proxy of public interfaces is in package jdk.proxy1; one of a non-public interface in its package.
    private String proxyName(DependencyAgent agent, List<String> interfaces) {
        String packageName = "jdk.proxy1";
        for (String name : interfaces) {
            ClassReader cls = agent.getClassSource().get(name);
            if (cls.getLevel() != AccessLevel.PUBLIC) {
                int dot = name.lastIndexOf('.');
                packageName = dot < 0 ? "" : name.substring(0, dot);
                break;
            }
        }
        String simple = "$Proxy" + proxyCounter++;
        return packageName.isEmpty() ? simple : packageName + "." + simple;
    }

    private void linkProxy(DependencyAgent agent, String name) {
        if (name == null) {
            return;
        }
        agent.linkClass(name);
        MethodDependency ctor = agent.linkMethod(new MethodReference(name, "<init>", HANDLER, ValueType.VOID));
        ctor.getVariable(0).propagate(agent.getType(name));
        newProxyDep.getVariable(3).connect(ctor.getVariable(1));
        ctor.use();
        newProxyDep.getResult().propagate(agent.getType(name));
        MethodDependency create = agent.linkMethod(CREATE_PROXY);
        create.getResult().propagate(agent.getType(name));
        create.use();
        agent.linkMethod(IS_GENERATED).use();
    }

    // Methods of the proxy: hashCode, equals and toString of Object, then each interface's public methods in order;
    // a signature already present keeps the first Method, as in the JDK. Each calls Proxy.invokeHandler.
    private ClassHolder createProxyClass(ClassHierarchy hierarchy, String name, List<String> interfaces,
            DependencyAgent agent) {
        ClassHolder cls = new ClassHolder(name);
        cls.setLevel(AccessLevel.PUBLIC);
        cls.getModifiers().add(ElementModifier.FINAL);
        cls.setParent(PROXY);
        cls.getInterfaces().addAll(interfaces);

        Map<String, MethodReader> methods = new LinkedHashMap<>();
        Map<String, String> owners = new LinkedHashMap<>();
        ClassReader object = agent.getClassSource().get("java.lang.Object");
        for (String objectMethod : new String[] { "hashCode", "equals", "toString" }) {
            for (MethodReader method : object.getMethods()) {
                if (method.getName().equals(objectMethod) && method.getLevel() == AccessLevel.PUBLIC
                        && !method.hasModifier(ElementModifier.STATIC)) {
                    String key = signatureKey(method);
                    methods.putIfAbsent(key, method);
                    owners.putIfAbsent(key, "java.lang.Object");
                }
            }
        }
        for (String iface : interfaces) {
            collectInterfaceMethods(agent, iface, methods, owners, new LinkedHashSet<>());
        }

        MethodHolder ctor = new MethodHolder("<init>", HANDLER, ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(ctor, hierarchy);
        // The class is not submitted yet, so the hierarchy cannot see it extends Proxy; type the receiver as Proxy.
        pe.var(0, ValueType.object(PROXY)).invokeSpecial(PROXY_INIT, pe.var(1, HANDLER));
        pe.exit();
        cls.addMethod(ctor);

        MethodHolder clinit = new MethodHolder("<clinit>", ValueType.VOID);
        clinit.getModifiers().add(ElementModifier.STATIC);
        ProgramEmitter init = ProgramEmitter.create(clinit, hierarchy);
        int index = 0;
        for (Map.Entry<String, MethodReader> entry : methods.entrySet()) {
            MethodReader method = entry.getValue();
            String fieldName = "m" + index++;
            FieldHolder field = new FieldHolder(fieldName);
            field.setType(METHOD);
            field.setLevel(AccessLevel.PRIVATE);
            field.getModifiers().add(ElementModifier.STATIC);
            cls.addField(field);

            ValueType[] parameters = method.getParameterTypes();
            ValueEmitter types = init.constructArray(CLASS, parameters.length);
            for (int i = 0; i < parameters.length; ++i) {
                types.setElement(i, init.constant(parameters[i]));
            }
            ValueEmitter found = init.constant(ValueType.object(owners.get(entry.getKey())))
                    .invokeVirtual(GET_METHOD, init.constant(method.getName()), types);
            init.setField(new FieldReference(name, fieldName), found);

            cls.addMethod(createProxyMethod(hierarchy, cls, name, fieldName, method));
        }
        init.exit();
        cls.addMethod(clinit);
        return cls;
    }

    private static String signatureKey(MethodReader method) {
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        for (ValueType parameter : method.getParameterTypes()) {
            sb.append(parameter);
        }
        return sb.append(')').toString();
    }

    private void collectInterfaceMethods(DependencyAgent agent, String iface, Map<String, MethodReader> methods,
            Map<String, String> owners, Set<String> visited) {
        if (!visited.add(iface)) {
            return;
        }
        ClassReader cls = agent.getClassSource().get(iface);
        if (cls == null) {
            return;
        }
        for (MethodReader method : cls.getMethods()) {
            if (method.hasModifier(ElementModifier.STATIC) || method.getLevel() != AccessLevel.PUBLIC
                    || method.getName().equals("<clinit>")) {
                continue;
            }
            String key = signatureKey(method);
            if (!methods.containsKey(key)) {
                methods.put(key, method);
                owners.put(key, iface);
            }
        }
        for (String parent : cls.getInterfaces()) {
            collectInterfaceMethods(agent, parent, methods, owners, visited);
        }
    }

    private MethodHolder createProxyMethod(ClassHierarchy hierarchy, ClassHolder cls, String className,
            String fieldName, MethodReader method) {
        MethodHolder holder = new MethodHolder(method.getDescriptor());
        holder.setLevel(AccessLevel.PUBLIC);
        holder.getModifiers().add(ElementModifier.FINAL);
        ProgramEmitter pe = ProgramEmitter.create(holder, hierarchy);
        ValueType[] parameters = method.getParameterTypes();
        ValueEmitter args;
        if (parameters.length == 0) {
            args = pe.constantNull(ValueType.arrayOf(OBJECT));
        } else {
            args = pe.constructArray(OBJECT, parameters.length);
            for (int i = 0; i < parameters.length; ++i) {
                ValueEmitter value = pe.var(i + 1, parameters[i]);
                if (parameters[i] instanceof ValueType.Primitive) {
                    value = pe.invoke(boxing((ValueType.Primitive) parameters[i]), value);
                }
                args.setElement(i, value);
            }
        }
        ValueEmitter methodObject = pe.getField(new FieldReference(className, fieldName), METHOD);
        ValueEmitter result = pe.invoke(INVOKE_HANDLER, pe.var(0, cls).cast(ValueType.object(PROXY)), methodObject,
                args);
        ValueType resultType = method.getResultType();
        if (resultType == ValueType.VOID) {
            pe.exit();
        } else if (resultType instanceof ValueType.Primitive) {
            ValueType.Primitive primitive = (ValueType.Primitive) resultType;
            MethodReference box = boxing(primitive);
            result.cast(ValueType.object(box.getClassName()))
                    .invokeVirtual(new MethodReference(box.getClassName(), unboxName(primitive), resultType))
                    .returnValue();
        } else {
            result.cast(resultType).returnValue();
        }
        return holder;
    }

    private static MethodReference boxing(ValueType.Primitive type) {
        String wrapper;
        switch (type.getKind()) {
            case BOOLEAN:
                wrapper = "java.lang.Boolean";
                break;
            case BYTE:
                wrapper = "java.lang.Byte";
                break;
            case SHORT:
                wrapper = "java.lang.Short";
                break;
            case CHARACTER:
                wrapper = "java.lang.Character";
                break;
            case INTEGER:
                wrapper = "java.lang.Integer";
                break;
            case LONG:
                wrapper = "java.lang.Long";
                break;
            case FLOAT:
                wrapper = "java.lang.Float";
                break;
            default:
                wrapper = "java.lang.Double";
                break;
        }
        return new MethodReference(wrapper, "valueOf", type, ValueType.object(wrapper));
    }

    private static String unboxName(ValueType.Primitive type) {
        switch (type.getKind()) {
            case BOOLEAN:
                return "booleanValue";
            case BYTE:
                return "byteValue";
            case SHORT:
                return "shortValue";
            case CHARACTER:
                return "charValue";
            case INTEGER:
                return "intValue";
            case LONG:
                return "longValue";
            case FLOAT:
                return "floatValue";
            default:
                return "doubleValue";
        }
    }

    // The dispatchers: the proxy class compiled for exactly these interfaces (in this order), and whether a class is
    // one of the generated proxies. Their flows were declared as the proxies were linked.
    @Override
    public void completing(DependencyAgent agent) {
        if (agent.getMethod(CREATE_PROXY) != null) {
            submitDeclared(agent, CREATE_PROXY, createProxyProgram(agent));
        }
        if (agent.getMethod(IS_GENERATED) != null) {
            ProgramEmitter pe = ProgramEmitter.create(IS_GENERATED.getDescriptor(), agent.getClassHierarchy());
            ValueEmitter cls = pe.var(1, CLASS);
            for (String proxyClass : proxyClasses.values()) {
                if (proxyClass != null) {
                    pe.when(cls.isSame(pe.constant(ValueType.object(proxyClass))))
                            .thenDo(() -> pe.constant(1).returnValue());
                }
            }
            pe.constant(0).returnValue();
            submitDeclared(agent, IS_GENERATED, pe);
        }
    }

    // createProxy: for each generated proxy, when the array has its interfaces in order, construct it.
    private ProgramEmitter createProxyProgram(DependencyAgent agent) {
        ProgramEmitter pe = ProgramEmitter.create(CREATE_PROXY.getDescriptor(), agent.getClassHierarchy());
        ValueEmitter interfaces = pe.var(1, CLASS_ARRAY);
        ValueEmitter handler = pe.var(2, HANDLER);
        for (Map.Entry<List<String>, String> entry : proxyClasses.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            List<String> combination = entry.getKey();
            String proxyClass = entry.getValue();
            pe.when(interfaces.arrayLength().isEqualTo(pe.constant(combination.size())))
                    .thenDo(() -> emitMatch(pe, interfaces, handler, combination, 0, proxyClass));
        }
        pe.constantNull(OBJECT).returnValue();
        return pe;
    }

    private static void emitMatch(ProgramEmitter pe, ValueEmitter interfaces, ValueEmitter handler,
            List<String> combination, int index, String proxyClass) {
        if (index == combination.size()) {
            pe.construct(proxyClass, handler).returnValue();
            return;
        }
        String name = combination.get(index).substring(2);
        pe.when(interfaces.getElement(index).isSame(pe.constant(ValueType.object(name))))
                .thenDo(() -> emitMatch(pe, interfaces, handler, combination, index + 1, proxyClass));
    }

    private static void submitDeclared(DependencyAgent agent, MethodReference method, ProgramEmitter pe) {
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
    }
}
