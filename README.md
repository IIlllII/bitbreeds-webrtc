bitbreeds-webrtc
----------------

###Goal
The goal for bitbreeds-webrtc is to make a simple Java API for
talking directly to one or several browsers or
other WebRTC peers through an unordered/unreliable or 
unordered/reliable DataChannel.

At the moment the goal is to only allow it to be used as a server
(the browser must initiate the WebRTC connection), though extending
it to be able to act as a client should not be that hard, but 
it is not something I am interested in writing, since I do not need it at this moment.

###Maturity
bitbreeds webrtc is not complete and __not__ ready for
any kind of production use.


###How to run

class `SimpleSignalingExample` will start the server with these parameters pointing to the 
test keystore given.

```
-Dcom.bitbreeds.keystore=./webrtc-signaling/src/main/resources/ws2.jks 
-Dcom.bitbreeds.keystore.alias=websocket 
-Dcom.bitbreeds.keystore.pass=websocket
```
Then run `./web/index.html` in firefox to connect to the server.

If the server has issues finding the ip, you can supply that
manually like this.

```
-Dcom.bitbreeds.ip=192.168.1.5
```

####Or
class `BrowserTest` runs a full test, if you provide a path to firefox,
like below it will start the server, open the browser and connect. Then end
once it has opened the WebRTC connection.
```
-Dfirefox.path=/Users/crackling/Firefox.app/Contents/MacOS/firefox-bin
```

###Debug
To start firefox with logging, take a look in ./firefox_osx_webrtc_logging.sh: 
```
dat=`echo ~`
path="$dat/webrtc_firefox.log"
export NSPR_LOG_FILE=$path
export NSPR_LOG_MODULES='signaling:5,mtransport:5,SCTP:5,mediapipeline:9'
export R_LOG_LEVEL=9
export R_LOG_DESTINATION=stderr
open /Applications/Firefox.app/
```
That log will contain a lot of information needed to debug eventual issues.
On the server side setting levels in logback-test.xml control logging.

###Issues
* SCTP implementation is my own hacked together Java version, 
it should be replaced by the native version used by [libjitsi](https://github.com/jitsi/libjitsi).
* Signaling does not do ICE more then a bare minimum.
* The API for an outside application must be created.
* SCTP implementation is very wasteful (copies a lot of memory).
* Throughput is bad.

