package com.bitbreeds.webrtc.signaling;/*
 *
 * Copyright (c) 16/01/2020, Jonas Waage
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
