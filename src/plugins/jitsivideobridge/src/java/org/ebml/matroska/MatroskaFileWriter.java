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

import java.util.ArrayList;
import java.util.Date;

import org.ebml.BinaryElement;
import org.ebml.DateElement;
import org.ebml.FloatElement;
import org.ebml.MasterElement;
import org.ebml.StringElement;
import org.ebml.UnsignedIntegerElement;
import org.ebml.io.DataWriter;

/**
 * Summary description for MatroskaFileWriter.
 */
public class MatroskaFileWriter
{
  protected DataWriter ioDW;
  private MatroskaCluster clusterElem = null;
  private MasterElement segmentElem = null;
  private long clusterTimecode;
  protected MatroskaDocType doc = new MatroskaDocType();

  public long TimecodeScale = 1000000;
  public double Duration = 60.0;
  public Date SegmentDate = new Date();
  public ArrayList<MatroskaFileTrack> TrackList = new ArrayList<MatroskaFileTrack>();

	public MatroskaFileWriter(DataWriter outputDataWriter)
	{
		ioDW = outputDataWriter;
	}

  public void writeEBMLHeader()
  {
    MasterElement ebmlHeaderElem = (MasterElement)doc.createElement(MatroskaDocType.EBMLHeader_Id);

    UnsignedIntegerElement ebml1 = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.EBMLVersion_Id);
    ebml1.setValue(1);

    UnsignedIntegerElement ebml2 = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.EBMLReadVersion_Id);
    ebml2.setValue(1);

    UnsignedIntegerElement ebml3 = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.EBMLMaxIDLength_Id);
    ebml3.setValue(4);

    UnsignedIntegerElement ebml4 = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.EBMLMaxSizeLength_Id);
    ebml4.setValue(8);

    StringElement docTypeElem = (StringElement)doc.createElement(MatroskaDocType.DocType_Id);
    docTypeElem.setValue("webm");

    UnsignedIntegerElement docTypeVersionElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.DocTypeVersion_Id);
    docTypeVersionElem.setValue(2);

    UnsignedIntegerElement docTypeReadVersionElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.DocTypeReadVersion_Id);
    docTypeReadVersionElem.setValue(2);

    ebmlHeaderElem.addChildElement(ebml1);
    ebmlHeaderElem.addChildElement(ebml2);
    ebmlHeaderElem.addChildElement(ebml3);
    ebmlHeaderElem.addChildElement(ebml4);
    ebmlHeaderElem.addChildElement(docTypeElem);
    ebmlHeaderElem.addChildElement(docTypeVersionElem);
    ebmlHeaderElem.addChildElement(docTypeReadVersionElem);

    ebmlHeaderElem.writeElement(ioDW);
  }

  public void writeSegmentHeader()
  {

    segmentElem = (MasterElement)doc.createElement(MatroskaDocType.Segment_Id);

    MasterElement ebmlSeekHeadElem = (MasterElement)doc.createElement(MatroskaDocType.SeekHead_Id);

    MasterElement ebmlSeekEntryElem1 = (MasterElement)doc.createElement(MatroskaDocType.SeekEntry_Id);
    BinaryElement seekID1 = (BinaryElement)doc.createElement(MatroskaDocType.SeekID_Id);
    seekID1.setData(MatroskaDocType.Tracks_Id);
    UnsignedIntegerElement SeekPosition1 = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.SeekPosition_Id);
    SeekPosition1.setValue(0);
    ebmlSeekEntryElem1.addChildElement(seekID1);
    ebmlSeekEntryElem1.addChildElement(SeekPosition1);

    ebmlSeekHeadElem.addChildElement(ebmlSeekEntryElem1);

    segmentElem.addChildElement(ebmlSeekHeadElem);

  }

  public void writeSegmentInfo()
  {
    MasterElement segmentInfoElem = (MasterElement)doc.createElement(MatroskaDocType.SegmentInfo_Id);

    StringElement writingAppElem = (StringElement)doc.createElement(MatroskaDocType.WritingApp_Id);
    writingAppElem.setValue("Matroska File Writer v1.0");

    StringElement muxingAppElem = (StringElement)doc.createElement(MatroskaDocType.MuxingApp_Id);
    muxingAppElem.setValue("JEBML v1.0");

    DateElement dateElem = (DateElement)doc.createElement(MatroskaDocType.DateUTC_Id);
    dateElem.setDate(SegmentDate);

    //Add timecode scale
    UnsignedIntegerElement timecodescaleElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.TimecodeScale_Id);
    timecodescaleElem.setValue(TimecodeScale);

    FloatElement durationElem = (FloatElement)doc.createElement(MatroskaDocType.Duration_Id);
    //durationElem.setValue(Duration * 1000.0);
    durationElem.setValue(0);

    //segmentInfoElem.addChildElement(dateElem);
    segmentInfoElem.addChildElement(timecodescaleElem);
    segmentInfoElem.addChildElement(muxingAppElem);
    segmentInfoElem.addChildElement(writingAppElem);
    segmentInfoElem.addChildElement(durationElem);


    //segmentInfoElem.writeElement(ioDW);
    segmentElem.addChildElement(segmentInfoElem);
  }

  public void writeTracks()
  {
    MasterElement tracksElem = (MasterElement)doc.createElement(MatroskaDocType.Tracks_Id);

    for (int i = 0; i < TrackList.size(); i++)
    {
      MatroskaFileTrack track = (MatroskaFileTrack)TrackList.get(i);
      MasterElement trackEntryElem = (MasterElement)doc.createElement(MatroskaDocType.TrackEntry_Id);

      UnsignedIntegerElement trackNoElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.TrackNumber_Id);
      trackNoElem.setValue(track.TrackNo);

      UnsignedIntegerElement trackFlagLacingElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.TrackFlagLacing_Id);
      trackFlagLacingElem.setValue(0);

      UnsignedIntegerElement trackUIDElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.TrackUID_Id);
      trackUIDElem.setValue(track.TrackUID);

      UnsignedIntegerElement trackTypeElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.TrackType_Id);
      trackTypeElem.setValue(track.TrackType);

      StringElement trackNameElem = (StringElement)doc.createElement(MatroskaDocType.TrackName_Id);
      trackNameElem.setValue(track.Name);

      StringElement trackLangElem = (StringElement)doc.createElement(MatroskaDocType.TrackLanguage_Id);
      trackLangElem.setValue(track.Language);

      StringElement trackCodecIDElem = (StringElement)doc.createElement(MatroskaDocType.TrackCodecID_Id);
      trackCodecIDElem.setValue(track.CodecID);

      StringElement trackCodecName_IdElem = (StringElement)doc.createElement(MatroskaDocType.TrackCodecName_Id);
      trackCodecName_IdElem.setValue(track.CodecName);

      BinaryElement trackCodecPrivateElem = (BinaryElement)doc.createElement(MatroskaDocType.TrackCodecPrivate_Id);
      trackCodecPrivateElem.setData(track.CodecPrivate);

      UnsignedIntegerElement trackDefaultDurationElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.TrackDefaultDuration_Id);
      trackDefaultDurationElem.setValue(track.DefaultDuration);

      trackEntryElem.addChildElement(trackNoElem);
      trackEntryElem.addChildElement(trackUIDElem);
      trackEntryElem.addChildElement(trackFlagLacingElem);
      trackEntryElem.addChildElement(trackLangElem);
      trackEntryElem.addChildElement(trackCodecIDElem);
      trackEntryElem.addChildElement(trackCodecName_IdElem);
      trackEntryElem.addChildElement(trackTypeElem);
      //trackEntryElem.addChildElement(trackNameElem);
      //trackEntryElem.addChildElement(trackCodecPrivateElem);
     // trackEntryElem.addChildElement(trackDefaultDurationElem);

      // Now we add the audio/video dependant sub-elements
      if (track.TrackType == MatroskaDocType.track_video)
      {
        MasterElement trackVideoElem = (MasterElement)doc.createElement(MatroskaDocType.TrackVideo_Id);

        UnsignedIntegerElement trackVideoPixelWidthElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.PixelWidth_Id);
        trackVideoPixelWidthElem.setValue(track.Video_PixelWidth);

        UnsignedIntegerElement trackVideoPixelHeightElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.PixelHeight_Id);
        trackVideoPixelHeightElem.setValue(track.Video_PixelHeight);

        UnsignedIntegerElement trackVideoDisplayWidthElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.DisplayWidth_Id);
        trackVideoDisplayWidthElem.setValue(track.Video_DisplayWidth);

        UnsignedIntegerElement trackVideoDisplayHeightElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.DisplayHeight_Id);
        trackVideoDisplayHeightElem.setValue(track.Video_DisplayHeight);

        trackVideoElem.addChildElement(trackVideoPixelWidthElem);
        trackVideoElem.addChildElement(trackVideoPixelHeightElem);
        //trackVideoElem.addChildElement(trackVideoDisplayWidthElem);
        //trackVideoElem.addChildElement(trackVideoDisplayHeightElem);

        trackEntryElem.addChildElement(trackVideoElem);
      }
      else if (track.TrackType == MatroskaDocType.track_audio)
      {
        MasterElement trackAudioElem = (MasterElement)doc.createElement(MatroskaDocType.TrackVideo_Id);

        UnsignedIntegerElement trackAudioChannelsElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.Channels_Id);
        trackAudioChannelsElem.setValue(track.Audio_Channels);

        UnsignedIntegerElement trackAudioBitDepthElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.BitDepth_Id);
        trackAudioBitDepthElem.setValue(track.Audio_BitDepth);

        FloatElement trackAudioSamplingRateElem = (FloatElement)doc.createElement(MatroskaDocType.SamplingFrequency_Id);
        trackAudioSamplingRateElem.setValue(track.Audio_SamplingFrequency);

        FloatElement trackAudioOutputSamplingFrequencyElem = (FloatElement)doc.createElement(MatroskaDocType.OutputSamplingFrequency_Id);
        trackAudioOutputSamplingFrequencyElem.setValue(track.Audio_OutputSamplingFrequency);

        trackAudioElem.addChildElement(trackAudioChannelsElem);
        trackAudioElem.addChildElement(trackAudioBitDepthElem);
        trackAudioElem.addChildElement(trackAudioSamplingRateElem);
        trackAudioElem.addChildElement(trackAudioOutputSamplingFrequencyElem);

        trackEntryElem.addChildElement(trackAudioElem);
      }

      tracksElem.addChildElement(trackEntryElem);
    }

    segmentElem.addChildElement(tracksElem);
    segmentElem.writeElement(ioDW);
  }

  public void startCluster(long clusterTimecode)
  {
    clusterElem = (MatroskaCluster)doc.createElement(MatroskaDocType.Cluster_Id);

    UnsignedIntegerElement clusterTimecodeElem = (UnsignedIntegerElement)doc.createElement(MatroskaDocType.ClusterTimecode_Id);
    clusterTimecodeElem.setValue(clusterTimecode);
	clusterElem.addChildElement(clusterTimecodeElem);

	this.clusterTimecode = clusterTimecode;
  }

  public void endCluster()
  {
	if (clusterElem != null) clusterElem.writeElement(ioDW);
  }

  /**
   * Add a frame
   *
   * @param frame The frame to add
  */
  public void addFrame(MatroskaFileFrame frame)
  {
    MatroskaBlock simpleBlockElem = (MatroskaBlock)doc.createElement(MatroskaDocType.ClusterSimpleBlock_Id);
    simpleBlockElem.setFrameData(frame.TrackNo, frame.Timecode - clusterTimecode, frame.Data, frame.KeyFrame);
   	clusterElem.addChildElement(simpleBlockElem);
  }
}
