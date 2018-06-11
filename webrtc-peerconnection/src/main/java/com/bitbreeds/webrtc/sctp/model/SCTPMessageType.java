package com.bitbreeds.webrtc.sctp.model;

import com.bitbreeds.webrtc.common.SignalUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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


/**
 * Representation of SCTP message types
 *
 * {@link <a href=http://www.iana.org/assignments/sctp-parameters/sctp-parameters.xhtml>SCTP messages and params</a>}
 */
public enum SCTPMessageType {

    NOT_KNOWN(-1,new ArrayList<>(),false),

    DATA(0,Arrays.asList(
            SCTPFixedAttributeType.TSN,
            SCTPFixedAttributeType.STREAM_IDENTIFIER_S,
            SCTPFixedAttributeType.STREAM_SEQUENCE_NUMBER,
            SCTPFixedAttributeType.PROTOCOL_IDENTIFIER),
            true
    ),

    INITIATION(1,Arrays.asList(
            SCTPFixedAttributeType.INITIATE_TAG,
            SCTPFixedAttributeType.ARWC,
            SCTPFixedAttributeType.OUTBOUND_STREAMS,
            SCTPFixedAttributeType.INBOUND_STREAMS,
            SCTPFixedAttributeType.INITIAL_TSN),
            false
    ),

    INITIATION_ACK(2,Arrays.asList(
            SCTPFixedAttributeType.INITIATE_TAG,
            SCTPFixedAttributeType.ARWC,
            SCTPFixedAttributeType.OUTBOUND_STREAMS,
            SCTPFixedAttributeType.INBOUND_STREAMS,
            SCTPFixedAttributeType.INITIAL_TSN),
            false
    ),

    SELECTIVE_ACK(3,Arrays.asList(
            SCTPFixedAttributeType.CUMULATIVE_TSN_ACK,
            SCTPFixedAttributeType.ARWC,
            SCTPFixedAttributeType.NUM_GAP_BLOCKS,
            SCTPFixedAttributeType.NUM_DUPLICATE
            )
            ,true),

    HEARTBEAT(4,new ArrayList<>(),false),
    HEARTBEAT_ACK(5,new ArrayList<>(),false),
    ABORT(6,new ArrayList<>(),true),
    SHUTDOWN(7,new ArrayList<>(),true),
    SHUTDOWN_ACK(8,new ArrayList<>(),true),
    ERROR(9,new ArrayList<>(),true),
    COOKIE_ECHO(10,new ArrayList<>(),true),
    COOKIE_ACK(11,new ArrayList<>(),true),
    CWR(13,new ArrayList<>(),true),
    SHUTDOWN_COMPLETE(14,new ArrayList<>(),true),

    FORWARD_TSN(192,Arrays.asList(
            SCTPFixedAttributeType.CUMULATIVE_TSN_ACK)
            ,true);

    /**
     * Message identifier
     */
    private final int nr;

    /**
     * Whether the message has variable length fields.
     * If not the rest of the data in the message is some
     * some kind of data.
     */
    private final boolean noVarTypes;

    public List<SCTPFixedAttributeType> getFixedTypes() {
        return fixedTypes;
    }

    /**
     * The fixed types that should be present in the message
     */
    private final List<SCTPFixedAttributeType> fixedTypes;

    SCTPMessageType(
            int nr,
            List<SCTPFixedAttributeType> fixedTypes,
            boolean noVarTypes) {
        this.nr = nr;
        this.fixedTypes = fixedTypes;
        this.noVarTypes = noVarTypes;
    }

    public int getNr() {
        return nr;
    }

    public boolean isNoVarTypes() {
        return noVarTypes;
    }

    public byte[] toBytes() {
        return new byte[] {SignalUtil.sign(nr)};
    }

    public static SCTPMessageType fromByte(byte bt) {
        return Arrays.asList(values())
                .stream()
                .filter(i-> i.getNr() == bt)
                .findFirst().orElse(NOT_KNOWN);
    }

}
