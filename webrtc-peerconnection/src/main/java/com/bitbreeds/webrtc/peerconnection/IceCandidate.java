package com.bitbreeds.webrtc.peerconnection;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Copyright (c) 26/04/16, Jonas Waage
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
 * Representation of an ICE candidate
 */
public class IceCandidate {

    private final int port;
    private final String ip;
    private final long priority;
    private final BigInteger foundation;
    private final BigInteger component;
    private final String type;
    private final String protocol;

    public static IceCandidate fromString(String candidate) {
        String[] split = candidate.split(" ");
        return new IceCandidate(
                BigInteger.valueOf(Long.parseLong(split[0])),
                BigInteger.valueOf(Long.parseLong(split[1])),
                Integer.parseInt(split[5]),
                split[4],
                Long.parseLong(split[3]),
                split[7],
                split[2]);
    }

    public class Base {
        String ip;
        String type;
        String protocol;
        BigInteger component;
        BigInteger foundation;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Base base = (Base) o;
            return Objects.equals(ip, base.ip) &&
                    Objects.equals(type, base.type) &&
                    Objects.equals(protocol, base.protocol) &&
                    Objects.equals(component, base.component);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ip, type, protocol, component);
        }
    }

    public Base getBase() {
        Base b= new Base();
        b.ip = this.ip;
        b.component = this.component;
        b.protocol = this.protocol;
        b.type = this.type;
        b.foundation = this.foundation;
        return b;
    }

    public IceCandidate(BigInteger foundation,BigInteger component, int port, String ip, long priority, String type, String protocol) {
        this.port = port;
        this.ip = ip;
        this.priority = priority;
        this.foundation = foundation;
        this.type = type;
        this.protocol = protocol;
        this.component = component;
    }

    public String candidateString() {
        return "candidate:"+ foundation +" "+ component+ " "+protocol+" "+priority+" " + this.getIp() + " " + this.getPort() + " typ "+type;
    }

    public int getPort() {
        return port;
    }

    public String getIp() {
        return ip;
    }

    public long getPriority() {
        return priority;
    }

    public BigInteger getFoundation() {
        return foundation;
    }

    public String getType() {
        return type;
    }

    public String getProtocol() {
        return protocol;
    }


    public BigInteger getComponent() {
        return component;
    }

    @Override
    public String toString() {
        return "IceCandidate{" +
                "port=" + port +
                ", ip='" + ip + '\'' +
                ", priority=" + priority +
                ", foundation=" + foundation +
                ", component=" + component +
                ", type='" + type + '\'' +
                ", protocol='" + protocol + '\'' +
                '}';
    }
}
