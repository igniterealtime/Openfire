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
package org.ebml.io;

/**
 * Interface for seeking operations.
 * Not designed to be used alone but as a super interface for a more
 * specialied reading or writing interfaces.
 */
public interface DataSeekable
{
  /**
   * Returns the length
   *
   * @return <code>-1</code> if the length is unknown
   * @return <code> >0</code> length of the <code>DataSeekable</code>
   */
  public long length();

  public long getFilePointer();

  /**
   * Check if the <code>DataSeekable</code> object is seekable
   *
   * @return <code>true</code> if seeking is supported.
   * @return <code>false</code> if seeking is not supported.
   */
  public boolean isSeekable();

  /**
   * Seeks in the <code>DataSeekable</code>
   *
   * @param pos Absolute position to seek to
   * @return The new file position
   */
  public long seek(long pos);
}
