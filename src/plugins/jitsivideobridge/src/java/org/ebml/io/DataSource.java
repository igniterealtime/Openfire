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

/*
 * DataSource.java
 *
 * Created on November 18, 2002, 4:08 PM
 */

/**
 * Defines the interface used for custom <code>DataSource</code>'s.  A
 * <code>DataSource</code> provides methods of reading bytes individually or
 * in arrays.  These basic functions must be defined in any <code>DataSource</code>
 * objects to be used with the <code>EBMLReader</code>.
 *
 * @author John Cannon
 * @author Jory Stone
 */

public interface DataSource extends DataSeekable {

  public byte readByte();

  public int read(byte[] buff);

  public int read(byte[] buff, int offset, int length);

  /**
   * Skip a number of bytes in the <code>DataSeekable</code>
   *
   * @param offset The number of bytes to skip
   * @return The number of bytes skipped
   */
  public long skip(long offset);
}
