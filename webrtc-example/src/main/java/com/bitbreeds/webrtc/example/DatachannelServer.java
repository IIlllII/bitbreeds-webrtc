package com.bitbreeds.webrtc.example;

import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import com.bitbreeds.webrtc.peerconnection.*;
import com.bitbreeds.webrtc.signaling.Answer;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.websocket.WebsocketComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jndi.JndiBeanRepository;
import org.apache.camel.support.jndi.JndiContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    private static final KeyStoreInfo keyStoreInfo = new KeyStoreInfo(
            System.getProperty(ServerProperties.KEYSTORE,findKeystore()),
            System.getProperty(ServerProperties.ALIAS,"websocket"),
            System.getProperty(ServerProperties.PASS,"websocket"));


    /**
     * Application entry point
     * @param args application arguments
     * @throws Exception failure to create routes
     */
    public static void main(String... args) throws Exception {
        JndiContext context = new JndiContext();
        JndiBeanRepository reg = new JndiBeanRepository(context);

        context.bind("sslContextParameters",sslParameters());

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

        peerConnectionServer.onConnection = (connection) -> {

            connection.onDataChannel = (dataChannel) -> {

                /*
                 * Lets not pollute the log with all messages
                 */
                AtomicReference<Long> receiveLogTime = new AtomicReference<>(System.currentTimeMillis());
                AtomicReference<Long> sendLogTime = new AtomicReference<>(System.currentTimeMillis());

                LinkedBlockingDeque<String> toEcho = new LinkedBlockingDeque<>(10000);

                dataChannel.onOpen = (ev) -> {
                    logger.info("Running onOpen");
                    dataChannel.send("I'M SO OPEN!!!");
                };

                dataChannel.onMessage = (ev) -> {
                    String in = new String(ev.getData());

                    long time = System.currentTimeMillis();
                    long result = receiveLogTime.updateAndGet((i)-> {
                        long diff = time - i;
                        if(diff > 3000) {
                            return time;
                        }
                        return i;
                    });

                    if(time == result) {
                        logger.info("Got messages {} with queue space {}",in,toEcho.size());
                    }

                    try {
                        toEcho.offer("echo-"+in,30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted while offering",e);
                    }
                };

                dataChannel.onClose = (ev) -> {
                    logger.info("Received close: {}", ev);
                };

                dataChannel.onError = (ev) -> {
                    logger.info("Received error: ", ev.getError());
                };

                dataChannel.onBufferedAmountLow = (state) -> {
                    int spaceLeft = state.getSpaceLeftInBytes();
                    int toSend = spaceLeft / 10;
                    int sent = 0;
                    while (sent < toSend) {
                        String strToSend = toEcho.poll();
                        if(strToSend != null) {
                            sent += strToSend.length();
                            dataChannel.send(strToSend);
                        }
                    }

                    final int lol = sent;

                    long time = System.currentTimeMillis();
                    long result = sendLogTime.updateAndGet((i)-> {
                        long diff = time-i;
                        if(diff > 3000) {
                            return time;
                        }
                        return i;
                    });

                    if(time == result) {
                        logger.info("Sending messages of size {} in queue {}",lol,toEcho.size());
                    }
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
