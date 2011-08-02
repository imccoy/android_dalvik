/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.dx.dex.code.DalvCode;
import com.android.dx.rop.cst.Constant;
import com.android.dx.rop.cst.CstArray;
import com.android.dx.rop.cst.CstLiteralBits;
import com.android.dx.rop.cst.CstMethodRef;
import com.android.dx.rop.cst.CstType;
import com.android.dx.rop.cst.Zeroes;
import com.android.dx.rop.type.StdTypeList;
import com.android.dx.util.ByteArray;
import com.android.dx.util.ByteArrayAnnotatedOutput;
import com.android.dx.util.AnnotatedOutput;
import com.android.dx.util.Hex;
import com.android.dx.util.ValueWithSize;
import com.android.dx.util.Writers;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;

/**
 * Representation of all the parts of a Dalvik class that are generally
 * "inflated" into an in-memory representation at runtime. Instances of
 * this class are represented in a compact streamable form in a
 * {@code dex} file, as opposed to a random-access form.
 */
public final class ClassDataItem extends OffsettedItem {
    /** {@code non-null;} what class this data is for, just for listing generation */
    private final CstType thisClass;

    /** {@code non-null;} list of static fields */
    private final ArrayList<EncodedField> staticFields;

    /** {@code non-null;} list of initial values for static fields */
    private final HashMap<EncodedField, Constant> staticValues;

    /** {@code non-null;} list of instance fields */
    private final ArrayList<EncodedField> instanceFields;

    /** {@code non-null;} list of direct methods */
    private final ArrayList<EncodedMethod> directMethods;

    /** {@code non-null;} list of virtual methods */
    private final ArrayList<EncodedMethod> virtualMethods;

    /** {@code null-ok;} static initializer list; set in {@link #addContents} */
    private CstArray staticValuesConstant;

    /**
     * {@code null-ok;} encoded form, ready for writing to a file; set during
     * {@link #place0}
     */
    private byte[] encodedForm;

    /**
     * Constructs an instance. Its sets of members are initially
     * empty.
     *
     * @param thisClass {@code non-null;} what class this data is for, just
     * for listing generation
     */
    public ClassDataItem(CstType thisClass) {
        super(1, -1);

        if (thisClass == null) {
            throw new NullPointerException("thisClass == null");
        }

        this.thisClass = thisClass;
        this.staticFields = new ArrayList<EncodedField>(20);
        this.staticValues = new HashMap<EncodedField, Constant>(40);
        this.instanceFields = new ArrayList<EncodedField>(20);
        this.directMethods = new ArrayList<EncodedMethod>(20);
        this.virtualMethods = new ArrayList<EncodedMethod>(20);
        this.staticValuesConstant = null;
    }

    private ClassDataItem(CstType thisClass, ArrayList<EncodedField> staticFields, HashMap<EncodedField, Constant> staticValues, ArrayList<EncodedField> instanceFields, ArrayList<EncodedMethod> directMethods, ArrayList<EncodedMethod>virtualMethods, CstArray staticValuesConstant ) {
        super(1, -1);
        this.thisClass = thisClass;
        this.staticFields = staticFields;
        this.staticValues = staticValues;
        this.instanceFields = instanceFields;
        this.directMethods = directMethods;
        this.virtualMethods = virtualMethods;
        this.staticValuesConstant = staticValuesConstant;
    }


    /** {@inheritDoc} */
    @Override
    public ItemType itemType() {
        return ItemType.TYPE_CLASS_DATA_ITEM;
    }

    /** {@inheritDoc} */
    @Override
    public String toHuman() {
        return toString();
    }

    /**
     * Returns whether this instance is empty.
     *
     * @return {@code true} if this instance is empty or
     * {@code false} if at least one element has been added to it
     */
    public boolean isEmpty() {
        return staticFields.isEmpty() && instanceFields.isEmpty()
            && directMethods.isEmpty() && virtualMethods.isEmpty();
    }

    /**
     * Adds a static field.
     *
     * @param field {@code non-null;} the field to add
     * @param value {@code null-ok;} initial value for the field, if any
     */
    public void addStaticField(EncodedField field, Constant value) {
        if (field == null) {
            throw new NullPointerException("field == null");
        }

        if (staticValuesConstant != null) {
            throw new UnsupportedOperationException(
                    "static fields already sorted");
        }

        staticFields.add(field);
        staticValues.put(field, value);
    }

    /**
     * Adds an instance field.
     *
     * @param field {@code non-null;} the field to add
     */
    public void addInstanceField(EncodedField field) {
        if (field == null) {
            throw new NullPointerException("field == null");
        }

        instanceFields.add(field);
    }

    /**
     * Adds a direct ({@code static} and/or {@code private}) method.
     *
     * @param method {@code non-null;} the method to add
     */
    public void addDirectMethod(EncodedMethod method) {
        if (method == null) {
            throw new NullPointerException("method == null");
        }

        directMethods.add(method);
    }

    /**
     * Adds a virtual method.
     *
     * @param method {@code non-null;} the method to add
     */
    public void addVirtualMethod(EncodedMethod method) {
        if (method == null) {
            throw new NullPointerException("method == null");
        }

        virtualMethods.add(method);
    }

    /**
     * Gets all the methods in this class. The returned list is not linked
     * in any way to the underlying lists contained in this instance, but
     * the objects contained in the list are shared.
     *
     * @return {@code non-null;} list of all methods
     */
    public ArrayList<EncodedMethod> getMethods() {
        int sz = directMethods.size() + virtualMethods.size();
        ArrayList<EncodedMethod> result = new ArrayList<EncodedMethod>(sz);

        result.addAll(directMethods);
        result.addAll(virtualMethods);

        return result;
    }


    /**
     * Prints out the contents of this instance, in a debugging-friendly
     * way.
     *
     * @param out {@code non-null;} where to output to
     * @param verbose whether to be verbose with the output
     */
    public void debugPrint(Writer out, boolean verbose) {
        PrintWriter pw = Writers.printWriterFor(out);

        int sz = staticFields.size();
        for (int i = 0; i < sz; i++) {
            pw.println("  sfields[" + i + "]: " + staticFields.get(i));
        }

        sz = instanceFields.size();
        for (int i = 0; i < sz; i++) {
            pw.println("  ifields[" + i + "]: " + instanceFields.get(i));
        }

        sz = directMethods.size();
        for (int i = 0; i < sz; i++) {
            pw.println("  dmeths[" + i + "]:");
            directMethods.get(i).debugPrint(pw, verbose);
        }

        sz = virtualMethods.size();
        for (int i = 0; i < sz; i++) {
            pw.println("  vmeths[" + i + "]:");
            virtualMethods.get(i).debugPrint(pw, verbose);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addContents(DexFile file) {
        if (!staticFields.isEmpty()) {
            getStaticValuesConstant(); // Force the fields to be sorted.
            for (EncodedField field : staticFields) {
                field.addContents(file);
            }
        }

        if (!instanceFields.isEmpty()) {
            Collections.sort(instanceFields);
            for (EncodedField field : instanceFields) {
                field.addContents(file);
            }
        }

        if (!directMethods.isEmpty()) {
            Collections.sort(directMethods);
            for (EncodedMethod method : directMethods) {
                method.addContents(file);
            }
        }

        if (!virtualMethods.isEmpty()) {
            Collections.sort(virtualMethods);
            for (EncodedMethod method : virtualMethods) {
                method.addContents(file);
            }
        }
    }

    /**
     * Gets a {@link CstArray} corresponding to {@link #staticValues} if
     * it contains any non-zero non-{@code null} values.
     *
     * @return {@code null-ok;} the corresponding constant or {@code null} if
     * there are no values to encode
     */
    public CstArray getStaticValuesConstant() {
        if ((staticValuesConstant == null) && (staticFields.size() != 0)) {
            staticValuesConstant = makeStaticValuesConstant();
        }

        return staticValuesConstant;
    }

    /**
     * Gets a {@link CstArray} corresponding to {@link #staticValues} if
     * it contains any non-zero non-{@code null} values.
     *
     * @return {@code null-ok;} the corresponding constant or {@code null} if
     * there are no values to encode
     */
    private CstArray makeStaticValuesConstant() {
        // First sort the statics into their final order.
        Collections.sort(staticFields);

        /*
         * Get the size of staticValues minus any trailing zeros/nulls (both
         * nulls per se as well as instances of CstKnownNull).
         */

        int size = staticFields.size();
        while (size > 0) {
            EncodedField field = staticFields.get(size - 1);
            Constant cst = staticValues.get(field);
            if (cst instanceof CstLiteralBits) {
                // Note: CstKnownNull extends CstLiteralBits.
                if (((CstLiteralBits) cst).getLongBits() != 0) {
                    break;
                }
            } else if (cst != null) {
                break;
            }
            size--;
        }

        if (size == 0) {
            return null;
        }

        // There is something worth encoding, so build up a result.

        CstArray.List list = new CstArray.List(size);
        for (int i = 0; i < size; i++) {
            EncodedField field = staticFields.get(i);
            Constant cst = staticValues.get(field);
            if (cst == null) {
                cst = Zeroes.zeroFor(field.getRef().getType());
            }
            list.set(i, cst);
        }
        list.setImmutable();

        return new CstArray(list);
    }

    /** {@inheritDoc} */
    @Override
    protected void place0(Section addedTo, int offset) {
        // Encode the data and note the size.

        ByteArrayAnnotatedOutput out = new ByteArrayAnnotatedOutput();

        encodeOutput(addedTo.getFile(), out);
        encodedForm = out.toByteArray();
        setWriteSize(encodedForm.length);
    }

    /**
     * Writes out the encoded form of this instance.
     *
     * @param file {@code non-null;} file this instance is part of
     * @param out {@code non-null;} where to write to
     */
    private void encodeOutput(DexFile file, AnnotatedOutput out) {
        boolean annotates = out.annotates();

        if (annotates) {
            out.annotate(0, offsetString() + " class data for " +
                    thisClass.toHuman());
        }

        encodeSize(file, out, "static_fields", staticFields.size());
        encodeSize(file, out, "instance_fields", instanceFields.size());
        encodeSize(file, out, "direct_methods", directMethods.size());
        encodeSize(file, out, "virtual_methods", virtualMethods.size());

        encodeList(file, out, "static_fields", staticFields);
        encodeList(file, out, "instance_fields", instanceFields);
        encodeList(file, out, "direct_methods", directMethods);
        encodeList(file, out, "virtual_methods", virtualMethods);

        if (annotates) {
            out.endAnnotation();
        }
    }

    /**
     * Helper for {@link #encodeOutput}, which writes out the given
     * size value, annotating it as well (if annotations are enabled).
     *
     * @param file {@code non-null;} file this instance is part of
     * @param out {@code non-null;} where to write to
     * @param label {@code non-null;} the label for the purposes of annotation
     * @param size {@code >= 0;} the size to write
     */
    private static void encodeSize(DexFile file, AnnotatedOutput out,
            String label, int size) {
        if (out.annotates()) {
            out.annotate(String.format("  %-21s %08x", label + "_size:",
                            size));
        }

        out.writeUnsignedLeb128(size);
    }

    /**
     * Helper for {@link #encodeOutput}, which writes out the given
     * list. It also annotates the items (if any and if annotations
     * are enabled).
     *
     * @param file {@code non-null;} file this instance is part of
     * @param out {@code non-null;} where to write to
     * @param label {@code non-null;} the label for the purposes of annotation
     * @param list {@code non-null;} the list in question
     */
    private static void encodeList(DexFile file, AnnotatedOutput out,
            String label, ArrayList<? extends EncodedMember> list) {
        int size = list.size();
        int lastIndex = 0;

        if (size == 0) {
            return;
        }

        if (out.annotates()) {
            out.annotate(0, "  " + label + ":");
        }

        for (int i = 0; i < size; i++) {
            lastIndex = list.get(i).encode(file, out, lastIndex, i);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeTo0(DexFile file, AnnotatedOutput out) {
        boolean annotates = out.annotates();

        if (annotates) {
            /*
             * The output is to be annotated, so redo the work previously
             * done by place0(), except this time annotations will actually
             * get emitted.
             */
            encodeOutput(file, out);
        } else {
            out.write(encodedForm);
        }
    }

    public static ClassDataItem parse(DexFile file, CstType thisClass, ByteArray byteArray, int offset) {
        int[] staticFieldsCount = byteArray.getUnsignedLeb128(offset);
        int instanceFieldsCountOffset = offset + staticFieldsCount[1];
        int[] instanceFieldsCount = byteArray.getUnsignedLeb128(instanceFieldsCountOffset);
        int directMethodsCountOffset = instanceFieldsCountOffset + instanceFieldsCount[1];
        int[] directMethodsCount = byteArray.getUnsignedLeb128(directMethodsCountOffset);
        int virtualMethodsCountOffset = directMethodsCountOffset + directMethodsCount[1];
        int[] virtualMethodsCount = byteArray.getUnsignedLeb128(virtualMethodsCountOffset);
        int staticFieldsOffset = virtualMethodsCountOffset + virtualMethodsCount[1];
        ValueWithSize<ArrayList<EncodedField>> staticFields = parseStaticFields(staticFieldsCount[0], byteArray, staticFieldsOffset);
        int instanceFieldsOffset = staticFieldsOffset + staticFields.getSize();
        ValueWithSize<ArrayList<EncodedField>> instanceFields = parseInstanceFields(instanceFieldsCount[0], byteArray, instanceFieldsOffset);
        int directMethodsOffset = instanceFieldsOffset + instanceFields.getSize();
        ValueWithSize<ArrayList<EncodedMethod>> directMethods = parseDirectMethods(file, directMethodsCount[0], byteArray, directMethodsOffset);
        int virtualMethodsOffset = directMethodsOffset + directMethods.getSize();
        ValueWithSize<ArrayList<EncodedMethod>> virtualMethods = parseDirectMethods(file, virtualMethodsCount[0], byteArray, virtualMethodsOffset);
        

        return new ClassDataItem(thisClass, staticFields.getValue(), /* staticValues */null, instanceFields.getValue(), directMethods.getValue(), virtualMethods.getValue(), /* staticValuesConstant*/null);
    }

    private static ValueWithSize<ArrayList<EncodedField>> parseStaticFields(int count, ByteArray byteArray, int staticFieldsOffset) {
        ArrayList<EncodedField> staticFields = new ArrayList<EncodedField>();
        int lastIndex = 0;
        int diffOffset = staticFieldsOffset;
        for (int i = 0; i < count; i++) {
            int[] diff = byteArray.getUnsignedLeb128(diffOffset);
            int accessFlagsOffset = diffOffset + diff[1];
            int[] accessFlags = byteArray.getUnsignedLeb128(accessFlagsOffset);

            int index = lastIndex + diff[0];
            staticFields.add(new EncodedField(new FieldIdItem(byteArray, index).getFieldRef(), accessFlags[0]));

            diffOffset = accessFlagsOffset + accessFlags[1];
            lastIndex = index;
        }
        return new ValueWithSize<ArrayList<EncodedField>>(staticFields, diffOffset - staticFieldsOffset);
    }

    private static ValueWithSize<ArrayList<EncodedField>> parseInstanceFields(int count, ByteArray byteArray, int instanceFieldsOffset) {
        ArrayList<EncodedField> instanceFields = new ArrayList<EncodedField>();
        int lastIndex = 0;
        int diffOffset = instanceFieldsOffset;
        for (int i = 0; i < count; i++) {
            int[] diff = byteArray.getUnsignedLeb128(diffOffset);
            int accessFlagsOffset = diffOffset + diff[1];
            int[] accessFlags = byteArray.getUnsignedLeb128(accessFlagsOffset);

            int index = lastIndex + diff[0];
            instanceFields.add(new EncodedField(new FieldIdItem(byteArray, index).getFieldRef(), accessFlags[0]));

            diffOffset = accessFlagsOffset + accessFlags[1];
            lastIndex = index;
        }
        return new ValueWithSize<ArrayList<EncodedField>>(instanceFields, diffOffset - instanceFieldsOffset);
    }

    private static ValueWithSize<ArrayList<EncodedMethod>> parseDirectMethods(DexFile file, int count, ByteArray byteArray, int directMethodsOffset) {
        ArrayList<EncodedMethod> directMethods = new ArrayList<EncodedMethod>();
        int lastIndex = 0;
        int diffOffset = directMethodsOffset;
        for (int i = 0; i < count; i++) {
            int[] diff = byteArray.getUnsignedLeb128(diffOffset);
            int accessFlagsOffset = diffOffset +  diff[1];
            int[] accessFlags = byteArray.getUnsignedLeb128(accessFlagsOffset);
            int codeOffOffset = accessFlagsOffset + accessFlags[1];
            int[] codeOffset = byteArray.getUnsignedLeb128(codeOffOffset);
            
            int index = lastIndex + diff[0];
            CstMethodRef methodRef = (CstMethodRef)MethodIdItem.parse(file, byteArray, index).getMethodRef();
            
            DalvCode code = codeOffset[0] == 0 ? null : CodeItem.parse(file, methodRef, byteArray, codeOffset[0]).getDalvCode();
            directMethods.add(new EncodedMethod(methodRef, accessFlags[0], code, StdTypeList.EMPTY));

            diffOffset = codeOffOffset + codeOffset[1];
            lastIndex = index;
        }
        return new ValueWithSize<ArrayList<EncodedMethod>>(directMethods, diffOffset - directMethodsOffset);
    }

}
