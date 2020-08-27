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
