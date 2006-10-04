<%@ page import="org.jivesoftware.wildfire.ldap.LdapManager" %>
<%@ page import="javax.naming.AuthenticationException" %>
<%@ page import="javax.naming.CommunicationException" %>
<%@ page import="javax.naming.InvalidNameException" %>
<%@ page import="javax.naming.NamingException" %>
<%@ page import="java.util.Map" %>
<%
    boolean success = false;
    String errorDetail = "";
    Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
    if (settings != null) {
        LdapManager manager = new LdapManager(settings);
        try {
            manager.getContext();
            // We were able to successfully connect to the LDAP server
            success = true;
        }
        catch (NamingException e) {
            if (e instanceof AuthenticationException) {
                errorDetail = "Error authenticating with the LDAP server. Check supplied credentials";
            }
            else if (e instanceof CommunicationException) {
                errorDetail = "Error connecting to the LDAP server";
            }
            else if (e instanceof InvalidNameException) {
                errorDetail = "Invalid DN syntax or Naming violation";
            }
            else {
                errorDetail = e.getExplanation();
            }
            e.printStackTrace();
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
            <p><%= errorDetail %>.</p>
            <% } %>
            
        </div>
	</div>
	<!-- END connection settings test panel -->