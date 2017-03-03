#!/bin/bash
dat=`echo ~`
path="$dat/webrtc_firefox.log"
export NSPR_LOG_FILE=$path
export NSPR_LOG_MODULES='signaling:5,mtransport:5,SCTP:5,mediapipeline:9'
export R_LOG_LEVEL=9
export R_LOG_DESTINATION=stderr
open /Applications/Firefox.app/
