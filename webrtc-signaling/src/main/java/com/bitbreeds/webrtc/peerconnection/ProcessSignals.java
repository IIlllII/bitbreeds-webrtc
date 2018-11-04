package com.bitbreeds.webrtc.peerconnection;

import com.bitbreeds.webrtc.signaling.Answer;
import com.bitbreeds.webrtc.signaling.Offer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;
import java.math.BigInteger;

/*
 * Copyright (c) 26/04/16, Jonas Waage
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

/**
 * Process browser JSON contains SDP or candidates
 */
public class ProcessSignals implements Processor {

    private Gson gson = new Gson();
    private final static Logger logger = LoggerFactory.getLogger(ProcessSignals.class);

    public void process(Exchange exchange) throws Exception {
        String ex = (String)exchange.getIn().getBody();

        /*
         * NIST SDP implementation can not deal with protocol 'UDP/DTLS/SCTP', mitigating.
         * See <a href="https://tools.ietf.org/html/draft-ietf-mmusic-sctp-sdp-26"/>
         */
        String out = ex.replace("UDP/DTLS/SCTP","DTLS/SCTP");

        JsonObject el = gson.fromJson(out,JsonObject.class);

        SdpFactory factory = SdpFactory.getInstance();

        if(el.get("type") != null) {
            if("offer".equalsIgnoreCase(el.get("type").getAsString())) {
                SessionDescription sdp = factory.createSessionDescription(el.get("sdp").getAsString());
                logger.info("SDP" + sdp);
                exchange.getIn().setBody(new Offer(sdp));
            }
            else if("answer".equalsIgnoreCase(el.get("type").getAsString())) {
                SessionDescription sdp = factory.createSessionDescription(el.get("sdp").getAsString());
                logger.info("SDP" + sdp);
                exchange.getIn().setBody(new Answer(sdp));
            }
        }
        else if(el.get("candidate") != null) {

            String iceCandidate = el.get("candidate").getAsString();
            String[] ice = iceCandidate.split(" ");

            IceCandidate can = new IceCandidate(
                    BigInteger.valueOf(Long.valueOf(ice[0].split(":")[1])),
                    Integer.valueOf(ice[5]),
                    ice[4],
                    Long.valueOf(ice[3]));
            exchange.getIn().setBody(can);
        }
        else {
            throw new UnsupportedOperationException("unknown type");
        }

    }


}
