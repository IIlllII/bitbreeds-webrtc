package com.bitbreeds.webrtc.sctp.retransmission;/*
 *
 * Copyright (c) 12/11/2018, Jonas Waage
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

import com.bitbreeds.webrtc.sctp.impl.buffer.RetransmissionScheduler;
import org.junit.Test;


import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestRetransmission {

    @Test
    public void testStartStoRetrans() {
        RetransmissionScheduler scheduler = new RetransmissionScheduler();
        scheduler.start();

        await().atMost(5000, TimeUnit.MILLISECONDS)
                .until(scheduler::checkForTimeout);
    }

    @Test
    public void testNotStarted() {
        RetransmissionScheduler scheduler = new RetransmissionScheduler();
        assertFalse(scheduler.checkForTimeout());
    }


    @Test
    public void testSquence() {
        RetransmissionScheduler scheduler = new RetransmissionScheduler();
        scheduler.start();
        await().atMost(5,TimeUnit.SECONDS).until(scheduler::checkForTimeout);

        scheduler.stop();

        assertFalse(scheduler.checkForTimeout());
        scheduler.restart();

        await().atMost(5,TimeUnit.SECONDS).until(scheduler::checkForTimeout);

        scheduler.restart();
        assertFalse(scheduler.checkForTimeout());

        await().atMost(5,TimeUnit.SECONDS).until(scheduler::checkForTimeout);

        scheduler.start();
        scheduler.start();

        assertTrue(scheduler.checkForTimeout());
    }

}
