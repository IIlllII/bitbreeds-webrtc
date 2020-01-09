package com.bitbreeds.webrtc.dtls;

import com.bitbreeds.webrtc.common.ByteRange;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.peerconnection.UserData;
import com.bitbreeds.webrtc.stun.BindingService;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.TlsFatalAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;

/*
 * Copyright (c) 28/02/2017, Jonas Waage
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
 * Handles DTLS over UDP.
 *
 * If we receive a STUN message, we reply and do not pass the message to the layer below
 *
 *                     +----------------+
 *                     |        [0..3] -+--> forward to STUN
 *                     |                |
 *                     |      [16..19] -+--> forward to ZRTP
 *                     |                |
 *         packet -->  |      [20..63] -+--> forward to DTLS
 *                     |                |
 *                     |      [64..79] -+--> forward to TURN Channel
 *                     |                |
 *                     |    [128..191] -+--> forward to RTP/RTCP
 *                     +----------------+
 *
 */
public class DtlsMuxStunTransport implements DatagramTransport {

    private final static int IP_BYTES = 20;
    private final static int IP_MAX_BYTES = IP_BYTES + 64;
    private final static int UDP_BYTES = 8;

    private final DatagramSocket socket;
    private final int receiveLimit, sendLimit;

    private final UserData local;

    private final BindingService bindingService = new BindingService();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public DtlsMuxStunTransport(UserData local, DatagramSocket socket, int mtu) throws IOException {
        this.local = local;
        this.socket = socket;
        this.receiveLimit = mtu - IP_BYTES - UDP_BYTES;
        this.sendLimit = mtu - IP_MAX_BYTES - UDP_BYTES;
        if (!socket.isBound() || !socket.isConnected())
        {
            throw new IllegalArgumentException("Unbound socket");
        }
    }


    public int receive(byte[] buf, int off, int len, int waitMillis)
            throws IOException
    {
        socket.setSoTimeout(waitMillis);
        DatagramPacket packet = new DatagramPacket(buf, off, len);
        socket.receive(packet);
        logger.debug("Socket read msg: {}", Hex.encodeHexString(SignalUtil.copyRange(packet.getData(), new ByteRange(0,packet.getLength()))));
        if(buf.length >= 2 && buf[0] == 0 && buf[1] == 1) {
            SocketAddress currentSender = packet.getSocketAddress();

            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

            byte[] out = bindingService.processBindingRequest(
                    data,
                    local.getUserName(),
                    local.getPassword(),
                    (InetSocketAddress) currentSender
            );

            logger.debug("Stun packet received, responding with {}",Hex.encodeHexString(out));
            this.send(out,0,out.length);
            return 0; //We do not want DTLS to process (not that it will anyway), so we return 0 here.
        }
        else if(buf.length >= 1 && SignalUtil.unsign(buf[0]) >= 19 && SignalUtil.unsign(buf[0]) <= 63) {
            logger.debug("DTLS " + SignalUtil.unsign(buf[0]) + " returning length");
            return packet.getLength();
        }
        else {
            logger.debug("Non stun/dtls packet received, returning 0 length");
            return 0;
        }
    }

    public void send(byte[] buf, int off, int len)
            throws IOException {
        if (len > getSendLimit()) {
            throw new TlsFatalAlert(AlertDescription.record_overflow);
        }
        DatagramPacket packet = new DatagramPacket(buf, off, len);
        socket.send(packet);
    }

    public void close() throws IOException {
        logger.info("Socket closed by internal code");
        socket.close();
    }


    public int getReceiveLimit() {
        return receiveLimit;
    }

    public int getSendLimit() {
        return sendLimit;
    }

}
