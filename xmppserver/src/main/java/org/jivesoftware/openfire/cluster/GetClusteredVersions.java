/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.PluginMetadata;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

public class GetClusteredVersions implements ClusterTask<GetClusteredVersions> {

    private static final long serialVersionUID = -4081828933134021041L;
    private String openfireVersion;
    private Map<String, String> pluginVersions;

    public String getOpenfireVersion() {
        return openfireVersion;
    }

    public Map<String, String> getPluginVersions() {
        return pluginVersions;
    }

    @Override
    public GetClusteredVersions getResult() {
        return this;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        final ExternalizableUtil util = ExternalizableUtil.getInstance();
        util.writeSafeUTF(out, openfireVersion);
        util.writeStringMap(out, pluginVersions);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        final ExternalizableUtil util = ExternalizableUtil.getInstance();
        openfireVersion = util.readSafeUTF(in);
        pluginVersions = util.readStringMap(in);
    }

    @Override
    public void run() {
        final XMPPServer xmppServer = XMPPServer.getInstance();
        openfireVersion = xmppServer.getServerInfo().getVersion().toString();
        pluginVersions = new HashMap<>();
        pluginVersions = xmppServer.getPluginManager().getMetadataExtractedPlugins().values().stream()
            .filter(pluginMetadata -> !pluginMetadata.getCanonicalName().equals("admin"))
            .collect(Collectors.toMap(
                PluginMetadata::getName, pluginMetadata -> pluginMetadata.getVersion().toString()));
    }
}
