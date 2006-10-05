<%@ page import="org.jivesoftware.wildfire.ldap.LdapManager" %>
<%@ page import="java.util.Map, java.net.UnknownHostException, javax.naming.ldap.LdapContext, javax.naming.*" %>
<%
    boolean success = false;
    String errorDetail = "";
    Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
    if (settings != null) {
        LdapManager manager = new LdapManager(settings);
        LdapContext context = null;
        try {
            context = manager.getContext();
            NamingEnumeration list = context.list("");
            list.close();
            // We were able to successfully connect to the LDAP server
            success = true;
        }
        catch (NamingException e) {
            if (e instanceof AuthenticationException) {
                errorDetail =
                        "Error authenticating with the LDAP server. Check supplied credentials.";
            }
            else if (e instanceof CommunicationException) {
                errorDetail = "Error connecting to the LDAP server. Ensure that the directory " +
                        "server is running at the specified host name and port and that a firewall " +
                        "is not blocking access to the server.";
                Throwable cause = e.getCause();
                if (cause != null) {
                    if (cause instanceof UnknownHostException) {
                        errorDetail = "Unknown host address.";
                    }
                }
            }
            else if (e instanceof InvalidNameException) {
                errorDetail = "Invalid DN syntax or naming violation.";
            }
            else if (e instanceof NameNotFoundException) {
                errorDetail = "Error verifying base DN. Verify that the value is correct.";   
            }
            else {
                errorDetail = e.getExplanation();
            }
            e.printStackTrace();
        }
        finally {
            if (context != null) {
                try {
                    context.close();
                }
                catch (Exception e) {
                }
            }
        }
    }
%>
    <!-- BEGIN connection settings test panel -->
	<div class="jive-testPanel">
		<div class="jive-testPanel-content">
		
			<div align="right" class="jive-testPanel-close">
				<a href="#" class="lbAction" rel="deactivate">Close</a>
			</div>
			
			
			<h2>Test: <span>Connection Settings</span></h2>
            <% if (success) { %>
            <h4 class="jive-testSuccess">Status: Success!</h4>

			<p>A connection was successfully established to the LDAP server using the settings above.
			Close this test panel and continue to the next step.</p>
            <% } else { %>
            <h4 class="jive-testError">Status: Error</h4>
            <p><%= errorDetail %></p>
            <% } %>
            
        </div>
	</div>
	<!-- END connection settings test panel -->