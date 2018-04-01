package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.common.GapAck;
import com.bitbreeds.webrtc.sctp.impl.model.SendData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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
 * Buffer for sent messages
 * Responsibilities:
 * - Assigning TSN
 * - Ensuring we have a finite send buffer
 * - Ensuring we overhold max inflight
 * - Ensure resend if message is never acked
 *
 *
 * TODO count buffered on each datachannel, not just the connection.
 *
 */
public class SendBuffer {

    private final Object lock = new Object();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Queue<BufferedSent> queue = new ArrayDeque<>();
    private Map<Long,BufferedSent> inFlight = new HashMap<>();

    private final static int DEFAULT_MAX_INFLIGHT = 5;
    private final int maxInflight;

    private int capacity;

    private long remoteBufferSize;
    private long remoteCumulativeTSN;
    private boolean remoteIsInitialized = false;

    private long bytesSent = 0;

    public SendBuffer(int capacity) {
        this(capacity,DEFAULT_MAX_INFLIGHT);
    }

    public SendBuffer(
            int capacity,
            int maxInflight
    ) {
        if(capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be above 0, is " + capacity);
        }
        this.maxInflight = maxInflight;
        this.capacity = capacity;
    }


    public void initializeRemote(int remoteBufferSize, long remoteCumulativeTSN) {
        synchronized (lock) {
            if (!remoteIsInitialized) {
                this.remoteBufferSize = remoteBufferSize;
                this.remoteCumulativeTSN = remoteCumulativeTSN;
                this.remoteIsInitialized = true;
            }
        }
    }


    public long getCapacity() {
        return capacity;
    }

    public long getRemoteBufferSize() {
        return remoteBufferSize;
    }

    public long getBytesSent() {
        return bytesSent;
    }


    /**
     *
     * Buffer a message for sending and move as many messages to inflight as possible
     *
     * @param messages data to store
     */
    public void buffer(List<SendData> messages) {
        if(!remoteIsInitialized) {
            throw new InitialMessageNotReceived("Initial SCTP message not received yet, no initial TSN");
        }
        synchronized (lock) {
            messages.forEach( data -> {
                if (capacity - data.getSctpPayload().length < 0) {
                    throw new OutOfBufferSpaceError("Send buffer has capacity " + capacity +
                            " message with size "+ data.getSctpPayload().length +" was dropped");
                }
                capacity -= data.getSctpPayload().length;
                queue.add(BufferedSent.buffer(data, data.getTsn()));
            });
            logger.debug("After buffering inflight:" + inFlight + " queue: " + queue.size());
        }
    }


    public int getInflightSize() {
        return inFlight.size();
    }

    /**
     * Remove packets acknowledged in sack from inflight.
     *
     * Set remote buffer size.
     *
     * @param sack acknowledgement
     */
    public void receiveSack(SackData sack) {
        logger.debug("Handling sack " + sack);
        synchronized (lock) {
            if(sack.getCumulativeTSN() > remoteCumulativeTSN) {
                remoteBufferSize = sack.getBufferLeft();
                remoteCumulativeTSN = sack.getCumulativeTSN();
            }
            List<BufferedSent> acked = inFlight.values().stream()
                    .filter(i->acknowledged(sack,i))
                    .collect(Collectors.toList());

            long size = acked.stream()
                    .map(i->i.getData().getSctpPayload().length)
                    .reduce(0,Integer::sum);

            List<Long> tsns = acked.stream()
                    .map(BufferedSent::getTsn)
                    .collect(Collectors.toList());

            capacity +=size;

            inFlight.keySet().removeAll(tsns);

            logger.debug("After Sack inflight:" + inFlight + " queue: " + queue.size());
        }
    }

    private boolean acknowledged(SackData data,BufferedSent inFlight) {
        return data.getCumulativeTSN() >= inFlight.getTsn() ||
                inGapAck(data.getTsns(),inFlight.getTsn());
    }


    private boolean inGapAck(List<GapAck> acks,long inflightTSN) {
        return acks.stream()
                .reduce(false,
                        (a,b) -> b.inRange(inflightTSN),
                        (a,b) -> a || b);
    }

    /**
     *
     * Move messages to inflight
     *
     * @return messages to put on wire
     */
    public List<BufferedSent> getDataToSend() {
        ArrayList<BufferedSent> toSend = new ArrayList<>();
        synchronized (lock) {
            while (!queue.isEmpty() && canFly(queue.element())) {
                BufferedSent buff = queue.remove();
                BufferedSent sent = buff.send();
                inFlight.put(buff.getTsn(), sent);
                toSend.add(sent);
            }
            bytesSent += toSend.stream()
                    .map(i->i.getData().getSctpPayload().length)
                    .reduce(0,Integer::sum);
            logger.debug("After getting messages to send inflight:" + inFlight + " queue: " + queue.size());
        }

        return toSend;
    }


    /**
     * @return whether max inflight and remote buffer allows sending or not
     */
    private boolean canFly(BufferedSent data) {
        return maxInflight - inFlight.size() > 0
                && remoteBufferSize > data.getData().getSctpPayload().length;
    }



}
