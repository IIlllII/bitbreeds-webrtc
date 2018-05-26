package com.bitbreeds.webrtc.stun;

import com.bitbreeds.webrtc.common.SignalUtil;

import java.util.Arrays;

/**
 * Copyright (c) 11/05/16, Jonas Waage
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
public class StunHeader {

    private static final int TYPE_END_POSITION = 2;
    private static final int MSG_LGT_END_POSITION = 4;
    private static final int COOKIE_END_POSITION = 8;
    private static final int TRANSID_END_POSITION = 20;

    private final StunRequestTypeEnum type;
    private final int messageLength;
    private final byte[] cookie;
    private final byte[] transactionID;

    /**
     *
     * @param headerBytes
     * @return
     */
    public static StunHeader fromBytes(byte[] headerBytes) {
        if(headerBytes.length != 20) {
            throw new IllegalArgumentException("Header length must be 20, was: " + headerBytes.length);
        }

        StunRequestTypeEnum type = StunRequestTypeEnum.fromBytes(Arrays.copyOfRange(headerBytes,0,TYPE_END_POSITION));
        if(type == StunRequestTypeEnum.NOT_KNOWN) {
            throw new StunError("Unknown message type:" + type);
        }

        int messageLength = SignalUtil.intFromTwoBytes(Arrays.copyOfRange(headerBytes,TYPE_END_POSITION,MSG_LGT_END_POSITION));

        if(messageLength < 0 || messageLength > Short.MAX_VALUE) {
            throw new StunError("Too long messsage, was: " + messageLength);
        }

        byte[] cookie = Arrays.copyOfRange(headerBytes,MSG_LGT_END_POSITION,COOKIE_END_POSITION);
        byte[] transactionID = Arrays.copyOfRange(headerBytes,COOKIE_END_POSITION, TRANSID_END_POSITION);
        return new StunHeader(type,messageLength,cookie,transactionID);
    }


    /**
     *
     * @param type 2 bytes
     * @param messageLength 2 bytes
     * @param cookie 4 bytes
     * @param transactionID 12 bytes
     */
    public StunHeader(
            StunRequestTypeEnum type,
            int messageLength,
            byte[] cookie,
            byte[] transactionID) {
        if(cookie.length != 4) {
            throw new StunError("Cookie must be 4 bytes in size, was:" + cookie.length);
        }
        if(transactionID.length != 12) {
            throw new StunError("TransactionId must be 12 bytes in size, was:" + transactionID.length);
        }
        this.type = type;
        this.messageLength = messageLength;
        this.cookie = cookie;
        this.transactionID = transactionID;
    }


    public StunHeader updateMessageLength(int length) {
        return new StunHeader(
                this.type,
                length,
                this.cookie,
                this.transactionID
        );
    }


    /**
     *
     * @return bytes representing this StunHeader
     */
    public byte[] toBytes() {
        return SignalUtil.joinBytesArrays(
                type.toBytes(),
                SignalUtil.twoBytesFromInt(messageLength),
                cookie,
                transactionID);
    }

    public StunRequestTypeEnum getType() {
        return type;
    }

    public int getMessageLength() {
        return messageLength;
    }

    public byte[] getCookie() {
        return cookie;
    }

    public byte[] getTransactionID() {
        return transactionID;
    }

    @Override
    public String toString() {
        return "StunHeader{" +
                "type=" + type +
                ", messageLength=" + messageLength +
                ", cookie=" + Arrays.toString(cookie) +
                ", transactionID=" + Arrays.toString(transactionID) +
                '}';
    }
}
