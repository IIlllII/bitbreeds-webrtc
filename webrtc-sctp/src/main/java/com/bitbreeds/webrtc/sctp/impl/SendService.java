package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.*;
import com.bitbreeds.webrtc.sctp.impl.buffer.*;
import com.bitbreeds.webrtc.sctp.model.*;
import javafx.util.Pair;
import org.apache.commons.codec.binary.Hex;
import org.joda.time.DateTime;
import org.pcollections.HashPMap;
import org.pcollections.HashTreePMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.Buffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.bitbreeds.webrtc.common.SignalUtil.sign;
import static com.bitbreeds.webrtc.sctp.model.SCTPFixedAttributeType.*;

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
 * This handles all operation related to Sending payloads and receiving acknowledgements.
 *
 * This includes:
 * TSN creation
 * Storage of sent messages for resend.
 * InFlight and buffered counters
 * @see <a href="https://tools.ietf.org/html/rfc4960#section-3.3.4">SCTP congestion window</a>
 *
 * TODO use better datastructures
 *
 */
public class SendService {

    private static final Logger logger = LoggerFactory.getLogger(SendService.class);

    private final static int MAX_DATA_CHUNKSIZE = 1024;

    private AtomicInteger streamSeq = new AtomicInteger(1);

    private final Object tsnLock = new Object();

    private long localTSN = 1;

    /**
     * Reference to handler to be able to pull parameters when needed.
     */
    private final SCTPImpl handler;

    private final static int DEFAULT_BUFFER_SIZE = 2000000;
    private final SendBuffer sendBuffer;

    /**
     *
     * @param handler reference to coordinator
     */
    SendService(SCTPImpl handler) {
        this.handler = handler;
        sendBuffer = new SendBuffer(DEFAULT_BUFFER_SIZE);
    }

    public long inflightMessages() {
        return sendBuffer.getInflightSize();
    }

    public long capacity() {
        return sendBuffer.getCapacity();
    }

    public long remoteBufferSize() {
        return sendBuffer.getRemoteBufferSize();
    }

    public long currentTSN() {
        synchronized (tsnLock) {
            return localTSN;
        }
    }

    /**
     *
     * TODO only needs a range in return, fix later
     *
     * @param num number of tsns needed
     * @return sequential list of tsns needed.
     */
    private List<Long> getTsnGroup(int num) {
        synchronized (tsnLock) {
            ArrayList<Long> ar = new ArrayList<>(num);
            for (int i = 0; i < num; i++) {
                ar.add(localTSN++);
            }
            return ar;
        }
    }

    private Long getSingleTSN() {
        synchronized (tsnLock) {
            return localTSN++;
        }
    }

    /**
     * @return first TSN
     */
    public long getFirstTSN() {
        synchronized (tsnLock) {
            return localTSN;
        }
    }



    /**
     * @param tsn tsn for this message
     * @param data data to send
     */
    private void addMessageToSend(long tsn,byte[] data) {

    }



    /**
     * This decides what messages are to be resent.
     * It is decided based on whether the time passed since the message was
     * sent is above resendMillis.
     *
     * The time can be substantially larger then resendMillis if resending thread
     * has trouble sending.
     *
     * @return list of data that should be sent again since no ack has been received
     */
    public List<WireRepresentation> getMessagesForResend() {
        return Collections.emptyList();
    }

    /**
     *
     * @param cumulativeTsnAck cumulative acc to update
     * @param gaps gaps to update
     * @param duplicates received duplicates
     * @param remoteBufferSize remote buffer size according to sack
     */
    public void updateAcknowledgedTSNS(
            long cumulativeTsnAck,
            List<GapAck> gaps,
            List<Long> duplicates,
            int remoteBufferSize) {
        sendBuffer.receiveSack(new SackData(cumulativeTsnAck,gaps,duplicates,remoteBufferSize));
        List<BufferedSent> toSend = sendBuffer.getDataToSend();

        toSend.forEach(i ->
                handler.getDataChannel().putDataOnWire(i.getData().getSctpPayload())
        );

    }

    public void initializeRemote(int remoteReceiveBufferSize,long initialTSN) {
        sendBuffer.initializeRemote(remoteReceiveBufferSize,initialTSN);
    }

    private int nextSSN() {
        return streamSeq.getAndUpdate(i -> i >= Short.MAX_VALUE ? 1 : i + 1);
    }

    public List<BufferedSent> bufferForSending(byte[] data, SCTPPayloadProtocolId ppid, Integer stream, SCTPHeader baseHeader) {
        List<SendData> toSend = createPayloadMessage(data,ppid,baseHeader,false,stream);
        sendBuffer.buffer(toSend);
        return sendBuffer.getDataToSend();
    }

    /**
     *
     * @param data payload to send
     * @return create message with payload to send
     */
    private List<SendData> createPayloadMessage(
            byte[] data,
            SCTPPayloadProtocolId ppid,
            SCTPHeader base,
            boolean order,
            int stream) {

        if(data.length <= MAX_DATA_CHUNKSIZE) {
            SendData single = createPayloadMessage(data,ppid,base,SCTPOrderFlag.UNORDERED_UNFRAGMENTED,0,getSingleTSN(),stream);
            return Collections.singletonList(single);
        }
        else {

            List<byte[]> dataSplit = SignalUtil.split(data,MAX_DATA_CHUNKSIZE);
            List<SendData> outPut = new ArrayList<>();

            List<Long> TSNs = getTsnGroup(dataSplit.size());

            int ssn = nextSSN();

            SendData start = createPayloadMessage(
                    dataSplit.get(0),
                    ppid,
                    base,
                    order ? SCTPOrderFlag.ORDERED_START_FRAGMENT : SCTPOrderFlag.UNORDERED_START_FRAGMENT,
                    ssn,TSNs.get(0),stream);
            outPut.add(start);

            for(int i = 1; i < dataSplit.size()-1 ; i++) {
                SendData mid = createPayloadMessage(
                        dataSplit.get(i),
                        ppid,
                        base,
                        order ? SCTPOrderFlag.ORDERED_MIDDLE_FRAGMENT : SCTPOrderFlag.UNORDERED_MIDDLE_FRAGMENT,
                        ssn,TSNs.get(i),stream);
                outPut.add(mid);
            }

            SendData end = createPayloadMessage(
                    dataSplit.get(dataSplit.size()-1),
                    ppid,
                    base,
                    order ? SCTPOrderFlag.ORDERED_END_FRAGMENT : SCTPOrderFlag.UNORDERED_END_FRAGMENT,
                    ssn,
                    TSNs.get(dataSplit.size()-1),stream);
            outPut.add(end);

            return outPut;
        }
    }


    /**
     *
     * @param data the data to send
     * @param ppid protocol id
     * @param header sctp header
     * @return payload data
     */
    private SendData createPayloadMessage(
            byte[] data,
            SCTPPayloadProtocolId ppid,
            SCTPHeader header,
            SCTPOrderFlag flag,
            int ssn,
            long myTSN,
            Integer stream) {

        int streamId = stream == null ? 0 : stream;

        logger.debug("Creating payload");

        Map<SCTPFixedAttributeType,SCTPFixedAttribute> attr  = new HashMap<>();
        attr.put(TSN,new SCTPFixedAttribute(TSN, SignalUtil.longToFourBytes(myTSN)));
        attr.put(STREAM_IDENTIFIER_S,new SCTPFixedAttribute(STREAM_IDENTIFIER_S,SignalUtil.twoBytesFromInt(streamId)));
        attr.put(STREAM_SEQUENCE_NUMBER,new SCTPFixedAttribute(STREAM_SEQUENCE_NUMBER,SignalUtil.twoBytesFromInt(ssn)));
        attr.put(PROTOCOL_IDENTIFIER,new SCTPFixedAttribute(PROTOCOL_IDENTIFIER,new byte[]{0,0,0,sign(ppid.getId())}));

        int fixed = attr.values().stream()
                .map(i->i.getType().getLgt())
                .reduce(0,Integer::sum);

        int lgt = 4 + fixed + data.length;

        byte[] dataOut = SignalUtil.padToMultipleOfFour(data);

        SCTPChunk chunk = new SCTPChunk(
                SCTPMessageType.DATA,
                flag,
                lgt,
                attr,
                new HashMap<>(),
                dataOut);

        SCTPMessage msg = new SCTPMessage(header, Collections.singletonList(chunk));

        byte[] finalOut = SCTPUtil.addChecksum(msg).toBytes();

        logger.debug("Sending payload with TSN: " + myTSN  + " and data: " + Hex.encodeHexString(finalOut));

        return new SendData(myTSN,streamId,ssn,flag,ppid,finalOut);
    }

    /**
     * @return the amount of sent bytes in this association.
     */
    public long getSentBytes() {
        return sendBuffer.getBytesSent();
    }
}
