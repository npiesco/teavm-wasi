/*
 *  Copyright 2013 Alexey Andreev.
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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.impl.lambda.LambdaNames;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.util.TArrays;
import org.teavm.interop.Remove;
import org.teavm.interop.Rename;
import org.teavm.interop.Superclass;
import org.teavm.runtime.ExceptionHandling;

@Superclass("java.lang.Object")
public class TThrowable extends RuntimeException implements TSerializable {
    private static final long serialVersionUID = 2026791432677149320L;
    private String message;
    private TThrowable cause;
    private boolean suppressionEnabled;
    private boolean writableStackTrace;
    // Field initializers would run only in the fakeInit constructors, which the renamed <init> methods replace, so
    // this stays null until the first suppressed exception is added.
    private TThrowable[] suppressed;
    private TStackTraceElement[] stackTrace;

    @SuppressWarnings("unused")
    @Rename("fakeInit")
    protected TThrowable(String message, TThrowable cause, boolean enableSuppression, boolean writableStackTrace) {
    }

    @Rename("<init>")
    public void init(String message, TThrowable cause, boolean enableSuppression, boolean writableStackTrace) {
        if (writableStackTrace) {
            fillInStackTrace();
        }
        this.suppressionEnabled = enableSuppression;
        this.writableStackTrace = writableStackTrace;
        this.message = message;
        this.cause = cause;
    }

    @Rename("fakeInit")
    public TThrowable() {
    }

    @Rename("<init>")
    private void init() {
        this.suppressionEnabled = true;
        this.writableStackTrace = true;
        fillInStackTrace();
    }

    @Rename("fakeInit")
    public TThrowable(@SuppressWarnings("unused") String message) {
    }

    @Rename("<init>")
    private void init(String message) {
        this.suppressionEnabled = true;
        this.writableStackTrace = true;
        fillInStackTrace();
        this.message = message;
    }

    @SuppressWarnings("unused")
    @Rename("fakeInit")
    public TThrowable(String message, TThrowable cause) {
    }

    @Rename("<init>")
    private void init(String message, TThrowable cause) {
        this.suppressionEnabled = true;
        this.writableStackTrace = true;
        fillInStackTrace();
        this.message = message;
        this.cause = cause;
    }

    @SuppressWarnings("unused")
    @Rename("fakeInit")
    public TThrowable(TThrowable cause) {
    }

    // JDK: the detail message of a throwable constructed from a cause alone is the cause's toString().
    @Rename("<init>")
    private void init(TThrowable cause) {
        this.suppressionEnabled = true;
        this.writableStackTrace = true;
        fillInStackTrace();
        this.message = cause == null ? null : cause.toString();
        this.cause = cause;
    }

    @Override
    public Throwable fillInStackTrace() {
        if (PlatformDetector.isLowLevel()) {
            stackTrace = trimStackTrace((TStackTraceElement[]) (Object) ExceptionHandling.fillStackTrace());
        }
        return this;
    }

    @Rename("getMessage")
    public String getMessage0() {
        return message;
    }

    @Rename("getLocalizedMessage")
    public String getLocalizedMessage0() {
        return getMessage();
    }

    @Remove
    public native TThrowable getCause();

    @Rename("getCause")
    public TThrowable getCause0() {
        return cause != this ? cause : null;
    }

    @Remove
    public native Class<?> getClass0();

    @Rename("toString")
    public String toString0() {
        String message = getLocalizedMessage();
        return getClass().getName() + (message != null ? ": " + message : "");
    }

    public TThrowable initCause(TThrowable cause) {
        if (this.cause != this && this.cause != null) {
            throw new IllegalStateException("Cause already set");
        }
        if (cause == this) {
            throw new IllegalArgumentException("Circular causation relation");
        }
        this.cause = cause;
        return this;
    }

    @Override
    public void printStackTrace() {
        printStackTrace(System.err);
    }

    public void printStackTrace(PrintStream stream) {
        printStackTrace(new StackTraceSink(stream, null));
    }

    public void printStackTrace(PrintWriter stream) {
        printStackTrace(new StackTraceSink(null, stream));
    }

    // The JDK's layout: the trace, then suppressed exceptions and the cause, each without the frames it shares with
    // the trace that encloses it, guarding against cycles.
    private void printStackTrace(StackTraceSink s) {
        List<TThrowable> dejaVu = new ArrayList<>();
        dejaVu.add(this);
        s.println(String.valueOf(this));
        TStackTraceElement[] trace = getOurStackTrace();
        for (TStackTraceElement element : trace) {
            s.println("\tat " + element);
        }
        for (TThrowable se : getOurSuppressed()) {
            se.printEnclosedStackTrace(s, trace, "Suppressed: ", "\t", dejaVu);
        }
        TThrowable ourCause = cause != this ? cause : null;
        if (ourCause != null) {
            ourCause.printEnclosedStackTrace(s, trace, "Caused by: ", "", dejaVu);
        }
    }

    private void printEnclosedStackTrace(StackTraceSink s, TStackTraceElement[] enclosingTrace, String caption,
            String prefix, List<TThrowable> dejaVu) {
        for (TThrowable seen : dejaVu) {
            if (seen == this) {
                s.println(prefix + caption + "[CIRCULAR REFERENCE: " + this + "]");
                return;
            }
        }
        dejaVu.add(this);
        TStackTraceElement[] trace = getOurStackTrace();
        int m = trace.length - 1;
        int n = enclosingTrace.length - 1;
        while (m >= 0 && n >= 0 && trace[m].equals(enclosingTrace[n])) {
            m--;
            n--;
        }
        int framesInCommon = trace.length - 1 - m;
        s.println(prefix + caption + this);
        for (int i = 0; i <= m; i++) {
            s.println(prefix + "\tat " + trace[i]);
        }
        if (framesInCommon != 0) {
            s.println(prefix + "\t... " + framesInCommon + " more");
        }
        for (TThrowable se : getOurSuppressed()) {
            se.printEnclosedStackTrace(s, trace, "Suppressed: ", prefix + "\t", dejaVu);
        }
        TThrowable ourCause = cause != this ? cause : null;
        if (ourCause != null) {
            ourCause.printEnclosedStackTrace(s, trace, "Caused by: ", prefix, dejaVu);
        }
    }

    // The JDK leaves out the frames that construct the throwable, the generated classes implementing lambdas (hidden
    // classes there), the compiler's helpers that raise runtime checks, and the frames that start a thread below its
    // run method or main.
    private TStackTraceElement[] trimStackTrace(TStackTraceElement[] frames) {
        int start = 0;
        while (start < frames.length && frames[start].getMethodName().equals("fillInStackTrace")) {
            start++;
        }
        while (start < frames.length && frames[start].getMethodName().equals("<init>")
                && isThrowableClass(frames[start].getClassName())) {
            start++;
        }
        // A runtime check (a failed cast, say) raises through a compiler helper; the JDK reports the checking frame.
        while (start < frames.length && frames[start].getClassName().startsWith("org.teavm.")) {
            start++;
        }
        int end = start;
        while (end < frames.length && !isThreadEntry(frames[end])) {
            end++;
        }
        int count = 0;
        TStackTraceElement[] result = new TStackTraceElement[end - start];
        for (int i = start; i < end; i++) {
            if (!frames[i].getClassName().contains(LambdaNames.MARKER)) {
                frames[i].recorded = true;
                result[count++] = frames[i];
            }
        }
        return count == result.length ? result : TArrays.copyOf(result, count);
    }

    private boolean isThrowableClass(String className) {
        for (Class<?> cls = getClass(); cls != null; cls = cls.getSuperclass()) {
            if (cls.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isThreadEntry(TStackTraceElement frame) {
        String className = frame.getClassName();
        return className.startsWith("org.teavm.runtime.")
                || className.equals(Thread.class.getName()) && frame.getMethodName().equals(TThread.ENTRY_METHOD);
    }

    private TThrowable[] getOurSuppressed() {
        return suppressed != null ? suppressed : new TThrowable[0];
    }

    private TStackTraceElement[] getOurStackTrace() {
        return stackTrace != null ? stackTrace : new TStackTraceElement[0];
    }

    private static final class StackTraceSink {
        private final PrintStream stream;
        private final PrintWriter writer;

        StackTraceSink(PrintStream stream, PrintWriter writer) {
            this.stream = stream;
            this.writer = writer;
        }

        void println(String line) {
            if (stream != null) {
                stream.println(line);
            } else {
                writer.println(line);
            }
        }
    }

    @Rename("getStackTrace")
    public TStackTraceElement[] getStackTrace0() {
        return stackTrace != null ? stackTrace.clone() : new TStackTraceElement[0];
    }

    public void setStackTrace(@SuppressWarnings("unused") TStackTraceElement[] stackTrace) {
        this.stackTrace = stackTrace.clone();
    }

    @Rename("getSuppressed")
    public final TThrowable[] getSuppressed0() {
        TThrowable[] current = getOurSuppressed();
        return TArrays.copyOf(current, current.length);
    }

    public final void addSuppressed(TThrowable exception) {
        if (exception == this) {
            throw new IllegalArgumentException("Self-suppression not permitted", exception);
        }
        if (exception == null) {
            throw new NullPointerException("Cannot suppress a null exception.");
        }
        if (!suppressionEnabled) {
            return;
        }
        TThrowable[] current = getOurSuppressed();
        suppressed = TArrays.copyOf(current, current.length + 1);
        suppressed[suppressed.length - 1] = exception;
    }
}
