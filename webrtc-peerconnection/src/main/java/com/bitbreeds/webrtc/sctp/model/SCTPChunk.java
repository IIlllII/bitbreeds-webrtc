package com.bitbreeds.webrtc.sctp.model;

import com.bitbreeds.webrtc.common.ByteRange;
import com.bitbreeds.webrtc.common.SignalUtil;
import org.apache.commons.codec.binary.Hex;

import java.util.*;

import static com.bitbreeds.webrtc.common.SignalUtil.*;

/**
 * Copyright (c) 17/05/16, Jonas Waage
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
public class SCTPChunk {

    private final SCTPMessageType type ;
    private final SCTPOrderFlag flags;
    private final int length;

    /**
     * Fixed variables
     */
    private final Map<SCTPFixedAttributeType,SCTPFixedAttribute> fixed;

    /**
     *
     */
    private final Map<SCTPAttributeType,SCTPAttribute> variable;


    /**
     *
     */
    private final byte[] rest;


    /**
     *  @param type SCTPMessageType
     * @param flags usually 0
     * @param length length of the chunk
     * @param fixed attributed of fixed length and position
     * @param variable parameters or variable length
     * @param rest
     */
    public SCTPChunk(
            SCTPMessageType type,
            SCTPOrderFlag flags,
            int length,
            Map<SCTPFixedAttributeType, SCTPFixedAttribute> fixed,
            Map<SCTPAttributeType, SCTPAttribute> variable,
            byte[] rest) {
        this.type = type;
        this.flags = flags;
        this.length = length;
        this.fixed = fixed;
        this.variable = variable;
        this.rest = rest;
    }


    /**
     *
     * @return byterepresentation of chunk padded with 0s to length multiple of four.
     */
    public byte[] toBytes() {

        List<byte[]> fixedBytes = new ArrayList<>();
        for(SCTPFixedAttributeType t: type.getFixedTypes()) {
            fixedBytes.add( fixed.get(t).getData() );
        }

        final List<byte[]> nonFixed = new ArrayList<>();
        variable.values().stream().forEach(i -> {
            nonFixed.add(i.toBytes());
        });

        if(type.isNoVarTypes() && nonFixed.size() > 0){
            throw new IllegalStateException("No varible size fields allowed for chunk " + type);
        }

        return SignalUtil.padToMultipleOfFour(
                SignalUtil.joinBytesArrays(
                        type.toBytes(),
                        new byte[] {flags.getByteRep()},
                        twoBytesFromInt(length),
                        SignalUtil.joinBytesArrays(fixedBytes),
                        SignalUtil.joinBytesArrays(nonFixed),
                        rest
                )
        );
    }


    /**
     *
     * @param bytes
     * @return
     */
    public static SCTPChunk fromBytes(byte[] bytes) {
        if(bytes.length < 4) {
            throw new IllegalArgumentException("Bytes given are to short to be an SCTP chunk: "
                    + " length: " + bytes.length + "  data:" + Hex.encodeHexString(bytes));
        }

        SCTPMessageType type = SCTPMessageType.fromByte(SignalUtil.unsign(bytes[0]));

        int flags = SignalUtil.unsign(bytes[1]);
        int length = SignalUtil.intFromTwoBytes(Arrays.copyOfRange(bytes,2,4));

        Map<SCTPFixedAttributeType,SCTPFixedAttribute> fixedAttr = new HashMap<>();

        ByteRange range = SignalUtil.range(4,4);
        for(SCTPFixedAttributeType t:type.getFixedTypes()) {
            range = range.lengthFromA(t.getLgt());
            byte[] data = copyRange(bytes,range);
            fixedAttr.put(t, new SCTPFixedAttribute(t,data) );
            range = range.plus(t.getLgt());
        }

        Map<SCTPAttributeType,SCTPAttribute> varAttr = new HashMap<>();

        byte[] rest = new byte[] {};
        if(type.isNoVarTypes()) {
            range = range.lengthFromA(length-range.getA());
            rest = SignalUtil.copyRange(bytes,range);
        }
        else {
            while (bytes.length > range.getB()) {
                range = range.lengthFromA(2);
                SCTPAttributeType tp = SCTPAttributeType.fromInt(
                        intFromTwoBytes(copyRange(bytes, range)));
                range = range.plus(2);

                int lgt = intFromTwoBytes(copyRange(bytes, range));
                lgt = Math.max(lgt - 4, 0); //Lgt data includes type and lenght fields, subtract lgt and type to get data portion

                range = range.plus(2).lengthFromA(lgt);

                byte[] data = copyRange(bytes, range);
                range = range.plus(multipleOfFour(lgt));
                varAttr.put(tp, new SCTPAttribute(tp, data));
            }
        }

        return new SCTPChunk(
                type,
                SCTPOrderFlag.fromValue(flags),
                length,
                fixedAttr,
                varAttr,
                rest);
    }

    public byte[] getRest() {
        return rest;
    }

    public SCTPMessageType getType() {
        return type;
    }

    public SCTPOrderFlag getFlags() {
        return flags;
    }

    public int getLength() {
        return length;
    }

    public Map<SCTPFixedAttributeType, SCTPFixedAttribute> getFixed() {
        return fixed;
    }

    public Map<SCTPAttributeType, SCTPAttribute> getVariable() {
        return variable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SCTPChunk sctpChunk = (SCTPChunk) o;
        return length == sctpChunk.length &&
                type == sctpChunk.type &&
                flags == sctpChunk.flags &&
                Objects.equals(fixed, sctpChunk.fixed) &&
                Objects.equals(variable, sctpChunk.variable) &&
                Arrays.equals(rest, sctpChunk.rest);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(type, flags, length, fixed, variable);
        result = 31 * result + Arrays.hashCode(rest);
        return result;
    }

    @Override
    public String toString() {
        return "SCTPChunk{" +
                "type=" + type +
                ", flags=" + flags +
                ", length=" + length +
                ", fixed=" + fixed +
                ", variable=" + variable +
                ", rest=" + Hex.encodeHexString(rest) +
                '}';
    }
}
