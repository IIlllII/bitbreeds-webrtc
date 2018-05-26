package com.bitbreeds.webrtc.model.webrtc;

/**
 * Copyright (c) 13/07/16, Jonas Waage
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


import java.util.Arrays;

/**
 * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-protocol-09#section-5.1">WebRTC Datachannel spec</a>
 */
public enum DataChannelType {

    DATA_CHANNEL_RELIABLE(0x00),
    DATA_CHANNEL_RELIABLE_UNORDERED(0x80),
    DATA_CHANNEL_PARTIAL_RELIABLE_REXMIT (0x01),
    DATA_CHANNEL_PARTIAL_RELIABLE_REXMIT_UNORDERED (0x81),
    DATA_CHANNEL_PARTIAL_RELIABLE_TIMED (0x02),
    DATA_CHANNEL_PARTIAL_RELIABLE_TIMED_UNORDERED (0x82);

    private final int type;

    DataChannelType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static DataChannelType fromInt(int bt) {
        return Arrays.stream(values())
                .filter(i -> i.type == bt)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown DataChannel type"));
    }
}
