package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.websocket.WebsocketConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.jndi.JndiContext;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Copyright (c) 16/04/16, Jonas Waage
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
 * Simple WSS based signaling used for <b>TESTING</b>
 */
public class SimpleSignalingExample {

    private static KeyStoreInfo keyStoreInfo = new KeyStoreInfo(
                            "./src/test/resources/ws2.jks",
                                    "websocket",
                                    "websocket");

    /**
     * Application entry point
     * @param args application arguments
     * @throws Exception
     */
    public static void main(String... args) throws Exception {

        System.setProperty("com.bitbreeds.keystore","./src/test/resources/ws2.jks");
        System.setProperty("com.bitbreeds.keystore.alias","websocket");
        System.setProperty("com.bitbreeds.keystore.pass","websocket");

        JndiRegistry reg = new JndiRegistry(new JndiContext());

        reg.bind("sslContextParameters",sslParameters());

        CamelContext ctx = new DefaultCamelContext(reg);
        ctx.addRoutes(new WebsocketRouteNoSSL());
        ctx.setUseMDCLogging(true);
        ctx.setTracing(true);
        ctx.start();
    }

    private static class MDCStart implements Processor {

        private final static Logger logger = LoggerFactory.getLogger(SimpleSignalingExample.class);

        public void process(Exchange exchange) throws Exception {
            String key = (String)exchange.getIn().getHeader(WebsocketConstants.CONNECTION_KEY);

            MDC.clear();
            MDC.put("WebsocketConstants.CONNECTION_KEY",key);

            logger.info("Headers: {}",exchange.getIn().getHeaders());
        }
    }


    /**
     * Websocket to setup webrtc peerconnection
     */
    private static class WebsocketRoute extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("websocket:0.0.0.0:8443/incoming?sslContextParameters=#sslContextParameters")
                    .log("InputBody: ${body}")
                    .process(new ProcessSignals())
                    .bean(new PeerConnection(keyStoreInfo))
                    .choice()
                    .when(body().isInstanceOf(Answer.class))
                    .process(new AnswerToJSON())
                    .log("OutputBody: ${body}").to("websocket:0.0.0.0:8443/incoming?sslContextParameters=#sslContextParameters")
                    .otherwise().log("No response on ICE or answer");
        }
    }

    /**
     * Websocket to setup webrtc peerconnection without SSL.
     * This is useful for testing, but since it is not encrypted not
     * good for actual signaling.
     */
    private static class WebsocketRouteNoSSL extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("websocket:0.0.0.0:8443/incoming")
                    .log("InputBody: ${body}")
                    .process(new ProcessSignals())
                    .bean(new PeerConnection(keyStoreInfo))
                    .choice()
                    .when(body().isInstanceOf(Answer.class))
                    .process(new AnswerToJSON())
                    .log("OutputBody: ${body}").to("websocket:0.0.0.0:8443/incoming")
                    .otherwise().log("No response on ICE or answer");
        }
    }

    /**
     *
     * @return certificate paramters for tls
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static SSLContextParameters sslParameters() throws GeneralSecurityException, IOException {
        String storePath = System.getProperty(ServerProperties.KEYSTORE);
        String alias = System.getProperty(ServerProperties.ALIAS);
        String pass = System.getProperty(ServerProperties.PASS);

        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(storePath);
        ksp.setPassword(pass);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyStore(ksp);
        kmp.setKeyPassword(pass);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters scp = new SSLContextParameters();
        scp.setKeyManagers(kmp);
        scp.setTrustManagers(tmp);
        return scp;
    }



}
