/**
 * $RCSfile$
 * $Revision: 655 $
 * $Date: 2004-12-09 21:54:27 -0300 (Thu, 09 Dec 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.StreamIDFactory;
import java.util.Random;

/**
 * A basic stream ID factory that produces id's using java.util.Random
 * and a simple hex representation of a random int.
 *
 * @author Iain Shigeoka
 */
public class BasicStreamIDFactory implements StreamIDFactory {

    /**
     * The random number to use, someone with Java can predict stream IDs if they can guess the current seed *
     */
    Random random = new Random();

    public StreamID createStreamID() {
        return new BasicStreamID(Integer.toHexString(random.nextInt()));
    }

    public StreamID createStreamID(String name) {
        return new BasicStreamID(name);
    }

    private class BasicStreamID implements StreamID {
        String id;

        public BasicStreamID(String id) {
            this.id = id;
        }

        public String getID() {
            return id;
        }

        @Override
		public String toString() {
            return id;
        }

        @Override
		public int hashCode() {
            return id.hashCode();
        }
    }
}
