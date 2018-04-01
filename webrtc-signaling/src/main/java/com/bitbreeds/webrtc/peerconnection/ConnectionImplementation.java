package com.bitbreeds.webrtc.peerconnection;

import com.bitbreeds.webrtc.common.*;
import com.bitbreeds.webrtc.dtls.DtlsMuxStunTransport;
import com.bitbreeds.webrtc.dtls.KeyStoreInfo;
import com.bitbreeds.webrtc.dtls.WebrtcDtlsServer;
import com.bitbreeds.webrtc.model.webrtc.*;
import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.model.webrtc.DataChannelEvent;
import com.bitbreeds.webrtc.sctp.impl.SCTP;
import com.bitbreeds.webrtc.sctp.impl.SCTPImpl;
import com.bitbreeds.webrtc.sctp.impl.SCTPNoopImpl;
import com.bitbreeds.webrtc.sctp.impl.buffer.WireRepresentation;
import com.bitbreeds.webrtc.signaling.*;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.tls.DTLSServerProtocol;
import org.bouncycastle.crypto.tls.DatagramTransport;
import org.bouncycastle.crypto.tls.TlsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
 * The SCTP packets are currently fed to my own implementation of SCTP.
 *
 * This peerconnection supports creation ordered/unordered webrtc datachannels.
 *
 */
public class ConnectionImplementation implements Runnable,DataChannel,ConnectionInternalApi {


    enum ConnectionMode {BINDING,HANDSHAKE,TRANSFER};

    private final ReentrantLock lock = new ReentrantLock(true);

    private final static Logger logger = LoggerFactory.getLogger(ConnectionImplementation.class);

    private SCTP sctp = new SCTPNoopImpl();

    private final static int DEFAULT_WAIT_MILLIS = 10000;
    private final static int DEFAULT_MTU = 1500;
    private final static int DEFAULT_BUFFER_SIZE = 20000;

    private final DTLSServerProtocol serverProtocol;
    private final DatagramSocket socket;

    private final int port;

    private boolean running = true;
    private ConnectionMode mode;

    private final TlsServer dtlsServer;
    private volatile DatagramTransport transport;

    private final BindingService bindingService = new BindingService();

    private SocketAddress sender;

    private final ExecutorService processPool = Executors.newFixedThreadPool(1);
    private final ExecutorService workPool = Executors.newFixedThreadPool(1);

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Runnable heartBeat;
    private final Runnable reSender;
    private final Runnable monitor;

    private final LinkedBlockingQueue<byte[]> orderedQueue = new LinkedBlockingQueue<>();
    private final Runnable orderedReader;

    public Consumer<DataChannel> onOpen = (i) -> {};
    public BiConsumer<DataChannel,MessageEvent> onMessage = (i, j) -> {};
    public BiConsumer<DataChannel,ErrorEvent> onError = (i, j)-> {};

    private final UserData localUser = createLocalUser();

    private final PeerDescription remoteDescription;

    /**
     *
     * @return local userdata
     */
    public UserData getLocal() {
        return localUser;
    }

    public ConnectionImplementation(
            KeyStoreInfo keyStoreInfo,
            PeerDescription remoteDescription)
            throws IOException {
        logger.info("Initializing {}",this.getClass().getName());
        this.remoteDescription = remoteDescription;
        this.dtlsServer = new WebrtcDtlsServer(keyStoreInfo);
        this.socket = new DatagramSocket();
        this.socket.setReceiveBufferSize(2000000);
        this.socket.setSendBufferSize(2000000);
        this.port = socket.getLocalPort();
        this.serverProtocol = new DTLSServerProtocol(new SecureRandom());
        this.mode = ConnectionMode.BINDING;

        this.orderedReader = () -> {
            while(running && socket.isBound()) {
                try {
                    byte[] bytes = orderedQueue.poll(2, TimeUnit.SECONDS);
                    if(bytes != null) {
                        onMessage.accept(this,new MessageEvent(bytes,sender));
                    }
                }
                catch (Exception e) {
                    logger.error("Logging error",e);
                }
            }
        };

        /*
         * Print monitoring information
         */
        this.monitor = () -> {
            while(running && socket.isBound()) {
                try {
                    Thread.sleep(3000);
                    sctp.runMonitoring();
                }
                catch (Exception e) {
                    logger.error("Logging error",e);
                }
            }
        };

        /*
         * Create heartbeat message
         */
        this.heartBeat = () ->  {
            while(running && socket.isBound()) {
                try {
                    Thread.sleep(5000);
                    sctp.createHeartBeat().ifPresent(beat -> {
                        logger.debug("Sending heartbeat: " + Hex.encodeHexString(beat.getPayload()));
                        processPool.submit(() ->
                                putDataOnWire(beat.getPayload())
                        );
                    });
                } catch (Exception e) {
                    logger.error("HeartBeat error: ",e);
                }
            }
        };


        /*
         * Resends non acknowledged sent messages
         * TODO remove not needed anymore
         */
        this.reSender = () -> {
            while(running && socket.isBound() && !socket.isClosed()) {
                try {
                    Thread.sleep(1000);
                    List<WireRepresentation> msgs = sctp.getMessagesForResend();
                    if (!msgs.isEmpty()) {
                        msgs.forEach(i ->
                                {
                                    try {
                                        Thread.sleep(1); //Sleep to let others work a bit
                                        logger.debug("Resending data: " + Hex.encodeHexString(i.getPayload()));
                                        processPool.submit(()-> {
                                            putDataOnWire(i.getPayload());
                                        });
                                    } catch (InterruptedException e) {
                                        logger.error("Resend error: ",e);
                                    }
                                }
                        );
                    }
                } catch (Exception e) {
                    logger.error("Resend error: ",e);
                }
            }
        };
    }



    @Override
    public void run() {

        logger.info("Started listening to port: " + port);
        while(running && socket.isBound()) {

            byte[] bt = new byte[DEFAULT_BUFFER_SIZE];

                try {
                    if (mode == ConnectionMode.BINDING) {
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

                        this.mode = ConnectionMode.HANDSHAKE; //Go to handshake mode
                        logger.info("-> DTLS handshake");
                    }
                    else if(mode == ConnectionMode.HANDSHAKE) {
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

                        sctp = new SCTPImpl(this);
                        mode = ConnectionMode.TRANSFER;
                        logger.info("-> SCTP mode");
                    }
                    else if(mode == ConnectionMode.TRANSFER) {
                        logger.debug("In SCTP mode");
                        /*
                         * Here we receive message and put them to a worker thread for handling
                         * If the output of handling the message is a message, then we send those
                         * using the same thread.
                         */
                        byte[] buf = new byte[transport.getReceiveLimit()];
                        int length = transport.receive(buf, 0, buf.length, DEFAULT_WAIT_MILLIS);
                        if (length >= 0) {
                            byte[] handled = Arrays.copyOf(buf, length);
                            processPool.submit(() -> {
                                try {
                                    List<WireRepresentation> data = sctp.handleRequest(handled);
                                    data.forEach(i->putDataOnWire(i.getPayload()));
                                } catch (Exception e) {
                                    logger.error("Failed handling message: ", e);
                                }
                            });
                            logger.debug("Input: " + Hex.encodeHexString(handled));
                        }
                    }
                }
                catch (Exception e) {
                    logger.error("Com error:",e);
                    logger.info("Shutting down, we cannot continue here");
                    running = false; //Need to quit socket now
                }
        }


        logger.info("Shutting down processPool");
        try {
            processPool.shutdown();
            processPool.awaitTermination(5,TimeUnit.SECONDS);
            logger.info("Controlled shutdown of processPool finished");
        }
        catch (Exception e) {
            logger.info("Controlled shutdown of processPool failed, due to: ",e);
            processPool.shutdownNow();
        }

        logger.info("Shutting down workPool");
        try{
            workPool.shutdown();
            workPool.awaitTermination(5,TimeUnit.SECONDS);
            logger.info("Controlled shutdown of workPool finished");
        }
        catch (Exception e) {
            logger.info("Controlled shutdown of workPool failed, due to: ",e);
            workPool.shutdownNow();
        }
    }

    /**
     * Start the threads if not already started
     */
    private void startThreads() {
        if(started.compareAndSet(false,true)) {
            new Thread(heartBeat).start();
            //new Thread(reSender).start();
            new Thread(monitor).start();
            new Thread(orderedReader).start();
        }
    }


    /**
     * Data is sent as a SCTPMessage
     * @param data String in default charset
     */
    @Override
    public void send(String data) {
        send(data,Charset.defaultCharset());
    }


    /**
     * Data is sent as a SCTPMessage
     * @param data String sent with given charset
     */
    public void send(String data, Charset charset) {
        send(data.getBytes(charset));
    }

    /**
     * Data is sent as a SCTPMessage
     * @param data bytes to send
     */
    @Override
    public void send(byte[] data) {
        send(data, SCTPPayloadProtocolId.WEBRTC_STRING);
    }


    /**
     * Data is sent as a SCTPMessage
     *
     * @param data bytes to send
     */
    @Override
    public void send(byte[] data,SCTPPayloadProtocolId ppid) {
        if(mode == ConnectionMode.TRANSFER && running) {
            /*
             * Payload can be fragmented if more then 1024 bytes
             */
            List<WireRepresentation> out = sctp.bufferForSending(data, ppid,0);
            processPool.submit(() ->
                out.forEach(i->putDataOnWire(i.getPayload()))
            );
        }
        else {
            logger.error("Data {} not sent, socket not open",Hex.encodeHex(data));
        }
    }





    /**
     * The method to call to send data.
     * Uses a fair lock to ensure thread safety and avoid starvation
     *
     * @param out data to send
     */
    @Override
    public void putDataOnWire(byte[] out) {
        logger.trace("Sending: " + Hex.encodeHexString(out));
        lock.lock();
        try {
            transport.send(out, 0, out.length);
        } catch (IOException e) {
            logger.error("Sending message {} failed", Hex.encodeHex(out), e);
        } finally {
            lock.unlock();
        }
    }

    public InetSocketAddress getLocalAddress() {
        return  (InetSocketAddress) socket.getLocalSocketAddress();
    }


    /**
     * Trigger error handling
     * @param err exception to handle
     */
    public void runOnError(final Exception err) {
        workPool.submit(() -> {
            try {
                onError.accept(this,new ErrorEvent(err));
            } catch (Exception e) {
                logger.error("OnMessage failed",e);
            }
        });
    }


    /**
     * Submit work based on onOpen
     */
    @Override
     public void runOpen() {
        /*
         * TODO probably wrong place to do this
         */
        startThreads(); //On open we should also activate threads
        logger.debug("Running onOpen callback");
        workPool.submit(() -> {
            try {
                onOpen.accept(this);
            } catch (Exception e) {
                logger.error("OnOpen failed",e);
            }
        });
    }

    /**
     * Submit unordered messages
     */
    @Override
    public void runOnMessageUnordered(final byte[] data) {
        workPool.submit(() -> {
            try {
                onMessage.accept(this,new MessageEvent(data,sender));
            } catch (Exception e) {
                logger.error("OnMessage failed",e);
            }
        });
    }

    /**
     * Submit ordered message
     */
    @Override
    public void runOnMessageOrdered(final byte[] data) {
        try {
            orderedQueue.put(data);
        } catch (Exception e) {
            logger.error("OnMessage failed", e);
        }
    }


    public void setRunning(boolean running) {
        this.running = running;
    }

    public int getPort() {
        return port;
    }

    @Override
    public void onDataChannel(DataChannelEvent dataChannel) {

    }


    public void setOnOpen(Consumer<DataChannel> onOpen) {
        this.onOpen = onOpen;
    }

    public void setOnMessage(BiConsumer<DataChannel, MessageEvent> onMessage) {
        this.onMessage = onMessage;
    }

    public void setOnError(BiConsumer<DataChannel, ErrorEvent> onError) {
        this.onError = onError;
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
