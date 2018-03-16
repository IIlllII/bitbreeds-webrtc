package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.DataChannel;
import com.bitbreeds.webrtc.common.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.sctp.impl.buffer.WireRepresentation;
import com.bitbreeds.webrtc.sctp.model.SCTPMessage;

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
 * Creation and handling of SCTP messages.
 */
public interface SCTP {

    /**
     *
     * @return a SCTP heartbeat message
     */
    Optional<WireRepresentation> createHeartBeat();

    /**
     *
     * @return SCTP SACK
     */
    Optional<WireRepresentation> createSackMessage();

    /**
     *
     * @return messages for resend
     */
    List<WireRepresentation> getMessagesForResend();

    /**
     * @param data the request
     * @return possible return message for handling
     */
    List<WireRepresentation> handleRequest(byte[] data);

    /**
     * @param data the rawdata to create a message
     * @param id protocol
     * @param stream if set, this message is sent ordered on this stream
     * @return messages that SCTP means should be sent now
     */
    List<WireRepresentation> bufferForSending(byte[] data, SCTPPayloadProtocolId id, Integer stream);

    /**
     * Log useful monitoring values.
     */
    void runMonitoring();

    /**
     *
     * @return datachannel
     */
    DataChannel getDataChannel();
}
