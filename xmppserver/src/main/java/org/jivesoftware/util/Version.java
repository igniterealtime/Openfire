/*
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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds version information for Openfire.
 *
 * @author Iain Shigeoka
 */
public final class Version implements Comparable<Version> {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:\\s+(\\w+))?(?:\\s+(\\d+))?");
    private static final Pattern FOUR_DOT_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("(?i)(\\d+)\\.(\\d+)\\.(\\d+)(?:\\.(\\d+))?-SNAPSHOT");

    /**
     * The major number (ie 1.x.x).
     */
    private final int major;

    /**
     * The minor version number (ie x.1.x).
     */
    private final int minor;

    /**
     * The micro version number (ie x.x.1).
     */
    private final int micro;

    /**
     * A status release number or -1 to indicate none.
     */
    private final int statusVersion;

    /**
     * The release state of the product (Release, Release Candidate).
     */
    private final ReleaseStatus status;

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
     * @param statusVersion status release number or -1 to indicate none.
     */
    public Version(int major, int minor, int micro, ReleaseStatus status, int statusVersion) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.status = status == null ? ReleaseStatus.Release : status;
        this.statusVersion = statusVersion;
    }
    
    /**
     * Create a new version from a simple version string (e.g. "3.9.3")
     * 
     * @param source the version string
     */
    public Version(CharSequence source) {
        if (source != null) {
            Matcher matcher = PATTERN.matcher(source);
            if (matcher.matches()) {
                major = Integer.parseInt(matcher.group(1));
                minor = Integer.parseInt(matcher.group(2));
                micro = Integer.parseInt(matcher.group(3));
                String status = matcher.group(4);
                if (status != null) {
                    switch (status.toLowerCase()) {
                        case "rc":
                            this.status = ReleaseStatus.Release_Candidate;
                            break;
                        case "beta":
                            this.status = ReleaseStatus.Beta;
                            break;
                        case "alpha":
                            this.status = ReleaseStatus.Alpha;
                            break;
                        default:
                            this.status = ReleaseStatus.Release;
                    }
                } else {
                    this.status = ReleaseStatus.Release;
                }
                String statusVersion = matcher.group(5);
                if (statusVersion != null) {
                    this.statusVersion = Integer.parseInt(statusVersion);
                } else {
                    this.statusVersion = -1;
                }
                return;
            }
            final Matcher snapshotMatcher = SNAPSHOT_PATTERN.matcher(source);
            if (snapshotMatcher.matches()) {
                major = Integer.parseInt(snapshotMatcher.group(1));
                minor = Integer.parseInt(snapshotMatcher.group(2));
                micro = Integer.parseInt(snapshotMatcher.group(3));
                status = ReleaseStatus.Snapshot;
                final String statusVersionString = snapshotMatcher.group(4);
                statusVersion = statusVersionString == null ? -1 : Integer.parseInt(statusVersionString);
                return;
            }
            final Matcher fourDotMatcher = FOUR_DOT_PATTERN.matcher(source);
            if (fourDotMatcher.matches()) {
                major = Integer.parseInt(fourDotMatcher.group(1));
                minor = Integer.parseInt(fourDotMatcher.group(2));
                micro = Integer.parseInt(fourDotMatcher.group(3));
                statusVersion = Integer.parseInt(fourDotMatcher.group(4));
                status = ReleaseStatus.Release;
                return;
            }
        }
        this.major = this.minor = this.micro = 0;
        this.statusVersion = -1;
        this.status = ReleaseStatus.Release;
    }

    /**
     * Returns the version number of this instance of Openfire as a
     * String (ie major.minor.revision).
     *
     * @return The version as a string
     */
    public String getVersionString() {
        if (versionString == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(major).append('.').append(minor).append('.').append(micro);
            if(status == ReleaseStatus.Snapshot) {
                if (statusVersion >= 0) {
                    sb.append('.').append(statusVersion);
                }
                sb.append("-SNAPSHOT");
            } else if (status != ReleaseStatus.Release || statusVersion != -1) {
                sb.append(' ').append(status);
                if (statusVersion >= 0) {
                    sb.append(' ').append(statusVersion);
                }
            }
            versionString = sb.toString();
        }
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
     * Obtain the status release number for this product. For example, if
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
        Release("Release"), Release_Candidate("RC"), Beta("Beta"), Alpha("Alpha"), Snapshot("Snapshot");

        private String status;

        ReleaseStatus(String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return status;
        }
    }
    
    /**
     * Convenience method for comparing versions
     * 
     * @param otherVersion a version to compare against
     * @return {@code true} if this version is newer, otherwise {@code false}
     */
    public boolean isNewerThan(Version otherVersion) {
        return this.compareTo(otherVersion) > 0;
    }

    @Override
    public int compareTo(Version that) {
        if (that == null) {
            return 1;
        }
        int result = Integer.compare(getMajor(), that.getMajor());
        if (result == 0) {
            result = Integer.compare(getMinor(), that.getMinor());
            if (result == 0) {
                result = Integer.compare(getMicro(), that.getMicro());
                if (result == 0) {
                    result = that.getStatus().compareTo(getStatus());
                    if (result == 0) {
                        result = Integer.compare(getStatusVersion(), that.getStatusVersion());
                    }
                }
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Version)) {
            return false;
        }
        Version other = (Version) o;

        return Objects.equals(major, other.major)
                && Objects.equals(minor, other.minor)
                && Objects.equals(micro, other.micro)
                && Objects.equals(statusVersion, other.statusVersion)
                && Objects.equals(status, other.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, micro, statusVersion, status);
    }

    @Override
    public String toString() {
        return getVersionString();
    }

    public Version ignoringReleaseStatus() {
        return new Version(major, minor, micro, null, -1 );
    }
}
