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

import org.ebml.util.*;

/**
  * Matroska Frame, holds a Matroska frame timecode, duration, and data
  */
public class MatroskaFileFrame 
{
  /**
   * Matroska Frame Puller interface
   */
  public interface MatroskaFramePuller 
  {
    public void PushNewMatroskaFrame(MatroskaFileFrame frame);
  };

  /**
   * The track this frame belongs to
   */
  public int TrackNo;
  /**
   * A timecode, it should be in ms
   */
  public long Timecode;
  /**
   * The duration of this frame, it should also be in ms
   */
  public long Duration;
  /**
   * The first reference this frame has, set to 0 for no reference
   */
  public long Reference;
  /**
   * More references, can be null if there are no more references
   */
  public long [] References;
  /**
   * The frame data
   */
  public byte [] Data;
  public boolean KeyFrame;

/**
   * MatroskaFrame Default constructor
   */
  public MatroskaFileFrame() 
  {
    //System.out.println("new " + this);
  }

  /**
   * MatroskaFrame Copy constructor
   * @param copy MatroskaFrame to copy
   */
  public MatroskaFileFrame(MatroskaFileFrame copy) 
  {
    //System.out.println("MatroskaFrame copy " + this);
    this.TrackNo = copy.TrackNo;
    this.Timecode = copy.Timecode;
    this.Duration = copy.Duration;
    this.Reference = copy.Reference;
    this.KeyFrame = copy.KeyFrame;
    if (copy.References != null) 
    {
      this.References = new long[copy.References.length];
      ArrayCopy.arraycopy(copy.References, 0, this.References, 0, copy.References.length);
    }
    if (copy.Data != null) 
    {
      this.Data = new byte[copy.Data.length];
      ArrayCopy.arraycopy(copy.Data, 0, this.Data, 0, copy.Data.length);
    }
  }
  
  public boolean isKeyFrame() {
    return KeyFrame;
  }
}
