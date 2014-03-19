/**
 * JEBML - Java library to read/write EBML/Matroska elements.
 * Copyright (C) 2004 Jory Stone <jebml@jory.info>
 * Based on Javatroska (C) 2002 John Cannon <spyder@matroska.org>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.ebml.matroska;

import java.util.*;
import org.ebml.*;
import org.ebml.io.*;
import org.ebml.util.*;


public class MatroskaFile {
  /**
   * Number of Clusters to search before assuming that a track has ended
   */
  public static int CLUSTER_TRACK_SEARCH_COUNT = 4;

  protected DataSource ioDS;
  protected EBMLReader reader;
  protected Element level0 = null;
  protected String SegmentTitle;
  protected Date SegmentDate;
  protected String MuxingApp;
  protected String WritingApp;
  protected long TimecodeScale = 1000000;
  protected double Duration;
  public ArrayList<MatroskaFileTrack> TrackList = new ArrayList<MatroskaFileTrack>();
  protected ArrayList<MatroskaFileTagEntry> TagList = new ArrayList<MatroskaFileTagEntry>();
  protected TLinkedList FrameQueue = new TLinkedList();
	protected boolean ScanFirstCluster = true;
  public long ClusterTimecode = 0;

  /**
   * Primary Constructor for Matroska File class.
   *
   * @param inputDataSource DataSource to read the Matroska file from
   */
  public MatroskaFile(DataSource inputDataSource) {
    ioDS = inputDataSource;
    reader = new EBMLReader(ioDS, MatroskaDocType.obj);
  }

  /**
   * Read / Parse the Matroska file.
   * Call this before any other method.
   * @throws RuntimeException On various errors
   */
  public void readFile() {
    Element level1 = null;
    Element level2 = null;
//    Element level3 = null;
//    Element level4 = null;

    level0 = reader.readNextElement();
    if (level0 == null) {
      throw new java.lang.RuntimeException("Error: Unable to scan for EBML elements");
    }

    if (level0.equals(MatroskaDocType.EBMLHeader_Id)) {
      level1 = ((MasterElement)level0).readNextChild(reader);

      while (level1 != null) {
        level1.readData(ioDS);
        if (level1.equals(MatroskaDocType.DocType_Id)) {
          String DocType = ((StringElement)level1).getValue();
          if (DocType.compareTo("matroska") != 0 && DocType.compareTo("webm") != 0) {
            throw new java.lang.RuntimeException("Error: DocType is not matroska, \"" + ((StringElement)level1).getValue() + "\"");
          }
        }
        level1 = ((MasterElement)level0).readNextChild(reader);
      }
    } else {
      throw new java.lang.RuntimeException("Error: EBML Header not the first element in the file");
    }

    level0 = reader.readNextElement();
    if (level0.equals(MatroskaDocType.Segment_Id)) {
      level1 = ((MasterElement)level0).readNextChild(reader);

      while (level1 != null) {
        if (level1.equals(MatroskaDocType.SegmentInfo_Id)) {
          _parseSegmentInfo(level1, level2);

        } else if (level1.equals(MatroskaDocType.Tracks_Id)) {
          _parseTracks(level1, level2);

        } else if (level1.equals(MatroskaDocType.Cluster_Id)) {
          if (ScanFirstCluster)
          {
            _parseNextCluster(level1);
          }
          // Break out of this loop, we should only parse the first cluster
          break;

        } else if (level1.equals(MatroskaDocType.Tags_Id)) {
          _parseTags(level1, level2);

        }

        level1.skipData(ioDS);
        level1 = ((MasterElement)level0).readNextChild(reader);
      }
    } else {
      throw new java.lang.RuntimeException("Error: Segment not the second element in the file");
    }
  }
  /**
   * Get the Next MatroskaFileFrame
   *
   * @return The next MatroskaFileFrame in the queue, or null if the file has ended
   */
  public MatroskaFileFrame getNextFrame() {
    if (FrameQueue.isEmpty()) {
      _fillFrameQueue();
    }

    // If FrameQueue is still empty, must be the end of the file
    if (FrameQueue.isEmpty()) {
      return null;
    }
    return (MatroskaFileFrame)FrameQueue.removeFirst();
  }
  /**
   * Get the Next MatroskaFileFrame, limited by TrackNo
   *
   * @param TrackNo The track number to only get MatroskaFileFrame(s) from
   * @return The next MatroskaFileFrame in the queue, or null if there are no more frames for the TrackNo track
   */
  public MatroskaFileFrame getNextFrame(int TrackNo) {
    if (FrameQueue.isEmpty()) {
      _fillFrameQueue();
    }

    // If FrameQueue is still empty, must be the end of the file
    if (FrameQueue.isEmpty()) {
      return null;
    }
    int tryCount = 0;
    MatroskaFileFrame frame = null;
    try {
      TLinkedList.IteratorImpl iter = FrameQueue.first();
      while (frame == null) {
        if (iter.hasNext()) {
          frame = (MatroskaFileFrame)iter.next();
          if (frame.TrackNo == TrackNo) {
            synchronized (FrameQueue) {
              iter.remove();
            }
            return frame;
          }
          frame = null;
        } else {
          _fillFrameQueue();
          // Update Iterator
          int index = iter.nextIndex();
          iter = FrameQueue.listIterator(index);
          if (++tryCount > CLUSTER_TRACK_SEARCH_COUNT) {
            // If we have not found any frames belonging to a track in 4 clusters
            // there is a good chance that the track is over
            return null;
          }
        }
      }
    } catch (RuntimeException ex) {
      ex.printStackTrace();
      return null;
    }

    return frame;
  }

  public boolean isSeekable() {
    return this.ioDS.isSeekable();
  }

  /**
   * Seek to the requested timecode, rescaning clusters and/or discarding frames
   * until we reach the nearest possible timecode, rounded down.
   *
   * <p>
   * For example<br>
   * Say we have a file with 10 frames
   * <table><tr><th>Frame No</th><th>Timecode</th></tr>
   * <tr><td>Frame 1</td> <td>0ms</td></tr>
   * <tr><td>Frame 2</td> <td>50ms</td></tr>
   * <tr><td>Frame 3</td> <td>100ms</td></tr>
   * <tr><td>Frame 4</td> <td>150ms</td></tr>
   * <tr><td>Frame 5</td> <td>200ms</td></tr>
   * <tr><td>Frame 6</td> <td>250ms</td></tr>
   * <tr><td>Frame 7</td> <td>300ms</td></tr>
   * <tr><td>Frame 8</td> <td>350ms</td></tr>
   * <tr><td>Frame 9</td> <td>400ms</td></tr>
   * <tr><td>Frame 10</td> <td>450ms</td></tr>
   * </table>
   * We are requested to seek to 333ms, so we discard frames until we hit an
   * timecode larger than the requested. We would seek to Frame 7 at 300ms.
   * </p>
   *
   * @param timecode Timecode to seek to in millseconds
   * @return Actual timecode we seeked to
   */
  public long seek(long timecode) {
    return 0;
  }

  private void _fillFrameQueue() {
    if (level0 == null)
      throw new java.lang.IllegalStateException("Call readFile() before reading frames");

    synchronized (level0) {
      Element level1 = ((MasterElement)level0).readNextChild(reader);
      while (level1 != null) {
        if (level1.equals(MatroskaDocType.Cluster_Id)) {
          _parseNextCluster(level1);
        }

        level1.skipData(ioDS);
        level1 = ((MasterElement)level0).readNextChild(reader);
      }
    }
  }

  private void _parseNextCluster(Element level1) {
    Element level2 = null;
    Element level3 = null;
    level2 = ((MasterElement)level1).readNextChild(reader);

    while (level2 != null) {
      if (level2.equals(MatroskaDocType.ClusterTimecode_Id)) {
        level2.readData(ioDS);
        ClusterTimecode = ((UnsignedIntegerElement)level2).getValue();

      }else if(level2.equals(MatroskaDocType.ClusterSimpleBlock_Id)) {
    	  MatroskaBlock block = null;
    	  long BlockDuration = 0;
    	  long BlockReference = 0;
    	  block = (MatroskaBlock)level2;
    	  block.readData(ioDS);
    	  block.parseBlock();
    	  MatroskaFileFrame frame = new MatroskaFileFrame();
    	  frame.TrackNo = block.getTrackNo();
    	  frame.Timecode = block.getAdjustedBlockTimecode(ClusterTimecode, this.TimecodeScale);
    	  frame.Duration = BlockDuration;
    	  frame.Reference = BlockReference;
    	  frame.Data = block.getFrame(0);
    	  frame.KeyFrame = block.isKeyFrame();
    	  synchronized (FrameQueue) {
    		  FrameQueue.addLast(new MatroskaFileFrame(frame));
    	  }

    	  if (block.getFrameCount() > 1) {
    		  for (int f = 1; f < block.getFrameCount(); f++) {
    			  frame.Data = block.getFrame(f);
    			  synchronized (FrameQueue) {
    				  FrameQueue.addLast(new MatroskaFileFrame(frame));
    			  }
    		  }
    	  }
    	  level2.skipData(ioDS);

      } else if (level2.equals(MatroskaDocType.ClusterBlockGroup_Id)) {
        MatroskaBlock block = null;
        long BlockDuration = 0;
        long BlockReference = 0;
        level3 = ((MasterElement)level2).readNextChild(reader);

        while (level3 != null) {
          if (level3.equals(MatroskaDocType.ClusterBlock_Id)) {
            block = (MatroskaBlock)level3;
            block.readData(ioDS);
            block.parseBlock();

          } else if (level3.equals(MatroskaDocType.ClusterBlockDuration_Id)) {
            level3.readData(ioDS);
            BlockDuration = ((UnsignedIntegerElement)level3).getValue();

          } else if (level3.equals(MatroskaDocType.ClusterReferenceBlock_Id)) {
            level3.readData(ioDS);
            BlockReference = ((SignedIntegerElement)level3).getValue();
          }

          level3.skipData(ioDS);
          level3 = ((MasterElement)level2).readNextChild(reader);
        }

        if (block == null)
          throw new java.lang.NullPointerException("BlockGroup element with no child Block!");

        MatroskaFileFrame frame = new MatroskaFileFrame();
        frame.TrackNo = block.getTrackNo();
        frame.Timecode = block.getAdjustedBlockTimecode(ClusterTimecode, this.TimecodeScale);
        frame.Duration = BlockDuration;
        frame.Reference = BlockReference;
        frame.Data = block.getFrame(0);
        synchronized (FrameQueue) {
          FrameQueue.addLast(new MatroskaFileFrame(frame));
        }

        if (block.getFrameCount() > 1) {
          for (int f = 1; f < block.getFrameCount(); f++) {
            frame.Data = block.getFrame(f);
            /*if (badMP3Headers()) {
              throw new RuntimeException("Bad Data!");
            }*/
            synchronized (FrameQueue) {
              FrameQueue.addLast(new MatroskaFileFrame(frame));
            }
            /*if (badMP3Headers()) {
              throw new RuntimeException("Bad Data!");
            }*/
          }
        }
      }

      level2.skipData(ioDS);
      level2 = ((MasterElement)level1).readNextChild(reader);
    }
  }

  protected boolean badMP3Headers() {
    TLinkedList.IteratorImpl iter = FrameQueue.listIterator();
    while (iter.hasNext()) {
      MatroskaFileFrame frame = (MatroskaFileFrame)iter.next();
      if (frame.TrackNo == 2
          && frame.Data[3] != 0x54)
      {
        throw new RuntimeException("Bad MP3 Header! Index: " + iter.nextIndex());
      }
    }
    return false;
  }
  private void _parseSegmentInfo(Element level1, Element level2) {
    level2 = ((MasterElement)level1).readNextChild(reader);

    while (level2 != null) {
      if (level2.equals(MatroskaDocType.Title_Id)) {
        level2.readData(ioDS);
        SegmentTitle = ((StringElement)level2).getValue();

      } else if (level2.equals(MatroskaDocType.DateUTC_Id)) {
          level2.readData(ioDS);
          SegmentDate = ((DateElement)level2).getDate();

      } else if (level2.equals(MatroskaDocType.MuxingApp_Id)) {
        level2.readData(ioDS);
        MuxingApp = ((StringElement)level2).getValue();

      } else if (level2.equals(MatroskaDocType.WritingApp_Id)) {
        level2.readData(ioDS);
        WritingApp = ((StringElement)level2).getValue();

      } else if (level2.equals(MatroskaDocType.Duration_Id)) {
        level2.readData(ioDS);
        Duration = ((FloatElement)level2).getValue();

      } else if (level2.equals(MatroskaDocType.TimecodeScale_Id)) {
        level2.readData(ioDS);
        TimecodeScale = ((UnsignedIntegerElement)level2).getValue();
      }

      level2.skipData(ioDS);
      level2 = ((MasterElement)level1).readNextChild(reader);
    }
  }

  private void _parseTracks(Element level1, Element level2) {
    Element level3 = null;
    Element level4 = null;
    level2 = ((MasterElement)level1).readNextChild(reader);

    while (level2 != null) {
      if (level2.equals(MatroskaDocType.TrackEntry_Id)) {
        MatroskaFileTrack track = new MatroskaFileTrack();
        level3 = ((MasterElement)level2).readNextChild(reader);

        while (level3 != null) {
          if (level3.equals(MatroskaDocType.TrackNumber_Id)) {
            level3.readData(ioDS);
            track.TrackNo = (short)((UnsignedIntegerElement)level3).getValue();

          } else if (level3.equals(MatroskaDocType.TrackUID_Id)) {
            level3.readData(ioDS);
            track.TrackUID = ((UnsignedIntegerElement)level3).getValue();

          } else if (level3.equals(MatroskaDocType.TrackType_Id)) {
            level3.readData(ioDS);
            track.TrackType = (byte)((UnsignedIntegerElement)level3).getValue();

          } else if (level3.equals(MatroskaDocType.TrackDefaultDuration_Id)) {
            level3.readData(ioDS);
            track.DefaultDuration = ((UnsignedIntegerElement)level3).getValue();

          } else if (level3.equals(MatroskaDocType.TrackName_Id)) {
            level3.readData(ioDS);
            track.Name = ((StringElement)level3).getValue();

          } else if (level3.equals(MatroskaDocType.TrackLanguage_Id)) {
            level3.readData(ioDS);
            track.Language = ((StringElement)level3).getValue();

          } else if (level3.equals(MatroskaDocType.TrackCodecID_Id)) {
            level3.readData(ioDS);
            track.CodecID = ((StringElement)level3).getValue();

          } else if (level3.equals(MatroskaDocType.TrackCodecPrivate_Id)) {
            level3.readData(ioDS);
            track.CodecPrivate = ((BinaryElement)level3).getData();

          } else if (level3.equals(MatroskaDocType.TrackVideo_Id)) {
            level4 = ((MasterElement)level3).readNextChild(reader);

            while (level4 != null) {
              if (level4.equals(MatroskaDocType.PixelWidth_Id)) {
                level4.readData(ioDS);
                track.Video_PixelWidth = (short)((UnsignedIntegerElement)level4).getValue();

              } else if (level4.equals(MatroskaDocType.PixelHeight_Id)) {
                level4.readData(ioDS);
                track.Video_PixelHeight = (short)((UnsignedIntegerElement)level4).getValue();

              } else if (level4.equals(MatroskaDocType.DisplayWidth_Id)) {
                level4.readData(ioDS);
                track.Video_DisplayWidth = (short)((UnsignedIntegerElement)level4).getValue();

              } else if (level4.equals(MatroskaDocType.DisplayHeight_Id)) {
                level4.readData(ioDS);
                track.Video_DisplayHeight = (short)((UnsignedIntegerElement)level4).getValue();
              }

              level4.skipData(ioDS);
              level4 = ((MasterElement)level3).readNextChild(reader);
            }

          } else if (level3.equals(MatroskaDocType.TrackAudio_Id)) {
            level4 = ((MasterElement)level3).readNextChild(reader);

            while (level4 != null) {
              if (level4.equals(MatroskaDocType.SamplingFrequency_Id)) {
                level4.readData(ioDS);
                track.Audio_SamplingFrequency = (float)((FloatElement)level4).getValue();

              } else if (level4.equals(MatroskaDocType.OutputSamplingFrequency_Id)) {
                level4.readData(ioDS);
                track.Audio_OutputSamplingFrequency = (float)((FloatElement)level4).getValue();

              } else if (level4.equals(MatroskaDocType.Channels_Id)) {
                level4.readData(ioDS);
                track.Audio_Channels = (short)((UnsignedIntegerElement)level4).getValue();

              } else if (level4.equals(MatroskaDocType.BitDepth_Id)) {
                level4.readData(ioDS);
                track.Audio_BitDepth = (byte)((UnsignedIntegerElement)level4).getValue();
              }

              level4.skipData(ioDS);
              level4 = ((MasterElement)level3).readNextChild(reader);
            }

          }
          level3.skipData(ioDS);
          level3 = ((MasterElement)level2).readNextChild(reader);
        }
        TrackList.add(track);
      }
      level2.skipData(ioDS);
      level2 = ((MasterElement)level1).readNextChild(reader);

    }
  }
  private void _parseTags(Element level1, Element level2) {
    Element level3 = null;
    Element level4 = null;
    level2 = ((MasterElement)level1).readNextChild(reader);

    while (level2 != null) {
      if (level2.equals(MatroskaDocType.Tag_Id)) {
        MatroskaFileTagEntry tag = new MatroskaFileTagEntry();
        level3 = ((MasterElement)level2).readNextChild(reader);

        while (level3 != null) {
          if (level3.equals(MatroskaDocType.TagTargets_Id)) {
            level4 = ((MasterElement)level3).readNextChild(reader);

            while (level4 != null) {
              if (level4.equals(MatroskaDocType.TagTargetTrackUID_Id)) {
                level4.readData(ioDS);
                tag.TrackUID.add(new Long(((UnsignedIntegerElement)level4).getValue()));

              } else if (level4.equals(MatroskaDocType.TagTargetChapterUID_Id)) {
                level4.readData(ioDS);
                tag.ChapterUID.add(new Long(((UnsignedIntegerElement)level4).getValue()));

              } else if (level4.equals(MatroskaDocType.TagTargetAttachmentUID_Id)) {
                level4.readData(ioDS);
                tag.AttachmentUID.add(new Long(((UnsignedIntegerElement)level4).getValue()));
              }

              level4.skipData(ioDS);
              level4 = ((MasterElement)level3).readNextChild(reader);
            }

          } else if (level3.equals(MatroskaDocType.TagSimpleTag_Id)) {
            tag.SimpleTags.add(_parseTagsSimpleTag(level3, level4));
          }
          level3.skipData(ioDS);
          level3 = ((MasterElement)level2).readNextChild(reader);
        }
        TagList.add(tag);
      }

      level2.skipData(ioDS);
      level2 = ((MasterElement)level1).readNextChild(reader);
    }
  }

  private MatroskaFileSimpleTag _parseTagsSimpleTag(Element level3, Element level4) {
    MatroskaFileSimpleTag SimpleTag = new MatroskaFileSimpleTag();
    level4 = ((MasterElement)level3).readNextChild(reader);

    while (level4 != null) {
      if (level4.equals(MatroskaDocType.TagSimpleTagName_Id)) {
        level4.readData(ioDS);
        SimpleTag.Name = ((StringElement)level4).getValue();

      } else if (level4.equals(MatroskaDocType.TagSimpleTagString_Id)) {
        level4.readData(ioDS);
        SimpleTag.Value = ((StringElement)level4).getValue();

      } else if (level4.equals(MatroskaDocType.TagSimpleTag_Id)) {
        SimpleTag.Children.add(_parseTagsSimpleTag(level3, level4));
      }

      level4.skipData(ioDS);
      level4 = ((MasterElement)level3).readNextChild(reader);
    }

    return SimpleTag;
  }

  /**
   * Get a String report for the Matroska file.
   * Call readFile() before this method, else the report will be empty.
   *
   * @return String Report
   */
  public String getReport() {
    java.io.StringWriter s = new java.io.StringWriter();
    int t;

    s.write("MatroskaFile report\n");

    s.write("Infomation Segment \n");
    s.write("\tSegment Title: " + SegmentTitle + "\n");
    s.write("\tSegment Date: " + SegmentDate + "\n");
    s.write("\tMuxing App : " + MuxingApp + "\n");
    s.write("\tWriting App : " + WritingApp + "\n");
    s.write("\tDuration : " + Duration/1000 + "sec \n");
    s.write("\tTimecodeScale : " + TimecodeScale + "\n");

    s.write("Track Count: " + TrackList.size() + "\n");
    for (t = 0; t < TrackList.size(); t++) {
      s.write("\tTrack " + t + "\n");
      s.write(TrackList.get(t).toString());
    }

    s.write("Tag Count: " + TagList.size() + "\n");
    for (t = 0; t < TagList.size(); t++) {
      s.write("\tTag Entry \n");
      s.write(TagList.get(t).toString());
    }

    s.write("End report\n");

    return s.getBuffer().toString();
  }
  public String getWritingApp() {
    return WritingApp;
  }
  /**
   * Returns an array of the tracks.
   * If there are no MatroskaFileTracks to return the returned array
   * will have a size of 0.
   *
   * @return Array of MatroskaFileTrack's
   */
  public MatroskaFileTrack [] getTrackList() {
    if (TrackList.size() > 0) {
      MatroskaFileTrack [] tracks = new MatroskaFileTrack[TrackList.size()];
      for (int t = 0; t < TrackList.size(); t++) {
        tracks[t] = (MatroskaFileTrack)TrackList.get(t);
      }
      return tracks;
    } else {
     return new MatroskaFileTrack[0];
    }
  }
  /**
   * <p>
   * This differs from the getTrackList method in that this method scans
   * each track and returns the one that has the same track number as TrackNo.
   * </p>
   *
   * <p>Note: TrackNo != track index</p>
   *
   * @param TrackNo The actual track number of the MatroskaFileTrack you would like to get
   * @return null if no MatroskaFileTrack is found with the requested TrackNo
   */
  public MatroskaFileTrack getTrack(int TrackNo) {
    for (int t = 0; t < TrackList.size(); t++) {
      MatroskaFileTrack track = (MatroskaFileTrack)TrackList.get(t);
      if (track.TrackNo == TrackNo)
        return track;
    }
    return null;
  }
  /**
   * Get the timecode scale for this MatroskaFile.
   * In Matroska the timecodes are stored scaled by this value.
   * However any MatroskaFileFrame you get through the methods of this class
   * will already have the timecodes correctly scaled to millseconds.
   *
   * @return TimecodeScale
   */
  public long getTimecodeScale() {
    return TimecodeScale;
  }
  public String getSegmentTitle() {
    return SegmentTitle;
  }
  public String getMuxingApp() {
    return MuxingApp;
  }
  /**
   * Get the duration for this MatroskaFile.
   * This is the duration value stored in the segment info.
   * Which may or may not be the exact length of all, some, or one of the tracks.
   *
   * @return Duration in seconds
   */
  public double getDuration() {
    return Duration;
  }

  /**
   * Sets if the readFile() method should scan the first cluster for infomation.
   * Set to false for faster parsing.
   */
  public void setScanFirstCluster(boolean scanFirstCluster)
  {
    ScanFirstCluster = scanFirstCluster;
  }

  /**
   * Gets if the readFile() method should scan the first cluster for infomation.
   * When set to false parsing is slightly faster.
   */
  public boolean getScanFirstCluster()
  {
    return ScanFirstCluster;
  }
}
