package org.jivesoftware.openfire.sasl;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.util.Map;

public class StackedMultiSaslServer implements SaslServer {
    private SaslServer submech = null;
    private Map<String, ?> props;

    StackedMultiSaslServer(Map<String, ?> props) {
        this.props = props;
    }

    @Override
    public String getMechanismName() {
        return "STACKED";
    }

    @Override
    public byte[] evaluateResponse(byte[] bytes) throws SaslException {
        if (submech == null) {
            return initialResponse(bytes);
        }
        return new byte[0];
    }

    private byte[] initialResponse(byte[] bytes) throws SaslException {
        int ptr = 0;
        int len = bytes[ptr] * 256 + bytes[ptr+1];
        String val = new String(bytes, ptr + 2, ptr + 2 + len);
        return new byte[0];
    }

    @Override
    public boolean isComplete() {
        return false;
    }

    @Override
    public String getAuthorizationID() {
        return null;
    }

    @Override
    public byte[] unwrap(byte[] bytes, int i, int i1) throws SaslException {
        return new byte[0];
    }

    @Override
    public byte[] wrap(byte[] bytes, int i, int i1) throws SaslException {
        return new byte[0];
    }

    @Override
    public Object getNegotiatedProperty(String s) {
        return null;
    }

    @Override
    public void dispose() throws SaslException {

    }
}
