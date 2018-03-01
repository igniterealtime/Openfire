package com.jcumulus.server.rtmfp;

/**
 * jCumulus is a Java port of Cumulus OpenRTMP
 *
 * Copyright 2011 OpenRTMFP
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License received along this program for more
 * details (or else see http://www.gnu.org/licenses/).
 *
 *
 * This file is a part of jCumulus.
 */

import com.jcumulus.server.rtmfp.packet.*;

import com.jcumulus.server.rtmfp.pipe.C;
import com.jcumulus.server.rtmfp.pipe.D;
import com.jcumulus.server.rtmfp.stream.B;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.log4j.Logger;


public class N
{

    public static byte A[] = "Adobe Systems 02".getBytes();
    public static int B = 1935;
    public static int C = 12;
    public static int G = 1192;
    private static final Logger E = Logger.getLogger(C.class);
    private static byte D[] = {
        -1, -1, -1, -1, -1, -1, -1, -1, -55, 15,
        -38, -94, 33, 104, -62, 52, -60, -58, 98, -117,
        -128, -36, 28, -47, 41, 2, 78, 8, -118, 103,
        -52, 116, 2, 11, -66, -90, 59, 19, -101, 34,
        81, 74, 8, 121, -114, 52, 4, -35, -17, -107,
        25, -77, -51, 58, 67, 27, 48, 43, 10, 109,
        -14, 95, 20, 55, 79, -31, 53, 109, 109, 81,
        -62, 69, -28, -123, -75, 118, 98, 94, 126, -58,
        -12, 76, 66, -23, -90, 55, -19, 107, 11, -1,
        92, -74, -12, 6, -73, -19, -18, 56, 107, -5,
        90, -119, -97, -91, -82, -97, 36, 17, 124, 75,
        31, -26, 73, 40, 102, 81, -20, -26, 83, -127,
        -1, -1, -1, -1, -1, -1, -1, -1
    };
    public static int F = 128;


    public N()
    {
    }

    public static int A(AudioPacket a)
    {
        int i = 0;
        for(int j = 0; j < 3; j++)
            i ^= a.C();

        a.E(4);
        return i;
    }

    public static void A(B b, int i)
    {
        Packet a = b.G();
        a.E(4);
        b.F(0);
        b.A(a.C() ^ a.C() ^ i);
    }

    public static boolean A(K k, AudioPacket a)
    {
        byte abyte0[] = k.A(a.G(), 0, a.I());
        if(abyte0 == null)
        {
            return false;
        } else
        {
            a.A(abyte0, abyte0.length);
            return B(a);
        }
    }

    public static B A(K k, B b)
    {
        if(k.A())
        {
            int i = (-1 - b.A()) + 5 & 0xf;
            b.F(b.A());
            b.A((byte)-1, i);
        } else
        {
            E.error("AesEncrypt not valid");
        }
        A(b);
        byte abyte0[] = k.A(b.B(), 4, b.A() - 4);
        if(abyte0 != null)
        {
            abyte0 = A(abyte0, 0, 4);
            return new B(abyte0, abyte0.length);
        } else
        {
            return null;
        }
    }

    private static byte[] A(byte abyte0[], int i, int j)
    {
        int k = abyte0.length;
        byte abyte1[] = new byte[k + j];
        System.arraycopy(abyte0, 0, abyte1, 0, i);
        for(int l = 0; l < j; l++)
            abyte1[i + l] = 0;

        System.arraycopy(abyte0, i, abyte1, i + j, k - i);
        return abyte1;
    }

    public static boolean B(AudioPacket a)
    {
        short word0 = a.E();
        short word1 = C(a);
        return word0 == word1;
    }

    public static void A(B b)
    {
        AudioPacket a = b.G();
        a.E(6);
        short word0 = C(a);
        b.F(4);
        b.A(word0);
    }

    public static short C(AudioPacket a)
    {
        int i = 0;
        int j = a.H();
        while(a.I() > 0)
            i += a.I() != 1 ? a.E() & 0xffff : a.L() & 0xff;
        a.E(j);
        i = (i >> 16) + (i & 0xffff);
        i += i >> 16;
        return (short)(~i & 0xffff);
    }

    public static D B()
    {
        BigInteger biginteger = new BigInteger(1, D);
        BigInteger biginteger1 = BigInteger.valueOf(2L);
        byte abyte0[] = new byte[D.length];
        try
        {
            com.jcumulus.server.rtmfp.pipe.C.A().read(abyte0);
            BigInteger biginteger2 = new BigInteger(1, abyte0);
            byte abyte1[] = biginteger1.modPow(biginteger2, biginteger).toByteArray();
            byte abyte2[] = Arrays.copyOfRange(abyte1, abyte1.length - F, abyte1.length);
            return new D(abyte0, abyte2);
        }
        catch(IOException ioexception)
        {
            E.error(ioexception.getMessage(), ioexception);
        }
        return null;
    }

    public static byte[] A(byte abyte0[], byte abyte1[])
    {
        BigInteger biginteger = new BigInteger(1, D);
        byte abyte2[] = (new BigInteger(1, abyte1)).modPow(new BigInteger(1, abyte0), biginteger).toByteArray();
        return Arrays.copyOfRange(abyte2, abyte2.length - F, abyte2.length);
    }

    public static byte[] B(byte abyte0[], byte abyte1[], int i, byte abyte2[])
    {
        try
        {
            SecretKeySpec secretkeyspec = new SecretKeySpec(abyte1, 0, i, "hmacSHA256");
            Mac mac = Mac.getInstance("hmacSHA256");
            mac.init(secretkeyspec);
            byte abyte3[] = mac.doFinal(abyte2);
            secretkeyspec = new SecretKeySpec(abyte0, "hmacSHA256");
            mac = Mac.getInstance("hmacSHA256");
            mac.init(secretkeyspec);
            return mac.doFinal(abyte3);
        }
        catch(NoSuchAlgorithmException nosuchalgorithmexception)
        {
            E.error(nosuchalgorithmexception.getMessage(), nosuchalgorithmexception);
        }
        catch(InvalidKeyException invalidkeyexception)
        {
            E.error(invalidkeyexception.getMessage(), invalidkeyexception);
        }
        return null;
    }

    public static byte[] A(byte abyte0[], byte abyte1[], int i, byte abyte2[])
    {
        try
        {
            SecretKeySpec secretkeyspec = new SecretKeySpec(abyte2, "hmacSHA256");
            Mac mac = Mac.getInstance("hmacSHA256");
            mac.init(secretkeyspec);
            mac.update(abyte1, 0, i);
            byte abyte3[] = mac.doFinal();
            secretkeyspec = new SecretKeySpec(abyte0, "hmacSHA256");
            mac = Mac.getInstance("hmacSHA256");
            mac.init(secretkeyspec);
            return mac.doFinal(abyte3);
        }
        catch(NoSuchAlgorithmException nosuchalgorithmexception)
        {
            E.error(nosuchalgorithmexception.getMessage(), nosuchalgorithmexception);
        }
        catch(InvalidKeyException invalidkeyexception)
        {
            E.error(invalidkeyexception.getMessage(), invalidkeyexception);
        }
        return null;
    }

    public static short A()
    {
        return A((new com.jcumulus.server.rtmfp.pipe.B()).getTime());
    }

    public static short A(long l)
    {
        return (short)(int)(0x13880L / (l * 4L));
    }
}
