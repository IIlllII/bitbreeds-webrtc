package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.model.SCTPHeader;
import com.bitbreeds.webrtc.sctp.model.SCTPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copyright (c) 21/07/16, Jonas Waage
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
 * Utility for common SCTP operations
 */
public class SCTPUtil {

    private final static Logger logger = LoggerFactory.getLogger(SCTPUtil.class);

    /**
     * @param message with checksum set to 0
     * @return Message with computed checksum.
     */
    public static SCTPMessage addChecksum(SCTPMessage message) {
        byte[] bt = message.toBytes();

        SCTPHeader csumHdr = new SCTPHeader(
                message.getHeader().getDestinationPort(),
                message.getHeader().getSourcePort(),
                message.getHeader().getVerificationTag(),
                SignalUtil.computeCRC32c(bt));

        SCTPMessage csumMsg = new SCTPMessage(csumHdr, message.getChunks());
        logger.debug("Output before encode: " + csumMsg );
        return csumMsg;
    }


    /**
     * Commom header with 0d out checksum as it should be before it is computed.
     *
     * TBH this feels like a bunch of crap, why can one not just append the freaking checksum to
     * the end of the message or something.
     *
     * @return common sctp header
     */
    public static SCTPHeader baseHeader(SCTPContext context) {
        return new SCTPHeader(
                context.getSourcePort(),
                context.getDestPort(),
                SignalUtil.bytesToLong(context.getInitiateTag()),
                0);
    }


}
