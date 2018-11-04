package com.bitbreeds.webrtc.model.webrtc;

import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.peerconnection.PeerConnection;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Copyright (c) 01/04/2018, Jonas Waage
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
public class DataChannel {

    private final int streamId;

    public UUID getId() {
        return id;
    }

    private final UUID id = UUID.randomUUID();

    private final ReliabilityParameters reliabilityParameters;

    private final ConnectionInternalApi connection;

    public DataChannel(ConnectionInternalApi connection, int streamId, ReliabilityParameters reliabilityParameters) {
        this.streamId = streamId;
        this.reliabilityParameters = reliabilityParameters;
        this.connection = connection;
    }

    public Consumer<MessageEvent> onMessage = (i)-> {};

    public Consumer<ErrorEvent> onError = (i)->{};

    public Consumer<CloseEvent> onClose = (i)->{};

    public Consumer<OpenEvent> onOpen = (i)->{};

    public void send(String data) {
        connection.send(data.getBytes(), SCTPPayloadProtocolId.WEBRTC_STRING,streamId,reliabilityParameters.getSctpReliability());
    }

    public void send(byte[] data) {
        connection.send(data, SCTPPayloadProtocolId.WEBRTC_BINARY,streamId,reliabilityParameters.getSctpReliability());
    }

    public PeerConnection getConnection() {
        return connection.getPeerConnection();
    }

    public int getStreamId() {
        return streamId;
    }

    public ReliabilityParameters getReliabilityParameters() {
        return reliabilityParameters;
    }


    @Override
    public String toString() {
        return "DataChannel{" +
                "streamId=" + streamId +
                ", reliabilityParameters=" + reliabilityParameters +
                '}';
    }
}
