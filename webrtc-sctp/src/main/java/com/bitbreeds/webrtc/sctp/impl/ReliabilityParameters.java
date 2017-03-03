package com.bitbreeds.webrtc.sctp.impl;

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


import org.apache.commons.codec.binary.Hex;

/**
 * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-protocol-09#section-8.2.1">datachannel spec</a>
 */
public class ReliabilityParameters {

    private final int parameter; //either number of resends or time to resend in ms
    private final DataChannelType type;
    private final DataChannelPriority priority;
    private final byte[] label;
    private final byte[] protocol;

    public ReliabilityParameters(
            int parameter,
            DataChannelType type,
            DataChannelPriority priority,
            byte[] label,
            byte[] protocol) {

        this.parameter = parameter;
        this.type = type;
        this.priority = priority;
        this.label = label;
        this.protocol = protocol;
    }

    public int getParameter() {
        return parameter;
    }

    public DataChannelType getType() {
        return type;
    }

    public DataChannelPriority getPriority() {
        return priority;
    }

    public byte[] getLabel() {
        return label;
    }

    public byte[] getProtocol() {
        return protocol;
    }

    @Override
    public String toString() {
        return "ReliabilityParameters{" +
                "parameter=" + parameter +
                ", type=" + type +
                ", priority=" + priority +
                ", label=" + new String(label) +
                ", protocol=" + Hex.encodeHexString(protocol) +
                '}';
    }
}
