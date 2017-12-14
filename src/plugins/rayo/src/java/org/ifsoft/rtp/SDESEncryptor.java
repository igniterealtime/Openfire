package org.ifsoft.rtp;

import org.ifsoft.*;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDESEncryptor
{

    public synchronized RTCPPacket[] decryptRTCP(Byte encryptedBytes[])
    {
        return null;
    }

    public synchronized RTPPacket decryptRTP(Byte encryptedBytes[]) throws ArgumentOutOfRangeException
    {
        return null;
    }

    public synchronized Byte[] encryptRTCP(RTCPPacket packets[]) throws Exception
    {
        return null;
    }

    public synchronized Byte[] encryptRTP(RTPPacket packet) throws Exception
    {
        return null;
    }

    public SDESEncryptor(EncryptionMode encryptionMode, Byte localKey[], Byte localSalt[], Byte remoteKey[], Byte remoteSalt[]) throws Exception
    {
    }
}
