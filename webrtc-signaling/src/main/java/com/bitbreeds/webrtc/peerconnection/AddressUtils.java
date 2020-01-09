package com.bitbreeds.webrtc.peerconnection;/*
 *
 * Copyright (c) 09/01/2020, Jonas Waage
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AddressUtils {

    private static final Logger logger = LoggerFactory.getLogger(AddressUtils.class);

    public static InetAddress findLocalAddress() throws UnknownHostException {
        String name = InetAddress.getLocalHost().getHostName();
        List<InetAddress> address = Arrays.asList(InetAddress.getAllByName(name));
        List<InetAddress> nonLoopback = address.stream()
                .filter(i-> !i.isLoopbackAddress())
                .filter(i -> i instanceof Inet4Address)
                .collect(Collectors.toList());

        InetAddress selected = nonLoopback.stream().findFirst().orElseThrow(() -> new IllegalStateException(""));

        logger.info("Picking {} from {}",selected,nonLoopback);
        return selected;
    }

    public static String findAddress() {
        String adr = System.getProperty("com.bitbreeds.ip");
        if(adr != null) {
            logger.info("Using configured address {}",adr);
            return adr;
        }
        else {
            try {
                String resolved = findLocalAddress().getHostAddress();
                logger.info("Using resolved address {}",resolved);
                return resolved;
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Failed to resolve a local address",e);
            }
        }
    }
}
