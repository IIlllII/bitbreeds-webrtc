package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.sctp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Copyright (c) 12/06/16, Jonas Waage
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
 * Creates a response message to a initiation request
 */
public class HeartBeatAckHandler implements MessageHandler {

    private final static Logger logger = LoggerFactory.getLogger(HeartBeatAckHandler.class);

    @Override
    public Optional<SCTPMessage> handleMessage(
            SCTPImpl handler,
            SCTPContext ctx,
            SCTPHeader header,
            SCTPChunk data) {

        logger.debug("Received heartbeat ack: " + data);

        SCTPAttribute info = data.getVariable().get(SCTPAttributeType.HERTBEAT_INFO);

        /**
         * Should be related to a sent heartbeat so we can measure RTT.
         */
        handler.getHeartBeatService().receiveHeartBeatAck(info.getData());

        return Optional.empty();
    }


}
