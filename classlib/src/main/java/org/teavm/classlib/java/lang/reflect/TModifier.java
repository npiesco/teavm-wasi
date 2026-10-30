/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.classlib.java.lang.reflect;

import org.teavm.classlib.java.lang.TClass;

public class TModifier {
    private static String[] modifierNames;
    public static final int PUBLIC = 1;
    public static final int PRIVATE = 2;
    public static final int PROTECTED = 4;
    public static final int STATIC = 8;
    public static final int FINAL = 16;
    public static final int SYNCHRONIZED = 32;
    public static final int VOLATILE = 64;
    public static final int TRANSIENT = 128;
    public static final int NATIVE = 256;
    public static final int INTERFACE = 512;
    public static final int ABSTRACT = 1024;
    public static final int STRICT = 2048;
    private static final int[] canonicalOrder = { PUBLIC, PROTECTED, PRIVATE, ABSTRACT, STATIC, FINAL, TRANSIENT,
            VOLATILE, SYNCHRONIZED, NATIVE, STRICT, INTERFACE };

    private TModifier() {
    }

    private static final int ACCESS_MODIFIERS = PUBLIC | PROTECTED | PRIVATE;

    public static int classModifiers() {
        return PUBLIC | PROTECTED | PRIVATE | ABSTRACT | STATIC | FINAL | STRICT;
    }

    public static int interfaceModifiers() {
        return PUBLIC | PROTECTED | PRIVATE | ABSTRACT | STATIC | STRICT;
    }

    public static int constructorModifiers() {
        return ACCESS_MODIFIERS;
    }

    public static int methodModifiers() {
        return ACCESS_MODIFIERS | ABSTRACT | STATIC | FINAL | SYNCHRONIZED | NATIVE | STRICT;
    }

    public static int fieldModifiers() {
        return ACCESS_MODIFIERS | STATIC | FINAL | TRANSIENT | VOLATILE;
    }

    public static int parameterModifiers() {
        return FINAL;
    }

    // Executable.sharedToString: modifiers followed by a space, with "default" after the access modifier.
    static void appendModifiers(StringBuilder sb, int mod, boolean isDefault) {
        if (mod != 0 && !isDefault) {
            sb.append(toString(mod)).append(' ');
            return;
        }
        int access = mod & ACCESS_MODIFIERS;
        if (access != 0) {
            sb.append(toString(access)).append(' ');
        }
        if (isDefault) {
            sb.append("default ");
        }
        mod &= ~ACCESS_MODIFIERS;
        if (mod != 0) {
            sb.append(toString(mod)).append(' ');
        }
    }

    static void appendSignature(StringBuilder sb, TClass<?>[] parameterTypes, TClass<?>[] exceptionTypes) {
        sb.append('(');
        for (int i = 0; i < parameterTypes.length; ++i) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parameterTypes[i].getTypeName());
        }
        sb.append(')');
        for (int i = 0; i < exceptionTypes.length; ++i) {
            sb.append(i == 0 ? " throws " : ",").append(exceptionTypes[i].getTypeName());
        }
    }

    public static boolean isPublic(int mod) {
        return (mod & PUBLIC) != 0;
    }

    public static boolean isPrivate(int mod) {
        return (mod & PRIVATE) != 0;
    }

    public static boolean isProtected(int mod) {
        return (mod & PROTECTED) != 0;
    }

    public static boolean isStatic(int mod) {
        return (mod & STATIC) != 0;
    }

    public static boolean isFinal(int mod) {
        return (mod & FINAL) != 0;
    }

    public static boolean isSynchronized(int mod) {
        return (mod & SYNCHRONIZED) != 0;
    }

    public static boolean isVolatile(int mod) {
        return (mod & VOLATILE) != 0;
    }

    public static boolean isTransient(int mod) {
        return (mod & TRANSIENT) != 0;
    }

    public static boolean isNative(int mod) {
        return (mod & NATIVE) != 0;
    }

    public static boolean isInterface(int mod) {
        return (mod & INTERFACE) != 0;
    }

    public static boolean isAbstract(int mod) {
        return (mod & ABSTRACT) != 0;
    }

    public static boolean isStrict(int mod) {
        return (mod & STRICT) != 0;
    }

    public static String toString(int mod) {
        StringBuilder sb = new StringBuilder();
        String[] modifierNames = getModifierNames();
        int index = 0;
        for (int modifier : canonicalOrder) {
            if ((mod & modifier) != 0) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(modifierNames[index]);
            }
            ++index;
        }
        return sb.toString();
    }

    private static String[] getModifierNames() {
        if (modifierNames == null) {
            modifierNames = new String[] { "public", "protected", "private", "abstract", "static", "final",
                    "transient", "volatile", "synchronized", "native", "strictfp", "interface" };
        }
        return modifierNames;
    }
}
