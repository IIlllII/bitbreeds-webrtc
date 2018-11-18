package com.bitbreeds.webrtc.peerconnection;

/*
 *
 * Copyright (c) 18/11/2018, Jonas Waage
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

import com.bitbreeds.webrtc.common.ByteRange;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import com.bitbreeds.webrtc.model.sctp.GapAck;
import com.bitbreeds.webrtc.sctp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.bitbreeds.webrtc.sctp.model.SCTPFixedAttributeType.STREAM_IDENTIFIER_S;
import static com.bitbreeds.webrtc.sctp.model.SCTPFixedAttributeType.STREAM_SEQUENCE_NUMBER;
import static com.bitbreeds.webrtc.sctp.model.SCTPFixedAttributeType.TSN;

/**
 * Logs values from incoming and outgoing SCTP packets
 *
 * Useful for debugging protocol problems.
 */
public class LoggingConnection extends ConnectionImplementation {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public LoggingConnection(KeyStoreInfo keyStoreInfo, PeerDescription remoteDescription) {
        super(keyStoreInfo, remoteDescription);
    }

    public void putDataOnWire(byte[] out) {
        SCTPMessage msg = SCTPMessage.fromBytes(out);
        msg.getChunks().forEach(i -> logSCTPChunk(i,"Outgoing id:"+this.getPeerConnection().getId()));
        super.putDataOnWire(out);
    }

    @Override
    public void processReceivedMessage(byte[] buf) {
        SCTPMessage msg = SCTPMessage.fromBytes(buf);
        msg.getChunks().forEach(i -> logSCTPChunk(i,"Incoming id:"+this.getPeerConnection().getId()));
        super.processReceivedMessage(buf);
    }

    public void logSCTPChunk(SCTPChunk chunk,String prefix) {
        if(SCTPMessageType.DATA.equals(chunk.getType())) {
            long tsn = SignalUtil.bytesToLong(chunk.getFixed().get(TSN).getData());
            int streamId = SignalUtil.intFromTwoBytes(chunk.getFixed().get(STREAM_IDENTIFIER_S).getData());
            int sequence = SignalUtil.intFromTwoBytes(chunk.getFixed().get(STREAM_SEQUENCE_NUMBER).getData());

            logger.info("{} Type: {} tsn: {} streamid: {} sequence: {} flags {}",prefix,chunk.getType(),tsn,streamId,sequence,chunk.getFlags());
        }
        else if(SCTPMessageType.SELECTIVE_ACK.equals(chunk.getType())) {
            SCTPFixedAttribute cum_tsn = chunk.getFixed().get(SCTPFixedAttributeType.CUMULATIVE_TSN_ACK);
            SCTPFixedAttribute arcw = chunk.getFixed().get(SCTPFixedAttributeType.ARWC);
            SCTPFixedAttribute num_gap = chunk.getFixed().get(SCTPFixedAttributeType.NUM_GAP_BLOCKS);
            SCTPFixedAttribute num_dupl = chunk.getFixed().get(SCTPFixedAttributeType.NUM_DUPLICATE);

            int remoteBuffer = SignalUtil.intFromFourBytes(arcw.getData());

            long cumulativeTSN = SignalUtil.bytesToLong(cum_tsn.getData());
            int gaps = SignalUtil.intFromTwoBytes(num_gap.getData());
            int dupl = SignalUtil.intFromTwoBytes(num_dupl.getData());

            List<GapAck> gapAcks = new ArrayList<>(gaps);
            ByteRange rng = new ByteRange(0,2);
            for(int i = 0; i<gaps; i++) {
                int a = SignalUtil.intFromTwoBytes(SignalUtil.copyRange(chunk.getRest(),rng));
                rng = rng.plus(2);
                int b = SignalUtil.intFromTwoBytes(SignalUtil.copyRange(chunk.getRest(),rng));
                rng = rng.plus(2);
                gapAcks.add(new GapAck(a,b));
            }

            List<Long> duplicates = new ArrayList<>(dupl);
            rng = rng.lengthFromA(4);
            for(int i = 0; i<dupl; i++) {
                long dpl = SignalUtil.bytesToLong(SignalUtil.copyRange(chunk.getRest(),rng));
                rng = rng.plus(4);
                duplicates.add(dpl);
            }

            logger.info("{} Type: {} tsn: {} buffer: {} gaps: {} duplicates: {}",prefix,chunk.getType(),cumulativeTSN,remoteBuffer,gapAcks,duplicates);
        }
        else if(SCTPMessageType.FORWARD_TSN.equals(chunk.getType())) {
            SCTPFixedAttribute cum_tsn = chunk.getFixed().get(SCTPFixedAttributeType.CUMULATIVE_TSN_ACK);
            long ackpt = SignalUtil.bytesToLong(cum_tsn.getData());
            logger.info("{} Type: {} forwardackpoint: {} ",prefix,chunk.getType().name(),ackpt);
        }
        else {
            logger.info("{} Type: {} ",prefix,chunk.getType().name());
        }
    }

}
