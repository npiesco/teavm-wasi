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
import java.util.List;
import java.util.function.BiConsumer;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 * Generates the WebAssembly bodies of {@code ServiceLoader.serviceCount}, {@code createService} and
 * {@code providerClass} from the provider table that {@link ServiceLoaderSupport} collects out of
 * {@code META-INF/services}. The bodies are plain IR (comparisons against class constants and
 * constructor calls), so they are compiled like any other method.
 */
public class ServiceLoaderWasmSupport extends AbstractDependencyListener {
    private static final String SERVICE_LOADER = "java.util.ServiceLoader";
    private static final ValueType CLASS = ValueType.object("java.lang.Class");
    private static final MethodReference SERVICE_COUNT = new MethodReference(SERVICE_LOADER, "serviceCount",
            CLASS, ValueType.INTEGER);
    private static final MethodReference CREATE_SERVICE = new MethodReference(SERVICE_LOADER, "createService",
            CLASS, ValueType.INTEGER, ValueType.object("java.lang.Object"));
    private static final MethodReference PROVIDER_CLASS = new MethodReference(SERVICE_LOADER, "providerClass",
            CLASS, ValueType.INTEGER, CLASS);

    private final ServiceLoaderInformation information;

    public ServiceLoaderWasmSupport(ServiceLoaderInformation information) {
        this.information = information;
    }

    @Override
    public void completing(DependencyAgent agent) {
        if (agent.getMethod(SERVICE_COUNT) != null) {
            ProgramEmitter pe = ProgramEmitter.create(SERVICE_COUNT.getDescriptor(), agent.getClassHierarchy());
            ValueEmitter service = pe.var(1, CLASS);
            for (String type : information.serviceTypes()) {
                int count = information.serviceImplementations(type).size();
                pe.when(service.isSame(pe.constant(ValueType.object(type))))
                        .thenDo(() -> pe.constant(count).returnValue());
            }
            pe.constant(0).returnValue();
            agent.submitMethod(SERVICE_COUNT, pe.getProgram());
        }
        submitPerProvider(agent, CREATE_SERVICE, ValueType.object("java.lang.Object"),
                (pe, implementation) -> pe.construct(implementation).returnValue());
        submitPerProvider(agent, PROVIDER_CLASS, CLASS,
                (pe, implementation) -> pe.constant(ValueType.object(implementation)).returnValue());
    }

    private void submitPerProvider(DependencyAgent agent, MethodReference method, ValueType resultType,
            BiConsumer<ProgramEmitter, String> emitProvider) {
        if (agent.getMethod(method) == null) {
            return;
        }
        ProgramEmitter pe = ProgramEmitter.create(method.getDescriptor(), agent.getClassHierarchy());
        ValueEmitter service = pe.var(1, CLASS);
        ValueEmitter index = pe.var(2, ValueType.INTEGER);
        for (String type : information.serviceTypes()) {
            List<String> implementations = new ArrayList<>(information.serviceImplementations(type));
            pe.when(service.isSame(pe.constant(ValueType.object(type)))).thenDo(() -> {
                for (int i = 0; i < implementations.size(); ++i) {
                    String implementation = implementations.get(i);
                    pe.when(index.isEqualTo(pe.constant(i))).thenDo(() -> emitProvider.accept(pe, implementation));
                }
            });
        }
        pe.constantNull(resultType).returnValue();
        agent.submitMethod(method, pe.getProgram());
    }
}
