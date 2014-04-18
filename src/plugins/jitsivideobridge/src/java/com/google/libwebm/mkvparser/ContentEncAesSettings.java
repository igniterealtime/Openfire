// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class ContentEncAesSettings extends Common {

  public ContentEncAesSettings() {
    nativePointer = newContentEncAesSettings();
  }

  public ContentEncoding.CipherMode getCipherMode() {
    int ordinal = (int) getCipherMode(nativePointer);
    return ContentEncoding.CipherMode.values()[ordinal];
  }

  public void setCipherMode(ContentEncoding.CipherMode cipherMode) {
    setCipherMode(nativePointer, cipherMode.ordinal());
  }

  protected ContentEncAesSettings(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteContentEncAesSettings(nativePointer);
  }

  private static native void deleteContentEncAesSettings(long jContentEncAesSettings);
  private static native long getCipherMode(long jContentEncAesSettings);
  private static native long newContentEncAesSettings();
  private static native void setCipherMode(long jContentEncAesSettings, long cipherMode);
}
