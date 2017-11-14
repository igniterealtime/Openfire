/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xmpp.jnodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.voip.*;
import com.sun.voip.server.*;

public class SpeakerChannel implements IChannel
{
    private static final Logger Log = LoggerFactory.getLogger(SpeakerChannel.class);
    private MemberReceiver memberReceiver;
    private short sequenceNumber = 1;
    private CallParticipant cp;
    private int kt = 0;
    private boolean active = true;


    public SpeakerChannel(MemberReceiver memberReceiver)
    {
        this.memberReceiver = memberReceiver;

        Log.info("SpeakerChannel init " + memberReceiver);
    }

    public void sendComfortNoisePayload()
    {

    }

    public boolean encode()
    {
        return false;
    }

    public boolean isActive()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
        Log.info("SpeakerChannel setActive " + active + " " + memberReceiver);
    }

    public void pushAudio(int[] dataToSend)
    {
        if (memberReceiver != null && active)
        {
            if (sequenceNumber < 10) Log.info("SpeakerChannel pushAudio " + memberReceiver);
            memberReceiver.handleWebRtcMedia(dataToSend, sequenceNumber++);
        }
    }

    public synchronized void pushAudio(byte[] rtpData, byte[] opus)
    {

    }

    public void pushReceiverAudio(int[] dataToSend)
    {
        if (memberReceiver != null && active)
        {
            kt++;
            if (kt < 10) Log.info("SpeakerChannel pushReceiverAudio " + memberReceiver);
            memberReceiver.handleWebRtcMedia(dataToSend, sequenceNumber++);
        }
    }

}
