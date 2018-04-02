package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.model.webrtc.ConnectionInternalApi;
import com.bitbreeds.webrtc.model.webrtc.DataChannel;
import com.bitbreeds.webrtc.model.webrtc.DataChannelDefinition;
import com.bitbreeds.webrtc.peerconnection.ConnectionImplementation;
import com.bitbreeds.webrtc.dtls.CertUtil;
import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import org.pcollections.ConsPStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sdp.MediaDescription;
import javax.sdp.SessionDescription;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

    private ConsPStack<IceCandidate> remoteCandidates = ConsPStack.empty();
    private ConsPStack<IceCandidate> localCandidates = ConsPStack.empty();
    private ConcurrentHashMap<Integer,ConnectionInternalApi> connections = new ConcurrentHashMap<>();
    private final Object candidateMutex = new Object();

    public Consumer<ConnectionImplementation> onConnection = (i) -> {};

    public Consumer<DataChannel> onDataChannel = (i) -> {};

    /**
     * Server keystore for encryption
     */
    private KeyStoreInfo keyStoreInfo;

    public SimplePeerServer(KeyStoreInfo keyStoreInfo) {
        this.keyStoreInfo = keyStoreInfo;
    }

    /**
     * @param candidate the received candidate
     */
    private void addLocalCandidate(IceCandidate candidate) {
        synchronized (candidateMutex) {
            localCandidates = localCandidates.plus(candidate);
        }
    }

    /**
     * @param candidate the received candidate
     */
    private void addCandidate(IceCandidate candidate) {
        synchronized (candidateMutex) {
            remoteCandidates = remoteCandidates.plus(candidate);
        }
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

        PeerDescription remotePeer = new PeerDescription(new UserData(user,pwd),mid,sdp);

        String fingerPrint = CertUtil.getCertFingerPrint(
                keyStoreInfo.getFilePath(),
                keyStoreInfo.getAlias(),
                keyStoreInfo.getPassword());

        ConnectionImplementation ds = new ConnectionImplementation(keyStoreInfo,remotePeer);
        onDataChannel.accept(ds);
        onConnection.accept(ds);
        connections.put(ds.getPort(),ds);
        new Thread(ds).start();

        SessionDescription answerSdp = SDPUtil.createSDP(
                ds.getIceCandidate(),
                ds.getLocal().getUserName(),
                ds.getLocal().getPassword(),
                fingerPrint,
                mid
        );

        logger.info("Answer: "+ answerSdp);

        return Collections.singletonList(new Answer(answerSdp));
    }




}

