package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.sctp.impl.ReceivedData;

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
public class BufferedReceived {

    private final ReceivedData data;
    private final ReceiveBufferedState bufferState;
    private final DeliveredState deliverState;

    public BufferedReceived(
            ReceivedData data,
            ReceiveBufferedState state,
            DeliveredState deliverState) {
        this.data = data;
        this.bufferState = state;
        this.deliverState = deliverState;
    }

    public ReceivedData getData() {
        return data;
    }

    public ReceiveBufferedState getState() {
        return bufferState;
    }

    public boolean canBeOverwritten() {
        return ReceiveBufferedState.FINISHED.equals(bufferState) && DeliveredState.DELIVERED.equals(deliverState);
    }

    public BufferedReceived acknowledge() {
        return new BufferedReceived(data, ReceiveBufferedState.ACKED,deliverState);
    }

    public BufferedReceived finish() {
        return new BufferedReceived(data, ReceiveBufferedState.FINISHED,deliverState);
    }

    public BufferedReceived deliver() {
        return new BufferedReceived(data,bufferState,DeliveredState.DELIVERED);
    }

    public boolean readyForUnorderedDelivery() {
        return DeliveredState.READY.equals(deliverState) && getData().getFlag().isUnordered();
    }

    public boolean readyForOrderedDelivery() {
        return DeliveredState.READY.equals(deliverState) && getData().getFlag().isOrdered();
    }

    public boolean isDelivered() {
        return DeliveredState.DELIVERED.equals(deliverState);
    }

    @Override
    public String toString() {
        return "BufferedReceived{" +
                "data=" + data +
                ", bufferState=" + bufferState +
                ", deliverState=" + deliverState +
                '}';
    }
}
