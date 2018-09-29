package com.bitbreeds.webrtc.sctp.impl.buffer;

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
public class RetransmissionTimeout {

    public final static long INITIAL_MILLIS = 3000L;
    public final static double INITIAL = 3.0;
    private final static double ALPHA = 1/8.0;
    private final static double BETA = 1/4.0;

    private final static double MIN = 1.0;
    private final static double MAX = 60.0;

    private final double srtt ;
    private final double rttvar;
    private final double rto;

    static RetransmissionTimeout initial() {
        return new RetransmissionTimeout(-1,-1,INITIAL);
    }

    private RetransmissionTimeout(double srtt, double rttvar, double rto) {
        this.srtt = srtt;
        this.rttvar = rttvar;
        this.rto = Math.min(Math.max(rto,MIN),MAX);
    }

    int getRetransmissionTimeoutMillis() {
        return (int)Math.floor(rto*1000.0);
    }

    public RetransmissionTimeout backOff() {
        return new RetransmissionTimeout(srtt,rttvar,rto*2.0);
    }

    RetransmissionTimeout addMeasurement(double rtt) {
        if(srtt < 0) {
            double nextRttvar = rtt / 2.0;
            double nextRto = srtt + 4 * rttvar;
            return new RetransmissionTimeout(rtt,nextRttvar,nextRto);
        }
        else {
            double nextRttvar = (1 - BETA) * rttvar + BETA * ( srtt - rtt );
            double nextSrtt = (1 - ALPHA) * srtt + ALPHA * rtt;
            double nextRto = srtt + 4 * rttvar;
            return new RetransmissionTimeout(nextSrtt,nextRttvar,nextRto);
        }
    }

}
