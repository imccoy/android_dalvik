/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.dx.dex.code.form;

import com.android.dx.dex.code.DalvInsn;
import com.android.dx.dex.code.Dop;
import com.android.dx.dex.code.InsnFormat;
import com.android.dx.dex.code.SimpleInsn;
import com.android.dx.dex.file.DexFile;
import com.android.dx.rop.code.RegisterSpec;
import com.android.dx.rop.code.RegisterSpecList;
import com.android.dx.rop.code.SourcePosition;
import com.android.dx.rop.type.Type;
import com.android.dx.util.AnnotatedOutput;
import com.android.dx.util.ByteArray;
import com.android.dx.util.ValueWithSize;

/**
 * Instruction format {@code 32x}. See the instruction format spec
 * for details.
 */
public final class Form32x extends InsnFormat {
    /** {@code non-null;} unique instance of this class */
    public static final InsnFormat THE_ONE = new Form32x();

    /**
     * Constructs an instance. This class is not publicly
     * instantiable. Use {@link #THE_ONE}.
     */
    private Form32x() {
        // This space intentionally left blank.
    }

    /** {@inheritDoc} */
    @Override
    public String insnArgString(DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        return regs.get(0).regString() + ", " + regs.get(1).regString();
    }

    /** {@inheritDoc} */
    @Override
    public String insnCommentString(DalvInsn insn, boolean noteIndices) {
        // This format has no comment.
        return "";
    }

    /** {@inheritDoc} */
    @Override
    public int codeSize() {
        return 3;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCompatible(DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        return (insn instanceof SimpleInsn) &&
            (regs.size() == 2) &&
            unsignedFitsInShort(regs.get(0).getReg()) &&
            unsignedFitsInShort(regs.get(1).getReg());
    }

    /** {@inheritDoc} */
    @Override
    public InsnFormat nextUp() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void writeTo(AnnotatedOutput out, DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();

        write(out,
              opcodeUnit(insn, 0),
              (short) regs.get(0).getReg(),
              (short) regs.get(1).getReg());
    }

    public ValueWithSize<DalvInsn> parse(DexFile file, Dop opcode, ByteArray byteArray, int offset, int address) {
	int cu2 = byteArray.getShort(offset + 2);
        int a = (lowByte(cu2) << 8) | highByte(cu2);
	int cu3 = byteArray.getShort(offset + 4);
        int b = (lowByte(cu3) << 8) | highByte(cu3);
        RegisterSpecList regs = RegisterSpecList.make(RegisterSpec.make(a, Type.VOID), RegisterSpec.make(a, Type.VOID));
        SimpleInsn insn = new SimpleInsn(opcode, SourcePosition.NO_INFO, regs);
        return new ValueWithSize<DalvInsn>(insn, 6);
    }


}
