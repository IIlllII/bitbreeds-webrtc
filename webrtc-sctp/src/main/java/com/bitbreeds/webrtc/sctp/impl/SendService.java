package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.common.SackUtil;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.model.*;
import org.apache.commons.codec.binary.Hex;
import org.joda.time.DateTime;
import org.pcollections.HashPMap;
import org.pcollections.HashTreePMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.bitbreeds.webrtc.common.SignalUtil.sign;
import static com.bitbreeds.webrtc.sctp.model.SCTPFixedAttributeType.*;

/**
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
 */
public class SendService {

    private static final Logger logger = LoggerFactory.getLogger(SendService.class);

    private AtomicLong sentBytes = new AtomicLong(0L);

    /**
     * Reference to handler to be able to pull parameters and when needed.
     */
    private final SCTPImpl handler;

    /**
     *
     * @param handler
     */
    public SendService(SCTPImpl handler) {
        this.handler = handler;
    }

    /**
     * Time in milliseconds before resend kicks in
     * It seems we get sacks evry second.
     * Wait 2 seconds before triggering resend.
     */
    private int resendMillis = 2000;

    /**
     * TSN for sent data
     */
    protected final AtomicInteger growingTSN = new AtomicInteger(0);

    /**
     * Mutex to access TSNS and duplicate data
     */
    private final Object dataMutex = new Object();

    /**
     * A map of sent TSNs.
     * This uses a persistent collection, which is easy to work with
     * when we need to pull it and resend.
     */
    protected HashPMap<Long,TimeData> sentTSNS = HashTreePMap.empty();

    /**
     * Set the amount of millis before a resend is attempted
     * @param resendMillis the minimum time passed before resend is attempted
     */
    public void setResendMillis(int resendMillis) {
        this.resendMillis = resendMillis;
    }


    /**
     * @return first TSN
     */
    public long getFirstTSN() {
        growingTSN.set(1);
        return growingTSN.get();
    }

    /**
     * @return increment TSN
     */
    public long getNextTSN() {
        long next = growingTSN.getAndIncrement();
        growingTSN.compareAndSet(Integer.MAX_VALUE,1);
        return next;
    }


    /**
     * @param tsn tsn for this message
     * @param data data to send
     */
    public void addMessageToSend(long tsn,byte[] data) {
        DateTime datePlusMillis = DateTime.now().plusMillis(resendMillis);
        TimeData timeData = new TimeData(datePlusMillis,data);
        synchronized (dataMutex) {
            sentTSNS = sentTSNS.plus(tsn,timeData);
        }
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
    public List<byte[]> getMessagesForResend() {
        List<Long> forResend = sentTSNS.entrySet().stream()
                .filter( i -> DateTime.now().isAfter(i.getValue().time))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        logger.debug("Got these TSNs for resend {}",forResend);

        return sentTSNS.values().stream()
                .filter( i -> DateTime.now().isAfter(i.time))
                .map(i->i.data)
                .collect(Collectors.toList());
    }

    /**
     * @param gaps retrieved gaps
     * @param duplicates TODO not sure what to do with these
     */
    public void updateAcknowledgedTSNS(
            long cumulativeTsnAck,
            List<SackUtil.GapAck> gaps,
            List<Long> duplicates) {

        final HashPMap<Long,TimeData> sent = sentTSNS;

        logger.debug("Before removal contains: " + sent);
        logger.debug("Updating acks with cumulative " + cumulativeTsnAck + " and gaps " + gaps );

        /**
         * Filter and remove those with TSN below cumulativeTsnAck.
         */
        final Collection<Long> ls = sent.keySet().stream()
                .filter(j -> j <= cumulativeTsnAck).collect(Collectors.toSet());

        synchronized (dataMutex) {
            sentTSNS = sentTSNS.minusAll(ls);
        }

        removeAllAcknowledged(cumulativeTsnAck,gaps);

        logger.debug("After remove it contains: " + sentTSNS);
    }


    /**
     *
     * @param cumulativeTsnAck lowest ack we are sure everything below has been acknowledged
     * @param gaps the acknowledged groups offset from cumulativeTsnAck
     *
     * <a href=https://tools.ietf.org/html/rfc4960#section-3.3.4>SACK spec</a>
     */
    protected void removeAllAcknowledged(long cumulativeTsnAck,List<SackUtil.GapAck> gaps) {
        gaps.forEach(i-> {
            Collection<Long> ls = sentTSNS.keySet().stream()
                    .filter(j ->
                            j >= (cumulativeTsnAck + i.start) &&
                                    j <= (cumulativeTsnAck + i.end))
                    .collect(Collectors.toSet());

            synchronized (dataMutex) {
                sentTSNS = sentTSNS.minusAll(ls);
            }
        });
    }



    /**
     *
     * @param data the data to send
     * @param ppid protocol id
     * @param header sctp header
     * @return payload data
     */
    public byte[] createPayloadMessage(
            byte[] data,
            SCTPPayloadProtocolId ppid,
            SCTPHeader header) {

        logger.debug("Creating payload");

        long myTSN = getNextTSN();

        Map<SCTPFixedAttributeType,SCTPFixedAttribute> attr  = new HashMap<>();
        attr.put(TSN,new SCTPFixedAttribute(TSN, SignalUtil.longToFourBytes(myTSN)));
        attr.put(STREAM_IDENTIFIER_S,new SCTPFixedAttribute(STREAM_IDENTIFIER_S,SignalUtil.twoBytesFromInt(0)));
        attr.put(STREAM_SEQUENCE_NUMBER,new SCTPFixedAttribute(STREAM_SEQUENCE_NUMBER,SignalUtil.twoBytesFromInt(0)));
        attr.put(PROTOCOL_IDENTIFIER,new SCTPFixedAttribute(PROTOCOL_IDENTIFIER,new byte[]{0,0,0,sign(ppid.getId())}));

        int fixed = attr.values().stream()
                .map(i->i.getType().getLgt())
                .reduce(0,Integer::sum);

        int lgt = 4 + fixed + data.length;

        byte[] dataOut = SignalUtil.padToMultipleOfFour(data);

        SCTPChunk chunk = new SCTPChunk(
                SCTPMessageType.DATA,
                SCTPFlags.UNORDERED_UNFRAGMENTED,
                lgt,
                attr,
                new HashMap<>(),
                dataOut);

        SCTPMessage msg = new SCTPMessage(header, Collections.singletonList(chunk));

        byte[] finalOut = SCTPUtil.addChecksum(msg).toBytes();

        logger.debug("Sending payload with TSN: " + myTSN  + " and data: " + Hex.encodeHexString(finalOut));

        sentBytes.getAndAdd(data.length);

        /**
         * Add to resend map
         * Should remove on correct SACK or too large or too long map.
         */
        addMessageToSend(myTSN,finalOut);

        return finalOut;
    }

    /**
     * @return the amount of sent bytes in this association.
     */
    public long getSentBytes() {
        return sentBytes.get();
    }
}
