// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

public class AudioTrack extends Track {

  public static long parse(Segment segment, Info info, long element_start, long element_size,
      AudioTrack[] audioTrack) {
    long[] jAudioTrack = {0};
    long result = Parse(segment.getNativePointer(), info.getNativePointer(), element_start,
        element_size, jAudioTrack);
    audioTrack[0] = new AudioTrack(jAudioTrack[0]);
    return result;
  }

  public long getBitDepth() {
    return GetBitDepth(nativePointer);
  }

  public long getChannels() {
    return GetChannels(nativePointer);
  }

  public double getSamplingRate() {
    return GetSamplingRate(nativePointer);
  }

  @Override
  public long seek(long time_ns, BlockEntry[] result) {
    long[] jResult = {0};
    long output = Seek(nativePointer, time_ns, jResult);
    result[0] = BlockEntry.newBlockEntry(jResult[0]);
    return output;
  }

  protected AudioTrack(long nativePointer) {
    super(nativePointer);
  }

  private static native long GetBitDepth(long jAudioTrack);
  private static native long GetChannels(long jAudioTrack);
  private static native double GetSamplingRate(long jAudioTrack);
  private static native long Parse(long jSegment, long jInfo, long element_start, long element_size,
      long[] jAudioTrack);
  private static native long Seek(long jAudioTrack, long time_ns, long[] jResult);
}
