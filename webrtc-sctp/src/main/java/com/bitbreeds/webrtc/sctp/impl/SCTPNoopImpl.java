package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.model.webrtc.ConnectionInternalApi;
import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.sctp.impl.buffer.WireRepresentation;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Copyright (c) 29/06/16, Jonas Waage
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
 * Noon SCTP implementation.
 * This is a do nothing implementation so we have something to initalize with.
 */
public class SCTPNoopImpl implements SCTP {
    @Override
    public Optional<WireRepresentation> createHeartBeat() {
        return Optional.empty();
    }

    @Override
    public Optional<WireRepresentation> createSackMessage() {
        return Optional.empty();
    }

    @Override
    public List<WireRepresentation> getMessagesForResend() {
        return Collections.emptyList();
    }

    @Override
    public List<WireRepresentation> handleRequest(byte[] data) {
        return Collections.emptyList();
    }

    @Override
    public List<WireRepresentation> bufferForSending(byte[] data, SCTPPayloadProtocolId id, Integer stream) {
        return Collections.emptyList();
    }

    @Override
    public void runMonitoring() {}

    @Override
    public ConnectionInternalApi getConnection() {
        return null;
    }

    @Override
    public void shutdown() { }
}
