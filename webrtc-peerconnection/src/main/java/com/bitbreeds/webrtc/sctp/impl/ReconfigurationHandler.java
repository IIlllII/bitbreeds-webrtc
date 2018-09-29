package com.bitbreeds.webrtc.sctp.impl;/*
 *
 * Copyright (c) 14/07/2018, Jonas Waage
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

import com.bitbreeds.webrtc.sctp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * <a href="https://tools.ietf.org/html/rfc6525#section-3.1">reconfig rfc</a>
 */
public class ReconfigurationHandler implements MessageHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public Optional<SCTPMessage> handleMessage(SCTP handler, SCTPContext ctx, SCTPHeader header, SCTPChunk data) {
        SCTPAttribute incoming = data.getVariable().get(SCTPAttributeType.INCOMING_SSN_RESET_REQUEST);
        SCTPAttribute outgoing = data.getVariable().get(SCTPAttributeType.OUTGOING_SSN_RESET_REQUEST);
        return Optional.empty();
    }
    
}
