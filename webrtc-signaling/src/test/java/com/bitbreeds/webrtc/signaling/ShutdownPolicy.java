package com.bitbreeds.webrtc.signaling;/*
 *
 * Copyright (c) 16/01/2020, Jonas Waage
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

import com.bitbreeds.webrtc.peerconnection.SimplePeerServer;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.spi.RoutePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class ShutdownPolicy implements RoutePolicy {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final SimplePeerServer simplePeerServer;

    public ShutdownPolicy(SimplePeerServer simplePeerServer) {
        this.simplePeerServer = Objects.requireNonNull(simplePeerServer);
    }

    @Override
    public void onInit(Route route) {

    }

    @Override
    public void onRemove(Route route) {

    }

    @Override
    public void onStart(Route route) {

    }

    @Override
    public void onStop(Route route) {
        try {
            simplePeerServer.shutDown();
        } catch (RuntimeException e) {
            logger.error("Error when stopping route {}",route.getId(),e);
        }
    }

    @Override
    public void onSuspend(Route route) {

    }

    @Override
    public void onResume(Route route) {

    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {

    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {

    }
}
