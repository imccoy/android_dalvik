package com.android.dx.dex.file;

import com.android.dx.rop.annotation.Annotation;
import com.android.dx.rop.annotation.AnnotationVisibility;
import com.android.dx.rop.annotation.NameValuePair;
import com.android.dx.rop.cst.Constant;
import com.android.dx.rop.cst.CstArray;
import com.android.dx.rop.cst.CstInteger;
import com.android.dx.rop.cst.CstKnownNull;
import com.android.dx.rop.cst.CstMethodRef;
import com.android.dx.rop.cst.CstString;
import com.android.dx.rop.cst.CstType;
import com.android.dx.rop.cst.CstUtf8;
import com.android.dx.util.ByteArray;
import com.android.dx.util.Hex;

public final class ValueDecoder {
    /** annotation value type constant: {@code byte} */
    private static final int VALUE_BYTE = 0x00;

    /** annotation value type constant: {@code short} */
    private static final int VALUE_SHORT = 0x02;

    /** annotation value type constant: {@code char} */
    private static final int VALUE_CHAR = 0x03;

    /** annotation value type constant: {@code int} */
    private static final int VALUE_INT = 0x04;

    /** annotation value type constant: {@code long} */
    private static final int VALUE_LONG = 0x06;

    /** annotation value type constant: {@code float} */
    private static final int VALUE_FLOAT = 0x10;

    /** annotation value type constant: {@code double} */
    private static final int VALUE_DOUBLE = 0x11;

    /** annotation value type constant: {@code string} */
    private static final int VALUE_STRING = 0x17;

    /** annotation value type constant: {@code type} */
    private static final int VALUE_TYPE = 0x18;

    /** annotation value type constant: {@code field} */
    private static final int VALUE_FIELD = 0x19;

    /** annotation value type constant: {@code method} */
    private static final int VALUE_METHOD = 0x1a;

    /** annotation value type constant: {@code enum} */
    private static final int VALUE_ENUM = 0x1b;

    /** annotation value type constant: {@code array} */
    private static final int VALUE_ARRAY = 0x1c;

    /** annotation value type constant: {@code annotation} */
    private static final int VALUE_ANNOTATION = 0x1d;

    /** annotation value type constant: {@code null} */
    private static final int VALUE_NULL = 0x1e;

    /** annotation value type constant: {@code boolean} */
    private static final int VALUE_BOOLEAN = 0x1f;

    ByteArray byteArray;
    int offset;

    public ValueDecoder(ByteArray byteArray, int offset) {
        this.byteArray = byteArray;
        this.offset = offset;
    }

    public Annotation readAnnotation(AnnotationVisibility visibility) {
        int annotationTypeOffset = readUnsignedLeb128();
        CstType annotationType = new TypeIdItem(byteArray, annotationTypeOffset).getDefiningClass();

        int pairsSize = readUnsignedLeb128();
        Annotation annotation = new Annotation(annotationType, visibility);

        for (int i = 0; i < pairsSize; i++) {
            int nameIdx = readUnsignedLeb128();
            CstUtf8 name = new StringIdItem(byteArray, nameIdx).getValue();
            Constant value = readConstant();
            annotation.add(new NameValuePair(name, value));
        }
        return annotation;
    }

    private Constant readConstant() {
        int type = readByte();
        switch (type & 0x1F) {
        case VALUE_INT:
            long bits = readSignedIntegralValue(type);
            return CstInteger.make((int)bits);
        case VALUE_STRING:
            return readString(type);
        case VALUE_TYPE: 
            return readType(type);
        case VALUE_METHOD:
            return readMethod(type);
        case VALUE_ARRAY:
            return readArray();
        case VALUE_NULL:
            return CstKnownNull.THE_ONE;
        default:
            throw new RuntimeException("Don't know " + type + "(" + Hex.u4(type) + ")");
        }
    }

    private CstType readType(int typeByte) {
        int index = (int)readUnsignedIntegralValue(typeByte);
        return new TypeIdItem(byteArray, index).getDefiningClass();
    }

    private CstString readString(int typeByte) {
        System.out.println("Reading string number from offset " + Hex.u4(offset));
        int index = (int)readUnsignedIntegralValue(typeByte);
        System.out.println("String number " + index);
        return new CstString(new StringIdItem(byteArray, index).getValue());
    }

    private CstMethodRef readMethod(int typeByte) {
        int index = (int)readUnsignedIntegralValue(typeByte);
        return (CstMethodRef)new MethodIdItem(byteArray, index).getMethodRef();
    }

    private CstArray readArray() {
        int size = readUnsignedLeb128();
        CstArray.List list = new CstArray.List(size);
        for (int i = 0; i < size; i++) {
            list.set(i, readConstant());
        }
        list.setImmutable();
        return new CstArray(list);
    }

    private long readUnsignedIntegralValue(int typeByte) {
        int requiredBytes = (typeByte >> 5) + 1;
        long value = 0;
        for (int i = 0; i < requiredBytes; i++) {
            System.out.println(Hex.u8(value));
            int b = readByte();
            value = value | ((b & 0xFF) << (i * 8));
            requiredBytes--;
        }
        return value;
    }

    private long readSignedIntegralValue(int typeByte) {
        throw new RuntimeException("This is wrong; need to pack bytes in other order, and sign-extend");
        /*
        int requiredBytes = (typeByte >> 5) + 1;
        long value = 0;
        while (requiredBytes > 0) {
            value = (value << 8) | readByte();
            requiredBytes--;
        }
        return value;
        */
    } 

    private int readUnsignedLeb128() {
        int[] valAndLength = byteArray.getUnsignedLeb128(offset);
        offset += valAndLength[1];
        return valAndLength[0];
    }

    private int readInt() {
        int val = byteArray.getInt2(offset);
        offset += 4;
        return val;
    }

    private int getByte() {
        return byteArray.getByte(offset);
    }

    private int readByte() {
        int val = getByte();
        offset += 1;
        return val;
    }

}
