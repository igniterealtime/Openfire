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

import java.util.*;

/**
 * DocType for Matroska files.
 * This has all the element type id declares and also is in charge of 
 * creating the correct element classes from the type id.
 */
public class MatroskaDocType implements DocType 
{
  // Custom Element Types
  static public short BLOCK_ELEMENT = (short)(ElementType.LAST_ELEMENT_TYPE + 1);
  static public short SEGMENT_ELEMENT = (short)(ElementType.LAST_ELEMENT_TYPE + 2);
  static public short CLUSTER_ELEMENT = (short)(ElementType.LAST_ELEMENT_TYPE + 3);

  // EBML Id's
  static public byte [] Void_Id = {(byte)0xEC};
  static public byte [] EBMLHeader_Id = {0x1A, 0x45, (byte)0xDF, (byte)0xA3};
    static public byte [] EBMLVersion_Id = {0x42, (byte)0x86};
    static public byte [] DocTypeReadVersion_Id = {0x42, (byte)0x85};
    static public byte [] EBMLReadVersion_Id = {0x42, (byte)0xF7};
    static public byte [] EBMLMaxIDLength_Id = {0x42, (byte)0xF2};
    static public byte [] EBMLMaxSizeLength_Id = {0x42, (byte)0xF3};
    static public byte [] DocType_Id = {0x42, (byte)0x82};
    static public byte [] DocTypeVersion_Id = {0x42, (byte)0x87};

  static public byte [] Segment_Id = {0x18, 0x53, (byte)0x80, 0x67};
    static public byte [] SeekHead_Id = {0x11, 0x4D, (byte)0x9B, 0x74};
      static public byte [] SeekEntry_Id = {0x4D, (byte)0xBB};
        static public byte [] SeekID_Id = {0x53, (byte)0xAB};
        static public byte [] SeekPosition_Id = {0x53, (byte)0xAC};
    static public byte [] SegmentInfo_Id = {0x15, (byte)0x49, (byte)0xA9, (byte)0x66};
      static public byte [] SegmentUID_Id = {0x73, (byte)0xA4};
      static public byte [] SegmentFilename_Id = {0x73, (byte)0x84};
      static public byte [] TimecodeScale_Id = {0x2A, (byte)0xD7, (byte)0xB1};
      static public byte [] Duration_Id = {0x44, (byte)0x89};
      static public byte [] DateUTC_Id = {0x44, (byte)0x61};
      static public byte [] Title_Id = {0x7B, (byte)0xA9};
      static public byte [] MuxingApp_Id = {0x4D, (byte)0x80};
      static public byte [] WritingApp_Id = {0x57, 0x41};
    static public byte [] Tracks_Id = {0x16, (byte)0x54, (byte)0xAE, (byte)0x6B};
      static public byte [] TrackEntry_Id = {(byte)0xAE};
        static public byte [] TrackNumber_Id = {(byte)0xD7};
        static public byte [] TrackUID_Id = {0x73, (byte)0xC5};
        static public byte [] TrackType_Id = {(byte)0x83};
        static public byte [] TrackDefaultDuration_Id = {0x23, (byte)0xE3, (byte)0x83};
        static public byte [] TrackName_Id = {0x53, 0x6E};
        static public byte [] TrackLanguage_Id = {0x22, (byte)0xB5, (byte)0x9C};
        static public byte [] TrackCodecID_Id = {(byte)0x86};
        static public byte [] TrackCodecPrivate_Id = {(byte)0x63, (byte)0xA2};
          static public byte [] TrackVideo_Id = {(byte)0xE0};
            static public byte [] PixelWidth_Id = {(byte)0xB0};
            static public byte [] PixelHeight_Id = {(byte)0xBA};
            static public byte [] DisplayWidth_Id = {0x54, (byte)0xB0};
            static public byte [] DisplayHeight_Id = {0x54, (byte)0xBA};
          static public byte [] TrackAudio_Id = {(byte)0xE1};
            static public byte [] SamplingFrequency_Id = {(byte)0xB5};
            static public byte [] OutputSamplingFrequency_Id = {0x78, (byte)0xB5};
            static public byte [] Channels_Id = {(byte)0x9F};
            static public byte [] BitDepth_Id = {0x62, 0x64};

    static public byte [] Attachments_Id = {0x19, 0x41, (byte)0xA4, 0x69};
      static public byte [] AttachedFile_Id = {0x61, (byte)0xA7};
        static public byte [] AttachedFileDescription_Id = {0x46, (byte)0x7E};
        static public byte [] AttachedFileName_Id = {0x46, (byte)0x6E};
        static public byte [] AttachedFileMimeType_Id = {0x46, (byte)0x60};
        static public byte [] AttachedFileData_Id = {0x46, (byte)0x5C};
        static public byte [] AttachedFileUID_Id = {0x46, (byte)0xAE};

    static public byte [] Tags_Id = {0x12, (byte)0x54, (byte)0xC3, (byte)0x67};
      static public byte [] Tag_Id = {0x73, (byte)0x73};
        static public byte [] TagTargets_Id = {0x63, (byte)0xC0};
          static public byte [] TagTargetTrackUID_Id = {0x63, (byte)0xC5};
          static public byte [] TagTargetChapterUID_Id = {0x63, (byte)0xC4};
          static public byte [] TagTargetAttachmentUID_Id = {0x63, (byte)0xC6};
        static public byte [] TagSimpleTag_Id = {0x67, (byte)0xC8};
          static public byte [] TagSimpleTagName_Id = {0x45, (byte)0xA3};
          static public byte [] TagSimpleTagString_Id = {0x44, (byte)0x87};
          static public byte [] TagSimpleTagBinary_Id = {0x44, (byte)0x85};

    static public byte [] Cluster_Id = {0x1F, (byte)0x43, (byte)0xB6, (byte)0x75};
      static public byte[] ClusterTimecode_Id = {(byte)0xE7};
      static public byte[] ClusterBlockGroup_Id = {(byte)0xA0};
        static public byte[] ClusterBlock_Id = {(byte)0xA1};
        static public byte[] ClusterSimpleBlock_Id = {(byte)0xA3};
        static public byte[] ClusterBlockDuration_Id = {(byte)0x9B};
        static public byte[] ClusterReferenceBlock_Id = {(byte)0xFB};

  static public byte[] Chapters_Id = {0x10, (byte)0x43, (byte)0xA7, (byte)0x70};
    static public byte [] ChapterEditionEntry_Id = {(byte)0x45, (byte)0xB9};
      static public byte [] ChapterEditionUID_Id	= {(byte)0x45, (byte)0xBC};
      static public byte [] ChapterEditionFlagHidden_Id = {(byte)0x45, (byte)0xBD};
      static public byte [] ChapterEditionFlagDefault_Id = {(byte)0x45, (byte)0xDB};
      static public byte [] ChapterEditionManaged_Id = {(byte)0x45, (byte)0xDD};
      static public byte [] ChapterAtom_Id = {(byte)0xB6};
        static public byte [] ChapterAtomChapterUID_Id = {(byte)0x73, (byte)0xC4};
        static public byte [] ChapterAtomChapterTimeStart_Id = {(byte)0x91};
        static public byte [] ChapterAtomChapterTimeEnd_Id = {(byte)0x92};
        static public byte [] ChapterAtomChapterFlagHidden_Id = {(byte)0x98};
        static public byte [] ChapterAtomChapterFlagEnabled_Id = {(byte)0x45, (byte)0x98};
        static public byte [] ChapterAtomChapterPhysicalEquiv_Id = {(byte)0x63, (byte)0xC3};
        static public byte [] ChapterAtomChapterTrack_Id = {(byte)0x8F};
          static public byte [] ChapterAtomChapterTrackNumber_Id = {(byte)0x89};
        static public byte [] ChapterAtomChapterDisplay_Id = {(byte)0x80};
          static public byte [] ChapterAtomChapString_Id = {(byte)0x85};
          static public byte [] ChapterAtomChapLanguage_Id = {(byte)0x43, (byte)0x7C};
          static public byte [] ChapterAtomChapCountry_Id = {(byte)0x43, (byte)0x7E};
  
  // Track Types
  static public byte track_video       = 0x01; ///< Rectangle-shaped non-transparent pictures aka video
  static public byte track_audio       = 0x02; ///< Anything you can hear
  static public byte track_complex     = 0x03; ///< Audio and video in same track, used by DV
  static public byte track_logo        = 0x10; ///< Overlay-pictures, displayed over video
  static public byte track_subtitle    = 0x11; ///< Text-subtitles. One track contains one language and only one track can be active (player-side configuration)
  static public byte track_control     = 0x20; ///< Control-codes for menus and other stuff

  /**
   * Converts a integer track type to String form.
   *
   * @param trackType Integer Track Type
   * @return String <code>trackType</code> in String form
   */
  static public String TrackTypeToString(byte trackType) {
    if (trackType == track_video)
      return "Video";
    if (trackType == track_audio)
      return "Audio";
    if (trackType == track_complex)
      return "Complex";
    if (trackType == track_logo)
      return "Logo";
    if (trackType == track_subtitle)
      return "Subtitle";
    if (trackType == track_control)
      return "Control";

    return "";
  }
  protected ElementType type;

  static public MatroskaDocType obj = new MatroskaDocType();

  public MatroskaDocType() {
    //long start = java.lang.System.currentTimeMillis();
    init();
    //System.out.println("MatroskaDocType Loaded in " + java.lang.System.currentTimeMillis() - start + " milliseconds");
  }

  protected void init() {
    try {
      ElementType baseLevel = new ElementType("", (short)0, (byte[])null,
                                              (short)0, new ArrayList<ElementType>());
      ElementType level0 = null;
      ElementType level1 = null;
      ElementType level2 = null;
      ElementType level3 = null;
      ElementType level4 = null;
      ElementType level5 = null;

      level0 = new ElementType("Void",
                               (short)1,
                               Void_Id,
                               ElementType.BINARY_ELEMENT,
                               (ArrayList<ElementType>)null);
      baseLevel.children.add(level0);

      level0 = new ElementType("EBMLHeader",
                               (short)0,
                               EBMLHeader_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level1 = new ElementType("EBMLVersion",
                               (short)1,
                               EBMLVersion_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level0.children.add(level1);

      level1 = new ElementType("EBMLReadVersion",
                               (short)1,
                               EBMLReadVersion_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level0.children.add(level1);

      level1 = new ElementType("EBMLMaxIDLength",
                               (short)1,
                               EBMLMaxIDLength_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level0.children.add(level1);

      level1 = new ElementType("EBMLMaxSizeLength",
                               (short)1,
                               EBMLMaxSizeLength_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level0.children.add(level1);

      level1 = new ElementType("DocType",
                               (short)1,
                               DocType_Id,
                               ElementType.STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level0.children.add(level1);

      level1 = new ElementType("DocTypeVersion",
                               (short)1,
                               DocTypeVersion_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level0.children.add(level1);

      level1 = new ElementType("DocTypeReadVersion",
                               (short)1,
                               DocTypeReadVersion_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level0.children.add(level1);

      baseLevel.children.add(level0);

      level0 = new ElementType("Segment",
                               (short)0,
                               Segment_Id,
                               MatroskaDocType.SEGMENT_ELEMENT,
                               new ArrayList<ElementType>());

      level1 = new ElementType("SeekHead",
                               (short)1,
                               SeekHead_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level2 = new ElementType("SeekEntry",
                               (short)2,
                               SeekEntry_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level3 = new ElementType("SeekID",
                               (short)3,
                               SeekID_Id,
                               ElementType.BINARY_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("SeekPosition",
                               (short)3,
                               SeekPosition_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);
      // Add Seek Element
      level1.children.add(level2);
      // Add SeekHead Element
      level0.children.add(level1);

      level1 = new ElementType("SegmentInfo",
                               (short)1,
                               SegmentInfo_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level2 = new ElementType("SegmentUID",
                               (short)2,
                               SegmentUID_Id,
                               ElementType.BINARY_ELEMENT,
                               (ArrayList<ElementType>)null);
      level1.children.add(level2);

      level2 = new ElementType("SegmentFilename",
                               (short)2,
                               SegmentFilename_Id,
                               ElementType.STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level1.children.add(level2);

      level2 = new ElementType("TimecodeScale",
                               (short)2,
                               TimecodeScale_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level1.children.add(level2);

      level2 = new ElementType("Duration",
                               (short)2,
                               Duration_Id,
                               ElementType.FLOAT_ELEMENT,
                               (ArrayList<ElementType>)null);
      level1.children.add(level2);

      level2 = new ElementType("DateUTC",
                               (short)2,
                               DateUTC_Id,
                               ElementType.DATE_ELEMENT,
                               (ArrayList<ElementType>)null);
      level1.children.add(level2);

      level2 = new ElementType("Title",
                               (short)2,
                               Title_Id,
                               ElementType.STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level1.children.add(level2);

      level2 = new ElementType("MuxingApp",
                               (short)2,
                               MuxingApp_Id,
                               ElementType.STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level1.children.add(level2);

      level2 = new ElementType("WritingApp",
                               (short)2,
                               WritingApp_Id,
                               ElementType.STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level1.children.add(level2);

      // Add Segment Infomation Element
      level0.children.add(level1);

      level1 = new ElementType("Tracks",
                               (short)1,
                               Tracks_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level2 = new ElementType("TrackEntry",
                               (short)2,
                               TrackEntry_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level3 = new ElementType("TrackNumber",
                               (short)3,
                               TrackNumber_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("TrackUID",
                               (short)3,
                               TrackUID_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("TrackType",
                               (short)3,
                               TrackType_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("TrackDefaultDuration",
                               (short)3,
                               TrackDefaultDuration_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("TrackName",
                               (short)3,
                               TrackName_Id,
                               ElementType.STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("TrackLanguage",
                               (short)3,
                               TrackLanguage_Id,
                               ElementType.ASCII_STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("TrackCodecID",
                               (short)3,
                               TrackCodecID_Id,
                               ElementType.ASCII_STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("TrackCodecPrivate",
                               (short)3,
                               TrackCodecPrivate_Id,
                               ElementType.BINARY_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("TrackVideo",
                               (short)3,
                               TrackVideo_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level4 = new ElementType("PixelWidth",
                               (short)4,
                               PixelWidth_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("PixelHeight",
                               (short)4,
                               PixelHeight_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("DisplayWidth",
                               (short)4,
                               DisplayWidth_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("DisplayHeight",
                               (short)4,
                               DisplayHeight_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level3.children.add(level4);

      // Add TrackVideo Element
      level2.children.add(level3);

      level3 = new ElementType("TrackAudio",
                               (short)3,
                               TrackAudio_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level4 = new ElementType("SamplingFrequency",
                               (short)4,
                               SamplingFrequency_Id,
                               ElementType.FLOAT_ELEMENT,
                               (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("OutputSamplingFrequency",
                               (short)4,
                               OutputSamplingFrequency_Id,
                               ElementType.FLOAT_ELEMENT,
                               (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("Channels",
                               (short)4,
                               Channels_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("BitDepth",
                               (short)4,
                               BitDepth_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level3.children.add(level4);

      // Add TrackAudio Element
      level2.children.add(level3);

      // Add TrackEntry Element
      level1.children.add(level2);
      // Add Tracks Element
      level0.children.add(level1);

      level1 = new ElementType("Attachments",
                               (short)1,
                               Attachments_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level2 = new ElementType("AttachedFile",
                               (short)2,
                               AttachedFile_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level3 = new ElementType("AttachedFileDescription",
                               (short)3,
                               AttachedFileDescription_Id,
                               ElementType.STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("AttachedFileName",
                               (short)3,
                               AttachedFileName_Id,
                               ElementType.STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("AttachedFileMimeType",
                               (short)3,
                               AttachedFileMimeType_Id,
                               ElementType.ASCII_STRING_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("AttachedFileData",
                               (short)3,
                               AttachedFileData_Id,
                               ElementType.BINARY_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("AttachedFileUID",
                               (short)3,
                               AttachedFileUID_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      // Add AttachedFile Element
      level1.children.add(level2);
      // Add Attachments Element
      level0.children.add(level1);

      level1 = new ElementType("Tags",
                               (short)1,
                               Tags_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level2 = new ElementType("Tag",
                               (short)2,
                               Tag_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level3 = new ElementType("TagTargets",
                               (short)3,
                               TagTargets_Id,
                               ElementType.MASTER_ELEMENT,
                              new ArrayList<ElementType>());

     level4 = new ElementType("TagTargetTrackUID",
                              (short)4,
                              TagTargetTrackUID_Id,
                              ElementType.UINTEGER_ELEMENT,
                              (ArrayList<ElementType>)null);
     level3.children.add(level4);

     level4 = new ElementType("TagTargetChapterUID",
                              (short)4,
                              TagTargetChapterUID_Id,
                              ElementType.UINTEGER_ELEMENT,
                              (ArrayList<ElementType>)null);
     level3.children.add(level4);

     level4 = new ElementType("TagTargetAttachmentUID",
                              (short)4,
                              TagTargetAttachmentUID_Id,
                              ElementType.UINTEGER_ELEMENT,
                              (ArrayList<ElementType>)null);
     level3.children.add(level4);

     // Add Targets
      level2.children.add(level3);

      level3 = new ElementType("TagSimpleTag",
                               (short)3,
                               TagSimpleTag_Id,
                               ElementType.MASTER_ELEMENT,
                              new ArrayList<ElementType>());

     level4 = new ElementType("TagSimpleTagName",
                              (short)4,
                              TagSimpleTagName_Id,
                              ElementType.STRING_ELEMENT,
                              (ArrayList<ElementType>)null);
     level3.children.add(level4);

     level4 = new ElementType("TagSimpleTagString",
                              (short)4,
                              TagSimpleTagString_Id,
                              ElementType.STRING_ELEMENT,
                              (ArrayList<ElementType>)null);
     level3.children.add(level4);

     level4 = new ElementType("TagSimpleTagBinary",
                              (short)4,
                              TagSimpleTagBinary_Id,
                              ElementType.BINARY_ELEMENT,
                              (ArrayList<ElementType>)null);
     level3.children.add(level4);

     // Add SimpleTag
      level2.children.add(level3);

      // Add Tag Element
      level1.children.add(level2);
      // Add Tags Element
      level0.children.add(level1);

      level1 = new ElementType("Cluster",
                               (short)1,
                               Cluster_Id,
                               MatroskaDocType.CLUSTER_ELEMENT,
                               new ArrayList<ElementType>());

      level2 = new ElementType("ClusterTimecode",
                               (short)2,
                               ClusterTimecode_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level1.children.add(level2);

      level2 = new ElementType("ClusterBlockGroup",
                               (short)2,
                               ClusterBlockGroup_Id,
                               ElementType.MASTER_ELEMENT,
                               new ArrayList<ElementType>());

      level3 = new ElementType("ClusterBlock",
                               (short)3,
                               ClusterBlock_Id,
                               MatroskaDocType.BLOCK_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("ClusterBlockDuration",
                               (short)3,
                               ClusterBlockDuration_Id,
                               ElementType.UINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("ClusterReferenceBlock",
                               (short)3,
                               ClusterReferenceBlock_Id,
                               ElementType.SINTEGER_ELEMENT,
                               (ArrayList<ElementType>)null);
      level2.children.add(level3);

      // Add ClusterBlockGroup Element
      level1.children.add(level2);
      

      level2 = new ElementType("SimpleBlock",
              (short)2,
              ClusterSimpleBlock_Id,
              MatroskaDocType.BLOCK_ELEMENT,
              new ArrayList<ElementType>());

      // Add SimpleBlock Element
      level1.children.add(level2);

      // Add Cluster Element
      level0.children.add(level1);

      level1 = new ElementType("Chapters",
        (short)1,
        Chapters_Id,
        ElementType.MASTER_ELEMENT,
        new ArrayList<ElementType>());

      level2 = new ElementType("ChapterEditionEntry",
        (short)2,
        ChapterEditionEntry_Id,
        ElementType.MASTER_ELEMENT,
        new ArrayList<ElementType>());

      level3 = new ElementType("ChapterEditionUID",
        (short)3,
        ChapterEditionUID_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("ChapterEditionFlagHidden",
        (short)3,
        ChapterEditionFlagHidden_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("ChapterEditionFlagDefault",
        (short)3,
        ChapterEditionFlagDefault_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("ChapterEditionManaged",
        (short)3,
        ChapterEditionManaged_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level2.children.add(level3);

      level3 = new ElementType("ChapterAtom",
        (short)3,
        ChapterAtom_Id,
        ElementType.MASTER_ELEMENT,
        new ArrayList<ElementType>());      

      level4 = new ElementType("ChapterAtomChapterUID",
        (short)4,
        ChapterAtomChapterUID_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("ChapterAtomChapterTimeStart",
        (short)4,
        ChapterAtomChapterTimeStart_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("ChapterAtomChapterTimeEnd",
        (short)4,
        ChapterAtomChapterTimeEnd_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("ChapterAtomChapterFlagHidden",
        (short)4,
        ChapterAtomChapterFlagHidden_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("ChapterAtomChapterFlagEnabled",
        (short)4,
        ChapterAtomChapterFlagEnabled_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("ChapterAtomChapterPhysicalEquiv",
        (short)4,
        ChapterAtomChapterPhysicalEquiv_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level3.children.add(level4);

      level4 = new ElementType("ChapterAtomChapterTrack",
        (short)4,
        ChapterAtomChapterTrack_Id,
        ElementType.MASTER_ELEMENT,
        new ArrayList<ElementType>());      

      level5 = new ElementType("ChapterAtomChapterTrackNumber",
        (short)5,
        ChapterAtomChapterTrackNumber_Id,
        ElementType.UINTEGER_ELEMENT,
        (ArrayList<ElementType>)null);
      level4.children.add(level5);

      // Add ChapterAtomChapterTrack Element
      level3.children.add(level4);

      level4 = new ElementType("ChapterAtomChapterDisplay",
        (short)4,
        ChapterAtomChapterDisplay_Id,
        ElementType.MASTER_ELEMENT,
        new ArrayList<ElementType>());      

      level5 = new ElementType("ChapterAtomChapString",
        (short)5,
        ChapterAtomChapString_Id,
        ElementType.STRING_ELEMENT,
        (ArrayList<ElementType>)null);
      level4.children.add(level5);

      level5 = new ElementType("ChapterAtomChapLanguage",
        (short)5,
        ChapterAtomChapLanguage_Id,
        ElementType.ASCII_STRING_ELEMENT,
        (ArrayList<ElementType>)null);
      level4.children.add(level5);

      level5 = new ElementType("ChapterAtomChapCountry",
        (short)5,
        ChapterAtomChapCountry_Id,
        ElementType.ASCII_STRING_ELEMENT,
        (ArrayList<ElementType>)null);
      level4.children.add(level5);

      // Add ChapterAtomChapterDisplay Element
      level3.children.add(level4);

      // Add ChapterAtom Element
      level2.children.add(level3);

      // Add ChapterEditionEntry Element
      level1.children.add(level2);

      // Add Chapters Element
      level0.children.add(level1);

      // Add Segment Element
      baseLevel.children.add(level0);

      type = baseLevel;
    } catch (java.lang.Exception ex) {
      ex.printStackTrace();
    }
  }
  /**
   * Get the base ElementType tree.
   *
   * @return An ElementType that is filled with all the valid ElementType(s) for the MatroskaDocType
   */
  public ElementType getElements() {
    return type;
  }

  /**
   * Creates an Element sub-class based on the ElementType.
   *
   * @param type ElementType to use for creation of Element.
   * @return new Element sub-class, BinaryElement is the default.
   * @throws RuntimeException if the ElementType has an unknown type field.
   */
  public Element createElement(ElementType type) {
    Element elem = null;
    elem = type.createElement();

    if (elem == null) {
      if (type.type == MatroskaDocType.BLOCK_ELEMENT) 
      {
        elem = new MatroskaBlock(type.id);
      } 
      else if (type.type == MatroskaDocType.SEGMENT_ELEMENT) 
      {
        elem = new MatroskaSegment(type.id);
      }
      else if (type.type == MatroskaDocType.CLUSTER_ELEMENT) 
      {
        elem = new MatroskaCluster(type.id);        
      } 
      else if (type.type == ElementType.UNKNOWN_ELEMENT) 
      {
        elem = new BinaryElement(type.id);

      } 
      else 
      {
        throw new java.lang.RuntimeException("Error: Unknown Element Type");
      }
      elem.setElementType(type);
    }

    return elem;
  }

  /**
   * Creates an Element sub-class based on the element id.
   *
   * @param type id to use for creation of Element.
   * @return new Element sub-class, BinaryElement is the default.
   * @throws RuntimeException if the ElementType has an unknown type field.
   */
  public Element createElement(byte [] type) 
  {
    ElementType elementType = getElements().findElement(type);
    if (elementType == null) 
    {
      elementType = new UnknownElementType(type);
    }

    return createElement(elementType);
  }
}
