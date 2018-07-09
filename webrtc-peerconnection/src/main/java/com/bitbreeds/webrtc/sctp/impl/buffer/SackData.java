package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.model.sctp.GapAck;

import java.util.List;
import java.util.Objects;

/**
 * Copyright (c) 19/02/2018, Jonas Waage
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


public class SackData {
    private final List<GapAck> tsns;
    private final List<Long> duplicates;
    private final int bufferLeft;
    private final long cumulativeTSN;

    public SackData(
            long cumulativeTSN,
            List<GapAck> tsns,
            List<Long> duplicates,
            int bufferLeft) {
        this.tsns = tsns;
        this.duplicates = duplicates;
        this.bufferLeft = bufferLeft;
        this.cumulativeTSN = cumulativeTSN;
    }

    public List<GapAck> getTsns() {
        return tsns;
    }

    public List<Long> getDuplicates() {
        return duplicates;
    }

    public int getBufferLeft() {
        return bufferLeft;
    }

    public long getCumulativeTSN() {
        return cumulativeTSN;
    }

    @Override
    public String toString() {
        return "SackData{" +
                "tsns=" + tsns +
                ", duplicates=" + duplicates +
                ", bufferLeft=" + bufferLeft +
                ", cumulativeTSN=" + cumulativeTSN +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SackData sackData = (SackData) o;
        return bufferLeft == sackData.bufferLeft &&
                cumulativeTSN == sackData.cumulativeTSN &&
                Objects.equals(tsns, sackData.tsns) &&
                Objects.equals(duplicates, sackData.duplicates);
    }

    @Override
    public int hashCode() {

        return Objects.hash(tsns, duplicates, bufferLeft, cumulativeTSN);
    }
}
