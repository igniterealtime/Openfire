<%@ page import="org.jivesoftware.xmpp.workgroup.RequestQueue" %>
<%@ page import="org.jivesoftware.xmpp.workgroup.Workgroup" %>
<%@ page import="org.jivesoftware.xmpp.workgroup.WorkgroupManager" %>
<%@ page import="org.jivesoftware.xmpp.workgroup.routing.RoutingManager" %>
<%@ page import="org.jivesoftware.xmpp.workgroup.routing.RoutingRule" %>
<%@ page import="org.jivesoftware.xmpp.workgroup.utils.ModelUtil" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.xmpp.forms.DataForm" %>
<%@ page import="org.xmpp.forms.FormField" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.jivesoftware.util.Log"%>
<%@ page import="org.jivesoftware.util.NotFoundException,
        org.jivesoftware.openfire.fastpath.dataforms.FormManager,
        org.jivesoftware.openfire.user.UserNotFoundException"%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
  <head><title>Workgroup Routing</title></head>
  <body>

  <%
      String wgID = request.getParameter("wgID");

      final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
      Workgroup workgroup = null;
      try {
          workgroup = workgroupManager.getWorkgroup(new JID(wgID));
      }
      catch (UserNotFoundException e) {
          Log.error(e);
      }
      FormManager formManager = FormManager.getInstance();
      DataForm dataForm = formManager.getDataForm(workgroup);

      RoutingManager routingManager = RoutingManager.getInstance();

      Collection<RoutingRule> rules = routingManager.getRoutingRules(workgroup);

      boolean edit = request.getParameter("edit") != null;
      int pos = ParamUtils.getIntParameter(request, "pos", -1);

      boolean submit = request.getParameter("submit") != null;
      boolean errors = false;
      if (submit) {
          String variable = request.getParameter("variable");
          String value = request.getParameter("value");


          if (!ModelUtil.hasLength(value)) {
              errors = true;
          }

          if (!errors) {
              // Add Rule
              long queueID = ParamUtils.getLongParameter(request, "queueID", -1);
              routingManager.addRoutingRule(workgroup, queueID, rules.size() + 1, "");
          }
      }


      boolean changePos = request.getParameter("changePos") != null;
      boolean delete = request.getParameter("remove") != null;


      if (changePos) {
          boolean up = request.getParameter("up") != null;
          boolean down = request.getParameter("down") != null;
          String index = request.getParameter("pos");
          int routerIndex = Integer.parseInt(index);

          RoutingRule moveUpRule = null;
          RoutingRule moveDownRule = null;
          if (up) {
              // Change selected router index to pos - 1 and
              // change pos - 1 to pos + 1 and save.
              for (RoutingRule rule : rules) {
                  if (rule.getPosition() == routerIndex) {
                      moveUpRule = rule;
                  }

                  if (rule.getPosition() == routerIndex - 1) {
                      moveDownRule = rule;
                  }
              }

              // Delete both rules and reapply
              routingManager.removeRoutingRule(workgroup, routerIndex);
              routingManager.removeRoutingRule(workgroup, routerIndex - 1);

              // Add new rules
              moveUpRule.setPosition(routerIndex - 1);
              moveDownRule.setPosition(routerIndex);

          }
          else if (down) {
              for (RoutingRule rule : rules) {
                  if (rule.getPosition() == routerIndex) {
                      moveUpRule = rule;
                  }

                  if (rule.getPosition() == routerIndex - 1) {
                      moveDownRule = rule;
                  }
              }

              // Delete both rules and reapply
              routingManager.removeRoutingRule(workgroup, routerIndex);
              routingManager.removeRoutingRule(workgroup, routerIndex + 1);

              // Add new rules
              moveUpRule.setPosition(routerIndex + 1);
              moveDownRule.setPosition(routerIndex);
          }

          routingManager
                  .addRoutingRule(workgroup, moveUpRule.getQueueID(), moveUpRule.getPosition(), "");
          routingManager.addRoutingRule(workgroup, moveDownRule.getQueueID(),
                  moveDownRule.getPosition(), "");
      }

      if (delete) {
          String index = request.getParameter("pos");
          int routerIndex = Integer.parseInt(index);
          routingManager.removeRoutingRule(workgroup, routerIndex);
          for (RoutingRule rule : rules) {
              if (rule.getPosition() > routerIndex) {
                  routingManager
                          .updateRoutingRule(workgroup, rule.getPosition(), rule.getPosition() - 1);
              }
          }

      }


      rules = routingManager.getRoutingRules(workgroup);

  %>

  <% if (errors) { %>
        <div class="errors">
            Please specify a value to match against the metadata variable.
        </div>
  <% } %>

   <div class="div-border" style="padding: 12px; width: 95%;">
        <table class="jive-table" cellspacing="0" width="100%">
            <th>Order</th><th>Metadata</th><th>Value</th><th>Routes to Queue</th><th>Move</th><th>Edit</th><th>Delete</th>

            <tr style="border-left: none;">

            </tr>


            <% for (RoutingRule rule : rules) {
                RequestQueue rq = null;
                try {
                    rq = workgroup.getRequestQueue(rule.getQueueID());
                }
                catch (NotFoundException e) {
                    Log.error(e);
                }

                int rulePosition = rule.getPosition();
            %>
               <tr>
                   <td><%= rule.getPosition()%></td>
                   <td>
                       test
                   </td>
                   
                   <td>
                       <%= rq.getName() %>
                   </td>
                  <td nowrap>
            <%  if ((rule.getPosition()) < rules.size()) { %>
                <a href="workgroup-routing.jsp?wgID=<%= wgID %>&changePos=true&down=true&pos=<%= rule.getPosition() %>"
                        ><img src="images/arrow_down.gif" width="16" height="16" alt="Move this router down." border="0"></a>
            <%  } else { %>
                <img src="images/blank.gif" width="16" height="16" border="0" alt=""/>
            <%  } %>

            <%  if (rule.getPosition() != 1) { %>
                <a href="workgroup-routing.jsp?wgID=<%= wgID %>&changePos=true&up=true&pos=<%= rule.getPosition() %>"
                        ><img src="images/arrow_up.gif" width="16" height="16" alt="Move this router up." border="0"></a>
            <%  } else { %>
                <img src="images/blank.gif" width="16" height="16" border="0" alt=""/>
            <%  } %>
                  <td align="center">
            <a href="workgroup-routing.jsp?edit=true&wgID=<%= wgID %>&pos=<%= rule.getPosition() %>"
                    ><img src="images/edit-16x16.gif" width="16" height="16" alt="Edit the properties of this Router" border="0"
                    ></a>
        </td>
        <td align="center">
            <a href="workgroup-routing.jsp?remove=true&wgID=<%= wgID %>&pos=<%= rule.getPosition()%>"
                    ><img src="images/delete-16x16.gif" width="16" height="16" alt="Delete this Router" border="0"
                    ></a>
        </td>

               </tr>
            <% } %>



            <% if (rules.size() == 0) { %>
            <tr>
                <td colspan="4" align="center">There are no routing rules defined for this workgroup.</td>
            </tr>
            <%} %>

            </table>
       <br/>

            <fieldset>
              <legend>Add Routing Rule</legend>
                <table cellspacing="0" cellpadding="3">
                      <form action="workgroup-routing.jsp" method="post">
                  <tr>
                      <td colspan="3">
                          Create a new routing rule. Routing rules do searches against given values of the form.<br/><br/>
                      </td>
                  </tr>
                <tr>
                <td>
                    Form Variable:
                </td>
                    <td>
                    <select name="variable">
                     <% for (FormField field : dataForm.getFields()) { %>
                          <option value="<%= field.getVariable()%>"><%= field.getVariable()%></option>
                        <% } %>

                    </select>
                </td>
                    </tr><tr>
                 <td>Form Value:</td>
                <td>
                    <input type="text" name="value" size="30"/>
                </td>
                  </tr><tr>

                    <td>Route To Queue:</td>
                <td>
                    <select name="queueID">
                     <% for (RequestQueue queue : workgroup.getRequestQueues()) { %>
                            <option value="<%= queue.getID()%>"><%= queue.getName()%></option>
                        <% } %>
                    </select>
                </td>
                <td>

                   <input type="submit" name="submit" value="Add Routing Rule"/>
                </td>
            </tr>

                <input type="hidden" name="wgID" value="<%= wgID%>"/>
                           </form>
                     </table>
   </fieldset>
    </div>

  </body>
</html>