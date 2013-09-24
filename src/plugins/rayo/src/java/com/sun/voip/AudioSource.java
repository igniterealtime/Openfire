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

import java.io.IOException;

/*
 * Interface for an audio data source.  
 * Implementations are DotAuAudioSource and LinearDataAudioSource.
 * Another implemention might generate sine waves.
 */
public interface AudioSource {

    public static final int ULAW = 1;
    public static final int LINEAR = 3;

    public int[] getLinearData(int sampleTime) throws IOException;

    public void rewind() throws IOException;

    public void done();

    public int getSampleRate();

    public int getChannels();

}
