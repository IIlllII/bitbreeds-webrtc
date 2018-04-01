package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.model.sctp.SackUtil;
import com.bitbreeds.webrtc.common.SetUtil;
import com.bitbreeds.webrtc.sctp.impl.model.SendData;
import com.bitbreeds.webrtc.sctp.model.SCTPOrderFlag;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Copyright (c) 26/02/2018, Jonas Waage
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
public class SendBufferTest {


    private List<SendData> makeData(long tsn) {
        return Collections.singletonList(new SendData(tsn,0,0, SCTPOrderFlag.UNORDERED_UNFRAGMENTED, SCTPPayloadProtocolId.WEBRTC_BINARY,
                new byte[] {0,0,0,0,0,0,1,1,1,1,1}));
    }

    @Test
    public void sendMessageReceiveSack() {
        SendBuffer buffer = new SendBuffer(1000);

        buffer.initializeRemote(1000,1);

        buffer.buffer(makeData(1));
        buffer.buffer(makeData(2));
        buffer.buffer(makeData(3));

        List<BufferedSent> toSend = buffer.getDataToSend();

        assertEquals(3,toSend.size());
        assertEquals(1,toSend.get(0).getTsn());
        assertEquals(2,toSend.get(1).getTsn());
        assertEquals(3,toSend.get(2).getTsn());
    }


    @Test
    public void sendSeveralReceivePartialSack() {
        SendBuffer buffer = new SendBuffer(1000);

        buffer.initializeRemote(1000,1);

        buffer.buffer(makeData(1));
        buffer.buffer(makeData(2));
        buffer.buffer(makeData(3));

        List<BufferedSent> toSend = buffer.getDataToSend();

        assertEquals(3,toSend.size());
        assertEquals(1,toSend.get(0).getTsn());
        assertEquals(2,toSend.get(1).getTsn());
        assertEquals(3,toSend.get(2).getTsn());

        SackData sack = new SackData(1L, SackUtil.getGapAckList(SetUtil.newHashSet(3L)), Collections.emptyList(),750);
        buffer.receiveSack(sack);

        assertEquals(1,buffer.getInflightSize());

        buffer.buffer(makeData(4));

        List<BufferedSent> nextSend = buffer.getDataToSend();
        assertEquals(1,nextSend.size());
    }


    @Test(expected = OutOfBufferSpaceError.class)
    public void outOfBufferTest() {
        SendBuffer buffer = new SendBuffer(200);
        buffer.initializeRemote(1000,1);

        for(int i=0; i<100; i++) {
            buffer.buffer(makeData(i+1));
        }
    }

}
