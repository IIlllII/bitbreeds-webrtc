package com.bitbreeds.webrtc.dtls;

import com.bitbreeds.webrtc.common.ByteRange;
import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.signaling.BindingService;
import com.bitbreeds.webrtc.signaling.PeerConnection;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.tls.AlertDescription;
import org.bouncycastle.crypto.tls.DTLSServerProtocol;
import org.bouncycastle.crypto.tls.DatagramTransport;
import org.bouncycastle.crypto.tls.TlsFatalAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.security.SecureRandom;
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
 */
public class DtlsMuxStunTransport implements DatagramTransport {

    private final static int IP_BYTES = 20;
    private final static int IP_MAX_BYTES = IP_BYTES + 64;
    private final static int UDP_BYTES = 8;

    private final DatagramSocket socket;
    private final int receiveLimit, sendLimit;

    private final PeerConnection parent;

    private final BindingService bindingService = new BindingService();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public DtlsMuxStunTransport(PeerConnection parent, DatagramSocket socket, int mtu) throws IOException {
        this.parent = parent;
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
                    parent.getLocal().getUserName(),
                    parent.getLocal().getPassword(),
                    (InetSocketAddress) currentSender
            );

            logger.debug("Stun packet received, responding with {}",Hex.encodeHexString(out));
            this.send(out,0,out.length);
            return 0; //We do not want DTLS to process (not that it will anyway), so we return 0 here.
        } else {
            logger.debug("Non stun packet received, returning length");
        }

        return packet.getLength();
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
        socket.close();
    }


    public int getReceiveLimit() {
        return receiveLimit;
    }

    public int getSendLimit() {
        return sendLimit;
    }

}
