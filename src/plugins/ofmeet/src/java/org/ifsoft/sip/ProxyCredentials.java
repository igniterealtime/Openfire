/**
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ifsoft.sip;


public class ProxyCredentials
{
    private String xmppUserName = null;
    private String userName = null;
    private String userDisplay = null;
    private char[] password = null;
    private String authUserName = null;
    private String realm = null;
    private String proxy = null;
    private String host = null;
    private String name = null;

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public void setAuthUserName(String userName)
    {
        this.authUserName = userName;
    }

    public void setXmppUserName(String xmppUserName)
    {
        this.xmppUserName = xmppUserName;
    }

    public void setRealm(String realm)
    {
        this.realm = realm;
    }

    public void setProxy(String proxy)
    {
        this.proxy = proxy;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setUserDisplay(String userDisplay)
    {
        this.userDisplay = userDisplay;
    }

    public void setPassword(char[] passwd) {
        this.password = passwd;
    }

    public String getUserDisplay()
    {
        return userDisplay;
    }

    public String getUserName()
    {
        return this.userName;
    }

    public String getRealm()
    {
        return realm;
    }

    public String getProxy()
    {
        return proxy;
    }

    public String getHost()
    {
        return host;
    }

    public String getName()
    {
        return name;
    }

    public String getAuthUserName()
    {
        return this.authUserName;
    }

    public String getXmppUserName()
    {
        return this.xmppUserName;
    }

    public char[] getPassword() {
        return password;
	}

}
