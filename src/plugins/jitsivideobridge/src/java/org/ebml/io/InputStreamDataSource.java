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

import java.io.*;

/*
 * InputStreamDataSource
 *
 * Created on November 19, 2002, 9:35 PM
 *
 * @author  John Cannon
 */
public class InputStreamDataSource
    implements DataSource {
  protected InputStream in = null;
  protected long pos = 0;
  protected byte[] buffer = new byte[1];

  public InputStream getInputStream() {
    return in;
  }
  /** Creates a new instance of InputStreamDataSource */
  public InputStreamDataSource(InputStream in) {
    this.in = in;
  }

  public byte readByte() {
    try {
      int l = in.read(buffer);
      pos += l;
      return buffer[0];
    }
    catch (IOException e) {
      e.printStackTrace();
      return -1;
    }
  }

  public int read(byte[] buff) {
    try {
      int l = in.read(buff);
      pos += l;
      return l;
    }
    catch (IOException e) {
      e.printStackTrace();
      return -1;
    }
  }

  public int read(byte[] buff, int offset, int length) {
    try {
      int l = in.read(buff, offset, length);
      pos += l;
      return l;
    }
    catch (IOException e) {
      e.printStackTrace();
      return -1;
    }
  }

  public long skip(long offset) {
    try {
      long l = in.skip(offset);
      pos += l;
      return l;
    }
    catch (IOException e) {
      e.printStackTrace();
      return -1;
    }
  }

  public long length() {
    try {
      return pos + in.available();
    }
    catch (IOException e) {
      e.printStackTrace();
      return -1;
    }
  }

  public long getFilePointer() {
    return pos;
  }

  public boolean isSeekable() {
    return false;
  }

  public long seek(long pos) {
    return pos;
  }

}
