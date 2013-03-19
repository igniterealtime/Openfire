<%--
  -	$RCSfile$
  -	$Revision: 22570 $
  -	$Date: 2005-10-10 21:07:40 -0700 (Mon, 10 Oct 2005) $
--%>
<%@ page
import="org.jivesoftware.openfire.fastpath.history.AgentChatSession,
        org.jivesoftware.openfire.fastpath.history.ChatSession,
        org.jivesoftware.openfire.fastpath.history.ChatTranscriptManager,
        org.jivesoftware.xmpp.workgroup.utils.ModelUtil,
        java.util.Iterator,
        java.util.List"%>

<html>
    <head>
        <title>Chat Conversation</title>
        <meta name="pageID" content="chat-summary"/>
      <style type="text/css">
          .conversation-label1 {
              color: blue;
              font-size: 10px;
              font-family: Verdana, Arial, sans-serif;
          }

          .conversation-label2 {
              color: red;
              font-size: 10px;
              font-family: Verdana, Arial, sans-serif;
          }

          .notification-label {
              color: #060;
              font-size: 10px;
              font-family: Verdana, Arial, sans-serif;
          }

          .conversation-body {
               color: black;
               font-size: 11px;
               font-family: Verdana, Arial, sans-serif;
           }
    </style>
    </head>
    <body>
<%
    String sessionID = request.getParameter("sessionID");
    ChatSession chatSession = ChatTranscriptManager.getChatSession(sessionID);
    AgentChatSession initial = chatSession.getFirstSession();
    if (initial == null) {
        if (chatSession.getState() == 0) {
            out.println("User Cancelled");
        }
        else if (chatSession.getState() == 1) {
            out.println("User could not be routed");
        }
        else {
            out.println("Agent never joined");
        }
    }
    else {

    }

    List questionList = chatSession.getMetadata().get("question");
    String question = "No question was asked";
    if (questionList != null && questionList.size() > 0) {
        question = (String)questionList.get(0);
        chatSession.getMetadata().remove("question");
    }

%>
<div  class="jive-contentBox">
    <table cellpadding="3" cellspacing="0" border="0" width="70%">
        <h4>Conversation Metadata</h4>
        <tr>
            <td  colspan=1 class="conversation-body">
                <b>Question:</b>
            </td>
            <td colspan=4 class="conversation-body">
                <%= question %>
            </td>
        </tr>
<%
    int counter = 0;
    Iterator<String> metaIter = chatSession.getMetadata().keySet().iterator();
    while (metaIter.hasNext()) {
        String metaname = metaIter.next();
        String metavalue = "";
        metavalue = org.jivesoftware.xmpp.workgroup.request.Request
                .encodeMetadataValue(chatSession.getMetadata().get(metaname));

        counter++;
%>
            <tr>
                <td nowrap class="conversation-body">
                    <%= metaname %>
                </td>
                <td colspan="3" class="conversation-body">
                    <%= metavalue %>
            </tr>
<%

        }
%>
<%
        String transcript = chatSession.getTranscript();
%>
</table>
   </div>
<br/>
<div  class="jive-contentBox">
 <table  cellpadding="3" cellspacing="0" border="0" width="70%">
        <tr class="jive-even" >
            <td colspan=4>
             <h4>Chat Transcripts</h4>
               </td>
        </tr>
        <tr>
          <td><%= transcript %></td>
        </tr>
    </table>
<%
    if (!ModelUtil.hasLength(chatSession.getTranscript())) {
%>
        <table class="jive-table" cellpadding="3" cellspacing="1" border="0">
            <tr>
                <td class="c1" colspan=4>
                    <tr>
                        <td>
                            No Chats have occured in this workgroup.
                        </td>
                    </tr>
                </td>
            </tr>
        </table>
<%
    }
%>
    </body>
</html>
