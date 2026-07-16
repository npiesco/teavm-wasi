/*
 *  Copyright 2026 TeaVM Contributors.
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
package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.model.expression.WasmBlock;
import org.teavm.backend.wasm.model.expression.WasmExpression;
import org.teavm.model.MethodReference;
import org.teavm.runtime.EventQueue;

public class WasiEventQueueIntrinsic implements WasmIntrinsic {
    private static final MethodReference INTERRUPT = new MethodReference(EventQueue.class, "interrupt", void.class);

    @Override
    public boolean isApplicable(MethodReference methodReference) {
        return methodReference.equals(INTERRUPT);
    }

    @Override
    public WasmExpression apply(InvocationExpr invocation, WasmIntrinsicManager manager) {
        // The JavaScript runtime uses this callback to reschedule its host timer.
        // Standalone WASI has no external event-loop controller to notify.
        return new WasmBlock(false);
    }
}
