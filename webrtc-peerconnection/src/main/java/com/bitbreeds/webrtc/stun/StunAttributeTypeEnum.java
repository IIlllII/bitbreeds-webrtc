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
public enum StunAttributeTypeEnum {

    NOT_KNOWN(0x0000),
    MAPPED_ADDRESS(0x0001),
    RESPONSE_ADDRESS(0x0002),
    CHANGE_REQUEST(0x0003),
    SOURCE_ADDRESS(0x0004),
    CHANGED_ADDRESS(0x0005),
    USERNAME(0x0006),
    PASSWORD(0x0007),
    MESSAGE_INTEGRITY(0x0008),
    XOR_MAPPED_ADDRESS(0x0020),
    FINGERPRINT(0x8028),
    ERROR_CODE(0x0009),
    REFLECTED_FROM(0x000b),
    REALM(0x0014),
    NONCE(0x0015),
    UNKNOWN_ATTRIBUTES(0x000a),
    SOFTWARE(0x8022),
    ALTERNATE_SERVER(0x8023),
    ORIGIN(0x802F),
    USE_CANDIDATE(0x0025),
    PRIORITY(0x0024),
    ICE_CONTROLLED(0x8029),
    ICE_CONTROLLING(0x802A),
    RESPONSE_ORIGIN(0x802B);

    public int getNr() {
        return nr;
    }

    private int nr;

    StunAttributeTypeEnum(int nr) {
        this.nr = nr;
    }


    /**
     * Converts two bytes to enum.
     * @param bytes two bytes that represent enum
     * @return Enum from the value of the given bytes
     */
    public static StunAttributeTypeEnum fromBytes(byte[] bytes) {
        int nr = SignalUtil.intFromTwoBytes(bytes);
        return fromInt(nr);
    }


    /**
     *
     * @param nr int representing Enum
     * @return int to enum
     */
    public static StunAttributeTypeEnum fromInt(int nr) {
        return Arrays.asList(StunAttributeTypeEnum.values()).stream().filter(i->i.nr==nr)
                .findFirst().orElse(NOT_KNOWN);
    }


    /**
     * @return bytes for the wire
     */
    public byte[] toBytes() {
        return SignalUtil.twoBytesFromInt(this.nr);
    }

}
