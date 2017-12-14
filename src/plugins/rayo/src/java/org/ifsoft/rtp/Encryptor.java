package org.ifsoft.rtp;

import org.ifsoft.*;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Encryptor
{
    private EncryptionMode _encryptionMode;
    private Byte _localKey[];
    private Byte _localSalt[];
    private Long _octetCount;
    private Long _packetCount;
    private Byte _remoteKey[];
    private Byte _remoteSalt[];
    private static Byte _rTCPAuthLabel = Byte.valueOf((byte)0);
    private AESCounter _rTCPDecryption;
    private Byte _rTCPDecryptionAuth[];
    private AESCounter _rTCPEncryption;
    private Byte _rTCPEncryptionAuth[];
    private Integer _rTCPIntegritySize;
    private static Byte _rTCPKeyLabel = Byte.valueOf((byte)0);
    private static Byte _rTCPSaltLabel = Byte.valueOf((byte)0);
    private static Byte _rTPAuthLabel = Byte.valueOf((byte)0);
    private AESCounter _rTPDecryption;
    private Byte _rTPDecryptionAuth[];
    private Integer _rTPDecryptionHighestSequenceNumber;
    private Long _rTPDecryptionROC;
    private AESCounter _rTPEncryption;
    private Byte _rTPEncryptionAuth[];
    private Long _rTPEncryptionROC;
    private Integer _rTPIntegritySize;
    private static Byte _rTPKeyLabel = Byte.valueOf((byte)0);
    private static Byte _rTPSaltLabel = Byte.valueOf((byte)0);
    private Integer _sRTCPIndex;

    private static final Logger Log = LoggerFactory.getLogger(Encryptor.class);

    static
    {
        _rTPKeyLabel = new Byte((byte)0);
        _rTPAuthLabel = new Byte((byte)1);
        _rTPSaltLabel = new Byte((byte)2);
        _rTCPKeyLabel = new Byte((byte)3);
        _rTCPAuthLabel = new Byte((byte)4);
        _rTCPSaltLabel = new Byte((byte)5);
    }

    private static void copyRTPProperties(RTPPacket source, RTPPacket target)
    {
        target.setMarker(source.getMarker());
        target.setSequenceNumber(source.getSequenceNumber());
        target.setSynchronizationSource(source.getSynchronizationSource());
        target.setTimestamp(source.getTimestamp());
    }

    public RTCPPacket[] decryptRTCP(Byte encryptedBytes[])
    {
        EncryptionMode _var0 = getEncryptionMode();

        if(_var0 != null ? _var0.equals(EncryptionMode.Null) : _var0 == EncryptionMode.Null)
        {
            Log.info("decryptRTCP no encryption found");
            return RTCPPacket.parseBytes(encryptedBytes);
        }
        if(ArrayExtensions.getLength(encryptedBytes).intValue() < 12 + _rTCPIntegritySize.intValue())
        {
            Log.info("decryptRTCP packet too small");
            return null;
        }
        Byte buffer[] = BitAssistant.subArray(encryptedBytes, Integer.valueOf(0), Integer.valueOf(ArrayExtensions.getLength(encryptedBytes).intValue() - _rTCPIntegritySize.intValue()));
        Byte buffer2[] = BitAssistant.subArray(encryptedBytes, Integer.valueOf(ArrayExtensions.getLength(encryptedBytes).intValue() - _rTCPIntegritySize.intValue()), _rTCPIntegritySize);
        Byte buffer4[] = BitAssistant.subArray(Crypto.getHmacSha1(_rTCPDecryptionAuth, buffer), Integer.valueOf(0), _rTCPIntegritySize);

        if(!BitAssistant.sequencesAreEqual(buffer2, buffer4).booleanValue())
        {
            Log.info("decryptRTCP sequences Are not Equal");
            return null;
        }

        Long ssrc = BitAssistant.toLongFromIntegerNetwork(buffer, Integer.valueOf(4));
        Byte buffer5[] = BitAssistant.subArray(buffer, Integer.valueOf(ArrayExtensions.getLength(buffer).intValue() - 4));
        buffer5[0] = new Byte((byte)(buffer5[0].byteValue() & 0x7f));
        Integer num2 = BitAssistant.toIntegerNetwork(buffer5, Integer.valueOf(0));
        Byte data[] = BitAssistant.subArray(buffer, Integer.valueOf(8), Integer.valueOf(ArrayExtensions.getLength(buffer).intValue() - 12));
        Byte buffer7[];

        if(_rTCPDecryption == null)
            buffer7 = data;
        else
            buffer7 = _rTCPDecryption.decrypt(data, ssrc, new Long((new Integer(num2.intValue())).longValue()));

        ArrayList list = new ArrayList();
        ArrayListExtensions.addRange(list, BitAssistant.subArray(buffer, Integer.valueOf(0), Integer.valueOf(8)));
        ArrayListExtensions.addRange(list, buffer7);
        return RTCPPacket.parseBytes((Byte[])list.toArray(new Byte[0]));
    }

    public RTPPacket decryptRTP(Byte encryptedBytes[])
        throws Exception
    {
        if(ArrayExtensions.getLength(encryptedBytes).intValue() < 12)
        {
            Log.info("decryptRTP packet less than 12 bytes");
            return null;
        }
        Integer num = new Integer(encryptedBytes[1].byteValue() & 0x7f);

        if(num.intValue() >= 72 && num.intValue() <= 76)
        {
            Log.info("decryptRTP packet first bye no good " + encryptedBytes[1]);
            return null;
        }
        EncryptionMode _var0 = getEncryptionMode();

        if(_var0 != null ? _var0.equals(EncryptionMode.Null) : _var0 == EncryptionMode.Null)
        {
            Log.info("decryptRTP no encryption found");
            return RTPPacket.parseBytes(encryptedBytes);
        }
        if(ArrayExtensions.getLength(encryptedBytes).intValue() < 12 + _rTPIntegritySize.intValue())
        {
            Log.info("decryptRTP packet too small");
            return null;
        }
        Byte collection[] = BitAssistant.subArray(encryptedBytes, Integer.valueOf(0), Integer.valueOf(ArrayExtensions.getLength(encryptedBytes).intValue() - _rTPIntegritySize.intValue()));
        Byte buffer2[] = BitAssistant.subArray(encryptedBytes, Integer.valueOf(ArrayExtensions.getLength(encryptedBytes).intValue() - _rTPIntegritySize.intValue()), _rTPIntegritySize);
        ArrayList list = ArrayListExtensions.createArray(collection);
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(_rTPDecryptionROC));
        Byte buffer4[] = BitAssistant.subArray(Crypto.getHmacSha1(_rTPDecryptionAuth, (Byte[])list.toArray(new Byte[0])), Integer.valueOf(0), _rTPIntegritySize);

        if(!BitAssistant.sequencesAreEqual(buffer2, buffer4).booleanValue())
        {
            //Log.info("decryptRTP sequences Are not Equal");
            //return null;
        }
        RTPPacket packet = RTPPacket.parseBytes(collection);

        if(packet == null)
        {
            Log.info("decryptRTP collection is bad");
            return null;
        }

        if(_rTPDecryption != null)
            packet.setPayload(_rTPDecryption.decrypt(packet.getPayload(), packet.getSynchronizationSource(), getRTPDecryptionPacketIndex(packet.getSequenceNumber())));
        return packet;
    }

    public Byte[] encryptRTCP(RTCPPacket packets[])
        throws Exception
    {
        if(packets == null || ArrayExtensions.getLength(packets).intValue() == 0)
            throw new Exception("Cannot encrypt a null RTCP packet.");
        if(!(packets[0] instanceof RTCPSRPacket))
            throw new Exception("RTCP transmissions must start with a Sender Report (RTCPSRPacket).");
        RTCPPacket arr$[] = packets;
        int len$ = arr$.length;
        for(int i$ = 0; i$ < len$; i$++)
        {
            RTCPPacket packet = arr$[i$];
            if(packet instanceof RTCPSRPacket)
            {
                RTCPSRPacket packet2 = (RTCPSRPacket)(RTCPSRPacket)packet;
                packet2.setPacketCount(_packetCount);
                packet2.setOctetCount(_octetCount);
            }
        }

        Byte bytes[] = RTCPPacket.getBytes(packets);
        EncryptionMode _var0 = getEncryptionMode();
        if(_var0 != null ? _var0.equals(EncryptionMode.Null) : _var0 == EncryptionMode.Null)
            return bytes;
        Integer rTCPEncryptionPacketIndex = getRTCPEncryptionPacketIndex();
        Long ssrc = BitAssistant.toLongFromIntegerNetwork(bytes, Integer.valueOf(4));
        Byte data[] = BitAssistant.subArray(bytes, Integer.valueOf(8), Integer.valueOf(ArrayExtensions.getLength(bytes).intValue() - 8));
        Byte buffer3[];
        if(_rTCPEncryption == null)
            buffer3 = data;
        else
            buffer3 = _rTCPEncryption.encrypt(data, ssrc, new Long((new Integer(rTCPEncryptionPacketIndex.intValue())).longValue()));
        ArrayList collection = new ArrayList();
        ArrayListExtensions.addRange(collection, BitAssistant.subArray(bytes, Integer.valueOf(0), Integer.valueOf(8)));
        ArrayListExtensions.addRange(collection, buffer3);
        Byte integerBytesNetwork[] = BitAssistant.getIntegerBytesNetwork(rTCPEncryptionPacketIndex);
        integerBytesNetwork[0] = new Byte((byte)(integerBytesNetwork[0].byteValue() | 0x80));
        ArrayListExtensions.addRange(collection, integerBytesNetwork);
        Byte buffer6[] = BitAssistant.subArray(Crypto.getHmacSha1(_rTCPEncryptionAuth, (Byte[])collection.toArray(new Byte[0])), Integer.valueOf(0), _rTCPIntegritySize);
        ArrayList list2 = new ArrayList();
        ArrayListExtensions.addRange(list2, collection);
        ArrayListExtensions.addRange(list2, buffer6);
        return (Byte[])list2.toArray(new Byte[0]);
    }

    public Byte[] encryptRTP(RTPPacket packet)
        throws Exception
    {
        if(packet == null)
            throw new Exception("Cannot encrypt a null RTP packet.");
        Long num = Long.valueOf(_packetCount.longValue() + 1L);
        Long num2 = Long.valueOf(_octetCount.longValue() + (long)ArrayExtensions.getLength(packet.getPayload()).intValue());
        if(num.longValue() >= 0x100000000L)
            num = Long.valueOf(num.longValue() - 0x100000000L);
        if(num2.longValue() >= 0x100000000L)
            num2 = Long.valueOf(num2.longValue() - 0x100000000L);
        _packetCount = num;
        _octetCount = num2;
        EncryptionMode _var0 = getEncryptionMode();
        if(_var0 != null ? _var0.equals(EncryptionMode.Null) : _var0 == EncryptionMode.Null)
            return packet.getBytes();
        Byte buffer[];
        if(_rTPEncryption == null)
            buffer = packet.getPayload();
        else
            buffer = _rTPEncryption.encrypt(packet.getPayload(), packet.getSynchronizationSource(), getRTPEncryptionPacketIndex(packet.getSequenceNumber()));
        Byte payload[] = packet.getPayload();
        packet.setPayload(buffer);
        Byte bytes[] = packet.getBytes();
        packet.setPayload(payload);
        ArrayList list = ArrayListExtensions.createArray(bytes);
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(_rTPEncryptionROC));
        Byte collection[] = BitAssistant.subArray(Crypto.getHmacSha1(_rTPEncryptionAuth, (Byte[])list.toArray(new Byte[0])), Integer.valueOf(0), _rTPIntegritySize);
        ArrayList list2 = new ArrayList();
        ArrayListExtensions.addRange(list2, bytes);
        ArrayListExtensions.addRange(list2, collection);
        return (Byte[])list2.toArray(new Byte[0]);
    }

    public Encryptor(EncryptionMode encryptionMode, Byte localKey[], Byte localSalt[], Byte remoteKey[], Byte remoteSalt[])
        throws Exception
    {
        _octetCount = Long.valueOf(0L);
        _packetCount = Long.valueOf(0L);
        _rTCPIntegritySize = Integer.valueOf(0);
        _rTPDecryptionHighestSequenceNumber = Integer.valueOf(0);
        _rTPDecryptionROC = Long.valueOf(0L);
        _rTPEncryptionROC = Long.valueOf(0L);
        _rTPIntegritySize = Integer.valueOf(0);
        _sRTCPIndex = Integer.valueOf(0);
        _rTPEncryptionROC = Long.valueOf(0L);
        _rTPDecryptionROC = Long.valueOf(0L);
        _rTPDecryptionHighestSequenceNumber = Integer.valueOf(0);
        _sRTCPIndex = Integer.valueOf(0);
        _packetCount = Long.valueOf(0L);
        _octetCount = Long.valueOf(0L);
        setEncryptionMode(encryptionMode);
        EncryptionMode _var0 = getEncryptionMode();
        if(_var0 != null ? !_var0.equals(EncryptionMode.Null) : _var0 != EncryptionMode.Null)
        {
            AESCounter counter = new AESCounter(localKey, localSalt);
            AESCounter counter2 = new AESCounter(remoteKey, remoteSalt);
            EncryptionMode _var1 = encryptionMode;
            EncryptionMode _var2 = encryptionMode;
            if((_var1 != null ? _var1.equals(EncryptionMode.Default) : _var1 == EncryptionMode.Default) || (_var2 != null ? _var2.equals(EncryptionMode.AES128Weak) : _var2 == EncryptionMode.AES128Weak))
            {
                Byte key[] = counter.generate(_rTPKeyLabel, Integer.valueOf(16));
                Byte salt[] = counter.generate(_rTPSaltLabel, Integer.valueOf(14));
                _rTPEncryption = new AESCounter(key, salt);
                Byte buffer3[] = counter.generate(_rTCPKeyLabel, Integer.valueOf(16));
                Byte buffer4[] = counter.generate(_rTCPSaltLabel, Integer.valueOf(14));
                _rTCPEncryption = new AESCounter(buffer3, buffer4);
                Byte buffer5[] = counter2.generate(_rTPKeyLabel, Integer.valueOf(16));
                Byte buffer6[] = counter2.generate(_rTPSaltLabel, Integer.valueOf(14));
                _rTPDecryption = new AESCounter(buffer5, buffer6);
                Byte buffer7[] = counter2.generate(_rTCPKeyLabel, Integer.valueOf(16));
                Byte buffer8[] = counter2.generate(_rTCPSaltLabel, Integer.valueOf(14));
                _rTCPDecryption = new AESCounter(buffer7, buffer8);
            }
            Byte buffer9[] = counter.generate(_rTPAuthLabel, Integer.valueOf(20));
            _rTPEncryptionAuth = buffer9;
            Byte buffer10[] = counter.generate(_rTCPAuthLabel, Integer.valueOf(20));
            _rTCPEncryptionAuth = buffer10;
            Byte buffer11[] = counter2.generate(_rTPAuthLabel, Integer.valueOf(20));
            _rTPDecryptionAuth = buffer11;
            Byte buffer12[] = counter2.generate(_rTCPAuthLabel, Integer.valueOf(20));
            _rTCPDecryptionAuth = buffer12;
            EncryptionMode _var3 = getEncryptionMode();
            if(_var3 == EncryptionMode.Default || _var3 == EncryptionMode.NullStrong)
                _rTPIntegritySize = Integer.valueOf(10);
            else
            if(_var3 == EncryptionMode.AES128Weak || _var3 == EncryptionMode.NullWeak)
                _rTPIntegritySize = Integer.valueOf(4);
            _rTCPIntegritySize = Integer.valueOf(10);
        }
    }

    public EncryptionMode getEncryptionMode()
    {
        return _encryptionMode;
    }

    public Byte[] getLocalKey()
    {
        return _localKey;
    }

    public Byte[] getLocalSalt()
    {
        return _localSalt;
    }

    public Byte[] getRemoteKey()
    {
        return _remoteKey;
    }

    public Byte[] getRemoteSalt()
    {
        return _remoteSalt;
    }

    private Integer getRTCPEncryptionPacketIndex()
    {
        Encryptor encryptor = this;
        Integer integer = encryptor._sRTCPIndex;
        Integer integer1 = encryptor._sRTCPIndex = Integer.valueOf(encryptor._sRTCPIndex.intValue() + 1);
        return integer;
    }

    private Long getRTPDecryptionPacketIndex(Integer sequenceNumber)
    {
        Long rTPDecryptionROC;
        if(_rTPDecryptionHighestSequenceNumber.intValue() < 32768)
        {
            if(sequenceNumber.intValue() - _rTPDecryptionHighestSequenceNumber.intValue() > 32768)
            {
                rTPDecryptionROC = Long.valueOf((_rTPDecryptionROC.longValue() - 1L) % 34L);
            } else
            {
                rTPDecryptionROC = _rTPDecryptionROC;
                _rTPDecryptionHighestSequenceNumber = Math.max(_rTPDecryptionHighestSequenceNumber, sequenceNumber);
            }
        } else
        if(_rTPDecryptionHighestSequenceNumber.intValue() - 32768 > sequenceNumber.intValue())
        {
            rTPDecryptionROC = Long.valueOf((_rTPDecryptionROC.longValue() + 1L) % 34L);
            _rTPDecryptionHighestSequenceNumber = sequenceNumber;
            _rTPDecryptionROC = rTPDecryptionROC;
        } else
        {
            rTPDecryptionROC = _rTPDecryptionROC;
            _rTPDecryptionHighestSequenceNumber = Math.max(_rTPDecryptionHighestSequenceNumber, sequenceNumber);
        }
        return Long.valueOf(18L * rTPDecryptionROC.longValue() + (long)sequenceNumber.intValue());
    }

    private Long getRTPEncryptionPacketIndex(Integer sequenceNumber)
    {
        Long rTPEncryptionROC = _rTPEncryptionROC;
        if(sequenceNumber.intValue() == 65535)
            _rTPEncryptionROC = Long.valueOf((_rTPEncryptionROC.longValue() + 1L) % 34L);
        return Long.valueOf(18L * rTPEncryptionROC.longValue() + (long)sequenceNumber.intValue());
    }

    private void setEncryptionMode(EncryptionMode value)
    {
        _encryptionMode = value;
    }

    private void setLocalKey(Byte value[])
    {
        _localKey = value;
    }

    private void setLocalSalt(Byte value[])
    {
        _localSalt = value;
    }

    private void setRemoteKey(Byte value[])
    {
        _remoteKey = value;
    }

    private void setRemoteSalt(Byte value[])
    {
        _remoteSalt = value;
    }

    public static void testSRTP()
        throws Exception
    {
        Byte key[] = {
            Byte.valueOf((byte)-31), Byte.valueOf((byte)-7), Byte.valueOf((byte)122), Byte.valueOf((byte)13), Byte.valueOf((byte)62), Byte.valueOf((byte)1), Byte.valueOf((byte)-117), Byte.valueOf((byte)-32), Byte.valueOf((byte)-42), Byte.valueOf((byte)79),
            Byte.valueOf((byte)-93), Byte.valueOf((byte)44), Byte.valueOf((byte)6), Byte.valueOf((byte)-34), Byte.valueOf((byte)65), Byte.valueOf((byte)57)
        };
        Byte salt[] = {
            Byte.valueOf((byte)14), Byte.valueOf((byte)-58), Byte.valueOf((byte)117), Byte.valueOf((byte)-83), Byte.valueOf((byte)73), Byte.valueOf((byte)-118), Byte.valueOf((byte)-2), Byte.valueOf((byte)-21), Byte.valueOf((byte)-74), Byte.valueOf((byte)-106),
            Byte.valueOf((byte)11), Byte.valueOf((byte)58), Byte.valueOf((byte)-85), Byte.valueOf((byte)-26)
        };
        AESCounter counter = new AESCounter(key, salt);
        Byte buffer3[] = counter.generate(_rTPKeyLabel, Integer.valueOf(16));
        Byte buffer4[] = counter.generate(_rTPAuthLabel, Integer.valueOf(20));
        Byte buffer5[] = counter.generate(_rTPSaltLabel, Integer.valueOf(14));
        AESCounter counter2 = new AESCounter(buffer3, buffer5);
        Byte bytes[] = {
            Byte.valueOf((byte)-128), Byte.valueOf((byte)15), Byte.valueOf((byte)18), Byte.valueOf((byte)52), Byte.valueOf((byte)-34), Byte.valueOf((byte)-54), Byte.valueOf((byte)-5), Byte.valueOf((byte)-83), Byte.valueOf((byte)-54), Byte.valueOf((byte)-2),
            Byte.valueOf((byte)-70), Byte.valueOf((byte)-66), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85),
            Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85), Byte.valueOf((byte)-85)
        };
        String hexString = BitAssistant.getHexString(new Byte[] {
            Byte.valueOf((byte)-128), Byte.valueOf((byte)15), Byte.valueOf((byte)18), Byte.valueOf((byte)52), Byte.valueOf((byte)-34), Byte.valueOf((byte)-54), Byte.valueOf((byte)-5), Byte.valueOf((byte)-83), Byte.valueOf((byte)-54), Byte.valueOf((byte)-2),
            Byte.valueOf((byte)-70), Byte.valueOf((byte)-66), Byte.valueOf((byte)78), Byte.valueOf((byte)85), Byte.valueOf((byte)-36), Byte.valueOf((byte)76), Byte.valueOf((byte)-25), Byte.valueOf((byte)-103), Byte.valueOf((byte)120), Byte.valueOf((byte)-40),
            Byte.valueOf((byte)-116), Byte.valueOf((byte)-92), Byte.valueOf((byte)-46), Byte.valueOf((byte)21), Byte.valueOf((byte)-108), Byte.valueOf((byte)-99), Byte.valueOf((byte)36), Byte.valueOf((byte)2), Byte.valueOf((byte)-73), Byte.valueOf((byte)-115),
            Byte.valueOf((byte)106), Byte.valueOf((byte)-52), Byte.valueOf((byte)-103), Byte.valueOf((byte)-22), Byte.valueOf((byte)23), Byte.valueOf((byte)-101), Byte.valueOf((byte)-115), Byte.valueOf((byte)-69)
        });
        RTPPacket packet = RTPPacket.parseBytes(bytes);
        Byte array[] = packet.getBytes();
        String str2 = BitAssistant.getHexString(bytes);
        String str3 = BitAssistant.getHexString(array);
        String _var0 = str2;
        if(_var0 != null ? !_var0.equals(str3) : _var0 != str3)
            throw new Exception();
        Integer num = Integer.valueOf(0);
        packet.setPayload(counter2.encrypt(packet.getPayload(), packet.getSynchronizationSource(), new Long((new Integer(num.intValue() + packet.getSequenceNumber().intValue())).longValue())));
        Byte buffer9[] = packet.getBytes();
        String str4 = StringExtensions.substring(hexString, 0, StringExtensions.getLength(hexString).intValue() - 20);
        String str5 = BitAssistant.getHexString(buffer9);
        String _var1 = str4;
        if(_var1 != null ? !_var1.equals(str5) : _var1 != str5)
            throw new Exception();
        ArrayList list = ArrayListExtensions.createArray(buffer9);
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(new Long((new Integer(num.intValue())).longValue())));
        Byte buffer11[] = BitAssistant.subArray(Crypto.getHmacSha1(buffer4, (Byte[])list.toArray(new Byte[0])), Integer.valueOf(0), Integer.valueOf(10));
        String str6 = hexString.substring(StringExtensions.getLength(hexString).intValue() - 20);
        String str7 = BitAssistant.getHexString(buffer11);
        String _var2 = str6;
        if(_var2 != null ? !_var2.equals(str7) : _var2 != str7)
            throw new Exception();
        else
            return;
    }
}
