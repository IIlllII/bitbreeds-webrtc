package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.peerconnection.IceCandidate;
import gov.nist.javax.sdp.fields.AttributeField;
import gov.nist.javax.sdp.fields.MediaField;
import org.junit.Test;

import javax.sdp.*;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;

import static org.junit.Assert.assertEquals;

/*
 * Copyright (c) 05/02/2018, Jonas Waage
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
public class SDPTest {

    @Test
    public void testSdpGen() {

        SessionDescription sdp = SDPUtil.createSDP(Collections.emptyList(),new IceCandidate(BigInteger.valueOf(123L),BigInteger.ONE,35400,"127.0.0.1",123,"host","udp"),"user","pwd","AA","data",true);
        System.out.println(sdp.toString());

    }

    @Test
    public void testSdpRead() throws SdpException {

        SessionDescription sdp = SDPUtil.createSDP(Collections.emptyList(),new IceCandidate(BigInteger.valueOf(123L),BigInteger.ONE,35400,"127.0.0.1",123,"host","udp"),"user","pwd","AA","data",true);
        System.out.println(sdp.toString());

        SdpFactory factory = SdpFactory.getInstance();

        String data = sdp.toString().replaceAll("UDP/DTLS/SCTP","DTLS/SCTP");

        SessionDescription parsed = factory.createSessionDescription(data);

        MediaDescription med = (MediaDescription)parsed.getMediaDescriptions(true).get(0);
        String pwd = med.getAttribute("ice-pwd");
        String user = med.getAttribute("ice-ufrag");
        String mid = med.getAttribute("mid");

        ArrayList<String> list = SDPUtil.getCandidates(med.getAttributes(true));

        assertEquals(1,list.size());
    }




}
