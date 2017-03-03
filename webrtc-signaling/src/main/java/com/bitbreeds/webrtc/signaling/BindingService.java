package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.common.SignalUtil;
import com.bitbreeds.webrtc.stun.*;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Copyright (c) 08/05/16, Jonas Waage
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
 * Service for creating a STUN response from a request
 */
public class BindingService  {

    private static final Logger logger = LoggerFactory.getLogger(BindingService.class);

    public byte[] processBindingRequest(
            byte[] data,
            String userName,
            String password,
            InetSocketAddress sender) {

        logger.trace("Input: " + Hex.encodeHexString(data));

        StunMessage msg = StunMessage.fromBytes(data);

        logger.trace("InputParsed: " + msg);

        byte[] content = SignalUtil.joinBytesArrays(
                SignalUtil.twoBytesFromInt(0x01),
                SignalUtil.xor(
                        SignalUtil.twoBytesFromInt(sender.getPort()),
                        Arrays.copyOf(msg.getHeader().getCookie(),2)),
                SignalUtil.xor(
                        sender.getAddress().getAddress(),
                        msg.getHeader().getCookie()
                        )
        );

        StunAttribute attr = new StunAttribute(
                StunAttributeTypeEnum.XOR_MAPPED_ADDRESS,content);

        StunAttribute user = msg.getAttributeSet().get(StunAttributeTypeEnum.USERNAME);
        String strUser = new String(user.toBytes()).split(":")[0].trim();

        msg.validate(password,data);

        HashMap<StunAttributeTypeEnum,StunAttribute> outSet = new HashMap<>();
        outSet.put(StunAttributeTypeEnum.XOR_MAPPED_ADDRESS,attr);
        outSet.putAll(msg.getAttributeSet());

        StunMessage output = StunMessage.fromData(
                StunRequestTypeEnum.BINDING_RESPONSE,
                msg.getHeader().getCookie(),
                msg.getHeader().getTransactionID(),
                outSet,
                true,
                true,
                strUser,
                password
        );

        byte[] bt = output.toBytes();
        logger.trace("Response: "+Hex.encodeHexString(bt));
        return bt;
    }



}
