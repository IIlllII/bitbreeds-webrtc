#!/bin/bash
dat=`echo ~`
path="$dat/webrtc_firefox.log"
pathold="$dat/webrtc_firefox_old.log"
trace="$dat/webrtc_trace.log"
echo $path
echo $pathold
export WEBRTC_TRACE_FILE=$trace
export MOZ_LOG_FILE=$path
export NSPR_LOG_FILE=$pathold
export MOZ_LOG='timestamp,sync,jsep:5,rtplogger:5,SCTP:5,signaling:5,mtransport:5,MediaManager:5,webrtc_trace:5'
#export NSPR_LOG_MODULES='timestamp,sync,jsep:5,rtplogger:5,sctp:5,signaling:5,mtransport:5,MediaManager:4,webrtc_trace:5'
export R_LOG_LEVEL=9
export R_LOG_VERBOSE=1
#export R_LOG_DESTINATION=stderr
#cd /Applications/Firefox.app/Contents/MacOS
#./firefox-bin
open /Applications/Firefox.app/
