/*
 *  Copyright 2015 Alexey Andreev.
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

import java.util.Objects;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.util.TObjects;

public final class TStackTraceElement extends TObject implements TSerializable {
    private String classLoaderName;
    private String moduleName;
    private String moduleVersion;
    private String declaringClass;
    private String methodName;
    private String fileName;
    private int lineNumber;
    // Set for frames the runtime records, as the JDK's VM-filled frames: they leave out the built-in "app" class
    // loader and the versions of platform modules, which elements built by programs print.
    transient boolean recorded;

    public TStackTraceElement(String declaringClass, String methodName, String fileName, int lineNumber) {
        this(null, null, null, declaringClass, methodName, fileName, lineNumber);
    }

    public TStackTraceElement(String classLoaderName, String moduleName, String moduleVersion, String declaringClass,
            String methodName, String fileName, int lineNumber) {
        if (declaringClass == null) {
            throw new TNullPointerException("Declaring class is null");
        }
        if (methodName == null) {
            throw new TNullPointerException("Method name is null");
        }
        this.classLoaderName = classLoaderName;
        this.moduleName = moduleName;
        this.moduleVersion = moduleVersion;
        this.declaringClass = declaringClass;
        this.methodName = methodName;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
    }

    public String getClassLoaderName() {
        return classLoaderName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getModuleVersion() {
        return moduleVersion;
    }

    public String getClassName() {
        return declaringClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public boolean isNativeMethod() {
        return lineNumber == -2;
    }

    @Override
    public int hashCode() {
        int result = 31 * declaringClass.hashCode() + methodName.hashCode();
        result = 31 * result + Objects.hashCode(classLoaderName);
        result = 31 * result + Objects.hashCode(moduleName);
        result = 31 * result + Objects.hashCode(moduleVersion);
        result = 31 * result + Objects.hashCode(fileName);
        result = 31 * result + lineNumber;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TStackTraceElement)) {
            return false;
        }
        TStackTraceElement other = (TStackTraceElement) obj;
        return TObjects.equals(classLoaderName, other.classLoaderName)
                && TObjects.equals(moduleName, other.moduleName)
                && TObjects.equals(moduleVersion, other.moduleVersion)
                && TObjects.equals(declaringClass, other.declaringClass)
                && TObjects.equals(methodName, other.methodName)
                && TObjects.equals(fileName, other.fileName)
                && lineNumber == other.lineNumber;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (classLoaderName != null && !classLoaderName.isEmpty() && !(recorded && classLoaderName.equals("app"))) {
            sb.append(classLoaderName).append('/');
        }
        if (moduleName != null && !moduleName.isEmpty()) {
            sb.append(moduleName);
            if (moduleVersion != null && !moduleVersion.isEmpty() && !recorded) {
                sb.append('@').append(moduleVersion);
            }
        }
        if (sb.length() > 0) {
            sb.append('/');
        }
        sb.append(declaringClass).append('.').append(methodName).append('(');
        if (isNativeMethod()) {
            sb.append("Native Method");
        } else if (fileName == null) {
            sb.append("Unknown Source");
        } else {
            sb.append(fileName);
            if (lineNumber >= 0) {
                sb.append(':').append(lineNumber);
            }
        }
        return sb.append(')').toString();
    }
}
