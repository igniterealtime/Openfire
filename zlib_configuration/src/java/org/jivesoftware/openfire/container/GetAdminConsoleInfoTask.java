/**
 * $Revision: $
 * $Date: $
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

package org.jivesoftware.openfire.container;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.*;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Task that will return the bind interface and ports being used by the admin
 * console of the node where the task will be executed. When the admin console
 * is binded to all network interfaces this task will try to find a valid IP
 * address that will work for the remote node.<p>
 *
 * A <tt>null</tt> bindInterface in the result of this task means that the task
 * failed to find a valid IP address where the admin console is listening.
 *
 * @author Gaston Dombiak
 */
public class GetAdminConsoleInfoTask implements ClusterTask {
    private String bindInterface;
    private int adminPort;
    private int adminSecurePort;
    private String adminSecret;


    public Object getResult() {
        return this;
    }

    public void run() {
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        AdminConsolePlugin adminConsolePlugin = ((AdminConsolePlugin) pluginManager.getPlugin("admin"));

        bindInterface = adminConsolePlugin.getBindInterface();
        adminPort = adminConsolePlugin.getAdminUnsecurePort();
        adminSecurePort = adminConsolePlugin.getAdminSecurePort();
        adminSecret = AdminConsolePlugin.secret;

        if (bindInterface == null) {
            Enumeration<NetworkInterface> nets = null;
            try {
                nets = NetworkInterface.getNetworkInterfaces();
            } catch (SocketException e) {
                // We failed to discover a valid IP address where the admin console is running
                return;
            }
            for (NetworkInterface netInterface : Collections.list(nets)) {
                boolean found = false;
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                for (InetAddress address : Collections.list(addresses)) {
                    if ("127.0.0.1".equals(address.getHostAddress()) || "0:0:0:0:0:0:0:1".equals(address.getHostAddress())) {
                        continue;
                    }
                    Socket socket = new Socket();
                    InetSocketAddress remoteAddress = new InetSocketAddress(address, adminPort > 0 ? adminPort : adminSecurePort);
                    try {
                        socket.connect(remoteAddress);
                        bindInterface = address.getHostAddress();
                        found = true;
                        break;
                    } catch (IOException e) {
                        // Ignore this address. Let's hope there is more addresses to validate
                    }
                }
                if (found) {
                    break;
                }
            }
        }
    }

    public String getBindInterface() {
        return bindInterface;
    }

    public int getAdminPort() {
        return adminPort;
    }

    public int getAdminSecurePort() {
        return adminSecurePort;
    }

    public String getAdminSecret() {
        return adminSecret;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeInt(out, adminPort);
        ExternalizableUtil.getInstance().writeInt(out, adminSecurePort);
        ExternalizableUtil.getInstance().writeBoolean(out, bindInterface != null);
        if (bindInterface != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, bindInterface);
        }
        ExternalizableUtil.getInstance().writeSafeUTF(out, adminSecret);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        adminPort = ExternalizableUtil.getInstance().readInt(in);
        adminSecurePort = ExternalizableUtil.getInstance().readInt(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            bindInterface = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        adminSecret = ExternalizableUtil.getInstance().readSafeUTF(in);
    }

}
