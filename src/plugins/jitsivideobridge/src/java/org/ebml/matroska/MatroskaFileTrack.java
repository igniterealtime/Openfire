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

/**
  * Matroska Track Class
  */
public class MatroskaFileTrack 
{
  public short TrackNo;
  public long TrackUID;
  public byte TrackType;
  public long DefaultDuration;
  public String Name;
  public String Language;
  public String CodecID;
  public byte [] CodecPrivate;

  public short Video_PixelWidth;
  public short Video_PixelHeight;
  public short Video_DisplayWidth;
  public short Video_DisplayHeight;
  public float Audio_SamplingFrequency;
  public float Audio_OutputSamplingFrequency;
  public short Audio_Channels;
  public byte Audio_BitDepth;

  /**
   * Converts the Track to String form
   * @return String form of MatroskaFileTrack data
   */
  public String toString() 
  {
    String s = new String();

    s += "\t\t" + "TrackNo: " + TrackNo + "\n";
    s += "\t\t" + "TrackUID: " + TrackUID + "\n";
    s += "\t\t" + "TrackType: " + MatroskaDocType.TrackTypeToString(TrackType) + "\n";
    s += "\t\t" + "DefaultDuration: " + DefaultDuration + "\n";
    s += "\t\t" + "Name: " + Name + "\n";
    s += "\t\t" + "Language: " + Language + "\n";
    s += "\t\t" + "CodecID: " + CodecID + "\n";
    if (CodecPrivate != null)
      s += "\t\t" + "CodecPrivate: " + CodecPrivate.length + " byte(s)" + "\n";

    if (TrackType == MatroskaDocType.track_video) 
    {
      s += "\t\t" + "PixelWidth: " + Video_PixelWidth + "\n";
      s += "\t\t" + "PixelHeight: " + Video_PixelHeight + "\n";
      s += "\t\t" + "DisplayWidth: " + Video_DisplayWidth + "\n";
      s += "\t\t" + "DisplayHeight: " + Video_DisplayHeight + "\n";
    }

    if (TrackType == MatroskaDocType.track_audio) 
    {
      s += "\t\t" + "SamplingFrequency: " + Audio_SamplingFrequency + "\n";
      if (Audio_OutputSamplingFrequency != 0)
        s += "\t\t" + "OutputSamplingFrequency: " + Audio_OutputSamplingFrequency + "\n";
      s += "\t\t" + "Channels: " + Audio_Channels + "\n";
      if (Audio_BitDepth != 0)
        s += "\t\t" + "BitDepth: " + Audio_BitDepth + "\n";
    }

    return s;
  }
}
