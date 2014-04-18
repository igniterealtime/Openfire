// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class ContentCompression extends Common {

  public ContentCompression() {
    nativePointer = newContentCompression();
  }

  public long getAlgo() {
    return getAlgo(nativePointer);
  }

  public void setAlgo(long algo) {
    setAlgo(nativePointer, algo);
  }

  protected ContentCompression(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteContentCompression(nativePointer);
  }

  private static native void deleteContentCompression(long jContentCompression);
  private static native long getAlgo(long jContentCompression);
  private static native long newContentCompression();
  private static native void setAlgo(long jContentCompression, long algo);
}
