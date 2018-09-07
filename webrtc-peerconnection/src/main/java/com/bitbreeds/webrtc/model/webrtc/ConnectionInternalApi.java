package com.bitbreeds.webrtc.model.webrtc;


/*
 * Copyright (c) 01/03/2017, Jonas Waage
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


import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.sctp.impl.SCTPReliability;

/**
 * The Connection interface that SCTP use internally
 */
public interface ConnectionInternalApi {

    /**
     *
     * @param deliverable present data to user api
     */
    void presentToUser(Deliverable deliverable);

    /**
     *
     * @param data send data from external API over DC
     * @param id protocol id
     * @param streamId sctp stream
     * @param partialReliability reliability parameters
     */
    void send(byte[] data, SCTPPayloadProtocolId id, int streamId, SCTPReliability partialReliability);

    /**
     *
     * @param data send data over UDP
     */
    void putDataOnWire(byte[] data);

    /**
     *
     * @return port used by connection
     */
    int getPort();

    /**
     *
     * @param data received sctp message
     */
    void processReceivedMessage(byte[] data);

    /**
     * Perform controlled shutdown
     */
    void close();

    /**
     * No more packets can be sent after this has been called.
     * This should only be performed as part of a controlled shutdown or abort.
     */
    void closeConnection();

}
