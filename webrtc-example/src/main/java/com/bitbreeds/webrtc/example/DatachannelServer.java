package com.bitbreeds.webrtc.example;

import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import com.bitbreeds.webrtc.peerconnection.*;
import com.bitbreeds.webrtc.signaling.Answer;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.websocket.WebsocketComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.jndi.JndiContext;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;

/*
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
 *
 * This uses a keystore in this repo
 * DO NOT USE FOR ANYTHING BUT TESTING
 * For actual use implement this yourself and provide your own keystore both for signaling and WebRTC
 *
 */
public class DatachannelServer {

    private final static Logger logger = LoggerFactory.getLogger(DatachannelServer.class);

    /**
     * @return keystore path
     */
    private static String findKeystore() {
        URL url = DatachannelServer.class.getClassLoader().getResource("ws2.jks");
        if(null != url) {
            File a = new File(url.getFile());
            if(a.exists()) {
                logger.info("Loading keystore {}", a.getAbsolutePath());
                return a.getAbsolutePath();
            }
            else {
                logger.error("No keystore found at: " + a.getAbsolutePath() + " did you forget to " +
                        "add one using -D" + ServerProperties.KEYSTORE + "?");
                return null;
            }
        }
        else {
            return null;
        }
    }

    private static KeyStoreInfo keyStoreInfo = new KeyStoreInfo(
            System.getProperty(ServerProperties.KEYSTORE,findKeystore()),
            System.getProperty(ServerProperties.ALIAS,"websocket"),
            System.getProperty(ServerProperties.PASS,"websocket"));


    /**
     * Application entry point
     * @param args application arguments
     * @throws Exception failure to create routes
     */
    public static void main(String... args) throws Exception {
        JndiRegistry reg = new JndiRegistry(new JndiContext());

        reg.bind("sslContextParameters",sslParameters());

        SimplePeerServer peerConnectionServer = new SimplePeerServer(keyStoreInfo);

        setupPeerConnection(peerConnectionServer);

        CamelContext ctx = new DefaultCamelContext(reg);
        WebsocketComponent component = (WebsocketComponent)ctx.getComponent("websocket");
        component.setMinThreads(3);
        component.setMaxThreads(15);
        ctx.addRoutes(new WebsocketRouteNoSSL(peerConnectionServer));
        ctx.addRoutes(new OutRoute());
        ctx.addRoutes(new DelayRoute());
        ctx.setUseMDCLogging(true);
        ctx.setTracing(true);
        ctx.start();
    }

    private static void setupPeerConnection(SimplePeerServer peerConnectionServer) {
        HashSet<String> messages = new HashSet<>();

        peerConnectionServer.onConnection = (connection) -> {

            connection.onDataChannel = (dataChannel) -> {

                dataChannel.onOpen = (ev) -> {
                    logger.info("Running onOpen");
                    dataChannel.send("I'M SO OPEN!!!");
                };

                dataChannel.onMessage = (ev) -> {
                    String in = new String(ev.getData());
                    logger.info("Running onMessage: " + in);
                    dataChannel.send("echo-" + in);

                    if(messages.contains(in)) {
                        throw new IllegalStateException("Duplicate" + in);
                    }
                    messages.add(in);
                };

                dataChannel.onClose = (ev) -> {
                    logger.info("Received close: {}", ev);
                };

                dataChannel.onError = (ev) -> {
                    logger.info("Received error: {}", ev.getError());
                };

            };

        };
    }




    /**
     * Websocket to setup webrtc peerconnection without SSL.
     * This is useful for testing, but since it is not encrypted not
     * good for actual signaling.
     *
     */
    private static class WebsocketRouteNoSSL extends RouteBuilder {

        private final SimplePeerServer peerConnection;

        public WebsocketRouteNoSSL(SimplePeerServer peerConnection) {
            this.peerConnection = peerConnection;
        }

        @Override
        public void configure() throws Exception {
            from("websocket:0.0.0.0:8443/incoming")
                    .log("InputBody: ${body}")
                    .process(new ProcessSignals())
                    .bean(peerConnection)
                    .split()
                    .body()
                    .choice()
                    .when(body().isInstanceOf(Answer.class))
                    .process(new AnswerToJSON())
                    .to("seda:out")
                    .when(body().isInstanceOf(IceCandidate.class))
                    .process(new IceCandidateToJSON())
                    .to("seda:delay")
                    .end();
        }
    }

    private static class OutRoute extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("seda:out")
                    .log("InputBody: ${body}")
                    .to("websocket:0.0.0.0:8443/incoming");
        }
    }

    private static class DelayRoute extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("seda:delay")
                    .delay(250)
                    .log("InputBody: ${body}")
                    .to("websocket:0.0.0.0:8443/incoming");
        }
    }


    /**
     *
     * @return certificate paramters for tls
     */
    public static SSLContextParameters sslParameters() {
        String storePath = System.getProperty(ServerProperties.KEYSTORE,findKeystore());
        String alias = System.getProperty(ServerProperties.ALIAS,"websocket");
        String pass = System.getProperty(ServerProperties.PASS,"websocket");

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
