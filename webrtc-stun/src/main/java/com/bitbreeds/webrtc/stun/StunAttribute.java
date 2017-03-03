package com.bitbreeds.webrtc.stun;

import com.bitbreeds.webrtc.common.SignalUtil;
import org.apache.commons.codec.binary.Hex;

import java.util.Arrays;

/**
 * Copyright (c) 11/05/16, Jonas Waage
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
public class StunAttribute {

    private final StunAttributeTypeEnum type;

    private final int length;

    private final byte[] data;

    /**
     *
     * @param type {@link StunAttributeTypeEnum}
     * @param data data of attribute
     */
    public StunAttribute(StunAttributeTypeEnum type,byte[] data) {
        length = data.length;
        this.type = type;
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StunAttribute that = (StunAttribute) o;

        return type == that.type;
    }

    /**
     *
     * @return representing this value on the wire
     */
    public byte[] toBytes() {
        int outLgt = SignalUtil.multipleOfFour(this.length);
        return SignalUtil.joinBytesArrays(
                type.toBytes(),
                SignalUtil.twoBytesFromInt(length),
                Arrays.copyOf(data,outLgt));
    }

    @Override
    public int hashCode() {
        return type != null ? type.hashCode() : 0;
    }

    public StunAttributeTypeEnum getType() {
        return type;
    }

    public int getLength() {
        return length;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return "StunAttribute{" +
                "type=" + type +
                ", length=" + length +
                ", data=" + Hex.encodeHexString(data) +
                '}';
    }
}
