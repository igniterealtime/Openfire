// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class ContentEncoding extends Common {

  public ContentEncoding() {
    nativePointer = newContentEncoding();
  }

  public ContentEncAesSettings encAesSettings() {
    long pointer = encAesSettings(nativePointer);
    return new ContentEncAesSettings(pointer);
  }

  public long encAlgo() {
    return encAlgo(nativePointer);
  }

  public long encodingOrder() {
    return encodingOrder(nativePointer);
  }

  public long encodingScope() {
    return encodingScope(nativePointer);
  }

  public long encodingType() {
    return encodingType(nativePointer);
  }

  public boolean setEncryptionId(byte[] id) {
    return SetEncryptionID(nativePointer, id, id.length);
  }

  public long size() {
    return Size(nativePointer);
  }

  public boolean write(IMkvWriter writer) {
    return Write(nativePointer, writer.getNativePointer());
  }

  protected ContentEncoding(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteContentEncoding(nativePointer);
  }

  private static native void deleteContentEncoding(long jContentEncoding);
  private static native long encAesSettings(long jContentEncoding);
  private static native long encAlgo(long jContentEncoding);
  private static native long encodingOrder(long jContentEncoding);
  private static native long encodingScope(long jContentEncoding);
  private static native long encodingType(long jContentEncoding);
  private static native long newContentEncoding();
  private static native boolean SetEncryptionID(long jContentEncoding, byte[] jId, long length);
  private static native long Size(long jContentEncoding);
  private static native boolean Write(long jContentEncoding, long jWriter);
}
