package org.jivesoftware.openfire.csi;

import org.dom4j.Element;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.net.Bind2InlineHandler;
import org.jivesoftware.openfire.net.Bind2Request;
import org.jivesoftware.openfire.session.LocalClientSession;

public class CsiModule extends BasicModule {
    static class Bind2CSIHandler implements Bind2InlineHandler {

        @Override
        public String getNamespace() {
            return CsiManager.NAMESPACE;
        }

        @Override
        public boolean handleElement(LocalClientSession session, Element bound, Element element) {
            if (element.getName().equals("active")) {
                session.getCsiManager().activate();
            }
            return true;
        }
    }
    private static final Bind2CSIHandler handler = new Bind2CSIHandler();
    /**
     * <p>Create a basic module with the given name.</p>
     *
     * @param moduleName The name for the module or null to use the default
     */
    public CsiModule(String moduleName) {
        super(moduleName);
    }

    @Override
    public void start() throws IllegalStateException {
        super.start();
        Bind2Request.registerElementHandler(handler);
    }

    @Override
    public void stop() {
        Bind2Request.unregisterElementHandler(handler.getNamespace());
        super.stop();
    }
}
