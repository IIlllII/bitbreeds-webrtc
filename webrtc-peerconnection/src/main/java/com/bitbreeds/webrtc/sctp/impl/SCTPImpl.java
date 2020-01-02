package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.model.webrtc.*;
import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.sctp.impl.buffer.*;
import com.bitbreeds.webrtc.sctp.impl.model.*;
import com.bitbreeds.webrtc.sctp.impl.util.SCTPUtil;
import com.bitbreeds.webrtc.sctp.model.*;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * Copyright (c) 19/05/16, Jonas Waage
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
 * This class deals with handling SCTP messages and the flow control.
 * This includes keeping track of messages received on either side.
 * Resending messages, computing checksums, creating heartbeats. and so on.
 *
 * The handling of specific chunks is passed to the correct {@link MessageHandler}.
 *
 * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-protocol-09#section-8.2.1">peerconnection spec</a>
 *
 */
public class SCTPImpl implements SCTP  {

    private static final Logger logger = LoggerFactory.getLogger(SCTPImpl.class);

    private static final Logger monitoring = LoggerFactory.getLogger("com.bitbreeds.monitorlog");

    private static int DEFAULT_BUFFER_SIZE = 160000;

    private final int localBufferSize = DEFAULT_BUFFER_SIZE;

    private final static int DEFAULT_SEND_BUFFER_SIZE = 2000000;

    private final AtomicReference<SCTPState> state = new AtomicReference<>(SCTPState.CLOSED);

    private final Object sackLock = new Object();
    private int packetCountSinceSack = 0;
    private boolean hasNonAcknowledgedData = false;
    private boolean sackImmediately = false;

    /**
     * The impl access to write data to the socket
     */
    private final ConnectionInternalApi connection;

    private final ReceiveBuffer receiveBuffer =  new ReceiveBuffer(1000,localBufferSize);
    private final SendBuffer sendBuffer = new SendBuffer(DEFAULT_SEND_BUFFER_SIZE);
    private final PayloadCreator payloadCreator = new PayloadCreator();
    private final HeartBeatService heartBeatService = new HeartBeatService();
    private final AtomicReference<RetransmissionTimer> retransmissionCalculator = new AtomicReference<>(RetransmissionTimer.initial(Instant.now()));
    private final SingleTimedAction shutdownAction = new SingleTimedAction(this::shutDownTask,200); //Not in use yet
    private SCTPContext context;

    /**
     *
     * @param connection interface to socket
     */
    public SCTPImpl(ConnectionInternalApi connection) {
        this.connection = connection;
        logger.warn("Starting normal SCTP impl {}",this.getClass());
    }

    /**
     *
     */
    private final Map<SCTPMessageType,MessageHandler> handlerMap = createHandlerMap();

    private Map<SCTPMessageType,MessageHandler> createHandlerMap() {
        HashMap<SCTPMessageType, MessageHandler> map = new HashMap<>();

        //Init handling
        map.put(SCTPMessageType.INITIATION,new InitiationHandler());
        map.put(SCTPMessageType.COOKIE_ECHO,new CookieEchoHandler());

        //Open
        map.put(SCTPMessageType.HEARTBEAT_ACK,new HeartBeatAckHandler());
        map.put(SCTPMessageType.HEARTBEAT,new HeartBeatHandler());
        map.put(SCTPMessageType.DATA,new PayloadHandler());
        map.put(SCTPMessageType.SELECTIVE_ACK,new SelectiveAckHandler());
        map.put(SCTPMessageType.FORWARD_TSN,new ForwardTsnHandler());

        //Reconfig
        map.put(SCTPMessageType.RE_CONFIG,new ReconfigurationHandler());

        //Shutdown and abort handling
        map.put(SCTPMessageType.ABORT,new AbortHandler());
        map.put(SCTPMessageType.SHUTDOWN,new ShutdownHandler());
        map.put(SCTPMessageType.SHUTDOWN_ACK,new ShutdownAckHandler());
        map.put(SCTPMessageType.SHUTDOWN_COMPLETE,new ShutdownCompleteHandler());
        return map;
    }

    /**
     * Convert millis tp seconds
     * @param heartBeatAck to associate with sent heartbeat
     */
    public void receiveHeartBeatAck(byte[] heartBeatAck) {
        long rttMillis = heartBeatService.receiveHeartBeatAck(heartBeatAck);
        retransmissionCalculator.updateAndGet((i)->i.addMeasure(rttMillis/1000.0));
    }

    public void establish() {
        SCTPState next = state.updateAndGet(SCTPState::moveToEstablished);
        logger.info("Moved to {}",next);
    }

    public void setContext(SCTPContext context) {
        this.context = context;
    }

    private void doRetransmission() {
        if(state.get() == SCTPState.ESTABLISHED) {
            logger.info("Retransmission started {}", Instant.now());
            retransmissionCalculator.updateAndGet((i)->i.restart(Instant.now()));
            RetransmitData toSend = sendBuffer.getDataToRetransmit();
            performRetransmit(toSend);
        }
    }

    private void performRetransmit(RetransmitData toSend) {
        if(toSend.getFwdAckPoint().getAckPoint() > toSend.getRemoteCumulativeTSN()) {
            SCTPChunk chunk = SackCreator.creatForwardTsnChunk(toSend.getFwdAckPoint());
            SCTPMessage msg = new SCTPMessage(SCTPUtil.baseHeader(context), Collections.singletonList(chunk));

            SCTPMessage withChecksum = SCTPUtil.addChecksum(msg);
            getConnection().putDataOnWireAsync(withChecksum.toBytes());

            retransmissionCalculator.updateAndGet((i)->i.start(Instant.now()));
            logger.info("Sending advanced ack point {}", toSend.getFwdAckPoint());
        }

        toSend.getBuffered().forEach(i -> {
                    logger.info("Retransmit {}", i);
                    getConnection().putDataOnWireAsync(i.getData().getSctpPayload());
                }
        );
    }

    /**
     *
     * @param pt ack pt
     */
    public void updateAckPoint(long pt) {
        logger.debug("Update ack point to " + pt);
        ForwardAccResult result = receiveBuffer.receiveForwardAckPoint(pt);
        getConnection().putDataOnWire(createSackMessage(result.getSackData()).getPayload());

        result.getToDeliver().forEach(i->
                getConnection().presentToUser(i)
        );
    }

    /**
     * @param sackData acknowledgement
     */
    public void updateAcknowledgedTSNS(SackData sackData) {
        logger.debug("Got sack {}",sackData );

        SackResult result = sendBuffer.receiveSack(sackData);
        if(sendBuffer.getInflightSize() == 0) {
            retransmissionCalculator.updateAndGet(RetransmissionTimer::stop);
        }
        else if (result.isUpdatedCumulative()){
            retransmissionCalculator.updateAndGet((i)->i.restart(Instant.now()));
        }

        if(result.getAdvancedAckPoint().getAckPoint() > result.getRemoteCumulativeTSN()) {
            SCTPChunk chunk = SackCreator.creatForwardTsnChunk(result.getAdvancedAckPoint());
            SCTPMessage msg = new SCTPMessage(SCTPUtil.baseHeader(context), Collections.singletonList(chunk));

            SCTPMessage withChecksum = SCTPUtil.addChecksum(msg);
            getConnection().putDataOnWireAsync(withChecksum.toBytes());

            retransmissionCalculator.updateAndGet((i)->i.start(Instant.now()));
            logger.info("Sending advanced ack point {}", result.getAdvancedAckPoint());
        }

        result.getFastRetransmits().forEach(i -> {
                    logger.info("Fast retransmit {}",i);
                    getConnection().putDataOnWireAsync(i.getData().getSctpPayload());
                }
        );

        List<BufferedSent> toSend = sendBuffer.getDataToSend();

        toSend.forEach(i ->
                getConnection().putDataOnWireAsync(i.getData().getSctpPayload())
        );
    }

    public void initializeRemote(int remoteReceiveBufferSize,long initialTSN) {
        sendBuffer.initializeRemote(remoteReceiveBufferSize,initialTSN);
    }

    /**
     * Receive initial TSN
     */
    public void handleReceiveInitialTSN(long tsn) {
        receiveBuffer.setInitialTSN(tsn);
    }


    public long getFirstTSN() {
        return payloadCreator.getFirstTSN();
    }

    /**
     *
     * @param data payload to send
     */
    public void bufferForSending(
            byte[] data,
            SCTPPayloadProtocolId ppid,
            Integer stream,
            SCTPReliability reliability) {

        if(!state.get().canSend()) {
            throw new IllegalStateException("Buffering should only happen in the established state");
        }

        List<SendData> messages = payloadCreator.createPayloadMessage(
                data,ppid,
                SCTPUtil.baseHeader(context),
                stream,
                reliability);

        sendBuffer.buffer(messages);
    }


    /**
     *
     * @return payloads moved to inflight and ready to be sent
     */
    public List<WireRepresentation> runPeriodicSCTPTasks() {
        synchronized (sackLock) {
            if(hasNonAcknowledgedData || sackImmediately) {
                hasNonAcknowledgedData = false;
                sackImmediately = false;
                packetCountSinceSack = 0;
                SackData sackData = receiveBuffer.getSackDataToSend();
                getConnection().putDataOnWireAsync(createSackMessage(sackData).getPayload());
            }
        }

        if(sendBuffer.getCapacity() > sendBuffer.getInitialBufferCapacity()/2) {
            this.getConnection().notifyDatachannelsBufferedAmountLow(
                    new BufferState(sendBuffer.getInitialBufferCapacity(),sendBuffer.getCapacity())
            );
        }

        if(retransmissionCalculator.get().checkForTimeout(Instant.now())){
            logger.info("Timeout of t3 timer, running retransmission");
            doRetransmission(); //Will send, so return empty
            return Collections.emptyList();
        }
        else {
            List<BufferedSent> toSend = sendBuffer.getDataToSend();
            if (!toSend.isEmpty()) {
                retransmissionCalculator.updateAndGet((i)->i.start(Instant.now()));
            }
            return toSend.stream()
                    .map(i -> new WireRepresentation(i.getData().getSctpPayload()))
                    .collect(Collectors.toList());
        }

    }

    @Override
    public int sendBufferCapacity() {
        return sendBuffer.getCapacity();
    }

    /**
     * @return message with acks
     */
    private WireRepresentation createSackMessage(SackData sackData) {
        SCTPMessage message = SackCreator.createSack(SCTPUtil.baseHeader(context),sackData);
        logger.debug("Created sack {} to send",message);
        return new WireRepresentation(message.toBytes());
    }


    /**
     * @return heartbeat message
     */
    @Override
    public Optional<WireRepresentation> createHeartBeat() {
        return Optional.of(new WireRepresentation(
                heartBeatService.createHeartBeat(SCTPUtil.baseHeader(context)).toBytes()));
    }


    /**
     * Handle message and create a immediate response if needed
     * @param input the incoming message
     * @return responses
     */
    public List<WireRepresentation> handleRequest(byte[] input) {
        logger.debug(Hex.encodeHexString(input));
        SCTPMessage inFullMessage = SCTPMessage.fromBytes(input);

        logger.debug("Input Parsed: " + inFullMessage);

        SCTPHeader inHdr = inFullMessage.getHeader();
        List<SCTPChunk> inChunks = inFullMessage.getChunks();

        List<WireRepresentation> result = inChunks.stream()
                .map(i->handleChunk(i,inHdr))
                .flatMap(i->i)
                .map(i->new WireRepresentation(SCTPUtil.addChecksum(i).toBytes()))
                .collect(Collectors.toList());

        /*
         * https://tools.ietf.org/html/rfc4960#section-6.2
         * Send sack if packet count with no sack is 2 or more
         */
        synchronized (sackLock) {
            packetCountSinceSack++;
            if(packetCountSinceSack >= 2 || sackImmediately) {
                packetCountSinceSack = 0;
                sackImmediately = false;
                hasNonAcknowledgedData = false;
                SackData sackData = receiveBuffer.getSackDataToSend();
                getConnection().putDataOnWireAsync(createSackMessage(sackData).getPayload());
            }
        }

        return result;
    }

    /**
     *
     * @param chunk chunk data
     * @param hdr hdr of chunk
     * @return handle chunk by finding correct processing in handlermap
     */
    private Stream<SCTPMessage> handleChunk(SCTPChunk chunk, SCTPHeader hdr) {
        MessageHandler handler = handlerMap.get(chunk.getType());
        if (handler != null) {
            logger.debug("Received: {} {}",hdr,chunk);
            Optional<SCTPMessage> out = handler.handleMessage(this, context, hdr, chunk);
            return out.map(Stream::of).orElse(Stream.empty());
        } else {
            logger.warn("Not handled messagetype: " + chunk.getType());
            return Stream.empty();
        }
    }


    @Override
    public long getBufferCapacity() {
        return receiveBuffer.getCapacity();
    }


    /**
     * Handles reception of a payload
     *
     * @param data payload representation
     */
    public void handleSctpPayload(ReceivedData data) {

        Objects.requireNonNull(data);

        logger.trace("Flags: " + data.getFlag() + " Stream: " + data.getStreamId() + " Stream seq: " + data.getStreamSequence());
        logger.trace("Data as hex: " + Hex.encodeHexString(data.getPayload()));
        logger.trace("Data as string: " + new String(data.getPayload()) + ":");

        StoreResult result = receiveBuffer.store(data);
        List<Deliverable> deliverables = receiveBuffer.getMessagesForDelivery();
        if(result.isMustSackImmediately()) {
            sackImmediately = true;
        }
        else {
            synchronized (sackLock) {
                hasNonAcknowledgedData = true;
            }
        }
        deliverables.forEach(
                i -> getConnection().presentToUser(i)
        );
    }

    /**
     * Perform abort of connection
     */
    @Override
    public void abort() {
        if( !SCTPState.CLOSED.equals(state.get()) ) {
            SCTPState next = state.updateAndGet(SCTPState::close);
            logger.info("Moved to {}", next);
            shutdownAction.shutdown();
            getConnection().closeConnection();
        }
    }

    public void receiveShutDown() {
        SCTPState next = state.updateAndGet(SCTPState::receivedShutdown);
        logger.info("Moved to {}",next);
        shutdownAction.start();
    }

    public void finalSctpShutdown() {
        if( !SCTPState.CLOSED.equals(state.get()) ) {
            SCTPState next = state.updateAndGet(SCTPState::close);
            logger.info("Moved to {}", next);
            shutdownAction.stop();
            shutdownAction.shutdown();
            getConnection().closeConnection();
        }
    }

    private void shutDownTask() {
        try {
            SCTPState curr = this.state.get();
            if (SCTPState.SHUTDOWN_PENDING.equals(curr)) {
                if (!sendBuffer.hasMessagesBuffered()) {
                    long received = receiveBuffer.getCumulativeTSN();
                    SCTPMessage msg = ShutDownMessageCreator.createShutDown(SCTPUtil.baseHeader(context), received);
                    getConnection().putDataOnWire(msg.toBytes());
                    SCTPState next = state.updateAndGet(SCTPState::sendShutdown);
                    logger.info("Moved to {}", next);
                }
                else {
                    RetransmitData toSend = sendBuffer.getDataToRetransmit();
                    performRetransmit(toSend);
                }
                shutdownAction.restart();
            } else if (SCTPState.SHUTDOWN_SENT.equals(curr)) {
                long received = receiveBuffer.getCumulativeTSN();
                SCTPMessage msg = ShutDownMessageCreator.createShutDown(SCTPUtil.baseHeader(context), received);
                getConnection().putDataOnWire(msg.toBytes());
                shutdownAction.restart();
            } else if (SCTPState.SHUTDOWN_RECEIVED.equals(curr)) {
                if (!sendBuffer.hasMessagesBuffered()) {
                    SCTPMessage msg = ShutDownMessageCreator.createShutDownAck(SCTPUtil.baseHeader(context));
                    getConnection().putDataOnWire(msg.toBytes());
                    SCTPState next = state.updateAndGet(SCTPState::sendShutdownAck);
                    logger.info("Moved to {}", next);
                }
            } else if (SCTPState.SHUTDOWN_ACK_SENT.equals(curr)) {
                SCTPMessage msg = ShutDownMessageCreator.createShutDownComp(SCTPUtil.baseHeader(context));
                getConnection().putDataOnWire(msg.toBytes());
                SCTPState next = state.updateAndGet(SCTPState::sendShutdownAck);
                logger.info("Moved to {}", next);
            }
        } catch (RuntimeException e) {
            logger.error("Shutdown failed");
            finalSctpShutdown();
        }
    }

    /**
     * Do controlled shutdown (deliver already buffered before shutdown)
     */
    @Override
    public void shutdown() {
        SCTPState next = state.updateAndGet(SCTPState::shutDown);
        logger.info("Moved to {}",next);
        shutdownAction.start();
    }

    /**
     * Print relevant monitoring and debugging data
     */
    public void runMonitoring() {
        monitoring.info("---------------------------------------------");
        monitoring.info("Connection: "+this.getConnection().getPeerConnection().getId());
        monitoring.info("Inflight: " + sendBuffer.getInflightSize());
        monitoring.info("CumulativeReceivedTSN: " + receiveBuffer.getCumulativeTSN());
        monitoring.info("MyTsn: " + payloadCreator.currentTSN());
        monitoring.info("Total received bytes: " + receiveBuffer.getReceivedBytes());
        monitoring.info("Total delivered bytes to user: " + receiveBuffer.getDeliveredBytes());
        monitoring.info("Total sent bytes: " + sendBuffer.getBytesSent());
        monitoring.info("RTT: " + heartBeatService.getRttMillis());
        monitoring.info("Remote buffer: " + sendBuffer.getRemoteBufferSize());
        monitoring.info("Local send buffer: " + sendBuffer.getCapacity());
        monitoring.info("Local buffer: " + receiveBuffer.getCapacity());
        monitoring.info("Cwnd: " + sendBuffer.getCwnd());
        monitoring.info("State: " + state.get());
        monitoring.info("Current t3 timeout: " + retransmissionCalculator.get().getCurrentTimeoutMillis());
    }

    @Override
    public ConnectionInternalApi getConnection() {
        return connection;
    }
}
