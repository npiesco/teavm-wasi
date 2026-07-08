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
 * Minimal emulation of {@code java.nio.file.Files}. File I/O is not wired to
 * WASI in this classlib; the type exists so reachable references (e.g. a lazily
 * used {@code file:} string lookup) link, and reads throw clearly if actually
 * invoked from a WASI module.
 */
public final class TFiles {
    private TFiles() {
    }

    public static byte[] readAllBytes(TPath path) {
        throw new UnsupportedOperationException("java.nio.file.Files.readAllBytes is not supported on the WASI target");
    }
}
