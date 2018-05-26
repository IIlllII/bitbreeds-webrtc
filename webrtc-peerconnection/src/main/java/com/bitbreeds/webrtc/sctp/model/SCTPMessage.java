package com.bitbreeds.webrtc.sctp.model;

import com.bitbreeds.webrtc.common.SignalUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
 * {@link <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-channel-13#section-6">webrtc-sctp</a>}
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
