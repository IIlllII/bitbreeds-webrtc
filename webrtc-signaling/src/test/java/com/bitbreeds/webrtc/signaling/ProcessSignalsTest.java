package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.peerconnection.IceCandidate;
import com.bitbreeds.webrtc.peerconnection.ProcessSignals;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

import javax.sdp.MediaDescription;

import static org.junit.Assert.assertEquals;

/*
 * Copyright (c) 26/04/16, Jonas Waage
 */
public class ProcessSignalsTest {

    String candidate = "{\"candidate\":\"candidate:0 1 UDP 2122252543 192.168.1.100 50793 typ host\",\"sdpMid\":\"sdparta_0\",\"sdpMLineIndex\":0}";

    String offerSdp = "{\"type\":\"offer\",\"sdp\":\"v=0\r\no=mozilla...THIS_IS_SDPARTA-45.0.2 3569146212985342561 0 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\na=sendrecv\r\na=fingerprint:sha-256 99:45:B1:94:7E:97:AE:F2:A5:75:86:89:B5:AD:06:BB:63:02:FA:05:04:B2:83:1B:52:C9:EF:0E:61:8F:38:73\r\na=ice-options:trickle\r\na=msid-semantic:WMS *\r\nm=application 9 DTLS/SCTP 5000\r\nc=IN IP4 0.0.0.0\r\na=sendrecv\r\na=ice-pwd:c490fef46f74bdbe64edd636bc49a259\r\na=ice-ufrag:64dc2277\r\na=mid:sdparta_0\r\na=sctpmap:5000 webrtc-peerconnection 256\r\na=setup:actpass\r\na=ssrc:1765902606 cname:{57daa76c-5046-3946-9db8-b50bd1374273}\r\n\"}";

    String answerSdp = "{\"type\":\"answer\",\"sdp\":\"v=0\r\no=mozilla...THIS_IS_SDPARTA-45.0.2 3569146212985342561 0 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\na=sendrecv\r\na=fingerprint:sha-256 99:45:B1:94:7E:97:AE:F2:A5:75:86:89:B5:AD:06:BB:63:02:FA:05:04:B2:83:1B:52:C9:EF:0E:61:8F:38:73\r\na=ice-options:trickle\r\na=msid-semantic:WMS *\r\nm=application 9 DTLS/SCTP 5000\r\nc=IN IP4 0.0.0.0\r\na=sendrecv\r\na=ice-pwd:c490fef46f74bdbe64edd636bc49a259\r\na=ice-ufrag:64dc2277\r\na=mid:sdparta_0\r\na=sctpmap:5000 webrtc-peerconnection 256\r\na=setup:actpass\r\na=ssrc:1765902606 cname:{57daa76c-5046-3946-9db8-b50bd1374273}\r\n\"}";


    ProcessSignals processor = new ProcessSignals();

    @Test
    public void testCandidateParsing() throws Exception {
        Exchange ex = new DefaultExchange(new DefaultCamelContext());
        ex.getIn().setBody(candidate);
        processor.process(ex);
        assertEquals(ex.getIn().getBody().getClass(),IceCandidate.class);
    }


    @Test
    public void testSDPParsing() throws Exception {
        Exchange ex = new DefaultExchange(new DefaultCamelContext());
        ex.getIn().setBody(offerSdp);
        processor.process(ex);
        assertEquals(ex.getIn().getBody().getClass(),Offer.class);
    }


    @Test
    public void testSDPParseAnswer() throws Exception {
        Exchange ex = new DefaultExchange(new DefaultCamelContext());
        ex.getIn().setBody(answerSdp);
        processor.process(ex);
        assertEquals(ex.getIn().getBody().getClass(),Answer.class);
    }


    @Test
    public void testSDPParseOffer() throws Exception {
        Exchange ex = new DefaultExchange(new DefaultCamelContext());
        ex.getIn().setBody(offerSdp);
        processor.process(ex);
        assertEquals(ex.getIn().getBody().getClass(),Offer.class);

        Offer offer = (Offer)ex.getIn().getBody();

        MediaDescription mediaDescription = (MediaDescription) offer.getSdp().getMediaDescriptions(true).get(0);

        String icePwd = mediaDescription.getAttribute("ice-pwd");
        String iceUfrag = mediaDescription.getAttribute("ice-ufrag");
        String fingerprint = offer.getSdp().getAttribute("fingerprint");

        assertEquals(icePwd,"c490fef46f74bdbe64edd636bc49a259");
        assertEquals(iceUfrag,"64dc2277");
        assertEquals(fingerprint,"sha-256 99:45:B1:94:7E:97:AE:F2:A5:75:86:89:B5:AD:06:BB:63:02:FA:05:04:B2:83:1B:52:C9:EF:0E:61:8F:38:73");
    }

}
