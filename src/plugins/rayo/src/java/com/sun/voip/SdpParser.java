/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation and distributed hereunder
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this
 * code.
 */

package com.sun.voip;

import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import java.util.Vector;

import java.text.ParseException;

public class SdpParser {

    private SdpInfo sdpInfo;

    private Vector supportedMedia = new Vector();

    public SdpParser() {
    }

    public synchronized SdpInfo parseSdp(String sdpData) throws ParseException {
	/*
	 * parse sdpData
	 */
	String t = "c=IN IP4 ";
	int start = sdpData.indexOf(t);
	int finish = sdpData.indexOf("\r", start);

	if (start < 0 || finish < 0) {
	    throw new ParseException("Invalid remote SDP", 0);
	}

	String remoteHost = sdpData.substring(start + t.length(), finish).trim();

	t = "m=audio";
    start = sdpData.indexOf(t);
    String s = sdpData.substring(start + t.length());

	t = "RTP/AVP ";
    finish = s.indexOf(t);
    String port = s.substring(0, finish).trim();

	if (start < 0 || finish < 0) {
	    throw new ParseException("Invalid remote SDP", 0);
	}

	int remotePort;

	try {
            remotePort = Integer.parseInt(port);
	} catch (NumberFormatException e) {
	    Logger.println("Invalid remote port in sdp " + port
		+ " sdpData " + sdpData);

	    throw new ParseException("Invalid remote port in sdp " + port
                + " sdpData " + sdpData, 0);
	}

	start = finish + t.length();
	finish = s.indexOf("\r\n");

	s = s.substring(start, finish);  // point at payloads

	/*
	 * Get all supported RTP Payloads
	 */
        String[] payloads = s.split("[\" \"]");

	String[] sdp = sdpData.split("[\r\n]");

	MediaInfo mediaInfo = new MediaInfo(RtpPacket.PCMU_PAYLOAD,
	    0, 8000, 1, false);

	supportedMedia.add(mediaInfo);  // we always support payload 0

	byte telephoneEventPayload = 0;

	/*
	 * Get all "a=rtpmap:" entries, stop when we hit a non-rtpmap entry
	 */
	for (int i = 0 ; i < sdp.length; i++) {
	    s = sdp[i];

	    if (s.indexOf("a=rtpmap:") != 0) {
		continue;
	    }

	    RtpmapParser rtpmapParser = new RtpmapParser(s);

	    mediaInfo = rtpmapParser.getMediaInfo();

	    if (mediaInfo == null) {
		//Logger.println("no media info for " + s);
		continue;	// skip this entry
	    }

	    if (mediaInfo.isTelephoneEventPayload()) {
		telephoneEventPayload = mediaInfo.getPayload();
	    }

	    supportedMedia.add(mediaInfo);
	}

	/*
	 * At this point, payloads[] contains all of the supported payloads
	 * and the Vector supportedMedia contains the MediaInfo's for
	 * all supported payloads.
	 *
	 * For each payload, find the corresponding MediaInfo and
         * select the appropriate one.
	 */
	mediaInfo = null;

	boolean preferredMediaSpecified = false;

	t = "a=PreferredPayload:";

	if ((start = sdpData.indexOf(t)) >= 0) {
	    s = sdpData.substring(start + t.length());

	    finish = s.indexOf("\r\n");

	    if (finish > 0) {
	        int payload;

		s = s.substring(0, finish);

		payload = Integer.parseInt(s);

		try {
		    mediaInfo = getMediaInfo(payload);
		} catch (ParseException e) {
		}
	        preferredMediaSpecified = true;
	    }
	}

	if (mediaInfo == null) {
	    for (int i = 0; i < payloads.length; i++) {
	        int payload = 0;

	        try {
	            payload = Integer.parseInt(payloads[i]);
	        } catch (NumberFormatException e) {
	            Logger.println("Invalid payload in rtpmap: " + payloads[i]);

	            throw new ParseException("Invalid payload int rtpmap: "
                        + payloads[i], 0);
	        }

	        if (payload != 0 && payload < 96 || payload > 127) {
		    /*
		     * Not one we can deal with
		     */
		    continue;
	        }

	        /*
	         * See if it's a supported payload
	         */
		MediaInfo m = null;

		try {
	            m = getMediaInfo(payload);
		} catch (ParseException e) {
		    Logger.println("ignoring undefined payload " + payload);
		    continue;
		}

	        if (m.isTelephoneEventPayload()) {
		    continue;
	        }

	        if (mediaInfo == null ||
		        mediaInfo.getSampleRate() < m.getSampleRate()) {

		    mediaInfo = m;
	        } else if (mediaInfo.getSampleRate() == m.getSampleRate()) {
	            if (mediaInfo.getChannels() < m.getChannels()) {
	 	        mediaInfo = m;
                    }
	        }
	    }
	}

	if (mediaInfo == null) {
	    Logger.println("No suitable media payload in sdp data "
		+ sdpData);

	    throw new ParseException("No suitable media payload in sdp data "
		+ sdpData, 0);
	}

	sdpInfo = new SdpInfo(
	    remoteHost, remotePort, telephoneEventPayload, supportedMedia,
		mediaInfo, preferredMediaSpecified);

	t = "a=transmitPayload:";

	if ((start = sdpData.indexOf(t)) >= 0) {
	    s = sdpData.substring(start + t.length());

	    finish = s.indexOf("\r\n");

	    if (finish > 0) {
	        int payload;

		s = s.substring(0, finish);

		payload = Integer.parseInt(s);

		try {
		    sdpInfo.setTransmitMediaInfo(getMediaInfo(payload));
		    Logger.println("Set xmit mediaInfo to "
			+ sdpInfo.getTransmitMediaInfo());
		} catch (ParseException e) {
		}
	    }
	}

	int ix;

	t = "a=transmitMediaInfoOk";

        if ((ix = sdpData.indexOf(t)) >= 0) {
            sdpInfo.setTransmitMediaInfoOk(true);
        }

	t = "a=userName:";

        if ((ix = sdpData.indexOf(t)) >= 0) {
            String userName = sdpData.substring(ix + t.length());

            finish = userName.indexOf("\n");

            if (finish > 0) {
                sdpInfo.setUserName(userName.substring(0, finish).trim());
            } else {
                /*
                 * This is a workaround for a bug where "\r\n" are missing
                 * from the SDP.
                 * XXX This assumes "userName:" is last in the sdp.
                 */

                sdpInfo.setUserName(userName.substring(0).trim());
            }
        }

	t = "a=callId:";

        if ((ix = sdpData.indexOf(t)) >= 0) {
	    String callId = sdpData.substring(ix + t.length());

            finish = callId.indexOf("\n");

            if (finish > 0) {
                sdpInfo.setCallId(
                    callId.substring(0, finish).trim());
            }
	}

	t = "a=conferenceId:";

	if ((ix = sdpData.indexOf(t)) >= 0) {
	    String conferenceId = sdpData.substring(ix + t.length());

            finish = conferenceId.indexOf("\n");

            if (finish > 0) {
                sdpInfo.setConferenceId(
		    conferenceId.substring(0, finish).trim());
            } else {
		/*
		 * This is a workaround for a bug where "\r\n" are missing
		 * from the SDP.
		 * XXX This assumes "conferenceId:" is last in the sdp.
		 */
		sdpInfo.setConferenceId(conferenceId.substring(0).trim());
	    }
	}

        if (sdpData.indexOf("a=distributedBridge") >= 0) {
            sdpInfo.setDistributedBridge();
	}

	t = "a=rtcpAddress:";

	if ((ix = sdpData.indexOf(t)) >= 0) {
	    s = sdpData.substring(ix + t.length());

            finish = s.indexOf("\n");

            if (finish > 0) {
		s = s.substring(0, finish).trim();
	    } else {
		s = s.substring(0).trim();
	    }

	    String[] tokens = s.split(":");

	    if (tokens.length != 2) {
	        throw new ParseException("Invalid rtcp address in sdp "
                    + " sdpData " + sdpData, 0);
	    }

	    try {
		sdpInfo.setRtcpAddress(new InetSocketAddress(
		    InetAddress.getByName(tokens[0]), Integer.parseInt(tokens[1])));
	    } catch (UnknownHostException e) {
	        throw new ParseException("Invalid rtcp host address in sdp "
                    + " sdpData " + sdpData, 0);
	    } catch (NumberFormatException e) {
	        throw new ParseException("Invalid rtcp port in sdp "
                    + " sdpData " + sdpData, 0);
	    }
	}

	return sdpInfo;
    }

    private MediaInfo getMediaInfo(int payload) throws ParseException {
	for (int i = 0; i < supportedMedia.size(); i++) {
	    MediaInfo mediaInfo = (MediaInfo) supportedMedia.elementAt(i);

	    if (mediaInfo.getPayload() == payload) {
		return mediaInfo;
	    }
	}

	throw new ParseException("Unsupported payload " + payload, 0);
    }

}

class RtpmapParser {

    private MediaInfo mediaInfo;

    /*
     * An rtpmap entry looks like this:
     *
     * a=rtpmap:<payload> <PCMU | PCM | SPEEX>/<sampleRate>/<channels>
     *     or
     * a=rtpmap:<payload> telephone-event/8000/1
     */
    public RtpmapParser(String rtpmap) throws ParseException {
    	byte payload;
    	int encoding;
    	int sampleRate;
    	int channels;

	byte telephoneEventPayload;

 	int start;
	int finish;

	finish = rtpmap.indexOf(" ");

	if (finish < 0) {
	    Logger.println("Invalid rtpmap:  " + rtpmap);

	    throw new ParseException("Invalid rtpmap:  " + rtpmap, 0);
	}

	try {
	    payload = (byte)Integer.parseInt(rtpmap.substring(9, finish));
	} catch (NumberFormatException e) {
	    Logger.println("Invalid payload in rtpmap: " + rtpmap);

	    throw new ParseException("Invalid payload in rtpmap:  "
		+ rtpmap, 0);
	}

	String s = rtpmap.substring(finish + 1);

	finish = s.indexOf("telephone-event");

	if (finish >= 0) {
	    mediaInfo = new MediaInfo(payload, 0, 8000, 1, true);
	    telephoneEventPayload = payload;
	    return;
	}

	finish = s.indexOf("CN/");

	if (finish >= 0) {
	    return;	// ignore this entry
	}

	start = s.indexOf("PCM/");

	if (start >= 0) {
	    s = s.substring(start + 4);
	    encoding = RtpPacket.PCM_ENCODING;
	} else {
	    start = s.indexOf("PCMU/");

	    if (start >= 0) {
	        s = s.substring(start + 5);
	        encoding = RtpPacket.PCMU_ENCODING;
	    } else {
		start = s.indexOf("SPEEX/");

		if (start < 0) {
		    if (Logger.logLevel >= Logger.LOG_INFO) {
		        Logger.println("Ignoring rtpmap entry: "
			    + payload + " " + s);
		    }
		    return;		// ignore this entry
		}

	        s = s.substring(start + 6);
	        encoding = RtpPacket.SPEEX_ENCODING;
	    }
	}

	finish = s.indexOf("/");
	boolean channelsPresent = true;

	String rate;

	if (finish < 0) {
	    channelsPresent = false;

	    rate = s.substring(start);
	} else {
	    rate = s.substring(start, finish);  // point at sample rate
	}

	try {
	    sampleRate = Integer.parseInt(rate);
	} catch (NumberFormatException e) {
	    Logger.println("Invalid sample rate in rtpmap: " + rtpmap);

	    throw new ParseException("Invalid sample rate in rtpmap:  "
		+ rtpmap, 0);
	}

	if (channelsPresent) {
	    s = s.substring(finish + 1);	// point at channels

	    try {
	        channels = Integer.parseInt(s);
	    } catch (NumberFormatException e) {
	        Logger.println("Invalid channels in rtpmap: " + rtpmap);

	        throw new ParseException("Invalid channels in rtpmap:  "
		    + rtpmap, 0);
	    }
	} else {
	    channels = 1;
	}

	mediaInfo = new MediaInfo(payload, encoding, sampleRate,
            channels, false);
    }

    public MediaInfo getMediaInfo() {
	return mediaInfo;
    }

}
