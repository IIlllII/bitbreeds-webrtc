package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.ByteRange;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.impl.util.SCTPUtil;
import com.bitbreeds.webrtc.sctp.model.*;
import org.pcollections.HashPMap;
import org.pcollections.HashTreePMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.bitbreeds.webrtc.common.SignalUtil.bytesToLong;
import static com.bitbreeds.webrtc.common.SignalUtil.copyRange;

/*
 * Copyright (c) 21/07/16, Jonas Waage
 */

/**
 * Responsible for creating a heartbeat.
 * Responsible for handling heartbeat ack data.
 * Responsible for holding data derived from heartbeats.
 *
 * TODO implement shutdown if lots of missing heartbeats.
 */
public class HeartBeatService {

    private final static Logger logger = LoggerFactory.getLogger(HeartBeatService.class);

    /**
     * RTT in milliseconds
     */
    private volatile long rttMillis = -1L;

    private HashPMap<UUID, Instant> rttMap = HashTreePMap.empty();

    private final Object mutex = new Object();

    /**
     * Calculates RTT value
     * Resets shutdown timers
     *
     * @param heartBeatInfo from ack
     * @return millis of rtt
     */
    public long receiveHeartBeatAck(byte[] heartBeatInfo) {

        UUID uuid = new UUID(
                bytesToLong(copyRange(heartBeatInfo, new ByteRange(0, 8))),
                bytesToLong(copyRange(heartBeatInfo, new ByteRange(8, 16))));

        Instant time = rttMap.get(uuid);
        if (time == null) {
            logger.debug("Ack with unknown uuid {}",uuid);
            //TODO figure out why this happens: throw new IllegalArgumentException("Ack with unkown uuid: " + uuid + " map contains: " + rttMap);
        } else {
            rttMillis = Instant.now().toEpochMilli() - time.toEpochMilli();
            synchronized (mutex) {
                rttMap = rttMap.minus(uuid);
            }
        }

        List<UUID> old = rttMap.entrySet().stream()
                .filter(a-> a.getValue().isBefore(Instant.now().minusSeconds(10)))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        synchronized (mutex) {
            rttMap = rttMap.minus(old);
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
     * @param header header
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
            rttMap = rttMap.plus(id, Instant.now());
        }
        return out;
    }

}
