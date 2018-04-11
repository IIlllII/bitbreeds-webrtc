package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.sctp.impl.model.SendData;

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
public class BufferedSent implements Comparable<BufferedSent> {

    private final SendData data;
    private final SendBufferedState bufferState;
    private final LocalDateTime lastSendTime;
    private final long tsn;
    private final int resends;
    private final int fastResendNum;

    public boolean canFastResend() {
        return !fastResent && fastResendNum >= 3;
    }

    private final boolean fastResent;

    public BufferedSent(
            SendData data,
            SendBufferedState bufferState,
            LocalDateTime lastSendTime,
            long tsn,
            int resends,
            int fastResendNum,
            boolean fastResent) {
        this.data = data;
        this.bufferState = bufferState;
        this.lastSendTime = lastSendTime;
        this.tsn = tsn;
        this.resends = resends;
        this.fastResendNum = fastResendNum;
        this.fastResent = fastResent;
    }

    public static BufferedSent buffer(SendData data,long tsn) {
        return new BufferedSent(data,SendBufferedState.STORED,null,tsn,0,0,false);
    }

    public boolean canBeOverwritten() {
        return SendBufferedState.ACKNOWLEDGED.equals(bufferState);
    }

    public BufferedSent acknowledge() {
        return new BufferedSent(data, SendBufferedState.ACKNOWLEDGED,lastSendTime,tsn,resends,fastResendNum,fastResent);
    }

    public BufferedSent resend() {
        return new BufferedSent(data, SendBufferedState.SENT,LocalDateTime.now(),tsn,resends+1,fastResendNum,fastResent);
    }

    public BufferedSent fastResend() {
        return new BufferedSent(data, SendBufferedState.SENT,LocalDateTime.now(),tsn,resends,fastResendNum,true);
    }

    public BufferedSent markFast() {
        return new BufferedSent(data, SendBufferedState.SENT,lastSendTime,tsn,resends,fastResendNum+1,fastResent);
    }

    public BufferedSent send() {
        return new BufferedSent(data, SendBufferedState.SENT,LocalDateTime.now(),tsn,resends,fastResendNum,fastResent);
    }

    public SendData getData() {
        return data;
    }

    public long getTsn() {
        return tsn;
    }

    public LocalDateTime getLastSendTime() {
        return lastSendTime;
    }

    @Override
    public String toString() {
        return "BufferedSent{" +
                "bufferState=" + bufferState +
                ", lastSendTime=" + lastSendTime +
                '}';
    }

    @Override
    public int compareTo(BufferedSent bufferedSent) {
        return Long.compare(this.tsn,bufferedSent.tsn);
    }
}
