package com.bitbreeds.webrtc.sctp.impl.buffer;

import java.util.List;

/**
 * Copyright (c) 12/04/2018, Jonas Waage
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
public class SackResult {

    private final List<BufferedSent> fastRetransmits;
    private final boolean updatedCumulative;
    private final FwdAckPoint advancedAckPoint;
    private final long remoteCumulativeTSN;

    public SackResult(List<BufferedSent> fastRetransmits,
                      boolean updatedCumulative,
                      long remoteCumulativeTSN,
                      FwdAckPoint advancedAckPoint) {
        this.fastRetransmits = fastRetransmits;
        this.updatedCumulative = updatedCumulative;
        this.advancedAckPoint = advancedAckPoint;
        this.remoteCumulativeTSN = remoteCumulativeTSN;
    }

    public long getRemoteCumulativeTSN() {
        return remoteCumulativeTSN;
    }

    public List<BufferedSent> getFastRetransmits() {
        return fastRetransmits;
    }

    public boolean isUpdatedCumulative() {
        return updatedCumulative;
    }

    public FwdAckPoint getAdvancedAckPoint() {
        return advancedAckPoint;
    }

    @Override
    public String toString() {
        return "SackResult{" +
                "fastRetransmits=" + fastRetransmits +
                ", updatedCumulative=" + updatedCumulative +
                ", advancedAckPoint=" + advancedAckPoint +
                ", remoteCumulativeTSN=" + remoteCumulativeTSN +
                '}';
    }
}
