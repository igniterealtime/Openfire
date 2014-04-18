// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Display extends Common {

  public String getCountry() {
    return GetCountry(nativePointer);
  }

  public String getLanguage() {
    return GetLanguage(nativePointer);
  }

  public String getString() {
    return GetString(nativePointer);
  }

  protected Display(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
  }

  private static native String GetCountry(long jDisplay);
  private static native String GetLanguage(long jDisplay);
  private static native String GetString(long jDisplay);
}
