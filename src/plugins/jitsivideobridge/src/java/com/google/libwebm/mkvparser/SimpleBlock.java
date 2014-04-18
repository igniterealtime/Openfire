// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

public class SimpleBlock extends BlockEntry {

  public SimpleBlock(Cluster cluster, long index, long start, long size) {
    nativePointer = newSimpleBlock(cluster.getNativePointer(), index, start, size);
  }

  @Override
  public Block getBlock() {
    long pointer = GetBlock(nativePointer);
    return new Block(pointer);
  }

  @Override
  public Kind getKind() {
    int ordinal = GetKind(nativePointer);
    return Kind.values()[ordinal];
  }

  public long Parse() {
    return Parse(nativePointer);
  }

  protected SimpleBlock(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteSimpleBlock(nativePointer);
  }

  private static native void deleteSimpleBlock(long jSimpleBlock);
  private static native long GetBlock(long jSimpleBlock);
  private static native int GetKind(long jSimpleBlock);
  private static native long newSimpleBlock(long jCluster, long index, long start, long size);
  private static native long Parse(long jSimpleBlock);
}
