/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.util;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import org.jitsi.service.neomedia.*;

import java.net.*;

/**
 * Contains factory methods for creating Jingle offer sent in 'session-invite'
 * by Jitsi Meet conference focus.
 *
 * @author Pawel Domas
 */
public class JingleOfferFactory
{
    private JingleOfferFactory(){ }

    /**
     * Creates <tt>ContentPacketExtension</tt> for given media type that will be
     * included in initial conference offer.
     *
     * @param mediaType the media type for which new offer content will
     *                  be created.
     *
     * @return <tt>ContentPacketExtension</tt> for given media type that will be
     *         used in initial conference offer.
     */
    public static ContentPacketExtension createContentForMedia(
            MediaType mediaType, boolean enableFirefoxHacks)
    {

        ContentPacketExtension content
            = new ContentPacketExtension(
                    ContentPacketExtension.CreatorEnum.initiator,
                    mediaType.name().toLowerCase());

        content.setSenders(ContentPacketExtension.SendersEnum.both);

        // FIXME: re-use Format and EncodingConfiguration
        // to construct the offer
        if (mediaType == MediaType.AUDIO)
        {
            RtpDescriptionPacketExtension rtpDesc
                = new RtpDescriptionPacketExtension();

            rtpDesc.setMedia("audio");

            RTPHdrExtPacketExtension ssrcAudioLevel
                = new RTPHdrExtPacketExtension();
            ssrcAudioLevel.setID("1");
            ssrcAudioLevel.setURI(
                URI.create("urn:ietf:params:rtp-hdrext:ssrc-audio-level"));
            rtpDesc.addExtmap(ssrcAudioLevel);

			boolean sipEnabled = false;
			String sipEnabledString = System.getProperty("org.jitsi.videobridge.ofmeet.sip.enabled");	// BAO
			if (sipEnabledString != null) sipEnabled = sipEnabledString.equals("true");

			if (sipEnabled == false)
			{
				// a=rtpmap:111 opus/48000/2
				PayloadTypePacketExtension opus
					= new PayloadTypePacketExtension();
				opus.setId(111);
				opus.setName("opus");
				opus.setClockrate(48000);
				opus.setChannels(2);
				rtpDesc.addPayloadType(opus);
				// fmtp:111 minptime=10
				ParameterPacketExtension opusMinptime
					= new ParameterPacketExtension();
				opusMinptime.setName("minptime");
				opusMinptime.setValue("10");
				opus.addParameter(opusMinptime);
				// a=rtpmap:103 ISAC/16000
				PayloadTypePacketExtension isac16
					= new PayloadTypePacketExtension();
				isac16.setId(103);
				isac16.setName("ISAC");
				isac16.setClockrate(16000);
				rtpDesc.addPayloadType(isac16);
				// a=rtpmap:104 ISAC/32000
				PayloadTypePacketExtension isac32
					= new PayloadTypePacketExtension();
				isac32.setId(104);
				isac32.setName("ISAC");
				isac32.setClockrate(32000);
				rtpDesc.addPayloadType(isac32);
			}

            // a=rtpmap:0 PCMU/8000
            PayloadTypePacketExtension pcmu
                = new PayloadTypePacketExtension();
            pcmu.setId(0);
            pcmu.setName("PCMU");
            pcmu.setClockrate(8000);
            rtpDesc.addPayloadType(pcmu);
            // a=rtpmap:8 PCMA/8000
            PayloadTypePacketExtension pcma
                = new PayloadTypePacketExtension();
            pcma.setId(8);
            pcma.setName("PCMA");
            pcma.setClockrate(8000);
            rtpDesc.addPayloadType(pcma);
            // a=rtpmap:106 CN/32000
            PayloadTypePacketExtension cn
                = new PayloadTypePacketExtension();
            cn.setId(106);
            cn.setName("CN");
            cn.setClockrate(32000);
            rtpDesc.addPayloadType(cn);
            // a=rtpmap:105 CN/16000
            PayloadTypePacketExtension cn16
                = new PayloadTypePacketExtension();
            cn16.setId(105);
            cn16.setName("CN");
            cn16.setClockrate(16000);
            rtpDesc.addPayloadType(cn16);
            // a=rtpmap:13 CN/8000
            PayloadTypePacketExtension cn8
                = new PayloadTypePacketExtension();
            cn8.setId(13);
            cn8.setName("CN");
            cn8.setClockrate(8000);
            rtpDesc.addPayloadType(cn8);
            // rtpmap:126 telephone-event/8000
            PayloadTypePacketExtension teleEvent
                = new PayloadTypePacketExtension();
            teleEvent.setId(126);
            teleEvent.setName("telephone-event");
            teleEvent.setClockrate(8000);
            rtpDesc.addPayloadType(teleEvent);
            // a=maxptime:60
            rtpDesc.setAttribute("maxptime", "60");
            content.addChildExtension(rtpDesc);
        }
        else if (mediaType == MediaType.VIDEO)
        {
            RtpDescriptionPacketExtension rtpDesc
                = new RtpDescriptionPacketExtension();

            rtpDesc.setMedia("video");

            // a=extmap:2 urn:ietf:params:rtp-hdrext:toffset
            RTPHdrExtPacketExtension toOffset
                = new RTPHdrExtPacketExtension();
            toOffset.setID("2");
            toOffset.setURI(
                URI.create("urn:ietf:params:rtp-hdrext:toffset"));
            rtpDesc.addExtmap(toOffset);
            // a=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time
            RTPHdrExtPacketExtension absSendTime
                = new RTPHdrExtPacketExtension();
            absSendTime.setID("3");
            absSendTime.setURI(
                URI.create(
                    "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time"));
            rtpDesc.addExtmap(absSendTime);
            // a=rtpmap:100 VP8/90000
            PayloadTypePacketExtension vp8
                = new PayloadTypePacketExtension();
            vp8.setId(100);
            vp8.setName("VP8");
            vp8.setClockrate(90000);
            rtpDesc.addPayloadType(vp8);
            // a=rtcp-fb:100 ccm fir
            RtcpFbPacketExtension ccmFir = new RtcpFbPacketExtension();
            ccmFir.setFeedbackType("ccm");
            ccmFir.setFeedbackSubtype("fir");
            vp8.addRtcpFeedbackType(ccmFir);
            // a=rtcp-fb:100 nack
            RtcpFbPacketExtension nack = new RtcpFbPacketExtension();
            nack.setFeedbackType("nack");
            vp8.addRtcpFeedbackType(nack);
            if (!enableFirefoxHacks)
            {
				// a=rtcp-fb:100 nack pli
				RtcpFbPacketExtension nackPli = new RtcpFbPacketExtension();
				nackPli.setFeedbackType("nack");
				nackPli.setFeedbackSubtype("pli");
				vp8.addRtcpFeedbackType(nackPli);

                // a=rtcp-fb:100 goog-remb
                RtcpFbPacketExtension remb = new RtcpFbPacketExtension();
                remb.setFeedbackType("goog-remb");
                vp8.addRtcpFeedbackType(remb);
            }
            // a=rtpmap:116 red/90000
            PayloadTypePacketExtension red
                = new PayloadTypePacketExtension();
            red.setId(116);
            red.setName("red");
            red.setClockrate(90000);
            rtpDesc.addPayloadType(red);
            // a=rtpmap:117 ulpfec/90000
            PayloadTypePacketExtension ulpfec
                = new PayloadTypePacketExtension();
            ulpfec.setId(117);
            ulpfec.setName("ulpfec");
            ulpfec.setClockrate(90000);
            rtpDesc.addPayloadType(ulpfec);

            content.addChildExtension(rtpDesc);
        }
        else if (mediaType == MediaType.DATA)
        {
            //SctpMapExtension sctpMap = new SctpMapExtension();
            //sctpMap.setPort(5000);
            //sctpMap.setProtocol(SctpMapExtension.Protocol.WEBRTC_CHANNEL);
            //sctpMap.setStreams(1024);
            //content.addChildExtension(sctpMap);

            RtpDescriptionPacketExtension rdpe
                = new RtpDescriptionPacketExtension();
            rdpe.setMedia("application");

            content.addChildExtension(rdpe);
        }
        else
            return null;

        // DTLS-SRTP
        //setDtlsEncryptionOnContent(mediaType, content, null);

        content.addChildExtension(new IceUdpTransportPacketExtension());

        return content;
    }
}
