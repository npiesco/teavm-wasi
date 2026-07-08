/*
 *  Copyright 2024 Spooler contributors.
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
package org.teavm.classlib.java.net;

/**
 * Minimal emulation of {@code java.net.InetAddress}. WASI has no host name
 * resolution or DNS; the type exists so reachable references (e.g. a lazily
 * used {@code localhost:} string lookup) link, and each resolving operation
 * throws clearly if a WASI module actually attempts it.
 */
public class TInetAddress {
    protected TInetAddress() {
    }

    public static TInetAddress getLocalHost() {
        throw new UnsupportedOperationException("java.net.InetAddress.getLocalHost is not supported on the WASI target");
    }

    public static TInetAddress getByName(String host) {
        throw new UnsupportedOperationException("java.net.InetAddress.getByName is not supported on the WASI target");
    }

    public String getHostName() {
        throw new UnsupportedOperationException("java.net.InetAddress.getHostName is not supported on the WASI target");
    }

    public String getCanonicalHostName() {
        throw new UnsupportedOperationException(
                "java.net.InetAddress.getCanonicalHostName is not supported on the WASI target");
    }

    public String getHostAddress() {
        throw new UnsupportedOperationException(
                "java.net.InetAddress.getHostAddress is not supported on the WASI target");
    }
}
