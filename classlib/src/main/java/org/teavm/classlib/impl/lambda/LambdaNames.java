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
package org.teavm.classlib.impl.lambda;

/**
 * Names of the classes generated to implement lambdas and method references. The JDK implements them with hidden
 * classes, which stack traces leave out, so stack traces leave these out as well.
 */
public final class LambdaNames {
    public static final String MARKER = "$lambda$_";

    private LambdaNames() {
    }
}
