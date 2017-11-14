package org.ifsoft.rtp;

import org.ifsoft.*;

import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Crypto
{

    public Crypto()
    {
    }

    public static Byte[] getMD5Hash(String s)
    {
        try
        {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return BitAssistant.bytesToArray(md5.digest(BitAssistant.bytesFromArray(Encoding.getUTF8().getBytes(s))));
        }
        catch(Exception e)
        {
            return new Byte[0];
        }
    }

    public static Byte[] getHmacSha1(Byte key[], Byte buffer[])
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(BitAssistant.bytesFromArray(key), "HmacSHA1"));
            return BitAssistant.bytesToArray(mac.doFinal(BitAssistant.bytesFromArray(buffer)));
        }
        catch(Exception e)
        {
            return new Byte[0];
        }
    }

    private static void IncrementCounter(byte counter[])
    {
        int count = BitAssistant.toIntegerFromShortNetwork(BitAssistant.bytesToArray(counter), Integer.valueOf(14)).intValue();
        Byte countBytes[] = BitAssistant.getShortBytesFromIntegerNetwork(Integer.valueOf(count + 1));
        counter[14] = countBytes[0].byteValue();
        counter[15] = countBytes[1].byteValue();
    }

    public static Byte[] generateAESKeystream(Byte key[], Integer length, Byte counter[])
    {
        byte output[] = new byte[length.intValue()];
        for(int i = 0; i < output.length; i++)
            output[i] = 0;

        byte input[] = BitAssistant.bytesFromArray(counter);
        try
        {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(1, new SecretKeySpec(BitAssistant.bytesFromArray(key), "AES"));
            for(int i = 0; i < length.intValue(); i += 16)
            {
                cipher.update(input, 0, 16, output, i);
                IncrementCounter(input);
            }

            for(int i = 0; i < counter.length; i++)
                counter[i] = Byte.valueOf(input[i]);

            return BitAssistant.bytesToArray(output);
        }
        catch(Exception e)
        {
            return new Byte[0];
        }
    }
}
