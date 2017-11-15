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

import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.RtpPacket;
import com.sun.voip.TreatmentManager;

import java.util.ArrayList;

import java.io.IOException;
import java.text.ParseException;

public class WGManager {

    private ArrayList whisperGroups = new ArrayList();
    
    private MediaInfo mediaInfo;

    private WhisperGroup conferenceWhisperGroup;
   
    public WGManager(String conferenceId, MediaInfo mediaInfo) {
    this.mediaInfo = mediaInfo;

    int channels = mediaInfo.getChannels();

    conferenceWhisperGroup = createWhisperGroup(
        conferenceId, WhisperGroup.getDefaultAttenuation());
    }

    public WhisperGroup getConferenceWhisperGroup() {
    return conferenceWhisperGroup;
    }

    public boolean hasCommonMix() {
    return conferenceWhisperGroup.hasCommonMix();
    }

    public ArrayList getWhisperGroups() {
    return whisperGroups;
    }

    public void setMediaInfo(MediaInfo mediaInfo) {
    this.mediaInfo = mediaInfo;

        synchronized (whisperGroups) {
            for (int i = 0; i < whisperGroups.size(); i++) {
                WhisperGroup whisperGroup = (WhisperGroup)
                    whisperGroups.get(i);

                whisperGroup.setMediaInfo(mediaInfo);
            }
        }
    }

    public String getWhisperGroups(ConferenceMember member) {
        String s = "";

        boolean firstTime = true;

        synchronized(whisperGroups) {
            for (int i = 0; i < whisperGroups.size(); i++) {
                WhisperGroup whisperGroup =
                    (WhisperGroup)whisperGroups.get(i);

                if (whisperGroup.isMember(member)) {
                    if (firstTime) {
                        firstTime = false;
                        s += "\tBelongs to Whisper Group\n";
                    }
                    s += "\t    " + whisperGroup.getId() + "\n";
                }
            }
        }

        return s;
    }

    public WhisperGroup createWhisperGroup(String whisperGroupId, 
            double attenuation) {

        WhisperGroup whisperGroup;

        synchronized(whisperGroups) {
        for (int i = 0; i < whisperGroups.size(); i++) {
        whisperGroup = (WhisperGroup) whisperGroups.get(i);

        if (whisperGroupId.equals(whisperGroup.getId())) {
            Logger.println("whisper group already exists: "
            + whisperGroupId);
            return whisperGroup;
        }
        }

        whisperGroup = new WhisperGroup(whisperGroupId, 
        attenuation, mediaInfo);

            whisperGroups.add(whisperGroup);

            if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("New Whisper group " + toString());
            }
    }

    return whisperGroup;
    }

    public boolean isInWhisperGroup(ConferenceMember member) {
    synchronized(whisperGroups) {
            for (int i = 0; i < whisperGroups.size(); i++) {
                WhisperGroup whisperGroup =
                    (WhisperGroup)whisperGroups.get(i);

        if (whisperGroup.isMember(member)) {
            return true;
        }
        }
    }
    return false;
    }

    public void migrate(ConferenceMember oldMember, 
        ConferenceMember newMember) {

        synchronized(whisperGroups) {
            for (int i = 0; i < whisperGroups.size(); i++) {
                WhisperGroup whisperGroup =
                    (WhisperGroup)whisperGroups.get(i);

                if (whisperGroup.isMember(oldMember)) {
            whisperGroup.removeCall(oldMember);
             
            whisperGroup.addCall(newMember);
        }
        }
    }
    }

    public void removeCall(WhisperGroup whisperGroup,
        ConferenceMember member) {

    whisperGroup.removeCall(member);

    if (whisperGroup.isTransient()) {
        String id = whisperGroup.getId();

        if (whisperGroup.getMembers().size() == 0) {
        Logger.println("Removing transient whisper group " + id);

        try {
            destroyWhisperGroup(whisperGroup.getId());
        } catch (ParseException e) {
            Logger.println("Failed to destroy whisper group " + id
            + " " + e.getMessage());
        }
        } else {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println(whisperGroup.getMembers().size() 
                + " members remaining in transient whisper group " 
                + id);
        }
        }
    } 
    }

    public void destroyWhisperGroup(String id) throws ParseException {
        synchronized(whisperGroups) {
            WhisperGroup whisperGroup = findWhisperGroup(id);

            if (whisperGroup == null) {
                Logger.println("can't find whisperGroup " + id);
                throw new ParseException("can't find whisperGroup " + id, 0);
            }

            synchronized(whisperGroup) {
                ArrayList members = whisperGroup.getMembers();

        /*
         * We don't want to be called recursively when we
         * remove the last member!
         */
        whisperGroup.setTransient(false);

        while (members.size() > 0) {
            ConferenceMember member = (ConferenceMember)
            members.get(0);

            member.removeCall(id);
        }
            }

            whisperGroups.remove(whisperGroup);
        }

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Whisper group " + id + " destroyed.");
        }
    }

    public WhisperGroup findWhisperGroup(String id) {
        for (int i = 0; i < whisperGroups.size(); i++) {
            WhisperGroup whisperGroup =
                (WhisperGroup)whisperGroups.get(i);

            if (whisperGroup.getId().equals(id)) {
                return whisperGroup;
            }
        }

        return null;
    }

    public void setTransientWhisperGroup(String id, boolean isTransient) 
        throws ParseException {

        synchronized(whisperGroups) {
            WhisperGroup whisperGroup = findWhisperGroup(id);

            if (whisperGroup == null) {
                Logger.println("Whisper group " + id + " doesn't exist");

                throw new ParseException("Whisper group "
                    + id + " doesn't exist", 0);
            }

        whisperGroup.setTransient(isTransient);
    }
    }

    public void setLockedWhisperGroup(String id, boolean isLocked) 
        throws ParseException {

        synchronized(whisperGroups) {
            WhisperGroup whisperGroup = findWhisperGroup(id);

            if (whisperGroup == null) {
                Logger.println("Whisper group " + id + " doesn't exist");

                throw new ParseException("Whisper group "
                    + id + " doesn't exist", 0);
            }

        whisperGroup.setLocked(isLocked);
    }
    }

    public void setWhisperGroupAttenuation(String id, double attenuation) 
        throws ParseException {

        synchronized(whisperGroups) {
            WhisperGroup whisperGroup = findWhisperGroup(id);

            if (whisperGroup == null) {
                Logger.println("Whisper group " + id + " doesn't exist");

                throw new ParseException("Whisper group "
                    + id + " doesn't exist", 0);
            }

        whisperGroup.setAttenuation(attenuation);
    }
    }

    public void setWhisperGroupNoCommonMix(String id, boolean noCommonMix) 
        throws ParseException {

        synchronized(whisperGroups) {
            WhisperGroup whisperGroup = findWhisperGroup(id);

            if (whisperGroup == null) {
                Logger.println("Whisper group " + id + " doesn't exist");

                throw new ParseException("Whisper group "
                    + id + " doesn't exist", 0);
            }

        whisperGroup.setNoCommonMix(noCommonMix);
    }
    }

    public void addConferenceTreatment(TreatmentManager treatmentManager) {
    conferenceWhisperGroup.addTreatment(treatmentManager);
    }

    public void pauseConferenceTreatment(String treatment, boolean isPaused) {
    conferenceWhisperGroup.pauseTreatment(treatment, isPaused);
    }

    public void removeConferenceTreatment(String treatment) {
    conferenceWhisperGroup.removeTreatment(treatment);
    }

    public void recordConference(boolean enabled, String recordingFile,
        String recordingType) throws IOException {

    conferenceWhisperGroup.recordConference(enabled, recordingFile,
        recordingType);
    }

    public String getRecordingFile() {
    return conferenceWhisperGroup.getRecordingFile();
    }

    public String getAbbreviatedWhisperGroupInfo(boolean showMembers) {
        String s = "";

        synchronized(whisperGroups) {
            for (int i = 0; i < whisperGroups.size(); i++) {
                WhisperGroup whisperGroup = (WhisperGroup)
                    whisperGroups.get(i);

                s += whisperGroup.toAbbreviatedString(showMembers) + "\n";
            }
        }
        return s;
    }

    public String getAbbreviatedWhisperGroupInfo(
        ConferenceMember member, boolean showMembers) {

        synchronized(whisperGroups) {
            String s = "";

            for (int i = 0; i < whisperGroups.size(); i++) {
                WhisperGroup whisperGroup =
                    (WhisperGroup)whisperGroups.get(i);

                if (whisperGroup.isMember(member)) {
                    s += " " + whisperGroup.toAbbreviatedString(showMembers);
                }
        }

        return s;
    }
    }

    public String getWhisperGroupInfo() {
        String s = "";

        synchronized(whisperGroups) {
            for (int i = 0; i < whisperGroups.size(); i++) {
                WhisperGroup whisperGroup = (WhisperGroup)
                    whisperGroups.get(i);

                s += whisperGroup + "\n";
            }
        }
        return s;
    }

    public String getWhisperGroupInfo(ConferenceMember member) {
    synchronized(whisperGroups) {
        String s = "";

        for (int i = 0; i < whisperGroups.size(); i++) {
            WhisperGroup whisperGroup = 
            (WhisperGroup)whisperGroups.get(i);

            if (whisperGroup.isMember(member)) {
            s += " " + whisperGroup;
        }
        }
        return s;
    }
    }

}
