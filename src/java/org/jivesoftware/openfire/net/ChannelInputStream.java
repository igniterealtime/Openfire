/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.net;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Class that simulate an InputStream given a un-blocking channel.
 *
 * @author Daniele Piras
 */
class ChannelInputStream extends InputStream
{
  ByteBuffer buf = ByteBuffer.allocate(1024);

  ReadableByteChannel inputChannel;

  public ChannelInputStream(ReadableByteChannel ic)
  {
    inputChannel = ic;
  }

  private void doRead() throws IOException
  {
    final int cnt = inputChannel.read(buf);
    if (cnt > 0)
    {
      buf.flip();
    }
    else
    {
      if (cnt == -1)
      {
        buf.flip();
      }
    }
  }

  public synchronized int read(byte[] bytes, int off, int len)
      throws IOException
  {
    if (buf.position() == 0)
    {
      doRead();
    }
    else
    {
      buf.flip();
    }
    len = Math.min(len, buf.remaining());
    if (len == 0)
    {
      return -1;
    }
    buf.get(bytes, off, len);
    if (buf.hasRemaining())
    {
      // Discard read data and move unread data to the begining of the buffer.
      // Leave
      // the position at the end of the buffer as a way to indicate that there
      // is
      // unread data
      buf.compact();
    }
    else
    {
      buf.clear();
    }
    return len;
  }

  @Override
  public int read() throws IOException
  {
    byte[] tmpBuf = new byte[1];
    int byteRead = read(tmpBuf, 0, 1);
    if (byteRead < 1)
    {
      return -1;
    }
    else
    {
      return tmpBuf[0];
    }
  }
}
