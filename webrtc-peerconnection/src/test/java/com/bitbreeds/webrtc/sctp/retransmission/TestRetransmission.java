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


import java.time.Instant;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestRetransmission {

    @Test
    public void testStartStoRetrans() {
        Instant time = Instant.now();
        RetransmissionScheduler scheduler = RetransmissionScheduler.initial(time);
        scheduler = scheduler.start(Instant.now());

        assertTrue(scheduler.checkForTimeout(time.plusMillis(4000)));
    }

    @Test
    public void testNotStarted() {
        Instant time = Instant.now();
        RetransmissionScheduler scheduler = RetransmissionScheduler.initial(time);
        assertFalse(scheduler.checkForTimeout(time.plusMillis(4000)));
    }

    @Test
    public void testSquence() {
        Instant time = Instant.now();
        RetransmissionScheduler scheduler = RetransmissionScheduler.initial(time);
        scheduler = scheduler.start(time);

        time = time.plusMillis(4000);
        assertTrue(scheduler.checkForTimeout(time));

        scheduler = scheduler.stop();

        time = time.plusMillis(4000);
        assertFalse(scheduler.checkForTimeout(time));

        time = time.plusMillis(2000);

        scheduler = scheduler.restart(time);

        time = time.plusMillis(2000);

        scheduler = scheduler.restart(time);

        time = time.plusMillis(1000);
        assertFalse(scheduler.checkForTimeout(time));

        time = time.plusMillis(4000);

        scheduler = scheduler.start(time);
        time = time.plusMillis(1000);
        scheduler = scheduler.start(time);

        assertTrue(scheduler.checkForTimeout(time));
    }

}
