

var peerConnection = new RTCPeerConnection();

peerConnection.onicecandidate = function(e) {
    console.log('IceCand: ' + JSON.stringify(e));
    if(e.isTrusted === true) {
        console.log("State: " +dataChannel.readyState);
        console.log("IceState: " + peerConnection.iceGatheringState);
    }

	if (peerConnection.iceGatheringState === 'complete') {
        console.log("Candidate: " + JSON.stringify(e.candidate));
        console.log("IceState" + peerConnection.iceGatheringState);
        var loc = JSON.stringify(peerConnection.localDescription);
        console.log(peerConnection.localDescription.sdp);
        ws.send(loc);
	}
};


peerConnection.onsignalingstatechange = function(event) {
    console.log("Signal: " + JSON.stringify(event));
};

peerConnection.onconnectionstatechange = function(event) {
  console.log("State: " + JSON.stringify(event));
  switch(pc.connectionState) {
    case "connected":
      break;
    case "disconnected":
        break;
    case "failed":
      break;
    case "closed":
      break;
  }
};

peerConnection.ondatachannel = function (dt) {
    console.log("datachannel: " + JSON.stringify(ev))
};

peerConnection.onidpvalidationerror = function (dt) {
    console.log("ipvalidationfail: " + JSON.stringify(ev))
};

peerConnection.onclose = function (ev) {
    console.log("Close: " + JSON.stringify(ev))
};

peerConnection.iceconnectionstatechange = function(ev) {
    console.log("icestate: " + JSON.stringify(ev));
};

peerConnection.onicegatheringstatechange = function(ev) {
    console.log("gather: " + JSON.stringify(ev));
};

peerConnection.onidpvalidationerror = function(ev) {
    console.log("ipvalid: " + JSON.stringify(ev));
};

peerConnection.onnegotiationneeded = function(ev) {
    console.log("negneded: " + JSON.stringify(ev));
};

peerConnection.onpeeridentity = function(ev) {
    console.log("peerident: " + JSON.stringify(ev));
};


var dataChannel = peerConnection.createDataChannel("channel",
    {ordered:false,maxRetransmits:0}
    );

var sent = 0;
var received = 0;
var count = 0;

var logstats=false;

var BUFF_MAX = 1000000;

var logger = window.setInterval(function(){
    if(logstats) {
        console.log("------------------ STATS ---------------------");
        console.log("Ordered: " + dataChannel.ordered);
        console.log("Sent: " + sent);
        console.log("Received: " + received);
        console.log("Count: " + count);
        console.log("Buffered: " + dataChannel.bufferedAmount);
        console.log("Low buff thresh: " + dataChannel.bufferedAmountLowThreshold);
        console.log("maxRetransmits: " + dataChannel.maxRetransmits);
        console.log("signalstate: " + peerConnection.signalingState);
        console.log("sctp: " + peerConnection.sctp);
        if (peerConnection.sctp) {
            console.log("sctp: " + peerConnection.sctp.maxMessageSize);
        }
        console.log("stats: " + JSON.stringify(peerConnection.getStats()));
        console.log("state: " + peerConnection.connectionState);
        console.log("transmit: " + transmit);
        console.log("----------------------------------------------");
    }
},3000);

var transmit = true;
var data = {};
var msgSent = [];
var msgReceived = [];
var messageNum = 0;
var last = null;
var maxLatency = 0;
var average = 0;
var start = null;

dataChannel.onopen = function (e) {
    console.log("Open data channel: " + JSON.stringify(e));
    var sts = document.getElementById("status");
    sts.innerText = "OPEN";

    var time = new Date().getTime();

    window.setTimeout(function () {

        var transfer = window.setInterval(function () {

            try {
                if(transmit) {
                    if (dataChannel.bufferedAmount <= BUFF_MAX && dataChannel.readyState === "open") {
                        let out = "CLIENT-MSG-"+messageNum;
                        msgSent.push({data:out,time:new Date().getTime()})
                        dataChannel.send(out);
                        messageNum++;
                        //console.log("sent " + out.length + " bytes");
                    }
                    else {
                        //console.log("buffer above " + BUFF_MAX + " bytes, no send");
                    }
                } else {
                    console.log("Transmission ended");
                }
            }
            catch (err) {
                console.log("Error" + err.message);
                let sts = document.getElementById("error");
                sts.innerText += JSON.stringify(e)
            }
        }, 25);

    }, 1000);


    window.setTimeout(function () {
        var sum = 0;
        for (let i = 1; i < msgReceived.length; i++) {
            let diff = msgReceived[i][1] - msgReceived[i-1][1];
            sum += diff;
        }
        var allReceived = document.getElementById("all-received");
        allReceived.innerText = "AVERAGE: "+sum/msgReceived.length;

    }, 1000*90);

};

dataChannel.onclose = function (e) {
    console.log("Close: " + JSON.stringify(e));
};

dataChannel.onerror = function (e) {
    console.log("Error: " + JSON.stringify(e));
    console.log("Got message: " + e.data);
    let sts = document.getElementById("error");
    sts.innerText += JSON.stringify(e)
};

var reader = new FileReader();
reader.onloadend = function () {
    console.log("Got message: " + reader.result);
};


dataChannel.onmessage = function (e) {
    if(e.data != null) {
        msgReceived.push([e.data,new Date().getTime()]);
        received = received + e.data.length;
        if(last != null) {
            var diff = new Date().getTime() - last;
            maxLatency = Math.max(diff,maxLatency)
            last = new Date().getTime()
            average = (last - start)/msgReceived.length
        }
        else {
            start = new Date().getTime()
            last = new Date().getTime()
        }

        var sts = document.getElementById("status");
        sts.innerText = "Average: "+average+"  Max: "+maxLatency + "  Messages: "+msgReceived.length;
    }

};

var ws = new WebSocket("ws://0.0.0.0:8443/incoming");

ws.onmessage = function(message) {

    var data = JSON.parse(message.data);

    if(data.type) {
        switch (data.type) {
            case 'offer':
                console.log("Offer received: ");
                console.log(data.sdp);
                peerConnection.setRemoteDescription(new RTCSessionDescription(data));
                peerConnection.createAnswer().then(function(description) {
                peerConnection.setLocalDescription(description);
                });
            break;

            case 'answer':
                console.log("Answer received: " + JSON.stringify(data));
                console.log("Answer: " + data.sdp);
                peerConnection.setRemoteDescription(new RTCSessionDescription(data)).then(function (sess) {
                    console.log("Set remote with success ");
                }).catch(function(e){
                    console.log("error setting remote: "+e);
                    let sts = document.getElementById("error");
                    sts.innerText += JSON.stringify(e)
                });
            break;
        }
    }

    if(data.candidate) {
        console.log("Received: "+JSON.stringify(data));
        peerConnection.addIceCandidate(data).then(
            function(candidate){
                console.log("Adding candidate: " + candidate + " " +  JSON.stringify(data));
            }
        ).catch(
            function(e){
                let sts = document.getElementById("error");
                sts.innerText += JSON.stringify(e)
                console.log("error candidate: "+e);
            }
        );
    }

};

ws.onopen = function(ev) {
    peerConnection.createOffer().then(function(desc)
    {
        return peerConnection.setLocalDescription(desc);
    }).then( function() {

        console.log("Created offer, icecandidates will be sent");
        //Offer sending handled in onicecandidate

    }).catch(
        function(error) {
            let sts = document.getElementById("error");
            sts.innerText += JSON.stringify(e)
            console.log("Offer Error" + error)

        }
    );
};

ws.onerror = function (e) {
    let sts = document.getElementById("error");
    sts.innerText += JSON.stringify(e)
}