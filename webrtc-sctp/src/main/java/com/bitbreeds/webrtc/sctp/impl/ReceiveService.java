package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.GapAck;
import com.bitbreeds.webrtc.common.SackUtil;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.impl.buffer.Deliverable;
import com.bitbreeds.webrtc.sctp.impl.buffer.ReceiveBuffer;
import com.bitbreeds.webrtc.sctp.impl.buffer.SackData;
import com.bitbreeds.webrtc.sctp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.bitbreeds.webrtc.common.SignalUtil.*;

/*
 * Copyright (c) 19/05/16, Jonas Waage
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
 *
 * This class handles the state for received payloads and TSNs and creating a SACK from those.
 * This should implement the below, though currently it does probably not do so well.
 *
 * @see <a hred="https://tools.ietf.org/html/rfc4960#section-3.3.4">SCTP SACK spec</a>
 * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-protocol-09#section-8.2.1">datachannel spec</a>
 *
 */
public class ReceiveService {

    private final ReceiveBuffer buffer;

    private static final Logger logger = LoggerFactory.getLogger(ReceiveService.class);

    private final SCTPImpl handler;

    private final Object sackLock = new Object();

    /**
     * Values for handling when to create a SACK.
     * Delayed or immediate.
     */
    private boolean shouldSack = false;
    private int dataCount = 0;

    /**
     * TSN which describes the next expected TSN.
     * Not needed for non ordered communication
     */
    ReceiveService(SCTPImpl handler,int bufferSize) {
        this.handler = handler;
        this.buffer = new ReceiveBuffer(1000,bufferSize);
    }




    /**
     * Receive initial TSN
     */
    public void handleReceiveInitialTSN(long tsn) {
        buffer.setInitialTSN(tsn);
    }


    /**
     * @param data data to store and evaluate
     * @return whether to sack or not
     */
    public boolean handleReceive(ReceivedData data) {
        Objects.requireNonNull(data);
        buffer.store(data);
        List<Deliverable> deliverables = buffer.getMessagesForDelivery();
        deliverables.forEach(
                i -> handler.getDataChannel().runOnMessageUnordered(i.getData())
        );

        boolean performImmediateSack = false;
        synchronized (sackLock) {
            dataCount++;
            shouldSack = true;


            //SACK IMMEDIATE IF MORE THEN X OUTSTANDING
            /*if (dataCount > 1) {
                performImmediateSack = true;
                shouldSack = false;
                dataCount = 0;
            }*/
        }
        return true;
    }

    public long getDeliveredBytes() {
        return buffer.getDeliveredBytes();
    }
    public long getReceivedBytes() {
        return buffer.getReceivedBytes();
    }
    public long getCumulativeTSN() {
        return buffer.getCumulativeTSN();
    }
    public long getBufferCapacity() {
        return buffer.getCapacity();
    }



    /**
     * @return attempt to create a SCTP SACK message.
     */
    public Optional<SCTPMessage> createSack(SCTPHeader header ) {

        synchronized (sackLock) {
            if (!shouldSack) {
                return Optional.empty();
            }
            shouldSack = false;
            dataCount = 0;
        }

        SackData sackData = buffer.getSackDataToSend();//Pull sack data

        //Calculate gap acks from only relevant data.
        List<GapAck> acks = sackData.getTsns();

        List<byte[]> varData = new ArrayList<>();

        for(int i = 1; i<acks.size(); i++) {
            GapAck ack = acks.get(i);
            int start = (int)(ack.start - sackData.getCumulativeTSN());
            int end = (int)(ack.end - sackData.getCumulativeTSN());
            varData.add(twoBytesFromInt(start));
            varData.add(twoBytesFromInt(end));
        }

        for (Long l : sackData.getDuplicates()) {
            varData.add(longToFourBytes(l));
        }

        HashMap<SCTPFixedAttributeType,SCTPFixedAttribute> fixed = new HashMap<>();
        SCTPFixedAttribute cum_tsn =
                new SCTPFixedAttribute(SCTPFixedAttributeType.CUMULATIVE_TSN_ACK,
                        longToFourBytes(sackData.getCumulativeTSN()));

        SCTPFixedAttribute arcw =
                new SCTPFixedAttribute(SCTPFixedAttributeType.ARWC,
                        fourBytesFromInt(sackData.getBufferLeft()));

        SCTPFixedAttribute num_gap =
                new SCTPFixedAttribute(SCTPFixedAttributeType.NUM_GAP_BLOCKS,
                        twoBytesFromInt(varData.size()));

        SCTPFixedAttribute num_dupl =
                new SCTPFixedAttribute(SCTPFixedAttributeType.NUM_DUPLICATE,
                        twoBytesFromInt(sackData.getDuplicates().size()));


        fixed.put(SCTPFixedAttributeType.CUMULATIVE_TSN_ACK,
                cum_tsn);
        fixed.put(SCTPFixedAttributeType.ARWC,arcw);
        fixed.put(SCTPFixedAttributeType.NUM_GAP_BLOCKS,num_gap);
        fixed.put(SCTPFixedAttributeType.NUM_DUPLICATE,num_dupl);

        int sum = fixed.keySet().stream()
                .map(SCTPFixedAttributeType::getLgt).reduce(0, Integer::sum);

        byte[] data = joinBytesArrays(varData);

        SCTPChunk sack = new SCTPChunk(
                SCTPMessageType.SELECTIVE_ACK,
                SCTPOrderFlag.fromValue((byte)0),
                4 + sum + data.length,
                fixed,
                new HashMap<>(),
                SignalUtil.padToMultipleOfFour(data)
        );

        SCTPMessage msg = new SCTPMessage(header, Collections.singletonList(sack));

        logger.debug("Sending sack data: " + msg);
        return Optional.of(SCTPUtil.addChecksum(msg));
    }


}
