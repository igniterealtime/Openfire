// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

public class BlockGroup extends BlockEntry {

  public BlockGroup(Cluster cluster, long index, long blockStart, long blockSize, long previous,
      long next, long duration) {
    nativePointer = newBlockGroup(cluster.getNativePointer(), index, blockStart, blockSize,
        previous, next, duration);
  }

  @Override
  public Block getBlock() {
    long pointer = GetBlock(nativePointer);
    return new Block(pointer);
  }

  public long getDurationTimeCode() {
    return GetDurationTimeCode(nativePointer);
  }

  @Override
  public Kind getKind() {
    int ordinal = GetKind(nativePointer);
    return Kind.values()[ordinal];
  }

  public long getNextTimeCode() {
    return GetNextTimeCode(nativePointer);
  }

  public long getPrevTimeCode() {
    return GetPrevTimeCode(nativePointer);
  }

  public long Parse() {
    return Parse(nativePointer);
  }

  protected BlockGroup(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteBlockGroup(nativePointer);
  }

  private static native void deleteBlockGroup(long jBlockGroup);
  private static native long GetBlock(long jBlockGroup);
  private static native long GetDurationTimeCode(long jBlockGroup);
  private static native int GetKind(long jBlockGroup);
  private static native long GetNextTimeCode(long jBlockGroup);
  private static native long GetPrevTimeCode(long jBlockGroup);
  private static native long newBlockGroup(long jCluster, long index, long blockStart,
      long blockSize, long previous, long next, long duration);
  private static native long Parse(long jBlockGroup);
}
