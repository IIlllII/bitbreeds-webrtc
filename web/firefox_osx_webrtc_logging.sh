#!/bin/bash
dat=`echo ~`
path="$dat/webrtc_firefox.log"
trace="$dat/webrtc_trace.log"
echo $path
export WEBRTC_TRACE_FILE=$trace
export MOZ_LOG_FILE=$path
export MOZ_LOG='timestamp,sync,jsep:5,rtplogger:5,SCTP:5,signaling:5,mtransport:5,MediaManager:5,webrtc_trace:5'
export R_LOG_LEVEL=9
export R_LOG_VERBOSE=1
open /Applications/Firefox.app/
