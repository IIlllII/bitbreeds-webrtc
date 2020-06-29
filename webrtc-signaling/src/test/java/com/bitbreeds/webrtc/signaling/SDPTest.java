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
