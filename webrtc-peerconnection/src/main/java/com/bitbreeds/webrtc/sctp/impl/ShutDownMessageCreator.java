package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.model.*;

import java.util.Collections;
import java.util.HashMap;

/**
 * Copyright (c) 03/04/2018, Jonas Waage
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
public class ShutDownMessageCreator {


    SCTPMessage createShutDown(SCTPHeader header,long cumulativeTSN) {

        byte[] data = SignalUtil.longToFourBytes(cumulativeTSN);

        SCTPChunk sack = new SCTPChunk(
                SCTPMessageType.SHUTDOWN,
                SCTPOrderFlag.fromValue((byte)0),
                4  + data.length,
                Collections.emptyMap(),
                new HashMap<>(),
                SignalUtil.padToMultipleOfFour(data)
        );

        return new SCTPMessage(header, Collections.singletonList(sack));
    }

    SCTPMessage createShutDownAck(SCTPHeader header) {
        return createEmpty(SCTPMessageType.SHUTDOWN_COMPLETE,header);
    }

    SCTPMessage createShutDownComp(SCTPHeader header) {
        return createEmpty(SCTPMessageType.SHUTDOWN_ACK,header);
    }

    SCTPMessage createAbort(SCTPHeader header) {
        return createEmpty(SCTPMessageType.ABORT,header);
    }

    private SCTPMessage createEmpty(SCTPMessageType messageType,SCTPHeader header) {
        SCTPChunk sack = new SCTPChunk(
                messageType,
                SCTPOrderFlag.fromValue((byte)0),
                4,
                Collections.emptyMap(),
                Collections.emptyMap(),
                new byte[0]
        );

        return new SCTPMessage(header, Collections.singletonList(sack));
    }

}
