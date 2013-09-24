/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */

package com.sun.voip;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Abstract superclass of audio sources that read data from a file.
 * @author jkaplan
 */
public abstract class FileAudioSource implements AudioSource {
    private static Map<String, Class<? extends FileAudioSource>> types =
            new HashMap<String, Class<? extends FileAudioSource>>();
    
    // initialize the map
    static {
        types.put(".au", DotAuAudioSource.class);
    }
    
    /**
     * Get a file audio source for the given file.  This looks at
     * the extension of the source and decides which AudioSource type
     * to load.
     * <p>
     * All audio sources are expected to have a constructor that takes
     * the path as an argument and throws an IOException in the case of
     * an error.
     * 
     * @param path the path to the audio file
     * @return a FileAudioSource for playing the path or null if there is
     * an error finding an audio source for the given type
     */
    public static FileAudioSource getAudioSource(String path) {
        // get the extension
        int extIdx = path.lastIndexOf(".");
        if (extIdx == -1) {
            Logger.error("Unable to determine extension of " + path);
            return null;
        }
        
        String ext = path.substring(extIdx);
        Class<? extends FileAudioSource> clazz = types.get(ext);
        if (clazz == null) {
            Logger.error("No player for extension " + ext);
            return null;
        }
        
        try {
            Constructor<? extends FileAudioSource> c =
                    clazz.getConstructor(String.class);
            return c.newInstance(path);
        } catch (Exception e) {
	    if (e instanceof IOException) {
	        Logger.println("getAudioSource for " + path + " got IOException: "
		    + e.getMessage()); 
	    } else {
	        if (Logger.logLevel >= Logger.LOG_INFO) {
                    Logger.exception("Error instantiating class " + clazz, e);
	        }
	    }
            return null;
        }
    }
    
    /**
     * Register a new type of audio source.  There can be one
     * decoder for each audio file type, identified by extension.
     * @param extension the extension of files to decode, e.g. ".au"
     * @param clazz the class to use to decode the given audio source
     */
    public static void registerAudioSource(String extension,
                       Class<? extends FileAudioSource> clazz)
    {
        types.put(extension, clazz);
    }
    
    /**
     * Load the audio source from the given path.  The path may be either
     * in the jar file which FileAudioSource is read from or in the
     * file system.  The jar file is checked first.
     * @param path the path to load from
     * @return an InputStream read from the given path
     * @throws IOException if there is an error loading the stream
     */
    protected InputStream getInputStream(String path) throws IOException {
	/*
	 * replace back slashes with slash.
	 */
        path = path.replaceAll(Matcher.quoteReplacement("\\"), "/");

        // try to load the stream as a resource
	InputStream in = getClass().getResourceAsStream(path);

	if (in != null) {
	    in = new BufferedInputStream(in, 16*1024);

	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		Logger.println("Successfully opened as stream '" + path + "'");
	    }
	} else {
            // no luck, try the file system
	    if (File.separator.equals("\\")) {
		/*
		 * Replace slash with back slash
		 */
                path = path.replaceAll("/", Matcher.quoteReplacement("\\"));
	    }

	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	        Logger.println("can't read audio resource, trying as a file: " 
		    + path);
	    }

            try {
                in = new FileInputStream(path);
            } catch (FileNotFoundException e) {
                throw new IOException("DotAuAudioSource error " + path + ":  " +
                    e.getMessage());
	    }
	}
        
        return in;
    }
}
