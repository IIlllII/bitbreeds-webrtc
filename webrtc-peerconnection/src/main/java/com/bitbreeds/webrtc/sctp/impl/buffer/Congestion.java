package com.bitbreeds.webrtc.sctp.impl.buffer;/*
 *
 * Copyright (c) 10/11/2018, Jonas Waage
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

public class Congestion {

    private final int cwnd;
    private final int ssThresh;
    private final int MTU;
    private final int partialBytesAcked;

    static Congestion initial(int MTU) {
        return new Congestion(Math.min(MTU*4,Math.max(2*MTU,4380)),12*MTU,MTU,0);
    }

    public Congestion(int cwnd, int ssThresh, int MTU, int partialBytesAcked) {
        this.cwnd = cwnd;
        this.ssThresh = ssThresh;
        this.MTU = MTU;
        this.partialBytesAcked = partialBytesAcked;
    }

    public Congestion timeout() {
        return new Congestion(MTU,Math.max(cwnd/2, 4*MTU),MTU,partialBytesAcked);
    }

    public Congestion increase() {
        return new Congestion(cwnd+MTU,ssThresh,MTU,partialBytesAcked);
    }

    public Congestion loss() {
        return new Congestion(Math.max(cwnd/2,MTU*4),ssThresh,MTU,partialBytesAcked);
    }

    public Congestion reset() {
        return new Congestion(Math.max(cwnd/2,MTU*4),ssThresh,MTU,0);
    }
}
