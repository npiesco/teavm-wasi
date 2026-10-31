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
package org.teavm.classlib.impl.security;

/**
 * A message digest algorithm: incremental input, a fixed-length digest after which the state is reset, and an
 * independent copy of the state for MessageDigest.clone.
 */
public abstract class DigestEngine {
    public abstract int length();

    public abstract void update(byte input);

    public void update(byte[] input, int offset, int length) {
        for (int i = 0; i < length; ++i) {
            update(input[offset + i]);
        }
    }

    public abstract byte[] digest();

    public abstract void reset();

    public abstract DigestEngine copy();
}
