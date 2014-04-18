// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public abstract class BlockEntry extends Common {

  public enum Kind {
    kBlockEOS,
    kBlockSimple,
    kBlockGroup
  };

  public static BlockEntry newBlockEntry(long nativePointer) {
    BlockEntry blockEntry = null;
    int type = getClassType(nativePointer);
    if (type == 1) {
      blockEntry = new BlockGroup(nativePointer);
    } else if (type == 2) {
      blockEntry = new SimpleBlock(nativePointer);
    }
    return blockEntry;
  }

  public boolean eos() {
    return EOS(nativePointer);
  }

  public abstract Block getBlock();

  public Cluster getCluster() {
    long pointer = GetCluster(nativePointer);
    return new Cluster(pointer);
  }

  public long getIndex() {
    return GetIndex(nativePointer);
  }

  public abstract Kind getKind();

  protected BlockEntry() {
    super();
  }

  protected BlockEntry(long nativePointer) {
    super(nativePointer);
  }

  private static native boolean EOS(long jBlockEntry);
  private static native int getClassType(long jBlockEntry);
  private static native long GetCluster(long jBlockEntry);
  private static native long GetIndex(long jBlockEntry);
}
