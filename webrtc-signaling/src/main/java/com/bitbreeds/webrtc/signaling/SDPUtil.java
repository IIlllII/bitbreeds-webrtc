package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.peerconnection.IceCandidate;
import gov.nist.javax.sdp.MediaDescriptionImpl;
import gov.nist.javax.sdp.SessionDescriptionImpl;
import gov.nist.javax.sdp.TimeDescriptionImpl;
import gov.nist.javax.sdp.fields.*;

import javax.sdp.*;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

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
            List<IceCandidate> remoteCandidates,
            IceCandidate ice,
            String user,
            String pwd,
            String fingerprint,
            String mid,
            boolean isIceLite) {

        try {
            SessionDescription sdp = baseSessionDescription();
            ProtoVersionField v = new ProtoVersionField();
            v.setProtoVersion(0);
            sdp.setVersion(v);
            OriginField originField = new OriginField();
            originField.setAddress(ice.getIp());
            originField.setAddressType("IP4");
            originField.setUsername("pyrrhic_victory");

            Random rd = new Random();
            originField.setSessionId(12345678+rd.nextInt(12345678)); //Hmm random?
            originField.setSessionVersion(0);
            originField.setNetworkType("IN");
            sdp.setOrigin(originField);

            sdp.setAttribute("s","-");
            sdp.setAttribute("t","0 0");

            AttributeField sendrecv = createAttribute("sendrecv","");
            AttributeField print = createAttribute("fingerprint",fingerprint);
            AttributeField bundle = createAttribute("group","BUNDLE "+mid);
            AttributeField msid = createAttribute("msid-semantic","WMS *");
            AttributeField iceoptions = createAttribute("ice-options","trickle");

            Vector<SDPField> vec = new Vector<>();
            vec.add(sendrecv);
            vec.add(print);
            vec.add(bundle);
            vec.add(msid);
            //vec.add(iceoptions);
            if(isIceLite) {
                vec.add(createAttribute("ice-lite",""));
            }
            sdp.setAttributes(vec);


            Vector<MediaDescription> vec2 = new Vector<>();
            vec2.add(creatMedia(remoteCandidates,user,pwd,ice.getIp(),fingerprint,ice.getPort(),mid));
            sdp.setMediaDescriptions(vec2);

            return sdp;
        } catch (SdpException e) {
            throw new RuntimeException("SDP creation failed: ",e);
        }
    }

    /*
     * Same as SDPFactory.createSessionDescription(), but it does not do a local ip lookup
     */
    private static SessionDescriptionImpl baseSessionDescription() throws SdpException {
        SessionDescriptionImpl sdp = new SessionDescriptionImpl();
        ProtoVersionField ProtoVersionField = new ProtoVersionField();
        ProtoVersionField.setVersion(0);
        sdp.setVersion(ProtoVersionField);

        SessionNameField sessionNameImpl = new SessionNameField();
        sessionNameImpl.setValue("-");
        sdp.setSessionName(sessionNameImpl);
        TimeDescriptionImpl timeDescriptionImpl = new TimeDescriptionImpl();
        TimeField timeImpl = new TimeField();
        timeImpl.setZero();
        timeDescriptionImpl.setTime(timeImpl);
        Vector times = new Vector();
        times.addElement(timeDescriptionImpl);
        sdp.setTimeDescriptions(times);
        return sdp;
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
            List<IceCandidate> remoteCandidates,
            String user,
            String pass,
            String address,
            String fingerprint,
            int port,
            String mid) {
        try {
            MediaDescriptionImpl media = new MediaDescriptionImpl();
            MediaField mediaField = new MediaField();
            mediaField.setProtocol("UDP/DTLS/SCTP");

            Vector<String> formats = new Vector<>();
            formats.add("webrtc-datachannel");

            mediaField.setMediaFormats(formats);
            mediaField.setMediaType("application");
            mediaField.setPort(port);
            media.setMedia(mediaField);

            ConnectionField connectionField = new ConnectionField();
            connectionField.setAddress("0.0.0.0");
            connectionField.setNettype("IN");
            connectionField.setAddressType("IP4");
            media.setConnection(connectionField);

            //FIXME (do we need to look at remotes here or not)
            /*Optional<IceCandidate> cand = remoteCandidates.stream()
                    .filter(i ->
                        "udp".equalsIgnoreCase(i.getProtocol()) &&
                                "host".equalsIgnoreCase(i.getType()) &&
                                address.equalsIgnoreCase(i.getIp()) &&
                                BigInteger.ONE.equals(i.getComponent())
                    )
                    .findFirst();

            Optional<Integer> max = remoteCandidates.stream()
                    .map(i->i.getFoundation().intValue())
                    .max(Integer::compare);

            int foundationIfNoHit = max.map(i->i+1).orElse(0);

            int foundation = cand
                    .map(i->i.getFoundation().intValue())
                    .orElse(foundationIfNoHit);*/

            Vector<AttributeField> cands = new Vector<>();
            int component = 1;
            cands.add(createAttribute("candidate",0+" "+component+" udp "+findPriority(component)+" "+address+" "+port+" typ host"));

            media.setAttributes(cands);
            media.setAttribute("sendrecv","");
            media.setAttribute("end-of-candidates","");
            media.setAttribute("ice-pwd",pass);
            media.setAttribute("ice-ufrag",user);
            media.setAttribute("setup","passive");
            media.setAttribute("mid",mid);
            media.setAttribute("sctp-port","5000");
            media.setAttribute("max-message-size","1073741823");
            return media;
        }
        catch (SdpException e) {
            throw new RuntimeException("SDP creation failed: ",e);
        }
    }


    static int findPriority(int componentId) {
        return 2113929216 + 16776960 + (256 - componentId);
    }

    public static ArrayList<String> getCandidates(Vector vec) throws SdpParseException {
        Object[] arr = vec.toArray();
        ArrayList<String> out = new ArrayList<>();
        for(int i = 0; i<arr.length; i++) {
            Object r = arr[i];
            if(r instanceof AttributeField) {
                AttributeField data = (AttributeField) r;
                if("candidate".equalsIgnoreCase(data.getAttribute().getKey())) {
                    out.add(data.getAttribute().getValue());
                }
            }
        }
        return out;
    }
}
