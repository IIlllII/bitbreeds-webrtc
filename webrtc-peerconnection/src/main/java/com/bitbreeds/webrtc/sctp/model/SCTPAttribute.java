package com.bitbreeds.webrtc.sctp.model;

import com.bitbreeds.webrtc.common.SignalUtil;
import org.apache.commons.codec.binary.Hex;

/**
 * Copyright (c) 18/05/16, Jonas Waage
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
public class SCTPAttribute {

    private final SCTPAttributeType type;
    private final byte[] data;

    public SCTPAttribute(SCTPAttributeType type, byte[] data) {
        this.type = type;
        this.data = data;
    }

    public int getLength() {
        return 4+data.length;
    }

    public SCTPAttributeType getType() {
        return type;
    }

    public byte[] getData() {
        return data;
    }

    public byte[] toBytes() {
        return SignalUtil.padToMultipleOfFour(SignalUtil.joinBytesArrays(
                type.toBytes(),
                SignalUtil.twoBytesFromInt(getLength()),
                data
                ));
    }

    @Override
    public String toString() {
        return "SCTPAttribute{" +
                "type=" + type +
                ", data=" + Hex.encodeHexString(data) +
                '}';
    }
}
