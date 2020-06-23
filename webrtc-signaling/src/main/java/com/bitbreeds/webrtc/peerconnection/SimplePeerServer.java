package com.bitbreeds.webrtc.peerconnection;

import com.bitbreeds.webrtc.dtls.CertUtil;
import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import com.bitbreeds.webrtc.signaling.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sdp.MediaDescription;
import javax.sdp.SessionDescription;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 *
 * This class is responsible for creating webrtc peerconnection based on offers.
 *
 */
public class SimplePeerServer {

    private final static Logger logger = LoggerFactory.getLogger(SimplePeerServer.class);

    private final String address;

    /**
     * Pool for recurring monitoring and keep alive tasks
     */
    private final ScheduledExecutorService monitoring = Executors.newScheduledThreadPool(4);

    /**
     * Pool for periodic SCTP tasks
     */
    private final ScheduledExecutorService sctpTasksPool = Executors.newScheduledThreadPool(3);

    /**
     * Function that allows interception of connection methods
     */
    private final Function<PeerDescription,ConnectionImplementation> connectionWrapper;

    public SimplePeerServer(
            KeyStoreInfo keyStoreInfo,
            Function<PeerDescription,ConnectionImplementation> connectionWrapper) {
        this.connectionWrapper = connectionWrapper;
        this.keyStoreInfo = keyStoreInfo;
        address = AddressUtils.findAddress();

        //Run periodic tasks
        sctpTasksPool.scheduleAtFixedRate(() ->
                        connections.values().forEach(ConnectionImplementation::runPeriodicSctpTasks),
                100, 100, TimeUnit.MILLISECONDS
        );

        //Schedule sending of heartbeat
        monitoring.scheduleAtFixedRate(() -> {
            connections.values().forEach(ConnectionImplementation::sendSCTPHeartBeat);
        },3000,3000, TimeUnit.MILLISECONDS);

        //Schedule logging
        monitoring.scheduleAtFixedRate(() -> {
            connections.values().forEach(ConnectionImplementation::runConnectionStateLogging);
        }, 3000, 3000, TimeUnit.MILLISECONDS);

        //Schedule reaping of unresponsive connections
        monitoring.scheduleAtFixedRate(() -> {
            List<Map.Entry<Integer,ConnectionImplementation>> toClose = connections.entrySet().stream()
                    .filter(i -> Instant.now().minusSeconds(30).isAfter(i.getValue().timeOfLastHeartBeatAck()))
                    .collect(Collectors.toList());

            if(!toClose.isEmpty()) {
                logger.info("Reaping connections {} due to missing heartbeats",
                        toClose.stream()
                                .map(i->i.getValue().getPeerConnection().getId().toString())
                                .collect(Collectors.toList()));
                toClose.forEach(i -> {
                    try {
                        connections.remove(i.getKey());
                        i.getValue().close();
                    } catch (Exception e) {
                        logger.error("Reaping failed due to", e);
                    }
                });
            }

            List<Map.Entry<Integer,ConnectionImplementation>> toRemove = connections.entrySet().stream()
                    .filter(i->i.getValue().isSocketClosed())
                    .collect(Collectors.toList());

            if(!toRemove.isEmpty()) {
                logger.info("Removing from connection list due to closed socket {}",
                        toRemove.stream().map(i->i.getValue().getPeerConnection().getId()));

                toRemove.stream().map(Map.Entry::getKey).forEach(i -> connections.remove(i));
            }

        }, 10000, 10000, TimeUnit.MILLISECONDS);
    }

    public SimplePeerServer(KeyStoreInfo keyStoreInfo) {
        this(keyStoreInfo,null);
    }


    private ConcurrentHashMap<Integer,ConnectionImplementation> connections = new ConcurrentHashMap<>();
    public Consumer<PeerConnection> onConnection = (i) -> {};

    /**
     * Server keystore for encryption
     */
    private KeyStoreInfo keyStoreInfo;


    public Map<Integer, ConnectionImplementation> getConnections() {
        return connections;
    }

    /**
     *
     * @param offer the received offer
     * @return The answer to respond with.
     */
    public List<Object> handleOffer(Offer offer) throws Exception {

        SessionDescription sdp = offer.getSdp();

        MediaDescription med = (MediaDescription)sdp.getMediaDescriptions(true).get(0);
        String pwd = med.getAttribute("ice-pwd");
        String user = med.getAttribute("ice-ufrag");
        String mid = med.getAttribute("mid");

        ArrayList<String> rawCandidates = SDPUtil.getCandidates(med.getAttributes(true));

        List<IceCandidate> offerCandidates = rawCandidates
                .stream()
                .map(IceCandidate::fromString)
                .collect(Collectors.toList());

        logger.info("ICE candidates: {}",offerCandidates);

        //Chrome and firefox puts this in different SDP parts.
        String signature = sdp.getAttribute("fingerprint");
        signature = signature != null ? signature : med.getAttribute("fingerprint");

        PeerDescription remotePeer = new PeerDescription(new UserData(user,pwd),mid,signature);

        String fingerPrint = CertUtil.getCertFingerPrint(
                keyStoreInfo.getFilePath(),
                keyStoreInfo.getAlias(),
                keyStoreInfo.getPassword());

        ConnectionImplementation ds = connectionWrapper != null ?
                connectionWrapper.apply(remotePeer) :
                new ConnectionImplementation(keyStoreInfo,remotePeer,address);

        onConnection.accept(ds.getPeerConnection());
        connections.put(ds.getPort(),ds);
        new Thread(ds).start();

        SessionDescription answerSdp = SDPUtil.createSDP(
                offerCandidates,
                ds.getIceCandidate(),
                ds.getLocal().getUserName(),
                ds.getLocal().getPassword(),
                fingerPrint,
                mid,
                true
        );

        logger.info("Answer: "+ answerSdp);

        return Collections.singletonList(new Answer(answerSdp));
    }


    public void shutDown() {
        connections.values().forEach(ConnectionImplementation::close);
        monitoring.shutdownNow();
        sctpTasksPool.shutdownNow();
    }


}

