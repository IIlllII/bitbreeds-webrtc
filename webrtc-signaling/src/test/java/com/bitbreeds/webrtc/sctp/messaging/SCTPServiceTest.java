package com.bitbreeds.webrtc.sctp.messaging;

import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.peerconnection.ConnectionImplementation;
import com.bitbreeds.webrtc.sctp.impl.buffer.WireRepresentation;
import com.bitbreeds.webrtc.sctp.model.CRC32c;
import com.bitbreeds.webrtc.sctp.impl.SCTPContext;
import com.bitbreeds.webrtc.sctp.impl.SCTPImpl;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

/**
 * Copyright (c) 25/05/16, Jonas Waage
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
public class SCTPServiceTest {

    private SCTPImpl srv = new SCTPImpl(Mockito.mock(ConnectionImplementation.class));

    long check = 0x83f086a0;

    public SCTPServiceTest() throws IOException {
    }

    @Before
    public void setContext() {
        srv.setContext(new SCTPContext(new byte[] {0x4,0x1,0x3,0x4},5000,5000));
    }

    @Test
    public void getInit() throws DecoderException {

        String inp = "1388138800000000699b219101000056eed2220b0002000001000800e36d1ec9c000000480080009c00fc180820000008002002440cbb942df677d367167427655856025aa291f084401663bb48624229a6b2e4b80040006000100008003000680c10000";
        WireRepresentation rep = srv.handleRequest(Hex.decodeHex(inp.toCharArray())).get(0);
        byte[] out = rep.getPayload();
        System.out.println(Hex.encodeHexString(out));

        CRC32c crc = new CRC32c();
        crc.reset();
        crc.update(out,0,out.length);

        System.out.println("As Bytes: "+Hex.encodeHexString(crc.getValueAsBytes()));

        for(int i =0; i<4; i++) {
            out[8+i] = (byte)0;
        }
        System.out.println("Data: "+Hex.encodeHexString(out));

        Long fing = SignalUtil.computeCRC32c(out);

        System.out.println("Out fingerprint: " + fing);
        System.out.println("Out fingerprint: " + Hex.encodeHexString(SignalUtil.longToFourBytes(fing)));

        String pack = "13881388eed2220b4057612702000000c5b5d8d00002000001000800c5b5d8d00007001c8871afdcecb7558bc4d8d0b21f54a9c73ac7b34200000154e9cfb01b";
        byte[] back = Hex.decodeHex(pack.toCharArray());
        fing = SignalUtil.computeCRC32c(back);
        System.out.println("Out fingerprint: " + fing);
        System.out.println("Out fingerprint: " + Hex.encodeHexString(SignalUtil.longToFourBytes(fing)));


        for(int i =0; i<4; i++) {
            back[8+i] = (byte)0;
        }
        System.out.println("Data: "+Hex.encodeHexString(back));
        fing = SignalUtil.computeCRC32(back);


        System.out.println("Out fingerprint: " + fing);
        System.out.println("Out fingerprint: " + Hex.encodeHexString(SignalUtil.longToFourBytes(fing)));

    }


    @Test
    public void solveInput() throws DecoderException {


        String input = "13881388548b3c9b4941db3e0a0000208f89999e04ebc3eb34da090dd11bd97422f5ff3600000154eeb897d8";
        WireRepresentation representation = srv.handleRequest(Hex.decodeHex(input.toCharArray())).get(0);

    }

    @Test
    public void doInput() throws DecoderException {

        String inp = "1388138800000000699b219101000056eed2220b0002000001000800e36d1ec9c000000480080009c00fc180820000008002002440cbb942df677d367167427655856025aa291f084401663bb48624229a6b2e4b80040006000100008003000680c10000";
        List<WireRepresentation> wr = srv.handleRequest(Hex.decodeHex(inp.toCharArray()));

        String dat = "138813887c42ba97c6c6f17800030023fe68433900000000000000320300000000000000000700006368616e6e656c00";
        List<WireRepresentation> wr2 = srv.handleRequest(Hex.decodeHex(dat.toCharArray()));

    }



    @Test
    public void testDataIntput() throws DecoderException {

        String inp = "1388138800000000699b219101000056eed2220b0002000001000800e36d1ec9c000000480080009c00fc180820000008002002440cbb942df677d367167427655856025aa291f084401663bb48624229a6b2e4b80040006000100008003000680c10000";
        List<WireRepresentation> wr = srv.handleRequest(Hex.decodeHex(inp.toCharArray()));

        String dataInput = "1388138869086b5b2c030e9c0003001c436cf1d40000000d0000003348656c6c6f20576f726c6421";
        String m = "1388 1388 69086b5b 2c030e9c 00 03 001c 436cf1d40000000d000000334865 6c6c6f20576f726c6421";

        List<WireRepresentation> wr2 = srv.handleRequest(Hex.decodeHex(dataInput.toCharArray()));


    }
}
