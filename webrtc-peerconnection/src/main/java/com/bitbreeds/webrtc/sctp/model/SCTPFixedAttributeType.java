package com.bitbreeds.webrtc.sctp.model;

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
 * Attributes of fixed length
 */
public enum SCTPFixedAttributeType {

    INITIATE_TAG(4),
    ARWC(4),
    OUTBOUND_STREAMS(2),
    INBOUND_STREAMS(2),
    INITIAL_TSN(4),
    STREAM_IDENTIFIER_S(2),
    STREAM_SEQUENCE_NUMBER(2),
    PROTOCOL_IDENTIFIER(4),
    TSN(4),
    CUMULATIVE_TSN_ACK(4),
    NUM_GAP_BLOCKS(2),
    NUM_DUPLICATE(2);

    /**
     * Length in bytes
     */
    private final int lgt;

    SCTPFixedAttributeType(int lgt) {
        this.lgt = lgt;
    }

    public int getLgt() {
        return lgt;
    }

}
