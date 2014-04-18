// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class ContentEncAesSettings extends Common {

  public ContentEncAesSettings() {
    nativePointer = newContentEncAesSettings();
  }

  public long cipherMode() {
    return cipherMode(nativePointer);
  }

  public long size() {
    return Size(nativePointer);
  }

  public boolean write(IMkvWriter writer) {
    return Write(nativePointer, writer.getNativePointer());
  }

  protected ContentEncAesSettings(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteContentEncAesSettings(nativePointer);
  }

  private static native long cipherMode(long jContentEncAesSettings);
  private static native void deleteContentEncAesSettings(long jContentEncAesSettings);
  private static native long newContentEncAesSettings();
  private static native long Size(long jContentEncAesSettings);
  private static native boolean Write(long jContentEncAesSettings, long jWriter);

  public enum CipherMode {None, kCTR};

}
