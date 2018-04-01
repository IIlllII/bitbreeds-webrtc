package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.common.DataChannel;
import com.bitbreeds.webrtc.common.ConnectionInternalApi;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.datachannel.ConnectionImplementation;
import com.bitbreeds.webrtc.dtls.CertUtil;
import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import org.apache.commons.codec.binary.Hex;
import org.pcollections.ConsPStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sdp.MediaDescription;
import javax.sdp.SessionDescription;
import java.io.IOException;
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
 * Handles offers to create a webrtc peer.
 *
 */
public class PeerServer {

    private final static Logger logger = LoggerFactory.getLogger(PeerServer.class);

    private ConsPStack<IceCandidate> remoteCandidates = ConsPStack.empty();
    private ConsPStack<IceCandidate> localCandidates = ConsPStack.empty();
    private ConcurrentHashMap<String,ConnectionInternalApi> connections = new ConcurrentHashMap<>();
    private final Object candidateMutex = new Object();

    private KeyStoreInfo keyStoreInfo;

    public PeerServer(KeyStoreInfo keyStoreInfo) {
        this.keyStoreInfo = keyStoreInfo;
    }

    public KeyStoreInfo getKeyStoreInfo() {
        return keyStoreInfo;
    }

    /**
     * Password and username for local user
     */
    private final UserData local = createLocalUser();

    /**
     * Password and usename for remote user
     */
    private PeerDescription remote;

    /**
     *
     * @return local userdata
     */
    public UserData getLocal() {
        return local;
    }

    /**
     * @return the remote username and password
     */
    public PeerDescription getRemote() {
        return remote;
    }

    /**
     * We must store the remote username/password when we receive it
     *
     * @param remote username/pass from answer
     */
    public void setRemote(PeerDescription remote) {
        this.remote = remote;
    }

    /**
     * If we start an offer, we must create a datachannel with
     * no userdata for remote user.
     *
     * @return ConnectionImplementation with no data for remote user, it can
     * not be started before this data is added.
     */
    public ConnectionImplementation createDataChannel() throws IOException {
        return new ConnectionImplementation(this);
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
     * @return A local user with randomly generated username and password.
     */
    private UserData createLocalUser() {
        String myUser = Hex.encodeHexString(SignalUtil.randomBytes(4));
        String myPass = Hex.encodeHexString(SignalUtil.randomBytes(16));
        return new UserData(myUser,myPass);
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

        this.setRemote(new PeerDescription(new UserData(user,pwd),mid,sdp));

        String localAddress = InetAddress.getLocalHost().getHostAddress();
        String address = System.getProperty("com.bitbreeds.ip",localAddress);
        logger.info("Adr: {}", address);

        String fingerPrint = CertUtil.getCertFingerPrint(
                keyStoreInfo.getFilePath(),
                keyStoreInfo.getAlias(),
                keyStoreInfo.getPassword());

        ConnectionInternalApi ds = connections.values().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Only Single channel supported for now"));

        /*
         * Create candidate from connections
         */
        Random random = new Random();
        int number = random.nextInt(1000000);
        IceCandidate candidate = new IceCandidate(BigInteger.valueOf(number),ds.getPort(),address,2122252543L);

        addLocalCandidate(candidate);

        SessionDescription answerSdp = SDPUtil.createSDP(
                candidate,
                local.getUserName(),
                local.getPassword(),
                fingerPrint,
                mid
        );

        logger.info("Answer: "+ answerSdp);

        return Collections.singletonList(new Answer(answerSdp));
    }

    public String getFingerPrint() {
        return CertUtil.getCertFingerPrint(
                keyStoreInfo.getFilePath(),
                keyStoreInfo.getAlias(),
                keyStoreInfo.getPassword());
    }

    public SessionDescription createAnswer() {
        IceCandidate candidate = localCandidates.stream()
                .sorted()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No candidate") );
        return SDPUtil.createSDP(candidate,local.getUserName(),local.getPassword(),getFingerPrint(),remote.getMediaStreamId());
    }


    /**
     *
     * @param ordered whether the channel does ordered communication or not.
     *
     * This should initialize a datachannel over the peerconnection, by sending
     * a datachannel open message.
     *
     * @return new datachannel
     */
    public DataChannel createDataChannel(boolean ordered, int maxPacketLifeTime, int maxRetransmits ) {
        if(connections.size() > 12) {
            throw new IllegalStateException("Too many connections defined for a single peer");
        }
        Random random = new Random();
        String next = String.valueOf(random.nextInt(65534));
        while (connections.containsKey(next)){
            next = String.valueOf(random.nextInt(65534));
        }
        ConnectionImplementation ds;
        try {
            ds = new ConnectionImplementation(this);
            connections.put(String.valueOf(next),ds);
            new Thread(ds).start();
            return ds;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create datachannel due to: ",e);
        }
    }


    /**
     * We sent an offer and here we handle the answer
     * @param answer the answer
     */
    public void handleAnswer(Answer answer) throws Exception {
        /*
         * Not implemented
         */
    }

    /**
     *
     * @param ice add the ice candidate to the ice list
     *
     */
    public void handleIce(IceCandidate ice) throws Exception {
        logger.info("Candidate received: "+ ice);
        addCandidate(ice);
    }



}

