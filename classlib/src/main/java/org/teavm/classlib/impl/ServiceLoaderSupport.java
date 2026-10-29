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
package org.teavm.classlib.impl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.teavm.classlib.ServiceLoaderFilter;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.CallLocation;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class ServiceLoaderSupport extends AbstractDependencyListener implements ServiceLoaderInformation {
    private static final MethodReference LOAD_METHOD = new MethodReference(ServiceLoader.class, "load", Class.class,
            ServiceLoader.class);
    private static final MethodDescriptor INIT_METHOD = new MethodDescriptor("<init>", void.class);
    private Map<String, Set<String>> serviceMap = new HashMap<>();
    private ClassLoader classLoader;
    private static final String SERVICES_PREFIX = "META-INF/services/";
    private Map<String, Set<String>> classpathProviders;

    public ServiceLoaderSupport(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Collection<? extends String> serviceTypes() {
        return serviceMap.keySet();
    }

    @Override
    public Collection<? extends String> serviceImplementations(String type) {
        Collection<? extends String> result = serviceMap.get(type);
        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method) {
        MethodReference ref = method.getReference();
        if (ref.getClassName().equals("java.lang.Class") && ref.getName().equals("forName")) {
            linkServiceProviders(agent);
            return;
        }
        if (!ref.getClassName().equals("java.util.ServiceLoader")) {
            return;
        }
        switch (ref.getName()) {
            case "loadServices":
                method.getResult().propagate(agent.getType("[Ljava/lang/Object;"));
                linkImplementations(agent, method.getResult().getArrayItem());
                break;
            case "createService":
                linkImplementations(agent, method.getResult());
                break;
            case "providerClass":
                method.getResult().propagate(agent.getType("java.lang.Class"));
                break;
            default:
                break;
        }
    }

    private void linkImplementations(DependencyAgent agent, DependencyNode target) {
        List<ServiceLoaderFilter> filters = getFilters(agent);
        DependencyNode sourceNode = agent.linkMethod(LOAD_METHOD).getVariable(1).getClassValueNode();
        sourceNode.connect(target);
        sourceNode.addConsumer(type -> {
            CallLocation location = new CallLocation(LOAD_METHOD);
            for (String implementationType : getImplementations(type.getName())) {
                if (filters.stream().anyMatch(filter -> !filter.apply(type.getName(), implementationType))) {
                    continue;
                }
                serviceMap.computeIfAbsent(type.getName(), k -> new LinkedHashSet<>()).add(implementationType);

                MethodReference ctor = new MethodReference(implementationType, INIT_METHOD);
                agent.linkMethod(ctor).addLocation(location).use();
                target.propagate(agent.getType(implementationType));
            }
        });
    }

    // Provider classes named in META-INF/services files are the classpath's standard declaration of classes to be
    // loaded by name. Once Class.forName is reachable, the providers of every reachable service type are part of
    // the closed world (a service nobody reaches cannot be looked up, so its providers stay out).
    private void linkServiceProviders(DependencyAgent agent) {
        if (classpathProviders != null) {
            return;
        }
        classpathProviders = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, List<byte[]>> entry : ClasspathScanner.resources(classLoader,
                    name -> name.startsWith(SERVICES_PREFIX) && name.length() > SERVICES_PREFIX.length())
                    .entrySet()) {
                Set<String> providers = classpathProviders.computeIfAbsent(
                        entry.getKey().substring(SERVICES_PREFIX.length()), k -> new LinkedHashSet<>());
                for (byte[] file : entry.getValue()) {
                    parseServiceFile(new ByteArrayInputStream(file), providers);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Set<String> reached = new HashSet<>(agent.getReachableClasses());
        for (String service : classpathProviders.keySet()) {
            if (reached.contains(service)) {
                linkProvidersOf(agent, service);
            }
        }
    }

    @Override
    public void classReached(DependencyAgent agent, String className) {
        if (classpathProviders != null && classpathProviders.containsKey(className)) {
            linkProvidersOf(agent, className);
        }
    }

    private void linkProvidersOf(DependencyAgent agent, String service) {
        Set<String> providers = classpathProviders.remove(service);
        if (providers == null) {
            return;
        }
        for (String provider : providers) {
            if (agent.getClassSource().get(provider) != null) {
                agent.linkClass(provider);
            }
        }
    }
    private Set<String> getImplementations(String type) {
        Set<String> result = new LinkedHashSet<>();
        try {
            Enumeration<URL> resources = classLoader.getResources("META-INF/services/" + type);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                try (InputStream stream = resource.openStream()) {
                    parseServiceFile(stream, result);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private void parseServiceFile(InputStream input, Set<String> consumer) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            consumer.add(line);
        }
    }

    private List<ServiceLoaderFilter> getFilters(DependencyAgent agent) {
        List<ServiceLoaderFilter> filters = new ArrayList<>();
        for (String filterTypeName : getImplementations(ServiceLoaderFilter.class.getName())) {
            Class<?> filterType;
            try {
                filterType = Class.forName(filterTypeName, true, classLoader);
            } catch (ClassNotFoundException e) {
                agent.getDiagnostics().error(null, "Could not load ServiceLoader filter class '{{c0}}'",
                        filterTypeName);
                continue;
            }

            if (!ServiceLoaderFilter.class.isAssignableFrom(filterType)) {
                agent.getDiagnostics().error(null, "Class '{{c0}}' does not implement ServiceLoaderFilter interface",
                        filterTypeName);
                continue;
            }

            try {
                filters.add((ServiceLoaderFilter) filterType.getConstructor().newInstance());
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException
                    | InstantiationException e) {
                agent.getDiagnostics().error(null, "Could not instantiate ServiceLoader filter '{{c0}}'",
                        filterTypeName);
            }
        }
        return filters;
    }
}
