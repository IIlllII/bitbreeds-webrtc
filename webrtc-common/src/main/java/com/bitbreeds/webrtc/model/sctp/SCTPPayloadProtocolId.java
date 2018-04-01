package com.bitbreeds.webrtc.model.sctp;

/**
 * Copyright (c) 10/07/16, Jonas Waage
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

import java.util.Arrays;

/**
 * @see <a href="https://tools.ietf.org/html/draft-ietf-rtcweb-data-channel-13#section-6.1">webrtc-ppid</a>
 *
 *
 * +-------------------------------+----------+-----------+------------+
 * | Value                         | SCTP     | Reference | Date       |
 * |                               | PPID     |           |            |
 * +-------------------------------+----------+-----------+------------+
 * | WebRTC String                 | 51       | [RFCXXXX] | 2013-09-20 |
 * | WebRTC Binary Partial         | 52       | [RFCXXXX] | 2013-09-20 |
 * | (Deprecated)                  |          |           |            |
 * | WebRTC Binary                 | 53       | [RFCXXXX] | 2013-09-20 |
 * | WebRTC String Partial         | 54       | [RFCXXXX] | 2013-09-20 |
 * | (Deprecated)                  |          |           |            |
 * | WebRTC String Empty           | 56       | [RFCXXXX] | 2014-08-22 |
 * | WebRTC Binary Empty           | 57       | [RFCXXXX] | 2014-08-22 |
 * +-------------------------------+----------+-----------+------------+
 *
 */
public enum SCTPPayloadProtocolId {

    WEBRTC_DCEP(50),
    WEBRTC_STRING(51),
    WEBRTC_BINARY(53),
    WEBRTC_STRING_EMPTY(56),
    WEBRTC_BINARY_EMPTY(57);

    private int id;
    SCTPPayloadProtocolId(int id) {
        this.id = id;
    }

    public static SCTPPayloadProtocolId fromValue(int id) {
        return Arrays.stream(values())
                .filter(i -> i.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No payload protocal id: " + id));
    }

    public int getId() {
        return id;
    }
}
