package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import com.bitbreeds.webrtc.peerconnection.*;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.websocket.WebsocketComponent;
import org.apache.camel.component.websocket.WebsocketConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jndi.JndiBeanRepository;
import org.apache.camel.support.jndi.JndiContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/*
 * Copyright (c) 16/04/16, Jonas Waage
 */

/**
 * Simple WSS based signaling used for <b>TESTING</b>
 */
public class SimpleSignaling {

    private final static Logger logger = LoggerFactory.getLogger(SimpleSignaling.class);

    /**
     * @return keystore path
     */
    private static String findKeystore() {
        URL url = SimpleSignaling.class.getClassLoader().getResource("ws2.jks");
        if(null != url) {
            File a = new File(url.getFile());
            logger.info("Loading keystore {}",a.getAbsolutePath());
            return a.getAbsolutePath();
        }
        else {
            return null;
        }
    }

    static KeyStoreInfo keyStoreInfo = new KeyStoreInfo(
            findKeystore(),
            "websocket",
            "websocket");


    /**
     * Application entry point
     * @param args application arguments
     * @throws Exception
     */
    public static void main(String... args) throws Exception {
        JndiContext jndiCtx = new JndiContext();
        JndiBeanRepository reg = new JndiBeanRepository(jndiCtx);
        jndiCtx.bind("sslContextParameters",sslParameters());

        SimplePeerServer peerConnectionServer = new SimplePeerServer(keyStoreInfo);

        setupPeerConnectionDuplicateCheck(peerConnectionServer);

        CamelContext ctx = new DefaultCamelContext(reg);
        WebsocketComponent component = (WebsocketComponent)ctx.getComponent("websocket");
        component.setMinThreads(1);
        component.setMaxThreads(15);
        ctx.addRoutes(new WebsocketRouteNoSSL(peerConnectionServer));
        ctx.addRoutes(new OutRoute());
        ctx.addRoutes(new DelayRoute());
        ctx.setUseMDCLogging(true);
        ctx.setTracing(true);
        ctx.start();
    }


    /**
     *
     * @return context with a route containing a server which can create lossy connections
     * @throws Exception
     */
    public static CamelContext camelContextDelayJitterLoss(int lossIn, int lossOut, int delay, int jitter, Consumer<SimplePeerServer> consumer) throws Exception {
        JndiContext jndiCtx = new JndiContext();
        JndiBeanRepository reg = new JndiBeanRepository(jndiCtx);
        jndiCtx.bind("sslContextParameters",sslParameters());

        SimplePeerServer peerConnectionServer = new SimplePeerServer(
                keyStoreInfo,
                (i) -> new LossyDelayedJitteryConnection(keyStoreInfo,i,lossIn,lossOut,delay,jitter,AddressUtils.findAddress(),5)
        );

        consumer.accept(peerConnectionServer);

        CamelContext ctx = new DefaultCamelContext(reg);
        WebsocketComponent component = (WebsocketComponent)ctx.getComponent("websocket");
        component.setMinThreads(1);
        component.setMaxThreads(15);
        ctx.addRoutes(new SimpleSignaling.WebsocketRouteNoSSL(peerConnectionServer));
        ctx.addRoutes(new SimpleSignaling.OutRoute());
        ctx.addRoutes(new SimpleSignaling.DelayRoute());
        ctx.setUseMDCLogging(true);
        ctx.setTracing(true);
        return ctx;
    }


    /**
     *
     * @return context with a route containing a server which can create lossy connections
     * @throws Exception
     */
    public static CamelContext camelContextLossy(int lossIn, int lossOut, Consumer<SimplePeerServer> consumer) throws Exception {
        JndiContext jndiCtx = new JndiContext();
        JndiBeanRepository reg = new JndiBeanRepository(jndiCtx);
        jndiCtx.bind("sslContextParameters",sslParameters());

        SimplePeerServer peerConnectionServer = new SimplePeerServer(
                keyStoreInfo,
                (i) -> new LossyConnection(keyStoreInfo,i,lossIn,lossOut,AddressUtils.findAddress())
        );

        consumer.accept(peerConnectionServer);

        CamelContext ctx = new DefaultCamelContext(reg);
        WebsocketComponent component = (WebsocketComponent)ctx.getComponent("websocket");
        component.setMinThreads(1);
        component.setMaxThreads(15);
        ctx.addRoutes(new SimpleSignaling.WebsocketRouteNoSSL(peerConnectionServer));
        ctx.addRoutes(new SimpleSignaling.OutRoute());
        ctx.addRoutes(new SimpleSignaling.DelayRoute());
        ctx.setUseMDCLogging(true);
        ctx.setTracing(true);
        return ctx;
    }


    public static CamelContext initContext(Consumer<SimplePeerServer> consumer) throws Exception {
        JndiContext jndiCtx = new JndiContext();
        JndiBeanRepository reg = new JndiBeanRepository(jndiCtx);
        jndiCtx.bind("sslContextParameters",sslParameters());

        SimplePeerServer peerConnectionServer = new SimplePeerServer(keyStoreInfo,
                (i) -> new LoggingConnection(keyStoreInfo,i,AddressUtils.findAddress()));

        consumer.accept(peerConnectionServer);

        CamelContext ctx = new DefaultCamelContext(reg);
        WebsocketComponent component = (WebsocketComponent)ctx.getComponent("websocket");
        component.setMinThreads(1);
        component.setMaxThreads(15);

        ctx.addRoutes(new WebsocketRouteNoSSL(peerConnectionServer));
        ctx.addRoutes(new OutRoute());
        ctx.addRoutes(new DelayRoute());
        ctx.setUseMDCLogging(true);
        ctx.setTracing(true);
        return ctx;
    }

    public static void setupPeerConnectionDuplicateCheck(SimplePeerServer peerConnectionServer) {
        setupPeerConnectionDuplicateCheck(peerConnectionServer,true);
    }

    public static void setupPeerConnectionDuplicateCheckNoThrow(SimplePeerServer peerConnectionServer) {
        setupPeerConnectionDuplicateCheck(peerConnectionServer,false);
    }

    public static void setupPeerConnectionDuplicateCheck(SimplePeerServer peerConnectionServer,boolean throwOnDupl) {

        peerConnectionServer.onConnection = (connection) -> {

            connection.onDataChannel = (dataChannel) -> {
                //Detect duplicate messages
                HashSet<String> messages = new HashSet<>();

                dataChannel.onOpen = (ev) -> {
                    logger.info("Running onOpen");
                    dataChannel.send("OPEN!");
                };

                dataChannel.onMessage = (ev) -> {
                    String in = new String(ev.getData());
                    logger.info("Running onMessage: " + in);
                    dataChannel.send("echo-" + in);

                    if(messages.contains(in)) {
                        if (throwOnDupl) {
                            throw new IllegalStateException("Duplicate: " + in);
                        }
                    }
                    messages.add(in);
                };

                dataChannel.onClose = (ev) -> {
                    logger.info("Received close: {}", ev);
                };

                dataChannel.onError = (ev) -> {
                    logger.info("Received error: ", ev.getError());
                };

            };

        };
    }



    public static void throughputConnection(SimplePeerServer peerConnectionServer) {

        peerConnectionServer.onConnection = (connection) -> {

            connection.onDataChannel = (dataChannel) -> {

                StringBuilder br = new StringBuilder();
                for(int i = 0; i < 90;i++) {
                    br.append("0123456789");
                }
                String strToSend = br.toString();

                dataChannel.onOpen = (ev) -> {
                    logger.debug("Running onOpen");
                    dataChannel.send("OPEN!");
                };

                dataChannel.onMessage = (ev) -> {
                    String in = new String(ev.getData());
                    logger.debug("Running onMessage: " + in);
                };

                dataChannel.onClose = (ev) -> {
                    logger.debug("Received close: {}", ev);
                };

                dataChannel.onError = (ev) -> {
                    logger.debug("Received error: ", ev.getError());
                };

                dataChannel.onBufferedAmountLow = (state) -> {
                    int spaceLeft = state.getSpaceLeftInBytes();
                    int toSend = spaceLeft / 10;
                    int sent = 0;
                    while (sent < toSend) {
                        sent += strToSend.length();
                        dataChannel.send(strToSend);
                    }
                };

            };

        };
    }


    static class ReceivedRecord {
        public final String data;
        public final long time;
        public ReceivedRecord(String data,long time) {
            this.data = data;
            this.time = time;
        }

        @Override
        public String toString() {
            return "ReceivedRecord{" +
                    "data='" + data + '\'' +
                    ", time=" + time +
                    '}';
        }
    }

    public static void gameServerEquivalent(SimplePeerServer peerConnectionServer, ArrayList<ReceivedRecord> storeReceived) {

        peerConnectionServer.onConnection = (connection) -> {

            connection.onDataChannel = (dataChannel) -> {

                AtomicInteger sendNum = new AtomicInteger(0);
                ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

                dataChannel.onOpen = (ev) -> {
                    dataChannel.send("OPEN!");
                    executorService.scheduleAtFixedRate(() -> {
                        dataChannel.send("SERVER-MSG-"+sendNum.getAndIncrement());
                    },25,25, TimeUnit.MILLISECONDS);
                    logger.debug("Running onOpen");
                };

                dataChannel.onMessage = (ev) -> {
                    String in = new String(ev.getData());
                    logger.debug("Running onMessage: " + in);
                    storeReceived.add(new ReceivedRecord(in,System.currentTimeMillis()));

                };

                dataChannel.onClose = (ev) -> {
                    logger.debug("Received close: {}", ev);
                    executorService.shutdownNow();
                    connection.close();
                };

                dataChannel.onError = (ev) -> {
                    logger.debug("Received error: ", ev.getError());
                    executorService.shutdownNow();
                    connection.close();
                };

            };

        };
    }


    private static class MDCStart implements Processor {

        private final static Logger logger = LoggerFactory.getLogger(SimpleSignaling.class);

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
                    .bean(new SimplePeerServer(keyStoreInfo))
                    .split()
                    .body()
                    .choice()
                    .when(body().isInstanceOf(Answer.class))
                    .process(new AnswerToJSON())
                    .when(body().isInstanceOf(IceCandidate.class))
                    .process(new IceCandidateToJSON())
                    .otherwise().log("No response on ICE or answer")
                    .end()
                    .log("OutputBody: ${body}")
                    .to("websocket:0.0.0.0:8443/incoming?sslContextParameters=#sslContextParameters");
        }
    }

    /**
     * Websocket to setup webrtc peerconnection without SSL.
     * This is useful for testing, but since it is not encrypted not
     * good for actual signaling.
     *
     */
    static class WebsocketRouteNoSSL extends RouteBuilder {

        private final SimplePeerServer peerConnection;

        public WebsocketRouteNoSSL(SimplePeerServer peerConnection) {
            this.peerConnection = peerConnection;
        }

        @Override
        public void configure() throws Exception {
            from("websocket:0.0.0.0:8443/incoming")
                    .routePolicy(new ShutdownPolicy(peerConnection))
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

    static class OutRoute extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("seda:out")
                    .log("InputBody: ${body}")
                    .to("websocket:0.0.0.0:8443/incoming");
        }
    }

    static class DelayRoute extends RouteBuilder {

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
