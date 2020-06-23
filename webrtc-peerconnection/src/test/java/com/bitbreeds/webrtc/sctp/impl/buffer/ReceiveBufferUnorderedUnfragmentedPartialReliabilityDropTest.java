package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.model.webrtc.Deliverable;
import com.bitbreeds.webrtc.sctp.impl.SCTPReliability;
import com.bitbreeds.webrtc.sctp.impl.model.ReceivedData;
import com.bitbreeds.webrtc.sctp.model.SCTPOrderFlag;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Copyright (c) 20/02/2018, Jonas Waage
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
public class ReceiveBufferUnorderedUnfragmentedPartialReliabilityDropTest {

    private ReceivedData makeDs(long tsn, byte[] data) {
        return new ReceivedData(tsn,
                0,
                0,
                SCTPOrderFlag.UNORDERED_UNFRAGMENTED,
                SCTPPayloadProtocolId.WEBRTC_BINARY,
                SCTPReliability.createUnordered(),
                data);
    }

    @Test
    public void testReceiveOutOfOrderMissing0neReceiceForwardAck() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        buffer.store(makeDs(1,new byte[]{0,1,2}));
        buffer.store(makeDs(2,new byte[]{0,1,2}));

        buffer.store(makeDs(4,new byte[]{0,1,2}));

        List<Deliverable> todel = buffer.getMessagesForDelivery();
        assertEquals(3,todel.size());
        SackData sackData = buffer.getSackDataToSend();

        ForwardAccResult del = buffer.receiveForwardAckPoint(4);
        assertEquals(0,del.getToDeliver().size());

        SackData sack = del.getSackData();
        assertEquals(4,sack.getCumulativeTSN());
        assertEquals(Collections.emptyList(),sack.getTsns());
        assertEquals(sack.getDuplicates(),Collections.emptyList());
    }


}
