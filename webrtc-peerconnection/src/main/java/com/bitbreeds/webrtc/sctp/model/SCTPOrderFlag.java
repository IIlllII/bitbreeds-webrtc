package com.bitbreeds.webrtc.sctp.model;

/**
 * Copyright (c) 16/06/16, Jonas Waage
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


import com.bitbreeds.webrtc.common.SignalUtil;

import java.util.Arrays;

/**
 * Reserved: 5 bits

 Should be set to all '0's and ignored by the receiver.

 U bit: 1 bit

 The (U)nordered bit, if set to '1', indicates that this is an
 unordered DATA chunk, and there is no Stream Sequence Number
 assigned to this DATA chunk.  Therefore, the receiver MUST ignore
 the Stream Sequence Number field.

 After reassembly (if necessary), unordered DATA chunks MUST be
 dispatched to the upper layer by the receiver without any attempt
 to reorder.

 If an unordered user message is fragmented, each fragment of the
 message MUST have its U bit set to '1'.

 B bit: 1 bit

 The (B)eginning fragment bit, if set, indicates the first fragment
 of a user message.

 E bit: 1 bit

 The (E)nding fragment bit, if set, indicates the last fragment of
 a user message.


 An unfragmented user message shall have both the B and E bits set to
 '1'.  Setting both B and E bits to '0' indicates a middle fragment of
 a multi-fragment user message, as summarized in the following table:
 */

public enum SCTPOrderFlag {

    UNORDERED_UNFRAGMENTED(4+2+1),
    UNORDERED_START_FRAGMENT(4+2),
    UNORDERED_END_FRAGMENT(4+1),
    ORDERED_UNFRAGMENTED(2+1),
    ORDERED_START_FRAGMENT(2),
    ORDERED_END_FRAGMENT(1),
    UNORDERED_MIDDLE_FRAGMENT(4),
    ORDERED_MIDDLE_FRAGMENT(0);

    private int byteRep;

    SCTPOrderFlag(int byteRep) {
        this.byteRep = byteRep;
    }

    /**
     *
     * @param b byte to convert
     * @return flagenum from bytes;
     */
    public static SCTPOrderFlag fromValue(int b) {
        return Arrays.asList(values()).stream()
                .filter(i->i.byteRep == b)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No flag combination has value: "+ b));
    }

    public boolean isOrdered() {
        return (byteRep & (byte)4) == 0;
    }

    public boolean isUnordered() {
        return !isOrdered();
    }

    public boolean isStart() {
        return this.equals(UNORDERED_START_FRAGMENT) || this.equals(ORDERED_START_FRAGMENT);
    }

    public boolean isMiddle() {
        return this.equals(UNORDERED_MIDDLE_FRAGMENT) || this.equals(ORDERED_MIDDLE_FRAGMENT);
    }

    public boolean isEnd() {
        return this.equals(UNORDERED_END_FRAGMENT) || this.equals(ORDERED_END_FRAGMENT);
    }

    public boolean isFragmented() {
        return !this.equals(UNORDERED_UNFRAGMENTED) && !this.equals(ORDERED_UNFRAGMENTED);
    }

    public boolean isUnFragmented() {
        return this.equals(UNORDERED_UNFRAGMENTED) || this.equals(ORDERED_UNFRAGMENTED);
    }

    /**
     * @return Byte representation of enum
     */
    public byte getByteRep() {
        return SignalUtil.sign(byteRep);
    }

}
