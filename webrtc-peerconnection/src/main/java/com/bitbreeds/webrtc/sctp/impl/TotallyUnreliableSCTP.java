package com.bitbreeds.webrtc.sctp.impl;/*
 *
 * Copyright (c) 21/09/2018, Jonas Waage
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.model.webrtc.ConnectionInternalApi;
import com.bitbreeds.webrtc.model.webrtc.Deliverable;
import com.bitbreeds.webrtc.sctp.impl.buffer.FwdAckPoint;
import com.bitbreeds.webrtc.sctp.impl.buffer.OutOfBufferSpaceError;
import com.bitbreeds.webrtc.sctp.impl.buffer.SackData;
import com.bitbreeds.webrtc.sctp.impl.buffer.WireRepresentation;
import com.bitbreeds.webrtc.sctp.impl.model.ReceivedData;
import com.bitbreeds.webrtc.sctp.impl.util.SCTPUtil;
import com.bitbreeds.webrtc.sctp.model.*;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bitbreeds.webrtc.common.SignalUtil.sign;
import static com.bitbreeds.webrtc.sctp.model.SCTPFixedAttributeType.*;
import static com.bitbreeds.webrtc.sctp.model.SCTPFixedAttributeType.PROTOCOL_IDENTIFIER;

/**
 *
 * Experiment where the forwards TSN will be bundled WITH the data chunk
 * and where SACKs will always be sent based on highest TSN, and will
 * NOT report duplicates or gap acks.
 *
 * Packets with lower TSN will always be dropped on reception, since they are
 * out of date.
 *
 * The purpose of this is usecases where only the last packet matters.
 *
 */
public class TotallyUnreliableSCTP implements SCTP {

    private long INITIAL_BUFFER_SIZE = 200000;
    private int MESSAGES_TO_SEND_PR_CALL = 2;
    private int BUFFER_COUNT = 100;

    private AtomicLong receivedBytes = new AtomicLong(0);
    private AtomicLong sentBytest = new AtomicLong(0);

    private AtomicInteger remoteBufferSize = new AtomicInteger(0);

    /**
     * The tsns we assign
     */
    private AtomicLong tsn = new AtomicLong(0);

    /**
     * Keeps track of the highest TSN received.
     * Which will be sacked immediately.
     */
    private AtomicLong remoteTsnReceived = new AtomicLong(0);

    /**
     * The impl access to write data to the socket
     */
    private final ConnectionInternalApi connection;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Map<SCTPMessageType,MessageHandler> handlerMap = createHandlerMap();
    private SCTPContext context;
    private final HeartBeatService heartBeatService = new HeartBeatService();

    private final BlockingQueue<WireRepresentation> queue = new ArrayBlockingQueue<>(BUFFER_COUNT);

    /**
     *
     * @param connection interface to socket
     */
    public TotallyUnreliableSCTP(ConnectionInternalApi connection) {
        this.connection = connection;
        logger.warn("Starting this gigantic hack {}, failure awaits",this.getClass());
    }


    /**
     * @return heartbeat message
     */
    @Override
    public Optional<WireRepresentation> createHeartBeat() {
        return Optional.of(new WireRepresentation(
                heartBeatService.createHeartBeat(SCTPUtil.baseHeader(context)).toBytes(),
                SCTPMessageType.HEARTBEAT));
    }

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

    @Override
    public List<WireRepresentation> handleRequest(byte[] input) {
        logger.debug(Hex.encodeHexString(input));
        SCTPMessage inFullMessage = SCTPMessage.fromBytes(input);

        logger.info("Input Parsed: " + inFullMessage);

        SCTPHeader inHdr = inFullMessage.getHeader();
        List<SCTPChunk> inChunks = inFullMessage.getChunks();

        //TODO https://tools.ietf.org/html/rfc4960#section-8.5.1, rules for INIT chunk
        return inChunks.stream()
                .map(i->handleChunk(i,inHdr))
                .flatMap(i->i)
                .map(i->new WireRepresentation(SCTPUtil.addChecksum(i).toBytes(),i.getChunks().get(0).getType()))
                .collect(Collectors.toList());
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
    public void bufferForSending(byte[] data, SCTPPayloadProtocolId id, Integer stream, SCTPReliability partialReliability) {

        int streamId = stream == null ? 0 : stream; //Use zero if not set
        int streamSequenceNumber = 0; //Only for ordered

        long msgTSN = tsn.incrementAndGet();

        Map<SCTPFixedAttributeType, SCTPFixedAttribute> attr = new HashMap<>();
        attr.put(TSN, new SCTPFixedAttribute(TSN, SignalUtil.longToFourBytes(msgTSN)));
        attr.put(STREAM_IDENTIFIER_S, new SCTPFixedAttribute(STREAM_IDENTIFIER_S, SignalUtil.twoBytesFromInt(streamId)));
        attr.put(STREAM_SEQUENCE_NUMBER, new SCTPFixedAttribute(STREAM_SEQUENCE_NUMBER, SignalUtil.twoBytesFromInt(streamSequenceNumber)));
        attr.put(PROTOCOL_IDENTIFIER, new SCTPFixedAttribute(PROTOCOL_IDENTIFIER, new byte[]{0, 0, 0, sign(id.getId())}));

        int fixed = attr.values().stream()
                .map(i -> i.getType().getLgt())
                .reduce(0, Integer::sum);

        int lgt = 4 + fixed + data.length;

        byte[] dataOut = SignalUtil.padToMultipleOfFour(data);

        SCTPChunk chunk = new SCTPChunk(
                SCTPMessageType.DATA,
                SCTPOrderFlag.UNORDERED_UNFRAGMENTED,
                lgt,
                attr,
                new HashMap<>(),
                dataOut);

        SCTPChunk ackPt = SackCreator.creatForwardTsnChunk(new FwdAckPoint(msgTSN-1, Collections.emptyList()));


        SCTPMessage msg = new SCTPMessage(
                SCTPUtil.baseHeader(context),
                Arrays.asList(ackPt,chunk)); //Must be bundled with control chunks first

        byte[] finalOut = SCTPUtil.addChecksum(msg).toBytes();

        if(!queue.offer(new WireRepresentation(finalOut, SCTPMessageType.DATA))) {
            throw new OutOfBufferSpaceError("Buffering failed due to no buffer space");
        }
    }

    /**
     * Print relevant monitoring and debugging data
     */
    public void runMonitoring() {
        logger.info("---------------------------------------------");
        logger.info("CumulativeReceivedTSN: " + remoteTsnReceived.get());
        logger.info("MyTsn: " + tsn.get());
        logger.info("Total received bytes: " + receivedBytes.get());
        logger.info("Total sent bytes: " + sentBytest.get());
        logger.info("RTT: " + heartBeatService.getRttMillis());
    }

    @Override
    public ConnectionInternalApi getConnection() {
        return connection;
    }

    @Override
    public void receiveHeartBeatAck(byte[] data) {
        logger.info("Received hearthBeatAck with data");
    }

    @Override
    public void updateAckPoint(long ackPoint) {
        long updated = remoteTsnReceived.updateAndGet((l) -> Math.max(ackPoint,l));

        //Maybe send a simple sack
    }

    @Override
    public void handleReceiveInitialTSN(long remoteTsn) {
        logger.info("Received initial TSN from remote {}",remoteTsn);
    }

    @Override
    public void handleSctpPayload(ReceivedData storage) {
        long updated = remoteTsnReceived.updateAndGet((l) -> Math.max(storage.getTSN(),l));

        //Maybe send sack?
        Optional<SCTPMessage> msg = SackCreator.createSack(
                SCTPUtil.baseHeader(context),
                new SackData(remoteTsnReceived.get(),Collections.emptyList(),Collections.emptyList(),(int)INITIAL_BUFFER_SIZE));

        msg.ifPresent((i)-> {
                    connection.putDataOnWireAsyncHighPrio(i.toBytes());
                });

        Deliverable deliverable = new Deliverable(
                storage.getPayload(),
                storage.getStreamSequence(),
                storage.getStreamId(),
                storage.getProtocolId()
        );

        connection.presentToUser(deliverable);
    }

    @Override
    public void updateAcknowledgedTSNS(SackData sack) {
        logger.info("Sack with data {}",sack);

        long updated = remoteTsnReceived.updateAndGet((l) -> Math.max(sack.getCumulativeTSN(),l));
    }

    @Override
    public void initializeRemote(int remoteBufferSize, long localTSN) {
        logger.info("Initialized with buffersize {}, and local TSN {}",remoteBufferSize,localTSN);
        this.remoteBufferSize.set(remoteBufferSize);
    }


    @Override
    public void shutdown() {
        //TODO
    }

    @Override
    public void establish() {
        //TODO
    }

    @Override
    public void setContext(SCTPContext context) {
        this.context = context;
    }

    @Override
    public long getFirstTSN() {
        return tsn.get();
    }

    @Override
    public long getBufferCapacity() {
        return INITIAL_BUFFER_SIZE;
    }


    @Override
    public void finalSctpShutdown() {

    }

    @Override
    public void receiveShutDown() {

    }

    @Override
    public void abort() {
        connection.closeConnection();
    }

    @Override
    public List<WireRepresentation> getPayloadsToSend() {
        ArrayList<WireRepresentation> toSend = new ArrayList<>(MESSAGES_TO_SEND_PR_CALL);
        queue.drainTo(toSend,MESSAGES_TO_SEND_PR_CALL);
        return toSend;
    }
}
