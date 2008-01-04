/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.entitycaps;

import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

/**
 * Contains identities and supported features describing client capabilities
 * for an entity.
 * 
 * @author Armando Jagucki
 *
 */
public class EntityCapabilities implements Cacheable, Externalizable {

    /**
     * Identities included in these entity capabilities.
     */
    private Set<String> identities = new HashSet<String>();

    /**
     * Features included in these entity capabilities.
     */
    private Set<String> features = new HashSet<String>();

    /**
     * Hash string that corresponds to the entity capabilities. To be
     * regenerated and used for discovering potential poisoning of entity
     * capabilities information.
     */
    private String verAttribute;

    /**
     * Adds an identity to the entity capabilities.
     * 
     * @param identity the identity
     * @return true if the entity capabilities did not already include the
     *         identity
     */
    boolean addIdentity(String identity) {
        return identities.add(identity);
    }

    /**
     * Adds a feature to the entity capabilities.
     * 
     * @param feature the feature
     * @return true if the entity capabilities did not already include the
     *         feature
     */
    boolean addFeature(String feature) {
        return features.add(feature);
    }

    /**
     * Determines whether or not a given identity is included in these entity
     * capabilities.
     * 
     * @param category the category of the identity
     * @param type the type of the identity
     * @return true if identity is included, false if not
     */
    public boolean containsIdentity(String category, String type) {
        return identities.contains(category + "/" + type);
    }

    /**
     * Determines whether or not a given feature is included in these entity
     * capabilities.
     * 
     * @param feature the feature
     * @return true if feature is included, false if not
     */
    public boolean containsFeature(String feature) {
        return features.contains(feature);
    }

    /**
     * @param verAttribute the verAttribute to set
     */
    void setVerAttribute(String verAttribute) {
        this.verAttribute = verAttribute;
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ExternalizableUtil.getInstance().readStrings(in, identities);
        ExternalizableUtil.getInstance().readStrings(in, features);
        verAttribute = ExternalizableUtil.getInstance().readSafeUTF(in);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeStrings(out, identities);
        ExternalizableUtil.getInstance().writeStrings(out, features);
        ExternalizableUtil.getInstance().writeSafeUTF(out, verAttribute);
    }

    public int getCachedSize() {
        int size = CacheSizes.sizeOfCollection(identities);
        size += CacheSizes.sizeOfCollection(features);
        size += CacheSizes.sizeOfString(verAttribute);
        return size;
    }
}
