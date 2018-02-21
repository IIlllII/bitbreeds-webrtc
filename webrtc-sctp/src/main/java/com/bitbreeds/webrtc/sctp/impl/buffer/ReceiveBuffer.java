package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.sctp.impl.DataStorage;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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
public class Buffer {

    private final static int MAX_MSG_REFS=1000;

    private final Object lock = new Object();
    private final Buffered[] buffer;
    private int capacity;

    private final long cumulativeTSN;

    private final Set<Long> duplicates;

    public Buffer(int capacity, long initialTSN) {
        this.buffer = new Buffered[MAX_MSG_REFS];
        this.capacity = capacity;
        this.cumulativeTSN = initialTSN;
        this.duplicates = new HashSet<>();
    }

    public void store(DataStorage storage) {
        int position = posFromTSN(storage.getTSN());
        synchronized (lock) {
            Buffered old = buffer[position];
            if(old == null || BufferedState.DELIVERED.equals(old.getState())) {
                buffer[position] = new Buffered(storage,BufferedState.RECEIVED);
                this.capacity -= storage.getPayload().length;
            }
            else {
                duplicates.add(storage.getTSN());
            }
        }
    }

    private int posFromTSN(long tsn) {
       return (int)(tsn % MAX_MSG_REFS);
    }

    /**
     *
     * @return sack data
     */
    public SackData getSackData() {
        int dupl = posFromTSN(cumulativeTSN);


        SackData data = new SackData(,)

    }





}
