/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Fast way to copy data from one file to another.
 * From ORA Java I/O by Elliotte Rusty Harold.
 *
 * @author Iain Shigeoka
 * @see StreamCopier
 */
public class FileCopier {

    /**
     * Copies the inFile to the outFile.
     *
     * @param inFile  The file to copy from
     * @param outFile The file to copy to
     * @throws IOException If there was a problem making the copy
     */
    public static void copy(String inFile, String outFile) throws IOException {
        copy(new File(inFile), new File(outFile));
    }

    /**
     * Copies the inFile to the outFile.
     *
     * @param inFile  The file to copy from
     * @param outFile The file to copy to
     * @throws IOException If there was a problem making the copy
     */
    public static void copy(File inFile, File outFile) throws IOException {
        FileInputStream fin = null;
        FileOutputStream fout = null;
        try {
            fin = new FileInputStream(inFile);
            fout = new FileOutputStream(outFile);
            StreamCopier.copy(fin, fout);
        }
        finally {
            try {
                if (fin != null) fin.close();
            }
            catch (IOException e) {
                // do nothing
            }
            try {
                if (fout != null) fout.close();
            }
            catch (IOException e) {
                // do nothing
            }
        }
    }
}
