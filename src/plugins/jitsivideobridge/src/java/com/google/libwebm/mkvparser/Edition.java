// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Edition extends Common {

  public Atom getAtom(int index) {
    long pointer = GetAtom(nativePointer, index);
    return new Atom(pointer);
  }

  public int getAtomCount() {
    return GetAtomCount(nativePointer);
  }

  protected Edition(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
  }

  private static native long GetAtom(long jEdition, int index);
  private static native int GetAtomCount(long jEdition);
}
