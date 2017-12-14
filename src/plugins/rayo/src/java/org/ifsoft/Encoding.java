package org.ifsoft;

import java.io.UnsupportedEncodingException;

public class Encoding
{

    public Encoding()
    {
    }

    public static Encoding getUTF8()
    {
        return new Encoding();
    }

    public String getString(Byte bytes[])
    {
        return getString(bytes, 0, bytes.length);
    }

    public String getString(Byte bytes[], int index, int count)
    {
        if(bytes == null)
            return null;
        byte b[] = BitAssistant.bytesFromArray(bytes);
        try
        {
            return new String(b, index, count, "UTF-8");
        }
        catch(UnsupportedEncodingException e)
        {
            return new String(b, index, count);
        }
    }

    public Byte[] getBytes(String s)
    {
        if(s == null)
            return null;
        byte b[];
        try
        {
            b = s.getBytes("UTF-8");
        }
        catch(UnsupportedEncodingException e)
        {
            b = s.getBytes();
        }
        return BitAssistant.bytesToArray(b);
    }

    public Integer getByteCount(String s)
    {
        return Integer.valueOf(getBytes(s).length);
    }
}
