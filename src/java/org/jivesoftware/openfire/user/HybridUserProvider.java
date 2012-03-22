/*
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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
 *
 * HybridUserProvider.java
 *
 * Created on 16. April 2007, 21:48
 * by Marc Seeger
 * code works fine as far as my 10 User Test-Server goes
 * It basically checks different userproviders which are being set in the configuration xml file
 * I use it in combination with hybridauth providers to be able to get the usual users from ldap but still have some Bots in MySQL
 *
 * Changed on 14. Nov. 2007, 10:48
 * by Chris Neasbitt
 *  -changed getUsers(int startIndex, int numResults) method to return a subset of the total users from all providers
 *  -changed the getUsers() method to use a vector internally since addAll is an optional method of the collection
 *   interface we cannot assume that all classes that support the collection interface also support the addAll method
 *  -changed the getUserCount() method to iterate through an array of providers while calling a private helper method
 *   getUserCount(UserProvider provider) on each of them.
 */

package org.jivesoftware.openfire.user;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Marc Seeger
 * @author Chris Neasbitt
 */

public class HybridUserProvider implements UserProvider {

	private static final Logger Log = LoggerFactory.getLogger(HybridUserProvider.class);

    private UserProvider primaryProvider = null;
    private UserProvider secondaryProvider = null;
    private UserProvider tertiaryProvider = null;
    private UserProvider[] userproviders = {primaryProvider, secondaryProvider, tertiaryProvider};

    private Set<String> primaryOverrides = new HashSet<String>();
    private Set<String> secondaryOverrides = new HashSet<String>();
    private Set<String> tertiaryOverrides = new HashSet<String>();


    public HybridUserProvider() {
// Load primary, secondary, and tertiary user providers.
        String primaryClass = JiveGlobals.getXMLProperty("hybridUserProvider.primaryProvider.className");
        if (primaryClass == null) {
            Log.error("A primary UserProvider must be specified in the openfire.xml.");
            return;
        }
        try {
            Class c = ClassUtils.forName(primaryClass);
            primaryProvider = (UserProvider) c.newInstance();
            Log.debug("Primary user provider: " + primaryClass);
        } catch (Exception e) {
            Log.error("Unable to load primary user provider: " + primaryClass +
                    ". Users in this provider will be disabled.", e);
            return;
        }
        String secondaryClass = JiveGlobals.getXMLProperty("hybridUserProvider.secondaryProvider.className");
        if (secondaryClass != null) {
            try {
                Class c = ClassUtils.forName(secondaryClass);
                secondaryProvider = (UserProvider) c.newInstance();
                Log.debug("Secondary user provider: " + secondaryClass);
            } catch (Exception e) {
                Log.error("Unable to load secondary user provider: " + secondaryClass, e);
            }
        }
        String tertiaryClass = JiveGlobals.getXMLProperty("hybridUserProvider.tertiaryProvider.className");
        if (tertiaryClass != null) {
            try {
                Class c = ClassUtils.forName(tertiaryClass);
                tertiaryProvider = (UserProvider) c.newInstance();
                Log.debug("Tertiary user provider: " + tertiaryClass);
            } catch (Exception e) {
                Log.error("Unable to load tertiary user provider: " + tertiaryClass, e);
            }
        }

        // Now, load any overrides.
        String overrideList = JiveGlobals.getXMLProperty(
                "hybridUserProvider.primaryProvider.overrideList", "");
        for (String user : overrideList.split(",")) {
            primaryOverrides.add(user.trim().toLowerCase());
        }

        if (secondaryProvider != null) {
            overrideList = JiveGlobals.getXMLProperty(
                    "hybridUserProvider.secondaryProvider.overrideList", "");
            for (String user : overrideList.split(",")) {
                secondaryOverrides.add(user.trim().toLowerCase());
            }
        }

        if (tertiaryProvider != null) {
            overrideList = JiveGlobals.getXMLProperty(
                    "hybridUserProvider.tertiaryProvider.overrideList", "");
            for (String user : overrideList.split(",")) {
                tertiaryOverrides.add(user.trim().toLowerCase());
            }
        }

    }


    public User createUser(String username, String password, String name, String email) throws UserAlreadyExistsException {
        //initialize our returnvalue
        User returnvalue = null;

        //try to use the providers to create a user and change the return value to that user
        if (!primaryProvider.isReadOnly()) {
            try {
                returnvalue = primaryProvider.createUser(username, password, name, email);
            }

            finally {
            }

        } else if (secondaryProvider != null) {
            if (!secondaryProvider.isReadOnly()) {
                try {
                    returnvalue = secondaryProvider.createUser(username, password, name, email);
                }

                finally {
                }

            }
        } else if (tertiaryProvider != null) {
            if (!tertiaryProvider.isReadOnly()) {
                try {
                    returnvalue = tertiaryProvider.createUser(username, password, name, email);
                }

                finally {
                }
            }
        }

        //return our created user
        if (returnvalue != null) {
            return returnvalue;
        } else {
            throw new UnsupportedOperationException();
        }
    }


    public void deleteUser(String username) {
        if (!primaryProvider.isReadOnly()) {
            try {
                primaryProvider.deleteUser(username);
                return;
            }

            finally {
            }

        } else if (secondaryProvider != null) {
            if (!secondaryProvider.isReadOnly()) {
                try {
                    secondaryProvider.deleteUser(username);
                    return;
                }

                finally {
                }

            }
        } else if (tertiaryProvider != null) {
            if (!tertiaryProvider.isReadOnly()) {
                try {
                    tertiaryProvider.deleteUser(username);
                    return;
                }

                finally {
                }

            } else {
// Reject the operation since all of the providers seem to be read-only
                throw new UnsupportedOperationException();
            }

        }
    }


    public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException {


        Collection<User> returnvalue = null;
        try {
            returnvalue = primaryProvider.findUsers(fields, query);
        }

        finally {
        }

        if (secondaryProvider != null) {
            try {

                returnvalue = secondaryProvider.findUsers(fields, query);
            }

            finally {
            }
        }
        if (tertiaryProvider != null) {
            try {
                returnvalue = tertiaryProvider.findUsers(fields, query);
            }

            finally {
            }
        }

        //return our collection of users
        if (returnvalue != null) {
            return returnvalue;
        } else {
            throw new UnsupportedOperationException();
        }
    }


    public Collection<User> findUsers(Set<String> fields, String query, int startIndex, int numResults) throws UnsupportedOperationException {
        Collection<User> returnvalue = null;
        try {
            returnvalue = primaryProvider.findUsers(fields, query, startIndex, numResults);
        }

        finally {
        }

        if (secondaryProvider != null) {
            try {

                returnvalue = secondaryProvider.findUsers(fields, query, startIndex, numResults);
            }

            finally {
            }
        }
        if (tertiaryProvider != null) {
            try {
                returnvalue = tertiaryProvider.findUsers(fields, query, startIndex, numResults);
            }

            finally {
            }
        }

        //return our Collection of Users
        if (returnvalue != null) {
            return returnvalue;
        } else {
            throw new UnsupportedOperationException();
        }
    }


    public Set<String> getSearchFields() throws UnsupportedOperationException {
        Set<String> returnvalue = null;
        try {
            returnvalue = primaryProvider.getSearchFields();
        }

        finally {
        }

        if (secondaryProvider != null) {
            try {

                returnvalue = secondaryProvider.getSearchFields();
            }

            finally {
            }
        }
        if (tertiaryProvider != null) {
            try {
                returnvalue = tertiaryProvider.getSearchFields();
            }

            finally {
            }
        }

        //return our Set of Strings
        if (returnvalue != null) {
            return returnvalue;
        } else {
            throw new UnsupportedOperationException();
        }
    }


    public int getUserCount() {
        int count = 0;
        for (UserProvider provider : userproviders) {
            count = count + this.getUserCount(provider);
        }
        return count;
    }

    private int getUserCount(UserProvider provider) {
        int returnvalue = 0;
        if (provider != null) {
            try {

                returnvalue = returnvalue + provider.getUserCount();
            }

            finally {
            }
        }
        return returnvalue;
    }


    public Collection<String> getUsernames() {
        Collection<String> returnvalue = null;
        try {
            returnvalue = primaryProvider.getUsernames();
        }

        finally {
        }

        if (secondaryProvider != null) {
            try {
                returnvalue.addAll(secondaryProvider.getUsernames());
            }

            finally {
            }
        }
        if (tertiaryProvider != null) {
            try {
                returnvalue.addAll(tertiaryProvider.getUsernames());
            }

            finally {
            }
        }

        //return our Set of Strings
        if (returnvalue != null) {
            return returnvalue;
        } else {
            throw new UnsupportedOperationException();
        }
    }


    public Collection<User> getUsers() {
        Vector<User> returnvalue = null;
        try {
            returnvalue = new Vector<User>(primaryProvider.getUsers());
        }

        finally {
        }

        if (secondaryProvider != null) {
            try {
                returnvalue.addAll(secondaryProvider.getUsers());
            }

            finally {
            }
        }
        if (tertiaryProvider != null) {
            try {
                returnvalue.addAll(tertiaryProvider.getUsers());
            }

            finally {
            }
        }

        //return our Set of Strings
        if (returnvalue != null) {
            return returnvalue;
        } else {
            throw new UnsupportedOperationException();
        }
    }

/*
*Changed by Chris Neasbitt to more accurately represent the intent of the method
*
*This method now removes a sub set of the combined users from all providers.  This
*is done in places as to avoid copying collections of users in memory.
*/

    public Collection<User> getUsers(int startIndex, int numResults) {
        Vector<User> returnresult = new Vector<User>();
        int numResultsLeft = numResults;
        int currentStartIndex = startIndex;
        for (UserProvider provider : userproviders) {
            if (numResultsLeft == 0) {
                break;
            }

            int pusercount = this.getUserCount(provider);

            if (pusercount == 0 || currentStartIndex >= pusercount) {

                currentStartIndex = currentStartIndex - pusercount;
                continue;

            } else {

                Collection<User> subresult = provider.getUsers(currentStartIndex, numResultsLeft);
                currentStartIndex = currentStartIndex - subresult.size();
                numResultsLeft = numResultsLeft - subresult.size();
                returnresult.addAll(subresult);
            }
        }

        return returnresult;
    }

    public boolean isReadOnly() {
        return false;
    }

    public boolean isNameRequired() {
        return false;
    }

    public boolean isEmailRequired() {
        return false;
    }

    public User loadUser(String username) throws UserNotFoundException {
        try {
            return primaryProvider.loadUser(username);

        }

        catch (Exception e) {
        }

        if (secondaryProvider != null) {
            try {

                return secondaryProvider.loadUser(username);
            }

            catch (Exception e) {
            }
        }


        if (tertiaryProvider != null) {
            try {
                return tertiaryProvider.loadUser(username);
            }

            catch (Exception e) {
            }
        }

        //if we get this far, no provider seems to successfully have loaded the user
        throw new UserNotFoundException();
    }

    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
        if (primaryProvider != null)
            try {
                primaryProvider.setCreationDate(username, creationDate);
                return;
            } catch (Exception e) {
            }

        if (secondaryProvider != null) {
            try {
                secondaryProvider.setCreationDate(username, creationDate);
                return;
            }

            catch (Exception e) {
            }
        }
        if (tertiaryProvider != null) {
            try {
                tertiaryProvider.setCreationDate(username, creationDate);
                return;
            }

            catch (Exception e) {
                throw new UserNotFoundException();
            }
        }


    }

    public void setEmail(String username, String email) throws UserNotFoundException {
        if (primaryProvider != null)
            try {
                primaryProvider.setEmail(username, email);
                return;
            } catch (Exception e) {
            }

        if (secondaryProvider != null) {
            try {
                secondaryProvider.setEmail(username, email);
                return;
            }

            catch (Exception e) {
            }
        }
        if (tertiaryProvider != null) {
            try {
                tertiaryProvider.setEmail(username, email);
                return;
            }

            catch (Exception e) {
                throw new UserNotFoundException();
            }
        }

    }


    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {

        //without it eclipse goes apeshit
        if (primaryProvider != null)
            //apeshit I say!


            try {
                primaryProvider.setModificationDate(username, modificationDate);
                return;
            } catch (Exception e) {
            }

        if (secondaryProvider != null) {
            try {
                secondaryProvider.setModificationDate(username, modificationDate);
                return;
            }

            catch (Exception e) {
            }
        }
        if (tertiaryProvider != null) {
            try {
                tertiaryProvider.setModificationDate(username, modificationDate);
                return;
            }

            catch (Exception e) {
                throw new UserNotFoundException();
            }
        }


    }

    public void setName(String username, String name) throws UserNotFoundException {
        if (primaryProvider != null)
            try {
                primaryProvider.setName(username, name);
                return;
            } catch (Exception e) {
            }

        if (secondaryProvider != null) {
            try {
                secondaryProvider.setName(username, name);
                return;
            }

            catch (Exception e) {
            }
        }
        if (tertiaryProvider != null) {
            try {
                tertiaryProvider.setName(username, name);
                return;
            }

            catch (Exception e) {
                throw new UserNotFoundException();
            }
        }
    }
}

