/**
 * $Revision$
 * $Date$
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

package org.jivesoftware.util;

/**
 * Holds version information for Openfire.
 *
 * @author Iain Shigeoka
 */
public class Version {

    /**
     * The major number (ie 1.x.x).
     */
    private int major;

    /**
     * The minor version number (ie x.1.x).
     */
    private int minor;

    /**
     * The micro version number (ie x.x.1).
     */
    private int micro;

    /**
     * A status release number or -1 to indicate none.
     */
    private int statusVersion;

    /**
     * The release state of the product (Release, Release Candidate).
     */
    private ReleaseStatus status;

    /**
     * Cached version string information
     */
    private String versionString;

    /**
     * Create a new version information object.
     *
     * @param major the major release number.
     * @param minor the minor release number.
     * @param micro the micro release number.
     * @param status the status of the release.
     */
    public Version(int major, int minor, int micro, ReleaseStatus status, int statusVersion) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.status = status;
        this.statusVersion = statusVersion;
        if (status != null) {
            if (status == ReleaseStatus.Release) {
                versionString = major + "." + minor + "." + micro;
            }
            else {
                if (statusVersion >= 0) {
                    versionString = major + "." + minor + "." + micro + " " + status.toString() +
                            " " + statusVersion;
                }
                else {
                    versionString = major + "." + minor + "." + micro + " " + status.toString();
                }
            }
        }
        else {
            versionString = major + "." + minor + "." + micro;
        }
    }

    /**
     * Returns the version number of this instance of Openfire as a
     * String (ie major.minor.revision).
     *
     * @return The version as a string
     */
    public String getVersionString() {
        return versionString;
    }

    /**
     * Returns the release status of this product.
     *
     * @return the release status of this product.
     */
    public ReleaseStatus getStatus() {
        return status;
    }

    /**
     * Obtain the major release number for this product.
     *
     * @return The major release number 1.x.x
     */
    public int getMajor() {
        return major;
    }

    /**
     * Obtain the minor release number for this product.
     *
     * @return The minor release number x.1.x
     */
    public int getMinor() {
        return minor;
    }

    /**
     * Obtain the micro release number for this product.
     *
     * @return The micro release number x.x.1
     */
    public int getMicro() {
        return micro;
    }

    /**
     * Obtain the status relase number for this product. For example, if
     * the release status is <strong>alpha</strong> the release may be <strong>5</strong>
     * resulting in a release status of <strong>Alpha 5</strong>.
     *
     * @return The status version or -1 if none is set.
     */
    public int getStatusVersion() {
        return statusVersion;
    }

    /**
     * A class to represent the release status of the server. Product releases
     * are indicated by type safe enum constants.
     */
    public enum ReleaseStatus {
        Release(""), Release_Candidate("RC"), Beta("Beta"), Alpha("Alpha");

        private String status;

        private ReleaseStatus(String status) {
            this.status = status;
        }

        public String toString() {
            return status;
        }
    }
}
