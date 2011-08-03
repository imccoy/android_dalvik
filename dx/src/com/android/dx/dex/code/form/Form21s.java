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

import com.android.dx.dex.code.CstInsn;
import com.android.dx.dex.code.DalvInsn;
import com.android.dx.dex.code.Dop;
import com.android.dx.dex.code.InsnFormat;
import com.android.dx.dex.file.DexFile;
import com.android.dx.rop.code.RegisterSpec;
import com.android.dx.rop.code.RegisterSpecList;
import com.android.dx.rop.code.SourcePosition;
import com.android.dx.rop.cst.Constant;
import com.android.dx.rop.cst.CstInteger;
import com.android.dx.rop.cst.CstLiteralBits;
import com.android.dx.rop.type.Type;
import com.android.dx.util.AnnotatedOutput;
import com.android.dx.util.ByteArray;
import com.android.dx.util.ValueWithSize;

/**
 * Instruction format {@code 21s}. See the instruction format spec
 * for details.
 */
public final class Form21s extends InsnFormat {
    /** {@code non-null;} unique instance of this class */
    public static final InsnFormat THE_ONE = new Form21s();

    /**
     * Constructs an instance. This class is not publicly
     * instantiable. Use {@link #THE_ONE}.
     */
    private Form21s() {
        // This space intentionally left blank.
    }

    /** {@inheritDoc} */
    @Override
    public String insnArgString(DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        CstLiteralBits value = (CstLiteralBits) ((CstInsn) insn).getConstant();

        return regs.get(0).regString() + ", " + literalBitsString(value);
    }

    /** {@inheritDoc} */
    @Override
    public String insnCommentString(DalvInsn insn, boolean noteIndices) {
        CstLiteralBits value = (CstLiteralBits) ((CstInsn) insn).getConstant();
        return literalBitsComment(value, 16);
    }

    /** {@inheritDoc} */
    @Override
    public int codeSize() {
        return 2;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCompatible(DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        if (!((insn instanceof CstInsn) &&
              (regs.size() == 1) &&
              unsignedFitsInByte(regs.get(0).getReg()))) {
            return false;
        }

        CstInsn ci = (CstInsn) insn;
        Constant cst = ci.getConstant();

        if (!(cst instanceof CstLiteralBits)) {
            return false;
        }

        CstLiteralBits cb = (CstLiteralBits) cst;

        return cb.fitsInInt() && signedFitsInShort(cb.getIntBits());
    }

    /** {@inheritDoc} */
    @Override
    public InsnFormat nextUp() {
        return Form21h.THE_ONE;
    }

    /** {@inheritDoc} */
    @Override
    public void writeTo(AnnotatedOutput out, DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        int value =
            ((CstLiteralBits) ((CstInsn) insn).getConstant()).getIntBits();

        write(out,
              opcodeUnit(insn, regs.get(0).getReg()),
              (short) value);
    }

    public ValueWithSize<DalvInsn> parse(DexFile file, Dop opcode, ByteArray byteArray, int offset, int address) {
        int cu1 = byteArray.getShort(offset);
        int a = lowByte(cu1);
        int cu2 = byteArray.getShort(offset + 2);
        int b = ((lowByte(cu2) << 8)) | (highByte(cu2));
	RegisterSpecList regs = RegisterSpecList.make(RegisterSpec.make(a, Type.VOID));
        CstInsn insn = new CstInsn(opcode, SourcePosition.NO_INFO, regs, CstInteger.make(b)); 
        return new ValueWithSize<DalvInsn>(insn, 4);
    }

}
