package org.ifsoft;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class BitAssistant
{

    public BitAssistant()
    {
    }

    public static ArrayList bytesToArrayList(byte bytes[])
    {
        ArrayList arrayList = new ArrayList(bytes.length);
        for(int i = 0; i < bytes.length; i++)
            arrayList.set(i, new Byte(bytes[i]));

        return arrayList;
    }

    public static byte[] bytesFromArrayList(ArrayList arrayList)
    {
        byte bytes[] = new byte[arrayList.size()];
        for(int i = 0; i < arrayList.size(); i++)
            bytes[i] = ((Byte)arrayList.get(i)).byteValue();

        return bytes;
    }

    public static Byte[] bytesToArray(byte bytes[])
    {
        Byte array[] = new Byte[bytes.length];
        for(int i = 0; i < array.length; i++)
            array[i] = new Byte(bytes[i]);

        return array;
    }

    public static byte[] bytesFromArray(Byte array[])
    {
        byte bytes[] = new byte[array.length];
        for(int i = 0; i < array.length; i++)
            bytes[i] = array[i].byteValue();

        return bytes;
    }

    public static Boolean isLittleEndian()
    {
        return Boolean.valueOf(false);
    }

    public static Boolean sequencesAreEqual(Byte array1[], Byte array2[])
    {
        if(array1 == null && array2 == null)
            return Boolean.valueOf(true);
        if(array1 == null || array2 == null)
            return Boolean.valueOf(false);
        if(array1.length != array2.length)
            return Boolean.valueOf(false);
        for(int i = 0; i < array1.length; i++)
            if(array1[i].byteValue() != array2[i].byteValue())
                return Boolean.valueOf(false);

        return Boolean.valueOf(true);
    }

    public static Byte[] subArray(Byte array[], Integer offset)
    {
        return subArray(array, offset, Integer.valueOf(array.length - offset.intValue()));
    }

    public static Byte[] subArray(Byte array[], Integer offset, Integer count)
    {
        Byte subarray[] = new Byte[count.intValue()];
        for(int i = 0; i < count.intValue(); i++)
            subarray[i] = array[offset.intValue() + i];

        return subarray;
    }

    public static void reverse(Byte array[])
    {
        for(int i = 0; i < array.length / 2; i++)
        {
            Byte t = array[array.length - i - 1];
            array[array.length - i - 1] = array[i];
            array[i] = t;
        }

    }

    public static String getHexString(Byte array[])
    {
        StringBuilder sb = new StringBuilder();
        Byte arr$[] = array;
        int len$ = arr$.length;
        for(int i$ = 0; i$ < len$; i$++)
        {
            Byte b = arr$[i$];
            String hex = Integer.toHexString(0xff & b.byteValue());
            if(hex.length() == 1)
                sb.append('0');
            sb.append(hex);
        }

        return sb.toString();
    }

    public static Byte[] getHexBytes(String s)
    {
        int len = s.length();
        Byte bytes[] = new Byte[len / 2];
        for(int i = 0; i < len; i += 2)
            bytes[i / 2] = Byte.valueOf((byte)((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16)));

        return bytes;
    }

    public static Byte[] getBooleanBytes(Boolean value)
    {
        byte bytes[] = new byte[1];
        if(value.booleanValue())
            bytes[0] = 1;
        else
            bytes[0] = 0;
        return bytesToArray(bytes);
    }

    public static Byte[] getDoubleBytes(Double value)
    {
        byte bytes[] = new byte[8];
        ByteBuffer.wrap(bytes).putDouble(value.doubleValue());
        return bytesToArray(bytes);
    }

    public static Byte[] getFloatBytes(Float value)
    {
        byte bytes[] = new byte[4];
        ByteBuffer.wrap(bytes).putFloat(value.floatValue());
        return bytesToArray(bytes);
    }

    public static Byte[] getIntegerBytes(Integer value)
    {
        byte bytes[] = new byte[4];
        ByteBuffer.wrap(bytes).putInt(value.intValue());
        return bytesToArray(bytes);
    }

    public static Byte[] getIntegerBytesFromLong(Long value)
    {
        Byte bytes[] = getLongBytes(value);
        if(isLittleEndian().booleanValue())
            return subArray(bytes, Integer.valueOf(0), Integer.valueOf(4));
        else
            return subArray(bytes, Integer.valueOf(4), Integer.valueOf(4));
    }

    public static Byte[] getLongBytes(Long value)
    {
        byte bytes[] = new byte[8];
        ByteBuffer.wrap(bytes).putLong(value.longValue());
        return bytesToArray(bytes);
    }

    public static Byte[] getShortBytes(Short value)
    {
        byte bytes[] = new byte[2];
        ByteBuffer.wrap(bytes).putShort(value.shortValue());
        return bytesToArray(bytes);
    }

    public static Byte[] getShortBytesFromInteger(Integer value)
    {
        Byte bytes[] = getIntegerBytes(value);
        if(isLittleEndian().booleanValue())
            return subArray(bytes, Integer.valueOf(0), Integer.valueOf(2));
        else
            return subArray(bytes, Integer.valueOf(2), Integer.valueOf(2));
    }

    public static Boolean toBoolean(Byte value[], Integer startIndex)
    {
        if(value[startIndex.intValue()].byteValue() == 0)
            return Boolean.valueOf(false);
        else
            return Boolean.valueOf(true);
    }

    public static Double toDouble(Byte value[], Integer startIndex)
    {
        return Double.valueOf(ByteBuffer.wrap(bytesFromArray(value)).getDouble(startIndex.intValue()));
    }

    public static Float toFloat(Byte value[], Integer startIndex)
    {
        return Float.valueOf(ByteBuffer.wrap(bytesFromArray(value)).getFloat(startIndex.intValue()));
    }

    public static Integer toInteger(Byte value[], Integer startIndex)
    {
        return Integer.valueOf(ByteBuffer.wrap(bytesFromArray(value)).getInt(startIndex.intValue()));
    }

    public static Integer toIntegerFromShort(Byte value[], Integer startIndex)
    {
        Byte bytes[] = new Byte[4];
        if(isLittleEndian().booleanValue())
        {
            bytes[0] = value[startIndex.intValue()];
            bytes[1] = value[startIndex.intValue() + 1];
            bytes[2] = Byte.valueOf((byte)0);
            bytes[3] = Byte.valueOf((byte)0);
        } else
        {
            bytes[0] = Byte.valueOf((byte)0);
            bytes[1] = Byte.valueOf((byte)0);
            bytes[2] = value[startIndex.intValue()];
            bytes[3] = value[startIndex.intValue() + 1];
        }
        return toInteger(bytes, Integer.valueOf(0));
    }

    public static Long toLong(Byte value[], Integer startIndex)
    {
        return Long.valueOf(ByteBuffer.wrap(bytesFromArray(value)).getLong(startIndex.intValue()));
    }

    public static Long toLongFromInteger(Byte value[], Integer startIndex)
    {
        Byte bytes[] = new Byte[8];
        if(isLittleEndian().booleanValue())
        {
            bytes[0] = value[startIndex.intValue()];
            bytes[1] = value[startIndex.intValue() + 1];
            bytes[2] = value[startIndex.intValue() + 2];
            bytes[3] = value[startIndex.intValue() + 3];
            bytes[4] = Byte.valueOf((byte)0);
            bytes[5] = Byte.valueOf((byte)0);
            bytes[6] = Byte.valueOf((byte)0);
            bytes[7] = Byte.valueOf((byte)0);
        } else
        {
            bytes[0] = Byte.valueOf((byte)0);
            bytes[1] = Byte.valueOf((byte)0);
            bytes[2] = Byte.valueOf((byte)0);
            bytes[3] = Byte.valueOf((byte)0);
            bytes[4] = value[startIndex.intValue()];
            bytes[5] = value[startIndex.intValue() + 1];
            bytes[6] = value[startIndex.intValue() + 2];
            bytes[7] = value[startIndex.intValue() + 3];
        }
        return toLong(bytes, Integer.valueOf(0));
    }

    public static Short toShort(Byte value[], Integer startIndex)
    {
        return Short.valueOf(ByteBuffer.wrap(bytesFromArray(value)).getShort(startIndex.intValue()));
    }

    public static Byte[] getBooleanBytesNetwork(Boolean value)
    {
        Byte bytes[] = getBooleanBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static Byte[] getDoubleBytesNetwork(Double value)
    {
        Byte bytes[] = getDoubleBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static Byte[] getFloatBytesNetwork(Float value)
    {
        Byte bytes[] = getFloatBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static Byte[] getIntegerBytesNetwork(Integer value)
    {
        Byte bytes[] = getIntegerBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static Byte[] getIntegerBytesFromLongNetwork(Long value)
    {
        Byte bytes[] = getIntegerBytesFromLong(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static Byte[] getLongBytesNetwork(Long value)
    {
        Byte bytes[] = getLongBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static Byte[] getShortBytesNetwork(Short value)
    {
        Byte bytes[] = getShortBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static Byte[] getShortBytesFromIntegerNetwork(Integer value)
    {
        Byte bytes[] = getShortBytesFromInteger(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static Boolean toBooleanNetwork(Byte value[], Integer startIndex)
    {
        Byte bytes[] = new Byte[1];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toBoolean(bytes, Integer.valueOf(0));
    }

    public static Double toDoubleNetwork(Byte value[], Integer startIndex)
    {
        Byte bytes[] = new Byte[8];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toDouble(bytes, Integer.valueOf(0));
    }

    public static Float toFloatNetwork(Byte value[], Integer startIndex)
    {
        Byte bytes[] = new Byte[4];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toFloat(bytes, Integer.valueOf(0));
    }

    public static Integer toIntegerNetwork(Byte value[], Integer startIndex)
    {
        Byte bytes[] = new Byte[4];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toInteger(bytes, Integer.valueOf(0));
    }

    public static Integer toIntegerFromShortNetwork(Byte value[], Integer startIndex)
    {
        Byte bytes[] = new Byte[2];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toIntegerFromShort(bytes, Integer.valueOf(0));
    }

    public static Long toLongNetwork(Byte value[], Integer startIndex)
    {
        Byte bytes[] = new Byte[8];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toLong(bytes, Integer.valueOf(0));
    }

    public static Long toLongFromIntegerNetwork(Byte value[], Integer startIndex)
    {
        Byte bytes[] = new Byte[4];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toLongFromInteger(bytes, Integer.valueOf(0));
    }

    public static Short toShortNetwork(Byte value[], Integer startIndex)
    {
        Byte bytes[] = new Byte[2];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toShort(bytes, Integer.valueOf(0));
    }
}
