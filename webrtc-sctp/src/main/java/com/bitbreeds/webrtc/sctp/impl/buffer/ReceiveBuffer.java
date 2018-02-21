package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.common.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.common.SackUtil;
import com.bitbreeds.webrtc.sctp.impl.DataStorage;
import com.bitbreeds.webrtc.sctp.model.SCTPOrderFlag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
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
 *
 *
 * Initial state
 * -------------------------
 * 0
 * -
 * 0
 * -
 * 0
 * -
 * 0
 * -
 * 0
 * -
 * 0
 * -
 * 0
 * -
 * 0
 * -------------------------
 *
 * receive TSN 17  with 17 % 8 = 1
 *
 * -------------------------
 * 0
 * -
 * 17 + data, RECEIVED
 * -
 * 0
 * -
 * 0
 * -
 * 0
 * -
 * 0
 * -
 * 0
 * -
 * 0
 * -------------------------
 *
 *
 * Must handle TSN rollover, but not now initially
 *
 */
public class ReceiveBuffer {


    private final Object lock = new Object();
    private final Buffered[] buffer;
    private int capacity;

    private long cumulativeTSN;

    private long maxReceivedTSN;

    private long lowestDelivered;

    private List<Long> duplicates;

    public ReceiveBuffer(int bufferSize,int capacity, long initialTSN) {
        if(bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer must be above 0, is " + bufferSize);
        }
        this.buffer = new Buffered[bufferSize];
        this.capacity = capacity;
        this.cumulativeTSN = initialTSN;
        this.maxReceivedTSN = initialTSN;
        this.lowestDelivered = initialTSN;
        this.duplicates = new ArrayList<>();
    }

    /**
     * Store received message at TSN % size;
     *
     * @param data data to store
     */
    public void store(DataStorage data) {
        int position = posFromTSN(data.getTSN());
        synchronized (lock) {
            Buffered old = buffer[position];
            if(old == null || old.canBeOverwritten()) {
                buffer[position] = new Buffered(data,BufferedState.RECEIVED,DeliveredState.READY);
                this.maxReceivedTSN = Math.max(this.maxReceivedTSN,data.getTSN());
                this.capacity -= data.getPayload().length;
            }
            else if(data.getTSN() == old.getData().getTSN()){
                duplicates.add(data.getTSN());
            }
            else {
                /*
                 * Only malicious implementations should hit this unless we use a very small buffer
                 */
                throw new OutOfBufferSpaceError("Can not store since out og buffer space");
            }
        }
    }

    private int posFromTSN(long tsn) {
       return (int)(tsn % buffer.length);
    }

    private Buffered getBuffered(long tsn) {
        return buffer[posFromTSN(tsn)];
    }

    private void setBuffered(long tsn,Buffered buffered) {
        buffer[posFromTSN(tsn)] = buffered;
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
            data = new SackData(newCumulativeTSN,received,duplicates,capacity);
            duplicates = new ArrayList<>();
        }
        return data;
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
            Buffered bf = getBuffered(this.cumulativeTSN + i);
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
        for (int i = 0; i < diff; i++) {
            long tsn = this.cumulativeTSN + i;
            Buffered bf = getBuffered(tsn);
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
            Buffered bf = getBuffered(tsn);
            if (bf != null && !bf.canBeOverwritten()) {
                data.add(bf.getData().getTSN());
                setBuffered(tsn,bf.acknowledge());
            }
        }
        return data;
    }


    /**
     *
     * @return sack data
     */
    public List<Deliverable> getMessagesForDelivery() {
        List<Deliverable> dl = new ArrayList<>();
        synchronized (lock) {
            int diff = (int)(maxReceivedTSN - lowestDelivered);
            for (int i = 1; i<=diff ;i++) {
                long tsn = lowestDelivered+i;
                Buffered bf = getBuffered(tsn);
                if(bf != null) {
                    if(bf.readyForUnorderedDelivery()) {
                        if (bf.getData().getFlag().isUnFragmented()) {
                            dl.add(new Deliverable(bf.getData()));
                            setBuffered(tsn, bf.deliver());
                        }
                        else {
                            //Handle defrag
                        }
                    } else {
                        //Handle ordered
                    }
                }
            }
            for (int i = 1; i < dl.size(); i++) {
                Buffered vf = getBuffered(lowestDelivered + i);
                if (vf != null && vf.isDelivered()) {
                    lowestDelivered++;
                } else {
                    break;
                }
            }


        }
        return dl;
    }




}
