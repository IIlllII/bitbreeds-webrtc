package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.ByteRange;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.impl.util.SCTPUtil;
import com.bitbreeds.webrtc.sctp.model.*;
import org.joda.time.DateTime;
import org.pcollections.HashPMap;
import org.pcollections.HashTreePMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.bitbreeds.webrtc.common.SignalUtil.bytesToLong;
import static com.bitbreeds.webrtc.common.SignalUtil.copyRange;

/**
 * Copyright (c) 21/07/16, Jonas Waage
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
 * Responsible for creating a heartbeat.
 * Responsible for handling heartbeat ack data.
 * Responsible for holding data derived from heartbeats.
 * <p>
 * TODO implement shutdown if lots of missing heartbeats.
 */
public class HeartBeatService {

    private final static Logger logger = LoggerFactory.getLogger(HeartBeatService.class);

    /**
     * RTT in milliseconds
     */
    private volatile long rttMillis = -1L;

    private HashPMap<UUID, DateTime> rttMap = HashTreePMap.empty();

    private final Object mutex = new Object();

    /**
     * Calculates RTT value
     * Resets shutdown timers
     *
     * @param heartBeatInfo from ack
     */
    public long receiveHeartBeatAck(byte[] heartBeatInfo) {

        UUID uuid = new UUID(
                bytesToLong(copyRange(heartBeatInfo, new ByteRange(0, 8))),
                bytesToLong(copyRange(heartBeatInfo, new ByteRange(8, 16))));

        DateTime time = rttMap.get(uuid);
        if (time == null) {
            throw new IllegalArgumentException("Ack with unkown uuid: " + uuid + " map contains: " + rttMap);
        } else {
            rttMillis = DateTime.now().getMillis() - time.getMillis();
            synchronized (mutex) {
                rttMap = rttMap.minus(uuid);
            }
        }
        return rttMillis;
    }


    /**
     * @return the current RTT in milliseconds, -1 if not yet calculated.
     */
    public long getRttMillis() {
        return rttMillis;
    }

    /**
     * @return heartbeat message
     */
    public SCTPMessage createHeartBeat(SCTPHeader header) {

        UUID id = UUID.randomUUID();
        byte[] heartBeatInfo = SignalUtil.joinBytesArrays(
                SignalUtil.longToBytes(id.getMostSignificantBits()),
                SignalUtil.longToBytes(id.getLeastSignificantBits()));

        Map<SCTPAttributeType, SCTPAttribute> variableAttr = new HashMap<>();
        variableAttr.put(SCTPAttributeType.HERTBEAT_INFO,
                new SCTPAttribute(SCTPAttributeType.HERTBEAT_INFO, heartBeatInfo));

        int sum = variableAttr.values().stream()
                .map(SCTPAttribute::getLength).reduce(0, Integer::sum);

        SCTPChunk heartBeat = new SCTPChunk(
                SCTPMessageType.HEARTBEAT,
                SCTPOrderFlag.fromValue((byte) 0),
                4 + sum,
                new HashMap<>(),
                variableAttr,
                new byte[]{});
        SCTPMessage msg = new SCTPMessage(header, Collections.singletonList(heartBeat));

        SCTPMessage out = SCTPUtil.addChecksum(msg);
        synchronized (mutex) {
            rttMap = rttMap.plus(id, DateTime.now());
        }
        return out;
    }

}
