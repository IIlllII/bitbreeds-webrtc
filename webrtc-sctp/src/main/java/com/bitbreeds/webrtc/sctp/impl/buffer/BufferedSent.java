package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.sctp.impl.ReceivedData;

import java.time.LocalDateTime;

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
public class BufferedSent {

    private final ReceivedData data;
    private final SendBufferedState bufferState;
    private final LocalDateTime lastSendTime;

    public BufferedSent(
            ReceivedData data,
            SendBufferedState state,
            LocalDateTime lastSendTime) {
        this.data = data;
        this.bufferState = state;
        this.lastSendTime = lastSendTime;
    }

    public static BufferedSent buffer(ReceivedData data) {
        return new BufferedSent(data,SendBufferedState.STORED,null);
    }

    public boolean canBeOverwritten() {
        return SendBufferedState.ACKNOWLEDGED.equals(bufferState);
    }

    public BufferedSent acknowledge() {
        return new BufferedSent(data, SendBufferedState.ACKNOWLEDGED,this.lastSendTime);
    }

    public BufferedSent send() {
        return new BufferedSent(data, SendBufferedState.SENT,LocalDateTime.now());
    }

    @Override
    public String toString() {
        return "BufferedReceived{" +
                "data=" + data +
                ", bufferState=" + bufferState +
                '}';
    }
}
