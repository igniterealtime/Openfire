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

/*
 * Various strings of call status
 */
public class CallStatus {

    public static final String UNINITIALIZED         = "000 UNINITIALIZED";
    public static final String NO_SUCH_WHISPER_GROUP = "050 NO SUCH WHISPER GROUP";
    public static final String INVITED               = "100 INVITED";
    public static final String ANSWERED	             = "110 ANSWERED";
    public static final String JOIN_TIMEOUT          = "120 JOIN CONFIRMATION TIMEOUT";
    public static final String CALL_ANSWER_TIMEOUT   = "127 CALL ANSWER TIMEOUT";
    public static final String ESTABLISHED	     = "200 ESTABLISHED";
    public static final String NUMBER_OF_CALLS       = "220 NUMBER OF CALLS";
    public static final String TREATMENT_DONE	     = "230 TREATMENT DONE";
    public static final String STARTED_SPEAKING      = "250 STARTED SPEAKING";
    public static final String STOPPED_SPEAKING      = "259 STOPPED SPEAKING";
    public static final String DTMF_KEY              = "269 DTMF DTMFKey=";
    public static final String MIGRATED	             = "270 MIGRATED";
    public static final String MIGRATION_FAILED      = "275 MIGRATION FAILED no answer";
    public static final String ENDING	             = "290 ENDING";
    public static final String ENDED	             = "299 ENDED";
    public static final String BUSY_HERE	     = "486 Busy Here";
    public static final String CANT_START_CONFERENCE = "900 Can't start conference";
    public static final String CANT_CREATE_MEMBER    = "910 Can't create member";
    public static final String H323_NOT_IMPLEMENTED  = "920 H323 is not implemented";

}
