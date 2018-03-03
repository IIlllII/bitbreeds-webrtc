package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.sctp.model.SCTPOrderFlag;

/**
 * Copyright (c) 24/02/2018, Jonas Waage
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


public class SendData {

    private final int streamId;
    private final int streamSequence;
    private final SCTPOrderFlag flags;
    private final SCTPPayloadProtocolId protocolId;
    private final byte[] payload;
    private final long tsn;

    public SendData(long tsn,int streamId, int streamSequence, SCTPOrderFlag flags, SCTPPayloadProtocolId protocolId, byte[] payload) {
        this.tsn = tsn;
        this.streamId = streamId;
        this.streamSequence = streamSequence;
        this.flags = flags;
        this.protocolId = protocolId;
        this.payload = payload;
    }

    public long getTsn() {
        return tsn;
    }

    public int getStreamId() {
        return streamId;
    }

    public int getStreamSequence() {
        return streamSequence;
    }

    public SCTPOrderFlag getFlags() {
        return flags;
    }

    public SCTPPayloadProtocolId getProtocolId() {
        return protocolId;
    }

    public byte[] getPayload() {
        return payload;
    }
}
