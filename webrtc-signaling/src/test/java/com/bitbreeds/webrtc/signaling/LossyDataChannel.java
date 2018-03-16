package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.datachannel.DataChannelImpl;

import java.io.IOException;
import java.util.Random;

/**
 * Copyright (c) 11/03/2018, Jonas Waage
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

public class LossyDataChannel extends DataChannelImpl {

    private final Integer packetlossPercentage;
    private final Random random = new Random(System.currentTimeMillis());

    public LossyDataChannel(PeerConnection parent,Integer packetlossPercentage) throws IOException {
        super(parent);
        if(packetlossPercentage < 0 || packetlossPercentage > 100) {
            throw new IllegalArgumentException("Bad packetlossPercentage [0-100] allowed, was "+packetlossPercentage);
        }
        this.packetlossPercentage = packetlossPercentage;
    }

    @Override
    public void putDataOnWire(byte[] out) {
        if(random.nextInt(100) > packetlossPercentage) {
            super.putDataOnWire(out);
        }
    }
}
