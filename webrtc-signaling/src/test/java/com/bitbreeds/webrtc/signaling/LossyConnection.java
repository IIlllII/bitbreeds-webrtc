package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.peerconnection.AddressUtils;
import com.bitbreeds.webrtc.peerconnection.ConnectionImplementation;
import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import com.bitbreeds.webrtc.peerconnection.PeerDescription;
import com.bitbreeds.webrtc.sctp.model.SCTPMessage;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Copyright (c) 11/03/2018, Jonas Waage
 */


/**
 *
 * Creates a connection that will drop a certain percentage of messages.
 *
 * This connection is made to allow simple testing of recovery.
 *
 */
public class LossyConnection extends ConnectionImplementation {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Integer packetlossPercentageIn;
    private final Integer packetlossPercentageOut;
    private final Random random = new Random(System.currentTimeMillis());

    private AtomicInteger outCount = new AtomicInteger(0);
    private AtomicInteger inCount = new AtomicInteger(0);


    public LossyConnection(KeyStoreInfo keyStoreInfo,
                           PeerDescription remoteDescription,
                           Integer packetlossPercentageIn,
                           Integer packetlossPercentageOut,
                           String address) {
        super(keyStoreInfo,remoteDescription, AddressUtils.findAddress());
        if(packetlossPercentageIn < 0 || packetlossPercentageIn > 100) {
            throw new IllegalArgumentException("Bad packetlossPercentage [0-100] allowed, was "+packetlossPercentageIn);
        }
        if(packetlossPercentageOut < 0 || packetlossPercentageOut > 100) {
            throw new IllegalArgumentException("Bad packetlossPercentage [0-100] allowed, was "+packetlossPercentageOut);
        }
        this.packetlossPercentageIn = packetlossPercentageIn;
        this.packetlossPercentageOut = packetlossPercentageOut;
    }

    /**
     * Ensure that the connection will not send a certain
     * percentage of messages (simulating a drop).
     *
     * @param out data to send
     */
    @Override
    public void putDataOnWire(byte[] out) {
        int cnt = outCount.incrementAndGet();

        int rd = random.nextInt(100);
        if(rd > packetlossPercentageOut || cnt < 5) {
            super.putDataOnWire(out);
        }
        else {
            SCTPMessage msg = SCTPMessage.fromBytes(out);
            logger.info("Parsed: {}",msg);
            logger.info("Dropped out message with rd {} and data {}",rd, Hex.encodeHexString(out));
        }
    }


    /**
     * Ensure that the connection will not process this message
     * for some percentage of messages, simulating a drop.
     *
     * @param buf with received bytes
     */
    @Override
    public void processReceivedMessage(byte[] buf) {
        int cnt = inCount.incrementAndGet();

        int rd = random.nextInt(100);
        if(rd > packetlossPercentageIn || cnt < 5) {
            super.processReceivedMessage(buf);
        }
        else {
            SCTPMessage msg = SCTPMessage.fromBytes(buf);
            logger.info("Parsed: {}",msg);
            logger.info("Dropped received message with rd {} and data {}",rd, Hex.encodeHexString(buf));
        }
    }
}
