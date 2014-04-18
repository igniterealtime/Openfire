// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class ContentEncryption extends Common {

  public ContentEncryption() {
    nativePointer = newContentEncrytpion();
  }

  public ContentEncAesSettings getAesSettings() {
    long pointer = getAesSettings(nativePointer);
    return new ContentEncAesSettings(pointer);
  }

  public long getAlgo() {
    return getAlgo(nativePointer);
  }

  public byte[] getKeyId() {
    return getKeyId(nativePointer);
  }

  public long getSigAlgo() {
    return getSigAlgo(nativePointer);
  }

  public long getSigHashAlgo() {
    return getSigHashAlgo(nativePointer);
  }

  public byte[] getSigKeyId() {
    return getSigKeyId(nativePointer);
  }

  public byte[] getSignature() {
    return getSignature(nativePointer);
  }

  public void setAesSettings(ContentEncAesSettings aesSettings) {
    setAesSettings(nativePointer, aesSettings.getNativePointer());
  }

  public void setAlgo(long algo) {
    setAlgo(nativePointer, algo);
  }

  public void setKeyId(byte[] keyId) {
    setKeyId(nativePointer, keyId);
  }

  public void setSigAlgo(long sigAlgo) {
    setSigAlgo(nativePointer, sigAlgo);
  }

  public void setSigHashAlgo(long sigHashAlgo) {
    setSigHashAlgo(nativePointer, sigHashAlgo);
  }

  public void setSigKeyId(byte[] sigKeyId) {
    setSigKeyId(nativePointer, sigKeyId);
  }

  public void setSignature(byte[] signature) {
    setSignature(nativePointer, signature);
  }

  protected ContentEncryption(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteContentEncrytpion(nativePointer);
  }

  private static native void deleteContentEncrytpion(long jContentEncrytpion);
  private static native long getAesSettings(long jContentEncrytpion);
  private static native long getAlgo(long jContentEncrytpion);
  private static native byte[] getKeyId(long jContentEncrytpion);
  private static native long getSigAlgo(long jContentEncrytpion);
  private static native long getSigHashAlgo(long jContentEncrytpion);
  private static native byte[] getSigKeyId(long jContentEncrytpion);
  private static native byte[] getSignature(long jContentEncrytpion);
  private static native long newContentEncrytpion();
  private static native void setAesSettings(long jContentEncrytpion, long aesSettings);
  private static native void setAlgo(long jContentEncrytpion, long algo);
  private static native void setKeyId(long jContentEncrytpion, byte[] jKeyId);
  private static native void setSigAlgo(long jContentEncrytpion, long sigAlgo);
  private static native void setSigHashAlgo(long jContentEncrytpion, long sigHashAlgo);
  private static native void setSigKeyId(long jContentEncrytpion, byte[] jSigKeyId);
  private static native void setSignature(long jContentEncrytpion, byte[] jSignature);
}
