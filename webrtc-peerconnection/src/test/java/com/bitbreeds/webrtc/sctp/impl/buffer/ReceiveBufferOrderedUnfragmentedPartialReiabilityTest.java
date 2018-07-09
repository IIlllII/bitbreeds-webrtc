package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.model.sctp.SackUtil;
import com.bitbreeds.webrtc.model.webrtc.Deliverable;
import com.bitbreeds.webrtc.sctp.error.DroppedDataException;
import com.bitbreeds.webrtc.sctp.impl.model.ReceivedData;
import com.bitbreeds.webrtc.sctp.model.SCTPOrderFlag;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
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
public class ReceiveBufferOrderedUnfragmentedPartialReiabilityTest {

    private ReceivedData makeDsStream1(long tsn, int ssn, byte[] data) {
        return new ReceivedData(tsn,
                1,
                ssn,
                SCTPOrderFlag.ORDERED_UNFRAGMENTED,
                SCTPPayloadProtocolId.WEBRTC_BINARY,data);
    }

    private ReceivedData makeDsStream2(long tsn, int ssn, byte[] data) {
        return new ReceivedData(tsn,
                2,
                ssn,
                SCTPOrderFlag.ORDERED_UNFRAGMENTED,
                SCTPPayloadProtocolId.WEBRTC_BINARY,data);
    }


    @Test
    public void testReceiveOutOfOrderMissing0ne() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        buffer.store(makeDsStream1(1,0,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(2,1,new byte[]{0,1,2}));

        buffer.store(makeDsStream1(4,3,new byte[]{0,1,2}));

        buffer.getMessagesForDelivery();
        buffer.getSackDataToSend();

        buffer.store(makeDsStream1(3,2,new byte[]{0,1,2}));

        ForwardAccResult del = buffer.receiveForwardAckPoint(4);
        assertEquals(2,del.getToDeliver().size());

        SackData sack = del.getSackData();
        assertEquals(4,sack.getCumulativeTSN());
        assertEquals(Collections.emptyList(),sack.getTsns());
        assertEquals(sack.getDuplicates(),Collections.emptyList());
    }


    @Test(expected = DroppedDataException.class)
    public void testReceiveOutOfOrderDroppingOrderedData() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        buffer.store(makeDsStream1(1,0,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(2,1,new byte[]{0,1,2}));

        buffer.store(makeDsStream1(4,3,new byte[]{0,1,2}));

        buffer.getMessagesForDelivery();
        buffer.getSackDataToSend();

        buffer.receiveForwardAckPoint(4);
    }

}
