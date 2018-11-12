package com.bitbreeds.webrtc.sctp.impl.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/*
 * Copyright (c) 26/02/2018, Jonas Waage
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
 * TODO make immutable version, updated in AtomicRef in SCTPImpl (way easier to test)
 */
public class RetransmissionScheduler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final AtomicReference<RetransmissionTimeout> timeout;

    private final AtomicReference<Instant> lastInteraction =
            new AtomicReference<>(Instant.now());

    private final AtomicBoolean hasInflight = new AtomicBoolean(false);

    public RetransmissionScheduler() {
        timeout = new AtomicReference<>(RetransmissionTimeout.initial());
    }

    /**
     * Add measurement of RTT so timeout can be updated
     * @param rtt of connection
     * @return new timeout calculation
     */
    public RetransmissionTimeout addMeasure(double rtt) {
        RetransmissionTimeout tim = timeout.updateAndGet(i->i.addMeasurement(rtt));
        logger.info("Update retransmission timeout to {}",tim);
        return tim;
    }

    /**
     *
     * @return true if a timeout occurred
     */
    public boolean checkForTimeout() {
        if(hasInflight.get()) {
            Instant ins = lastInteraction.get();
            long millis = timeout.get().getRetransmissionTimeoutMillis();
            return ins.plusMillis(millis).isBefore(Instant.now());
        }
        return false;
    }

    /**
     * Stop current timeout and reschedule
     */
    public void restart() {
        stop();
        start();
    }

    /**
     * Will schedule a retransmission if none is running.
     */
    public void start() {
        if(hasInflight.compareAndSet(false,true)) {
            lastInteraction.set(Instant.now());
        }
    }

    public void stop() {
        hasInflight.compareAndSet(true,false);
    }


}
