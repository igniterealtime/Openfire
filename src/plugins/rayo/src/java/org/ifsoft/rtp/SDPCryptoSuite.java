package org.ifsoft.rtp;

public abstract class SDPCryptoSuite
{

    public SDPCryptoSuite()
    {
    }

    public static String getAESCM128HMACSHA132()
    {
        return "AES_CM_128_HMAC_SHA1_32";
    }

    public static String getAESCM128HMACSHA180()
    {
        return "AES_CM_128_HMAC_SHA1_80";
    }

    public static String getCryptoSuite(EncryptionMode encryptionMode)
    {
        EncryptionMode _var0 = encryptionMode;
        if(_var0 != null ? _var0.equals(EncryptionMode.Default) : _var0 == EncryptionMode.Default)
            return getAESCM128HMACSHA180();
        EncryptionMode _var1 = encryptionMode;
        if(_var1 != null ? _var1.equals(EncryptionMode.AES128Weak) : _var1 == EncryptionMode.AES128Weak)
            return getAESCM128HMACSHA132();
        EncryptionMode _var2 = encryptionMode;
        if(_var2 != null ? _var2.equals(EncryptionMode.NullStrong) : _var2 == EncryptionMode.NullStrong)
            return getNULLHMACSHA180();
        EncryptionMode _var3 = encryptionMode;
        if(_var3 != null ? _var3.equals(EncryptionMode.NullWeak) : _var3 == EncryptionMode.NullWeak)
            return getNULLHMACSHA132();
        else
            return null;
    }

    public static EncryptionMode getEncryptionMode(String cryptoSuite)
    {
        String _var0 = cryptoSuite;
        if(_var0 != null ? _var0.equals(getAESCM128HMACSHA180()) : _var0 == getAESCM128HMACSHA180())
            return EncryptionMode.Default;
        String _var1 = cryptoSuite;
        if(_var1 != null ? _var1.equals(getAESCM128HMACSHA132()) : _var1 == getAESCM128HMACSHA132())
            return EncryptionMode.AES128Weak;
        String _var2 = cryptoSuite;
        if(_var2 != null ? _var2.equals(getNULLHMACSHA180()) : _var2 == getNULLHMACSHA180())
            return EncryptionMode.NullStrong;
        String _var3 = cryptoSuite;
        if(_var3 != null ? _var3.equals(getNULLHMACSHA132()) : _var3 == getNULLHMACSHA132())
            return EncryptionMode.NullWeak;
        else
            return EncryptionMode.Null;
    }

    public static String getNULLHMACSHA132()
    {
        return "NULL_HMAC_SHA1_32";
    }

    public static String getNULLHMACSHA180()
    {
        return "NULL_HMAC_SHA1_80";
    }
}
