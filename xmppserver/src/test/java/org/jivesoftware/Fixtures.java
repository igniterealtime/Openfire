package org.jivesoftware;

import java.io.File;
import java.net.URL;

import org.jivesoftware.util.JiveGlobals;

public final class Fixtures {

    private Fixtures() {
    }

    /**
     * Reconfigures the Openfire home directory to the blank test one. This allows JiveGlobals.getProperty() etc.
     * to work in test classes without errors being displayed to stderr.
     */
    public static void reconfigureOpenfireHome() throws Exception {
        final URL configFile = ClassLoader.getSystemResource("conf/openfire.xml");
        if (configFile == null) {
            throw new IllegalStateException("Unable to read openfire.xml file; does conf/openfire.xml exist in the test classpath, i.e. test/resources?");
        }
        final File openfireHome = new File(configFile.toURI()).getParentFile().getParentFile();
        JiveGlobals.setHomeDirectory(openfireHome.toString());
    }

}
