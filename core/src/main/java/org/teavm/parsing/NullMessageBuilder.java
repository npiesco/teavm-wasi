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
package org.teavm.parsing;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

/**
 * The JDK's helpful NullPointerException messages (JEP 358) for each bytecode that can dereference null: the failed
 * action, and the expression that produced the null value when the bytecode determines it. Sources are found by
 * dataflow over the method, as the JVM does, so a value merged from different producers has no description.
 */
final class NullMessageBuilder {
    private static final int MAX_CAUSE_DETAIL = 5;

    private final MethodNode method;
    private final InsnList instructions;
    private Frame<SourceValue>[] frames;

    private NullMessageBuilder(MethodNode method) {
        this.method = method;
        this.instructions = method.instructions;
    }

    // Messages indexed like method.instructions, or null when the method cannot be analyzed.
    static String[] build(MethodNode method) {
        NullMessageBuilder builder = new NullMessageBuilder(method);
        try {
            builder.frames = new Analyzer<>(new PassThroughInterpreter()).analyze("java/lang/Object", method);
        } catch (AnalyzerException | RuntimeException e) {
            return null;
        }
        String[] messages = new String[builder.instructions.size()];
        for (int i = 0; i < messages.length; ++i) {
            messages[i] = builder.message(i);
        }
        return messages;
    }

    private String message(int index) {
        Frame<SourceValue> frame = frames[index];
        if (frame == null) {
            return null;
        }
        AbstractInsnNode insn = instructions.get(index);
        String action;
        int slot;
        switch (insn.getOpcode()) {
            case Opcodes.IALOAD:
            case Opcodes.LALOAD:
            case Opcodes.FALOAD:
            case Opcodes.DALOAD:
            case Opcodes.AALOAD:
            case Opcodes.BALOAD:
            case Opcodes.CALOAD:
            case Opcodes.SALOAD:
                action = "Cannot load from " + arrayKind(insn.getOpcode()) + " array";
                slot = 1;
                break;
            case Opcodes.IASTORE:
            case Opcodes.FASTORE:
            case Opcodes.AASTORE:
            case Opcodes.BASTORE:
            case Opcodes.CASTORE:
            case Opcodes.SASTORE:
                action = "Cannot store to " + arrayKind(insn.getOpcode()) + " array";
                slot = 2;
                break;
            case Opcodes.LASTORE:
            case Opcodes.DASTORE:
                action = "Cannot store to " + arrayKind(insn.getOpcode()) + " array";
                slot = 3;
                break;
            case Opcodes.ARRAYLENGTH:
                action = "Cannot read the array length";
                slot = 0;
                break;
            case Opcodes.ATHROW:
                action = "Cannot throw exception";
                slot = 0;
                break;
            case Opcodes.MONITORENTER:
                action = "Cannot enter synchronized block";
                slot = 0;
                break;
            case Opcodes.MONITOREXIT:
                action = "Cannot exit synchronized block";
                slot = 0;
                break;
            case Opcodes.GETFIELD:
                action = "Cannot read field \"" + ((FieldInsnNode) insn).name + "\"";
                slot = 0;
                break;
            case Opcodes.PUTFIELD: {
                FieldInsnNode field = (FieldInsnNode) insn;
                action = "Cannot assign field \"" + field.name + "\"";
                slot = Type.getType(field.desc).getSize();
                break;
            }
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKEINTERFACE: {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.name.equals("<init>")) {
                    return null;
                }
                action = "Cannot invoke \"" + methodName(call) + "\"";
                slot = (Type.getArgumentsAndReturnSizes(call.desc) >> 2) - 1;
                break;
            }
            default:
                return null;
        }
        AbstractInsnNode source = source(frame, slot);
        if (source == null) {
            return action;
        }
        String cause = cause(index, slot, MAX_CAUSE_DETAIL, false);
        if (cause == null) {
            return action;
        }
        if (isInvoke(source.getOpcode())) {
            return action + " because the return value of \"" + cause + "\" is null";
        }
        return action + " because \"" + cause + "\" is null";
    }

    // The JVM's print_NPE_cause0: a description of the value in a stack slot (0 is the top) before index.
    private String cause(int index, int slot, int maxDetail, boolean inner) {
        if (maxDetail <= 0) {
            return null;
        }
        Frame<SourceValue> frame = frames[index];
        if (frame == null) {
            return null;
        }
        AbstractInsnNode source = source(frame, slot);
        if (source == null) {
            return null;
        }
        int sourceIndex = instructions.indexOf(source);
        switch (source.getOpcode()) {
            case Opcodes.ILOAD:
            case Opcodes.LLOAD:
            case Opcodes.FLOAD:
            case Opcodes.DLOAD:
            case Opcodes.ALOAD:
                return localVariable(sourceIndex, ((VarInsnNode) source).var);
            case Opcodes.ACONST_NULL:
                return "null";
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
                return String.valueOf(source.getOpcode() - Opcodes.ICONST_0);
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                return String.valueOf(((IntInsnNode) source).operand);
            case Opcodes.GETSTATIC: {
                FieldInsnNode field = (FieldInsnNode) source;
                return className(field.owner) + "." + field.name;
            }
            case Opcodes.GETFIELD: {
                String object = cause(sourceIndex, 0, maxDetail - 1, true);
                String name = ((FieldInsnNode) source).name;
                return object != null ? object + "." + name : name;
            }
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKEINTERFACE:
                return methodName((MethodInsnNode) source);
            case Opcodes.IALOAD:
            case Opcodes.LALOAD:
            case Opcodes.FALOAD:
            case Opcodes.DALOAD:
            case Opcodes.AALOAD:
            case Opcodes.BALOAD:
            case Opcodes.CALOAD:
            case Opcodes.SALOAD: {
                String array = cause(sourceIndex, 1, maxDetail - 1, inner);
                String element = cause(sourceIndex, 0, maxDetail - 1, true);
                return (array != null ? array : "<array>") + "[" + (element != null ? element : "...") + "]";
            }
            default:
                return null;
        }
    }

    // The single instruction that produced the value in a stack slot (counted in JVM words from the top).
    private static AbstractInsnNode source(Frame<SourceValue> frame, int slot) {
        int words = 0;
        for (int i = frame.getStackSize() - 1; i >= 0; --i) {
            SourceValue value = frame.getStack(i);
            words += value.getSize();
            if (slot < words) {
                return value.insns.size() == 1 ? value.insns.iterator().next() : null;
            }
        }
        return null;
    }

    // The JVM's print_local_var: the debug name in scope at the load, else this, <parameterN> or <localN>.
    private String localVariable(int loadIndex, int slot) {
        List<LocalVariableNode> variables = method.localVariables;
        if (variables != null) {
            for (LocalVariableNode variable : variables) {
                if (variable.index == slot && instructions.indexOf(variable.start) <= loadIndex
                        && loadIndex < instructions.indexOf(variable.end)) {
                    return variable.name;
                }
            }
        }
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        Frame<SourceValue> frame = frames[loadIndex];
        boolean isParameter = frame != null && slot < frame.getLocals() && frame.getLocal(slot).insns.isEmpty();
        if (!isStatic && slot == 0 && isParameter) {
            return "this";
        }
        int current = isStatic ? 0 : 1;
        int parameterIndex = 1;
        for (Type type : Type.getArgumentTypes(method.desc)) {
            if (slot >= current && slot < current + type.getSize()) {
                return isParameter ? "<parameter" + parameterIndex + ">" : "<local" + slot + ">";
            }
            current += type.getSize();
            parameterIndex++;
        }
        return "<local" + slot + ">";
    }

    private static boolean isInvoke(int opcode) {
        return opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESPECIAL || opcode == Opcodes.INVOKESTATIC
                || opcode == Opcodes.INVOKEINTERFACE;
    }

    private static String arrayKind(int opcode) {
        switch (opcode) {
            case Opcodes.IALOAD:
            case Opcodes.IASTORE:
                return "int";
            case Opcodes.LALOAD:
            case Opcodes.LASTORE:
                return "long";
            case Opcodes.FALOAD:
            case Opcodes.FASTORE:
                return "float";
            case Opcodes.DALOAD:
            case Opcodes.DASTORE:
                return "double";
            case Opcodes.BALOAD:
            case Opcodes.BASTORE:
                return "byte/boolean";
            case Opcodes.CALOAD:
            case Opcodes.CASTORE:
                return "char";
            case Opcodes.SALOAD:
            case Opcodes.SASTORE:
                return "short";
            default:
                return "object";
        }
    }

    // The JVM's print_method_name: Class.name(parameter types), with Object and String unqualified.
    private static String methodName(MethodInsnNode call) {
        StringBuilder sb = new StringBuilder(className(call.owner)).append('.').append(call.name).append('(');
        Type[] parameters = Type.getArgumentTypes(call.desc);
        for (int i = 0; i < parameters.length; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(shortenWellKnown(parameters[i].getClassName()));
        }
        return sb.append(')').toString();
    }

    private static String className(String internalName) {
        return shortenWellKnown(internalName.replace('/', '.'));
    }

    private static String shortenWellKnown(String name) {
        for (String known : new String[] { "java.lang.Object", "java.lang.String" }) {
            if (name.startsWith(known) && (name.length() == known.length() || name.charAt(known.length()) == '[')) {
                return known.substring("java.lang.".length()) + name.substring(known.length());
            }
        }
        return name;
    }

    // As the JVM's simulation: duplicating, swapping and casting keep the original producer of a value.
    private static final class PassThroughInterpreter extends SourceInterpreter {
        PassThroughInterpreter() {
            super(AsmUtil.API_VERSION);
        }

        @Override
        public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
            switch (insn.getOpcode()) {
                case Opcodes.DUP:
                case Opcodes.DUP_X1:
                case Opcodes.DUP_X2:
                case Opcodes.DUP2:
                case Opcodes.DUP2_X1:
                case Opcodes.DUP2_X2:
                case Opcodes.SWAP:
                    return value;
                default:
                    return super.copyOperation(insn, value);
            }
        }

        @Override
        public SourceValue unaryOperation(AbstractInsnNode insn, SourceValue value) {
            if (insn.getOpcode() == Opcodes.CHECKCAST) {
                return value;
            }
            return super.unaryOperation(insn, value);
        }
    }
}
