package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.model.sctp.SackUtil;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.model.webrtc.Deliverable;
import com.bitbreeds.webrtc.sctp.error.DroppedDataException;
import com.bitbreeds.webrtc.sctp.impl.model.ReceivedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
 * Buffer to store SCTP messages for delivery defrag and creation of SACK
 * <a href="https://tools.ietf.org/html/rfc4960#section-6.2.1">SCTP sack</a>
 * <a href="https://tools.ietf.org/html/rfc2581#section-4.2">TCP congestion control</a>
 *
 * TODO Must handle TSN and Stream Sequence id rollover
 *
 * TODO Must handle delivery to different ordered or unordered streams/datachannels
 *
 */
public class ReceiveBuffer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Object lock = new Object();
    private final BufferedReceived[] buffer;
    private int capacity;

    private long cumulativeTSN; //Highest returned TSN

    private long maxReceivedTSN; //Largest received TSN

    private long lowestDelivered; //Lowest delivered (needed due to do defragmentation)

    private List<Long> duplicates;

    private long receivedBytes = 0;
    private long deliveredBytes = 0;
    private boolean initialReceived = false;

    private Map<Integer,Integer> orderedStreams = new HashMap<>();

    public ReceiveBuffer(int bufferSize,int capacity) {
        if(bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer must be above 0, is " + bufferSize);
        }
        if(capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be above 0, is " + capacity);
        }
        this.buffer = new BufferedReceived[bufferSize];
        this.capacity = capacity;
        this.cumulativeTSN = -1;
        this.maxReceivedTSN = -1;
        this.lowestDelivered = -1;
        this.duplicates = new ArrayList<>();
    }

    /**
     *
     * These getters are for monitoring, and can not be fully trusted
     * since they are not synchronized
     */
    public long getReceivedBytes() {
        return receivedBytes;
    }

    public long getDeliveredBytes() {
        return deliveredBytes;
    }

    public long getCumulativeTSN() {
        return cumulativeTSN;
    }

    public long getCapacity() {
        return capacity;
    }


    /**
     *
     * @param initialTSN first TSN of the connection
     */
    public void setInitialTSN(long initialTSN) {
        synchronized (lock) {
            this.cumulativeTSN = initialTSN-1;
            this.maxReceivedTSN = initialTSN-1;
            this.lowestDelivered = initialTSN-1;
            this.initialReceived = true;
        }
    }

    /**
     * Store received message at TSN % size;
     *
     * @param data data to store
     */
    public void store(ReceivedData data) {
        if(Math.abs(data.getTSN() - cumulativeTSN) > buffer.length*2) {
            throw new IllegalArgumentException("TSN " + data.getTSN() + " is not in the expected range");
        }

        int position = posFromTSN(data.getTSN());
        synchronized (lock) {
            logger.debug("Storing {} with {} in {}",data,data.getTSN(),position);
            if(!initialReceived) {
                throw new InitialMessageNotReceived("Initial SCTP message not received yet, no initial TSN");
            }


            BufferedReceived old = buffer[position];
            if(data.getTSN() <= cumulativeTSN) {
                duplicates.add(data.getTSN());
                logger.info("{} was a duplicate, ignore",data.getTSN());
            }
            else if(old == null || old.canBeOverwritten()) {
                buffer[position] = new BufferedReceived(data, ReceiveBufferedState.RECEIVED,DeliveredState.READY);
                this.maxReceivedTSN = Math.max(this.maxReceivedTSN,data.getTSN());
                this.capacity -= data.getPayload().length;
                this.receivedBytes += data.getPayload().length;
            }
            else if(data.getTSN() == old.getData().getTSN()){
                duplicates.add(data.getTSN());
                logger.info("{} was a duplicate, ignore",data.getTSN());
            }
            else {
                /*
                 * Only malicious implementations should hit this unless a very small buffer is used
                 */
                List<BufferedReceived> bad = Arrays.stream(buffer)
                        .filter(i -> i != null && !i.canBeOverwritten())
                        .collect(Collectors.toList());

                throw new OutOfBufferSpaceError("Can not store since out of buffer space: "
                + " Buffer " + bad
                + " Capacity: " + this.capacity
                + " TSN: " + this.cumulativeTSN + " Position" + position + " TSN IN " + data.getTSN());
            }
        }
    }

    /**
     * @return sack data for creating complete SACK
     */
    public SackData getSackDataToSend() {
        SackData data;
        synchronized (lock) {
            long newCumulativeTSN = findNewCumulativeTSN();
            updateCumulativeTSN(newCumulativeTSN);
            Set<Long> received = getReceived();
            data = new SackData(
                    newCumulativeTSN,
                    SackUtil.getGapAckList(newCumulativeTSN,received),
                    duplicates,
                    capacity);
            duplicates = new ArrayList<>();
        }
        return data;
    }


    public ForwardAccResult receiveForwardAckPoint(long advancedAckPoint) {
        synchronized (lock) {
            if (advancedAckPoint > cumulativeTSN) {

                if (Math.abs(advancedAckPoint - cumulativeTSN) > buffer.length * 2) {
                    throw new IllegalArgumentException("Bad ack");
                }

                long diff = advancedAckPoint - cumulativeTSN;
                List<BufferedReceived> toDeliver = new ArrayList<>();
                for (int i = 1; i <= diff; i++) {
                    long tsn = cumulativeTSN + i;
                    BufferedReceived bf = getBuffered(tsn);
                    if (bf != null) {
                        if (!bf.isDelivered()) {
                            toDeliver.add(bf);
                            setBuffered(tsn,bf.deliver().finish());
                        }
                        else {
                            setBuffered(tsn,bf.finish());
                        }
                    }
                }

                List<BufferedReceived> unfragmented = toDeliver
                        .stream()
                        .filter(i->i.getData().getFlag().isUnFragmented())
                        .sorted()
                        .collect(Collectors.toList());

                List<Deliverable> deliverables = new ArrayList<>();

                unfragmented.forEach(i-> {
                    if(i.getData().getFlag().isUnordered()) {
                        Deliverable dl = i.toDeliverable();
                        deliverables.add(dl);
                    }
                    else {
                        Deliverable dl = i.toDeliverable();
                        if(nextInStream(i.getData())) {
                            orderedStreams.put(i.getData().getStreamId(),i.getData().getStreamSequence()+1);
                            deliverables.add(dl);
                        }
                        else {
                            throw new DroppedDataException("Ordered stream dropped data");
                        }
                    }
                });

                cumulativeTSN = advancedAckPoint;
                return new ForwardAccResult(getSackDataToSend(),deliverables);
            }

            return new ForwardAccResult(getSackDataToSend(),Collections.emptyList());
        }
    }

    /**
     * @return get messages for next layer
     */
    public List<Deliverable> getMessagesForDelivery() {
        List<Deliverable> dl = new ArrayList<>();
        synchronized (lock) {
            int diff = (int)(maxReceivedTSN - lowestDelivered);
            for (int i = 1; i <= diff ;i++) {
                long tsn = lowestDelivered+i;
                BufferedReceived bf = getBuffered(tsn);
                if (bf != null) {
                    if (bf.readyForUnorderedDelivery()) {
                        if (bf.getData().getFlag().isUnFragmented()) {
                            //Add to list of deliverables
                            dl.add(bf.toDeliverable());
                            setBuffered(tsn, bf.deliver());
                        } else {
                            if (bf.getData().getFlag().isStart()) {
                                finishFragment(bf)
                                        .ifPresent(dl::add);
                            }
                        }
                    } else if (bf.readyForOrderedDelivery()) {
                        if (bf.getData().getFlag().isUnFragmented()) {
                            receiveUnfragmentedBuffered(bf)
                                    .ifPresent(deliverable -> {
                                        dl.add(deliverable);
                                        setBuffered(tsn, bf.deliver());
                                    });
                        } else {
                            if (bf.getData().getFlag().isStart()) {
                                if (nextInStream(bf.getData())) {
                                    finishFragment(bf)
                                            .ifPresent(dl::add);
                                }
                            }
                        }
                    }
                }
            }
            updateLowestDelivered(dl);
            int sum = dl.stream()
                    .map(i -> i.getData().length)
                    .reduce(0, Integer::sum);

            this.deliveredBytes += sum;
            this.capacity += sum;
        }
        return dl;
    }


    /*
     *
     * @param ds
     * @return
     */
    private boolean nextInStream(ReceivedData ds) {
        Integer sq = orderedStreams.get(ds.getStreamId());
        return (sq == null && ds.getStreamSequence() == 0) || (sq != null && sq == ds.getStreamSequence());
    }

    /*
     *
     * @param buffered data
     * @return
     */
    private Optional<Deliverable> receiveUnfragmentedBuffered(BufferedReceived buffered) {
        if(nextInStream(buffered.getData())) {
            setBuffered(buffered.getData().getTSN(), buffered.deliver());
            orderedStreams.put(buffered.getData().getStreamId(),buffered.getData().getStreamSequence()+1);
            return Optional.of(buffered.toDeliverable());
        }
        return Optional.empty();
    }

    /**
     *
     * Update lowest deliverable, so we can use it for calc later
     * @param deliverables current deliverables
     */
    private void updateLowestDelivered(List<Deliverable> deliverables) {
        int fragments = deliverables.stream()
                .map(Deliverable::getOriginalFragmentNumber)
                .reduce(0,Integer::sum);

        for (int i = 1; i <= fragments; i++) {
            BufferedReceived vf = getBuffered(lowestDelivered + i);
            if (vf != null && vf.isDelivered()) {
                lowestDelivered++;
            } else {
                break;
            }
        }
    }

    /**
     * Retrieve position from TSN
     * @param tsn to get position for
     * @return position
     */
    private int posFromTSN(long tsn) {
        return (int)(tsn % buffer.length);
    }

    /**
     *
     * @param tsn to get buffered data for
     * @return buffered data for tsn, null if no data
     */
    private BufferedReceived getBuffered(long tsn) {
        return buffer[posFromTSN(tsn)];
    }

    /**
     *
     * @param tsn to set buffered data for
     * @param buffered data
     */
    private void setBuffered(long tsn,BufferedReceived buffered) {
        buffer[posFromTSN(tsn)] = buffered;
    }



    /**
     * Not thread safe, must happen in lock
     *
     * @return new cumulative tsn
     */
    private long findNewCumulativeTSN() {
        long newCumulativeTSN = cumulativeTSN;
        long diff = this.maxReceivedTSN - cumulativeTSN;
        for (int i = 1; i <= diff; i++) {
            BufferedReceived bf = getBuffered(this.cumulativeTSN + i);
            if (bf != null && !bf.canBeOverwritten()) {
                newCumulativeTSN++;
            } else {
                break;
            }
        }
        return newCumulativeTSN;
    }

    /**
     * Not thread safe, must happen in lock
     */
    private void updateCumulativeTSN(long newCumulativeTSN) {
        long diff = newCumulativeTSN - cumulativeTSN;
        for (int i = 0; i <= diff; i++) {
            long tsn = this.cumulativeTSN + i;
            BufferedReceived bf = getBuffered(tsn);
            if(bf != null) {
                setBuffered(tsn, bf.finish());
            }
        }
        this.cumulativeTSN = newCumulativeTSN;
    }

    /**
     * Not thread safe, must happen in lock
     */
    private Set<Long> getReceived() {
        Set<Long> data = new HashSet<>();
        int diff = (int) (maxReceivedTSN - cumulativeTSN);
        for (int i = 1; i <= diff; i++) {
            long tsn = cumulativeTSN + i;
            BufferedReceived bf = getBuffered(tsn);
            if (bf != null && !bf.canBeOverwritten()) {
                data.add(bf.getData().getTSN());
                setBuffered(tsn,bf.acknowledge());
            }
        }
        return data;
    }



    private void setDelivered(List<Long> tsns) {
        tsns.forEach(dlTsn -> {
                    BufferedReceived xs = getBuffered(dlTsn);
                    setBuffered(dlTsn,xs.deliver());
                }
        );
    }

    private Deliverable fromTsns(List<Long> tsns) {
        List<BufferedReceived> buffered = tsns.stream()
                .map(this::getBuffered).collect(Collectors.toList());

        List<byte[]> data = buffered.stream()
                .map(j->(j.getData()).getPayload())
                .collect(Collectors.toList());

        BufferedReceived first = buffered.get(0);

        return new Deliverable(
                SignalUtil.joinBytesArrays(data),
                data.size(),
                first.getData().getStreamId(),
                first.getData().getProtocolId());
    }


    /**
     *
     * @param start the start fragment
     * @return deliverable defragmented message
     */
    private Optional<Deliverable> finishFragment(BufferedReceived start) {
        if(!start.getData().getFlag().isStart()) {
            return Optional.empty();
        }
        else {
            long tsn = start.getData().getTSN();
            List<Long> good = new ArrayList<>();
            good.add(tsn);

            for (long i = tsn + 1; i <= maxReceivedTSN; i++) {
                BufferedReceived next = getBuffered(i);
                if (next != null && !next.canBeOverwritten()) {
                    ReceivedData ds = next.getData();
                    if(ds.getFlag().isMiddle()) {
                        good.add(ds.getTSN());
                    } else if(ds.getFlag().isEnd()) {
                        good.add(ds.getTSN());
                        Deliverable del = fromTsns(good);
                        setDelivered(good);
                        return Optional.of(del);
                    }
                } else {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }
    }




}
