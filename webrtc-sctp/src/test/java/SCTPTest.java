import com.bitbreeds.webrtc.sctp.model.SCTPHeader;
import com.bitbreeds.webrtc.sctp.model.SCTPMessage;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import java.util.Arrays;

/**
 * Copyright (c) 17/05/16, Jonas Waage
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
 * {@link <a href=http://www.iana.org/assignments/sctp-parameters/sctp-parameters.xhtml>SCTP messages and params</a>}
 */
public class SCTPTest {

    String data = "1388138800000000a630caa40100005633c89cf5000200000100080037429a54c000000480080009c00fc1808200000080020024e9eac84358178100ae0c280e0598ed4bf2071c7314acc154aa20de77ec40026780040006000100008003000680c10000";

    String data2 = "1388 1388 00000000 a630caa4 01 00 0056 33c89cf5 00020000 0100 0800 37429a54 c000 0004 8008 0009 c00fc18082000000 8002 0024 e9eac84358178100ae0c280e0598ed4bf2071c7314acc154aa20de77ec400267 8004 0006 00010000 8003 0006 80c10000";

    @Test
    public void testSCTPHeader() throws DecoderException {

        byte[] hex  = Hex.decodeHex(data.toCharArray());

        SCTPHeader hrd = SCTPHeader.fromBytes(Arrays.copyOf(hex,12));

        System.out.println("Hrd: " + hrd);
    }


    @Test
    public void testSCTPFull() throws DecoderException {

        byte[] hex  = Hex.decodeHex(data.toCharArray());

        SCTPMessage msg = SCTPMessage.fromBytes(hex);

        System.out.println("Message: " + msg);

    }


    @Test
    public void testHeartBeat() throws DecoderException {

        String msg = "13881388295823fba2c6a0b60400002c00010028c2b34c57c3ca0d0000000000000000007b100000400a2410010000000000000000000000";

        byte[] bt = Hex.decodeHex(msg.toCharArray());

        SCTPMessage parsed = SCTPMessage.fromBytes(bt);

        System.out.println("Message: " + parsed);


    }
}
