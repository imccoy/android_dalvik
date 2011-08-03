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
import com.android.dx.dex.code.DalvOps;
import com.android.dx.dex.code.InsnFormat;
import com.android.dx.dex.file.DexFile;
import com.android.dx.rop.code.RegisterSpec;
import com.android.dx.rop.code.RegisterSpecList;
import com.android.dx.rop.code.SourcePosition;
import com.android.dx.rop.cst.Constant;
import com.android.dx.rop.cst.CstFieldRef;
import com.android.dx.rop.cst.CstString;
import com.android.dx.rop.cst.CstType;
import com.android.dx.rop.type.Type;
import com.android.dx.dex.file.StringIdItem;
import com.android.dx.dex.file.TypeIdItem;
import com.android.dx.util.AnnotatedOutput;
import com.android.dx.util.ByteArray;
import com.android.dx.util.ValueWithSize;

/**
 * Instruction format {@code 21c}. See the instruction format spec
 * for details.
 */
public final class Form21c extends InsnFormat {
    /** {@code non-null;} unique instance of this class */
    public static final InsnFormat THE_ONE = new Form21c();

    /**
     * Constructs an instance. This class is not publicly
     * instantiable. Use {@link #THE_ONE}.
     */
    private Form21c() {
        // This space intentionally left blank.
    }

    /** {@inheritDoc} */
    @Override
    public String insnArgString(DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        return regs.get(0).regString() + ", " + cstString(insn);
    }

    /** {@inheritDoc} */
    @Override
    public String insnCommentString(DalvInsn insn, boolean noteIndices) {
        if (noteIndices) {
            return cstComment(insn);
        } else {
            return "";
        }
    }

    /** {@inheritDoc} */
    @Override
    public int codeSize() {
        return 2;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCompatible(DalvInsn insn) {
        if (!(insn instanceof CstInsn)) {
            return false;
        }

        RegisterSpecList regs = insn.getRegisters();
        RegisterSpec reg;

        switch (regs.size()) {
            case 1: {
                reg = regs.get(0);
                break;
            }
            case 2: {
                /*
                 * This format is allowed for ops that are effectively
                 * 2-arg but where the two args are identical.
                 */
                reg = regs.get(0);
                if (reg.getReg() != regs.get(1).getReg()) {
                    return false;
                }
                break;
            }
            default: {
                return false;
            }
        }

        if (!unsignedFitsInByte(reg.getReg())) {
            return false;
        }

        CstInsn ci = (CstInsn) insn;
        int cpi = ci.getIndex();

        if (! unsignedFitsInShort(cpi)) {
            return false;
        }

        Constant cst = ci.getConstant();
        return (cst instanceof CstType) ||
            (cst instanceof CstFieldRef) ||
            (cst instanceof CstString);
    }

    /** {@inheritDoc} */
    @Override
    public InsnFormat nextUp() {
        return Form31c.THE_ONE;
    }

    /** {@inheritDoc} */
    @Override
    public void writeTo(AnnotatedOutput out, DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        int cpi = ((CstInsn) insn).getIndex();

        write(out,
              opcodeUnit(insn, regs.get(0).getReg()),
              (short) cpi);
    }

    public ValueWithSize<DalvInsn> parse(DexFile file, Dop opcode, ByteArray byteArray, int offset, int address) {
        int a = byteArray.getByte(offset + 1);
	int cu2 = byteArray.getShort(offset + 2);
        int b = (lowByte(cu2) << 8) | highByte(cu2);
        RegisterSpecList regs = RegisterSpecList.make(RegisterSpec.make(a, Type.VOID));
	Constant constant = getConstant(opcode, byteArray, b);
        CstInsn insn = new CstInsn(opcode, SourcePosition.NO_INFO, regs, constant);
        return new ValueWithSize<DalvInsn>(insn, 4);
    }

    private Constant getConstant(Dop opcode, ByteArray byteArray, int index) {
	if (opcode.getFamily() == DalvOps.SGET) {
	    throw new RuntimeException("eeets a field");
	} else if (opcode.getOpcode() == DalvOps.CONST_STRING) {
	    return new StringIdItem(byteArray, index).getValue();
	} else { /* CONST_CLASS, CHECK_CAST or NEW_INSTANCE */
	    return new TypeIdItem(byteArray, index).getDefiningClass();
	}
    }

}
