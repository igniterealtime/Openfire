// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public abstract class IMkvWriter extends Common {

  public static IMkvWriter newMkvWriter(long nativePointer) {
    IMkvWriter mkvWriter = null;
    int type = getType(nativePointer);
    if (type == 1) {
      mkvWriter = new MkvWriter(nativePointer);
    }
    return mkvWriter;
  }

  public abstract void elementStartNotify(long elementId, long position);
  public abstract long position();
  public abstract int position(long position);
  public abstract boolean seekable();
  public abstract int write(byte[] buffer);

  protected IMkvWriter() {
    super();
  }

  protected IMkvWriter(long nativePointer) {
    super(nativePointer);
  }

  private static native int getType(long jMkvReader);
}
