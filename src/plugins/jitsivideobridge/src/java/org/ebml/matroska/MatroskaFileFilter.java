package org.ebml.matroska;

import java.io.*;

/**
 * <p>Title: JEBML</p>
 * <p>Description: Java Classes to Read EBML Elements</p>
 * <p>Copyright: Copyright (c) 2002-2004 John Cannon <spyder@matroska.org>, Jory Stone <jcsston@toughguy.net></p>
 * <p>Company: </p>
 * @author jcsston
 * @version 1.0
 */

public class MatroskaFileFilter extends javax.swing.filechooser.FileFilter {
  public MatroskaFileFilter() {
  }
  public boolean accept(File parm1) {
    if (parm1.isDirectory())
      return true;

    String path = parm1.getAbsolutePath();
    path = path.toLowerCase();

    if (path.endsWith("mkv")
        || path.endsWith("mka")
        || path.endsWith("mks"))
      return true;

    return false;
  }
  public String getDescription() {
    return "Matroska Video/Audio Files";
  }

}