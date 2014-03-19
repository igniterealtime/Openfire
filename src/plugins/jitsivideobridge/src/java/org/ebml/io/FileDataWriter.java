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

public class FileDataWriter implements DataWriter
{
  RandomAccessFile file = null;

  public FileDataWriter(String filename) throws FileNotFoundException, IOException
  {
    file = new RandomAccessFile(filename, "rw");
  }
  public FileDataWriter(String filename, String mode) throws FileNotFoundException, IOException
  {
    file = new RandomAccessFile(filename, mode);
  }
  public int write(byte b)
  {
    try
    {
      file.write(b);
      return 1;
    }
    catch (IOException ex)
    {
      return 0;
    }
  }
  public int write(byte[] buff)
  {
    try
    {
      file.write(buff);
      return buff.length;
    }
    catch (IOException ex)
    {
      return 0;
    }
  }
  public int write(byte[] buff, int offset, int length)
  {
    try
    {
      file.write(buff, offset, length);
      return length;
    }
    catch (IOException ex)
    {
      return 0;
    }
  }

  public long length()
  {
    try
    {
      return file.length();
    }
    catch (IOException ex)
    {
      return -1;
    }
  }
  public long getFilePointer()
  {
    try
    {
      return file.getFilePointer();
    }
    catch (IOException ex)
    {
      return -1;
    }
  }
  public boolean isSeekable()
  {
    return true;
  }
  public void close()
  {
    try
    {
    	file.close();
    }
    catch (IOException ex)
    {

    }
  }
  public long seek(long pos)
  {
    try
    {
      file.seek(pos);
      return file.getFilePointer();
    }
    catch (IOException ex)
    {
      return -1;
    }
  }
}
