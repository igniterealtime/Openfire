package org.ifsoft;


public class Convert
{
    private static final char ALPHABET[];
    private static int toInt[];

    static
    {
        ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        toInt = new int[128];
        for(int i = 0; i < ALPHABET.length; i++)
            toInt[ALPHABET[i]] = i;

    }

    public Convert()
    {
    }

    public static Integer toInt32(String value, Integer base)
    {
        return Integer.valueOf(value, base.intValue());
    }

    public static Integer toInt32(char c)
    {
        return new Integer(c);
    }

    public static Byte[] fromBase64String(String s)
    {
        int delta = s.endsWith("==") ? 2 : ((int) (s.endsWith("=") ? 1 : 0));
        Byte buffer[] = new Byte[(s.length() * 3) / 4 - delta];
        int mask = 255;
        int index = 0;
        for(int i = 0; i < s.length(); i += 4)
        {
            int c0 = toInt[s.charAt(i)];
            int c1 = toInt[s.charAt(i + 1)];
            buffer[index++] = Byte.valueOf((byte)((c0 << 2 | c1 >> 4) & mask));
            if(index >= buffer.length)
                return buffer;
            int c2 = toInt[s.charAt(i + 2)];
            buffer[index++] = Byte.valueOf((byte)((c1 << 4 | c2 >> 2) & mask));
            if(index >= buffer.length)
                return buffer;
            int c3 = toInt[s.charAt(i + 3)];
            buffer[index++] = Byte.valueOf((byte)((c2 << 6 | c3) & mask));
        }

        return buffer;
    }

    public static String toBase64String(Byte b[])
    {
        int size = b.length;
        char ar[] = new char[((size + 2) / 3) * 4];
        int a = 0;
        for(int i = 0; i < size;)
        {
            byte b0 = b[i++].byteValue();
            byte b1 = i >= size ? 0 : b[i++].byteValue();
            byte b2 = i >= size ? 0 : b[i++].byteValue();
            int mask = 63;
            ar[a++] = ALPHABET[b0 >> 2 & mask];
            ar[a++] = ALPHABET[(b0 << 4 | (b1 & 0xff) >> 4) & mask];
            ar[a++] = ALPHABET[(b1 << 2 | (b2 & 0xff) >> 6) & mask];
            ar[a++] = ALPHABET[b2 & mask];
        }

        switch(size % 3)
        {
        case 1: // '\001'
            ar[--a] = '=';
            // fall through

        case 2: // '\002'
            ar[--a] = '=';
            // fall through

        default:
            return new String(ar);
        }
    }
}
