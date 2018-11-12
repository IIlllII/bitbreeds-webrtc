package com.bitbreeds.webrtc.sctp.impl.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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


//TODO rewrite to check for resend, only stop.
public class RetransmissionScheduler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final AtomicReference<RetransmissionTimeout> timeout =
            new AtomicReference<>(RetransmissionTimeout.initial());

    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ScheduledFuture<?>> current = new AtomicReference<>();

    private final Runnable retransmit;

    public RetransmissionScheduler(Runnable retransmit) {
        this.scheduler = Executors.newScheduledThreadPool(1,r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });
        this.retransmit = retransmit;
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

    private void scheduleRetransmission() {
        current.set(createScheduler(retransmit));
    }

    /**
     * Stop current timeout and reschedule
     */
    public void restart() {
        stop();
        start();
    }

    public void shutdown() {
        logger.info("Shutting down pool {} ","retransmission");
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(3,TimeUnit.SECONDS);
            logger.info("Controlled shutdown of pool {} finished","retransmission");
        }
        catch (Exception e) {
            logger.info("Controlled shutdown of pool {} failed, due to: ","retransmission",e);
            scheduler.shutdownNow();
        }
    }

    /**
     * Will schedule a retransmission if none is running.
     */
    public void start() {
        if(current.get() == null) {
            scheduleRetransmission();
        }
    }

    public void stop() {
        ScheduledFuture<?> toCancel = current.getAndSet(null);
        logger.debug("Attempting stop of {}",toCancel);
        if(toCancel != null) {
            toCancel.cancel(false);
            logger.debug("Performed stop of {}",toCancel);
        }
    }

    private ScheduledFuture<?> createScheduler(Runnable action) {
            logger.debug("Scheduling retransmission timer with time {}",timeout.get().getRetransmissionTimeoutMillis());
            return scheduler.schedule(
                    action,
                    timeout.get().getRetransmissionTimeoutMillis(),
                    TimeUnit.MILLISECONDS);
    }

}
