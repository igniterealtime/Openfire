/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a Light-Weight XML Parser.
 * It read data from a channel and collect data until data are available in 
 * the channel.
 * When a message is complete you can retrieve messages invoking the method
 * getMsgs() and you can invoke the method areThereMsgs() to know if at least
 * an message is presents.
 * 
 * @author Daniele Piras
 *
 */
class XMLLightweightParser
{
  // Chars that rappresent CDATA section start
  protected static char[] CDATA_START = {'<','!','[','C','D','A','T','A','['};
  // Chars that rappresent CDATA section end
  protected static char[] CDATA_END = {']',']','>'};
  
  // Buffer with all data retrieved
  protected StringBuilder buffer = new StringBuilder();
  
  // ---- INTERNAL STATUS -------
  // Initial status
  protected static final int INIT = 0;
  // Status used when the first tag name is retrieved
  protected static final int HEAD = 2;
  // Status used when robot is inside the xml and it looking for the tag conclusion
  protected static final int INSIDE = 3;
  // Status used when a '<' is found and try to find the conclusion tag.
  protected static final int PRETAIL = 4;
  // Status used when the ending tag is equal to the head tag
  protected static final int TAIL = 5;
  // Status used when robot is inside the main tag and found an '/' to check '/>'.
  protected static final int VERIFY_CLOSE_TAG = 6;
  //  Status used when you are inside a parameter
  protected static final int INSIDE_PARAM_VALUE = 7;
  //  Status used when you are inside a cdata section
  protected static final int INSIDE_CDATA = 8;
  
  
  // Current robot status
  protected int status = INIT;
  
  // Index to looking for a CDATA section start or end.
  protected int cdataOffset = 0;
  
  // Number of chars that machs with the head tag. If the tailCount is equal to
  // the head length so a close tag is found.
  protected int tailCount = 0;
  // Indicate the starting point in the buffer for the next message.
  protected int startLastMsg = 0;
  // Flag used to discover tag in the form <tag />.
  protected boolean insideRootTag = false;
  // Object conteining the head tag
  protected StringBuilder head = new StringBuilder( 5 );
  // List with all finished messages found.
  protected List<String> msgs = new ArrayList<String>();

  
  private ReadableByteChannel inputChannel;
  byte[] rawByteBuffer;
  ByteBuffer byteBuffer;
  Charset encoder;
  
  public ReadableByteChannel getChannel()
  {
    return inputChannel;
  }
  
  public XMLLightweightParser( ReadableByteChannel channel, String charset )
  {
    rawByteBuffer = new byte[1024];
    byteBuffer = ByteBuffer.wrap( rawByteBuffer );
    setInput( channel, charset );
  }
  
  public XMLLightweightParser( InputStream is , String charset)
  {
    rawByteBuffer = new byte[1024];
    byteBuffer = ByteBuffer.wrap( rawByteBuffer );
    setInput( is, charset );
  }
  
  public void setInput( InputStream is, String charset )
  {
    inputChannel = Channels.newChannel( is );
    encoder = Charset.forName( charset );
    invalidateBuffer();
  }
  
  public void setInput( ReadableByteChannel channel, String charset )
  {
    inputChannel = channel;
    encoder = Charset.forName( charset );
    invalidateBuffer();
  }
  
  /*
   * true if the parser has found some complete xml message.
   */
  public boolean areThereMsgs()
  {
    return ( msgs.size() > 0 );
  }
  
  /*
   * @return an array with all messages found
   */
  public String[] getMsgs()
  {
    String[] res = new String[ msgs.size() ];
    for ( int i = 0; i < res.length; i++ )
    {
      res[ i ] = msgs.get( i );
    }
    msgs.clear();
    invalidateBuffer();
    return res;
  }
  
  /*
   * Method use to re-initialize the buffer 
   */
  protected void invalidateBuffer()
  {
    if ( buffer.length() > 0 )
    {
      String str = buffer.substring( startLastMsg ).toString().trim();
      buffer.delete( 0, buffer.length() );
      buffer.append( str );
      buffer.trimToSize();
    }
    startLastMsg = 0;
  }
  
  
  /*
   * Method that add a message to the list and reinit parser.
   */
  protected void foundMsg( String msg )
  {
    // Add message to the complete message list
    if ( msg != null )
    {
      msgs.add( msg.trim() );
    }
    // Move the position into the buffer
    status = INIT;
    tailCount = 0;
    cdataOffset = 0;
    head.setLength( 0 );
    insideRootTag = false;
  }
  
  /*
   * Main reading method
   */
  public void read() throws Exception
  {
 
    // Reset buffer
    byteBuffer.limit( rawByteBuffer.length );
    byteBuffer.rewind();
    int readByte = inputChannel.read( byteBuffer );
    if ( readByte == -1 )
    {
      // ERROR ON SOCKET!!
      throw new IOException( "ReadByte == -1.Socket Close" );
    }
    else if ( readByte <= 0 )
    {
      return;
    }
    else if (  readByte == 1 && rawByteBuffer[ 0 ] == ' ' )
    {
      // Heart bit! Ignore it.
      return;
    }
    byteBuffer.flip();
    byte[] bhs = byteBuffer.array();
    byteBuffer.rewind();
    CharBuffer charBuffer = encoder.decode( byteBuffer );
    charBuffer.flip();
    char[] buf = charBuffer.array();

      buffer.append( buf );
    // Robot.
    char ch;
    for ( int i = 0; i < readByte; i++ )
    {
      //ch = rawByteBuffer[ i ];
      ch = buf[ i ];
      if ( status == TAIL )
      {
        // Looking for the close tag
        if ( ch == head.charAt( tailCount ) )
        {
          tailCount++;
          if ( tailCount == head.length() )
          {
            // Close tag found!
            // Calculate the correct start,end position of the message into the buffer
            int end = buffer.length() - readByte + ( i + 1 );
            String msg = buffer.substring( startLastMsg, end );
            // Add message to the list
            foundMsg( msg );
            startLastMsg = end;
          }
        }
        else
        {
          tailCount = 0;
          status = INSIDE;
        }
      }
      else if ( status == PRETAIL )
      {
        if ( ch == CDATA_START[ cdataOffset ] )
        {
          cdataOffset++;
          if ( cdataOffset == CDATA_START.length )
          {
            status = INSIDE_CDATA;
            cdataOffset = 0;
            continue;
          }
        }
        else
        {
          cdataOffset = 0;
          status = INSIDE;
        }
        if ( ch == '/' )
        {
          status = TAIL;
        }
      }
      else if ( status == VERIFY_CLOSE_TAG )
      {
        if ( ch == '>' )
        {
          // Found a tag in the form <tag />
          int end = buffer.length() - readByte + ( i + 1 );
          String msg = buffer.substring( startLastMsg, end );
          // Add message to the list
          foundMsg( msg );
          startLastMsg = end;
        }
        else
        {
          status = INSIDE;
        }
      }
      else if ( status == INSIDE_PARAM_VALUE )
      {
        
        if ( ch == '"' )
        {
          status = INSIDE;
          continue;
        }
      }
      else if ( status == INSIDE_CDATA )
      {
        if ( ch == CDATA_END[ cdataOffset ] )
        {
          cdataOffset++;
          if ( cdataOffset == CDATA_END.length )
          {
            status = INSIDE;
            cdataOffset = 0;
            continue;
          }
        }
        else
        {
          cdataOffset = 0;
        }
      }
      else if ( status == INSIDE )
      {
        if ( ch == CDATA_START[ cdataOffset ] )
        {
          cdataOffset++;
          if ( cdataOffset == CDATA_START.length )
          {
            status = INSIDE_CDATA;
            cdataOffset = 0;
            continue;
          }
        }
        else
        {
          cdataOffset = 0;
        }
        if ( ch == '"' )
        {
          status = INSIDE_PARAM_VALUE;
        }
        else if ( ch == '>' )
        {
          if ( insideRootTag && "stream:stream>".equals( head.toString() ) )
          {
            // Found closing stream:stream
            int end = buffer.length() - readByte + ( i + 1 );
            String msg = buffer.substring( startLastMsg, end );
            foundMsg( msg ); 
            startLastMsg = end;
          }
          insideRootTag = false;
        }
        else if ( ch == '<' )
        {
          status = PRETAIL;
        }
        else if ( ch == '/' && insideRootTag  )
        {
          status = VERIFY_CLOSE_TAG;
        }
      }
      else if ( status == HEAD )
      {
        if ( ch == ' ' || ch == '>' )
        {
          // Append > to head to facility the research of </tag>
          head.append( ">" );
          status = INSIDE;
          insideRootTag = true;
          continue;
        }
        head.append( (char)ch );
        
      }
      else if ( status == INIT )
      {
        if ( ch != ' ' && ch != '\r' && ch != '\n' && ch != '<' )
        {
          invalidateBuffer();
          return;
        }
        if ( ch == '<' )
        {
          status = HEAD;
        }
      }
    }
  }

}
