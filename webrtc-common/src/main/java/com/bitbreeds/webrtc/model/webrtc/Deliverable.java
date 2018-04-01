package com.bitbreeds.webrtc.model.webrtc;

import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;

import java.util.Arrays;
import java.util.Objects;

/**
 * Copyright (c) 19/02/2018, Jonas Waage
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
public class Deliverable {
    private final byte[] data;
    private final int originalFragmentNumber;
    private final int streamId;
    private SCTPPayloadProtocolId protocolId;

    public Deliverable(byte[] data, int originalFragmentNumber, int streamId, SCTPPayloadProtocolId protocolId) {
        this.data = data;
        this.originalFragmentNumber = originalFragmentNumber;
        this.streamId = streamId;
        this.protocolId = protocolId;
    }

    public byte[] getData() {
        return data;
    }

    public int getOriginalFragmentNumber() {
        return originalFragmentNumber;
    }

    public int getStreamId() {
        return streamId;
    }

    public SCTPPayloadProtocolId getProtocolId() {
        return protocolId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Deliverable that = (Deliverable) o;
        return originalFragmentNumber == that.originalFragmentNumber &&
                streamId == that.streamId &&
                Arrays.equals(data, that.data) &&
                protocolId == that.protocolId;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(originalFragmentNumber, streamId, protocolId);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }
}
