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
package org.teavm.classlib.java.nio.file;

/**
 * Minimal emulation of {@code java.nio.file.Paths}. Real path resolution is not
 * wired to WASI; the factory exists so reachable references link and throws
 * clearly if a WASI module actually attempts to resolve a filesystem path.
 */
public final class TPaths {
    private TPaths() {
    }

    public static TPath get(String first, String... more) {
        throw new UnsupportedOperationException("java.nio.file.Paths.get is not supported on the WASI target");
    }
}
