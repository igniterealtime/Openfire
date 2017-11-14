/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
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
 * Holds version information for Java specification (a major and minor version, eg: 1.8).
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public final class JavaSpecVersion implements Comparable<JavaSpecVersion> {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)\\.(\\d+)");

    /**
     * The major number (ie 1.x).
     */
    private final int major;

    /**
     * The minor version number (ie x.8).
     */
    private final int minor;

    /**
     * Create a new version information object.
     *
     * @param major the major release number.
     * @param minor the minor release number.
     */
    public JavaSpecVersion( int major, int minor ) {
        this.major = major;
        this.minor = minor;
    }

    /**
     * Create a new version from a simple version string (e.g. "1.8")
     *
     * @param source the version string
     */
    public JavaSpecVersion( CharSequence source ) {
        if (source != null) {
            Matcher matcher = PATTERN.matcher(source);
            if (matcher.matches()) {
                major = Integer.parseInt(matcher.group(1));
                minor = Integer.parseInt(matcher.group(2));
            } else {
                this.major = this.minor = 0;
            }
        } else {
            this.major = this.minor = 0;
        }
    }

    /**
     * Returns the version number of this instance of Openfire as a
     * String (ie major.minor.revision).
     *
     * @return The version as a string
     */
    public String getVersionString() {
        return major + "." + minor;
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
     * Convenience method for comparing versions
     * 
     * @param otherVersion a verion to comapr against
     */
    public boolean isNewerThan(JavaSpecVersion otherVersion) {
        return this.compareTo(otherVersion) > 0;
    }

    @Override
    public int compareTo(JavaSpecVersion that) {
        if (that == null) {
            return 1;
        }
        int result = Integer.compare(getMajor(), that.getMajor());
        if (result == 0) {
            result = Integer.compare(getMinor(), that.getMinor());
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof JavaSpecVersion )) {
            return false;
        }
        JavaSpecVersion other = (JavaSpecVersion) o;

        return Objects.equals(major, other.major)
                && Objects.equals(minor, other.minor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor);
    }

    @Override
    public String toString() {
        return getVersionString();
    }
}
