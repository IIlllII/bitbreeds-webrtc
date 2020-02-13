package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.model.sctp.GapAck;
import com.bitbreeds.webrtc.sctp.impl.model.SendData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/*
 * Copyright (c) 19/02/2018, Jonas Waage
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
 * Buffer to store SCTP messages sent
 * <a href="https://tools.ietf.org/html/rfc4960#section-6.2.1">SCTP sack</a>
 * <a href="https://tools.ietf.org/html/rfc2581#section-4.2">TCP congestion control</a>
 *
 * Buffer for sent messages
 * Responsibilities:
 * - Assigning TSN
 * - Ensuring we have a finite send buffer
 * - Ensuring we overhold max inflight
 * - Ensure resend if message is never acked
 *
 */
public class SendBuffer {

    private final Object lock = new Object();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Queue<BufferedSent> queue = new ArrayDeque<>();
    private Map<Long,BufferedSent> inFlight = new HashMap<>();

    /**
     * How many bytes can be buffered for sending on this connection.
     * Does not count inflight packets.
     */
    private AtomicInteger capacity;

    private long remoteBufferSize;
    private long remoteCumulativeTSN;
    private final int initialBufferCapacity;

    private final int CONGESTION_MTU = 1500;

    private AtomicReference<Congestion> congestionWindow = new AtomicReference<>(
            Congestion.initial(CONGESTION_MTU)
    );

    /**
     * <a href="https://tools.ietf.org/html/rfc3758#section-3.5">Partial reliability</a>
     */
    private long advancedAckPoint;
    private boolean remoteIsInitialized = false;

    private long bytesSent = 0;

    public SendBuffer(
            int capacity) {
        if(capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be above 0, is " + capacity);
        }
        this.capacity = new AtomicInteger(capacity);
        this.initialBufferCapacity = capacity;
    }


    public void initializeRemote(int remoteBufferSize, long remoteCumulativeTSN) {
        synchronized (lock) {
            if (!remoteIsInitialized) {
                this.remoteBufferSize = remoteBufferSize;
                this.remoteCumulativeTSN = remoteCumulativeTSN;
                this.remoteIsInitialized = true;
            }
        }
    }

    public int getInitialBufferCapacity() {
        return initialBufferCapacity;
    }

    public int getCapacity() {
        return capacity.get();
    }

    public long getRemoteBufferSize() {
        return remoteBufferSize;
    }

    public long getBytesSent() {
        return bytesSent;
    }


    /**
     *
     * Buffer a message for sending and move as many messages to inflight as possible
     *
     * @param messages data to store
     */
    public void buffer(List<SendData> messages) {
        if(!remoteIsInitialized) {
            throw new InitialMessageNotReceived("Initial SCTP message not received yet, no initial TSN");
        }
        synchronized (lock) {
            messages.forEach( data -> {
                if (capacity.get() - data.getSctpPayload().length < 0) {
                    throw new OutOfBufferSpaceError("Send buffer has capacity " + capacity +
                            " message with size "+ data.getSctpPayload().length +" was dropped");
                }
                capacity.accumulateAndGet(data.getSctpPayload().length,(a,b)->a-b);
                queue.add(BufferedSent.buffer(data, data.getTsn()));
            });
            logger.debug("After buffering inflight:" + inFlight + " queue: " + queue.size());
        }
    }


    public int getInflightSize() {
        return inFlight.size();
    }

    /**
     *
     * @return whether there are buffered messages left to be sent
     */
    public boolean hasMessagesBuffered() {
        synchronized (lock) {
            return getInflightSize() > 0 || queue.size() > 0;
        }
    }

    public int getCwnd() {
        return this.congestionWindow.get().getCwnd();
    }

    /**
     * Remove packets acknowledged in sack from inflight.
     *
     * Set remote buffer size.
     *
     * @param sack acknowledgement
     * @return fastresend data
     */
    public SackResult receiveSack(SackData sack) {
        synchronized (lock) {
            logger.info("Handling sack {} with inflight {} and cumTSN {}", sack,inFlight,remoteCumulativeTSN);
            if(sack.getCumulativeTSN() >= remoteCumulativeTSN) {
                boolean updatedCumTSN = sack.getCumulativeTSN() >= remoteCumulativeTSN;

                logger.debug("Sack received {} gaps {}",sack.getCumulativeTSN(),sack.getTsns());

                remoteBufferSize = sack.getBufferLeft();
                remoteCumulativeTSN = sack.getCumulativeTSN();
                advancedAckPoint = Math.max(advancedAckPoint, sack.getCumulativeTSN());

                int belowCumTsnSize = inFlight.values().stream()
                        .filter(i -> i.getTsn() <= sack.getCumulativeTSN())
                        .map(i->i.getData().getSctpPayload().length)
                        .reduce(0, Integer::sum);

                List<BufferedSent> acked = inFlight.values().stream()
                        .filter(i -> acknowledged(sack, i))
                        .collect(Collectors.toList());

                int size = acked.stream()
                        .map(i -> i.getData().getSctpPayload().length)
                        .reduce(0, Integer::sum);

                List<Long> tsns = acked.stream()
                        .map(BufferedSent::getTsn)
                        .collect(Collectors.toList());

                inFlight.keySet().removeAll(tsns);

                List<GapAck> gapAcks = sack.getTsns();
                if(!gapAcks.isEmpty()) {
                    congestionWindow.updateAndGet(Congestion::packetLoss);

                    FwdAckPoint fwdAckPoint = abandonExpiredPackets(gapAcks);

                    GapAck ack = gapAcks.get(gapAcks.size()-1);

                    Long largest = sack.getCumulativeTSN()+ack.end;

                    List<Long> fastAck = inFlight.keySet().stream()
                            .filter(i -> i < largest).collect(Collectors.toList());

                    List<BufferedSent> marked = fastAck.stream()
                            .map(fast -> inFlight.get(fast))
                            .filter(BufferedSent::canResend)
                            .map(BufferedSent::markFast)
                            .collect(Collectors.toList());

                    Optional<BufferedSent> resend = marked.stream()
                            .filter(BufferedSent::canFastResend)
                            .min(BufferedSent::compareTo);

                    List<BufferedSent> resendList = resend
                            .map(BufferedSent::fastResend)
                            .map(Collections::singletonList)
                            .orElse(Collections.emptyList());

                    marked.forEach(
                            i->inFlight.put(i.getTsn(),i)
                    );
                    resendList.forEach(
                            i->inFlight.put(i.getTsn(),i)
                    );
                    return new SackResult(resendList,updatedCumTSN,remoteCumulativeTSN,fwdAckPoint);
                }

                if(inFlight.values().isEmpty()) {
                    congestionWindow.updateAndGet(Congestion::reset);
                }
                else {
                    congestionWindow.updateAndGet(i -> i.increase(belowCumTsnSize, size));
                }

                logger.debug("After Sack inflight:" + inFlight + " queue: " + queue.size());
                return new SackResult(
                        Collections.emptyList(),
                        updatedCumTSN,remoteCumulativeTSN,
                        new FwdAckPoint(advancedAckPoint,Collections.emptyList()));
            }
            else {
                logger.info("Out of order sack {}", sack);
            }
            return new SackResult(Collections.emptyList(),
                    false,
                    remoteCumulativeTSN,
                    new FwdAckPoint(advancedAckPoint,Collections.emptyList()));
        }
    }

    private FwdAckPoint abandonExpiredPackets(List<GapAck> gapAcks) {
        List<BufferedSent> toAbandon = inFlight.values()
                .stream().filter(BufferedSent::shouldAbandon)
                .collect(Collectors.toList());

        Set<Long> ids = toAbandon.stream()
                .map(BufferedSent::getTsn)
                .collect(Collectors.toSet());

        while(ids.contains(advancedAckPoint + 1) ||
                inGapAck(remoteCumulativeTSN,gapAcks,advancedAckPoint + 1)) {
            advancedAckPoint += 1;
        }

        List<Integer> streams = toAbandon.stream()
                .filter(i->i.getData().getReliability().isOrdered())
                .map(i->i.getData().getStreamId())
                .collect(Collectors.toList());

        /*
        toAbandon.forEach(buff -> {
                    inFlight.remove(buff.getTsn());
                }
        );*/

        logger.info("Abandoning {} {} {}",toAbandon.stream()
                .map(BufferedSent::getTsn)
                .collect(Collectors.toList()),advancedAckPoint,remoteCumulativeTSN);

        //inFlight.keySet().removeAll(ids); Hmm, why does not chrome update based on chunk

        return new FwdAckPoint(advancedAckPoint,streams);
    }

    private boolean acknowledged(SackData data,BufferedSent inFlight) {
        return data.getCumulativeTSN() >= inFlight.getTsn() ||
                inGapAck(data.getCumulativeTSN(),data.getTsns(),inFlight.getTsn());
    }


    private boolean inGapAck(Long cumulativeTSN,List<GapAck> acks,long inflightTSN) {
        return acks.stream()
                .reduce(false,
                        (a,b) -> b.inRange(inflightTSN - cumulativeTSN),
                        (a,b) -> a || b);
    }

    /**
     *
     * Move messages to inflight
     *
     * @return messages to put on wire
     */
    public List<BufferedSent> getDataToSend() {
        ArrayList<BufferedSent> toSend = new ArrayList<>();
        synchronized (lock) {
            //Make more efficient later
            int data = inFlight.values().stream()
                    .map(i->i.getData().getSctpPayload().length)
                    .reduce(0,Integer::sum);

            int cwndDiff = congestionWindow.get().getCwnd() - data;

            while (!queue.isEmpty() &&
                    cwndDiff > queue.element().getData().getSctpPayload().length &&
                    remoteBufferSize > queue.element().getData().getSctpPayload().length) {
                BufferedSent buff = queue.remove();
                BufferedSent sent = buff.send();
                cwndDiff -= buff.getData().getSctpPayload().length;
                capacity.accumulateAndGet(buff.getData().getSctpPayload().length,(a,b)->a+b);
                inFlight.put(buff.getTsn(), sent);
                toSend.add(sent);
            }
            bytesSent += toSend.stream()
                    .map(i->i.getData().getSctpPayload().length)
                    .reduce(0,Integer::sum);

            //logger.info("After getting messages to send inflight:" + inFlight + " queue: " + queue.size());
        }

        return toSend;
    }




    /**
     * Pull earliest message for retransmission
     *
     * @return first message based on TSN which is in flight.
     */
    public RetransmitData getDataToRetransmit() {
        synchronized (lock) {
            congestionWindow.updateAndGet(Congestion::retransmissionTimeout);

            FwdAckPoint fwdAckPoint = abandonExpiredPackets(Collections.emptyList());

            List<BufferedSent> bufferedSents = inFlight.values().stream()
                    .filter(BufferedSent::canResend)
                    .min(BufferedSent::compareTo)
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());

            //Mark number of resends and time
            bufferedSents.forEach(i->
                inFlight.computeIfPresent(i.getTsn(),(key,value) -> value.resend())
            );

            return new RetransmitData(bufferedSents,fwdAckPoint,remoteCumulativeTSN);
        }
    }




}
