/**
 *    Copyright 2012 Voxbone SA/NV
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ifsoft.sip;

/**
 * Class used to generate RFC2833 DTMF events
 *
 */

public class DtmfEvent
{
	byte event;
	short duration;

	byte [] rtpPacket = new byte[16];

	public DtmfEvent(char dtmf, long startTime, byte [] ssrc)
	{
		if (dtmf == '*')
		{
			event = 10;
		}
		else if (dtmf == '#')
		{
			event = 11;
		}
		else if (dtmf == 'A')
		{
			event = 12;
		}
		else if (dtmf == 'B')
		{
			event = 13;
		}
		else if (dtmf == 'C')
		{
			event = 14;
		}
		else if (dtmf == 'D')
		{
			event = 15;
		}
		else
		{
			event = Byte.parseByte("" + dtmf);
		}

		RtpUtil.buildRtpHeader(rtpPacket, 101, (short) 0, startTime, ssrc);
		rtpPacket[12] = this.event;
		rtpPacket[13] = 10;
		duration = 0;
	}

	public byte [] startPacket()
	{
		RtpUtil.setMarker(rtpPacket, true);

		duration += 160;

		rtpPacket[15] = (byte) (duration & 0xFF);
		rtpPacket[14] = (byte) ((duration >> 8) & 0xFF);

		return rtpPacket;
	}

	public byte [] continuationPacket()
	{
		RtpUtil.setMarker(rtpPacket, false);

		duration += 160;

		rtpPacket[15] = (byte) (duration & 0xFF);
		rtpPacket[14] = (byte) ((duration >> 8) & 0xFF);

		return rtpPacket;
	}

	public byte [] endPacket()
	{
		rtpPacket[13] |= (byte) (1<<7);
		return rtpPacket;
	}

}
