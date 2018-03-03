package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.ByteRange;
import com.bitbreeds.webrtc.common.GapAck;
import com.bitbreeds.webrtc.common.SackUtil;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Copyright (c) 12/06/16, Jonas Waage
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
 * Parses the sack to a representation that is then passed back to the
 * SCTPhandler for processing.
 *
 * @see <a href=https://tools.ietf.org/html/rfc4960#section-3.3.4>SACK spec</a>
 */
public class SelectiveAckHandler implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(SelectiveAckHandler.class);

    @Override
    public Optional<SCTPMessage> handleMessage(
            SCTPImpl handler,
            SCTPContext ctx,
            SCTPHeader header,
            SCTPChunk data) {

        logger.debug("Received SACK message");

        SCTPFixedAttribute cum_tsn = data.getFixed().get(SCTPFixedAttributeType.CUMULATIVE_TSN_ACK);
        SCTPFixedAttribute arcw = data.getFixed().get(SCTPFixedAttributeType.ARWC);
        SCTPFixedAttribute num_gap = data.getFixed().get(SCTPFixedAttributeType.NUM_GAP_BLOCKS);
        SCTPFixedAttribute num_dupl = data.getFixed().get(SCTPFixedAttributeType.NUM_DUPLICATE);

        int remoteBuffer = SignalUtil.intFromFourBytes(arcw.getData());

        long below_this_all_good = SignalUtil.bytesToLong(cum_tsn.getData());
        int gaps = SignalUtil.intFromTwoBytes(num_gap.getData());
        int dupl = SignalUtil.intFromTwoBytes(num_dupl.getData());

        List<GapAck> gapAcks = new ArrayList<>(gaps);
        ByteRange rng = new ByteRange(0,2);
        for(int i = 0; i<gaps; i++) {
            int a = SignalUtil.intFromTwoBytes(SignalUtil.copyRange(data.getRest(),rng));
            rng = rng.plus(2);
            int b = SignalUtil.intFromTwoBytes(SignalUtil.copyRange(data.getRest(),rng));
            rng = rng.plus(2);
            gapAcks.add(new GapAck(a,b));
        }

        List<Long> duplicates = new ArrayList<>(dupl);
        rng = rng.lengthFromA(4);
        for(int i = 0; i<dupl; i++) {
            long dpl = SignalUtil.bytesToLong(SignalUtil.copyRange(data.getRest(),rng));
            rng = rng.plus(4);
            duplicates.add(dpl);
        }

        /*
         * Get gaps acks and duplicates, and send to handler for processing
         */
        handler.getSender().updateAcknowledgedTSNS(
                below_this_all_good,
                gapAcks,
                duplicates,
                remoteBuffer);

        return Optional.empty();
    }

}
