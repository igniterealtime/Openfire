package org.ifsoft.rtp;

import org.ifsoft.*;

public class AESCounter
{

    private Byte _key[];
    private Byte _offset[];
    private Byte _salt[];


    public Byte[] decrypt(Byte data[], Long ssrc, Long packetIndex)
    {
        return encrypt(data, ssrc, packetIndex);
    }

    public Byte[] encrypt(Byte data[], Long ssrc, Long packetIndex)
    {
        Byte counter[] = new Byte[16];
        for(Integer num = Integer.valueOf(0); num.intValue() < ArrayExtensions.getLength(counter).intValue();)
        {
            counter[num.intValue()] = _offset[num.intValue()];
            Integer integer = num;
            Integer integer1 = num = Integer.valueOf(num.intValue() + 1);
            Integer _tmp = integer;
        }

        Byte integerBytesFromLongNetwork[] = BitAssistant.getIntegerBytesFromLongNetwork(ssrc);
        for(Integer num = Integer.valueOf(0); num.intValue() < ArrayExtensions.getLength(integerBytesFromLongNetwork).intValue();)
        {
            counter[num.intValue() + 4] = new Byte((byte)(counter[num.intValue() + 4].byteValue() ^ integerBytesFromLongNetwork[num.intValue()].byteValue()));
            Integer integer2 = num;
            Integer integer3 = num = Integer.valueOf(num.intValue() + 1);
            Integer _tmp1 = integer2;
        }

        Byte buffer3[] = BitAssistant.subArray(BitAssistant.getLongBytesNetwork(packetIndex), Integer.valueOf(2));
        for(Integer num = Integer.valueOf(0); num.intValue() < ArrayExtensions.getLength(buffer3).intValue();)
        {
            counter[num.intValue() + 8] = new Byte((byte)(counter[num.intValue() + 8].byteValue() ^ buffer3[num.intValue()].byteValue()));
            Integer integer4 = num;
            Integer integer5 = num = Integer.valueOf(num.intValue() + 1);
            Integer _tmp2 = integer4;
        }

        Integer length = Integer.valueOf((ArrayExtensions.getLength(data).intValue() / 16) * 16);
        if(length.intValue() < ArrayExtensions.getLength(data).intValue())
            length = Integer.valueOf(length.intValue() + 16);
        Byte array[] = Crypto.generateAESKeystream(_key, length, counter);
        if(ArrayExtensions.getLength(array).intValue() != 0)
        {
            array = BitAssistant.subArray(array, Integer.valueOf(0), ArrayExtensions.getLength(data));
            for(Integer num = Integer.valueOf(0); num.intValue() < ArrayExtensions.getLength(array).intValue();)
            {
                array[num.intValue()] = new Byte((byte)(array[num.intValue()].byteValue() ^ data[num.intValue()].byteValue()));
                Integer integer6 = num;
                Integer integer7 = num = Integer.valueOf(num.intValue() + 1);
                Integer _tmp3 = integer6;
            }

        }
        return array;
    }

    public AESCounter(Byte key[], Byte salt[])
        throws Exception
    {
        if(ArrayExtensions.getLength(key).intValue() != 16)
            throw new Exception("Invalid key length.");
        if(ArrayExtensions.getLength(salt).intValue() != 14)
            throw new Exception("Invalid salt length.");
        _key = key;
        _salt = salt;
        _offset = new Byte[16];
        for(Integer num = Integer.valueOf(0); num.intValue() < ArrayExtensions.getLength(_offset).intValue();)
        {
            _offset[num.intValue()] = Byte.valueOf((byte)0);
            Integer integer = num;
            Integer integer2 = num = Integer.valueOf(num.intValue() + 1);
            Integer _tmp = integer;
        }

        for(Integer num = Integer.valueOf(0); num.intValue() < ArrayExtensions.getLength(_salt).intValue();)
        {
            _offset[num.intValue()] = _salt[num.intValue()];
            Integer integer1 = num;
            Integer integer3 = num = Integer.valueOf(num.intValue() + 1);
            Integer _tmp1 = integer1;
        }

    }

    public Byte[] generate(Byte label, Integer length)
    {
        Byte buffer[] = new Byte[16];
        for(Integer num = Integer.valueOf(0); num.intValue() < ArrayExtensions.getLength(buffer).intValue();)
        {
            buffer[num.intValue()] = Byte.valueOf((byte)0);
            Integer integer = num;
            Integer integer1 = num = Integer.valueOf(num.intValue() + 1);
            Integer _tmp = integer;
        }

        buffer[7] = label;
        Byte counter[] = new Byte[16];
        for(Integer num = Integer.valueOf(0); num.intValue() < ArrayExtensions.getLength(counter).intValue();)
        {
            counter[num.intValue()] = new Byte((byte)(_offset[num.intValue()].byteValue() ^ buffer[num.intValue()].byteValue()));
            Integer integer2 = num;
            Integer integer3 = num = Integer.valueOf(num.intValue() + 1);
            Integer _tmp1 = integer2;
        }

        Integer num2 = Integer.valueOf((length.intValue() / 16) * 16);
        if(num2.intValue() < length.intValue())
            num2 = Integer.valueOf(num2.intValue() + 16);
        Byte array[] = Crypto.generateAESKeystream(_key, num2, counter);
        if(ArrayExtensions.getLength(array).intValue() == 0)
            return array;
        else
            return BitAssistant.subArray(array, Integer.valueOf(0), length);
    }

}
