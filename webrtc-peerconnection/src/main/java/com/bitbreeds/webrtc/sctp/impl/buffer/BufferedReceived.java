package com.bitbreeds.webrtc.sctp.impl.buffer;

import com.bitbreeds.webrtc.model.webrtc.Deliverable;
import com.bitbreeds.webrtc.sctp.impl.model.ReceivedData;

/*
 * Copyright (c) 19/02/2018, Jonas Waage
 */


public class BufferedReceived implements Comparable<BufferedReceived>{

    private final ReceivedData data;
    private final ReceiveBufferedState bufferState;
    private final DeliveredState deliverState;

    public BufferedReceived(
            ReceivedData data,
            ReceiveBufferedState state,
            DeliveredState deliverState) {
        this.data = data;
        this.bufferState = state;
        this.deliverState = deliverState;
    }

    public Deliverable toDeliverable() {
        return new Deliverable(
                data.getPayload(),
                data.getStreamSequence(),
                data.getStreamId(),
                data.getProtocolId());
    }

    public ReceivedData getData() {
        return data;
    }

    public ReceiveBufferedState getState() {
        return bufferState;
    }

    public boolean canBeOverwritten() {
        return ReceiveBufferedState.FINISHED.equals(bufferState) && DeliveredState.DELIVERED.equals(deliverState);
    }

    public BufferedReceived acknowledge() {
        return new BufferedReceived(data, ReceiveBufferedState.ACKED,deliverState);
    }

    public BufferedReceived finish() {
        return new BufferedReceived(data, ReceiveBufferedState.FINISHED,deliverState);
    }

    /**
     * Has been delivered to user
     * @return delivered buffer
     */
    public BufferedReceived deliver() {
        return new BufferedReceived(data,bufferState,DeliveredState.DELIVERED);
    }

    public boolean readyForUnorderedDelivery() {
        return DeliveredState.READY.equals(deliverState) && getData().getFlag().isUnordered();
    }

    public boolean readyForOrderedDelivery() {
        return DeliveredState.READY.equals(deliverState) && getData().getFlag().isOrdered();
    }

    public boolean isDelivered() {
        return DeliveredState.DELIVERED.equals(deliverState);
    }

    @Override
    public String toString() {
        return "BufferedReceived{" +
                "data=" + data +
                ", bufferState=" + bufferState +
                ", deliverState=" + deliverState +
                '}';
    }

    @Override
    public int compareTo(BufferedReceived o) {
        return this.getData().compareTo(o.getData());
    }
}
