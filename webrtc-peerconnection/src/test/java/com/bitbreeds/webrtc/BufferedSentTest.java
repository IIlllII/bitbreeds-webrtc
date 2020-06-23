package com.bitbreeds.webrtc;/*
 * Copyright (c) Jonas Waage 17/02/2020
 */

import com.bitbreeds.webrtc.model.sctp.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.sctp.impl.SCTPReliability;
import com.bitbreeds.webrtc.sctp.impl.buffer.BufferedSent;
import com.bitbreeds.webrtc.sctp.impl.buffer.SendBufferedState;
import com.bitbreeds.webrtc.sctp.impl.model.SendData;
import com.bitbreeds.webrtc.sctp.model.SCTPOrderFlag;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BufferedSentTest {


    @Test
    public void testCanNotResend() {
        byte[] arg = new byte[] {1,2};
        BufferedSent bufferedSent = new BufferedSent(
                new SendData(12,
                        0,
                        -1,
                        SCTPOrderFlag.UNORDERED_UNFRAGMENTED,
                        SCTPPayloadProtocolId.WEBRTC_BINARY,
                        SCTPReliability.createMaxRetransmits(0,false),
                        arg),
                SendBufferedState.SENT,
                Instant.now(),Instant.now(),
                12,
                0,
                3,
                true);

        assertFalse(bufferedSent.canResend());
        assertFalse(bufferedSent.canFastResend());

    }


    @Test
    public void testCanResend() {
        byte[] arg = new byte[] {1,2};
        BufferedSent bufferedSent = new BufferedSent(
                new SendData(12,
                        0,
                        -1,
                        SCTPOrderFlag.UNORDERED_UNFRAGMENTED,
                        SCTPPayloadProtocolId.WEBRTC_BINARY,
                        SCTPReliability.createUnordered(),
                        arg),
                SendBufferedState.SENT,
                Instant.now(),Instant.now(),
                12,
                0,
                3,
                false);

        assertTrue(bufferedSent.canResend());
        assertTrue(bufferedSent.canFastResend());

    }

}
