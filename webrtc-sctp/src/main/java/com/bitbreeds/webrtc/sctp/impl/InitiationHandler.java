package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.model.*;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.bitbreeds.webrtc.sctp.model.SCTPFixedAttributeType.*;

/*
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
public class InitiationHandler implements MessageHandler {

    /**
     * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-channel-13#ref-I-D.ietf-tsvwg-sctp-ndata">draft</a>
     *
     * Number of inbound and outbound stream
     */
    private final static int INIT_STREAMS = 65535;

    @Override
    public Optional<SCTPMessage> handleMessage(
            SCTPImpl handler,
            SCTPContext ctx,
            SCTPHeader header,
            SCTPChunk data) {

        SCTPContext context = new SCTPContext(
                data.getFixed().get(SCTPFixedAttributeType.INITIATE_TAG).getData(),
                header.getSourcePort(),
                header.getDestinationPort());

        handler.setContext(context);

        /*
         * Set initial remote buffersize
         */
        int remoteBufferSize = SignalUtil.intFromFourBytes(data.getFixed().get(SCTPFixedAttributeType.ARWC).getData());

        SCTPHeader hdr = new SCTPHeader(
                header.getDestinationPort(),
                header.getSourcePort(),
                SignalUtil.bytesToLong(context.getInitiateTag()),
                0L);

        /*
         * Create fixed attributes
         */
        Map<SCTPFixedAttributeType,SCTPFixedAttribute> attr  = new HashMap<>();
        byte[] initate = SignalUtil.randomBytes(4);

        attr.put(INITIATE_TAG,new SCTPFixedAttribute(INITIATE_TAG,initate));
        attr.put(ARWC,new SCTPFixedAttribute(ARWC,SignalUtil.fourBytesFromInt((int)handler.getBufferCapacity())));
        attr.put(OUTBOUND_STREAMS,
                new SCTPFixedAttribute(OUTBOUND_STREAMS,SignalUtil.twoBytesFromInt(INIT_STREAMS)));
        attr.put(INBOUND_STREAMS,
                new SCTPFixedAttribute(INBOUND_STREAMS,SignalUtil.twoBytesFromInt(INIT_STREAMS)));
        attr.put(INITIAL_TSN,new SCTPFixedAttribute(INITIAL_TSN,SignalUtil.longToFourBytes(handler.getSender().getFirstTSN())));

        long tsn = SignalUtil.bytesToLong(data.getFixed().get(INITIAL_TSN).getData());

        /*
         * Initialize remote
         */
        handler.getSender().initializeRemote(remoteBufferSize,tsn);
        handler.handleReceiveInitialTSN(tsn);

        /*
         * Create variable attributes
         */
        SCTPAttribute cookie = new SCTPAttribute(
                SCTPAttributeType.STATE_COOKIE,
                createCookie(context.getInitiateTag()) );

        Map<SCTPAttributeType,SCTPAttribute> variableAttr  = new HashMap<>();
        variableAttr.put(SCTPAttributeType.STATE_COOKIE,cookie);

        int chunkSize = 4 +
                attr.values().stream().
                        map(i->i.getData().length)
                        .reduce(0,Integer::sum)
                +
                variableAttr.values().stream()
                        .map(i->SignalUtil.multipleOfFour(i.getLength()))
                        .reduce(0,Integer::sum);

        SCTPChunk chunk = new SCTPChunk(
                SCTPMessageType.INITIATION_ACK,
                SCTPOrderFlag.fromValue((byte)0),
                chunkSize,
                attr,
                variableAttr,
                new byte[] {});

        SCTPMessage out = new SCTPMessage(hdr, Collections.singletonList(chunk));
        return Optional.of(out);
    }

    private byte[] createCookie( byte[] tag ) {
        long millis = System.currentTimeMillis();
        SecureRandom rd = new SecureRandom();
        rd.setSeed(millis);
        final byte[] key = new byte[8];

        rd.nextBytes(key);
        byte[] hmac = SignalUtil.hmacSha1(tag,key);
        return SignalUtil.joinBytesArrays(hmac,SignalUtil.longToBytes(millis));
    }

}
