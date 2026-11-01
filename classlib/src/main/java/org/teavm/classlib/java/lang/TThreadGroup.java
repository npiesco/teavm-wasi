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
package org.teavm.classlib.java.lang;

/**
 * {@code java.lang.ThreadGroup}: the last stop of an uncaught exception, which hands it to the default handler or
 * reports it on {@code System.err} as the JDK does.
 */
public class TThreadGroup extends TObject implements TThread.UncaughtExceptionHandler {
    private final TThreadGroup parent;
    private final String name;
    private int maxPriority = TThread.MAX_PRIORITY;
    private boolean daemon;

    TThreadGroup(TThreadGroup parent, String name, boolean root) {
        this.parent = parent;
        this.name = name;
    }

    public TThreadGroup(String name) {
        this(TThread.currentThread().getThreadGroup(), name);
    }

    public TThreadGroup(TThreadGroup parent, String name) {
        if (parent == null) {
            throw new NullPointerException();
        }
        this.parent = parent;
        this.name = name;
        maxPriority = parent.maxPriority;
        daemon = parent.daemon;
    }

    public final String getName() {
        return name;
    }

    public final TThreadGroup getParent() {
        return parent;
    }

    public final int getMaxPriority() {
        return maxPriority;
    }

    public final void setMaxPriority(int pri) {
        if (pri >= TThread.MIN_PRIORITY && pri <= TThread.MAX_PRIORITY) {
            maxPriority = parent != null ? Math.min(pri, parent.maxPriority) : pri;
        }
    }

    public final boolean isDaemon() {
        return daemon;
    }

    public final void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public final boolean parentOf(TThreadGroup g) {
        for (; g != null; g = g.parent) {
            if (g == this) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void uncaughtException(TThread t, Throwable e) {
        if (parent != null) {
            parent.uncaughtException(t, e);
        } else {
            TThread.UncaughtExceptionHandler ueh = TThread.getDefaultUncaughtExceptionHandler();
            if (ueh != null) {
                ueh.uncaughtException(t, e);
            } else {
                System.err.print("Exception in thread \"" + t.getName() + "\" ");
                e.printStackTrace(System.err);
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "[name=" + getName() + ",maxpri=" + getMaxPriority() + "]";
    }
}
