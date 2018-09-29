package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.model.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
public class HeartBeatHandler implements MessageHandler {

    @Override
    public Optional<SCTPMessage> handleMessage(
            SCTP handler,
            SCTPContext ctx,
            SCTPHeader header,
            SCTPChunk data) {

        SCTPHeader hdr = new SCTPHeader(
                header.getDestinationPort(),
                header.getSourcePort(),
                SignalUtil.bytesToLong(ctx.getInitiateTag()),
                0L);

        SCTPAttribute attr = data.getVariable().get(SCTPAttributeType.HERTBEAT_INFO);

        Map<SCTPAttributeType, SCTPAttribute> variableAttr = new HashMap<>();
        variableAttr.put(SCTPAttributeType.HERTBEAT_INFO, attr);

        int sum = variableAttr.values().stream()
                .map(SCTPAttribute::getLength).reduce(0, Integer::sum);

        SCTPChunk chunk = new SCTPChunk(
                SCTPMessageType.HEARTBEAT_ACK,
                SCTPOrderFlag.fromValue((byte)0),
                4 + sum,
                new HashMap<>(),
                variableAttr,
                new byte[]{});

        SCTPMessage out = new SCTPMessage(hdr, Collections.singletonList(chunk));
        return Optional.of(out);
    }


}
