// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class VoidElement extends Common {

  public VoidElement() {
    nativePointer = newVoidElement();
  }

  public long getElementSize() {
    return getElementSize(nativePointer);
  }

  public long getElementStart() {
    return getElementStart(nativePointer);
  }

  public void setElementSize(long elementSize) {
    setElementSize(nativePointer, elementSize);
  }

  public void setElementStart(long elementStart) {
    setElementStart(nativePointer, elementStart);
  }

  protected VoidElement(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteVoidElement(nativePointer);
  }

  private static native void deleteVoidElement(long jVoidElement);
  private static native long getElementSize(long jVoidElement);
  private static native long getElementStart(long jVoidElement);
  private static native long newVoidElement();
  private static native void setElementSize(long jVoidElement, long element_size);
  private static native void setElementStart(long jVoidElement, long element_start);
}
