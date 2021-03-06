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
import org.bouncycastle.tls.DTLSServerProtocol;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.TlsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * This peerconnection supports creation of ordered/unordered webrtc datachannels.
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

    private final AtomicBoolean isRunningOnBufferedAmount = new AtomicBoolean(false);

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
            PeerDescription remoteDescription,
            String address) {
        logger.info("Initializing {}",this.getClass().getName());
        Objects.requireNonNull(address);
        this.remoteDescription = remoteDescription;
        try {
            this.socket = new DatagramSocket();
            this.socket.setReceiveBufferSize(200000);
            this.socket.setSendBufferSize(200000);
            this.port = socket.getLocalPort();
            this.serverProtocol = new DTLSServerProtocol();
            this.mode = ConnectionMode.STUN_BINDING;
            this.peerConnection = new PeerConnection(this);
            this.dtlsServer = new WebrtcDtlsServer(this,keyStoreInfo,remoteDescription);

            this.iceCandidate = new IceCandidate(BigInteger.valueOf(0),BigInteger.valueOf(1), this.port, address, 2122252543L,"host","UDP");

        } catch (IOException e) {
            throw new IllegalStateException("Failed to start connection:", e);
        }
    }

    /**
     *
     */
    public void sendSCTPHeartBeat() {
        if (running && socket.isBound()) {
            sctp.createHeartBeat().ifPresent(beat -> {
                        logger.debug("Sending heartbeat: " + Hex.encodeHexString(beat.getPayload()));
                        putDataOnWire(beat.getPayload());
                    }
            );
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

                        sctp  = new SCTPImpl(this);
                        mode = ConnectionMode.SCTP;
                        logger.info("-> SCTP mode");

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

    @Override
    public void processReceivedMessage(byte[] buf) {
        List<WireRepresentation> data = sctp.handleRequest(buf);
        data.forEach(i -> putDataOnWire(i.getPayload()));
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
            dataChannels.values().forEach(i -> {
                try {
                    i.onClose.accept(new CloseEvent());
                } catch (RuntimeException e) {
                    logger.error("Close event failed due to: ", e);
                }
            });
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
             getPayloadsAndSend(); //There must be data to send now, so run immediately
        } else {
            logger.error("Data {} not sent, connection not open", String.valueOf(Hex.encodeHex(data)));
            throw new IllegalStateException("Connection not open");
        }
    }


    /**
     * Perform periodic tasks (like resend packets missing in a SACK)
     * This has to be maintained from the outside.
     */
    public void runPeriodicSctpTasks() {
        sctp.runPeriodicSCTPTasks();
    }

    public boolean isSocketClosed() {
        return socket.isClosed();
    }

    public void runConnectionStateLogging() {
        sctp.runMonitoring();
    }

    public Instant timeOfLastHeartBeatAck() {
        return sctp.timeOfLastHeartBeatAck();
    }

    public Instant timeOfLastSCTPpacket() {
        return sctp.timeOfLastSCTPPacket();
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
                try {
                definition.onMessage.accept(new MessageEvent(deliverable.getData(),sender));
                } catch (RuntimeException e) {
                    logger.error("OnMessage failed",e);
                }
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

    @Override
    public void notifyDatachannelsBufferedAmountLow(BufferState state) {
        if (isRunningOnBufferedAmount.compareAndSet(false, true)) {

            dataChannels.values().forEach(
                    i -> {
                        try {
                            i.onBufferedAmountLow.accept(state);
                        } catch (RuntimeException e) {
                            logger.error("Error in onBufferedAmountLow", e);
                        }
                    });

            isRunningOnBufferedAmount.set(false);
        }
    }

    @Override
    public int getBufferCapacity() {
        return sctp.sendBufferCapacity();
    }

    /**
     * @return A local user with randomly generated username and password.
     */
    private UserData createLocalUser() {
        String myUser = Hex.encodeHexString(SignalUtil.randomBytes(4));
        String myPass = Hex.encodeHexString(SignalUtil.randomBytes(16));
        return new UserData(myUser,myPass);
    }

    @Override
    public Optional<ReliabilityParameters> getStreamInfo(int stream) {
        return Optional.ofNullable(dataChannels.get(stream)).map(DataChannel::getReliabilityParameters);
    }

}
