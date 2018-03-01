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

import org.xmpp.jnodes.nio.DatagramListener;
import org.xmpp.jnodes.nio.SelDatagramChannel;
import org.xmpp.packet.*;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.math.*;

import org.ifsoft.*;
import org.ifsoft.rtp.*;
import org.ifsoft.rayo.*;

import com.sun.voip.server.*;
import com.sun.voip.*;

import org.dom4j.*;

import org.jitsi.impl.neomedia.codec.audio.opus.Opus;

import com.rayo.core.*;
import com.rayo.core.verb.*;
import com.rayo.core.xml.providers.*;



public class RelayChannel implements IChannel {

    private final SelDatagramChannel channelA;
    private final SelDatagramChannel channelB;
    private final SocketAddress addressA;
    private final SocketAddress addressB;
    private SocketAddress lastReceivedA;
    private SocketAddress lastReceivedB;
    private final SelDatagramChannel channelA_;
    private final SelDatagramChannel channelB_;
    private SocketAddress lastReceivedA_;
    private SocketAddress lastReceivedB_;
    private long lastReceivedTimeA;
    private long lastReceivedTimeB;
    private final int portA;
    private final int portB;
    private final String ip;
    private String attachment;

    private Byte localCryptoKey[];
    private Byte localCryptoSalt[];
    private Byte remoteCryptoKey[];
    private Byte remoteCryptoSalt[];
    private Encryptor encryptor = null;
    private Encryptor encryptor2 = null;
    private OutgoingCallHandler callHandler = null;
    private MemberReceiver memberReceiver = null;
    private int kt = 0;
    private int kt2 = 0;
    private int kt3 = 0;
    private Integer kt4 = new Integer((int)0);
    private ByteBuffer wBuffer = ByteBuffer.allocate(64 * 1024 );
    private RTPPacket lastAudioPacket = null;
    private RTPPacket lastVideoPacket = null;
    private JID from;
    private RayoComponent component;
    private Handset handset = null;

    private Long lastPacketTicks = Long.valueOf(0L);
    private Long lastVideoTimestamp = Long.valueOf(0L);
    private Integer lastAudioTimestamp = new Integer((int)0);

    private long decoder = 0;
    private final int sampleRate = 48000;
    private final int frameSizeInMillis = 20;
    private final int outputFrameSize = 2;
    private final int channels = 2;
    private int frameSizeInSamplesPerChannel = (sampleRate * frameSizeInMillis) / 1000;
    private int frameSizeInBytes = outputFrameSize * channels * frameSizeInSamplesPerChannel;

    private boolean active = true;

    private static final Logger Log = LoggerFactory.getLogger(RelayChannel.class);

    public static RelayChannel createLocalRelayChannel(final String host, final int minPort, final int maxPort) throws IOException {
        int range = maxPort - minPort;
        IOException be = null;

        for (int t = 0; t < 50; t++) {
            try {
                int a = Math.round((int) (Math.random() * range)) + minPort;
                a = a % 2 == 0 ? a : a + 1;
                return new RelayChannel(host, a);
            } catch (BindException e) {
                be = e;
            } catch (IOException e) {
                be = e;
            }
        }
        throw be;
    }

    public RelayChannel(final String host, final int portA) throws IOException {

        final int portB = portA + 2;

        addressA = new InetSocketAddress(host, portA);
        addressB = new InetSocketAddress(host, portB);

        channelA = SelDatagramChannel.open(null, addressA);
        channelB = SelDatagramChannel.open(null, addressB);

        channelA.setDatagramListener(new DatagramListener() {
            public synchronized void datagramReceived(final SelDatagramChannel channel, final ByteBuffer buffer, final SocketAddress address) {
                lastReceivedA = address;
                lastReceivedTimeA = System.currentTimeMillis();

                if (lastReceivedB != null) {
                    try {
                        buffer.flip();

                        if (callHandler != null)
                        {
                            ByteBuffer bb = buffer.asReadOnlyBuffer();
                            byte[] b = new byte[bb.remaining()];
                            bb.get(b, 0, b.length);

                            if (decryptMedia(b) == false) channelB.send(buffer, lastReceivedB);

                        } else {

                            channelB.send(buffer, lastReceivedB);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        channelB.setDatagramListener(new DatagramListener() {
            public synchronized void datagramReceived(final SelDatagramChannel channel, final ByteBuffer buffer, final SocketAddress address) {
                lastReceivedB = address;
                lastReceivedTimeB = System.currentTimeMillis();
                if (lastReceivedA != null) {
                    try {
                        buffer.flip();
                        channelA.send(buffer, lastReceivedA);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        this.portA = portA;
        this.portB = portB;

        // RTCP Support
        SocketAddress addressA_ = new InetSocketAddress(host, portA + 1);
        SocketAddress addressB_ = new InetSocketAddress(host, portB + 1);

        channelA_ = SelDatagramChannel.open(null, addressA_);
        channelB_ = SelDatagramChannel.open(null, addressB_);

        channelA_.setDatagramListener(new DatagramListener() {
            public void datagramReceived(final SelDatagramChannel channel, final ByteBuffer buffer, final SocketAddress address) {
                lastReceivedA_ = address;

                if (lastReceivedB_ != null) {
                    try {
                        buffer.flip();
                        //Log.info("RTCP A->B " + buffer.toString());
                        channelB_.send(buffer, lastReceivedB_);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        channelB_.setDatagramListener(new DatagramListener() {
            public void datagramReceived(final SelDatagramChannel channel, final ByteBuffer buffer, final SocketAddress address) {
                lastReceivedB_ = address;
                if (lastReceivedA_ != null) {
                    try {
                        buffer.flip();
                        //Log.info("RTCP B->A " + buffer.toString());
                        channelA_.send(buffer, lastReceivedA_);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        this.ip = host;
    }

    public SocketAddress getAddressB() {
        return addressB;
    }

    public SocketAddress getAddressA() {
        return addressA;
    }

    public int getPortA() {
        return portA;
    }

    public int getPortB() {
        return portB;
    }

    public String getIp() {
        return ip;
    }

    public long getLastReceivedTimeA() {
        return lastReceivedTimeA;
    }

    public long getLastReceivedTimeB() {
        return lastReceivedTimeB;
    }

    public OutgoingCallHandler getCallHandler() {
        return  callHandler;
    }

    public void setCallHandler(OutgoingCallHandler callHandler) {
        this.callHandler = callHandler;
    }

    public Handset getHandset() {
        return handset;
    }

    public String getAttachment() {
        return attachment;
    }

    public void setAttachment(String attachment) {
        this.attachment = attachment;
    }

    public JID getFrom() {
        return from;
    }

    public void setFrom(JID from, RayoComponent component) {
        this.from = from;
        this.component = component;
    }

    public void setCrypto(Handset handset)
    {
        this.handset = handset;

        Byte localCryptoByte[] = Convert.fromBase64String(handset.localCrypto);
        Byte remoteCryptoByte[] = Convert.fromBase64String(handset.remoteCrypto);

        if(ArrayExtensions.getLength(localCryptoByte).intValue() != 30 || ArrayExtensions.getLength(remoteCryptoByte).intValue() != 30)
            Log.error("Unexpected key/salt length.");
        else {
            localCryptoKey = BitAssistant.subArray(localCryptoByte, Integer.valueOf(0), Integer.valueOf(16));
            localCryptoSalt = BitAssistant.subArray(localCryptoByte, Integer.valueOf(16), Integer.valueOf(14));

            remoteCryptoKey = BitAssistant.subArray(remoteCryptoByte, Integer.valueOf(0), Integer.valueOf(16));
            remoteCryptoSalt = BitAssistant.subArray(remoteCryptoByte, Integer.valueOf(16), Integer.valueOf(14));

            Log.info("Crypto Suite " + handset.cryptoSuite + " " + handset.localCrypto + " "  + handset.remoteCrypto + " " + " " + handset.codec + " " + handset.stereo);

            try {
                encryptor = new Encryptor(SDPCryptoSuite.getEncryptionMode(handset.cryptoSuite), localCryptoKey, localCryptoSalt, remoteCryptoKey, remoteCryptoSalt);
                encryptor2 = new Encryptor(SDPCryptoSuite.getEncryptionMode(handset.cryptoSuite), remoteCryptoKey, remoteCryptoSalt, localCryptoKey, localCryptoSalt);

                decoder = Opus.decoder_create(sampleRate, channels);

                if (decoder == 0) Log.error( "Opus decoder creation error ");

                if (decoder == 0)
                {
                    handset.codec = "PCMU";
                    Log.warn( "Opus decoder creation failure, PCMU will be used in default");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String getMediaPreference()
    {
        String mediaPreference = "PCMU/8000/1";

        if (handset.codec == null || "OPUS".equals(handset.codec))
            mediaPreference = "PCM/48000/2";

        return mediaPreference;
    }

    public void close() {
        try {
            channelA.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            channelB.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            channelA_.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            channelB_.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (callHandler != null) callHandler.cancelRequest("Channel closing..");

        if (decoder != 0)
        {
            Opus.decoder_destroy(decoder);
            decoder = 0;
        }

        SayCompleteEvent complete = new SayCompleteEvent();
        complete.setReason(SayCompleteEvent.Reason.valueOf("SUCCESS"));

        Presence presence = new Presence();
        presence.setFrom(getAttachment() + "@rayo." + component.getDomain() + "/" + this.from.getNode());
        presence.setTo(this.from);
        presence.getElement().add(component.getHandsetProvider().toXML(complete));
        component.sendPacket(presence);

    }

    public SelDatagramChannel getChannelA() {
        return channelA;
    }

    public SelDatagramChannel getChannelB() {
        return channelB;
    }

    public SelDatagramChannel getChannelA_() {
        return channelA_;
    }

    public SelDatagramChannel getChannelB_() {
        return channelB_;
    }

    private boolean isStunPacket(Byte bytes[])
    {
        if(bytes == null || ArrayExtensions.getLength(bytes).intValue() < 20)
            return false;

        Byte buffer[] = BitAssistant.subArray(bytes, Integer.valueOf(0), Integer.valueOf(2));
        Byte method = new Byte((byte)(buffer[1].byteValue() & 0xf));
        Byte num2 = new Byte((byte)(buffer[0].byteValue() & 1));
        Byte num3 = new Byte((byte)(buffer[1].byteValue() & 0x10));

        Integer count = new Integer(BitAssistant.toShortNetwork(BitAssistant.subArray(bytes, Integer.valueOf(2), Integer.valueOf(2)), Integer.valueOf(0)).shortValue());

        if(20 + count.intValue() < ArrayExtensions.getLength(bytes).intValue())
            return false;

        Byte buffer3[] = BitAssistant.subArray(bytes, Integer.valueOf(4), Integer.valueOf(4));
        Byte magicCookie[] = new Byte[] {Byte.valueOf((byte)33), Byte.valueOf((byte)18), Byte.valueOf((byte)-92), Byte.valueOf((byte)66)};

        for(Integer i = Integer.valueOf(0); i.intValue() < ArrayExtensions.getLength(magicCookie).intValue();)
        {
            Byte _var0 = magicCookie[i.intValue()];

            if(_var0 != null ? !_var0.equals(buffer3[i.intValue()]) : _var0 != buffer3[i.intValue()])
                return false;

            i = Integer.valueOf(i.intValue() + 1);
        }

        return true;
    }

    private Short getLength(Byte bytes[])
    {
        if(ArrayExtensions.getLength(bytes).intValue() < 4)
            return Short.valueOf((short)-1);
        else
            return BitAssistant.toShortNetwork(bytes, Integer.valueOf(2));
    }

    private Long getNextAudioTimestamp(Long clockRate)
    {
        Integer timestamp = lastAudioTimestamp;
        lastAudioTimestamp = Integer.valueOf(lastAudioTimestamp.intValue() + (new Integer((new Long((20L * clockRate.longValue()) / 1000L)).intValue())).intValue());
        return new Long((new Integer(timestamp.intValue())).longValue());
    }



    public void sendComfortNoisePayload()
    {

    }

    public boolean encode()
    {
        return true;
    }

    public boolean isActive()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }

    public void pushAudio(int[] dataToSend)
    {

    }

    public synchronized void pushAudio(byte[] rtpData, byte[] opus)
    {
        try {

            if (lastAudioPacket != null)
            {
                RTPPacket newPacket = RTPPacket.parseBytes(BitAssistant.bytesToArray(rtpData));
                RTPPacket packet = RTPPacket.parseBytes(lastAudioPacket.getBytes());

                if (opus != null)
                {
                    packet.setPayload(BitAssistant.bytesToArray(opus));
                    packet.setTimestamp(getNextAudioTimestamp(Long.valueOf(48000)));

                } else { // ULAW
                    packet.setPayload(newPacket.getPayload());
                    packet.setTimestamp(getNextAudioTimestamp(Long.valueOf(8000)));
                }

                packet.setSequenceNumber(newPacket.getSequenceNumber());

                Byte pcms[] = encryptor2.encryptRTP(packet);

                wBuffer.clear();
                wBuffer.put( BitAssistant.bytesFromArray(pcms) );
                wBuffer.flip();

                if (getChannelB() != null && lastReceivedB != null)
                {
                    getChannelB().send(wBuffer, lastReceivedB);

                    kt++;

                    if ( kt < 20 ) {
                        Log.info( "+++ " + packet.getPayload().length );
                    }
                }

            }

        } catch (Exception e) {

            Log.error( "RelayChannel pushAudio exception " + e );
            e.printStackTrace();
        }

    }

    public synchronized void pushVideo(RTPPacket videoPacket)
    {

    }

    public void pushReceiverAudio(int[] dataToSend)
    {

    }

    private boolean decryptMedia(byte[] b)
    {
        Byte data[] = BitAssistant.bytesToArray(b);
        boolean decoded = false;

        if (isStunPacket(data) == false && encryptor != null)
        {
            RTPPacket packet = null;
            RTPPacket packet2 = null;
            RTCPPacket packets[] = null;
            try
            {
                packet2 = RTPPacket.parseBytes(BitAssistant.bytesToArray(b));

                if(packet2 != null)
                {
                    decoded = true;
                    //Log.info("Decoded media " + " " + packet2.getPayloadType() + " " + packet2.getSequenceNumber() + " " + packet2.getTimestamp());

                    if (packet2.getPayloadType() == 0)		// PCMU (uLaw), mix audio
                    {
                        packet = encryptor.decryptRTP(data);

                        if(packet != null)
                        {
                            lastAudioPacket = packet;

                            byte[] byteBuffer = BitAssistant.bytesFromArray(packet.getPayload());
                            int[] l16Buffer = new int[160];

                            AudioConversion.ulawToLinear(byteBuffer, 0, byteBuffer.length, l16Buffer);

                            memberReceiver = callHandler.getMemberReceiver();

                            if (memberReceiver != null )
                            {
                                memberReceiver.handleWebRtcMedia(l16Buffer, packet.getSequenceNumber().shortValue());

                                if ( kt2 < 10 ) {
                                    Log.info( "ULAW *** " + l16Buffer );
                                }

                                kt2++;
                            }
                        } else Log.warn("cannot decode packet " + packet2.getPayloadType() + " " + packet2.getSequenceNumber() + " " + packet2.getTimestamp());

                    } else if (packet2.getPayloadType() == 111)	{	// OPUS, decode and mix audio

                        packet = encryptor.decryptRTP(data);

                        if(packet != null)
                        {
                            lastAudioPacket = packet;

                            byte[] in = BitAssistant.bytesFromArray(packet.getPayload());
                            int inputOffset = 0;
                            int inputLength = in.length;

                            int frameSizeInSamplesPerChannel = Opus.decoder_get_nb_samples(decoder, in, inputOffset, inputLength);

                            if (frameSizeInSamplesPerChannel > 1)
                            {
                                int frameSizeInBytes = outputFrameSize * channels * frameSizeInSamplesPerChannel;

                                byte[] output = new byte[frameSizeInBytes];
                                frameSizeInSamplesPerChannel = Opus.decode(decoder, in, inputOffset, inputLength, output, 0, frameSizeInSamplesPerChannel, 0);

                                memberReceiver = callHandler.getMemberReceiver();

                                if (memberReceiver != null )
                                {
                                    int[] l16Buffer = AudioConversion.bytesToLittleEndianInts(output);
                                    placeInStereo(l16Buffer);
                                    memberReceiver.handleWebRtcMedia(l16Buffer, packet.getSequenceNumber().shortValue());

                                    if ( kt2 < 10 ) {
                                        Log.info( "OPUS *** " + l16Buffer );
                                    }

                                    kt2++;
                                }

                            } else Log.info( "OPUS.decode fail..." +  frameSizeInSamplesPerChannel);

                        } else Log.warn("cannot decode packet " + packet2.getPayloadType() + " " + packet2.getSequenceNumber() + " " + packet2.getTimestamp());


                    } else if (packet2.getPayloadType() == 100)	{	// video (vp8) pass-thru
                        decoded = false;

                    } else {

                        if (packet2.getPayloadType() != 13)
                        {
                            byte[] byteBuffer = BitAssistant.bytesFromArray(packet2.getPayload());

                            //Log.info("Unexpected Payload " + packet2.getPayloadType() + " size " + byteBuffer.length);
                            decoded = false;
                        }
                    }
                }
            }
            catch(Exception exception)
            {
                Log.error("RelayChannel - Could not decrypt data " + exception);
                exception.printStackTrace();
            }

        }
        //Log.info("Payload " + decoded + " " + b);
        return decoded;
    }

    private void placeInStereo(int[] buffer)
    {
        int stereo = 0;

        try {

            stereo = Integer.parseInt(handset.stereo);

        } catch(Exception exception) {

        }

        if (stereo > 0)
        {
            if (stereo > 90) stereo = 90;

            int pan = stereo - 90;
            pan = (pan < 0) ? -pan : pan;

            for (int j = 0; j < buffer.length; j+=2)
            {
                buffer[j] = (int) (buffer[j] * pan / 90);
            }

        } else {

            if (stereo < -90) stereo = -90;

            int pan = stereo + 90;
            pan = (pan < 0) ? -pan : pan;

            for (int j = 1; j < buffer.length; j+=2)
            {
                buffer[j] = (int) (buffer[j] * pan / 90);
            }
        }
    }
}
