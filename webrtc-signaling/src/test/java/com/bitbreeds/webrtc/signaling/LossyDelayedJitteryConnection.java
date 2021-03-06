package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import com.bitbreeds.webrtc.peerconnection.ConnectionImplementation;
import com.bitbreeds.webrtc.peerconnection.PeerDescription;
import com.bitbreeds.webrtc.sctp.model.SCTPChunk;
import com.bitbreeds.webrtc.sctp.model.SCTPMessage;
import com.bitbreeds.webrtc.sctp.model.SCTPMessageType;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/*
 * Copyright (c) 11/03/2018, Jonas Waage
 */


/**
 *
 * Creates a connection that will drop a certain percentage of messages and add a delay + jitter to each message
 * Allows to recreate some kinds of bad network conditions, and test the effects on the protocol.
 *
 */
public class LossyDelayedJitteryConnection extends ConnectionImplementation {

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Integer packetlossPercentageIn;
    private final Integer packetlossPercentageOut;
    private final int fixedDelay;
    private final int maxJitter;
    private final Random random = new Random(System.currentTimeMillis());

    private AtomicInteger outCount = new AtomicInteger(0);
    private AtomicInteger inCount = new AtomicInteger(0);

    private final ArrayList<DatedBytes> received = new ArrayList<>(1024);
    private final ArrayList<DatedBytes> toSend = new ArrayList<>(1024);

    private AtomicInteger ids = new AtomicInteger(0);

    private class DatedBytes {
        private final byte[] data;
        private final int id;
        private final long sendTime;

        private DatedBytes(byte[] data,long added,int delay) {
            this.data = data;
            this.id = ids.incrementAndGet();
            this.sendTime = added+delay;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DatedBytes that = (DatedBytes) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    public LossyDelayedJitteryConnection(KeyStoreInfo keyStoreInfo,
                                         PeerDescription remoteDescription,
                                         Integer packetlossPercentageIn,
                                         Integer packetlossPercentageOut,
                                         Integer fixedDelay,
                                         Integer maxJitter,
                                         String address,
                                         Integer updateTime) {
        super(keyStoreInfo,remoteDescription, address);

        int time = updateTime != null ? updateTime : 5;

        executorService.scheduleAtFixedRate(this::update, time, time, TimeUnit.MILLISECONDS);

        if(packetlossPercentageIn < 0 || packetlossPercentageIn > 100) {
            throw new IllegalArgumentException("Bad packetlossPercentage [0-100] allowed, was "+packetlossPercentageIn);
        }
        if(packetlossPercentageOut < 0 || packetlossPercentageOut > 100) {
            throw new IllegalArgumentException("Bad packetlossPercentage [0-100] allowed, was "+packetlossPercentageOut);
        }
        this.packetlossPercentageIn = packetlossPercentageIn;
        this.packetlossPercentageOut = packetlossPercentageOut;
        if(fixedDelay < 0) {
            throw new IllegalArgumentException("Bad fixedDelay > 0 allowed, was " + fixedDelay);
        }
        this.fixedDelay = fixedDelay;
        if(maxJitter < 0) {
            throw new IllegalArgumentException("Bad maxJitter > 0 allowed, was " + maxJitter);
        }
        this.maxJitter = maxJitter;
    }

    /**
     * Record the current time, and send/receive packets that have a lower time
     */
    public void update() {

        long time = System.currentTimeMillis();
        List<DatedBytes> shouldSend;

        synchronized (toSend) {
            shouldSend = toSend.stream().filter(i -> i.sendTime < time).collect(Collectors.toList());
            toSend.removeAll(shouldSend);
        }

        shouldSend.forEach(
                i -> super.putDataOnWire(i.data)
        );
        logger.debug("Sent {} {}",shouldSend.size(),toSend.size());

        List<DatedBytes> shouldReceive;
        synchronized (received) {
            shouldReceive = received.stream().filter(i -> i.sendTime < time).collect(Collectors.toList());
            received.removeAll(shouldReceive);
        }

        shouldReceive.forEach(
                i -> super.processReceivedMessage(i.data)
        );
        logger.debug("Received {} {}",shouldSend.size(),received.size());
    }

    /**
     * Ensure that the connection will not send a certain
     * percentage of messages (simulating a drop).
     *
     * Then add a delay including jitter to the packet
     *
     * Once that delay + jitter is passed send the packet
     *
     * @param out data to send
     */
    @Override
    public void putDataOnWire(byte[] out) {
        int cnt = outCount.incrementAndGet();

        SCTPMessage msg = SCTPMessage.fromBytes(out);
        List<SCTPMessageType> types = msg.getChunks()
                .stream()
                .map(SCTPChunk::getType)
                .collect(Collectors.toList());

        int rd = random.nextInt(100);
        if(rd > packetlossPercentageOut || cnt < 5) {
            int jitter = random.nextInt(maxJitter);
            synchronized (toSend) {
                logger.info("Sending packet with type {}",types);
                toSend.add(new DatedBytes(out, System.currentTimeMillis(), fixedDelay + jitter));
            }
        }
        else {
            logger.info("Dropped OUTGOING packet with type {}",types);
        }
    }


    /**
     * Ensure that the connection will not process this message
     * for some percentage of messages, simulating a drop.
     *
     * Then add a delay including jitter to the packet
     *
     * Once that delay + jitter is passed deliver the packet
     *
     * @param buf with received bytes
     */
    @Override
    public void processReceivedMessage(byte[] buf) {
        int cnt = inCount.incrementAndGet();

        SCTPMessage msg = SCTPMessage.fromBytes(buf);
        List<SCTPMessageType> types = msg.getChunks()
                .stream()
                .map(SCTPChunk::getType)
                .collect(Collectors.toList());
        int rd = random.nextInt(100);
        if(rd > packetlossPercentageIn || cnt < 5) {
            int jitter = random.nextInt(maxJitter);
            synchronized (received) {
                logger.info("Received packet with type {}",types);
                received.add(new DatedBytes(buf, System.currentTimeMillis(), fixedDelay + jitter));
            }
        }
        else {
            logger.info("Dropped INCOMING packet with type {}",types);
        }
    }

    @Override
    public void closeConnection() {
        super.closeConnection();
        executorService.shutdownNow();
    }
}
