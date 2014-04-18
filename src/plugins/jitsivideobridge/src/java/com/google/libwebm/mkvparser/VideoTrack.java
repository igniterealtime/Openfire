// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

public class VideoTrack extends Track {

  public static long parse(Segment segment, Info info, long element_start, long element_size,
      VideoTrack[] videoTrack) {
    long[] jVideoTrack = {0};
    long result = Parse(segment.getNativePointer(), info.getNativePointer(), element_start,
        element_size, jVideoTrack);
    videoTrack[0] = new VideoTrack(jVideoTrack[0]);
    return result;
  }

  public double getFrameRate() {
    return GetFrameRate(nativePointer);
  }

  public long getHeight() {
    return GetHeight(nativePointer);
  }

  public long getWidth() {
    return GetWidth(nativePointer);
  }

  @Override
  public long seek(long time_ns, BlockEntry[] result) {
    long[] jResult = {0};
    long output = Seek(nativePointer, time_ns, jResult);
    result[0] = BlockEntry.newBlockEntry(jResult[0]);
    return output;
  }

  @Override
  public boolean vetEntry(BlockEntry blockEntry) {
    return VetEntry(nativePointer, blockEntry.getNativePointer());
  }

  protected VideoTrack(long nativePointer) {
    super(nativePointer);
  }

  private static native double GetFrameRate(long jVideoTrack);
  private static native long GetHeight(long jVideoTrack);
  private static native long GetWidth(long jVideoTrack);
  private static native long Parse(long jSegment, long jInfo, long element_start, long element_size,
      long[] jVideoTrack);
  private static native long Seek(long jVideoTrack, long time_ns, long[] jResult);
  private static native boolean VetEntry(long jVideoTrack, long jBlockEntry);
}
