package com.bitbreeds.webrtc.peerconnection;

import com.bitbreeds.webrtc.common.ByteRange;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.dtls.DtlsMuxStunTransport;
import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import com.bitbreeds.webrtc.dtls.WebrtcDtlsServer;
import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.model.webrtc.*;
import com.bitbreeds.webrtc.sctp.impl.*;
import com.bitbreeds.webrtc.sctp.impl.buffer.WireRepresentation;
import com.bitbreeds.webrtc.stun.BindingService;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.tls.DTLSServerProtocol;
import org.bouncycastle.crypto.tls.DatagramTransport;
import org.bouncycastle.crypto.tls.TlsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static com.bitbreeds.webrtc.common.SignalUtil.*;

/*
 * Copyright (c) 16/05/16, Jonas Waage
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
 * @see <a href=https://tools.ietf.org/html/rfc4960#section-3.3.1>SCTP</a>
 * @see <a href=https://github.com/bcgit/bc-java/blob/adecd89d33edf278a5c601af2de696f0a6f65251/core/src/test/java/org/bouncycastle/crypto/tls/test/DTLSServerTest.java> tls server </a>
 * @see <a href=http://stackoverflow.com/questions/18065170/how-do-i-do-tls-with-bouncycastle> tls server </a>
 * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-protocol-09#section-8.2.1">peerconnection spec</a>
 *
 * An implementation of a webrtc peer connection.
 *
 * This is implemented using a UDP socket.
 * On this UDP socket DTLS and and STUN is multiplexed to allow encrypted SCTP messages, and
 * STUN to handle connectivity.
 *
 * TODO Use existing SCTP implementation
 * The SCTP packets are currently fed to my own shitty implementation of SCTP.
 * A goal is to hide that behind the SCTP interface so that I can switch to any userland sctp
 * relatively easily.
 *
 * This peerconnection supports creation ordered/unordered webrtc datachannels.
 *
 */
public class ConnectionImplementation implements Runnable,ConnectionInternalApi {

    public PeerConnection getPeerConnection() {
        return peerConnection;
    }

    enum ConnectionMode {STUN_BINDING, DTLS_HANDSHAKE, SCTP};

    private final ReentrantLock lock = new ReentrantLock(true);

    private final static Logger logger = LoggerFactory.getLogger(ConnectionImplementation.class);

    private SCTP sctp = new SCTPNoopImpl();

    private final static int DEFAULT_WAIT_MILLIS = 60000;
    private final static int DEFAULT_MTU = 1500;
    private final static int DEFAULT_BUFFER_SIZE = 4000;

    private final DTLSServerProtocol serverProtocol;
    private final DatagramSocket socket;

    private final int port;

    private boolean running = true;
    private ConnectionMode mode;

    private final TlsServer dtlsServer;
    private volatile DatagramTransport transport;

    private final BindingService bindingService = new BindingService();

    private SocketAddress sender;

    private final ConcurrentHashMap<Integer,DataChannel> dataChannels = new ConcurrentHashMap<>();

    /**
     * Pool for messages that should be sendt immediately (SACK,FWD_ACK_PT)
     */
    private final ExecutorService highPrioPool = Executors.newFixedThreadPool(1);

    /**
     * Pool for doing SCTP processing of received messages
     */
    private final ExecutorService processPool = Executors.newFixedThreadPool(1);


    /**
     * Pool sending messages over socket
     */
    private final ExecutorService normPrioPool = Executors.newFixedThreadPool(1);


    /**
     * Pool for processing messages sendt by used
     */
    private final ExecutorService sendPool = Executors.newFixedThreadPool(1);

    /**
     * Pool for recurring attempts to send
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Pool for running work of received messaged for user
     */
    private final ExecutorService workPool = Executors.newFixedThreadPool(5);

    private final IceCandidate iceCandidate;

    private final UserData localUser = createLocalUser();

    private final PeerDescription remoteDescription;

    public UserData getLocal() {
        return localUser;
    }

    public IceCandidate getIceCandidate() {
        return iceCandidate;
    }

    private PeerConnection peerConnection;

    public ConnectionImplementation(
            KeyStoreInfo keyStoreInfo,
            PeerDescription remoteDescription) {
        logger.info("Initializing {}",this.getClass().getName());
        this.remoteDescription = remoteDescription;
        try {
            this.socket = new DatagramSocket();
            this.socket.setReceiveBufferSize(200000);
            this.socket.setSendBufferSize(200000);
            this.port = socket.getLocalPort();
            this.serverProtocol = new DTLSServerProtocol(new SecureRandom());
            this.mode = ConnectionMode.STUN_BINDING;
            this.peerConnection = new PeerConnection(this);
            this.dtlsServer = new WebrtcDtlsServer(this,keyStoreInfo,remoteDescription);

            /*
             * Get address which external system can reach
             * TODO ensure this works
             */
            String localAddress = InetAddress.getLocalHost().getHostAddress();
            String address = System.getProperty("com.bitbreeds.ip", localAddress);
            logger.info("Adr: {}", address);

            /*
             * Create candidate from connections, since we only give one priority makes little sense so just adding a
             * number
             *
             * Todo random is weird
             */
            Random random = new Random();
            int number = random.nextInt(1000000);
            this.iceCandidate = new IceCandidate(BigInteger.valueOf(number), this.port, address, 2122252543L);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to start connection:", e);
        }
    }



    @Override
    public void run() {

        logger.info("Started listening to port: " + port);
        while(running && socket.isBound()) {

            byte[] bt = new byte[DEFAULT_BUFFER_SIZE];

                try {
                    if (mode == ConnectionMode.STUN_BINDING) {
                        logger.info("Listening for binding on: " + socket.getLocalSocketAddress() + " - " + socket.getPort());
                        Thread.sleep(5); //No reason to hammer on this

                        DatagramPacket packet = new DatagramPacket(bt, 0, bt.length);
                        socket.receive(packet);
                        SocketAddress currentSender = packet.getSocketAddress();

                        sender = currentSender;
                        byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                        logger.info("Received data: " + Hex.encodeHexString(data) + " on " + socket.getLocalSocketAddress() + " - " + socket.getPort());

                        if(this.remoteDescription == null) {
                            throw new IllegalArgumentException("No user data set for remote user");
                        }

                        byte[] out = bindingService.processBindingRequest(
                                data,
                                localUser.getUserName(),
                                localUser.getPassword(),
                                (InetSocketAddress) currentSender
                        );

                        ByteBuffer outData = ByteBuffer.wrap(out);
                        logger.info("Sending: " + Hex.encodeHexString(outData.array()) + " to " + currentSender);

                        DatagramPacket pc = new DatagramPacket(out, 0, out.length);
                        pc.setSocketAddress(sender);
                        socket.send(pc);

                        this.mode = ConnectionMode.DTLS_HANDSHAKE; //Go to handshake mode
                        logger.info("-> DTLS handshake");
                    }
                    else if(mode == ConnectionMode.DTLS_HANDSHAKE) {
                        Thread.sleep(5);

                        if(transport == null) {
                            socket.connect(sender);

                            logger.info("Connecting DTLS mux");
                            /*
                             * {@link NioUdpTransport} might replace the {@link UDPTransport} here.
                             * @see <a href="https://github.com/RestComm/mediaserver/blob/master/io/rtp/src/main/java/org/mobicents/media/server/impl/srtp/NioUdpTransport.java">NioUdpTransport</a>
                             */
                            //DatagramTransport udpTransport = new UDPTransport(socket, DEFAULT_MTU);
                            DtlsMuxStunTransport muxStunTransport = new DtlsMuxStunTransport(localUser, socket, DEFAULT_MTU);
                            transport = serverProtocol.accept(dtlsServer,muxStunTransport);
                        }

                        boolean useSpecialUdpImpl = Boolean.valueOf(System.getProperty("com.bitbreeds.experiment.nocongestion","false"));
                        sctp = useSpecialUdpImpl ? new TotallyUnreliableSCTP(this) : new SCTPImpl(this);
                        mode = ConnectionMode.SCTP;
                        logger.info("-> SCTP mode");

                        logger.debug("Schedule send polling");
                        scheduler.scheduleAtFixedRate(
                                this::getPayloadsAndSend,
                                1000,
                                10,
                                TimeUnit.MILLISECONDS);

                        logger.debug("Schedule heartbeat");
                        scheduler.scheduleAtFixedRate(()->{
                            try {
                                sctp.createHeartBeat().ifPresent(beat -> {
                                    logger.debug("Sending heartbeat: " + Hex.encodeHexString(beat.getPayload()));
                                    highPrioPool.submit(() ->
                                            putDataOnWire(beat.getPayload())
                                    );
                                });
                            } catch (RuntimeException e) {
                                logger.error("Heartheat failed due to:",e);
                            }
                        },5000,5000,TimeUnit.MILLISECONDS);

                        logger.debug("Schedule monitoring sample");
                        scheduler.scheduleAtFixedRate(() -> {
                                    try {
                                        sctp.runMonitoring();
                                    } catch (RuntimeException e) {
                                        logger.error("Error monitoring logging", e);
                                    }
                                },
                                1000, 1000, TimeUnit.MILLISECONDS);
                    }
                    else if(mode == ConnectionMode.SCTP) {
                        logger.debug("In SCTP mode");
                        /*
                         * Here we receive message and put them to a worker thread for handling
                         * If the output of handling the message is a message, then we send those
                         * using the same thread.
                         */
                        byte[] buf = new byte[transport.getReceiveLimit()];
                        int length = transport.receive(buf, 0, buf.length, DEFAULT_WAIT_MILLIS);
                        if (length >= 0) {
                            logger.debug("Received on conn: {} at port: {} with length {}",peerConnection.getId(),port,length);
                            byte[] handled = Arrays.copyOf(buf, length);
                            processReceivedMessage(handled);
                        }
                    }
                }
                catch (Exception e) {
                    logger.error("Com error:",e);
                    logger.info("Shutting down, we cannot continue here");
                    closeConnection();
                }
        }

        /*
         * Shut down thread pools in a controlled manner
         */
        shutDownPoolControlled(scheduler,"scheduler");
        shutDownPoolControlled(processPool,"processpool");
        shutDownPoolControlled(workPool,"workpool");
        shutDownPoolControlled(sendPool,"sendpool");
        shutDownPoolControlled(normPrioPool,"normPrioPool");
        shutDownPoolControlled(highPrioPool,"highPrioPool");
    }

    private void getPayloadsAndSend() {
        try {
            List<WireRepresentation> toSend = sctp.runPeriodicSCTPTasks();
            toSend.forEach(i -> putDataOnWire(i.getPayload()));
        } catch (Exception e) {
            logger.error("Shut down cause by sending failure due to",e);
            sctp.shutdown();
        }
    }

    private void shutDownPoolControlled(ExecutorService threadPoolExecutor,String name) {
        logger.info("Shutting down pool {} ",name);
        try {
            threadPoolExecutor.shutdown();
            threadPoolExecutor.awaitTermination(3,TimeUnit.SECONDS);
            logger.info("Controlled shutdown of pool {} finished",name);
        }
        catch (Exception e) {
            logger.info("Controlled shutdown of pool {} failed, due to: ",name,e);
            threadPoolExecutor.shutdownNow();
        }
    }

    @Override
    public void processReceivedMessage(byte[] buf) {
        processPool.submit(() -> {
            try {
                List<WireRepresentation> data = sctp.handleRequest(buf);
                data.forEach(i->putDataOnWire(i.getPayload()));
            } catch (Exception e) {
                logger.error("Failed handling message: ", e);
            }
        });
        logger.debug("Input: " + Hex.encodeHexString(buf));
    }

    /**
     * Stop running so all receive loop will stop, and then close socket
     */
    @Override
    public void closeConnection() {
        if(this.running) {
            this.setRunning(false);
            //Give DCs a chance to notify user
            dataChannels.values().forEach(i ->
                    workPool.submit(() -> i.onClose.accept(new CloseEvent()))
            );
            sctp.abort();
            socket.close();
        }
    }


    /**
     * Perform controlled shutdown of the SCTP association
     */
    public void close() {
        sctp.shutdown();
    }


    /**
     * Data is sent as a SCTPMessage
     *
     * @param data bytes to send
     */
    @Override
    public void send(byte[] data, SCTPPayloadProtocolId ppid, int streamId, SCTPReliability partialReliability) {
        if (mode == ConnectionMode.SCTP && running) {
             sctp.bufferForSending(data, ppid, streamId, partialReliability); //Buffer messages
             sendPool.submit(this::getPayloadsAndSend); //There must be data to send now, so run immediately
        } else {
            logger.error("Data {} not sent, socket not open", String.valueOf(Hex.encodeHex(data)));
        }
    }

    @Override
    public void putDataOnWireAsyncNormPrio(byte[] out) {
        normPrioPool.submit(() -> putDataOnWire(out));
    }

    @Override
    public void putDataOnWireAsyncHighPrio(byte[] out) {
        highPrioPool.submit(() -> putDataOnWire(out));
    }


    /**
     * The method to call to send data.
     * Uses a fair lock to ensure thread safety and avoid starvation
     *
     * @param out data to send
     */
    @Override
    public void putDataOnWire(byte[] out) {
        lock.lock();
        try {
            logger.debug("Sending on conn: {} data: {}",peerConnection.getId(),  Hex.encodeHexString(out));
            transport.send(out, 0, out.length);
        } catch (IOException e) {
            logger.error("Sending message {} failed", Hex.encodeHex(out), e);
            throw new RuntimeException("Sending failed",e);
        } finally {
            lock.unlock();
        }
    }


    /**
     *
     * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-channel-12">data channel spec</a>
     */
    @Override
    public void presentToUser(Deliverable deliverable) {
        DataChannel definition = dataChannels.get(deliverable.getStreamId());

        if (deliverable.getProtocolId() == SCTPPayloadProtocolId.WEBRTC_DCEP) {
            byte[] msgData = deliverable.getData();
            DataChannelMessageType msg = DataChannelMessageType.fromInt(unsign(msgData[0]));
            if(DataChannelMessageType.OPEN.equals(msg)) {
                if(definition != null) {
                    return; //Already open data channel, unsure how to handle
                }

                logger.info("Received open: " + Hex.encodeHexString(msgData));
                DataChannelType type = DataChannelType.fromInt(unsign(msgData[1]));
                DataChannelPriority priority = DataChannelPriority.fromInt(intFromTwoBytes(
                        copyRange(msgData, new ByteRange(2, 4))));
                int relParam = intFromFourBytes(copyRange(msgData, new ByteRange(4, 8)));
                int labelLength = SignalUtil.intFromTwoBytes(copyRange(msgData, new ByteRange(8, 10)));
                int protocolLength = SignalUtil.intFromTwoBytes(copyRange(msgData, new ByteRange(10, 12)));
                byte[] label = SignalUtil.copyRange(msgData, new ByteRange(12, 12 + labelLength));
                byte[] protocol = SignalUtil.copyRange(msgData,
                        new ByteRange(12 + labelLength, 12 + labelLength + protocolLength));

                ReliabilityParameters parameters = new ReliabilityParameters(
                        relParam,
                        type,
                        priority,
                        label,
                        protocol);

                /*
                 * Send ack
                 */
                byte[] ack = new byte[] {sign(DataChannelMessageType.ACK.getType())};
                this.send(ack,SCTPPayloadProtocolId.WEBRTC_DCEP,deliverable.getStreamId(),SCTPReliability.createUnordered());

                DataChannel nuDef = new DataChannel(this,deliverable.getStreamId(), parameters);

                /*
                 * Allow user to hook in behavior when datachannel is created
                 */
                peerConnection.onDataChannel.accept(nuDef);

                dataChannels.put(nuDef.getStreamId(), nuDef);

                logger.info("Opening datachannel with is {} and params {}", nuDef.getStreamId(), nuDef.getReliabilityParameters());

                /*
                 * Run user callback
                 */
                nuDef.onOpen.accept(new OpenEvent());

            } else {
                throw new IllegalArgumentException("PPID " +SCTPPayloadProtocolId.WEBRTC_DCEP + " should be sent with " + DataChannelMessageType.OPEN);
            }
        } else {
            if(definition != null) {
                workPool.submit(() -> {
                    try {
                        definition.onMessage.accept(new MessageEvent(deliverable.getData(),sender));
                    } catch (Exception e) {
                        logger.error("OnMessage failed",e);
                    }
                });
            }
            else {
                throw new IllegalStateException("DataChannel is not open");
            }
        }
    }


    public void setRunning(boolean running) {
        this.running = running;
    }

    public int getPort() {
        return port;
    }


    /**
     * @return A local user with randomly generated username and password.
     */
    private UserData createLocalUser() {
        String myUser = Hex.encodeHexString(SignalUtil.randomBytes(4));
        String myPass = Hex.encodeHexString(SignalUtil.randomBytes(16));
        return new UserData(myUser,myPass);
    }

}
