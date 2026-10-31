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

import java.util.LinkedHashSet;
import java.util.Set;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.ClassReader;
import org.teavm.model.GenericValueType;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 * Generates java.util.HashMap.selfComparable(Class): the JDK's comparableClassFor test that a class directly
 * implements {@code Comparable<C>} with C the class itself, read from the generic signatures of the classes
 * whose Class objects reach the method.
 */
public class SelfComparableSupport extends AbstractDependencyListener {
    private static final ValueType CLASS = ValueType.object("java.lang.Class");
    private static final MethodReference SELF_COMPARABLE = new MethodReference("java.util.HashMap",
            "selfComparable", CLASS, ValueType.BOOLEAN);
    private final Set<String> classes = new LinkedHashSet<>();

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method) {
        if (method.getReference().equals(SELF_COMPARABLE)) {
            method.getVariable(1).getClassValueNode().addConsumer(type -> {
                ClassReader cls = agent.getClassSource().get(type.getName());
                if (cls != null && implementsComparableOfSelf(cls)) {
                    classes.add(cls.getName());
                }
            });
        }
    }

    private static boolean implementsComparableOfSelf(ClassReader cls) {
        for (GenericValueType.Object iface : cls.getGenericInterfaces()) {
            if (!iface.getClassName().equals("java.lang.Comparable")) {
                continue;
            }
            GenericValueType.Argument[] args = iface.getArguments();
            if (args.length == 1 && args[0].getKind() == GenericValueType.ArgumentKind.INVARIANT
                    && args[0].getValue() instanceof GenericValueType.Object) {
                GenericValueType.Object arg = (GenericValueType.Object) args[0].getValue();
                if (arg.getClassName().equals(cls.getName()) && arg.getArguments().length == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void completing(DependencyAgent agent) {
        if (agent.getMethod(SELF_COMPARABLE) == null) {
            return;
        }
        ProgramEmitter pe = ProgramEmitter.create(SELF_COMPARABLE.getDescriptor(), agent.getClassHierarchy());
        ValueEmitter cls = pe.var(1, CLASS);
        for (String className : classes) {
            pe.when(cls.isSame(pe.constant(ValueType.object(className))))
                    .thenDo(() -> pe.constant(1).returnValue());
        }
        pe.constant(0).returnValue();
        agent.submitMethod(SELF_COMPARABLE, pe.getProgram());
    }
}
