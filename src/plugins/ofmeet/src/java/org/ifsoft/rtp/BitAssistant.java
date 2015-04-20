package org.ifsoft.rtp;

import java.nio.ByteBuffer;

public class BitAssistant
{

    public BitAssistant()
    {
    }

    public static Boolean isLittleEndian()
    {
        return Boolean.valueOf(false);
    }

    public static Boolean sequencesAreEqual(byte array1[], byte array2[])
    {
        if(array1 == null && array2 == null)
            return Boolean.valueOf(true);
        if(array1 == null || array2 == null)
            return Boolean.valueOf(false);
        if(array1.length != array2.length)
            return Boolean.valueOf(false);
        for(int i = 0; i < array1.length; i++)
            if(array1[i] != array2[i])
                return Boolean.valueOf(false);

        return Boolean.valueOf(true);
    }

    public static boolean sequencesAreEqual(byte array1[], int offset1, byte array2[], int offset2, int length)
    {
        if(array1 == null && array2 == null)
            return true;
        if(array1 == null || array2 == null)
            return false;
        if(array1.length < offset1 + length || array2.length < offset2 + length)
            return false;
        for(int i = 0; i < length; i++)
            if(array1[offset1 + i] != array2[offset2 + i])
                return false;

        return true;
    }

    public static boolean sequencesAreEqualConstantTime(byte array1[], byte array2[])
    {
        if(array1 == null && array2 == null)
            return true;
        if(array1 == null || array2 == null)
            return false;
        if(array1.length != array2.length)
            return false;
        boolean areEqual = true;
        for(int i = 0; i < array1.length; i++)
            if(array1[i] != array2[i])
                areEqual = false;

        return areEqual;
    }

    public static boolean sequencesAreEqualConstantTime(byte array1[], int offset1, byte array2[], int offset2, int length)
    {
        if(array1 == null && array2 == null)
            return true;
        if(array1 == null || array2 == null)
            return false;
        if(array1.length < offset1 + length || array2.length < offset2 + length)
            return false;
        boolean areEqual = true;
        for(int i = 0; i < length; i++)
            if(array1[offset1 + i] != array2[offset2 + i])
                areEqual = false;

        return areEqual;
    }

    public static byte[] subArray(byte array[], Integer offset)
    {
        return subArray(array, offset, Integer.valueOf(array.length - offset.intValue()));
    }

    public static byte[] subArray(byte array[], Integer offset, Integer count)
    {
        byte subarray[] = new byte[count.intValue()];
        for(int i = 0; i < count.intValue(); i++)
            subarray[i] = array[offset.intValue() + i];

        return subarray;
    }

    public static void reverse(byte array[])
    {
        for(int i = 0; i < array.length / 2; i++)
        {
            byte t = array[array.length - i - 1];
            array[array.length - i - 1] = array[i];
            array[i] = t;
        }

    }

    public static void copy(byte source[], int sourceIndex, byte destination[], int destinationIndex, int length)
    {
        for(int i = 0; i < length; i++)
            destination[destinationIndex + i] = source[sourceIndex + i];

    }

    public static String getHexString(byte array[])
    {
        return getHexString(array, 0, array.length);
    }

    public static String getHexString(byte array[], int offset, int length)
    {
        StringBuilder sb = new StringBuilder();
        for(int i = offset; i < offset + length; i++)
        {
            String hex = Integer.toHexString(0xff & array[i]);
            if(hex.length() == 1)
                sb.append('0');
            sb.append(hex);
        }

        return sb.toString();
    }

    public static byte[] getHexBytes(String s)
    {
        int len = s.length();
        byte bytes[] = new byte[len / 2];
        for(int i = 0; i < len; i += 2)
            bytes[i / 2] = (byte)((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));

        return bytes;
    }

    public static byte[] getBooleanBytes(Boolean value)
    {
        byte bytes[] = new byte[1];
        if(value.booleanValue())
            bytes[0] = 1;
        else
            bytes[0] = 0;
        return bytes;
    }

    public static byte[] getDoubleBytes(Double value)
    {
        byte bytes[] = new byte[8];
        ByteBuffer.wrap(bytes).putDouble(value.doubleValue());
        return bytes;
    }

    public static byte[] getFloatBytes(Float value)
    {
        byte bytes[] = new byte[4];
        ByteBuffer.wrap(bytes).putFloat(value.floatValue());
        return bytes;
    }

    public static byte[] getIntegerBytes(Integer value)
    {
        byte bytes[] = new byte[4];
        ByteBuffer.wrap(bytes).putInt(value.intValue());
        return bytes;
    }

    public static byte[] getIntegerBytesFromLong(Long value)
    {
        byte bytes[] = getLongBytes(value);
        if(isLittleEndian().booleanValue())
            return subArray(bytes, Integer.valueOf(0), Integer.valueOf(4));
        else
            return subArray(bytes, Integer.valueOf(4), Integer.valueOf(4));
    }

    public static byte[] getLongBytes(Long value)
    {
        byte bytes[] = new byte[8];
        ByteBuffer.wrap(bytes).putLong(value.longValue());
        return bytes;
    }

    public static byte[] getShortBytes(Short value)
    {
        byte bytes[] = new byte[2];
        ByteBuffer.wrap(bytes).putShort(value.shortValue());
        return bytes;
    }

    public static byte[] getShortBytesFromInteger(Integer value)
    {
        byte bytes[] = getIntegerBytes(value);
        if(isLittleEndian().booleanValue())
            return subArray(bytes, Integer.valueOf(0), Integer.valueOf(2));
        else
            return subArray(bytes, Integer.valueOf(2), Integer.valueOf(2));
    }

    public static Boolean toBoolean(byte value[], Integer startIndex)
    {
        if(value[startIndex.intValue()] == 0)
            return Boolean.valueOf(false);
        else
            return Boolean.valueOf(true);
    }

    public static Double toDouble(byte value[], Integer startIndex)
    {
        return Double.valueOf(ByteBuffer.wrap(value).getDouble(startIndex.intValue()));
    }

    public static Float toFloat(byte value[], Integer startIndex)
    {
        return Float.valueOf(ByteBuffer.wrap(value).getFloat(startIndex.intValue()));
    }

    public static Integer toInteger(byte value[], Integer startIndex)
    {
        return Integer.valueOf(ByteBuffer.wrap(value).getInt(startIndex.intValue()));
    }

    public static Integer toIntegerFromShort(byte value[], Integer startIndex)
    {
        byte bytes[] = new byte[4];
        if(isLittleEndian().booleanValue())
        {
            bytes[0] = value[startIndex.intValue()];
            bytes[1] = value[startIndex.intValue() + 1];
            bytes[2] = 0;
            bytes[3] = 0;
        } else
        {
            bytes[0] = 0;
            bytes[1] = 0;
            bytes[2] = value[startIndex.intValue()];
            bytes[3] = value[startIndex.intValue() + 1];
        }
        return toInteger(bytes, Integer.valueOf(0));
    }

    public static Long toLong(byte value[], Integer startIndex)
    {
        return Long.valueOf(ByteBuffer.wrap(value).getLong(startIndex.intValue()));
    }

    public static Long toLongFromInteger(byte value[], Integer startIndex)
    {
        byte bytes[] = new byte[8];
        if(isLittleEndian().booleanValue())
        {
            bytes[0] = value[startIndex.intValue()];
            bytes[1] = value[startIndex.intValue() + 1];
            bytes[2] = value[startIndex.intValue() + 2];
            bytes[3] = value[startIndex.intValue() + 3];
            bytes[4] = 0;
            bytes[5] = 0;
            bytes[6] = 0;
            bytes[7] = 0;
        } else
        {
            bytes[0] = 0;
            bytes[1] = 0;
            bytes[2] = 0;
            bytes[3] = 0;
            bytes[4] = value[startIndex.intValue()];
            bytes[5] = value[startIndex.intValue() + 1];
            bytes[6] = value[startIndex.intValue() + 2];
            bytes[7] = value[startIndex.intValue() + 3];
        }
        return toLong(bytes, Integer.valueOf(0));
    }

    public static Short toShort(byte value[], Integer startIndex)
    {
        return Short.valueOf(ByteBuffer.wrap(value).getShort(startIndex.intValue()));
    }

    public static byte[] getBooleanBytesNetwork(Boolean value)
    {
        byte bytes[] = getBooleanBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static byte[] getDoubleBytesNetwork(Double value)
    {
        byte bytes[] = getDoubleBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static byte[] getFloatBytesNetwork(Float value)
    {
        byte bytes[] = getFloatBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static byte[] getIntegerBytesNetwork(Integer value)
    {
        byte bytes[] = getIntegerBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static byte[] getIntegerBytesFromLongNetwork(Long value)
    {
        byte bytes[] = getIntegerBytesFromLong(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static byte[] getLongBytesNetwork(Long value)
    {
        byte bytes[] = getLongBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static byte[] getShortBytesNetwork(Short value)
    {
        byte bytes[] = getShortBytes(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static byte[] getShortBytesFromIntegerNetwork(Integer value)
    {
        byte bytes[] = getShortBytesFromInteger(value);
        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return bytes;
    }

    public static Boolean toBooleanNetwork(byte value[], Integer startIndex)
    {
        byte bytes[] = new byte[1];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toBoolean(bytes, Integer.valueOf(0));
    }

    public static Double toDoubleNetwork(byte value[], Integer startIndex)
    {
        byte bytes[] = new byte[8];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toDouble(bytes, Integer.valueOf(0));
    }

    public static Float toFloatNetwork(byte value[], Integer startIndex)
    {
        byte bytes[] = new byte[4];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toFloat(bytes, Integer.valueOf(0));
    }

    public static Integer toIntegerNetwork(byte value[], Integer startIndex)
    {
        byte bytes[] = new byte[4];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toInteger(bytes, Integer.valueOf(0));
    }

    public static Integer toIntegerFromShortNetwork(byte value[], Integer startIndex)
    {
        byte bytes[] = new byte[2];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toIntegerFromShort(bytes, Integer.valueOf(0));
    }

    public static Long toLongNetwork(byte value[], Integer startIndex)
    {
        byte bytes[] = new byte[8];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toLong(bytes, Integer.valueOf(0));
    }

    public static Long toLongFromIntegerNetwork(byte value[], Integer startIndex)
    {
        byte bytes[] = new byte[4];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toLongFromInteger(bytes, Integer.valueOf(0));
    }

    public static Short toShortNetwork(byte value[], Integer startIndex)
    {
        byte bytes[] = new byte[2];
        for(int i = 0; i < bytes.length; i++)
            bytes[i] = value[startIndex.intValue() + i];

        if(isLittleEndian().booleanValue())
            reverse(bytes);
        return toShort(bytes, Integer.valueOf(0));
    }
}
