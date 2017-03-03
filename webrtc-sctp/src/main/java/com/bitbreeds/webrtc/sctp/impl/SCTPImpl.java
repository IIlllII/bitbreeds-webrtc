package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.ByteRange;
import com.bitbreeds.webrtc.common.DataChannel;
import com.bitbreeds.webrtc.common.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.model.*;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.bitbreeds.webrtc.common.SignalUtil.*;

/**
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
 * TODO implement shutdown messages
 * TODO implement proper congestion control (HUGE!!!)
 * TODO figure out why transfer is slow (BIG!!)
 *
 * TODO implement JMX hooks, so transfer parameters can be adjusted at runtime
 *
 * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-protocol-09#section-8.2.1">datachannel spec</a>
 *
 */
public class SCTPImpl implements SCTP  {

    private static final Logger logger = LoggerFactory.getLogger(SCTPImpl.class);

    private ReliabilityParameters parameters =  null;

    private AtomicLong duplicateCount = new AtomicLong(0);
    AtomicLong receivedBytes = new AtomicLong(0L);

    /**
     * The impl access to write data to the socket
     */
    private final DataChannel writer;


    /**
     * Handles received messages and sending if acknowledgements.
     * Also handles buffering of received messages.
     */
    private final ReceiveService sackCreator = new ReceiveService();

    /**
     * Handles sending of messages and reception of acknowledgements.
     * Also handles buffering of sent messages.
     */
    private final SendService sendService = new SendService(this);


    private final HeartBeatService heartBeatService = new HeartBeatService();

    /**
     *
     * @param writer interface to socket
     */
    public SCTPImpl(DataChannel writer) {
        this.writer = writer;
    }

    /**
     *
     */
    private SCTPContext context;

    /**
     *
     */
    private final Map<SCTPMessageType,MessageHandler> handlerMap = createHandlerMap();

    Map<SCTPMessageType,MessageHandler> createHandlerMap() {
        HashMap<SCTPMessageType, MessageHandler> map = new HashMap<>();
        map.put(SCTPMessageType.INITIATION,new InitiationHandler());
        map.put(SCTPMessageType.COOKIE_ECHO,new CookieEchoHandler());
        map.put(SCTPMessageType.HEARTBEAT_ACK,new HeartBeatAckHandler());
        map.put(SCTPMessageType.HEARTBEAT,new HeartBeatHandler());
        map.put(SCTPMessageType.DATA,new PayloadHandler());
        map.put(SCTPMessageType.SELECTIVE_ACK,new SelectiveAckHandler());
        return map;
    }

    public ReceiveService getSackCreator() {
        return sackCreator;
    }

    public SendService getSendService() {
        return sendService;
    }

    public HeartBeatService getHeartBeatService() {
        return heartBeatService;
    }

    public void setContext(SCTPContext context) {
        this.context = context;
    }


    /**
     *
     * @param data payload to send
     * @return create message with payload to send
     */
    public byte[] createPayloadMessage(byte[] data,SCTPPayloadProtocolId ppid) {

        return sendService.createPayloadMessage(data,ppid,SCTPUtil.baseHeader(context));
    }



    /**
     * @return message with acks
     */
    public byte[] createSackMessage() {
        if(context == null) {
            return new byte[]{};
        }
        Optional<SCTPMessage> message = sackCreator.createSack(SCTPUtil.baseHeader(context));
        return message.map(SCTPMessage::toBytes).orElse(new byte[]{});
    }


    /**
     * @return messages to resend
     */
    @Override
    public List<byte[]> getMessagesForResend() {
        return sendService.getMessagesForResend();
    }


    /**
     * @return heartbeat message
     */
    @Override
    public byte[] createHeartBeat() {
        return heartBeatService.createHeartBeat(SCTPUtil.baseHeader(context)).toBytes();
    }


    /**
     * Handle message and create a response
     * @param input the incoming message
     * @return a byte response, an empty array is equal to no response.
     */
    public List<byte[]> handleRequest(byte[] input) {
        SCTPMessage inFullMessage = SCTPMessage.fromBytes(input);

        logger.debug("Input Parsed: " + inFullMessage );

        logger.debug("Flags: " + Hex.encodeHexString(new byte[]{input[13]}));

        SCTPHeader inHdr = inFullMessage.getHeader();
        List<SCTPChunk> inChunks = inFullMessage.getChunks();

        return inChunks.stream()
                .map(chunk -> {
                    MessageHandler handler = handlerMap.get(chunk.getType());
                    if (handler != null) {

                        Optional<SCTPMessage> out = handler.handleMessage(this, context, inHdr, chunk);
                        return out.map(i -> SCTPUtil.addChecksum(i).toBytes()).orElse(new byte[]{});
                    } else {
                        logger.warn("Not handled messagetype: " + chunk.getType());
                        return new byte[]{};
                    }
                }).collect(Collectors.toList());
    }


    /**
     * Run the open callback
     */
    protected void runOpen() {
        this.writer.runOpen();
    }

    /**
     * Run the onMessage callback
     * @param data input to callback
     */
    protected void runOnMessage(DataStorage data) {

        /**
         * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-channel-12">data channel spec</a>
         */
        if(parameters == null && data.getProtocolId() == SCTPPayloadProtocolId.WEBRTC_DCEP) {
            final byte[] msgData = data.getPayload();
            DataChannelMessageType msg = DataChannelMessageType.fromInt(unsign(msgData[0]));

            if(DataChannelMessageType.OPEN.equals(msg)) {
                logger.debug("Received open: "+ Hex.encodeHexString(msgData));
                DataChannelType type = DataChannelType.fromInt(unsign(msgData[2]));
                DataChannelPriority priority = DataChannelPriority.fromInt(intFromTwoBytes(
                        copyRange(msgData,new ByteRange(2,4))));
                int relParam = intFromFourBytes( copyRange(msgData,new ByteRange(4,8) ));
                int labelLength = SignalUtil.intFromTwoBytes(copyRange(msgData,new ByteRange(8,10)));
                int protocolLength = SignalUtil.intFromTwoBytes(copyRange(msgData,new ByteRange(10,12)));
                byte[] label = SignalUtil.copyRange(msgData,new ByteRange(12,12+labelLength));
                byte[] protocol = SignalUtil.copyRange(msgData,
                        new ByteRange(12+labelLength,12+labelLength+protocolLength));

                /**
                 * Store params
                 */
                parameters = new ReliabilityParameters(
                        relParam,
                        type,
                        priority,
                        label,
                        protocol);

                logger.info("Updated channel with parameters: " + parameters);

                /**
                 * Send ack and do not process anymore
                 */
                byte[] ack = new byte[] {sign(DataChannelMessageType.ACK.getType())};
                this.writer.send(ack,SCTPPayloadProtocolId.WEBRTC_DCEP);
                logger.debug("Sending ack: "+ Hex.encodeHexString(ack));
                return;
            }
            else {
                throw new IllegalArgumentException("PPID " +SCTPPayloadProtocolId.WEBRTC_DCEP + " should be sent with " + DataChannelMessageType.OPEN);
            }
        }

        logger.trace("Flags: " + data.getFlags() + " Stream: " + data.getStreamId() + " Stream seq: " + data.getStreamSequence() );
        logger.trace("Data as hex: " + Hex.encodeHexString(data.getPayload()));
        logger.trace("Data as string: " + new String(data.getPayload()) + ":");

        ReceiveService.TsnStatus status = sackCreator.handleTSN(data.getTSN());
        if(status != ReceiveService.TsnStatus.DUPLICATE) {
            receivedBytes.getAndAdd(data.getPayload().length);
            if (data.getFlags().isOrdered()) {
                /**
                 *
                 * TODO here we have to do something to ensure ordering
                 *
                 * Stream identifier and stream sequence must be used.
                 *
                 * Messages could be 'stored' until the correct sequence
                 * number is received, or maybe we can rely on resend for that?
                 *
                 * Must be careful not to add TSN if I mean to drop the
                 * message and rely on resend...
                 *
                 * Drop
                 *
                 */
                this.writer.runOnMessage(data.getPayload());
            } else {
                this.writer.runOnMessage(data.getPayload());
            }
        }
        else {
            duplicateCount.incrementAndGet();
        }


    }


    /**
     * Print relevant monitoring and debugging data.
     */
    public void runMonitoring() {
        logger.info("---------------------------------------------");
        logger.info("Size received: " + sackCreator.receivedTSNS.size());
        logger.info("Size sent: " + sendService.sentTSNS.size());
        logger.info("CumulativeReceivedTSN: " + sackCreator.cumulativeTSN);
        logger.info("MyTsn: " + sendService.growingTSN.get());
        logger.info("Duplicates: " + sackCreator.duplicatesSinceLast.size());
        logger.info("Total received bytes: " + receivedBytes.get());
        logger.info("Total sent bytes: " + sendService.getSentBytes());
        logger.info("DuplicateCount: " + duplicateCount.get());
        logger.info("RTT: " + heartBeatService.getRttMillis());
    }
}
