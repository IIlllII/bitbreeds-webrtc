package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.*;
import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.sctp.impl.model.SendData;
import com.bitbreeds.webrtc.sctp.impl.util.SCTPUtil;
import com.bitbreeds.webrtc.sctp.model.*;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
 * This handles all operation related to creating payload sctp messages.
 *
 * @see <a href="https://tools.ietf.org/html/rfc4960#section-3.3.4">SCTP congestion window</a>
 *
 */
public class PayloadCreator {

    private static final Logger logger = LoggerFactory.getLogger(PayloadCreator.class);

    private final static int MAX_DATA_CHUNKSIZE = 1024;

    private AtomicInteger streamSeq = new AtomicInteger(1);

    private final Object tsnLock = new Object();

    private long localTSN = 1;

    public long currentTSN() {
        synchronized (tsnLock) {
            return localTSN;
        }
    }

    /**
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


    private int nextSSN() {
        return streamSeq.getAndUpdate(i -> i >= Short.MAX_VALUE ? 1 : i + 1);
    }


    /**
     * @param data payload to send
     * @return create message with payload to send
     */
    List<SendData> createPayloadMessage(
            byte[] data,
            SCTPPayloadProtocolId ppid,
            SCTPHeader base,
            int stream,
            SCTPReliability reliability) {

        if (data.length <= MAX_DATA_CHUNKSIZE) {

            SendData single = createPayloadMessage(
                    data,
                    ppid,
                    base,
                    reliability.isOrdered() ? SCTPOrderFlag.ORDERED_UNFRAGMENTED : SCTPOrderFlag.UNORDERED_UNFRAGMENTED,
                    0,
                    getSingleTSN(),
                    stream,
                    reliability);

            return Collections.singletonList(single);
        } else {

            List<byte[]> dataSplit = SignalUtil.split(data, MAX_DATA_CHUNKSIZE);
            List<SendData> outPut = new ArrayList<>();

            List<Long> TSNs = getTsnGroup(dataSplit.size());

            int ssn = nextSSN();

            SendData start = createPayloadMessage(
                    dataSplit.get(0),
                    ppid,
                    base,
                    reliability.isOrdered() ? SCTPOrderFlag.ORDERED_START_FRAGMENT : SCTPOrderFlag.UNORDERED_START_FRAGMENT,
                    ssn,
                    TSNs.get(0),
                    stream,
                    reliability);
            outPut.add(start);

            for (int i = 1; i < dataSplit.size() - 1; i++) {
                SendData mid = createPayloadMessage(
                        dataSplit.get(i),
                        ppid,
                        base,
                        reliability.isOrdered() ? SCTPOrderFlag.ORDERED_MIDDLE_FRAGMENT : SCTPOrderFlag.UNORDERED_MIDDLE_FRAGMENT,
                        ssn,
                        TSNs.get(i),
                        stream,
                        reliability);

                outPut.add(mid);
            }

            SendData end = createPayloadMessage(
                    dataSplit.get(dataSplit.size() - 1),
                    ppid,
                    base,
                    reliability.isOrdered() ? SCTPOrderFlag.ORDERED_END_FRAGMENT : SCTPOrderFlag.UNORDERED_END_FRAGMENT,
                    ssn,
                    TSNs.get(dataSplit.size() - 1),
                    stream,
                    reliability);

            outPut.add(end);

            return outPut;
        }
    }


    /**
     * @param data   the data to send
     * @param ppid   protocol id
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
            Integer stream,
            SCTPReliability partialReliability) {

        int streamId = stream == null ? 0 : stream;

        Map<SCTPFixedAttributeType, SCTPFixedAttribute> attr = new HashMap<>();
        attr.put(TSN, new SCTPFixedAttribute(TSN, SignalUtil.longToFourBytes(myTSN)));
        attr.put(STREAM_IDENTIFIER_S, new SCTPFixedAttribute(STREAM_IDENTIFIER_S, SignalUtil.twoBytesFromInt(streamId)));
        attr.put(STREAM_SEQUENCE_NUMBER, new SCTPFixedAttribute(STREAM_SEQUENCE_NUMBER, SignalUtil.twoBytesFromInt(ssn)));
        attr.put(PROTOCOL_IDENTIFIER, new SCTPFixedAttribute(PROTOCOL_IDENTIFIER, new byte[]{0, 0, 0, sign(ppid.getId())}));

        int fixed = attr.values().stream()
                .map(i -> i.getType().getLgt())
                .reduce(0, Integer::sum);

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

        return new SendData(myTSN, streamId, ssn, flag, ppid, partialReliability ,finalOut);
    }
}
