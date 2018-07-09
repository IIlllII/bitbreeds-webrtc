package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.peerconnection.IceCandidate;
import gov.nist.javax.sdp.MediaDescriptionImpl;
import gov.nist.javax.sdp.fields.*;

import javax.sdp.*;
import java.util.Vector;

/**
 * Copyright (c) 05/02/2018, Jonas Waage
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
public class SDPUtil {

    public static SessionDescription createSDP(
            IceCandidate ice,
            String user,
            String pwd,
            String fingerprint,
            String mid) {

        try {
            SdpFactory factory = SdpFactory.getInstance();
            SessionDescription sdp = factory.createSessionDescription();
            ProtoVersionField v = new ProtoVersionField();
            v.setProtoVersion(0);
            sdp.setVersion(v);
            OriginField originField = new OriginField();
            originField.setAddress(ice.getIp());
            originField.setAddressType("IP4");
            originField.setUsername("-");
            originField.setSessionId(1234567); //Hmm random?
            originField.setSessionVersion(2);
            originField.setNetworkType("IN");
            sdp.setOrigin(originField);

            sdp.setAttribute("s","-");
            sdp.setAttribute("t","0 0");

            AttributeField fa = createAttribute("group","BUNDLE "+mid);
            AttributeField fb = createAttribute("msid-semantic"," WMS *");
            Vector<SDPField> vec = new Vector<>();
            vec.add(fa);
            vec.add(fb);
            sdp.setAttributes(vec);

            Vector<MediaDescription> vec2 = new Vector<>();
            vec2.add(creatMedia(user,pwd,ice.getIp(),fingerprint,ice.getPort(),mid));
            sdp.setMediaDescriptions(vec2);

            return sdp;
        } catch (SdpException e) {
            throw new RuntimeException("SDP creation failed: ",e);
        }
    }

    private static AttributeField createAttribute(String key, String value) {
        try {
            AttributeField attr = new AttributeField();
            attr.setName(key);
            attr.setValue(value);
            return attr;
        }
        catch (SdpException e) {
            throw new RuntimeException("SDP creation failed: ",e);
        }
    }


    private static MediaDescriptionImpl creatMedia(
            String user,
            String pass,
            String address,
            String fingerprint,
            int port,
            String mid) {
        try {
            MediaDescriptionImpl media = new MediaDescriptionImpl();
            MediaField mediaField = new MediaField();
            mediaField.setProtocol("DTLS/SCTP");

            Vector<String> formats = new Vector<>();
            formats.add("5000");

            mediaField.setMediaFormats(formats);
            mediaField.setMediaType("application");
            mediaField.setPort(port);
            media.setMedia(mediaField);

            ConnectionField connectionField = new ConnectionField();
            connectionField.setAddress(address);
            connectionField.setNettype("IN");
            connectionField.setAddressType("IP4");
            media.setConnection(connectionField);

            Vector<AttributeField> cands = new Vector<>();
            cands.add(createAttribute("candidate","1 1 udp 2113937151 "+address+" "+port+" typ host"));

            media.setAttributes(cands);

            media.setAttribute("ice-ufrag",user);
            media.setAttribute("ice-pwd",pass);
            media.setAttribute("ice-options","trickle");
            media.setAttribute("fingerprint",fingerprint);
            media.setAttribute("setup","passive");
            media.setAttribute("sendrecv ","");
            media.setAttribute("mid",mid);
            //Type of channel and amount of streams
            media.setAttribute("sctpmap","5000 webrtc-datachannel 256");
            media.setAttribute("max-message-size","1073741823");
            return media;
        }
        catch (SdpException e) {
            throw new RuntimeException("SDP creation failed: ",e);
        }
    }


}
