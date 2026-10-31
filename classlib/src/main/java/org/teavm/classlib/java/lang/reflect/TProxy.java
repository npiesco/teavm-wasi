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
package org.teavm.classlib.java.lang.reflect;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * java.lang.reflect.Proxy. The proxy classes are generated ahead of time (ProxySupport) for every list of interfaces
 * that can reach newProxyInstance; each method of a proxy calls invokeHandler with its Method and boxed arguments.
 */
public class TProxy implements Serializable {
    private static final long serialVersionUID = -2222568056686623797L;

    protected InvocationHandler h;

    protected TProxy(InvocationHandler h) {
        this.h = Objects.requireNonNull(h);
    }

    public static Object newProxyInstance(ClassLoader loader, Class<?>[] interfaces, InvocationHandler h) {
        Objects.requireNonNull(h);
        Class<?>[] intfs = interfaces.clone();
        if (intfs.length > 65535) {
            throw new IllegalArgumentException("interface limit exceeded: " + intfs.length);
        }
        Set<Class<?>> seen = new HashSet<>();
        for (Class<?> intf : intfs) {
            if (!intf.isInterface()) {
                throw new IllegalArgumentException(intf.getName() + " is not an interface");
            }
            if (!seen.add(intf)) {
                throw new IllegalArgumentException("repeated interface: " + intf.getName());
            }
        }
        Object proxy = createProxy(intfs, h);
        if (proxy == null) {
            StringBuilder names = new StringBuilder();
            for (Class<?> intf : intfs) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(intf.getName());
            }
            throw new IllegalArgumentException("No proxy class was compiled for interfaces [" + names
                    + "]: the interfaces passed to Proxy.newProxyInstance could not be determined at compile time");
        }
        return proxy;
    }

    public static boolean isProxyClass(Class<?> cl) {
        Objects.requireNonNull(cl);
        return isGeneratedProxyClass(cl);
    }

    public static InvocationHandler getInvocationHandler(Object proxy) {
        if (!isProxyClass(proxy.getClass())) {
            throw new IllegalArgumentException("not a proxy instance");
        }
        return ((TProxy) proxy).h;
    }

    // As the JDK's proxies: unchecked exceptions and errors pass through, checked ones only when the method declares
    // them, anything else is wrapped in an UndeclaredThrowableException.
    static Object invokeHandler(TProxy proxy, Method method, Object[] args) throws Throwable {
        try {
            return proxy.h.invoke(proxy, method, args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            for (Class<?> declared : method.getExceptionTypes()) {
                if (declared.isInstance(t)) {
                    throw t;
                }
            }
            throw new UndeclaredThrowableException(t);
        }
    }

    private static native Object createProxy(Class<?>[] interfaces, InvocationHandler h);

    private static native boolean isGeneratedProxyClass(Class<?> cls);
}
