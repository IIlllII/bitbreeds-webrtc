package com.bitbreeds.webrtc.sctp.model;

/**
 * Copyright (c) 09/04/2018, Jonas Waage
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
 */
public enum SCTPState {
    CLOSED(false),
    ESTABLISHED(true),
    COOKIE_ECHOED(false),
    COOKIE_WAIT(false),
    SHUTDOWN_ACK_SENT(false),
    SHUTDOWN_PENDING(true),
    SHUTDOWN_SENT(true),
    SHUTDOWN_RECEIVED(false);

    private boolean canSendAndReceive;

    SCTPState(boolean canSendAndReceive) {
        this.canSendAndReceive = canSendAndReceive;
    }

    public boolean isCanSendAndReceive() {
        return canSendAndReceive;
    }

    /*
     * All legal transitions covered below
     */
    public SCTPState moveToEstablished() {
        if(CLOSED.equals(this)) {
            return ESTABLISHED;
        }
        else {
            throw new IllegalStateException("Illegal transition " +ESTABLISHED+ " from " + this);
        }
    }

    public SCTPState abort() {
        return CLOSED;
    }

    public SCTPState shutDown() {
        if(ESTABLISHED.equals(this)) {
            return SHUTDOWN_PENDING;
        }
        else {
            throw new IllegalStateException("Illegal transition " +SHUTDOWN_PENDING+ " from " + this);
        }
    }


    public SCTPState receivedShutdown() {
        if(ESTABLISHED.equals(this)) {
            return SHUTDOWN_RECEIVED;
        }
        else {
            throw new IllegalStateException("Illegal transition " +SHUTDOWN_RECEIVED+ " from " + this);
        }
    }

    public SCTPState sendShutdown() {
        if(SHUTDOWN_PENDING.equals(this)) {
            return SHUTDOWN_SENT;
        }
        else {
            throw new IllegalStateException("Illegal transition " +SHUTDOWN_PENDING+ " from " + this);
        }
    }


    public SCTPState shutDownAck() {
        if(SHUTDOWN_SENT.equals(this) || SHUTDOWN_RECEIVED.equals(this)) {
            return SHUTDOWN_ACK_SENT;
        }
        else {
            throw new IllegalStateException("Illegal transition " +SHUTDOWN_ACK_SENT+ " from " + this);
        }
    }


    public SCTPState close() {
        if(SHUTDOWN_SENT.equals(this) || SHUTDOWN_ACK_SENT.equals(this)) {
            return CLOSED;
        }
        else {
            throw new IllegalStateException("Illegal transition " +CLOSED+ " from " + this);
        }
    }
}
