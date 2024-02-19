package org.jivesoftware.util.cert;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cert.CNCertificateIdentityMapping;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Certificate identity mapping that uses the CommonName as the
 * identity credentials and provides possibility to add optional prefix and suffix
 *
 */
public class CustomizedCNCertificateIdentityMapping extends CNCertificateIdentityMapping {

    private final String prefix;
    private final String suffix;

    public CustomizedCNCertificateIdentityMapping(){
        prefix = JiveGlobals.getProperty("provider.clientCertIdentityMap.customized.prefix", "");
        suffix = JiveGlobals.getProperty("provider.clientCertIdentityMap.customized.suffix", "");
    }

    /**
     * Maps certificate CommonName as identity credentials with optional prefix and/or suffix.
     * Prefix and suffix are specified by properties. Default value is an empty string.
     * @param certificate the certificates to map
     * @return A List of customized names.
     */
    @Override
    public List<String> mapIdentity(X509Certificate certificate) {
        return super.mapIdentity(certificate)
            .stream()
            .map(name -> prefix + name + suffix)
            .collect(Collectors.toList());
    }

    @Override
    public String name() {
        return "Customized Common Name Mapping";
    }
}
