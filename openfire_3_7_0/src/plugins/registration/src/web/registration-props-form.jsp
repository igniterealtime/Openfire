<%--
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>

<%@ page
   import="java.util.*,
           org.jivesoftware.admin.*,
           org.jivesoftware.openfire.XMPPServer,
           org.jivesoftware.openfire.user.*,
           org.jivesoftware.openfire.plugin.RegistrationPlugin,
           org.jivesoftware.openfire.group.*,
           org.jivesoftware.util.*"
   errorPage="error.jsp"%>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt"%>

<%
    boolean save = request.getParameter("save") != null;
    boolean saveWelcome = request.getParameter("savemessage") != null;
    boolean saveGroup = request.getParameter("savegroup") != null;
    boolean saveHeader = request.getParameter("saveheader") != null;

    boolean imEnabled = ParamUtils.getBooleanParameter(request, "imenabled", false);
    boolean emailEnabled = ParamUtils.getBooleanParameter(request, "emailenabled", false);
    boolean welcomeEnabled = ParamUtils.getBooleanParameter(request, "welcomeenabled", false);
    boolean groupEnabled = ParamUtils.getBooleanParameter(request, "groupenabled", false);
    boolean webEnabled = ParamUtils.getBooleanParameter(request, "webenabled", false);

    boolean reCaptchaEnabled = ParamUtils.getBooleanParameter(request, "recaptcha", false);
    boolean reCaptchaNoScript = ParamUtils.getBooleanParameter(request, "recaptchanoscript", false);
    String reCaptchaPublicKey = ParamUtils.getParameter(request, "recaptchapublickey");
    String reCaptchaPrivateKey = ParamUtils.getParameter(request, "recaptchaprivatekey");

    String contactIM = ParamUtils.getParameter(request, "contactIM");
    boolean addIM = ParamUtils.getBooleanParameter(request, "addIM");
    boolean deleteIM = ParamUtils.getBooleanParameter(request, "deleteIM");

    String contactEmail = ParamUtils.getParameter(request, "contactEmail");
    boolean addEmail = ParamUtils.getBooleanParameter(request, "addEmail");
    boolean deleteEmail = ParamUtils.getBooleanParameter(request, "deleteEmail");

    String welcomeMessage = ParamUtils.getParameter(request, "welcomemessage");
    String group = ParamUtils.getParameter(request, "groupname");

    String header = ParamUtils.getParameter(request, "header");

    RegistrationPlugin plugin = (RegistrationPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("registration");

    Map<String, String> errors = new HashMap<String, String>();
    if (addIM) {
        if (contactIM == null) {
            errors.put("missingContact", "missingContact");
        }
        else {
            contactIM = contactIM.trim().toLowerCase();
        
            if(contactIM.contains("@")) {
                if (!XMPPServer.getInstance().isLocal(new JID(contactIM))) {
                    errors.put("remoteContact", "remoteContact");
                }
                contactIM = contactIM.substring(0,contactIM.lastIndexOf("@"));
            }
        }
        if (errors.isEmpty()) {
            try  {
                XMPPServer.getInstance().getUserManager().getUser(contactIM);
                plugin.addIMContact(contactIM);
                response.sendRedirect("registration-props-form.jsp?addSuccess=true");
                return;
            }
            catch (UserNotFoundException unfe) {
                errors.put("userNotFound", "userNotFound");
            }
        }
    }

    if (deleteIM) {
        plugin.removeIMContact(contactIM);
        response.sendRedirect("registration-props-form.jsp?deleteSuccess=true");
        return;
    }

    if (addEmail) {
        if (contactEmail == null) {
            errors.put("missingContact", "missingContact");
        }
        else {
            if (plugin.isValidAddress(contactEmail)) {
                plugin.addEmailContact(contactEmail);
                response.sendRedirect("registration-props-form.jsp?addSuccess=true");
                return;
            }
            else {
                errors.put("invalidAddress", "invalidAddress");
            }
        }
    }

    if (deleteEmail) {
        plugin.removeEmailContact(contactEmail);
        response.sendRedirect("registration-props-form.jsp?deleteSuccess=true");
        return;
    }
    
    if (save) {
        plugin.setIMNotificationEnabled(imEnabled);
        plugin.setEmailNotificationEnabled(emailEnabled);
        plugin.setWelcomeEnabled(welcomeEnabled);
        plugin.setWebEnabled(webEnabled);
        plugin.setReCaptchaEnabled(reCaptchaEnabled);
        plugin.setReCaptchaNoScript(reCaptchaNoScript);
        plugin.setReCaptchaPublicKey(reCaptchaPublicKey);
        plugin.setReCaptchaPrivateKey(reCaptchaPrivateKey);
        
        if (groupEnabled) {
            group = plugin.getGroup();
            if (group == null || group.trim().length() < 1) {
                errors.put("groupNotFound", "groupNotFound");
            }
            
            try {
                GroupManager.getInstance().getGroup(group);
            }
            catch (Exception e) {
                errors.put("groupNotFound", "groupNotFound");
            }
        }
        
        if (errors.size() == 0) {
            plugin.setGroupEnabled(groupEnabled);
            response.sendRedirect("registration-props-form.jsp?settingsSaved=true");
            return;
        }
    }
    
    if (saveWelcome) {
        if (welcomeMessage == null || welcomeMessage.trim().length() < 1) {
            errors.put("missingWelcomeMessage", "missingWelcomeMessage");
        }
        else {
            plugin.setWelcomeMessage(welcomeMessage);
            response.sendRedirect("registration-props-form.jsp?welcomeSaved=true");
            return;
        }
    }
    
    if (saveGroup && plugin.groupEnabled()) {
        if (group == null || group.trim().length() < 1) {
            errors.put("groupNotFound", "groupNotFound");
        }
        
        try {
            GroupManager.getInstance().getGroup(group);
        }
        catch (Exception e) {
            errors.put("groupNotFound", "groupNotFound");
        }
        
        if (errors.size() == 0) {
            plugin.setGroup(group);
            response.sendRedirect("registration-props-form.jsp?groupSaved=true");
            return;
        }
    }
    
    if (saveGroup && !plugin.groupEnabled()) {
        group = (group == null) ? "" : group;
        plugin.setGroup(group);
    }
    
    if (saveHeader) {
        if (header == null || header.trim().length() < 1) {
            errors.put("missingHeader", "missingHeader");
        } else {
            plugin.setHeader(header);
            response.sendRedirect("registration-props-form.jsp?headerSaved=true");
            return;
        }
    }
    
    imEnabled = plugin.imNotificationEnabled();
    emailEnabled = plugin.emailNotificationEnabled();
    welcomeEnabled = plugin.welcomeEnabled();
    groupEnabled = plugin.groupEnabled();
    webEnabled = plugin.webEnabled();
    
    welcomeMessage = plugin.getWelcomeMessage();
    group = plugin.getGroup();
    header = plugin.getHeader();
    reCaptchaEnabled = plugin.reCaptchaEnabled();
    reCaptchaNoScript = plugin.reCaptchaNoScript();
    reCaptchaPublicKey = plugin.getReCaptchaPublicKey();
    reCaptchaPrivateKey = plugin.getReCaptchaPrivateKey();
%>

<html>
    <head>
        <title><fmt:message key="registration.props.form.title" /></title>
        <meta name="pageID" content="registration-props-form"/>
    </head>
    <body>

<script language="JavaScript" type="text/javascript">
function addIMContact() {
    document.regform.addIM.value = 'true';
    document.regform.submit();
}

function addEmailContact() {
    document.regform.addEmail.value = 'true';
    document.regform.submit();
}
</script>

<p><fmt:message key="registration.props.form.details" /></p>

<form action="registration-props-form.jsp?save" name="regform" method="post">
<input type="hidden" name="addIM" value="">
<input type="hidden" name="addEmail" value="">

<div class="jive-contentBoxHeader"><fmt:message key="registration.props.form.registration_settings" /></div>
<div class="jive-contentBox">
    <p><fmt:message key="registration.props.form.enable_features" /></p>
   
    <% if (ParamUtils.getBooleanParameter(request, "settingsSaved")) { %>
   
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.save_success" /></td>
        </tr>
    </tbody>
    </table>
    </div>
   
    <% } %>
   
    <% if (errors.containsKey("groupNotFound")) { %>
   
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.invalid_group" /></td>
        </tr>
    </tbody>
    </table>
    </div>
   
    <% } %>
   
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="imenabled" <%=(imEnabled) ? "checked" : "" %>></td>
            <td width="99%" align="left" colspan="2"><fmt:message key="registration.props.form.enable_im_notification" /></td>
        </tr>
        <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="emailenabled" <%=(emailEnabled) ? "checked" : "" %>></td>
            <td width="99%" align="left" colspan="2"><fmt:message key="registration.props.form.enable_email_notification" /></td>
        </tr>
        <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="welcomeenabled" <%=(welcomeEnabled) ? "checked" : "" %>></td>
            <td width="99%" align="left" colspan="2"><fmt:message key="registration.props.form.enable_welcome_msg" /></td>
        </tr>
        <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="groupenabled" <%=(groupEnabled) ? "checked" : "" %>></td>
            <td width="99%" align="left" colspan="2"><fmt:message key="registration.props.form.enable_add_user_to_group" /></td
        </tr>
        <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="webenabled" <%=(webEnabled) ? "checked" : "" %>></td>
            <td width="99%" align="left" colspan="2"><fmt:message key="registration.props.form.enable_web_registration" /> <%=plugin.webRegistrationAddress() %></td>
        </tr>
        <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="recaptcha" <%=(reCaptchaEnabled) ? "checked" : "" %>></td>
            <td width="99%" align="left" colspan="2"><fmt:message key="registration.props.form.enable_recaptcha" /> <%=plugin.webRegistrationAddress() %></td>
        </tr>
        <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="recaptchanoscript" <%=(reCaptchaNoScript) ? "checked" : "" %>></td>
            <td width="99%" align="left" colspan="2"><fmt:message key="registration.props.form.recaptcha_noscript" /></td>
        </tr>
        <tr>
            <td width="1%" align="center" nowrap>&nbsp;</td>
            <td width="24%" align="left"><fmt:message key="registration.props.form.recaptcha_public_key" /></td>
            <td width="75%" align="left"><input type="text" name="recaptchapublickey" size="40" maxlength="100" value="<%= (reCaptchaPublicKey != null ? reCaptchaPublicKey : "") %>"/></td>
        </tr>
        <tr>
            <td width="1%" align="center" nowrap>&nbsp;</td>
            <td width="24%" align="left"><fmt:message key="registration.props.form.recaptcha_private_key" /></td>
            <td width="75%" align="left"><input type="text" name="recaptchaprivatekey" size="40" maxlength="100" value="<%= (reCaptchaPrivateKey != null ? reCaptchaPrivateKey : "") %>"/></td>
        </tr>
    </tbody>
    </table>
    <br>
    <input type="submit" value="<fmt:message key="registration.props.form.save_settings" />"/>
</div>

<br>

<div class="jive-contentBoxHeader"><fmt:message key="registration.props.form.registration_contacts" /></div>
<div class="jive-contentBox">
    <p><fmt:message key="registration.props.form.registration_contacts_details" /></p>
   
    <% if (ParamUtils.getBooleanParameter(request, "deleteSuccess")) { %>
   
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.registration_contact_removed" /></td>
        </tr>
    </tbody>
    </table>
    </div>
   
    <% } else if (ParamUtils.getBooleanParameter(request, "addSuccess")) { %>
   
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.registration_contact_removed" /></td>
        </tr>
    </tbody>
    </table>
    </div>
   
    <% } else if (errors.containsKey("missingContact")) { %>
   
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.registration_contact_missing" /></td>
        </tr>
    </tbody>
    </table>
    </div>
   
    <% } else if (errors.containsKey("remoteContact")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.registration_remote_contact" /></td>
        </tr>
    </tbody>
    </table>
    </div>

    <% } else if (errors.containsKey("userNotFound")) { %>
   
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.registration_contact_not_found" /></td>
        </tr>
    </tbody>
    </table>
    </div>
   
    <% } else if (errors.containsKey("invalidAddress")) { %>
   
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.registration_invalid_email" /></td>
        </tr>
    </tbody>
    </table>
    </div>
   
    <% } %>
   
    <div>
    <label for="contacttf"><fmt:message key="registration.props.form.registration_add_im" />:</label> 
    <input type="text" name="contactIM" size="30" maxlength="100" value="<%= (contactIM != null ? contactIM : "") %>" id="contacttf"/> 
    <input type="submit" value="<fmt:message key="registration.props.form.registration_add" />" onclick="return addIMContact();"/>
   
    <br><br>
   
    <div class="jive-table" style="width:400px;">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th width="99%"><fmt:message key="registration.props.form.registration_im_contact" /></th>
            <th width="1%" nowrap><fmt:message key="registration.props.form.registration_remove" /></th>
        </tr>
    </thead>
    <tbody>
    <% if (plugin.getIMContacts().size() == 0) { %>
   
    <tr>
        <td width="100%" colspan="2" align="center" nowrap><fmt:message key="registration.props.form.registration_no_contact" /></td>
    </tr>
   
    <% } %>
   
    <% for (String imContact : plugin.getIMContacts()) { %>
   
    <tr>
        <td width="99%"><%=imContact %></td>
        <td width="1%" align="center"><a
                       href="registration-props-form.jsp?deleteIM=true&contactIM=<%=imContact %>"
                       title="Delete Contact?"
                       onclick="return confirm('Are you sure you want to delete this contact?');"><img
                       src="images/delete-16x16.gif" width="16" height="16"
                       border="0"></a>
        </td>
    </tr>
   
    <% } %>
    </tbody>
    </table>
    </div>
    </div>
   
    <div>
    <label for="emailtf"><fmt:message key="registration.props.form.registration_add_email" />:</label>
    <input type="text" name="contactEmail" size="30" maxlength="100" value="<%= (contactEmail != null ? contactEmail : "") %>" id="emailtf"/>
    <input type="submit" value="<fmt:message key="registration.props.form.registration_add" />" onclick="return addEmailContact();"/>
   
    <br><br>
   
    <div class="jive-table" style="width:400px;">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th width="99%"><fmt:message key="registration.props.form.registration_email_contact" /></th>
            <th width="1%" nowrap><fmt:message key="registration.props.form.registration_add" /></th>
        </tr>
    </thead>
    <tbody>
    <% if (plugin.getEmailContacts().size() == 0) { %>
   
    <tr>
        <td width="100%" colspan="2" align="center" nowrap><fmt:message key="registration.props.form.registration_no_contact" /></td>
    </tr>
   
    <% } %>
   
    <% for (String emailContact : plugin.getEmailContacts()) { %>
   
    <tr>
        <td width="99%"><%=emailContact %></td>
        <td width="1%" align="center"><a
                       href="registration-props-form.jsp?deleteEmail=true&contactEmail=<%=emailContact %>"
                       title="Delete Contact?"
                       onclick="return confirm('Are you sure you want to delete this contact?');"><img
                       src="images/delete-16x16.gif" width="16" height="16"
                       border="0"></a>
        </td>
    </tr>
   
    <% } %>
    </tbody>
    </table>
    </div>
    </div>
</div>
</form>

<br>

<form action="registration-props-form.jsp?savemessage=true" method="post">
<div class="jive-contentBoxHeader"><fmt:message key="registration.props.form.welcome_message" /></div>
<div class="jive-contentBox">
    <p><fmt:message key="registration.props.form.welcome_message_details" /></p>
   
    <% if (ParamUtils.getBooleanParameter(request, "welcomeSaved")) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.welcome_message_saved" /></td>
        </tr>
    </tbody>
    </table>
    </div>
   
    <% } %>
   
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="5%" valign="top">Message:&nbsp;</td>
            <td width="95%"><textarea cols="45" rows="5" wrap="virtual" name="welcomemessage"><%= welcomeMessage %></textarea></td>
            <% if (errors.containsKey("missingWelcomeMessage")) { %>
            <span class="jive-error-text"><br><fmt:message key="registration.props.form.welcome_message_missing" /></span>
            <% } %>            
        </tr>
    </tbody>
    </table>
    
    <br>
    <input type="submit" value="<fmt:message key="registration.props.form.welcome_message_save" />"/>
    </div>
</form>

<br>

<form action="registration-props-form.jsp?savegroup=true" method="post">
<div class="jive-contentBoxHeader"><fmt:message key="registration.props.form.default_group" /></div>
<div class="jive-contentBox">
    <p><fmt:message key="registration.props.form.default_group_details" /></p>
   
    <% if (ParamUtils.getBooleanParameter(request, "groupSaved")) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.default_group_saved" /></td>
        </tr>
    </tbody>
    </table>
    </div>
   
    <% } %>

    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td>Default Group:&nbsp;<input type="text" name="groupname" size="30" maxlength="100" value="<%= (group != null ? group : "") %>"/>
            <% if (errors.containsKey("groupNotFound")) { %> 
            <span class="jive-error-text"><br><fmt:message key="registration.props.form.default_group_invalid" /></span>
            <% } %>
        </tr>
    </tbody>
    </table>
    
   <br>
    <input type="submit" value="<fmt:message key="registration.props.form.default_group_save" />"/>
    </div>
</form>

<br>

<form action="registration-props-form.jsp?saveheader=true" method="post">
<div class="jive-contentBoxHeader"><fmt:message key="registration.props.form.sign_up" /></div>
<div class="jive-contentBox">
    <p><fmt:message key="registration.props.form.sign_up_details" /></p>
   
    <% if (ParamUtils.getBooleanParameter(request, "headerSaved")) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.props.form.sign_up_saved" /></td>
        </tr>
    </tbody>
    </table>
    </div>
   
    <% } %>
   
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td>Header Text:&nbsp;<input type="text" name="header" size="30" maxlength="100" value="<%=header %>"/></td>
            <% if (errors.containsKey("missingHeader")) { %>
            <span class="jive-error-text"><br><fmt:message key="registration.props.form.sign_up_missing" /></span>
            <% } %>
         </tr>
    </tbody>
    </table>
    
    <br>
    <input type="submit" value="<fmt:message key="registration.props.form.sign_up_save" />"/>
    </div>
</form>

</body>
</html>