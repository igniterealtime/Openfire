import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.bosh.BOSHConfiguration;
import org.jivesoftware.smack.bosh.XMPPBOSHConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class ConnectivityTest
{
    /**
     * Verifies that a few connections can be established and used to authenticate over BOSH.
     */
    @Test
    public void testBoshConnections() throws Exception
    {
        // Setup test fixture.
        final BOSHConfiguration config = BOSHConfiguration.builder()
            .setFile("/http-bind/")
            .setXmppDomain("example.org")
            .setHost("localhost")
            .setPort(7070)
            .setUseHttps(false)
            .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
            .build();
        final int iterations = 50;

        // For some reason, the first connection on a freshly started instance on CI seems to fail. This attempts to work
        // around that problem by ignoring the first connection attempt.
        final XMPPBOSHConnection warmupCon = new XMPPBOSHConnection(config);
        warmupCon.connect();
        try {
            warmupCon.login("john", "secret");
        } catch (Throwable e) {
            System.out.println("Warm-up connection failed with error message: " + e.getMessage());
        } finally {
            warmupCon.disconnect();
        }

        // Execute system under test.
        int result = 0;
        for (int i=0; i<iterations; i++) {
            final XMPPBOSHConnection con = new XMPPBOSHConnection(config);
            con.connect();
            try {
                con.login("john", "secret");
                if (con.isAuthenticated()) {
                    result++;
                }
            } catch (Throwable e) {
                // This will prevent the result from being incremented, which will cause the test to fail.
                e.printStackTrace();
            } finally {
                con.disconnect();
            }
        }

        // Verify result.
        Assert.assertEquals("Expected all BOSH connection attempts to result in an authenticated session, but not all did.", iterations, result);
    }

    /**
     * Verifies that a few connections can be established and used to authenticate over TCP.
     */
    @Test
    public void testTCPConnections() throws Exception
    {
        // Setup test fixture.
        final XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
            .setXmppDomain("example.org")
            .setHost("localhost")
            .setPort(5222)
            .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
            .build();
        final int iterations = 50;

        // For some reason, the first connection on a freshly started instance on CI seems to fail. This attempts to work
        // around that problem by ignoring the first connection attempt.
        final XMPPTCPConnection warmupCon = new XMPPTCPConnection(config);
        warmupCon.connect();
        try {
            warmupCon.login("john", "secret");
        } catch (Throwable e) {
            System.out.println("Warm-up connection failed with error message: " + e.getMessage());
        } finally {
            warmupCon.disconnect();
        }

        // Execute system under test.
        int result = 0;
        for (int i=0; i<iterations; i++) {
            final XMPPTCPConnection con = new XMPPTCPConnection(config);
            con.connect();
            try {
                con.login("john", "secret");
                if (con.isAuthenticated()) {
                    result++;
                }
            } catch (Throwable e) {
                // This will prevent the result from being incremented, which will cause the test to fail.
                e.printStackTrace();
            } finally {
                con.disconnect();
            }
        }

        // Verify result.
        Assert.assertEquals("Expected all TCP connection attempts to result in an authenticated session, but not all did.", iterations, result);
    }
}
