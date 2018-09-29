package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.impl.model.ReceivedData;
import com.bitbreeds.webrtc.sctp.model.SCTPChunk;
import com.bitbreeds.webrtc.sctp.model.SCTPHeader;
import com.bitbreeds.webrtc.sctp.model.SCTPMessage;
import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static com.bitbreeds.webrtc.sctp.model.SCTPFixedAttributeType.*;

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
 * Handle a payload.
 * Store TSN for sack, then delegate to onMessage handler
 */
public class PayloadHandler implements MessageHandler {

    private final static Logger logger = LoggerFactory.getLogger(PayloadHandler.class);

    @Override
    public Optional<SCTPMessage> handleMessage(
            SCTP handler,
            SCTPContext ctx,
            SCTPHeader header,
            SCTPChunk data) {

        logger.debug("Received payload:" + data);

        long tsn = SignalUtil.bytesToLong(data.getFixed().get(TSN).getData());
        int streamId = SignalUtil.intFromTwoBytes(data.getFixed().get(STREAM_IDENTIFIER_S).getData());
        int sequence = SignalUtil.intFromTwoBytes(data.getFixed().get(STREAM_SEQUENCE_NUMBER).getData());
        int proto = SignalUtil.intFromFourBytes(data.getFixed().get(PROTOCOL_IDENTIFIER).getData());
        SCTPPayloadProtocolId ppid = SCTPPayloadProtocolId.fromValue(proto);

        ReceivedData storage = new ReceivedData(tsn,streamId,sequence,data.getFlags(),ppid,data.getRest());

        handler.handleSctpPayload(storage);

        return Optional.empty();
    }


}
