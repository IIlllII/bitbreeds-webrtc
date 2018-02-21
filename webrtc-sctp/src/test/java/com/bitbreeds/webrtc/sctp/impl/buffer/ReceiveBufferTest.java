package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.common.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.sctp.impl.DataStorage;
import com.bitbreeds.webrtc.sctp.model.SCTPOrderFlag;
import org.junit.Test;

import java.util.Arrays;
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
public class ReceiveBufferTest {

    private DataStorage makeDs(long tsn,byte[] data) {
        return new DataStorage(tsn,
                0,
                0,
                SCTPOrderFlag.UNORDERED_UNFRAGMENTED,
                SCTPPayloadProtocolId.WEBRTC_BINARY,data);
    }

    @Test
    public void testReceiveOneUnordered() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100,1);

        DataStorage ds = makeDs(2,new byte[]{0,1,2});
        buffer.store(ds);

        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(Collections.singletonList(new Deliverable(ds)),del);

        SackData sack = buffer.getSackDataToSend();
        assertEquals(2,sack.getCumulativeTSN());
        assertEquals(sack.getTsns(),Collections.emptySet());
        assertEquals(sack.getDuplicates(),Collections.emptyList());

    }



    @Test
    public void testReceiveManyUnordered() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100,1);

        buffer.store(makeDs(2,new byte[]{0,1,2}));
        buffer.store(makeDs(3,new byte[]{0,1,2}));
        buffer.store(makeDs(4,new byte[]{0,1,2}));
        buffer.store(makeDs(5,new byte[]{0,1,2}));


        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(4,del.size());

        SackData sack = buffer.getSackDataToSend();
        assertEquals(5,sack.getCumulativeTSN());
        assertEquals(sack.getTsns(),Collections.emptySet());
        assertEquals(sack.getDuplicates(),Collections.emptyList());
    }


    @Test
    public void testReceiveOutOfOrderMissing0ne() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100,1);

        buffer.store(makeDs(2,new byte[]{0,1,2}));
        buffer.store(makeDs(3,new byte[]{0,1,2}));

        buffer.store(makeDs(5,new byte[]{0,1,2}));

        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(3,del.size());

        SackData sack = buffer.getSackDataToSend();
        assertEquals(3,sack.getCumulativeTSN());
        assertEquals( Stream.of(5L).collect(Collectors.toSet()),sack.getTsns());
        assertEquals(sack.getDuplicates(),Collections.emptyList());

    }


    @Test
    public void testReceiveDuplicate() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100,1);

        buffer.store(makeDs(2,new byte[]{0,1,2}));
        buffer.store(makeDs(3,new byte[]{0,1,2}));

        buffer.store(makeDs(3,new byte[]{0,1,2}));
        buffer.store(makeDs(2,new byte[]{0,1,2}));

        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(2,del.size());

        SackData sack = buffer.getSackDataToSend();
        assertEquals(3,sack.getCumulativeTSN());
        assertEquals(Collections.emptySet(),sack.getTsns());
        assertEquals(Stream.of(3L,2L).collect(Collectors.toList()),sack.getDuplicates());
    }


    @Test
    public void testWrapBuffer() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100,1);

        buffer.store(makeDs(2,new byte[]{0,1,2}));
        buffer.store(makeDs(3,new byte[]{0,1,2}));

        List<Deliverable> del = buffer.getMessagesForDelivery();
        assertEquals(2,del.size());

        SackData sack = buffer.getSackDataToSend();
        assertEquals(3,sack.getCumulativeTSN());

        buffer.store(makeDs(4,new byte[]{0,1,2}));
        buffer.store(makeDs(5,new byte[]{0,1,2}));

        List<Deliverable> del2 = buffer.getMessagesForDelivery();
        assertEquals(2,del2.size());

        SackData sack2 = buffer.getSackDataToSend();
        assertEquals(5,sack2.getCumulativeTSN());

        buffer.store(makeDs(6,new byte[]{0,0,0}));
        buffer.store(makeDs(7,new byte[]{1,1,1}));
        buffer.store(makeDs(8,new byte[]{2,2,2}));
        buffer.store(makeDs(9,new byte[]{3,3,3}));

        List<Deliverable> del3 = buffer.getMessagesForDelivery();
        assertEquals(4,del3.size());

        List<byte[]> data = del3.stream()
                .map(i->i.getData().getPayload())
                .collect(Collectors.toList());

        assertArrayEquals(new byte[]{0,0,0},data.get(0));
        assertArrayEquals(new byte[]{1,1,1},data.get(1));
        assertArrayEquals(new byte[]{2,2,2},data.get(2));
        assertArrayEquals(new byte[]{3,3,3},data.get(3));

        SackData sack3 = buffer.getSackDataToSend();

        assertEquals(9,sack3.getCumulativeTSN());
        assertEquals(Collections.emptySet(),sack3.getTsns());
        assertEquals(Collections.emptyList(),sack3.getDuplicates());
    }




    @Test(expected = OutOfBufferSpaceError.class)
    public void testWrapBufferNoClearFull() {
        ReceiveBuffer buffer = new ReceiveBuffer(6,100,1);

        buffer.store(makeDs(2,new byte[]{0,1,2}));
        buffer.store(makeDs(3,new byte[]{0,1,2}));
        buffer.store(makeDs(4,new byte[]{0,1,2}));
        buffer.store(makeDs(5,new byte[]{0,1,2}));
        buffer.store(makeDs(6,new byte[]{0,1,2}));
        buffer.store(makeDs(7,new byte[]{0,1,2}));
        buffer.store(makeDs(8,new byte[]{0,1,2}));
        buffer.store(makeDs(9,new byte[]{0,1,2}));

    }
}
