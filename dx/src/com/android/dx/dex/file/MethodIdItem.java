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

package com.android.dx.dex.file;

import com.android.dx.util.ByteArray;
import com.android.dx.rop.cst.CstBaseMethodRef;
import com.android.dx.rop.cst.CstMethodRef;
import com.android.dx.rop.cst.CstNat;
import com.android.dx.rop.cst.CstType;
import com.android.dx.rop.cst.CstUtf8;

/**
 * Representation of a method reference inside a Dalvik file.
 */
public final class MethodIdItem extends MemberIdItem {
    /**
     * Constructs an instance.
     *
     * @param method {@code non-null;} the constant for the method
     */
    public MethodIdItem(CstBaseMethodRef method) {
        super(method);
    }

    /** {@inheritDoc} */
    @Override
    public ItemType itemType() {
        return ItemType.TYPE_METHOD_ID_ITEM;
    }

    public MethodIdItem(ByteArray byteArray, int index) {
        this(parseMethodRef(byteArray, index));
    }

    public static CstMethodRef parseMethodRef(ByteArray byteArray, int index) {
        int methodIdsOffset = byteArray.getInt2(0x5C);
        int methodIdOffset = methodIdsOffset + (index * WRITE_SIZE);
        int definingClassOffset = byteArray.getShort2(methodIdOffset);
        CstType definingClass = new TypeIdItem(byteArray, definingClassOffset).getDefiningClass();

        int prototypeOffset = byteArray.getShort2(methodIdOffset + 2);
        CstUtf8 prototype = new CstUtf8(new ProtoIdItem(byteArray, prototypeOffset).getPrototype().getDescriptor());

        int nameOffset = byteArray.getInt2(methodIdOffset + 4);
        CstUtf8 name = new CstUtf8(new StringIdItem(byteArray, nameOffset).getValue().getString()); 
        System.out.println("method " + name + " of type " + prototype + " defined by " + definingClass);

        return new CstMethodRef(definingClass, new CstNat(name, prototype));
    }

    /** {@inheritDoc} */
    @Override
    public void addContents(DexFile file) {
        super.addContents(file);

        ProtoIdsSection protoIds = file.getProtoIds();
        protoIds.intern(getMethodRef().getPrototype());
    }

    /**
     * Gets the method constant.
     *
     * @return {@code non-null;} the constant
     */
    public CstBaseMethodRef getMethodRef() {
        return (CstBaseMethodRef) getRef();
    }

    /** {@inheritDoc} */
    @Override
    protected int getTypoidIdx(DexFile file) {
        ProtoIdsSection protoIds = file.getProtoIds();
        return protoIds.indexOf(getMethodRef().getPrototype());
    }

    /** {@inheritDoc} */
    @Override
    protected String getTypoidName() {
        return "proto_idx";
    }
}
