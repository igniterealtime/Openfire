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

package com.sun.voip.server;

import com.sun.voip.AudioConversion;
import com.sun.voip.CallParticipant;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.MixDataSource;
import com.sun.voip.Recorder;
import com.sun.voip.RtpPacket;
import com.sun.voip.TreatmentManager;
import com.sun.voip.TreatmentDoneListener;
import com.sun.voip.Util;

import java.util.ArrayList;

import java.io.IOException;

import java.text.ParseException;

public class WhisperGroup implements MixDataSource, TreatmentDoneListener {
    private String id;

    private static double defaultAttenuation = .13;

    private static boolean commonMixDefault = true;

    private double attenuation;

    private boolean isTransient = false;
    private boolean isLocked = false;
    private boolean noCommonMix = false;

    private ArrayList members = new ArrayList();    // members in group
    private ArrayList whisperers = new ArrayList(); // members whispering

    private int[] linearMixBuffer;
    private int[] doNotRecordMix;

    private MediaInfo mediaInfo;

    public WhisperGroup(String id, double attenuation, MediaInfo mediaInfo) {
	this.id = id;
	this.attenuation = attenuation;
	this.mediaInfo = mediaInfo;

	if (commonMixDefault == false) {
	    noCommonMix = true;
	}

	Logger.writeFile("New Whisper group:  " + toString());
    }

    public static void setCommonMixDefault(boolean commonMixDefault) {
	WhisperGroup.commonMixDefault = commonMixDefault;
    }

    public static boolean getCommonMixDefault() {
	return commonMixDefault;
    }

    public void setMediaInfo(MediaInfo mediaInfo) {
	this.mediaInfo = mediaInfo;

	setAttenuation(attenuation);  // reset attenuation
    }

    public String getId() {
	return id;
    }

    public double getAttenuation() {
	return attenuation;
    }

    public static double getDefaultAttenuation() {
	return defaultAttenuation;
    }

    public static void setDefaultAttenuation(double defaultAttenuation) {
	WhisperGroup.defaultAttenuation = defaultAttenuation;
    }

    public ArrayList getMembers() {
	return members;
    }

    public int whisperCount() {
	return whisperers.size();
    }

    public boolean isMember(ConferenceMember member) {
        return members.contains(member);
    }

    private void removeWhisperer(ConferenceMember member) {
	whisperers.remove(member);
    }

    /*
     * For debugging
     */
    public String checkMember(ConferenceMember member) {
	String s = "";

	if (members.contains(member) == false) {
	    s += "\t**** Not a member of the whisper group! ****\n";
	}

	if (whisperers.contains(member) == false) {
	    s += "\t**** Not in whisperers! ****\n";
	}

	return s;
    }

    public void addCall(ConferenceMember member) {
        if (members.contains(member) == true) {
	    Logger.println(member + " is already in whisper group " + id);
	    return;
	}

        members.add(member);
    }      

    public void removeCall(ConferenceMember member) {
	if (members.contains(member) == false) {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println(member + " is not in whisper group " + id);
	    }
	    return;
	}

	members.remove(member);
    }

    public void setWhispering(boolean isWhispering, ConferenceMember member) {
	if (isMember(member) == false) {
	    Logger.println("Call " + member
		    + " is not a member of whisper group " + id);
	    return;
	}

        if (isWhispering) {
	    if (whisperers.contains(member)) {
		return;	  // already whispering
	    }

	    whisperers.add(member);

	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Call " + member + " started whispering to " 
		    + id);
	    }
	    return;
        } 

	if (whisperers.contains(member) == false) {
	    Logger.println("Call " + member + " is not in whisperers!");
	    return;
	}

	removeWhisperer(member);

        if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("Call " + member + " stopped whispering to " + id);
	}
    }

    public void setTransient(boolean isTransient) {
	this.isTransient = isTransient;
    }

    public boolean isTransient() {
	return isTransient;
    }

    public void setLocked(boolean isLocked) {
	this.isLocked = isLocked;
    }

    public boolean isLocked() {
	return isLocked;
    }

    public void setAttenuation(double attenuation) {
	this.attenuation = attenuation;
    }

    public boolean hasCommonMix() {
	return noCommonMix == false;
    }

    public void setNoCommonMix(boolean noCommonMix) {
	this.noCommonMix = noCommonMix;
    }

    public void forwardDtmf(MemberReceiver memberReceiver, String dtmfKeys) {
	synchronized (members) {
	    for (int i = 0; i < members.size(); i++) {
		ConferenceMember member = (ConferenceMember) members.get(i);

		if (member.getMemberReceiver() != memberReceiver) {
		    member.getMemberSender().setDtmfKeyToSend(dtmfKeys);
		}
	    }
	}
    }

    /*
     * This is called when data is received from a conference member
     * The member has already converted its contribution to linear.
     */
    public void addToLinearDataMix(int[] contribution, boolean doNotRecord) {
	if (doNotRecord) {
            if (doNotRecordMix == null) {
                doNotRecordMix = new int[contribution.length];

                System.arraycopy(contribution, 0, doNotRecordMix, 0,
                    contribution.length);
                return;
            }

            mixData(contribution, doNotRecordMix, true);
	    return;
	}

	if (linearMixBuffer == null) {
            linearMixBuffer = new int[contribution.length];

            System.arraycopy(contribution, 0, linearMixBuffer, 0,
                contribution.length);
            return;
        }

        mixData(contribution, linearMixBuffer, true);
    }

    public static void mixData(int[] inData, int[] mixData, boolean add) {
	try {
	    if (add) {
	        for (int i = 0; i < inData.length; i++) {
                    mixData[i] = mixData[i] + inData[i];
	        }
    	    } else {
	        for (int i = 0; i < inData.length; i++) {
                    mixData[i] = mixData[i] - inData[i];
	        }
	    }
	} catch (IndexOutOfBoundsException e) {
	    Logger.println("Exception!  inData length " + inData.length
		+ " mixData length " + mixData.length + " add " + add);

	    e.printStackTrace();
	}
    }

    public static void mixData(int[] conferenceData, int[] memberData,
	    int[] outData) {

	try {
	    for (int i = 0; i < outData.length; i ++) {
	        outData[i] = conferenceData[i] - memberData[i];
	    }
	} catch (IndexOutOfBoundsException e) {
	    Logger.println("Exception!  conferenceData length " 
		+ conferenceData.length +" memberData.length " 
		+ memberData.length + " outData length " + outData.length);

	    e.printStackTrace();
	}
    }

    private int[] previousContribution;
    private int[] currentContribution;

    public String getSourceId() {
	return id;
    }

    public boolean contributionIsInCommonMix() {
	return hasCommonMix();
    }

    public int[] getPreviousContribution() {
	return previousContribution;
    }

    public int[] getCurrentContribution() {
	previousContribution = currentContribution;
	return currentContribution;
    }

    public void saveCurrentContribution() {
	currentContribution = linearMixBuffer;
	linearMixBuffer = null;

	if (currentTreatment != null) {
            synchronized (conferenceTreatments) {
	        currentTreatment.saveCurrentContribution();

	        int[] treatmentData = currentTreatment.getCurrentContribution();	
	
		if (treatmentDone) {
	            conferenceTreatments.remove(currentTreatment);

	    	    startNextTreatment();
		}

	        if (treatmentData != null) {
	            if (currentContribution == null) {
		        currentContribution = treatmentData;
	            } else {
	                mixData(treatmentData, currentContribution, true);
	            }
		}
	    }
	}

	if (currentContribution != null) {
	    recordAudio(currentContribution, currentContribution.length);
	}

	if (doNotRecordMix != null) {
	    mixData(doNotRecordMix, currentContribution, true);
	    doNotRecordMix = null;
	}
    }

    ArrayList conferenceTreatments = new ArrayList();
    TreatmentManager currentTreatment;
    boolean treatmentDone;

    public void addTreatment(TreatmentManager treatmentManager) {
	synchronized (conferenceTreatments) {
	    conferenceTreatments.add(treatmentManager);

	    if (currentTreatment == null) {
	        startNextTreatment();
	    }
	}
    }

    public void pauseTreatment(String treatment, boolean isPaused) {
        synchronized(conferenceTreatments) {
            for (int i = 0; i < conferenceTreatments.size(); i++) {
                TreatmentManager treatmentManager = (TreatmentManager)
                    conferenceTreatments.get(i);

                if (treatmentManager.getId().equals(treatment)) {
		    treatmentManager.pause(isPaused);
		    break;
		}
	    }
	}
    }

    public void removeTreatment(String treatment) {
	synchronized(conferenceTreatments) {
	    for (int i = 0; i < conferenceTreatments.size(); i++) {
		TreatmentManager treatmentManager = (TreatmentManager)
		    conferenceTreatments.get(i);

		if (treatmentManager.getId().equals(treatment)) {
		    if (currentTreatment == treatmentManager) {
		        treatmentManager.stopTreatment();
		    } else {
		        conferenceTreatments.remove(treatmentManager);
		    }
		    break;
		}
	    }
	}
    }

    public void treatmentDoneNotification(TreatmentManager treatmentManager) {
	/*
	 * We cannot start the next treatment just yet because we got called
	 * by saveCurrentContribution() above and we still need to use
	 * current contribution to get the last chunk of data.
	 */
	treatmentDone = true;
    }

    private void startNextTreatment() {
	treatmentDone = false;

        if (conferenceTreatments.size() == 0) {
            currentTreatment = null;
            return;
        }

        currentTreatment = (TreatmentManager) conferenceTreatments.get(0);
        currentTreatment.addTreatmentDoneListener(this);

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("whisper group " + id
                + " Starting first treatment " + currentTreatment.getId());
        }
    }

    private Recorder audioRecorder;
    private String recordingFile;
    private Integer recordingLock = new Integer(0);

    public void recordConference(boolean enabled, String recordingFile,
	    String recordingType) throws IOException {

        if (enabled == false) {
	    synchronized (recordingLock) {
	        if (audioRecorder != null) {
		    audioRecorder.done();
	            audioRecorder = null;
	        }

		this.recordingFile = null;
	    }
	    return;
	}

	if (audioRecorder == null) {
	    synchronized (recordingLock) {
                audioRecorder = new Recorder(recordingFile, "au", 
		    mediaInfo);

		Logger.println("starting conference recorder for "
		    + recordingFile + " " + mediaInfo);

		this.recordingFile = recordingFile;
	    }
	}
    }

    public String getRecordingFile() {
	return recordingFile;
    }

    private void recordAudio(int[] data, int length) {
	synchronized (recordingLock) {
	    if (audioRecorder == null) {
	        return;
	    }

	    /*
	     * We have linear data to record.
	     * If the mediaInfo is PCMU, convert to PCMU before recording.
	     */
            try {
	        if (mediaInfo.getEncoding() != RtpPacket.PCMU_ENCODING) {
                    audioRecorder.write(data, 0, length);
		} else {
		    byte[] ulawData = new byte[data.length];

		    AudioConversion.linearToUlaw(data, ulawData, 0);
		    audioRecorder.write(ulawData, 0, ulawData.length);
		}
            } catch (IOException e) {
                Logger.println("Unable to record data " + e.getMessage());
                audioRecorder = null;
            }
	}
    }

    public String toAbbreviatedString() {
	return toAbbreviatedString(false);
    }

    public String toAbbreviatedString(boolean showMembers) {
	String id = this.id;

	if (id.length() >= 14) {
	    id = id.substring(0, 13);
	}

	String s = id + ":";

        s += (Math.round(attenuation * 1000) / 1000D);

	if (isTransient) {
	    s += " Transient";
	}

	if (isLocked) {
	    s += " Locked";
	}

 	if (noCommonMix) {
	    s += " NoCommonMix";
	}

        if (showMembers == false) {
	    return s;
	}

	s += " ";

        for (int j = 0; j < members.size(); j++) {
            ConferenceMember member = (ConferenceMember) members.get(j);

            CallParticipant cp = member.getCallParticipant();

            id = cp.toConsiseString();

            s += "'" + id;

            if (whisperers.contains(member)) {
		s += "+";
            }

            s += "' ";
        }

        return s;
    }

    public String toString() {
	String s = id + ":";

	s += mediaInfo + " ";

	s += (Math.round(attenuation * 1000) / 1000D);

	if (isTransient) {
	    s += " Transient";
	}

	if (isLocked) {
	    s += " Locked";
	}

	s += " ";

	for (int i = 0; i < members.size(); i++) {
	    ConferenceMember member = 
		(ConferenceMember)members.get(i);

	    CallParticipant cp = member.getCallParticipant();
	    s += cp.getCallId();

	    if (whisperers.contains(member)) {
		s += "+";
	    }

	    s += " ";
	}    

	return s;
    }

}
