/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.entitycaps;

import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
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
// TODO: Instances of this class should not be cached in distributed caches. The overhead of distributing data is a lot higher than recalculating the hash on every cluster node. We should remove the Externalizable interface, and turn this class into an immutable class.
public class EntityCapabilities implements Cacheable, Externalizable {

    /**
     * Identities included in these entity capabilities.
     */
    private Set<String> identities = new HashSet<>();

    /**
     * Features included in these entity capabilities.
     */
    private Set<String> features = new HashSet<>();

    /**
     * Hash string that corresponds to the entity capabilities. To be
     * regenerated and used for discovering potential poisoning of entity
     * capabilities information.
     */
    private String verAttribute;

    /**
     * The hash algorithm that was used to create the hash string.
     */
    private String hashAttribute;
    
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
     * Returns the identities of the entity capabilities.
     *
     * @return all identities.
     */
    public Set<String> getIdentities()
    {
        return identities;
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
     * Returns the features of the entity capabilities.
     *
     * @return all features.
     */
    public Set<String> getFeatures()
    {
        return features;
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

    void setVerAttribute(String verAttribute) {
        this.verAttribute = verAttribute;
    }
    
    String getVerAttribute() {
        return this.verAttribute;
    }

    void setHashAttribute(String hashAttribute) {
        this.hashAttribute = hashAttribute;
    }

    String getHashAttribute() {
        return this.hashAttribute;
    }
    
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ExternalizableUtil.getInstance().readStrings(in, identities);
        ExternalizableUtil.getInstance().readStrings(in, features);
        verAttribute = ExternalizableUtil.getInstance().readSafeUTF(in);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeStrings(out, identities);
        ExternalizableUtil.getInstance().writeStrings(out, features);
        ExternalizableUtil.getInstance().writeSafeUTF(out, verAttribute);
    }

    @Override
    public int getCachedSize() throws CannotCalculateSizeException {
        int size = CacheSizes.sizeOfCollection(identities);
        size += CacheSizes.sizeOfCollection(features);
        size += CacheSizes.sizeOfString(verAttribute);
        return size;
    }
}
