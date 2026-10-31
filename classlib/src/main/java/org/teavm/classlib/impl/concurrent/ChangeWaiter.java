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
package org.teavm.classlib.impl.concurrent;

import java.util.ArrayDeque;
import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.java.lang.TInterruptedException;
import org.teavm.classlib.java.lang.TThread;
import org.teavm.classlib.java.lang.TThreadInterruptHandler;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformRunnable;
import org.teavm.runtime.EventQueue;

/**
 * Suspends the current green thread until a blocking collection changes, a deadline passes or the thread is
 * interrupted: the condition variable of the blocking queues.
 */
public final class ChangeWaiter {
    private ArrayDeque<Handler> handlers;

    /**
     * Waits for {@link #signal()}. A positive {@code timeLimit} is an absolute {@code System.currentTimeMillis()}
     * deadline; zero waits without one. Returns {@code false} when the deadline passed first.
     */
    @Async
    public native Boolean await(long timeLimit) throws InterruptedException;

    private void await(long timeLimit, AsyncCallback<Boolean> callback) {
        if (handlers == null) {
            handlers = new ArrayDeque<>();
        }
        Handler handler = new Handler(callback);
        handlers.add(handler);
        if (timeLimit > 0) {
            int timeout = Math.max(0, (int) (timeLimit - System.currentTimeMillis()));
            handler.timerId = PlatformDetector.isLowLevel()
                    ? EventQueue.offer(handler, timeLimit)
                    : Platform.schedule(handler, timeout);
        } else {
            handler.timerId = -1;
        }
        TThread.currentThread().interruptHandler = handler;
    }

    public void signal() {
        if (handlers == null) {
            return;
        }
        while (!handlers.isEmpty()) {
            Handler handler = handlers.poll();
            if (PlatformDetector.isLowLevel()) {
                EventQueue.offer(handler::changed);
            } else {
                Platform.postpone(handler::changed);
            }
        }
        handlers = null;
    }

    static final class Handler implements PlatformRunnable, TThreadInterruptHandler, EventQueue.Event {
        private final AsyncCallback<Boolean> callback;
        private boolean complete;
        int timerId;

        Handler(AsyncCallback<Boolean> callback) {
            this.callback = callback;
        }

        @Override
        public void run() {
            if (complete()) {
                return;
            }
            callback.complete(false);
        }

        @Override
        public void interrupted() {
            if (complete()) {
                return;
            }
            callback.error(new TInterruptedException());
        }

        private boolean complete() {
            if (complete) {
                return true;
            }
            complete = true;
            if (timerId >= 0) {
                if (PlatformDetector.isLowLevel()) {
                    EventQueue.kill(timerId);
                } else {
                    Platform.killSchedule(timerId);
                }
                timerId = -1;
            }
            TThread.currentThread().interruptHandler = null;
            return false;
        }

        void changed() {
            if (complete()) {
                return;
            }
            callback.complete(true);
        }
    }
}
