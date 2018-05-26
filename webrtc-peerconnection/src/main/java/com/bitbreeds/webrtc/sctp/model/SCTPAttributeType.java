package com.bitbreeds.webrtc.sctp.model;

import com.bitbreeds.webrtc.common.SignalUtil;

import java.util.Arrays;

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

/**
 * Representation of variable length paramaters in SCTP messages
 *
 * {@link <a href=http://www.iana.org/assignments/sctp-parameters/sctp-parameters.xhtml>SCTP messages and params</a>}
 */
public enum SCTPAttributeType {

    NOT_KNOWN(-1),
    HERTBEAT_INFO(1),
    IPV4(5),
    IPV6(6),
    STATE_COOKIE(7),
    COOKIE_PRESERVATIVE(9),
    HOST_NAME(11),
    ADDRESS_TYPES(12),
    OUTGOING_SSN_RESET_REQUEST(13),
    INCOMING_SSN_RESET_REQUEST(14),
    SSN_TSN_RESET_REQEUST(15),
    RE_CONFIG(16),
    ADD_OUTGOING_STREAMS(17),
    ADD_INCOMING_STREAMS(18),
    FORWARD_TSN(0xC000),
    RANDOM(0x8002),
    CHUNK_LIST(0x8003),
    HMAC_ALGORITHM(0x8004),
    PADDING(0x8005),
    SUPPORTED_EXTENSIONS(0x8008),
    ADD_IP(0xC001),
    DELETE_IP(0xC002),
    ERROR_CAUSE(0xC003),
    SET_PRIMARY_ADDRESS(0xC004),
    SUCCESS_INDICATION(0xC005),
    ADAPTATION_LAYER_INDICATION(0xC006);

    private final int nr;

    SCTPAttributeType(int nr) {
        this.nr = nr;
    }

    public int getNr() {
        return nr;
    }

    public byte[] toBytes() {
        return SignalUtil.twoBytesFromInt(nr);
    }

    public static SCTPAttributeType fromInt(int bt) {
        return Arrays.asList(values())
                .stream()
                .filter(i -> i.nr == bt)
                .findFirst().orElse(
                     NOT_KNOWN
                );
    }

}
