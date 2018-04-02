package com.bitbreeds.webrtc.model.webrtc;


/**
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

/**
 * The Connection interface that SCTP use internally
 */
public interface ConnectionInternalApi {

    void runOpen();

    void handleMessage(Deliverable deliverable);

    void runOnMessageUnordered(byte[] data);

    void runOnMessageOrdered(byte[] data);

    void runOnError(final Exception err);

    void send(byte[] data);

    void send(byte[] data, SCTPPayloadProtocolId id, int streamId);

    void send(String data);

    void putDataOnWire(byte[] data);

    int getPort();

}
