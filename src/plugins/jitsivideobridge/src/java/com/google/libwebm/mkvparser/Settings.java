// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Settings extends Common {

  public Settings() {
    nativePointer = newSettings();
  }

  public long getSize() {
    return getSize(nativePointer);
  }

  public long getStart() {
    return getStart(nativePointer);
  }

  public void setSize(long size) {
    setSize(nativePointer, size);
  }

  public void setStart(long start) {
    setStart(nativePointer, start);
  }

  protected Settings(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteSettings(nativePointer);
  }

  private static native void deleteSettings(long jSettings);
  private static native long getSize(long jSettings);
  private static native long getStart(long jSettings);
  private static native long newSettings();
  private static native void setSize(long jSettings, long size);
  private static native void setStart(long jSettings, long start);
}
