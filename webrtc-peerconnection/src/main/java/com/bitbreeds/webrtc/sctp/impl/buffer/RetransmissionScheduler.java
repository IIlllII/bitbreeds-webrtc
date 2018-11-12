package com.bitbreeds.webrtc.sctp.impl.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

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


public class RetransmissionScheduler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final RetransmissionTimeout timeout;

    private final Instant lastInteraction;

    private final boolean hasInflight;

    public static RetransmissionScheduler initial(Instant time) {
        return new RetransmissionScheduler(RetransmissionTimeout.initial(),time,false);
    }

    private RetransmissionScheduler(RetransmissionTimeout timeout, Instant lastInteraction, boolean hasInflight) {
        this.timeout = timeout;
        this.lastInteraction = lastInteraction;
        this.hasInflight = hasInflight;
    }

    /**
     * Add measurement of RTT so timeout can be updated
     * @param rtt of connection
     * @return new timeout calculation
     */
    public RetransmissionScheduler addMeasure(double rtt) {
        RetransmissionTimeout tim = timeout.addMeasurement(rtt);
        logger.debug("Update retransmission timeout to {}",tim);
        return new RetransmissionScheduler(tim,lastInteraction,hasInflight);
    }

    /**
     *
     * @return true if a timeout occurred
     */
    public boolean checkForTimeout(Instant time) {
        if(hasInflight) {
            long millis = timeout.getRetransmissionTimeoutMillis();
            return lastInteraction.plusMillis(millis).isBefore(time);
        }
        return false;
    }

    /**
     * Stop current timeout and reschedule
     */
    public RetransmissionScheduler restart(Instant time) {
        return stop().start(time);
    }

    /**
     * Will schedule a retransmission if none is running.
     */
    public RetransmissionScheduler start(Instant time) {
        if(!hasInflight) {
            return new RetransmissionScheduler(timeout,time,true);
        }
        else {
            return this;
        }
    }

    public RetransmissionScheduler stop() {
        if(hasInflight) {
            return new RetransmissionScheduler(timeout,lastInteraction,false);
        }
        else {
            return this;
        }
    }

    public long getCurrentTimeoutMillis() {
        return timeout.getRetransmissionTimeoutMillis();
    }

}
