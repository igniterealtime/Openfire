package org.ifsoft.rtp;

import java.util.ArrayList;

public class Vp8Packet
{
    private Boolean _extendedControlBitsPresent;
    private byte _keyIndex;
    private Boolean _keyIndexPresent;
    private Boolean _layerSync;
    public static Integer _maxPacketSize = Integer.valueOf(1050);
    private Boolean _nonReferenceFrame;
    private byte _partitionId;
    private byte _payload[];
    private Short _pictureID;
    private Boolean _pictureIDPresent;
    private Boolean _startOfPartition;
    private byte _temporalLayerIndex;
    private Boolean _temporalLayerIndexPresent;
    private byte _temporalLevelZeroIndex;
    private Boolean _temporalLevelZeroIndexPresent;

    public Vp8Packet()
    {
        _extendedControlBitsPresent = Boolean.valueOf(false);
        _keyIndexPresent = Boolean.valueOf(false);
        _layerSync = Boolean.valueOf(false);
        _nonReferenceFrame = Boolean.valueOf(false);
        _pictureID = Short.valueOf((short)0);
        _pictureIDPresent = Boolean.valueOf(false);
        _startOfPartition = Boolean.valueOf(false);
        _temporalLayerIndexPresent = Boolean.valueOf(false);
        _temporalLevelZeroIndexPresent = Boolean.valueOf(false);
    }


    public static byte[] depacketize(Vp8Packet packets[])
    {
        Integer num = Integer.valueOf(0);
        Vp8Packet arr[] = packets;
        int len = arr.length;
        for(int i = 0; i < len; i++)
        {
            Vp8Packet packet = arr[i];
            num = Integer.valueOf(num.intValue() + ArrayExtensions.getLength(packet.getPayload()).intValue());
        }

        Integer destinationIndex = Integer.valueOf(0);
        byte destination[] = new byte[num.intValue()];
        arr = packets;
        len = arr.length;
        for(int i = 0; i < len; i++)
        {
            Vp8Packet packet = arr[i];
            BitAssistant.copy(packet.getPayload(), 0, destination, destinationIndex.intValue(), ArrayExtensions.getLength(packet.getPayload()).intValue());
            destinationIndex = Integer.valueOf(destinationIndex.intValue() + ArrayExtensions.getLength(packet.getPayload()).intValue());
        }

        return destination;
    }

    public static byte[] getBytes(Vp8Packet packet)
    {
        ByteCollection bytes = new ByteCollection();
        bytes.add((new Integer((packet.getExtendedControlBitsPresent().booleanValue() ? 0x80 : 0) | (packet.getNonReferenceFrame().booleanValue() ? 0x20 : 0) | (packet.getStartOfPartition().booleanValue() ? 0x10 : 0) | packet.getPartitionId() & 0xf)).byteValue());
        if(packet.getExtendedControlBitsPresent().booleanValue())
        {
            bytes.add((new Integer((packet.getPictureIDPresent().booleanValue() ? 0x80 : 0) | (packet.getTemporalLevelZeroIndexPresent().booleanValue() ? 0x40 : 0) | (packet.getTemporalLayerIndexPresent().booleanValue() ? 0x20 : 0) | (packet.getKeyIndexPresent().booleanValue() ? 0x10 : 0))).byteValue());
            if(packet.getPictureIDPresent().booleanValue())
            {
                byte shortBytesNetwork[] = BitAssistant.getShortBytesNetwork(packet.getPictureID());
                bytes.add((new Integer(0x80 | shortBytesNetwork[0] & 0x7f)).byteValue());
                bytes.add(shortBytesNetwork[1]);
            }
            if(packet.getTemporalLevelZeroIndexPresent().booleanValue())
                bytes.add(packet.getTemporalLevelZeroIndex());
            if(packet.getTemporalLayerIndexPresent().booleanValue() || packet.getKeyIndexPresent().booleanValue())
                bytes.add((byte)(packet.getTemporalLayerIndex() << 6 & 0xc0 | (packet.getLayerSync().booleanValue() ? 0x20 : 0) | packet.getKeyIndex() & 0x1f));
        }
        bytes.addRange(packet.getPayload());
        return bytes.toArray();
    }

    public byte[] getBytes()
    {
        return getBytes(this);
    }

    public Boolean getExtendedControlBitsPresent()
    {
        return _extendedControlBitsPresent;
    }

    public byte getKeyIndex()
    {
        return _keyIndex;
    }

    public Boolean getKeyIndexPresent()
    {
        return _keyIndexPresent;
    }

    public Boolean getLayerSync()
    {
        return _layerSync;
    }

    public Boolean getNonReferenceFrame()
    {
        return _nonReferenceFrame;
    }

    public byte getPartitionId()
    {
        return _partitionId;
    }

    public byte[] getPayload()
    {
        return _payload;
    }

    public Short getPictureID()
    {
        return _pictureID;
    }

    public Boolean getPictureIDPresent()
    {
        return _pictureIDPresent;
    }

    public Boolean getStartOfPartition()
    {
        return _startOfPartition;
    }

    public byte getTemporalLayerIndex()
    {
        return _temporalLayerIndex;
    }

    public Boolean getTemporalLayerIndexPresent()
    {
        return _temporalLayerIndexPresent;
    }

    public byte getTemporalLevelZeroIndex()
    {
        return _temporalLevelZeroIndex;
    }

    public Boolean getTemporalLevelZeroIndexPresent()
    {
        return _temporalLevelZeroIndexPresent;
    }

    public static Vp8Packet[] packetize(byte encodedData[])
    {
        Integer offset = Integer.valueOf(0);
        ArrayList list = new ArrayList();
        Integer num2 = Integer.valueOf(1049);
        Integer num3 = new Integer((new Double(Math.ceil(new Double((new Double((new Integer(ArrayExtensions.getLength(encodedData).intValue())).doubleValue())).doubleValue() / (new Double((new Integer(num2.intValue())).doubleValue())).doubleValue())))).intValue());
        if(num3.intValue() == 0)
            num3 = Integer.valueOf(1);
        Integer num4 = Integer.valueOf(ArrayExtensions.getLength(encodedData).intValue() / num3.intValue());
        Integer num5 = Integer.valueOf(ArrayExtensions.getLength(encodedData).intValue() - num3.intValue() * num4.intValue());
        for(Integer i = Integer.valueOf(0); i.intValue() < num3.intValue();)
        {
            Integer count = num4;
            if(i.intValue() < num5.intValue())
            {
                Integer integer = count;
                Integer integer1 = count = Integer.valueOf(count.intValue() + 1);
                Integer _tmp = integer;
            }
            Vp8Packet item = new Vp8Packet();
            item.setStartOfPartition(Boolean.valueOf(i.intValue() == 0));
            item.setPayload(BitAssistant.subArray(encodedData, offset, count));
            list.add(item);
            offset = Integer.valueOf(offset.intValue() + count.intValue());
            count = i;
            i = Integer.valueOf(i.intValue() + 1);
            Integer _tmp1 = count;
        }

        return (Vp8Packet[])list.toArray(new Vp8Packet[0]);
    }

    public static Vp8Packet parseBytes(byte packetBytes[])
    {
        Integer index = Integer.valueOf(0);
        Vp8Packet packet = new Vp8Packet();
        byte num2 = packetBytes[index.intValue()];
        packet.setExtendedControlBitsPresent(Boolean.valueOf((num2 & 0x80) == 128));
        packet.setNonReferenceFrame(Boolean.valueOf((num2 & 0x20) == 32));
        packet.setStartOfPartition(Boolean.valueOf((num2 & 0x10) == 16));
        packet.setPartitionId((byte)(num2 & 0xf));
        Integer integer = index;
        Integer integer1 = index = Integer.valueOf(index.intValue() + 1);
        Integer _tmp = integer;
        if(packet.getExtendedControlBitsPresent().booleanValue())
        {
            byte num3 = packetBytes[index.intValue()];
            packet.setPictureIDPresent(Boolean.valueOf((num3 & 0x80) == 128));
            packet.setTemporalLevelZeroIndexPresent(Boolean.valueOf((num3 & 0x40) == 64));
            packet.setTemporalLayerIndexPresent(Boolean.valueOf((num3 & 0x20) == 32));
            packet.setKeyIndexPresent(Boolean.valueOf((num3 & 0x10) == 16));
            Integer integer2 = index;
            Integer integer5 = index = Integer.valueOf(index.intValue() + 1);
            Integer _tmp1 = integer2;
            if(packet.getPictureIDPresent().booleanValue())
                if((packetBytes[index.intValue()] & 0x80) == 128)
                {
                    byte buffer[] = BitAssistant.subArray(packetBytes, index, Integer.valueOf(2));
                    buffer[0] = (byte)(buffer[0] & 0x7f);
                    packet.setPictureID(BitAssistant.toShortNetwork(buffer, Integer.valueOf(0)));
                    index = Integer.valueOf(index.intValue() + 2);
                } else
                {
                    byte buffer2[] = new byte[2];
                    buffer2[1] = packetBytes[index.intValue()];
                    packet.setPictureID(BitAssistant.toShortNetwork(buffer2, Integer.valueOf(0)));
                    Integer integer6 = index;
                    Integer integer9 = index = Integer.valueOf(index.intValue() + 1);
                    Integer _tmp2 = integer6;
                }
            if(packet.getTemporalLevelZeroIndexPresent().booleanValue())
            {
                packet.setTemporalLevelZeroIndex(packetBytes[index.intValue()]);
                Integer integer3 = index;
                Integer integer7 = index = Integer.valueOf(index.intValue() + 1);
                Integer _tmp3 = integer3;
            }
            if(packet.getTemporalLayerIndexPresent().booleanValue() || packet.getKeyIndexPresent().booleanValue())
            {
                packet.setTemporalLayerIndex((byte)(packetBytes[index.intValue()] >> 6 & 3));
                packet.setLayerSync(Boolean.valueOf((packetBytes[index.intValue()] & 0x20) == 32));
                packet.setKeyIndex((byte)(packetBytes[index.intValue()] & 0x1f));
                Integer integer4 = index;
                Integer integer8 = index = Integer.valueOf(index.intValue() + 1);
                Integer _tmp4 = integer4;
            }
        }
        packet.setPayload(BitAssistant.subArray(packetBytes, index));
        return packet;
    }

    private void setExtendedControlBitsPresent(Boolean value)
    {
        _extendedControlBitsPresent = value;
    }

    private void setKeyIndex(byte value)
    {
        _keyIndex = value;
    }

    private void setKeyIndexPresent(Boolean value)
    {
        _keyIndexPresent = value;
    }

    private void setLayerSync(Boolean value)
    {
        _layerSync = value;
    }

    private void setNonReferenceFrame(Boolean value)
    {
        _nonReferenceFrame = value;
    }

    private void setPartitionId(byte value)
    {
        _partitionId = value;
    }

    private void setPayload(byte value[])
    {
        _payload = value;
    }

    private void setPictureID(Short value)
    {
        _pictureID = value;
    }

    private void setPictureIDPresent(Boolean value)
    {
        _pictureIDPresent = value;
    }

    private void setStartOfPartition(Boolean value)
    {
        _startOfPartition = value;
    }

    private void setTemporalLayerIndex(byte value)
    {
        _temporalLayerIndex = value;
    }

    private void setTemporalLayerIndexPresent(Boolean value)
    {
        _temporalLayerIndexPresent = value;
    }

    private void setTemporalLevelZeroIndex(byte value)
    {
        _temporalLevelZeroIndex = value;
    }

    private void setTemporalLevelZeroIndexPresent(Boolean value)
    {
        _temporalLevelZeroIndexPresent = value;
    }

    public static Integer getSequenceNumberDelta(Integer sequenceNumber, Integer lastSequenceNumber)
    {
        Integer num = Integer.valueOf(sequenceNumber.intValue() - lastSequenceNumber.intValue());
        if(num.intValue() < -32768)
            return Integer.valueOf(num.intValue() + 65535);
        if(num.intValue() > 32768)
            num = Integer.valueOf(num.intValue() - 65535);
        return num;
    }
}
