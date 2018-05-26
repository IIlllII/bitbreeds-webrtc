package com.bitbreeds.webrtc.stun;

import com.bitbreeds.webrtc.common.SignalUtil;

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


/**
 * @see <a href=http://www.iana.org/assignments/stun-parameters/stun-parameters.xhtml>stun-parameters</a>
 */
public enum StunRequestTypeEnum {

    NOT_KNOWN(0x0000),

    BINDING_REQUEST(0x0001),

    BINDING_RESPONSE(0x0101),

    BINDING_ERROR_RESPONSE(0x0111),

    SHARED_SECRET_REQUEST(0x0002),

    SHARED_SECRET_RESPONSE(0x0102),

    SHARED_SECRET_ERROR_RESPONSE(0x0112),

    SHARED_SECRET_VERIFY_REQUEST(0x8102);

    public int getNr() {
        return nr;
    }

    private final int nr;

    StunRequestTypeEnum(int nr) {
        this.nr = nr;
    }

    /**
     * Converts two bytes to enum.
     * @param bytes two bytes that represent enum
     * @return Enum from the value of the given bytes
     */
    public static StunRequestTypeEnum fromBytes(byte[] bytes) {
        int nr = SignalUtil.intFromTwoBytes(bytes);
        return fromInt(nr);
    }


    /**
     *
     * @param nr int representing Enum
     * @return int to enum
     */
    public static StunRequestTypeEnum fromInt(int nr) {
        return Arrays.asList(StunRequestTypeEnum.values()).stream().filter(i->i.nr==nr)
                .findFirst().orElse(NOT_KNOWN);
    }


    /**
     * @return bytes for the wire
     */
    public byte[] toBytes() {
        return SignalUtil.twoBytesFromInt(this.nr);
    }
};

