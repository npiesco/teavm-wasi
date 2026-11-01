/*
 *  Copyright 2014 Alexey Andreev.
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

import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformRunnable;
import org.teavm.runtime.EventQueue;
import org.teavm.runtime.Fiber;

public class TThread extends TObject implements TRunnable {
    public static final int MIN_PRIORITY = 1;
    public static final int NORM_PRIORITY = 5;
    public static final int MAX_PRIORITY = 10;
    // The method that starts every thread but main; stack traces end above it.
    static final String ENTRY_METHOD = "runThread";
    private static final TThreadGroup SYSTEM_GROUP = new TThreadGroup(null, "system", true);
    private static final TThreadGroup MAIN_GROUP = new TThreadGroup(SYSTEM_GROUP, "main");
    private static int nextId = 1;
    private static int threadInitNumber;
    private static TThread mainThread = new TThread(MAIN_GROUP, null, "main");
    private static TThread currentThread = mainThread;
    private static int activeCount = 1;
    private static UncaughtExceptionHandler defaultUncaughtExceptionHandler;
    private UncaughtExceptionHandler uncaughtExceptionHandler;
    private long id;
    private int priority = NORM_PRIORITY;
    private TThreadGroup group;
    private boolean started;
    private boolean terminated;
    private State blockedState;
    private boolean daemon;
    private long timeSliceStart;
    private int yieldCount;
    private final Object finishedLock = new Object();
    private boolean interruptedFlag;
    public TThreadInterruptHandler interruptHandler;

    private String name;
    TRunnable target;

    static {
        mainThread.started = true;
    }

    public TThread() {
        this(null, null, nextThreadName());
    }

    public TThread(String name) {
        this(null, null, name);
    }

    public TThread(TRunnable target) {
        this(null, target, nextThreadName());
    }

    public TThread(TRunnable target, String name) {
        this(null, target, name);
    }

    public TThread(TThreadGroup group, TRunnable target) {
        this(group, target, nextThreadName());
    }

    public TThread(TThreadGroup group, String name) {
        this(group, null, name);
    }

    public TThread(TThreadGroup group, TRunnable target, String name) {
        if (name == null) {
            throw new NullPointerException("'name' is null");
        }
        TThread parent = currentThread;
        if (group == null) {
            group = parent != null ? parent.getThreadGroup() : MAIN_GROUP;
        }
        this.group = group;
        this.name = name;
        this.target = target;
        if (parent != null) {
            daemon = parent.daemon;
            priority = Math.min(parent.priority, group.getMaxPriority());
        }
        id = nextId++;
    }

    public TThread(TThreadGroup group, TRunnable target, String name, long stackSize) {
        this(group, target, name);
    }

    private static String nextThreadName() {
        return "Thread-" + threadInitNumber++;
    }

    public enum State {
        NEW,
        RUNNABLE,
        BLOCKED,
        WAITING,
        TIMED_WAITING,
        TERMINATED
    }

    public State getState() {
        if (!started) {
            return State.NEW;
        }
        if (terminated) {
            return State.TERMINATED;
        }
        return blockedState != null ? blockedState : State.RUNNABLE;
    }

    public final TThreadGroup getThreadGroup() {
        return terminated ? null : group;
    }

    public void start() {
        if (started) {
            throw new TIllegalThreadStateException();
        }
        started = true;
        if (PlatformDetector.isLowLevel()) {
            boolean daemon = this.daemon;
            if (!daemon) {
                Fiber.userThreadCount++;
            }
            EventQueue.offer(() -> Fiber.start(this::runThread, daemon));
        } else {
            Platform.startThread(this::runThread);
        }
    }

    private void runThread() {
        try {
            activeCount++;
            setCurrentThread(TThread.this);
            TThread.this.run();
        } catch (Throwable t) {
            dispatchUncaughtException(t);
        } finally {
            terminated = true;
            synchronized (finishedLock) {
                finishedLock.notifyAll();
            }
            activeCount--;
            setCurrentThread(mainThread);
        }
    }

    static void setCurrentThread(TThread thread) {
        if (currentThread != thread) {
            currentThread = thread;
        }
        currentThread.timeSliceStart = System.currentTimeMillis();
    }

    static TThread getMainThread() {
        return mainThread;
    }

    @Override
    public void run() {
        if (target != null) {
            target.run();
        }
    }

    public static TThread currentThread() {
        return currentThread;
    }

    public String getName() {
        return name;
    }

    public final void setName(String name) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        this.name = name;
    }

    public final boolean isDaemon() {
        return daemon;
    }

    public final void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public final void join(long millis, int nanos) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }
        if (nanos > 0 && millis < Long.MAX_VALUE) {
            millis++;
        }
        join(millis);
    }

    public final void join(long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (currentThread() == this) {
            return;
        }
        TThread waiter = currentThread();
        synchronized (finishedLock) {
            if (millis == 0) {
                while (isAlive()) {
                    waiter.blockedState = State.WAITING;
                    finishedLock.wait(0);
                    waiter.blockedState = null;
                }
            } else {
                long deadline = System.currentTimeMillis() + millis;
                while (isAlive()) {
                    long delay = deadline - System.currentTimeMillis();
                    if (delay <= 0) {
                        break;
                    }
                    waiter.blockedState = State.TIMED_WAITING;
                    finishedLock.wait(delay);
                    waiter.blockedState = null;
                }
            }
        }
    }

    public final void join() throws InterruptedException {
        join(0);
    }

    public static void yield() {
        TThread currentThread = currentThread();
        if (++currentThread.yieldCount < 30) {
            return;
        }
        currentThread().yieldCount = 0;
        if (currentThread.timeSliceStart + 100 < System.currentTimeMillis()) {
            switchContext(currentThread);
        }
    }

    @Async
    static native void switchContext(TThread thread);

    private static void switchContext(final TThread thread, final AsyncCallback<Void> callback) {
        if (PlatformDetector.isLowLevel()) {
            EventQueue.offer(() -> {
                setCurrentThread(thread);
                callback.complete(null);
            });
        } else {
            Platform.postpone(() -> {
                setCurrentThread(thread);
                callback.complete(null);
            });
        }
    }

    public void interrupt() {
        interruptedFlag = true;
        blockedState = null;
        if (interruptHandler != null) {
            interruptHandler.interrupted();
            interruptHandler = null;
        }
    }

    public static boolean interrupted() {
        TThread thread = currentThread();
        boolean result = thread.interruptedFlag;
        thread.interruptedFlag = false;
        return result;
    }

    public boolean isInterrupted() {
        return interruptedFlag;
    }

    public final boolean isAlive() {
        return started && !terminated;
    }

    public static int activeCount() {
        return activeCount;
    }

    public long getId() {
        return id;
    }

    public static boolean holdsLock(TObject obj) {
        return TObject.holdsLock(obj);
    }

    @Async
    public static native void sleep(long millis) throws TInterruptedException;

    private static void sleep(long millis, AsyncCallback<Void> callback) {
        if (millis < 0) {
            callback.error(new IllegalArgumentException("timeout value is negative"));
            return;
        }
        TThread current = currentThread();
        current.blockedState = State.TIMED_WAITING;
        SleepHandler handler = new SleepHandler(current, callback);
        if (PlatformDetector.isLowLevel()) {
            if (current.interruptedFlag) {
                handler.interrupted();
            } else {
                handler.scheduleId = EventQueue.offer(handler, System.currentTimeMillis() + millis);
                current.interruptHandler = handler;
            }
        } else {
            int intMillis = millis < Integer.MAX_VALUE ? (int) millis : Integer.MAX_VALUE;
            handler.scheduleId = Platform.schedule(handler, intMillis);
            current.interruptHandler = handler;
        }
    }

    static class SleepHandler implements PlatformRunnable, EventQueue.Event, TThreadInterruptHandler {
        private TThread thread;
        private AsyncCallback<Void> callback;
        private boolean isInterrupted;
        int scheduleId;

        SleepHandler(TThread thread, AsyncCallback<Void> callback) {
            this.thread = thread;
            this.callback = callback;
        }

        @Override
        public void interrupted() {
            thread.blockedState = null;
            thread.interruptedFlag = false;
            isInterrupted = true;
            if (PlatformDetector.isLowLevel()) {
                EventQueue.kill(scheduleId);
                EventQueue.offer(() -> callback.error(new TInterruptedException()));
            } else {
                Platform.killSchedule(scheduleId);
                Platform.postpone(() -> callback.error(new TInterruptedException()));
            }
        }

        @Override
        public void run() {
            if (!isInterrupted) {
                thread.blockedState = null;
                thread.interruptHandler = null;
                setCurrentThread(thread);
                callback.complete(null);
            }
        }
    }

    public final void setPriority(int newPriority) {
        this.priority = newPriority;
    }

    public final int getPriority() {
        return this.priority;
    }

    public TStackTraceElement[] getStackTrace() {
        return new TStackTraceElement[0];
    }

    public TClassLoader getContextClassLoader() {
        return TClassLoader.getSystemClassLoader();
    }

    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        if (terminated) {
            return null;
        }
        return uncaughtExceptionHandler != null ? uncaughtExceptionHandler : group;
    }

    public void setUncaughtExceptionHandler(UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }

    public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
        return defaultUncaughtExceptionHandler;
    }

    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler handler) {
        defaultUncaughtExceptionHandler = handler;
    }

    // Called with an exception that ended this thread's run method (the main thread's included).
    void dispatchUncaughtException(Throwable e) {
        UncaughtExceptionHandler handler = getUncaughtExceptionHandler();
        if (handler != null) {
            handler.uncaughtException(this, e);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Thread[#");
        sb.append(getId()).append(',').append(getName()).append(',').append(getPriority()).append(',');
        TThreadGroup g = getThreadGroup();
        if (g != null) {
            sb.append(g.getName());
        }
        return sb.append(']').toString();
    }

    public interface UncaughtExceptionHandler {
        void uncaughtException(TThread t, Throwable e);
    }
}
