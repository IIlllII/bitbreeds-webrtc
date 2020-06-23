package com.bitbreeds.webrtc.sctp.model;

import com.bitbreeds.webrtc.common.SignalUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * Copyright (c) 19/05/16, Jonas Waage
 */


/**
 * <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-channel-13#section-6">webrtc-sctp</a>
 */
public class SCTPMessage {

    private final SCTPHeader header;
    private final List<SCTPChunk> chunks;


    public SCTPMessage(SCTPHeader header, List<SCTPChunk> chunks) {
        this.header = header;
        this.chunks = chunks;
    }

    public static SCTPMessage fromBytes(byte[] bytes) {
        SCTPHeader hdr = SCTPHeader.fromBytes(Arrays.copyOfRange(bytes,0,12));
        List<SCTPChunk> chunks = new ArrayList<>();

        int initial = 12;
        //while(bytes.length > initial) {
        SCTPChunk chunk = SCTPChunk.fromBytes(Arrays.copyOfRange(bytes,initial,bytes.length));
        chunks.add(chunk);

        initial = initial + SignalUtil.multipleOfFour(chunk.getLength());
        while(initial < bytes.length) {
            chunk = SCTPChunk.fromBytes(Arrays.copyOfRange(bytes,initial,bytes.length));
            if(chunk.getLength() < 1) {
                break; //Must brake if
            }
            chunks.add(chunk);
            initial = initial + SignalUtil.multipleOfFour(chunk.getLength());
        }

        return new SCTPMessage(hdr,chunks);
    }


    public byte[] toBytes() {
        ArrayList<byte[]> bt = new ArrayList<>();
        bt.add(header.toBytes());
        chunks.forEach(i->bt.add(i.toBytes()));
        return SignalUtil.joinBytesArrays(bt);
    }

    public SCTPHeader getHeader() {
        return header;
    }

    public List<SCTPChunk> getChunks() {
        return chunks;
    }

    @Override
    public String toString() {
        return "SCTPMessage{" +
                "header=" + header +
                ", chunks=" + chunks +
                '}';
    }
}
