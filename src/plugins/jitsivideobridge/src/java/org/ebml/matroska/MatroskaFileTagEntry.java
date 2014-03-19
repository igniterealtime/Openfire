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

public class MatroskaFileTagEntry 
{
  public ArrayList<Long> TrackUID = new ArrayList<Long>();
  public ArrayList<Long> ChapterUID = new ArrayList<Long>();
  public ArrayList<Long> AttachmentUID = new ArrayList<Long>();
  public ArrayList<MatroskaFileSimpleTag> SimpleTags = new ArrayList<MatroskaFileSimpleTag>();

  public String toString() 
  {
    String s = new String();

    if (TrackUID.size() > 0) 
    {
      s += "\t\t" + "TrackUID: " + TrackUID.toArray().toString() + "\n";
    }
    if (ChapterUID.size() > 0) 
    {
      s += "\t\t" + "ChapterUID: " + ChapterUID.toArray().toString() + "\n";
    }
    if (AttachmentUID.size() > 0) 
    {
      s += "\t\t" + "AttachmentUID: " + AttachmentUID.toArray().toString() + "\n";
    }

    for (int t = 0; t < SimpleTags.size(); t++) 
    {
      s += ((MatroskaFileSimpleTag)SimpleTags.get(t)).toString(2);
    }

    return s;
  }
}
