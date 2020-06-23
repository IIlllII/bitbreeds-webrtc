package com.bitbreeds.webrtc.sctp.impl.model;

/*
 * Copyright (c) 29/06/16, Jonas Waage
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

import com.bitbreeds.webrtc.sctp.impl.SCTPReliability;
import com.bitbreeds.webrtc.sctp.impl.util.TSNUtil;
import com.bitbreeds.webrtc.sctp.model.SCTPOrderFlag;
import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stores data about a received message
 *
 * Needed for reassembly of fragmented messages and correct delivery to user.
 */
public class ReceivedData implements Comparable<ReceivedData> {

    private final long TSN;
    private final int streamId;
    private final int streamSequence;
    private final SCTPOrderFlag flags;
    private final SCTPPayloadProtocolId protocolId;
    private final SCTPReliability streamReliability;
    private final byte[] payload;

    public ReceivedData(long TSN,
                        int streamId,
                        int streamSequence,
                        SCTPOrderFlag flags,
                        SCTPPayloadProtocolId protocolId,
                        SCTPReliability streamReliability,
                        byte[] payload) {
        this.TSN = TSN;
        this.streamId = streamId;
        this.streamSequence = streamSequence;
        this.flags = flags;
        this.protocolId = protocolId;
        this.streamReliability = streamReliability;
        this.payload = payload;
    }

    public long getTSN() {
        return TSN;
    }

    public int getStreamId() {
        return streamId;
    }

    public int getStreamSequence() {
        return streamSequence;
    }

    public SCTPOrderFlag getFlag() {
        return flags;
    }

    public SCTPPayloadProtocolId getProtocolId() {
        return protocolId;
    }

    public byte[] getPayload() {
        return payload;
    }

    public SCTPReliability getStreamReliability() {
        return streamReliability;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReceivedData that = (ReceivedData) o;
        return TSN == that.TSN;
    }

    @Override
    public int hashCode() {
        return Objects.hash(TSN);
    }

    @Override
    public int compareTo(ReceivedData dataStorage) {
        if(this.getTSN() == dataStorage.getTSN()) {
            return 0;
        }
        if(TSNUtil.isBelow(this.getTSN(),dataStorage.getTSN())) {
            return -1;
        }
        else {
            return 1;
        }
    }

    @Override
    public String toString() {
        return "ReceivedData{" +
                "TSN=" + TSN +
                ", streamId=" + streamId +
                ", streamSequence=" + streamSequence +
                ", flags=" + flags +
                ", protocolId=" + protocolId +
                ", streamReliability=" + streamReliability +
                ", payload=" + Arrays.toString(payload) +
                '}';
    }
}
