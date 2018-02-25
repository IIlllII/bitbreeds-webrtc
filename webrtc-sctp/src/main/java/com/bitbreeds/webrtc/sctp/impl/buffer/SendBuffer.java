package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.sctp.impl.ReceivedData;

import java.util.*;

/*
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

/**
 * Buffer to store SCTP messages sent
 * <a href="https://tools.ietf.org/html/rfc4960#section-6.2.1">SCTP sack</a>
 * <a href="https://tools.ietf.org/html/rfc2581#section-4.2">TCP congestion control</a>
 *
 * TODO Must handle TSN and Stream Sequence id rollover
 *
 */
public class SendBuffer {

    private final Object lock = new Object();

    private final Queue<BufferedSent> buffered = new ArrayDeque<>();
    private final HashMap<Long,BufferedSent> inFlight = new HashMap<>();

    private final static int DEFAULT_MAX_INFLIGHT = 5;
    private final int maxInflight;

    private int capacity;

    int TSN;

    public SendBuffer(int capacity) {
        this(capacity,DEFAULT_MAX_INFLIGHT);
    }

    public SendBuffer(int capacity,int maxInflight) {
        if(capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be above 0, is " + capacity);
        }
        this.maxInflight = maxInflight;
        this.capacity = capacity;
    }


    public long getCapacity() {
        return capacity;
    }


    /**
     *
     * Buffer a message for sending
     *
     * @param data data to store
     */
    public void buffer(ReceivedData data) {
        synchronized (lock) {
            if(capacity - data.getPayload().length < 0) {
                throw new OutOfBufferSpaceError("Send buffer is full, message was dropped");
            }

            int canFly = maxInflight - inFlight.size();
            if(canFly <= 0) {
                BufferedSent buffer = BufferedSent.buffer(data);
                buffered.add(buffer);
            }

            else {
                BufferedSent buffer = BufferedSent.buffer(data);
                BufferedSent sent = buffer.send();
                //
                inFlight.put(data.getTSN(),sent);
            }


        }
    }

    /**
     * Remove packets acknowledged in sack from inflight
     * @param sack
     */
    public void receiveSack(SackData sack) {
        synchronized (lock) {


        }
    }

    /**
     * Get data to send to remote peer
     *
     * Move queued data to inflight, or ensure timed out inflight will be resent.
     *
     * Ensure send ordering is correct (lowest TSN first, resend first)
     *
     * @return sack data for creating complete SACK
     */
    public SendData getDataToSend() {
        throw new UnsupportedOperationException();
    }



}
