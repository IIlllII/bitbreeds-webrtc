package com.bitbreeds.webrtc.datachannel;

import com.bitbreeds.webrtc.dtls.DtlsMuxStunTransport;
import com.bitbreeds.webrtc.dtls.WebrtcDtlsServer;
import com.bitbreeds.webrtc.common.DataChannel;
import com.bitbreeds.webrtc.common.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.sctp.impl.SCTP;
import com.bitbreeds.webrtc.sctp.impl.SCTPImpl;
import com.bitbreeds.webrtc.sctp.impl.SCTPNoopImpl;
import com.bitbreeds.webrtc.signaling.*;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.tls.DTLSServerProtocol;
import org.bouncycastle.crypto.tls.DatagramTransport;
import org.bouncycastle.crypto.tls.TlsServer;
import org.bouncycastle.crypto.tls.UDPTransport;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
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
 * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-protocol-09#section-8.2.1">datachannel spec</a>
 */
public class DataChannelImpl implements Runnable,DataChannel {

    enum ConnectionMode {BINDING,HANDSHAKE,TRANSFER};

    private final ReentrantLock lock = new ReentrantLock(true);

    private final static Logger logger = LoggerFactory.getLogger(DataChannelImpl.class);

    private SCTP sctpService = new SCTPNoopImpl();

    private final static int DEFAULT_WAIT_MILLIS = 10000;
    private final static int DEFAULT_MTU = 1500;
    private final static int DEFAULT_BUFFER_SIZE = 20000;

    private final DTLSServerProtocol serverProtocol;
    private final DatagramSocket channel;

    private final int port;

    private boolean running = true;
    private ConnectionMode mode;

    private final TlsServer dtlsServer;
    private volatile DatagramTransport transport;

    private final BindingService bindingService = new BindingService();

    private SocketAddress sender;

    private final ExecutorService workPool = Executors.newFixedThreadPool(4);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Runnable heartBeat;
    private final Runnable sackSender;
    private final Runnable reSender;
    private final Runnable monitor;

    private static final int RECEIVE_BUFFER_DEFAULT = 6000000;
    private static final int SEND_BUFFER_DEFAULT = 6000000;

    private Consumer<DataChannel> onOpen = (i) -> {};
    private BiConsumer<DataChannel,MessageEvent> onMessage = (i,j) -> {};
    private BiConsumer<DataChannel,ErrorEvent> onError = (i,j)-> {};

    private final PeerConnection parent;

    public DataChannelImpl(
            PeerConnection parent)
            throws IOException {
        logger.info("Initializing {}",this.getClass().getName());
        this.dtlsServer = new WebrtcDtlsServer(parent.getKeyStoreInfo());
        this.parent = parent;
        this.channel = new DatagramSocket();
        this.channel.setReceiveBufferSize(RECEIVE_BUFFER_DEFAULT);
        this.channel.setSendBufferSize(SEND_BUFFER_DEFAULT);
        this.port = channel.getLocalPort();
        this.serverProtocol = new DTLSServerProtocol(new SecureRandom());
        this.mode = ConnectionMode.BINDING;

        /*
         * Print monitoring information
         */
        this.monitor = () -> {
            while(running && channel.isBound()) {
                try {
                    Thread.sleep(3000);
                    sctpService.runMonitoring();
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
            while(running && channel.isBound()) {
                try {
                    Thread.sleep(5000);
                    byte[] beat = sctpService.createHeartBeat();
                    logger.debug("Sending heartbeat: " + Hex.encodeHexString(beat));
                    putDataOnWire(beat);
                } catch (Exception e) {
                    logger.error("HeartBeat error: ",e);
                }
            }
        };

        /*
         * Acknowledge received data
         */
        this.sackSender = () -> {
            while(running && channel.isBound()) {
                try {
                    Thread.sleep(1); //sleep to not go ham on cpu
                    logger.trace("Creating sack:");
                    byte[] beat = sctpService.createSackMessage();
                    if(beat.length > 0) {
                        logger.trace("Sending sack: " + Hex.encodeHexString(beat));
                        putDataOnWire(beat);
                    } else {
                        logger.trace("Already on latest sack, no send");
                    }

                } catch (Exception e) {
                    logger.error("Sack error: ",e);
                }

            }
        };

        /*
         * Resends non acknowledged sent messages
         */
        this.reSender = () -> {
            while(running && channel.isBound() && !channel.isClosed()) {
                try {
                    Thread.sleep(250);
                    List<byte[]> msgs = sctpService.getMessagesForResend();
                    if (!msgs.isEmpty()) {
                        msgs.forEach(i ->
                                {
                                    try {
                                        Thread.sleep(1); //Sleep to let others work a bit
                                        logger.debug("Resending data: " + Hex.encodeHexString(i));
                                        putDataOnWire(i);
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
        if(parent.getRemote() == null) {
            throw new IllegalArgumentException("No user data set for remote user");
        }

        logger.info("Started listening to port: " + port);
        while(running && channel.isBound()) {

            byte[] bt = new byte[DEFAULT_BUFFER_SIZE];

                try {
                    if (mode == ConnectionMode.BINDING) {
                        logger.info("Listening for binding on: " + channel.getLocalSocketAddress() + " - " + channel.getPort());
                        Thread.sleep(5); //No reason to hammer on this

                        DatagramPacket packet = new DatagramPacket(bt, 0, bt.length);
                        channel.receive(packet);
                        SocketAddress currentSender = packet.getSocketAddress();

                        sender = currentSender;
                        byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                        logger.info("Received data: " + Hex.encodeHexString(data) + " on " + channel.getLocalSocketAddress() + " - " + channel.getPort());

                        byte[] out = bindingService.processBindingRequest(
                                data,
                                parent.getLocal().getUserName(),
                                parent.getLocal().getPassword(),
                                (InetSocketAddress) currentSender
                        );

                        ByteBuffer outData = ByteBuffer.wrap(out);
                        logger.info("Sending: " + Hex.encodeHexString(outData.array()) + " to " + currentSender);

                        DatagramPacket pc = new DatagramPacket(out, 0, out.length);
                        pc.setSocketAddress(sender);
                        channel.send(pc);

                        this.mode = ConnectionMode.HANDSHAKE; //Go to handshake mode
                        logger.info("-> DTLS handshake");
                    }
                    else if(mode == ConnectionMode.HANDSHAKE) {
                        Thread.sleep(5);

                        if(transport == null) {
                            channel.connect(sender);

                            logger.info("Connecting DTLS mux");
                            /*
                             * {@link NioUdpTransport} might replace the {@link UDPTransport} here.
                             * @see <a href="https://github.com/RestComm/mediaserver/blob/master/io/rtp/src/main/java/org/mobicents/media/server/impl/srtp/NioUdpTransport.java">NioUdpTransport</a>
                             */
                            DatagramTransport udpTransport = new UDPTransport(channel, DEFAULT_MTU);
                            DtlsMuxStunTransport muxStunTransport = new DtlsMuxStunTransport(parent,channel, DEFAULT_MTU);
                            transport = serverProtocol.accept(dtlsServer,muxStunTransport);
                        }

                        sctpService = new SCTPImpl(this);
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
                            workPool.submit(() -> {
                                try {
                                    List<byte[]> data = sctpService.handleRequest(handled);
                                    data.forEach(this::putDataOnWire);
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
                    running = false; //Need to quit channel now
                }
        }
        logger.info("Shutting down pool");
        workPool.shutdown();
    }

    /**
     * Start the threads if not already started
     */
    private void startThreads() {
        if(started.compareAndSet(false,true)) {
            new Thread(heartBeat).start();
            new Thread(sackSender).start();
            new Thread(reSender).start();
            new Thread(monitor).start();
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
        send(data,SCTPPayloadProtocolId.WEBRTC_STRING);
    }


    /**
     * Data is sent as a SCTPMessage
     *
     *
     * @param data bytes to send
     */
    @Override
    public void send(byte[] data,SCTPPayloadProtocolId ppid) {
        if(mode == ConnectionMode.TRANSFER && running) {
            byte[] out = sctpService.createPayloadMessage(data, ppid);
            putDataOnWire(out);
        }
        else {
            logger.error("Data {} not sent, channel not open",Hex.encodeHex(data));
        }
    }




    /**
     * The method to call to send data.
     * Uses a fair lock to ensure thread safety and avoid starvation
     *
     * @param out data to send
     */
    private void putDataOnWire(byte[] out) {
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
        return  (InetSocketAddress) channel.getLocalSocketAddress();
    }

    /**
     *
     * @param onMessage action to take when receiving a message
     */
    public void onMessage(BiConsumer<DataChannel,MessageEvent> onMessage) {
        this.onMessage = onMessage;
    }

    /**
     * @param onError action when an error occurs
     */
    public void onError(BiConsumer<DataChannel,ErrorEvent> onError) {
        this.onError = onError;
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
     * Submit work based on onMessage
     */
    @Override
    public void runOnMessage(final byte[] data) {
        workPool.submit(() -> {
            try {
                onMessage.accept(this,new MessageEvent(data,sender));
            } catch (Exception e) {
                logger.error("OnMessage failed",e);
            }
        });
    }


    /**
     *
     * @param onOpen action to take when connection is open
     */
    public void onOpen(Consumer<DataChannel> onOpen) {
        this.onOpen = onOpen;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public int getPort() {
        return port;
    }

    @Override
    public int getReceiveBufferSize() {
        if(channel != null) {
            try {
                return channel.getReceiveBufferSize();
            } catch (SocketException e) {
                return RECEIVE_BUFFER_DEFAULT;
            }
        }
        return RECEIVE_BUFFER_DEFAULT;
    }

    @Override
    public int getSendBufferSize() {
        if(channel != null) {
            try {
                return channel.getReceiveBufferSize();
            } catch (SocketException e) {
                return SEND_BUFFER_DEFAULT;
            }
        }
        return SEND_BUFFER_DEFAULT;
    }

}
