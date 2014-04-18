// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public abstract class IMkvReader extends Common {

  public static IMkvReader newMkvReader(long nativePointer) {
    IMkvReader mkvReader = null;
    int type = getClassType(nativePointer);
    if (type == 1) {
      mkvReader = new MkvReader(nativePointer);
    }
    return mkvReader;
  }

  public abstract int length(long[] total, long[] available);
  public abstract int read(long position, long length, byte[][] buffer);

  protected IMkvReader() {
    super();
  }

  protected IMkvReader(long nativePointer) {
    super(nativePointer);
  }

  private static native int getClassType(long jMkvReader);
}
