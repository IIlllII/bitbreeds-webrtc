package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.model.webrtc.Deliverable;
import com.bitbreeds.webrtc.sctp.impl.SCTPReliability;
import com.bitbreeds.webrtc.sctp.impl.model.ReceivedData;
import com.bitbreeds.webrtc.sctp.model.SCTPOrderFlag;
import org.junit.Test;
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
public class ReceiveBufferDuplicateTest {

    private ReceivedData makeFrag(long tsn, byte[] data, SCTPOrderFlag flag) {
        return new ReceivedData(tsn,
                0,
                0,
                flag,
                SCTPPayloadProtocolId.WEBRTC_BINARY,
                SCTPReliability.createOrdered(),
                data);
    }

    @Test
    public void testReceiveFragmented() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        ReceivedData start = makeFrag(1,new byte[]{0,0,0},SCTPOrderFlag.UNORDERED_UNFRAGMENTED);
        ReceivedData mid = makeFrag(2,new byte[]{0,1,1},SCTPOrderFlag.UNORDERED_UNFRAGMENTED);
        ReceivedData mid2 = makeFrag(2,new byte[]{0,0,2},SCTPOrderFlag.UNORDERED_UNFRAGMENTED);
        ReceivedData end = makeFrag(4,new byte[]{3,1,1},SCTPOrderFlag.UNORDERED_UNFRAGMENTED);
        ReceivedData nr3 = makeFrag(3,new byte[]{3,1,1},SCTPOrderFlag.UNORDERED_UNFRAGMENTED);
        ReceivedData nr4 = makeFrag(1,new byte[]{3,1,1},SCTPOrderFlag.UNORDERED_UNFRAGMENTED);

        buffer.store(start);
        buffer.store(mid2);
        buffer.store(mid);

        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(2,del.size());

        SackData sack = buffer.getSackDataToSend();
        assertEquals(2,sack.getCumulativeTSN());

        del = buffer.getMessagesForDelivery();
        assertEquals(0,del.size());

        buffer.store(nr3);
        buffer.store(nr4);
        buffer.store(end);
        List<Deliverable> del2 = buffer.getMessagesForDelivery();
        assertEquals(2,del2.size());

        del2 = buffer.getMessagesForDelivery();
        assertEquals(0,del2.size());
    }






}
