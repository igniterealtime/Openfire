<%@ taglib uri="core" prefix="c"%>
<%@ taglib uri="fmt" prefix="fmt"%>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>
<%@ page import="org.jivesoftware.util.*,
                 java.util.*,                  
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.muc.HistoryStrategy,
                 org.jivesoftware.messenger.muc.MultiUserChatServer"%>
                 
<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"/>
<%  admin.init(request, response, session, application, out ); %>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "MultiUser Chat History Settings";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "muc-history-settings.jsp"));
    pageinfo.setPageID("muc-history");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%!  // Global vars and methods:

    // Strategy definitions:
    static final int ALL = 1;
    static final int NONE = 2;
    static final int NUMBER = 3;
%>
<%   // Get parameters:
    boolean update = request.getParameter("update") != null;
    int policy = ParamUtils.getIntParameter(request,"policy",-1);
    int numMessages = ParamUtils.getIntParameter(request,"numMessages",0);

	// Get muc history
    MultiUserChatServer mucServer = (MultiUserChatServer)admin.getServiceLookup().lookup(MultiUserChatServer.class);
    HistoryStrategy historyStrat = mucServer.getHistoryStrategy();

    Map errors = new HashMap();
    if (update) {
        if (policy != ALL && policy != NONE && policy != NUMBER) {
            errors.put("general", "Please choose a valid chat history policy.");
        }
        else {
            if (policy == NUMBER && numMessages <= 0) {
                errors.put("numMessages", "Please enter a valid number of messages.");
            }
        }
        if (errors.size() == 0) {
            if (policy == ALL) {
                // Update MUC history strategy
                historyStrat.setType(HistoryStrategy.Type.all);
            }
            else if (policy == NONE) {
                // Update MUC history strategy
                historyStrat.setType(HistoryStrategy.Type.none);
            }
            else if (policy == NUMBER) {
                // Update MUC history strategy
                historyStrat.setType(HistoryStrategy.Type.number);
                historyStrat.setMaxNumber(numMessages);
            }
            // All done, redirect
            %>
            <p class="jive-success-text">Settings updated.</p>
            <% 
        }
    }

    // Set page vars
    if (errors.size() == 0) {
        if (historyStrat.getType() == HistoryStrategy.Type.all) {
            policy = ALL;
        }
        else if (historyStrat.getType() == HistoryStrategy.Type.none) {
            policy = NONE;
        }
        else if (historyStrat.getType() == HistoryStrategy.Type.number) {
            policy = NUMBER;
        }
        numMessages = historyStrat.getMaxNumber();
    }
%>

  <table cellpadding="3" cellspacing="1" border="0" width="600">
<form action="muc-history-settings.jsp">
   
    <tr>
      <td class="text" colspan="2">Group Chat rooms can replay conversation histories to provide context to new members joining a room.
        <fmt:message key="short.title" bundle="${lang}"/> provides several options for controlling how much history to store for each room.
      </td>
    </tr>
    <tr valign="top" class="">
      <td width="1%" nowrap>
        <input type="radio" name="policy" value="<%= NONE %>" id="rb01"  <%= ((policy==NONE) ? "checked" : "") %> />
      </td>
      <td width="99%">
        <label for="rb01">
          <b>Don't Show History</b>
        </label>- Do not show the entire chat history.
      </td>
    </tr>
    <tr valign="top">
      <td width="1%" nowrap>
        <input type="radio" name="policy" value="<%= ALL %>" id="rb02"  <%= ((policy==ALL) ? "checked" : "") %>/>
      </td>
      <td width="99%">
        <label for="rb02">
          <b>Show Entire Chat History</b>
        </label>- Show the entire chat history to the user.
      </td>
    </tr>
    <tr valign="top">
      <td width="1%" nowrap>
        <input type="radio" name="policy" value="<%= NUMBER %>" id="rb03"  <%= ((policy==NUMBER) ? "checked" : "") %> />
      </td>
      <td width="99%">
        <label for="rb03">
          <b>Show a Specific Number of Messages</b>
        </label>- Show a specific number of the most recent messages in the chat. Use the box below to specify that number.
      </td>
    </tr>
    <tr valign="top" class="">
      <td width="1%" nowrap>&nbsp;</td>
      <td width="99%">
        <input type="text" name="numMessages" size="5" maxlength="10" onclick="this.form.policy[2].checked=true;" value="<%= ((numMessages > 0) ? ""+numMessages : "") %>"/> messages
      </td>
    </tr>
  </table>
  <br/>
  <input type="submit" name="update" value="Save Settings"/>
</form>
<%@ include file="bottom.jsp"%>

