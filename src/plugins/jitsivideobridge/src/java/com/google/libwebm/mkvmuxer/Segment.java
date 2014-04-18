// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class Segment extends Common {

  public enum Mode {
    None,
    kLive,
    kFile
  };

  public static final long kDefaultMaxClusterDuration = 30000000000L;

  public Segment() {
    nativePointer = newSegment();
  }

  public long addAudioTrack(int sampleRate, int channels, int number) {
    return AddAudioTrack(nativePointer, sampleRate, channels, number);
  }

  public Chapter addChapter() {
    long pointer = AddChapter(nativePointer);
    return new Chapter(pointer);
  }

  public boolean addCuePoint(long timestamp, long track) {
    return AddCuePoint(nativePointer, timestamp, track);
  }

  public boolean addFrame(byte[] frame, long trackNumber, long timestampNs, boolean isKey) {
    return AddFrame(nativePointer, frame, frame.length, trackNumber, timestampNs, isKey);
  }

  public boolean addMetadata(byte[] frame, long trackNumber, long timestampNs, long durationNs) {
    return AddMetadata(nativePointer, frame, frame.length, trackNumber, timestampNs, durationNs);
  }

  public Track addTrack(int number) {
    long pointer = AddTrack(nativePointer, number);
    return Track.newTrack(pointer);
  }

  public long addVideoTrack(int width, int height, int number) {
    return AddVideoTrack(nativePointer, width, height, number);
  }

  public boolean chunking() {
    return chunking(nativePointer);
  }

  public long cuesTrack() {
    return getCuesTrack(nativePointer);
  }

  public boolean cuesTrack(long trackNumber) {
    return setCuesTrack(nativePointer, trackNumber);
  }

  public boolean finalizeSegment() {
    return Finalize(nativePointer);
  }

  public void forceNewClusterOnNextFrame() {
    ForceNewClusterOnNextFrame(nativePointer);
  }

  public Cues getCues() {
    long pointer = GetCues(nativePointer);
    return new Cues(pointer);
  }

  public SegmentInfo getSegmentInfo() {
    long pointer = GetSegmentInfo(nativePointer);
    return new SegmentInfo(pointer);
  }

  public Track getTrackByNumber(long trackNumber) {
    long pointer = GetTrackByNumber(nativePointer, trackNumber);
    return Track.newTrack(pointer);
  }

  public boolean init(IMkvWriter writer) {
    return Init(nativePointer, writer.getNativePointer());
  }

  public long maxClusterDuration() {
    return maxClusterDuration(nativePointer);
  }

  public long maxClusterSize() {
    return maxClusterSize(nativePointer);
  }

  public Mode mode() {
    int ordinal = mode(nativePointer);
    return Mode.values()[ordinal];
  }

  public boolean outputCues() {
    return outputCues(nativePointer);
  }

  public void outputCues(boolean outputCues) {
    OutputCues(nativePointer, outputCues);
  }

  public SegmentInfo segmentInfo() {
    long pointer = segmentInfo(nativePointer);
    return new SegmentInfo(pointer);
  }

  public void setMaxClusterDuration(long maxClusterDuration) {
    setMaxClusterDuration(nativePointer, maxClusterDuration);
  }

  public void setMaxClusterSize(long maxClusterSize) {
    setMaxClusterSize(nativePointer, maxClusterSize);
  }

  public void setMode(Mode mode) {
    setMode(nativePointer, mode.ordinal());
  }

  public boolean setChunking(boolean chunking, String filename) {
    return SetChunking(nativePointer, chunking, filename);
  }

  protected Segment(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteSegment(nativePointer);
  }

  private static native long AddAudioTrack(long jSegment, int sample_rate, int channels,
      int number);
  private static native long AddChapter(long jSegment);
  private static native boolean AddCuePoint(long jSegment, long timestamp, long track);
  private static native boolean AddFrame(long jSegment, byte[] jFrame, long length,
      long track_number, long timestamp_ns, boolean is_key);
  private static native boolean AddMetadata(long jSegment, byte[] jFrame, long length,
      long track_number, long timestamp_ns, long duration_ns);
  private static native long AddTrack(long jSegment, int number);
  private static native long AddVideoTrack(long jSegment, int width, int height, int number);
  private static native boolean chunking(long jSegment);
  private static native void deleteSegment(long jSegment);
  private static native boolean Finalize(long jSegment);
  private static native void ForceNewClusterOnNextFrame(long jSegment);
  private static native long GetCues(long jSegment);
  private static native long getCuesTrack(long jSegment);
  private static native long GetSegmentInfo(long jSegment);
  private static native long GetTrackByNumber(long jSegment, long track_number);
  private static native boolean Init(long jSegment, long jWriter);
  private static native long maxClusterDuration(long jSegment);
  private static native long maxClusterSize(long jSegment);
  private static native int mode(long jSegment);
  private static native long newSegment();
  private static native boolean outputCues(long jSegment);
  private static native void OutputCues(long jSegment, boolean output_cues);
  private static native long segmentInfo(long jSegment);
  private static native boolean setCuesTrack(long jSegment, long track_number);
  private static native void setMaxClusterDuration(long jSegment, long max_cluster_duration);
  private static native void setMaxClusterSize(long jSegment, long max_cluster_size);
  private static native void setMode(long jSegment, int mode);
  private static native boolean SetChunking(long jSegment, boolean chunking, String jFilename);
}
