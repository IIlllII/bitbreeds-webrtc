package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.model.sctp.SackUtil;
import com.bitbreeds.webrtc.model.webrtc.Deliverable;
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
public class ReceiveBufferOrderedUnfragmentedTest {

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
    public void testReceiveOneOrdered() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        ReceivedData ds = makeDsStream1(1,0,new byte[]{0,1,2});
        buffer.store(ds);

        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(Collections.singletonList(new Deliverable(ds.getPayload(),0,del.get(0).getStreamId(),del.get(0).getProtocolId())),del);

        SackData sack = buffer.getSackDataToSend();
        assertEquals(1,sack.getCumulativeTSN());
        assertEquals(sack.getTsns(),Collections.emptyList());
        assertEquals(sack.getDuplicates(),Collections.emptyList());
    }



    @Test
    public void testReceiveManyOrdered() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        buffer.store(makeDsStream1(1,0,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(2,1,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(3,2,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(4,3,new byte[]{0,1,2}));


        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(4,del.size());

        SackData sack = buffer.getSackDataToSend();
        assertEquals(4,sack.getCumulativeTSN());
        assertEquals(sack.getTsns(),Collections.emptyList());
        assertEquals(sack.getDuplicates(),Collections.emptyList());
    }


    @Test
    public void testReceiveOutOfOrderMissing0ne() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        buffer.store(makeDsStream1(1,0,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(2,1,new byte[]{0,1,2}));

        buffer.store(makeDsStream1(4,3,new byte[]{0,1,2}));

        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(2,del.size());

        SackData sack = buffer.getSackDataToSend();
        assertEquals(2,sack.getCumulativeTSN());
        assertEquals( SackUtil.getGapAckList(0L,Stream.of(2L).collect(Collectors.toSet())),sack.getTsns());
        assertEquals(sack.getDuplicates(),Collections.emptyList());
    }


    @Test
    public void testReceiveOutOfOrderDifferentStream() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        buffer.store(makeDsStream1(1,0,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(2,1,new byte[]{0,1,2}));

        buffer.store(makeDsStream2(4,0,new byte[]{0,1,2}));
        buffer.store(makeDsStream2(5,1,new byte[]{0,1,2}));

        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(4,del.size());

        SackData sack = buffer.getSackDataToSend();
        assertEquals(2,sack.getCumulativeTSN());
        assertEquals( SackUtil.getGapAckList(0L,Stream.of(2L,3L).collect(Collectors.toSet())),sack.getTsns());
        assertEquals(sack.getDuplicates(),Collections.emptyList());
    }


    @Test
    public void testReceiveDuplicate() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        buffer.store(makeDsStream1(1,0,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(2,1,new byte[]{0,1,2}));

        buffer.store(makeDsStream1(2,1,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(1,0,new byte[]{0,1,2}));

        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(2,del.size());

        SackData sack = buffer.getSackDataToSend();
        assertEquals(2,sack.getCumulativeTSN());
        assertEquals(Collections.emptyList(),sack.getTsns());
        assertEquals(Stream.of(2L,1L).collect(Collectors.toList()),sack.getDuplicates());
    }


    @Test
    public void testWrapBuffer() {

        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        buffer.store(makeDsStream1(1,0,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(2,1,new byte[]{0,1,2}));

        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(2,del.size());

        SackData sack = buffer.getSackDataToSend();
        assertEquals(2,sack.getCumulativeTSN());

        buffer.store(makeDsStream1(3,2,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(4,3,new byte[]{0,1,2}));

        List<Deliverable> del2 = buffer.getMessagesForDelivery();
        assertEquals(2,del2.size());

        SackData sack2 = buffer.getSackDataToSend();
        assertEquals(4,sack2.getCumulativeTSN());

        buffer.store(makeDsStream1(5,4,new byte[]{0,0,0}));
        buffer.store(makeDsStream1(6,5,new byte[]{1,1,1}));
        buffer.store(makeDsStream1(7,6,new byte[]{2,2,2}));
        buffer.store(makeDsStream1(8,7,new byte[]{3,3,3}));

        List<Deliverable> del3 = buffer.getMessagesForDelivery();
        assertEquals(4,del3.size());

        List<byte[]> data = del3.stream()
                .map(Deliverable::getData)
                .collect(Collectors.toList());

        assertArrayEquals(new byte[]{0,0,0},data.get(0));
        assertArrayEquals(new byte[]{1,1,1},data.get(1));
        assertArrayEquals(new byte[]{2,2,2},data.get(2));
        assertArrayEquals(new byte[]{3,3,3},data.get(3));

        SackData sack3 = buffer.getSackDataToSend();

        assertEquals(8,sack3.getCumulativeTSN());
        assertEquals(Collections.emptyList(),sack3.getTsns());
        assertEquals(Collections.emptyList(),sack3.getDuplicates());
    }




    @Test(expected = OutOfBufferSpaceError.class)
    public void testWrapBufferNoClearFull() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100);

        buffer.setInitialTSN(1);

        buffer.store(makeDsStream1(1,1,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(2,2,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(3,3,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(4,4,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(5,5,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(6,6,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(7,7,new byte[]{0,1,2}));
        buffer.store(makeDsStream1(8,8,new byte[]{0,1,2}));
    }




}
