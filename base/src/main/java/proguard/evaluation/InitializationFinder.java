/*
 * ProGuardCORE -- library to process Java bytecode.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package proguard.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import proguard.classfile.*;
import proguard.classfile.attribute.*;
import proguard.classfile.attribute.visitor.AttributeVisitor;
import proguard.classfile.attribute.visitor.ExceptionInfoVisitor;
import proguard.classfile.editor.ClassEstimates;
import proguard.classfile.instruction.InstructionFactory;
import proguard.classfile.instruction.visitor.InstructionVisitor;
import proguard.evaluation.value.*;
import proguard.util.ArrayUtil;

/**
 * This {@link AttributeVisitor} links 'new' instructions and their corresponding initializers in
 * the {@link CodeAttribute} instances that it visits.
 *
 * @author Eric Lafortune
 */
public class InitializationFinder implements AttributeVisitor, InstructionVisitor, ExceptionInfoVisitor {
  // *
  private static final boolean DEBUG = false;
  /*/
  private static       boolean DEBUG = System.getProperty("if") != null;
  //*/

  public static final int NONE = -1;

  private final PartialEvaluator partialEvaluator;
  private final boolean runPartialEvaluator;

  private int superInitializationOffset;
  private int[] initializationOffsets = new int[ClassEstimates.TYPICAL_CODE_LENGTH];
  private InstructionOffsetValue[] uninitializedOffsets =
      new InstructionOffsetValue[ClassEstimates.TYPICAL_CODE_LENGTH];

  /** Creates a new InitializationFinder. */
  public InitializationFinder() {
    this(new ReferenceTracingValueFactory(new BasicValueFactory()));
  }

  /**
   * Creates a new InitializationFinder. This private constructor gets around the constraint that
   * it's not allowed to add statements before calling 'this'.
   */
  private InitializationFinder(ReferenceTracingValueFactory referenceTracingValueFactory) {
    this(
        new PartialEvaluator(
            referenceTracingValueFactory,
            new ReferenceTracingInvocationUnit(
                new BasicInvocationUnit(referenceTracingValueFactory)),
            true,
            referenceTracingValueFactory),
        true);
  }

  /**
   * Creates a new InitializationFinder that will use the given partial evaluator.
   *
   * @param partialEvaluator the evaluator to be used for the analysis.
   * @param runPartialEvaluator specifies whether to run this evaluator on every code attribute that
   *     is visited.
   */
  public InitializationFinder(PartialEvaluator partialEvaluator, boolean runPartialEvaluator) {
    this.partialEvaluator = partialEvaluator;
    this.runPartialEvaluator = runPartialEvaluator;
  }

  /**
   * Returns whether the method is an instance initializer, in the CodeAttribute that was visited
   * most recently.
   */
  public boolean isInitializer() {
    return superInitializationOffset != NONE;
  }

  /**
   * Returns the instruction offset at which this initializer is calling the "super" or "this"
   * initializer method, or <code>NONE</code> if it is not an initializer.
   */
  public int superInitializationOffset() {
    return superInitializationOffset;
  }

  //    /**
  //     * Returns whether the instruction at the given offset is a 'new'
  //     * instruction.
  //     */
  //    public boolean isNew(int offset)
  //    {
  //        return initializationOffsets[offset] != NONE;
  //    }
  //
  //
  //    /**
  //     * Returns the instruction offset at which the object instance that is
  //     * created at the given 'new' instruction offset is initialized, or
  //     * <code>NONE</code> if it is not being created.
  //     */
  //    public int initializationOffset(int creationOffset)
  //    {
  //        return initializationOffsets[creationOffset];
  //    }

  /**
   * Returns the 'new' instruction offset at which the object instance is created that is
   * initialized at the given offset.
   */
  public int creationOffset(int initializationOffset) {
    return creationOffsetValue(initializationOffset).instructionOffset(0);
  }

  /** Returns whether the specified stack entry is initialized. */
  public boolean isInitializedBefore(int offset, int stackEntryIndexBottom) {
    InstructionOffsetValue creationOffsetValue = creationOffsetValue(offset, stackEntryIndexBottom);

    return isInitializedBefore(offset, creationOffsetValue);
  }

  /** Returns whether the specified stack entry is initialized. */
  public boolean isTopInitializedBefore(int offset, int stackEntryIndexTop) {
    return isInitializedBefore(
        offset, (partialEvaluator.getStackBefore(offset).size() - 1) - stackEntryIndexTop);
  }

  /** Returns whether the given creation offset is initialized before the given offset. */
  public boolean isInitializedBefore(int offset, InstructionOffsetValue creationOffsetValue) {
    return !uninitializedOffsets[offset].contains(creationOffsetValue.instructionOffset(0));
  }

  /**
   * Returns whether the instruction at the given offset is the special invocation of an instance
   * initializer.
   */
  public boolean isInitializer(int offset) {
    return partialEvaluator.isInitializer(offset);
  }

  // Implementations for AttributeVisitor.

  public void visitAnyAttribute(Clazz clazz, Attribute attribute) {}

  public void visitCodeAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute) {
    //        DEBUG =
    //            clazz.getName().equals("abc/Def") &&
    //            method.getName(clazz).equals("abc");

    int codeLength = codeAttribute.u4codeLength;

    superInitializationOffset = NONE;

    // Make sure the global arrays are sufficiently large.
    initializationOffsets = ArrayUtil.ensureArraySize(initializationOffsets, codeLength, NONE);
    uninitializedOffsets = ArrayUtil.ensureArraySize(uninitializedOffsets, codeLength, null);

    // Evaluate the method.
    if (runPartialEvaluator) {
      partialEvaluator.visitCodeAttribute(clazz, method, codeAttribute);
    }

    exceptionBranchTarget.clear();
    codeAttribute.exceptionsAccept(clazz, method, this);

    // Loop over all instructions. This is sufficient, because the JVM
    // specifications don't allow uninitialized instances on the stack or
    // in variables when branching backward. JVMs without preverification
    // and the Dalvik VM do allow it in practice.
    InstructionOffsetValue currentUninitializedOffsets =
        method.getName(clazz).equals(ClassConstants.METHOD_NAME_INIT)
            ? new InstructionOffsetValue(InstructionOffsetValue.METHOD_PARAMETER)
            : InstructionOffsetValue.EMPTY_VALUE;

    for(int tryIndex = 0; tryIndex < 10; tryIndex++) {


      for (int offset = 0; offset < codeLength; offset++) {
        if (partialEvaluator.isTraced(offset)) {
          // Exception handlers start without uninitialized instances
          // (on the stack or in variables).
          if (partialEvaluator.isExceptionHandler(offset)) {
            for (; offset < codeLength; offset++) {
              if (partialEvaluator.isTraced(offset)) {
                if (uninitializedOffsets[offset] != null) {
                  break;
                }
              }
            }

            if (offset >= codeLength) {
              throw new RuntimeException("why ");
            }
          }

          // Check if the uninitialized creation offsets have been set
          // before (because of a forward branch).
          if (uninitializedOffsets[offset] != null) {
            // Continue using them.
            currentUninitializedOffsets = uninitializedOffsets[offset];
          } else {
            uninitializedOffsets[offset] = currentUninitializedOffsets;
          }

          // Is it a 'new' instruction?
          if (partialEvaluator.isCreation(offset)) {
            // Add its offset to the current list.
            currentUninitializedOffsets = currentUninitializedOffsets.add(offset);
          }
          // Is it an instance initialization?
          else if (partialEvaluator.isInitializer(offset)) {
            // Remove its creation offset from the current list.
            InstructionOffsetValue creationOffsetValue = creationOffsetValue(offset);

            int creationOffset = creationOffsetValue.instructionOffset(0);

            if (creationOffsetValue.isMethodParameter(0)) {
              // Remember the super initialization offset of the
              // initializer method.
              superInitializationOffset = offset;
            } else {
              // Remember the instance initialization for the 'new'
              // instruction.
              initializationOffsets[creationOffset] = offset;
            }

            currentUninitializedOffsets = currentUninitializedOffsets.remove(creationOffset);
          }

          // Propagate the uninitialized creation offsets to the forward
          // branch targets, if any.
          InstructionOffsetValue branchTargets = partialEvaluator.branchTargets(offset);

          if (branchTargets != null) {
            for (int branchIndex = 0;
                 branchIndex < branchTargets.instructionOffsetCount();
                 branchIndex++) {
              int branchOffset = branchTargets.instructionOffset(branchIndex);
              if (uninitializedOffsets[branchOffset] == null) {
                uninitializedOffsets[branchOffset] = currentUninitializedOffsets;
                System.out.println("branch:" + branchOffset);
              }
            }
          }

          List<Integer> exceptionHandlers = getExceptionHandler(offset);
          if (exceptionHandlers != null) {
            for (Integer exceptionHandle : exceptionHandlers) {
              if (uninitializedOffsets[exceptionHandle] == null) {
                uninitializedOffsets[exceptionHandle] = currentUninitializedOffsets;
                System.out.println("exception:" + exceptionHandle);
              }
            }
          }


        }
      }


      boolean continueThis = false;
      for (int offset = 0; offset < codeLength; offset++) {
        if (partialEvaluator.isTraced(offset)) {
          if (uninitializedOffsets[offset] == null) {
            System.out.println("InitFinder: offset is null：" + offset + " when try index:" + tryIndex);
            continueThis = true;
            break;
          }
        }
      }

      if (tryIndex == 9) {
        System.out.print("all null offset:");
        for (int offset = 0; offset < codeLength; offset++) {
          if (partialEvaluator.isTraced(offset)) {
            if (uninitializedOffsets[offset] == null) {
              System.out.print(offset + ",");
            }
          }
        }
        throw new RuntimeException("why do this");
      }
      if (!continueThis) {
        break;
      }
    }

    if (DEBUG) {
      System.out.println();
      System.out.println(
          "InitializationFinder: "
              + clazz.getName()
              + "."
              + method.getName(clazz)
              + method.getDescriptor(clazz));

      for (int offset = 0; offset < codeLength; offset++) {
        if (partialEvaluator.isInstruction(offset)) {
          System.out.println(
              (initializationOffsets[offset] >= 0 ? "i" + initializationOffsets[offset] : "   ")
                  + (uninitializedOffsets[offset] != null
                          && uninitializedOffsets[offset].instructionOffsetCount() > 0
                      ? " u" + uninitializedOffsets[offset]
                      : "    ")
                  + (isInitializer(offset) ? " " + creationOffsetValue(offset) : "    ")
                  + " "
                  + InstructionFactory.create(codeAttribute.code, offset).toString(offset));
        }
      }
    }
  }

  // Small utility methods.

  /**
   * Returns the 'new' instruction offset value (or method parameter) at which the object instance
   * is created that is initialized at the given offset.
   */
  private InstructionOffsetValue creationOffsetValue(int initializationOffset) {
    int stackEntryIndexBottom = partialEvaluator.getStackAfter(initializationOffset).size();

    return creationOffsetValue(initializationOffset, stackEntryIndexBottom);
  }

  /**
   * Returns the 'new' instruction offset value (or method parameter) of the specified stack entry.
   */
  private InstructionOffsetValue creationOffsetValue(
      int instructionOffset, int stackEntryIndexBottom) {
    // Get the reference value of the new instance.
    ReferenceValue newReferenceValue =
        partialEvaluator
            .getStackBefore(instructionOffset)
            .getBottom(stackEntryIndexBottom)
            .referenceValue();

    // It's a traced reference.
    TracedReferenceValue tracedReferenceValue = (TracedReferenceValue) newReferenceValue;

    // Get the trace value.
    return tracedReferenceValue.getTraceValue().instructionOffsetValue();
  }


  private HashMap<Integer, ArrayList<Integer>> exceptionBranchTarget = new HashMap<>();
  @Override
  public void visitExceptionInfo(Clazz clazz, Method method, CodeAttribute codeAttribute, ExceptionInfo exceptionInfo) {
    for (int endPC = exceptionInfo.u2endPC - 1; endPC >= exceptionInfo.u2startPC; endPC--) {
      if (partialEvaluator.isTraced(endPC) && endPC != exceptionInfo.u2handlerPC ) {
        exceptionBranchTarget.computeIfAbsent(endPC, k -> new ArrayList<>()).add(exceptionInfo.u2handlerPC);
        break;
      }
    }
  }


  public List<Integer> getExceptionHandler(int offset) {
    return exceptionBranchTarget.get(offset);
  }
}
