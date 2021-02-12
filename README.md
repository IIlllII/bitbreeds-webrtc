bitbreeds-webrtc
----------------

### Goal
The goal for bitbreeds-webrtc is to eventually make a simple Java peer for
talking directly to one or several browsers or other WebRTC peers through an unordered/unreliable or 
unordered/reliable DataChannel.

At the moment the goal is to only allow it to be used as the "server"
(the browser must initiate the WebRTC connection), though extending
it to be able to act as a "client" (active side) should not be that hard, but 
it is not something I am interested in writing, since I do not need it at this moment.

### Maturity
bitbreeds webrtc is __experimental__ and not even close to complete and __not__ ready for
any kind of serious use.

### How to run
#### Run locally.

Main class `com.bitbreeds.webrtc.example.DatachannelServer` will start a server on port 8443 accepting websocket connections.

Then run `./web/index.html` in firefox to connect to the server, share candicates and 
start using WebRTC. If it works it should say _ONMESSAGE_

### Run an example echo server
First, build webrtc-example like so:
```
mvn clean package -Pbuild-example
```

Then you can start the _webrtc-example-<version-with-deps>-.jar_ from the project root like this:
This will use the default keystore in src/resources. __ONLY FOR TESTING!__.
```
java -jar webrtc-example/target/webrtc-example-0.2.6-SNAPSHOT-jar-with-dependencies.jar```
```

To supply the keystore do:
```
java -Dcom.bitbreeds.keystore=path-to-your-keystore -Dcom.bitbreeds.keystore.alias=your-key-alias -Dcom.bitbreeds.keystore.pass=your-key-pass -jar webrtc-example-1.0-SNAPSHOT-jar-with-dependencies.jar
```

If the server has problems finding its own public IP address, you can supply the
IP address like this.
```
-Dcom.bitbreeds.ip=192.168.1.5
```
This might be needed since you must send a candidate to the other peer.

The keystore parameters are pretty self explanatory:
```
-Dcom.bitbreeds.keystore=./ws2.jks
-Dcom.bitbreeds.keystore.alias=websocket
-Dcom.bitbreeds.keystore.pass=websocket
```


### Setting up a simple webrtc datachannel echoing input
Remember to provide your own keystore, the keystores provided are only for testing
```
SimplePeerServer peerConnectionServer = new SimplePeerServer(new KeystoreInfo("path","alias","password"));

peerConnectionServer.onConnection = (connection) -> {

        connection.onDataChannel = (dataChannel) -> {

            dataChannel.onOpen = (ev) -> {
                dataChannel.send("OPEN!");
            };

            dataChannel.onMessage = (ev) -> {
                String in = new String(ev.getData());
                dataChannel.send("echo-" + in);
            };

            dataChannel.onClose = (ev) -> {
            };

            dataChannel.onError = (ev) -> {
            };

        };
};
```

#### Run a complete selenium test
class `Browser<Chrome/Firefox>*Test` runs a full test against a browser.
It will start the server, open the browser and connect. Then end
once it has opened the WebRTC connection, or sent a bunch of messages over the 
peer connection.


### Debug
Chrome has ```chrome://webrtc-internals```, which is great for debugging.

Firefox has ```about:webrtc``` and tab in devtools.


### TODO
- Fix SCTP to something sane (right now it is my own hack of a thing)
- Figure out why I trigger bad congestion/flow control response in browsers (see SCTP issue above)
- Look into async DTLS some more
- Scale down the excessive (but very practical) amount of copying.