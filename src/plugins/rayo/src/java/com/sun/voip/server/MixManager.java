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

import java.lang.reflect.Constructor;

import java.util.ArrayList;

import com.sun.voip.AudioConversion;
import com.sun.voip.Logger;
import com.sun.voip.SpatialAudio;
import com.sun.voip.MixDataSource;
import com.sun.voip.RtpPacket;

public class MixManager {
    private static final int MAX_VOLUME = 16;

    private ArrayList mixDescriptors = new ArrayList();

    private ConferenceMember member;
    private int conferenceSamplesPerPacket;
    private int channels;

    private boolean useFastMix = false;

    private SpatialAudio sa;

    public MixManager(ConferenceMember member, 
	    int conferenceSamplesPerPacket, int channels) {

	this.member = member;
	this.conferenceSamplesPerPacket = conferenceSamplesPerPacket;
	this.channels = channels;

	/*
	 * Calculate the sample rate.
	 * Each packet has 20ms of data (50 packets per second).
	 */
	int sampleRate = 50 * (conferenceSamplesPerPacket / channels);

	sa = getSpatialAudio();

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("Using spatial audio module:  " + sa);
	}

	sa.initialize(member.getConferenceManager().getId(),
	    member.getCallParticipant().getCallId(), sampleRate, 
	    channels, conferenceSamplesPerPacket / channels);
    }

    private SpatialAudio getSpatialAudio() {
        String s = System.getProperty("com.sun.voip.server.SPATIAL_AUDIO");

        if (s != null) {
            try {
                Class micClass = Class.forName(s);
                Class[] params = new Class[] { };

                Constructor constructor = micClass.getConstructor(params);

                if (constructor != null) {
                    Object[] args = new Object[] { };

                    return (SpatialAudio) constructor.newInstance(args);
                }

                Logger.println("constructor not found for: " + s);
            } catch (Exception e) {
                Logger.println("Error loading '" + s + "': "
                    + e.getMessage());
            }
	}

    	return new SunSpatialAudio();
    }

    public void addMix(MixDescriptor mixDescriptor) {
	MixDataSource mixDataSource = mixDescriptor.getMixDataSource();

	if (mixDataSource instanceof WhisperGroup) {
	    WhisperGroup wg = (WhisperGroup) mixDataSource;

	    if (wg.hasCommonMix() == false) {
		if (Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("No common mix, not adding " + wg);
		}
		return;
	    }
	}

	MixDescriptor md = findMixDescriptor(mixDataSource);

	if (md != null) {
	    removeMix(md);
	}

	mixDescriptors.add(mixDescriptor);
	setUseFastMix();
    }
	
    public void addMix(MixDataSource mixDataSource, double attenuation) {
	MixDescriptor mixDescriptor = findMixDescriptor(mixDataSource);

	if (attenuation == 0) {
	    if (mixDescriptor != null) {
	    	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    Logger.println("Call " + member
		        + " Remove mix, volume 0 " + " mixDataSource " 
			+ mixDataSource);
		}
		removeMix(mixDescriptor);
	    } else {
	        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    Logger.println("Call " + member
			+ " no need to add " + mixDataSource + " volume 0");
		}
	    }

	    setUseFastMix();
	    return;
	}

	if (mixDescriptor == null) {
	    mixDescriptor = new MixDescriptor(mixDataSource, attenuation);
	    mixDescriptors.add(mixDescriptor);

	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		Logger.println("created new mix for " + mixDataSource 
		    + " " + attenuation);
	    }

	    setUseFastMix();
	    return;
	}

	mixDescriptor.setAttenuation(attenuation);
	setUseFastMix();
    }
    
    public void removeMix(MixDataSource mixDataSource) {
	MixDescriptor mixDescriptor = findMixDescriptor(mixDataSource);

	if (mixDescriptor == null) {
	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	        Logger.println("Didn't find MixDescriptor for "
		    + mixDataSource);
	    }
	    return;
	}

	removeMix(mixDescriptor);
    }

    public void removeMix(MixDescriptor mixDescriptor) {
	mixDescriptors.remove(mixDescriptor);

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Call " + member
		+ " removeMix removed " + mixDescriptor);
	}
	setUseFastMix();
    }

    public void setAttenuation(MixDescriptor md, double attenuation) {
	md.setAttenuation(attenuation);

	setUseFastMix();
    }

    public void setMuted(MixDescriptor md, boolean isMuted) {
	md.setMuted(isMuted);
	setUseFastMix();
    }

    public MixDescriptor findMixDescriptor(MixDataSource mixDataSource) {
        for (int i = 0; i < mixDescriptors.size(); i++) {
            MixDescriptor mixDescriptor = (MixDescriptor) mixDescriptors.get(i);

            if (mixDescriptor.getMixDataSource() == mixDataSource) {
                return mixDescriptor;
            }
        }

        if (forcePrivateMix) {
            double[] volume = new double[4];

            volume[0] = .5D;
            volume[1] = .5D;
            volume[2] = .5D;
            volume[3] = .5D;

            return new MixDescriptor(mixDataSource, 1.0, volume);
        }

        return null;
    }

    private void setUseFastMix() {
        useFastMix = false;

	if (mixDescriptors.size() != 2) {
	    if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
	        Logger.println("Call " + member +
		    " Can't use fastMix, must have exactly 2 descriptors");
	    }
	    return;
	}
	    
	for (int i = 0; i < 2; i++) {
	    MixDescriptor mixDescriptor = (MixDescriptor)
	        mixDescriptors.get(i);

	    if (mixDescriptor.isMuted()) {
	        if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
		    Logger.println("Call " + member
			+ " Can't use fastMix, md muted " + mixDescriptor);
		}
		return;
	    }

	    MixDataSource mixDataSource = mixDescriptor.getMixDataSource();

	    if (mixDataSource instanceof MemberReceiver) {
	        if (mixDataSource != member.getMemberReceiver()) {
	    	    if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
		    	Logger.println("Call " + member
			    + " Can't use fastMix, have private mix");
		    }
		    return;
	        }

	        if (mixDescriptor.getEffectiveVolume() != -1.0) {
	    	    if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
		        Logger.println("Call " + member
			    + " Can't use fastMix, no mix minus");
		    }
		    return;
	        }

		continue;
	    } else if (mixDataSource instanceof WhisperGroup == false) {
	        if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
		    Logger.println("Call " + member
			+ " Can't use fastMix, not simple mix");
		}
		return;
	    }

	    if (mixDescriptor.isNop() == false) {
	        if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
		    Logger.println(" Call " + member
			+ "Can't use fastMix, not spatially neutral");
		}
		return;
	    }
        }

	MixDescriptor mixDescriptor = (MixDescriptor) mixDescriptors.get(0);

        if (mixDescriptor.getMixDataSource() instanceof MemberReceiver) {
	    /*
	     * Swap the two descriptors so that the conference mix is first
	     */
	    mixDescriptors.add(mixDescriptor);
	    mixDescriptors.remove(0);
	}

	if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
	    Logger.println("Using fastMix");
	}

	useFastMix = true;
    }

    public void showDescriptors() {
        Logger.println("Call " + member + " descriptors "
            + mixDescriptors.size());

        for (int i = 0; i < mixDescriptors.size(); i++) {
            MixDescriptor mixDescriptor = (MixDescriptor) mixDescriptors.get(i);

	    Logger.println(mixDescriptor.toString());
        }
    }

    public ArrayList getMixDescriptors() {
        return mixDescriptors;
    }

    /*
     * For load testing
     */
    private static boolean forcePrivateMix = false;

    public static void setForcePrivateMix(boolean forcePrivateMix) {
	MixManager.forcePrivateMix = forcePrivateMix;
    }

    public static boolean getForcePrivateMix() {
	return forcePrivateMix;
    }

    public MixDescriptor setPrivateMix(MixDataSource mixDataSource, 
	    double[] spatialValues) {

        MixDescriptor mixDescriptor;

	mixDescriptor = findMixDescriptor(mixDataSource);

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("Private mix " + mixDescriptor);
	}

	if (mixDescriptor == null) {
	    mixDescriptor = new MixDescriptor(mixDataSource, 1.0, 
		spatialValues);

	    mixDescriptors.add(mixDescriptor);

	    Logger.println("Call " + member
		+ " creating new private mix for " + mixDataSource + " "
		+ mixDescriptor + " vol " + spatialValues[3]);
	} else {
	    if (mixDescriptor.equals(mixDataSource, spatialValues)) {
		return null;	// same as before
	    }

	    mixDescriptor.setSpatialValues(spatialValues);
	}

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("MixManager:  Setting private mix " 
		+ mixDescriptor);
	}

	setUseFastMix();
	return mixDescriptor;
    }

    public int[] mix() {
        int[] outData = null;

        if (mixDescriptors.size() == 0) {
            return null;
        }

        if (useFastMix) {
            if (Logger.logLevel >= Logger.LOG_INFO) {
                setUseFastMix();    // reset

                if (useFastMix == false) {
                    Logger.println("Call " + member
			+ " useFastMix should have been false!  "
			+ "resetting...");

                    Logger.println("Call " + member + " md size "
			+ mixDescriptors.size());

		    Logger.println(toAbbreviatedString());
                }
            }

	    if (useFastMix) {
		return fastMix();
	    }
	}

        outData = new int[conferenceSamplesPerPacket];

	//Logger.println("Call " + member + " MixManager mixing " 
	//	+ mixDescriptors.size());

	boolean needToSend = false;

        for (int i = 0; i < mixDescriptors.size(); i++) {
            MixDescriptor mixDescriptor = (MixDescriptor) mixDescriptors.get(i);

	    if (mixDescriptor.isMuted())  {
		continue;
	    }

	    MixDataSource mixDataSource = mixDescriptor.getMixDataSource();

            int[] contribution = mixDataSource.getCurrentContribution();

	    if (mixDescriptor.isPrivateMix() == true) {
		double[] spatialValues = mixDescriptor.getSpatialValues();

		if (MixDescriptor.isSpatiallyNeutral(spatialValues) &&
			spatialValues[3] != 0) {

		    /*
		     * Since only the volume needs to be adjusted,
		     * rather than subtracting out the contribution
		     * and then adding in the volume we can
		     * set the volume to volume - 1 and add that in.
		     */
		    if (mixDataSource.contributionIsInCommonMix()) {
		        double[] sv = new double[4];

		        sv[0] = spatialValues[0];
		        sv[1] = spatialValues[1];
		        sv[2] = spatialValues[2];
		        sv[3] = spatialValues[3] - 1;

		        spatialValues = sv;

		        if (Logger.logLevel == -69) {
			    Logger.println("Call " + member + " pm for " 
			        + mixDataSource.toAbbreviatedString()
			        + " s3 " + spatialValues[3]);
			}
		    }
		} else {
		    /*
		     * Subtract the current contribution from the mix
		     */
		    if (contribution != null && 
			    mixDataSource.contributionIsInCommonMix()) {

            		WhisperGroup.mixData(contribution, outData, false);

		        if (spatialValues[3] == 0) {
			    if (Logger.logLevel == -44) {
				Logger.println("subtracted out "
			            + mixDescriptor);
			    }
			    continue;  // we've already subtracted it out
		        }
		    }
		}

		contribution = sa.generateSpatialAudio(
		    mixDataSource.getSourceId(), 
                    mixDataSource.getPreviousContribution(),
		    contribution, spatialValues);
	    }

            if (contribution != null) {
		/*
		 * Mix into an int[] so that we can clip once after
		 * we're done mixing.
		 */
		boolean add = mixDescriptor.getEffectiveVolume() != -1;

            	WhisperGroup.mixData(contribution, outData, add);
		needToSend = true;
            }
        }

	if (needToSend == false) {
	    return null;
	}

	AudioConversion.clip(outData);

	if (Logger.logLevel == -39) {
	    checkData(outData, false);
	}

        return outData;
    }

    /*
     * We know there are two MixDescriptors and the first one is 
     * the conference Mix and the second one is for subtracting 
     * out the member's own data.
     */
    private int[] fastMix() {
	MixDescriptor conferenceMixDescriptor = (MixDescriptor)
	    mixDescriptors.get(0);

        int[] conferenceMixContribution =
	    conferenceMixDescriptor.getMixDataSource().getCurrentContribution();

        if (conferenceMixContribution == null) {
	    return null;
	}

	int[] outData = new int[conferenceSamplesPerPacket];

	MixDescriptor memberMixDescriptor = (MixDescriptor)
	    mixDescriptors.get(1);

        int[] memberContribution =
            memberMixDescriptor.getMixDataSource().getCurrentContribution();

        if (memberContribution == null) {
	    System.arraycopy(conferenceMixContribution, 0, outData, 0,
		conferenceMixContribution.length);
		
	    if (Logger.logLevel == -39) {
                checkData(outData, useFastMix);
            }

	    AudioConversion.clip(outData);
            return outData;
        }

	WhisperGroup.mixData(conferenceMixContribution, memberContribution, 
	    outData);

	if (Logger.logLevel == -39) {
            checkData(outData, useFastMix);
        }

        AudioConversion.clip(outData);
	return outData;
    }

    private void checkData(int[] data, boolean useFastMix) {
	for (int i = 0; i < data.length; i++) {
	    if (data[i] != 0) {
		Logger.println("Call " + member + " Non-zero data at " + i);
		Logger.println("Call " + member
		    + " useFastMix " + useFastMix);
		Logger.println("Call " + member + " " 
		    + toAbbreviatedString());
		if (mixDescriptors.size() != 2 && useFastMix == true) {
		    Logger.println("useFastMix should be false!!!");
		}
		break;
	    }
	}
    }

    //public void adjustVolume(MixDescriptor md, double volume) {
    //	md.adjustSpatialVolume(volume);
    //}

    public void adjustVolume(int[] data, double volume) {
	if (volume == 1) {
	    return;
	}

	if (volume == 0) {
	    for (int i = 0; i < data.length; i++) {
		data[i] = 0;	// optimize when volume is 0
	    }
	    return; 
	}

	for (int i = 0; i < data.length; i++) {
            data[i] = AudioConversion.clip((int)(data[i] * volume));
        }
    }

    public String toString() {
        String s = "";

        synchronized (this) {
            for (int i = 0; i < mixDescriptors.size(); i++) {
                MixDescriptor mixDescriptor = (MixDescriptor)
                    mixDescriptors.get(i);

                s += mixDescriptor.toString();
                s += "\n";
            }
        }

        return s;
    }

    public String toAbbreviatedString() {
        String s = "";

        synchronized (this) {
            for (int i = 0; i < mixDescriptors.size(); i++) {
                MixDescriptor mixDescriptor = (MixDescriptor)
                    mixDescriptors.get(i);

                s += "    " + mixDescriptor.toAbbreviatedString();

		if (member.getWhisperGroup() == 
		        mixDescriptor.getMixDataSource()) {
		
		    s += " + ";
		}

                s += "\n";
            }
        }

        return s;
    }

}
