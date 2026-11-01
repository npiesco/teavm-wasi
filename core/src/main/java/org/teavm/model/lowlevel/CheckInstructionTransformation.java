/*
 *  Copyright 2018 Alexey Andreev.
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
package org.teavm.model.lowlevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.teavm.common.Graph;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.util.BasicBlockSplitter;
import org.teavm.model.util.DefinitionExtractor;
import org.teavm.model.util.ProgramUtils;
import org.teavm.runtime.ExceptionHandling;

public class CheckInstructionTransformation {
    private BasicBlock returnBlock;
    private BasicBlock next;
    private List<BasicBlock> throwBlocks = new ArrayList<>();
    private Set<Instruction> checkedDivisions = new HashSet<>();
    private Set<Variable> nonZeroConstants = new HashSet<>();

    public void apply(Program program, ValueType returnType) {
        BasicBlockSplitter splitter = new BasicBlockSplitter(program);

        returnBlock = null;
        throwBlocks.clear();
        checkedDivisions.clear();
        findNonZeroConstants(program);
        int count = program.basicBlockCount();
        for (int i = 0; i < count; ++i) {
            next = program.basicBlockAt(i);
            BasicBlock block;
            while (next != null) {
                block = next;
                next = null;
                for (Instruction instruction : block) {
                    if (instruction instanceof NullCheckInstruction) {
                        replaceNullCheck(splitter, program, (NullCheckInstruction) instruction);
                    } else if (instruction instanceof BoundCheckInstruction) {
                        replaceBoundCheck(splitter, program, (BoundCheckInstruction) instruction);
                    } else if (instruction instanceof BinaryInstruction
                            && needsZeroCheck((BinaryInstruction) instruction)) {
                        insertZeroCheck(splitter, program, (BinaryInstruction) instruction);
                        break;
                    }
                }
            }
        }

        if (returnBlock != null) {
            ExitInstruction fakeExit = new ExitInstruction();
            if (returnType != ValueType.VOID) {
                Variable fakeReturnVar = program.createVariable();
                createFakeReturnValue(returnBlock, fakeReturnVar, returnType);
                fakeExit.setValueToReturn(fakeReturnVar);
            }
            returnBlock.add(fakeExit);
        }

        splitter.fixProgram();
        attachExceptionHandlers(program);
    }

    // A throw block runs the check's throw call, so a try/catch around the check must catch it: the block gets the
    // checked block's try/catch blocks, and each handler phi the value its variable has at the end of that block.
    // This runs after the splitter has fixed handler phis for every split part.
    private void attachExceptionHandlers(Program program) {
        if (throwBlocks.isEmpty()) {
            return;
        }
        Graph cfg = ProgramUtils.buildControlFlowGraph(program);
        DefinitionExtractor defExtractor = new DefinitionExtractor();
        for (BasicBlock throwBlock : throwBlocks) {
            BasicBlock checked = null;
            for (int predecessor : cfg.incomingEdges(throwBlock.getIndex())) {
                BasicBlock candidate = program.basicBlockAt(predecessor);
                if (!candidate.getTryCatchBlocks().isEmpty()) {
                    checked = candidate;
                    break;
                }
            }
            if (checked == null) {
                continue;
            }
            List<Variable> definedInChecked = new ArrayList<>();
            for (Phi phi : checked.getPhis()) {
                definedInChecked.add(phi.getReceiver());
            }
            definedInChecked.addAll(ProgramUtils.getVariablesDefinedInBlock(checked, defExtractor));
            Set<BasicBlock> handlers = new HashSet<>();
            for (TryCatchBlock tryCatch : checked.getTryCatchBlocks()) {
                if (!handlers.add(tryCatch.getHandler())) {
                    continue;
                }
                for (Phi phi : tryCatch.getHandler().getPhis()) {
                    Variable value = null;
                    int position = -2;
                    for (Incoming incoming : phi.getIncomings()) {
                        if (incoming.getSource() != checked) {
                            continue;
                        }
                        int incomingPosition = definedInChecked.indexOf(incoming.getValue());
                        if (incomingPosition > position) {
                            value = incoming.getValue();
                            position = incomingPosition;
                        }
                    }
                    if (value != null) {
                        Incoming incoming = new Incoming();
                        incoming.setSource(throwBlock);
                        incoming.setValue(value);
                        phi.getIncomings().add(incoming);
                    }
                }
            }
            throwBlock.getTryCatchBlocks().addAll(ProgramUtils.copyTryCatches(checked, program));
        }
    }

    private void replaceNullCheck(BasicBlockSplitter splitter, Program program, NullCheckInstruction nullCheck) {
        BasicBlock block = nullCheck.getBasicBlock();
        BasicBlock continueBlock = splitter.split(block, nullCheck);
        BasicBlock throwBlock = program.createBasicBlock();
        throwBlocks.add(throwBlock);

        InvokeInstruction throwNPE = new InvokeInstruction();
        throwNPE.setType(InvocationType.SPECIAL);
        String message = nullCheck.getLocation() != null ? nullCheck.getLocation().getNullMessage() : null;
        if (message != null) {
            StringConstantInstruction messageConstant = new StringConstantInstruction();
            messageConstant.setConstant(message);
            messageConstant.setReceiver(program.createVariable());
            messageConstant.setLocation(nullCheck.getLocation());
            throwBlock.add(messageConstant);
            throwNPE.setMethod(new MethodReference(ExceptionHandling.class, "throwNullPointerException",
                    String.class, void.class));
            throwNPE.setArguments(messageConstant.getReceiver());
        } else {
            throwNPE.setMethod(new MethodReference(ExceptionHandling.class, "throwNullPointerException",
                    void.class));
        }
        throwNPE.setLocation(nullCheck.getLocation());
        throwBlock.add(throwNPE);

        jumpToReturn(program, nullCheck, throwBlock);

        BranchingInstruction jumpIfNull = new BranchingInstruction(BranchingCondition.NULL);
        jumpIfNull.setOperand(nullCheck.getValue());
        jumpIfNull.setConsequent(throwBlock);
        jumpIfNull.setAlternative(continueBlock);
        jumpIfNull.setLocation(nullCheck.getLocation());
        nullCheck.replace(jumpIfNull);

        AssignInstruction assign = new AssignInstruction();
        assign.setAssignee(nullCheck.getValue());
        assign.setReceiver(nullCheck.getReceiver());
        assign.setLocation(nullCheck.getLocation());
        continueBlock.addFirst(assign);

        next = continueBlock;
    }

    private void replaceBoundCheck(BasicBlockSplitter splitter, Program program, BoundCheckInstruction boundCheck) {
        BasicBlock block = boundCheck.getBasicBlock();
        BasicBlock continueBlock = splitter.split(block, boundCheck);
        BasicBlock throwBlock = program.createBasicBlock();
        throwBlocks.add(throwBlock);

        // The length is read before either test, so the exception can report it as the JDK's message does.
        Variable length = null;
        if (boundCheck.getArray() != null) {
            ArrayLengthInstruction arrayLength = new ArrayLengthInstruction();
            arrayLength.setArray(boundCheck.getArray());
            arrayLength.setReceiver(program.createVariable());
            arrayLength.setLocation(boundCheck.getLocation());
            boundCheck.insertPrevious(arrayLength);
            length = arrayLength.getReceiver();
        }

        BasicBlock ifPositiveBlock = continueBlock;
        if (boundCheck.isLower() && length != null) {
            ifPositiveBlock = program.createBasicBlock();
        }

        InvokeInstruction throwAIOOBE = new InvokeInstruction();
        throwAIOOBE.setType(InvocationType.SPECIAL);
        if (length != null) {
            throwAIOOBE.setMethod(new MethodReference(ExceptionHandling.class, "throwArrayIndexOutOfBoundsException",
                    int.class, int.class, void.class));
            throwAIOOBE.setArguments(boundCheck.getIndex(), length);
        } else {
            throwAIOOBE.setMethod(new MethodReference(ExceptionHandling.class, "throwArrayIndexOutOfBoundsException",
                    void.class));
        }
        throwAIOOBE.setLocation(boundCheck.getLocation());
        throwBlock.add(throwAIOOBE);

        jumpToReturn(program, boundCheck, throwBlock);

        if (boundCheck.isLower()) {
            BranchingInstruction jumpIfLess = new BranchingInstruction(BranchingCondition.LESS);
            jumpIfLess.setOperand(boundCheck.getIndex());
            jumpIfLess.setConsequent(throwBlock);
            jumpIfLess.setAlternative(ifPositiveBlock);
            jumpIfLess.setLocation(boundCheck.getLocation());
            boundCheck.replace(jumpIfLess);
        }

        if (length != null) {
            BinaryInstruction compare = new BinaryInstruction(BinaryOperation.COMPARE, NumericOperandType.INT);
            compare.setFirstOperand(boundCheck.getIndex());
            compare.setSecondOperand(length);
            compare.setReceiver(program.createVariable());
            compare.setLocation(boundCheck.getLocation());

            BranchingInstruction jumpIfGreater = new BranchingInstruction(BranchingCondition.GREATER_OR_EQUAL);
            jumpIfGreater.setOperand(compare.getReceiver());
            jumpIfGreater.setConsequent(throwBlock);
            jumpIfGreater.setAlternative(continueBlock);
            jumpIfGreater.setLocation(boundCheck.getLocation());

            if (boundCheck.isLower()) {
                ifPositiveBlock.add(jumpIfGreater);
            } else {
                boundCheck.replace(jumpIfGreater);
            }
            jumpIfGreater.insertPrevious(compare);
        }

        AssignInstruction assign = new AssignInstruction();
        assign.setAssignee(boundCheck.getIndex());
        assign.setReceiver(boundCheck.getReceiver());
        assign.setLocation(boundCheck.getLocation());
        continueBlock.addFirst(assign);
    }

    // Divisors that are constants other than zero, defined once, need no check.
    private void findNonZeroConstants(Program program) {
        nonZeroConstants.clear();
        DefinitionExtractor defExtractor = new DefinitionExtractor();
        Set<Variable> redefined = new HashSet<>();
        Set<Variable> defined = new HashSet<>();
        for (BasicBlock block : program.getBasicBlocks()) {
            for (Phi phi : block.getPhis()) {
                if (!defined.add(phi.getReceiver())) {
                    redefined.add(phi.getReceiver());
                }
            }
            for (Instruction instruction : block) {
                instruction.acceptVisitor(defExtractor);
                for (Variable variable : defExtractor.getDefinedVariables()) {
                    if (!defined.add(variable)) {
                        redefined.add(variable);
                    }
                }
                if (instruction instanceof IntegerConstantInstruction
                        && ((IntegerConstantInstruction) instruction).getConstant() != 0) {
                    nonZeroConstants.add(((IntegerConstantInstruction) instruction).getReceiver());
                } else if (instruction instanceof LongConstantInstruction
                        && ((LongConstantInstruction) instruction).getConstant() != 0) {
                    nonZeroConstants.add(((LongConstantInstruction) instruction).getReceiver());
                }
            }
        }
        nonZeroConstants.removeAll(redefined);
    }

    private boolean needsZeroCheck(BinaryInstruction binary) {
        if (binary.getOperation() != BinaryOperation.DIVIDE && binary.getOperation() != BinaryOperation.MODULO) {
            return false;
        }
        if (binary.getOperandType() != NumericOperandType.INT && binary.getOperandType() != NumericOperandType.LONG) {
            return false;
        }
        return !checkedDivisions.contains(binary) && !nonZeroConstants.contains(binary.getSecondOperand());
    }

    // Java's integer division and remainder throw ArithmeticException for a zero divisor.
    private void insertZeroCheck(BasicBlockSplitter splitter, Program program, BinaryInstruction division) {
        checkedDivisions.add(division);
        BasicBlock block = division.getBasicBlock();
        BasicBlock continueBlock = splitter.split(block, division);
        division.delete();
        continueBlock.addFirst(division);

        BasicBlock throwBlock = program.createBasicBlock();
        throwBlocks.add(throwBlock);
        InvokeInstruction throwArithmetic = new InvokeInstruction();
        throwArithmetic.setType(InvocationType.SPECIAL);
        throwArithmetic.setMethod(new MethodReference(ExceptionHandling.class, "throwArithmeticException",
                void.class));
        throwArithmetic.setLocation(division.getLocation());
        throwBlock.add(throwArithmetic);
        jumpToReturn(program, division, throwBlock);

        Variable divisor = division.getSecondOperand();
        if (division.getOperandType() == NumericOperandType.LONG) {
            LongConstantInstruction zero = new LongConstantInstruction();
            zero.setReceiver(program.createVariable());
            zero.setLocation(division.getLocation());
            block.add(zero);
            BinaryInstruction compare = new BinaryInstruction(BinaryOperation.COMPARE, NumericOperandType.LONG);
            compare.setFirstOperand(divisor);
            compare.setSecondOperand(zero.getReceiver());
            compare.setReceiver(program.createVariable());
            compare.setLocation(division.getLocation());
            block.add(compare);
            divisor = compare.getReceiver();
        }
        BranchingInstruction jumpIfZero = new BranchingInstruction(BranchingCondition.EQUAL);
        jumpIfZero.setOperand(divisor);
        jumpIfZero.setConsequent(throwBlock);
        jumpIfZero.setAlternative(continueBlock);
        jumpIfZero.setLocation(division.getLocation());
        block.add(jumpIfZero);

        next = continueBlock;
    }

    private void jumpToReturn(Program program, Instruction instruction, BasicBlock throwBlock) {
        if (returnBlock == null) {
            returnBlock = program.createBasicBlock();
        }

        JumpInstruction jumpToFakeReturn = new JumpInstruction();
        jumpToFakeReturn.setTarget(returnBlock);
        jumpToFakeReturn.setLocation(instruction.getLocation());
        throwBlock.add(jumpToFakeReturn);
    }

    private void createFakeReturnValue(BasicBlock block, Variable variable, ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                case BYTE:
                case SHORT:
                case INTEGER:
                case CHARACTER:
                    IntegerConstantInstruction intZero = new IntegerConstantInstruction();
                    intZero.setReceiver(variable);
                    block.add(intZero);
                    return;
                case LONG:
                    LongConstantInstruction longZero = new LongConstantInstruction();
                    longZero.setReceiver(variable);
                    block.add(longZero);
                    return;
                case FLOAT:
                    FloatConstantInstruction floatZero = new FloatConstantInstruction();
                    floatZero.setReceiver(variable);
                    block.add(floatZero);
                    return;
                case DOUBLE:
                    DoubleConstantInstruction doubleZero = new DoubleConstantInstruction();
                    doubleZero.setReceiver(variable);
                    block.add(doubleZero);
                    return;
            }
        }

        NullConstantInstruction nullConstant = new NullConstantInstruction();
        nullConstant.setReceiver(variable);
        block.add(nullConstant);
    }
}
