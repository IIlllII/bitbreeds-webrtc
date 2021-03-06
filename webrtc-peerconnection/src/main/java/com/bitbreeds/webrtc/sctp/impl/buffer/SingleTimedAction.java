package com.bitbreeds.webrtc.sctp.impl.buffer;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
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
public class SingleTimedAction {

    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ScheduledFuture<?>> current = new AtomicReference<>();

    private final Runnable action;
    private final int millis;

    public SingleTimedAction(Runnable retransmit, int millis) {
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });

        this.action = retransmit;
        this.millis = millis;
    }

    private void schedule() {
        current.updateAndGet(i -> createScheduler(i, action));
    }

    /**
     * Stop current timeout and reschedule
     */
    public void restart() {
        stop();
        schedule();
    }

    /**
     * Will schedule a retransmission if none is running.
     */
    public void start() {
        schedule();
    }

    public void stop() {
        ScheduledFuture<?> toCancel = current.getAndSet(null);
        if(toCancel != null) {
            toCancel.cancel(false);
        }
    }

    private ScheduledFuture<?> createScheduler(ScheduledFuture<?> existing,Runnable action) {
        if(existing == null) {
            return scheduler.schedule(
                    action,
                    millis,
                    TimeUnit.MILLISECONDS);
        }
        else {
            return existing;
        }
    }


    public void shutdown() {
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(3,TimeUnit.SECONDS);
        }
        catch (Exception e) {
            scheduler.shutdownNow();
        }
    }

}
