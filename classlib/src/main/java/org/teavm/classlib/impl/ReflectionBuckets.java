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

/**
 * Selects the reflection dispatcher shard holding a class. The compiler computes the same value from the class name
 * when it assigns classes to shards, so a dispatcher needs one switch instead of a comparison per reflected class.
 */
public final class ReflectionBuckets {
    private ReflectionBuckets() {
    }

    public static int bucket(Class<?> cls, int count) {
        return bucket(cls.getName(), count);
    }

    public static int bucket(String className, int count) {
        return Math.floorMod(className.hashCode(), count);
    }
}
