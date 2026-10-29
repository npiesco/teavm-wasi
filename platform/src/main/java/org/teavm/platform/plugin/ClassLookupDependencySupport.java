/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.platform.plugin;

import org.teavm.dependency.*;
import org.teavm.model.*;
import org.teavm.platform.Platform;

public class ClassLookupDependencySupport extends AbstractDependencyListener {
    private DependencyNode allClasses;

    @Override
    public void started(DependencyAgent agent) {
        allClasses = agent.createNode();
    }

    @Override
    public void classReached(DependencyAgent agent, String className) {
        allClasses.propagate(agent.getType(className));
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method) {
        MethodReference ref = method.getReference();
        boolean platformLookup = ref.getClassName().equals(Platform.class.getName())
                && ref.getName().equals("lookupClass");
        // Class.forName on WebAssembly searches the runtime class table instead of calling
        // Platform.lookupClass; a class found that way must have its initializer linked too.
        boolean tableLookup = ref.getClassName().equals("java.lang.Class") && ref.getName().equals("lookupLowLevel");
        if (platformLookup || tableLookup) {
            allClasses.addConsumer(type -> {
                ClassReader cls = agent.getClassSource().get(type.getName());
                if (cls == null) {
                    return;
                }

                // Loading a class loads its superclass and interfaces (JVMS 5.3.5), so a class
                // found by runtime lookup must be able to find its supertypes too.
                if (cls.getParent() != null) {
                    agent.linkClass(cls.getParent());
                    allClasses.propagate(agent.getType(cls.getParent()));
                }
                for (String iface : cls.getInterfaces()) {
                    agent.linkClass(iface);
                    allClasses.propagate(agent.getType(iface));
                }

                MethodReader initMethod = cls.getMethod(new MethodDescriptor("<clinit>", void.class));
                if (initMethod != null) {
                    MethodDependency initDep = agent.linkMethod(initMethod.getReference());
                    method.addLocationListener(initDep::addLocation);
                    initDep.use();
                }
            });
        }
    }
}
