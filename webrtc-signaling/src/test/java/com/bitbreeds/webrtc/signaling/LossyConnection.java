package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.peerconnection.ConnectionImplementation;
import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import com.bitbreeds.webrtc.peerconnection.PeerDescription;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;

/**
 * Copyright (c) 11/03/2018, Jonas Waage
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

    public LossyConnection(KeyStoreInfo keyStoreInfo,
                           PeerDescription remoteDescription,
                           Integer packetlossPercentageIn,
                           Integer packetlossPercentageOut) {
        super(keyStoreInfo,remoteDescription);
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
        int rd = random.nextInt(100);
        if(rd > packetlossPercentageOut) {
            super.putDataOnWire(out);
        }
        else {
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
        int rd = random.nextInt(100);
        if(rd > packetlossPercentageIn) {
            super.processReceivedMessage(buf);
        }
        else {
            logger.info("Dropped received message with rd {} and data {}",rd, Hex.encodeHexString(buf));
        }
    }
}
