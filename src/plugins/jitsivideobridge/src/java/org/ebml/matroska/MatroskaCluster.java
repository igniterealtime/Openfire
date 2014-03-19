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

import org.ebml.*;
import org.ebml.util.*;

/**
 * Summary description for MatroskaCluster.
 */
public class MatroskaCluster extends MasterElement
{
  static public int NO_LACING = 0;
  static public int XIPH_LACING = 1;
  static public int EBML_LACING = 2;

  protected int [] laceMode = null;
  protected TLinkedList frames = new TLinkedList();
  protected long clusterTimecode = 0;

  public MatroskaCluster(byte[] type) 
  {
    super(type);
  }

  /**
   * Set the current lacing mode.
   * 
   * @param trackNo Track Number for the track to enable lacing for. 1-based index
   * @param laceMode The lacing moe to use. See NO_LACING, XIPH_LACING, and EBML_LACING.
   */
  void setLaceMode(short trackNo, int laceMode)
  {
    if (this.laceMode == null) 
    {
      this.laceMode = new int[trackNo];
    }
    if (this.laceMode.length < trackNo) 
    {
      int [] oldLaceMode = this.laceMode;
      this.laceMode = new int[trackNo];;
      ArrayCopy.arraycopy(this.laceMode, 0, oldLaceMode, 0, oldLaceMode.length);
    }
    this.laceMode[trackNo-1] = laceMode;
  }

  /**
   * Get the current lacing mode.
   * 
   * @param trackNo Track Number for the track to enable lacing for. 1-based index
   * @return -1 if the track no is invalid
   */
  int getLaceMode(short trackNo)
  {
    if (this.laceMode == null) 
    {
      return -1;
    }
    if (this.laceMode.length < trackNo) 
    {
      return -1;
    }
    
    return this.laceMode[trackNo-1];
  }

  public void AddFrame(MatroskaFileFrame frame) 
  {
    // Is this the earliest timecode?
    if (frame.Timecode < clusterTimecode) 
    {
      clusterTimecode = frame.Timecode;
    }
    frames.add(frame);
  }

  public void FlushFrames()
  {
    TLinkedList.IteratorImpl iter = frames.first();
    while (iter.hasNext()) 
    {
      //MatroskaFileFrame frame = (MatroskaFileFrame)
    	iter.next();
    }
    
  }
}
