package com.bitbreeds.webrtc.peerconnection;

import java.math.BigInteger;

/**
 * Copyright (c) 26/04/16, Jonas Waage
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
 * Representation of an ICE candidate
 */
public class IceCandidate {

    private final int port;
    private final String ip;
    private final long priority;
    private final BigInteger number;

    public IceCandidate(BigInteger number, int port, String ip, long priority) {
        this.port = port;
        this.ip = ip;
        this.priority = priority;
        this.number = number;
    }

    public String candidateString() {
        return "candidate:"+number+" 1 UDP 2122252543 " + this.getIp() + " " + this.getPort() + " typ host generation 0 ufrag Qhj8 network-cost 50";
    }

    public int getPort() {
        return port;
    }

    public String getIp() {
        return ip;
    }

    public long getPriority() {
        return priority;
    }
}
