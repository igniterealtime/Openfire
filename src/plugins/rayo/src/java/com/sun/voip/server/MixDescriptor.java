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
import com.sun.voip.MixDataSource;

/*
 * There is a MixDescriptor for each MixDataSource whose volume needs to be
 * adjusted.  A MixDataSource's volume needs to be adjusted if the 
 * MixDataSource is member in a whisper group or if a member has a 
 * custom mix volume for another member.
 */
class MixDescriptor {
    private static final String ZERO_VOLUME_PROPERTY =
    "com.sun.voip.server.ZERO_VOLUME";

    private static double zeroVolume = .0001;

    private MixDataSource mixDataSource;
    private double attenuation;

    /*
     * The spatial values consist of 4 doubles.
     * The first is frontBack with -1 being all the way back an +1 
     * all the way to the front.
     * The second double is leftRight with -1 all the way to the left
     * and +1 all the way to the right.
     * The third double is upDown with -1 all the way down and +1
     * all the way to the top.
     * The fourth double is the non-negative volume level.
     */
    private double[] spatialValues;

    private boolean isMuted;

    static {
    String s = System.getProperty(ZERO_VOLUME_PROPERTY);

    if (s != null) {
        try {
        zeroVolume = Double.parseDouble(s);

        if (zeroVolume < 0) {
            zeroVolume = 0;
        }
        } catch (NumberFormatException e) {
        Logger.println("Invalid zero volume:  " + s);
        }
    }
    }

    public MixDescriptor(MixDataSource mixDataSource, double attenuation) {
    this(mixDataSource, attenuation, null);
    }

    public MixDescriptor(MixDataSource mixDataSource, double attenuation,
        double[] spatialValues) {

    this.mixDataSource = mixDataSource;
    this.attenuation = attenuation;

    if (spatialValues != null) {
        this.spatialValues = new double[4];

        this.spatialValues[0] = spatialValues[0];
        this.spatialValues[1] = spatialValues[1];
        this.spatialValues[2] = spatialValues[2];
        this.spatialValues[3] = spatialValues[3];
    } else {
        this.spatialValues = null;
    }
    }

    public void setMixDataSource(MixDataSource mixDataSource) {
    this.mixDataSource = mixDataSource;
    }

    public MixDataSource getMixDataSource() {
    return mixDataSource;
    }

    public void setAttenuation(double attenuation) {
    this.attenuation = attenuation;
    }

    public double getAttenuation() {
    return attenuation;
    }

    //public void adjustSpatialVolume(double volume) {
    //	spatialValues[3] += volume;
    //}

    public boolean isMuted() {
    return isMuted;
    }

    public double getEffectiveVolume() {
    if (isMuted()) {
       return 0;
    }

    if (spatialValues != null) {
        return spatialValues[3] * attenuation;
    }

    return attenuation;
    }

    public void setMuted(boolean isMuted) {
    this.isMuted = isMuted;
    }

    public void setSpatialValues(double[] spatialValues) {
    this.spatialValues = spatialValues;
    }

    public boolean isPrivateMix() {
    return spatialValues != null;
    }

    public static boolean isZeroVolume(double volume) {
    return volume <= zeroVolume;
    }

    public boolean isNop() {
    return isNop(spatialValues, attenuation);
    }

    public static boolean isNop(double[] spatialValues, double attenuation) {
    if (spatialValues == null) {
        return attenuation == 1;
    }

    return isSpatiallyNeutral(spatialValues) && spatialValues[3] == 1
        && attenuation == 1;
    }

    public boolean isSpatiallyNeutral() {
    return isSpatiallyNeutral(spatialValues);
    }

    public static boolean isSpatiallyNeutral(double[] spatialValues) {
    if (spatialValues == null) {
        return true;
    }

    return spatialValues[0] == 0 &&
        spatialValues[1] == 0 &&
        spatialValues[2] == 0;
    }

    public double[] getSpatialValues() {
    return spatialValues;
    }

    public boolean equals(MixDataSource mixDataSource, 
        double[] spatialValues) {

    if (this.mixDataSource != mixDataSource) {
        return false;
    }

    if (this.spatialValues[3] == 0 && spatialValues[3] == 0) {
        return true;
    }

    return this.spatialValues[0] == spatialValues[0] &&
        this.spatialValues[1] == spatialValues[1] &&
        this.spatialValues[2] == spatialValues[2] &&
        this.spatialValues[3] == spatialValues[3];
    }

    public Object clone() {
    MixDescriptor mixDescriptor = new MixDescriptor(
        mixDataSource, attenuation, spatialValues);

    mixDescriptor.setMuted(isMuted);
    return mixDescriptor;
    }

    public String toString() {
    String s = "";

    if (mixDataSource != null) {
        s += mixDataSource;
    }

    s += values();

    return s;
    }

    public String toAbbreviatedString() {
        String s = "";

    if (mixDataSource != null) {
            s += mixDataSource.toAbbreviatedString();
    }

    s += values();
    
    return s;
    }

    private String values() {
    double frontBack = 1;
    double leftRight = 0;
    double upDown = 0;
    double volume = 1;

    if (spatialValues != null) {
        frontBack = spatialValues[0];
        leftRight = spatialValues[1];
        upDown = spatialValues[2];
        volume = spatialValues[3];
    }
        
    String s = "  attenuation = " + attenuation;

    s += ", frontBack = " + (Math.round(frontBack * 1000) / 1000.);

    s += ", leftRight = " + (Math.round(leftRight * 10000) / 10000.);

    s += ", upDown = " + (Math.round(upDown * 10000) / 10000.);

    s += ", volume = " + (Math.round(volume * 1000) / 1000.);

    s += ", effectiveVolume = " 
        + (Math.round(attenuation * volume * 1000) / 1000.);

    if (isMuted) {
        s += ", MUTED";
    }

    return s;
    }

}
