package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.datachannel.DataChannelImpl;
import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import org.apache.commons.codec.binary.Hex;
import org.pcollections.ConsPStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
 * A very poor mans PeerConnection hack, currently it only supports
 * connections initiated from another party.
 */
public class PeerConnection {

    private final static Logger logger = LoggerFactory.getLogger(PeerConnection.class);

    private ConsPStack<IceCandidate> remoteCandidates = ConsPStack.empty();
    private ConsPStack<IceCandidate> localCandidates = ConsPStack.empty();
    private ConcurrentHashMap<String,DataChannelImpl> channels = new ConcurrentHashMap<>();
    private final Object candidateMutex = new Object();

    private KeyStoreInfo keyStoreInfo;

    public PeerConnection(KeyStoreInfo keyStoreInfo) {
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
    private UserData remote;

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
    public UserData getRemote() {
        return remote;
    }

    /**
     * We must store the remote username/password when we receive it
     *
     * @param remote username/pass from answer
     */
    public void setRemote(UserData remote) {
        this.remote = remote;
    }

    /**
     * If we start an offer, we must create a datachannel with
     * no userdata for remote user.
     *
     * @return DataChannelImpl with no data for remote user, it can
     * not be started before this data is added.
     */
    public DataChannelImpl createDataChannel() throws IOException {
        return new DataChannelImpl(this);
    }


    /**
     * @param candidate the received candidate
     */
    private void addLocalCandidate(IceCandidate candidate) {
        synchronized (candidateMutex) {
            remoteCandidates = remoteCandidates.plus(candidate);
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

        this.setRemote(new UserData(user,pwd));

        /*
         * TODO The below should be defined outside PeerConnection
         */
        DataChannelImpl conn = channels.get(user);
        if(conn == null) {
            conn = new DataChannelImpl(this);

            //Add handling of input
            conn.onOpen((i) -> {
                logger.info("Running onOpen");
                i.send("I'M SO OPEN!!!");
            });
            conn.onMessage((i,j)->{
                String in = new String(j.getData());
                logger.debug("Running onMessage: " + in);
                i.send("ECHO: " + in);
            });
            conn.onError((i,j)->{
                logger.info("Received error",j.getError());
            });

            channels.put(user,conn);

            new Thread(conn).start();
        }

        String localAddress = InetAddress.getLocalHost().getHostAddress();
        String address = System.getProperty("com.bitbreeds.ip",localAddress);
        logger.info("Adr: {}", address);

        String fingerPrint = CertUtil.getCertFingerPrint(
                keyStoreInfo.getFilePath(),
                keyStoreInfo.getAlias(),
                keyStoreInfo.getPassword());


        /*
         * Create candidate
         */
        Random random = new Random();
        int number = random.nextInt(1000000);
        IceCandidate candidate = new IceCandidate(BigInteger.valueOf(number),conn.getPort(),address,2122252543L);

        addLocalCandidate(candidate);

        SessionDescription answerSdp = SDPUtil.createSDP(
                candidate,
                local.getUserName(),
                local.getPassword(),
                fingerPrint,
                mid
        );

        return Arrays.asList(new Answer(answerSdp));
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

