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

public class MatroskaBlock extends BinaryElement {
  protected int [] Sizes = null;
  protected int HeaderSize = 0;
  protected int BlockTimecode = 0;
  protected int TrackNo = 0;
  private boolean keyFrame;



public MatroskaBlock(byte[] type) {
    super(type);
  }

  //public void readData(DataSource source) {
  //  parseBlock();
  //}

  public void parseBlock() {
    int index = 0;
    TrackNo = (int)EBMLReader.readEBMLCode(data);
    index = Element.codedSizeLength(TrackNo);
    HeaderSize += index;

    short BlockTimecode1 = (short)(data[index++] & 0xFF);
    short BlockTimecode2 = (short)(data[index++] & 0xFF);
    if (BlockTimecode1 != 0 || BlockTimecode2 != 0) {
      BlockTimecode = (BlockTimecode1 << 8) | BlockTimecode2;
    }


    int keyFlag = data[index] & 0x80;
    if(keyFlag > 0)
    	this.keyFrame = true;
    else
    	this.keyFrame = false;

    int LaceFlag = data[index] & 0x06;
    index++;
    // Increase the HeaderSize by the number of bytes we have read
    HeaderSize += 3;
    if (LaceFlag != 0x00) {
      // We have lacing
      byte LaceCount = data[index++];
      HeaderSize += 1;
      if (LaceFlag == 0x02) { // Xiph Lacing
        Sizes = readXiphLaceSizes(index, LaceCount);

      } else if (LaceFlag == 0x06) { // EBML Lacing
        Sizes = readEBMLLaceSizes(index, LaceCount);

      } else if (LaceFlag == 0x04) { // Fixed Size Lacing
        Sizes = new int[LaceCount+1];
        Sizes[0] = (int)(this.getSize() - HeaderSize) / (LaceCount+1);
        for (int s = 0; s < LaceCount; s++)
          Sizes[s+1] = Sizes[0];
      } else {
        throw new RuntimeException("Unsupported lacing type flag.");
      }
    }
    //data = new byte[(int)(this.getSize() - HeaderSize)];
    //source.read(data, 0, data.length);
    //this.dataRead = true;
  }

  public int[] readEBMLLaceSizes(int index, short LaceCount) {
    int [] LaceSizes = new int[LaceCount+1];
    LaceSizes[LaceCount] = (int)this.getSize();

    // This uses the DataSource.getBytePosition() for finding the header size
    // because of the trouble of finding the byte size of sized ebml coded integers
    //long ByteStartPos = source.getFilePointer();
    int startIndex = index;

    LaceSizes[0] = (int)EBMLReader.readEBMLCode(data, index);
    index += Element.codedSizeLength(LaceSizes[0]);
    LaceSizes[LaceCount] -= LaceSizes[0];

    long FirstEBMLSize = LaceSizes[0];
    long LastEBMLSize = 0;
    for (int l = 0; l < LaceCount-1; l++) {
      LastEBMLSize = EBMLReader.readSignedEBMLCode(data, index);
      index += Element.codedSizeLength(LastEBMLSize);

      FirstEBMLSize += LastEBMLSize;
      LaceSizes[l+1] = (int)FirstEBMLSize;

      // Update the size of the last block
      LaceSizes[LaceCount] -= LaceSizes[l+1];
    }
    //long ByteEndPos = source.getFilePointer();

    //HeaderSize = HeaderSize + (int)(ByteEndPos - ByteStartPos);
    HeaderSize = HeaderSize + (int)(index - startIndex);
    LaceSizes[LaceCount] -= HeaderSize;

    return LaceSizes;
  }

  public int[] readXiphLaceSizes(int index, short LaceCount) {
    int [] LaceSizes = new int[LaceCount+1];
    LaceSizes[LaceCount] = (int)this.getSize();

    //long ByteStartPos = source.getFilePointer();

    for (int l = 0; l < LaceCount; l++) {
      short LaceSizeByte = 255;
      while (LaceSizeByte == 255) {
        LaceSizeByte = (short)(data[index++] & 0xFF);
        HeaderSize += 1;
        LaceSizes[l] += LaceSizeByte;
      }
      // Update the size of the last block
      LaceSizes[LaceCount] -= LaceSizes[l];
    }
    //long ByteEndPos = source.getFilePointer();

    LaceSizes[LaceCount] -= HeaderSize;

    return LaceSizes;
  }

  public int getFrameCount() {
    if (Sizes == null) {
      return 1;
    }
    return Sizes.length;
  }

  public byte [] getFrame(int frame) {
    if (Sizes == null) {
      if (frame != 0) {
        throw new IllegalArgumentException("Tried to read laced frame on non-laced Block. MatroskaBlock.getFrame(frame > 0)");
      }
      byte [] FrameData = new byte[data.length-HeaderSize];
      ArrayCopy.arraycopy(data, HeaderSize, FrameData, 0, FrameData.length);

      return FrameData;
    }
    byte [] FrameData = new byte[Sizes[frame]];

    // Calc the frame data offset
    int StartOffset = HeaderSize;
    for (int s = 0; s < frame; s++) {
      StartOffset += Sizes[s];
    }

    // Copy the frame data
    ArrayCopy.arraycopy(data, StartOffset, FrameData, 0, FrameData.length);

    return FrameData;
  }

  public long getAdjustedBlockTimecode(long ClusterTimecode, long TimecodeScale) {
    return ClusterTimecode + (BlockTimecode);// * TimecodeScale);
  }

  public int getTrackNo() {
    return TrackNo;
  }

  public int getBlockTimecode() {
    return BlockTimecode;
  }

  public void setFrameData(int trackNo, long timecode, byte [] frameData)
  {
		this.data = new byte[4 + frameData.length];

		this.data[0] = (byte) (trackNo | 0x80);
		this.data[1] = (byte) (timecode >> 8);
		this.data[2] = (byte) (timecode & 0xff);
		this.data[3] = 0;						// flags

		ArrayCopy.arraycopy(frameData, 0, this.data, 4, frameData.length);
		setSize(4 + frameData.length);
  }


  public boolean isKeyFrame() {
    return keyFrame;
  }
}
