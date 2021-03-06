package com.bitbreeds.webrtc.peerconnection;

import com.google.gson.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
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
 * Creates JSON from SDP answer to send back to browser
 */
public class IceCandidateToJSON implements Processor {

    private final static Logger logger = LoggerFactory.getLogger(IceCandidateToJSON.class);

    public void process(Exchange exchange) {
        IceCandidate ex = (IceCandidate)exchange.getIn().getBody();
        JsonObject obj = new JsonObject();
        obj.addProperty("candidate",ex.candidateString());
        obj.addProperty("sdpMid","data");
        obj.addProperty("sdpMLineIndex",0);

        String out = obj.toString();
        logger.info("Send candidate: " +out) ;

        exchange.getIn().setBody(out);
    }


}
