package org.ifsoft.rtp;

import org.ifsoft.*;
import java.util.ArrayList;

import org.slf4j.*;
import org.slf4j.Logger;

public class Vp8Packet
{
    private static final Logger Log = LoggerFactory.getLogger(Vp8Packet.class);
    private Boolean _extendedControlBitsPresent;
    private Byte _keyIndex;
    private Boolean _keyIndexPresent;
    private Boolean _layerSync;
    public static Integer _maxPacketSize = Integer.valueOf(0);
    private Boolean _nonReferenceFrame;
    private Byte _partitionId;
    private Byte _payload[];
    private Short _pictureID;
    private Boolean _pictureIDPresent;
    private Boolean _startOfPartition;
    private Byte _temporalLayerIndex;
    private Boolean _temporalLayerIndexPresent;
    private Byte _temporalLevelZeroIndex;
    private Boolean _temporalLevelZeroIndexPresent;

    static
    {
        _maxPacketSize = Integer.valueOf(1050);
    }


    public Vp8Packet()
    {
        _extendedControlBitsPresent = Boolean.valueOf(false);
        _keyIndex = Byte.valueOf((byte)0);
        _keyIndexPresent = Boolean.valueOf(false);
        _layerSync = Boolean.valueOf(false);
        _nonReferenceFrame = Boolean.valueOf(false);
        _partitionId = Byte.valueOf((byte)0);
        _pictureID = Short.valueOf((short)0);
        _pictureIDPresent = Boolean.valueOf(false);
        _startOfPartition = Boolean.valueOf(false);
        _temporalLayerIndex = Byte.valueOf((byte)0);
        _temporalLayerIndexPresent = Boolean.valueOf(false);
        _temporalLevelZeroIndex = Byte.valueOf((byte)0);
        _temporalLevelZeroIndexPresent = Boolean.valueOf(false);
    }

    public static Byte[] depacketize(Vp8Packet packets[])
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
        Byte destinationArray[] = new Byte[num.intValue()];
        arr = packets;
        len = arr.length;

        for(int i = 0; i < len; i++)
        {
            Vp8Packet packet = arr[i];
            ArrayExtensions.copy(packet.getPayload(), 0, destinationArray, destinationIndex.intValue(), ArrayExtensions.getLength(packet.getPayload()).intValue());
            destinationIndex = Integer.valueOf(destinationIndex.intValue() + ArrayExtensions.getLength(packet.getPayload()).intValue());
        }

        return destinationArray;
    }

    public Byte[] getBytes()
    {
        ArrayList list = new ArrayList();
        list.add(new Byte((new Byte((new Integer((getExtendedControlBitsPresent().booleanValue() ? 0x80 : 0) | (getNonReferenceFrame().booleanValue() ? 0x20 : 0) | (getStartOfPartition().booleanValue() ? 0x10 : 0) | getPartitionId().byteValue() & 0xf)).byteValue())).byteValue()));
        if(getExtendedControlBitsPresent().booleanValue())
        {
            list.add(new Byte((new Byte((new Integer((getPictureIDPresent().booleanValue() ? 0x80 : 0) | (getTemporalLevelZeroIndexPresent().booleanValue() ? 0x40 : 0) | (getTemporalLayerIndexPresent().booleanValue() ? 0x20 : 0) | (getKeyIndexPresent().booleanValue() ? 0x10 : 0))).byteValue())).byteValue()));
            if(getPictureIDPresent().booleanValue())
            {
                Byte shortBytesNetwork[] = BitAssistant.getShortBytesNetwork(getPictureID());
                list.add(new Byte((new Byte((new Integer(0x80 | shortBytesNetwork[0].byteValue() & 0x7f)).byteValue())).byteValue()));
                list.add(new Byte(shortBytesNetwork[1].byteValue()));
            }
            if(getTemporalLevelZeroIndexPresent().booleanValue())
                list.add(new Byte(getTemporalLevelZeroIndex().byteValue()));
            if(getTemporalLayerIndexPresent().booleanValue() || getKeyIndexPresent().booleanValue())
                list.add(new Byte((new Byte((byte)(getTemporalLayerIndex().byteValue() << 6 & 0xc0 | (getLayerSync().booleanValue() ? 0x20 : 0) | getKeyIndex().byteValue() & 0x1f))).byteValue()));
        }
        ArrayListExtensions.addRange(list, getPayload());
        return (Byte[])list.toArray(new Byte[0]);
    }

    public Boolean getExtendedControlBitsPresent()
    {
        return _extendedControlBitsPresent;
    }

    public Byte getKeyIndex()
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

    public Byte getPartitionId()
    {
        return _partitionId;
    }

    public Byte[] getPayload()
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

    public Byte getTemporalLayerIndex()
    {
        return _temporalLayerIndex;
    }

    public Boolean getTemporalLayerIndexPresent()
    {
        return _temporalLayerIndexPresent;
    }

    public Byte getTemporalLevelZeroIndex()
    {
        return _temporalLevelZeroIndex;
    }

    public Boolean getTemporalLevelZeroIndexPresent()
    {
        return _temporalLevelZeroIndexPresent;
    }

    public static Vp8Packet[] packetize(Byte encodedData[])
    {
        Integer offset = Integer.valueOf(0);
        ArrayList list = new ArrayList();
        Integer num2 = Integer.valueOf(_maxPacketSize.intValue() - 1);
        Integer num3 = new Integer((new Double(org.ifsoft.Math.ceiling(new Double((new Double((new Integer(ArrayExtensions.getLength(encodedData).intValue())).doubleValue())).doubleValue() / (new Double((new Integer(num2.intValue())).doubleValue())).doubleValue())).doubleValue())).intValue());
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

    public static Vp8Packet parse(Byte packetBytes[])
    {
        Integer index = Integer.valueOf(0);
        Vp8Packet packet = new Vp8Packet();
        Byte num2 = packetBytes[index.intValue()];
        packet.setExtendedControlBitsPresent(Boolean.valueOf((num2.byteValue() & 0x80) == 128));
        packet.setNonReferenceFrame(Boolean.valueOf((num2.byteValue() & 0x20) == 32));
        packet.setStartOfPartition(Boolean.valueOf((num2.byteValue() & 0x10) == 16));
        packet.setPartitionId(new Byte((byte)(num2.byteValue() & 0xf)));
        Integer integer = index;
        Integer integer1 = index = Integer.valueOf(index.intValue() + 1);
        Integer _tmp = integer;
        if(packet.getExtendedControlBitsPresent().booleanValue())
        {
            Byte num3 = packetBytes[index.intValue()];
            packet.setPictureIDPresent(Boolean.valueOf((num3.byteValue() & 0x80) == 128));
            packet.setTemporalLevelZeroIndexPresent(Boolean.valueOf((num3.byteValue() & 0x40) == 64));
            packet.setTemporalLayerIndexPresent(Boolean.valueOf((num3.byteValue() & 0x20) == 32));
            packet.setKeyIndexPresent(Boolean.valueOf((num3.byteValue() & 0x10) == 16));
            Integer integer2 = index;
            Integer integer5 = index = Integer.valueOf(index.intValue() + 1);
            Integer _tmp1 = integer2;
            if(packet.getPictureIDPresent().booleanValue())
                if((packetBytes[index.intValue()].byteValue() & 0x80) == 128)
                {
                    Byte buffer[] = BitAssistant.subArray(packetBytes, index, Integer.valueOf(2));
                    buffer[0] = new Byte((byte)(buffer[0].byteValue() & 0x7f));
                    packet.setPictureID(BitAssistant.toShortNetwork(buffer, Integer.valueOf(0)));
                    index = Integer.valueOf(index.intValue() + 2);
                } else
                {
                    Byte buffer2[] = new Byte[2];
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
                packet.setTemporalLayerIndex(new Byte((byte)(packetBytes[index.intValue()].byteValue() >> 6 & 3)));
                packet.setLayerSync(Boolean.valueOf((packetBytes[index.intValue()].byteValue() & 0x20) == 32));
                packet.setKeyIndex(new Byte((byte)(packetBytes[index.intValue()].byteValue() & 0x1f)));
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

    private void setKeyIndex(Byte value)
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

    private void setPartitionId(Byte value)
    {
        _partitionId = value;
    }

    private void setPayload(Byte value[])
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

    private void setTemporalLayerIndex(Byte value)
    {
        _temporalLayerIndex = value;
    }

    private void setTemporalLayerIndexPresent(Boolean value)
    {
        _temporalLayerIndexPresent = value;
    }

    private void setTemporalLevelZeroIndex(Byte value)
    {
        _temporalLevelZeroIndex = value;
    }

    private void setTemporalLevelZeroIndexPresent(Boolean value)
    {
        _temporalLevelZeroIndexPresent = value;
    }
}
