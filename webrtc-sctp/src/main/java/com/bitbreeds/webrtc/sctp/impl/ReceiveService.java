package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.SackUtil;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.sctp.model.*;
import org.pcollections.HashPMap;
import org.pcollections.HashTreePMap;
import org.pcollections.MapPSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.bitbreeds.webrtc.common.SignalUtil.*;

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
 * This class handles the state for received payloads and TSNs and creating a SACK from those.
 * This should implement the below, though currently it does probably not do so well.
 *
 * @see <a hred="https://tools.ietf.org/html/rfc4960#section-3.3.4">SCTP SACK spec</a>
 * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-protocol-09#section-8.2.1">datachannel spec</a>
 *
 *
 * TODO use better datastructures
 *
 */
public class ReceiveService {

    private static final Logger logger = LoggerFactory.getLogger(ReceiveService.class);
    public enum TsnStatus {DUPLICATE,FIRST};

    /**
     * Keeps fragments for reassembly on stream sequence number
     */
    private final ConcurrentHashMap<Long,FragmentReAssembler> fragmentsStore = new ConcurrentHashMap<>();

    /**
     * Ordered datastore
     */
    private final ConcurrentHashMap<Long,DataStorage> orderedStore = new ConcurrentHashMap<>();


    /**
     * A set of received TSNS
     * This uses a persistent collection, which is easy to work with
     * when we need to pull it and create a SACK.
     */
    private HashPMap<Long,DataStorage> dataStorageMapPSet = HashTreePMap.empty();

    /**
     * A list of duplicates since the last SACK
     * Reset when creating SACK
     */
    protected List<Long> duplicatesSinceLast = new ArrayList<>();

    /**
     * Mutex to access TSNS and duplicate data
     */
    private final Object sackMutex = new Object();

    /**
     * Received
     */
    protected volatile long cumulativeTSN = -1L;

    private final AtomicInteger bufferSize;
    private final SCTPImpl handler;

    /**
     * TSN which describes the next expected TSN.
     * Not needed for non ordered communication
     */
    private AtomicLong nextTSN = new AtomicLong(0);

    ReceiveService(SCTPImpl handler,int bufferSize) {
        this.bufferSize = new AtomicInteger(bufferSize);
        this.handler = handler;
    }

    /**
     * Data needed to create a SACK
     */
    private class SackData {
        final MapPSet<Long> tsns;
        final List<Long> duplicates;
        final int bufferLeft;

        SackData(
                MapPSet<Long> tsns,
                List<Long> duplicates,
                int bufferLeft) {
            this.tsns = tsns;
            this.duplicates = duplicates;
            this.bufferLeft = bufferLeft;
        }

        @Override
        public String toString() {
            return "SackData{" +
                    "tsns=" + tsns +
                    ", duplicates=" + duplicates +
                    ", bufferLeft=" + bufferLeft +
                    '}';
        }
    }

    /**
     *
     * @return list of received messages for batch sacking
     */
    private SackData getAndSetSackTSNList() {
        List<Long> duplicates;
        MapPSet<Long> tsnTmp;
        synchronized (sackMutex) {
            tsnTmp = MapPSet.from(dataStorageMapPSet);
            duplicates = duplicatesSinceLast;
            duplicatesSinceLast = new ArrayList<>();
        }
        return new SackData(tsnTmp,duplicates,bufferSize.get());
    }

    /**
     * We can remove all received TSNS with no gaps before them
     * @param l the highest TSNS with no gap before it.
     * @return true if new value, false otherwise
     */
    private boolean updateLowestTSN(long l) {

        /*
         * TODO Possibly move to less hot codepath later
         */
        Collection<DataStorage> ls = dataStorageMapPSet.values().stream()
                .filter( i -> TSNUtil.isBelow(i.getTSN(),l) )
                .collect(Collectors.toList());

        int sumBuffer = ls.stream()
                .map(i->i.getPayload().length)
                .reduce(0,Integer::sum);

        List<Long> keys = ls.stream()
                .map(DataStorage::getTSN)
                .collect(Collectors.toList());

        bufferSize.addAndGet(sumBuffer);

        synchronized (sackMutex) {
            if(cumulativeTSN == l) {
                return false;
            }
            else {
                dataStorageMapPSet = dataStorageMapPSet.minusAll(keys);
                cumulativeTSN = l;
                return true;
            }
        }

    }

    /**
     *
     * @return amount of free buffer in bytes
     */
    public int freeBufferSizeInBytes() {
        return bufferSize.get();
    }

    /**
     * Receive initial TSN
     */
    public void handleReceiveInitialTSN(long tsn) {
        nextTSN.set(tsn+1);
        dataStorageMapPSet = dataStorageMapPSet.plus(tsn,
                new DataStorage(
                        tsn,
                        -1,
                        -1,
                        null,
                        null,
                        new byte[0]));
    }

    /**
     * When we receive a new payload, we should attempt to deliver the next stored payloads
     */
    private void deliverAsManyOrderedAsPossible() {
        while(true) {
            DataStorage storage = orderedStore.remove(nextTSN.get());
            if(storage == null) {
                break;
            }
            else {
                if(storage.getFlag().isFragmented()) {
                    long ssn = storage.getStreamSequence();
                    FragmentReAssembler frag = fragmentsStore.get(ssn);
                    if(frag.isComplete()) {
                        List<byte[]> data = frag.completeOrderedMessage()
                                .stream()
                                .map(DataStorage::getPayload)
                                .collect(Collectors.toList());

                        byte[] msg = SignalUtil.joinBytesArrays(data);
                        fragmentsStore.remove(ssn);
                        handler.getDataChannel().runOnMessageOrdered(msg);
                        nextTSN.set(frag.lastTSN()+1);
                    }
                }
                else {
                    handler.getDataChannel().runOnMessageOrdered(storage.getPayload());
                    nextTSN.getAndIncrement();
                }
            }
        }


    }

    /**
     *
     * @param data data to store and evaluate
     * @return whether the TSN has been received before or not.
     */
    public TsnStatus handleReceive(DataStorage data) {
        Objects.requireNonNull(data);

        int lgt = data.getPayload().length;
        int free = freeBufferSizeInBytes();

        if(lgt > free) {
            throw new FullBufferException("Buffer has " + free + "bytes, received data of size " + lgt + " bytes");
        }

        TsnStatus status;
        synchronized (sackMutex) {
            long tsn = data.getTSN();
            if(dataStorageMapPSet.containsKey(tsn) ||
                    (tsn <= cumulativeTSN && Math.abs(tsn-cumulativeTSN) < TSNUtil.TSN_DIFF) ) {
                duplicatesSinceLast.add(tsn);
                status = TsnStatus.DUPLICATE;
            }
            else {
                bufferSize.addAndGet(-lgt);
                dataStorageMapPSet = dataStorageMapPSet.plus(tsn,data);
                status = TsnStatus.FIRST;
            }
        }

        if(!TsnStatus.DUPLICATE.equals(status)) {

            if (SCTPOrderFlag.UNORDERED_UNFRAGMENTED.equals(data.getFlag())) {
                //Send bytes Immediately to user layer
                handler.getDataChannel().runOnMessageUnordered(data.getPayload());
            }
            else if (SCTPOrderFlag.ORDERED_UNFRAGMENTED.equals(data.getFlag())) {
                //Use a local TSN == next and deliver immediately if right
                //Check if next in queue is in fragment store and deliver if they are
                if(data.getTSN() == nextTSN.get()) {
                    handler.getDataChannel().runOnMessageOrdered(data.getPayload());
                    nextTSN.getAndIncrement();
                }
                else {
                    orderedStore.put(data.getTSN(),data);
                    deliverAsManyOrderedAsPossible();
                }
            }
            else if (data.getFlag().isFragmented()) {
                long ssn = data.getStreamSequence();

                FragmentReAssembler result = fragmentsStore
                        .compute(ssn, (seq, frag) -> {
                                    if (frag != null) {
                                        return frag.addFragment(data);
                                    } else {
                                        return FragmentReAssembler.empty().addFragment(data);
                                    }
                                }
                        );

                if(data.getFlag().isStart() && data.getFlag().isOrdered()) {
                    orderedStore.put(data.getTSN(),data);
                }

                if (result.isComplete()) {
                    if (data.getFlag().isUnordered()) {
                        List<byte[]> outdata = result.completeOrderedMessage()
                                .stream()
                                .map(DataStorage::getPayload)
                                .collect(Collectors.toList());

                        byte[] msg = SignalUtil.joinBytesArrays(outdata);
                        fragmentsStore.remove(ssn);
                        handler.getDataChannel().runOnMessageUnordered(msg);
                    } else {
                        deliverAsManyOrderedAsPossible();
                    }
                }
            }
        }
        return status;
    }


    /**
     * @return attempt to create a SCTP SACK message.
     */
    public Optional<SCTPMessage> createSack(SCTPHeader header ) {

        SackData sackData = getAndSetSackTSNList(); //Pull sack data

        logger.trace("Got sack data: " + sackData);
        if(sackData.tsns.isEmpty()) {
            return Optional.empty();
        }

        //Find the minimum in the relevant window
        final Long min = sackData.tsns.stream()
                .reduce(TSNUtil::cmp)
                .orElseThrow(() -> new IllegalStateException("Should not happen!"));

        //Remove all non relevant data. If the tsn flipped this must happen
        Set<Long> relevant = sackData.tsns.stream().filter(i -> i >= min).collect(Collectors.toSet());

        //Calculate gap acks from only relevant data.
        List<SackUtil.GapAck> acks = SackUtil.getGapAckList(relevant);

        long lastBeforeGapTsn = acks.get(0).end;

        boolean updated = updateLowestTSN(lastBeforeGapTsn);

        /*
         * No sack needed already at latest
         */
        if(!updated && acks.size() == 1 && sackData.duplicates.size() == 0) {
            return Optional.empty();
        }

        List<byte[]> varData = new ArrayList<>();

        for(int i = 1; i<acks.size(); i++) {
            SackUtil.GapAck ack = acks.get(i);
            int start = (int)(ack.start - lastBeforeGapTsn);
            int end = (int)(ack.end - lastBeforeGapTsn);
            varData.add(twoBytesFromInt(start));
            varData.add(twoBytesFromInt(end));
        }

        for (Long l : sackData.duplicates) {
            varData.add(longToFourBytes(l));
        }

        HashMap<SCTPFixedAttributeType,SCTPFixedAttribute> fixed = new HashMap<>();
        SCTPFixedAttribute cum_tsn =
                new SCTPFixedAttribute(SCTPFixedAttributeType.CUMULATIVE_TSN_ACK,
                        longToFourBytes(lastBeforeGapTsn));

        SCTPFixedAttribute arcw =
                new SCTPFixedAttribute(SCTPFixedAttributeType.ARWC,
                        fourBytesFromInt(sackData.bufferLeft));

        SCTPFixedAttribute num_gap =
                new SCTPFixedAttribute(SCTPFixedAttributeType.NUM_GAP_BLOCKS,
                        twoBytesFromInt(varData.size()));

        SCTPFixedAttribute num_dupl =
                new SCTPFixedAttribute(SCTPFixedAttributeType.NUM_DUPLICATE,
                        twoBytesFromInt(sackData.duplicates.size()));


        fixed.put(SCTPFixedAttributeType.CUMULATIVE_TSN_ACK,
                cum_tsn);
        fixed.put(SCTPFixedAttributeType.ARWC,arcw);
        fixed.put(SCTPFixedAttributeType.NUM_GAP_BLOCKS,num_gap);
        fixed.put(SCTPFixedAttributeType.NUM_DUPLICATE,num_dupl);

        int sum = fixed.keySet().stream()
                .map(SCTPFixedAttributeType::getLgt).reduce(0, Integer::sum);

        byte[] data = joinBytesArrays(varData);

        SCTPChunk sack = new SCTPChunk(
                SCTPMessageType.SELECTIVE_ACK,
                SCTPOrderFlag.fromValue((byte)0),
                4 + sum + data.length,
                fixed,
                new HashMap<>(),
                SignalUtil.padToMultipleOfFour(data)
        );

        SCTPMessage msg = new SCTPMessage(header, Collections.singletonList(sack));

        logger.debug("Sending sack data: " + msg);
        return Optional.of(SCTPUtil.addChecksum(msg));
    }


}
