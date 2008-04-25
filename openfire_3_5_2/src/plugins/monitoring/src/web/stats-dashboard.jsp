<%@ page import="org.jivesoftware.openfire.archive.Conversation" %>
<%@ page import="org.jivesoftware.openfire.archive.ConversationManager" %>
<%@ page import="org.jivesoftware.openfire.reporting.graph.GraphEngine" %>
<%@ page import="org.jivesoftware.openfire.reporting.stats.StatisticsModule" %>
<%@ page import="org.jivesoftware.openfire.reporting.stats.StatsAction"%>
<%@ page import="org.jivesoftware.openfire.reporting.stats.StatsViewer"%>
<%@ page import="org.jivesoftware.openfire.user.UserNameManager"%>
<%@ page import="org.jivesoftware.openfire.user.UserNotFoundException"%>
<%@ page import="org.jivesoftware.util.CookieUtils"%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="javax.servlet.http.Cookie"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.util.*"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.plugin.MonitoringPlugin" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    String sessionKey = StatisticsModule.SESSIONS_KEY;
    MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("monitoring");
    ConversationManager conversationManager = (ConversationManager)plugin.getModule(ConversationManager.class);
    StatsViewer viewer = (StatsViewer)plugin.getModule(StatsViewer.class);

    String timePeriod = "last60minutes";
    Cookie timePeriodCookie = CookieUtils.getCookie(request, COOKIE_TIMEPERIOD);
    if (timePeriodCookie != null) {
        String cookieValue = timePeriodCookie.getValue();
        timePeriod = cookieValue;
    }

%>
<html>
<head>
    <title><fmt:message key="admin.sidebar.statistics.name" /></title>
    <meta name="pageID" content="statistics"/>
    <script src="/js/prototype.js" type="text/javascript"></script>
    <script src="/js/scriptaculous.js" type="text/javascript"></script>
    <script src="dwr/engine.js" type="text/javascript" ></script>
	<script src="dwr/util.js" type="text/javascript" ></script>
    <script src="dwr/interface/Stats.js" type="text/javascript"></script>

    <style type="text/css">
    .stats-description {
        color : black;
        font-size : 18px;
        font-weight : bold;
    }
    .stats-current {
        color : #555555;
        font-size : 20px;
        font-weight : bold;
    }
    .stat {
        border : 1px;
        border-color : #ccc;
        border-style : solid;
        background-color : #fffBe2;
        -moz-border-radius: 5px;
    }
    .stat_selected {
        border : 1px;
        border-color : #f6ab4d;
        border-style : solid;
        background-color : #fffBc2;
        -moz-border-radius: 5px;
    }

    .stat_enlarge_link {
        display: block;
        position: relative;
        margin: 4px 0px 2px 6px;
        padding-left: 18px;
        background: url(images/reports_dash-expand-small.gif) no-repeat;
        font-size: 11px;
    }
    .stat_shrink_link {
        position: relative;
        margin: 4px 0px 2px 6px;
        padding-left: 18px;
        background: url(images/reports_dash-contract-small.gif) no-repeat;
        font-size: 11px;
    }
    .timeControl {
        border : 1px;
        border-color : #ccc;
        border-style : solid;
        background-color : white;
    }

    .wrapper {
        border : 1px;
        border-color : #ccc;
        border-style : solid;
        -moz-border-radius: 5px;
    }

    .quickstats {
        border: 1px solid #cccccc;
        border-bottom: none;
    }
    .quickstats thead th {
        background-color: #eeeeee;
        text-align: left;
        padding: 3px;
        border-bottom: 1px solid #cccccc;
    }
    .quickstats tbody td {
        padding: 6px;
        border-bottom: 1px solid #cccccc;
        font-size: 11px;
    }

    .conversation {
        border-bottom : 1px;
        border-top : 0px;
        border-right : 0px;
        border-left : 0px;
        border-color : #ccc;
        border-style : solid;
    }

    .conversation table td {
        font-size: 11px;
    }

    conv-users, conv-messages {
	    float: left;
        display: block;
	    text-decoration: none;
    }
</style>

<style type="text/css">
	@import "style/style.css";
</style>
</head>

<body>

<script type="text/javascript">
PeriodicalExecuter.prototype.registerCallback = function() {
    this.intervalID = setInterval(this.onTimerEvent.bind(this), this.frequency * 1000);
}

PeriodicalExecuter.prototype.stop = function() {
    clearInterval(this.intervalID);
}
DWREngine.setErrorHandler(handleError);
window.onerror = handleError;
function handleError() {
    // swallow errors: probably caused by the server being down
}

var peStats = new PeriodicalExecuter(statsUpdater, 30);

var currentTimePeriod = '<%= timePeriod %>';

function statsUpdater() {
    try {
        Stats.getUpdatedStats(currentTimePeriod, updateStats);
    } catch(err) {
        // swallow errors
    }
}


function changeTimePeriod(period) {
    if (currentTimePeriod != period) {
        $(currentTimePeriod).className = '';
        $(period).className = 'timeControl';
        currentTimePeriod = period;
        createCookie("<%= COOKIE_TIMEPERIOD %>",currentTimePeriod,1000);
        Stats.getUpdatedStats(currentTimePeriod, updateStats);
    }
}

function updateStats(stats) {

    for (var stat in stats) {
        updateTable(stat, stats[stat]);

        if (stat == 'conversations' || stat == 'packet_count' || stat == 'sessions') {
            updateGraph('sparklines-' + stat, 'stat=' + stat + '&sparkline=true');
        } else {
            updateGraph('sparklines-' + stat, 'stat=' + stat + '&sparkline=true&color=dark');
        }
    }
}

function updateTable(id, data) {
    $(id + '.low').innerHTML = data.low;
    $(id + '.high').innerHTML = data.high;
    if ($(id + '.count') != undefined) {
        $(id + '.count').innerHTML = data.count;
    }
}

function updateGraph(graphid, graphkey) {
   var d = new Date();
   var t = d.getTime()
   $(graphid).src = 'graph?' + graphkey + '&t=' + t + "&timeperiod=" + currentTimePeriod + "&format=png";

    statParam = graphkey.split('&');
    statName = statParam[0].split('=');
    if (isSnapshotDetailVisible && currentSnapshot == statName[1]) {
        viewElement = $('snapshot-detail-image');
        viewElement.src = 'graph?stat=' + statName[1] + '&t=' + t + '&timeperiod=' + currentTimePeriod + '&width=700&height=250&format=png'
    }
}

var lastConversationID = 0;
var getConversationsDelay = 10000;
var insertConversationsDelay = 2000;
var peGetConversations;
var peInsertConversations;
var conversations = new Array();

function startupConversations() {
    conversationUpdater();
    peGetConversations = new PeriodicalExecuter(conversationUpdater, getConversationsDelay/1000);
}

function conversationUpdater() {
    Stats.getNLatestConversations(6, lastConversationID, updateConversations);
}

function updateConversations(data) {
    // list of map objects with users, lastactivity, messages keys
    if (data.length > 0) {
        for (var i=0; i<data.length; i++) {
            conversations[conversations.length] = data[i];
        }
        lastConversationID = conversations[conversations.length -1].conversationid;
    }

    // adjust insert frequency based on how many are in the queue
    if (data.length > 0 && Math.round(getConversationsDelay/(data.length)) > 2000) {
        insertConversationsDelay = Math.round(10000/(data.length));
    } else {
        insertConversationsDelay = 2000;
    }

    if (peInsertConversations) {
        peInsertConversations.stop();
    }
    peInsertConversations = new PeriodicalExecuter(insertConversation, insertConversationsDelay/1000);

}

function insertConversation() {
    if (conversations.length > 0) {

        if ($('conversations-scroller-none') != undefined) {
            Element.hide('conversations-scroller-none');
            Element.show('conversations-scroller');
        }

        var conversation = conversations.shift();
        convTableID = 'conversations-scroller';
        var tbody = $(convTableID);
        var rows = tbody.getElementsByTagName("div");
        for (var i = rows.length-1; i > 0; i--) {
            rows[i].innerHTML = rows[i-1].innerHTML;
        }
        newRow = document.createElement("div");
        newRow.setAttribute("class", "conversation");
        newRow.setAttribute('conversationid', conversation.conversationid);

        users = conversation.users;
        userString = '';
        for (i=0; i<users.length; i++) {
            userString += users[i] + "<br />";
        }

        newRowHTML =
        '<table cellspacing="0" cellpadding="0" border="0">' +
            '<tr>' +
                '<td style="width:8px;"><img src="images/blank.gif" height="40" width="8" alt="" border="0" /></td>' +
                '<td style="width:147px;">' +
                userString +
                '</td>' +
                '<td align="center" style="width:85px;">' +
                conversation.lastactivity +
                '</td>' +
                '<td><img src="images/blank.gif" width="6" alt="" border="0" /></td>' +
                '<td align="center" style="width:77px;">' + conversation.messages + '</td>' +
            '</tr>' +
        '</table>';

        newRow.innerHTML = newRowHTML;

        if (!isIE()) {
            rows[0].style.display = 'none';
            rows[0].innerHTML = newRow.innerHTML;
            new Effect.Appear(rows[0]);
        } else {
            rows[0].innerHTML = newRow.innerHTML;
        }
    }
}

function isIE() {
    return navigator.appName.indexOf('Microsoft') != -1;
}

var isSnapshotDetailVisible = false;
var currentSnapshot = '';


function displaySnapshotDetail(snapshot) {
    if (!isSnapshotDetailVisible) {
        $('snapshot-detail-image').src = 'graph?stat=' + snapshot + '&t=' + t + '&timeperiod=' + currentTimePeriod + '&width=700&height=250&format=png';
        Effect.SlideDown('snapshot-detail');
        isSnapshotDetailVisible = true;
        toggleSnapshotSelected(snapshot);
        currentSnapshot = snapshot;
    } else {
        if ($('snapshot-detail-image').src.indexOf(snapshot) == -1) {
            viewElement = $('snapshot-detail-image');
            viewElement.style.display = "none";
            viewElement.src = '/images/blank.gif';
            var i = new Image();
            i.onload = function() {
                viewElement.src = i.src;
                Effect.Appear('snapshot-detail-image');
            }
            var d = new Date();
            var t = d.getTime()
            i.src = 'graph?stat=' + snapshot + '&t=' + t + '&timeperiod=' + currentTimePeriod + '&width=700&height=250&format=png';
            toggleSnapshotSelected(snapshot);
            currentSnapshot = snapshot;
        } else {
            hideSnapshotDetail();
            currentSnapshot = '';
            $('table-sessions').className = "stat";
            $('table-conversations').className = "stat";
            $('table-packet_count').className = "stat";
        }
    }
}

function toggleSnapshotSelected(selected) {
    $('table-' + selected).className = "stat_selected";
    $(selected + '-enlarge').className = 'stat_shrink_link';
    $(selected + '-enlarge').innerHTML = '<fmt:message key="dashboard.snapshot.enlarge" />';
    if (currentSnapshot != '') {
        $('table-' + currentSnapshot).className = "stat";
        $(currentSnapshot + '-enlarge').className = 'stat_enlarge_link';
        $(currentSnapshot + '-enlarge').innerHTML = '<fmt:message key="dashboard.snapshot.shrink" />';
    }

}

function hideSnapshotDetail() {
    if (isSnapshotDetailVisible) {
        $(currentSnapshot + '-enlarge').className = 'stat_enlarge_link';
        $(currentSnapshot + '-enlarge').innerHTML = '<fmt:message key="dashboard.snapshot.enlarge" />';
        Effect.SlideUp('snapshot-detail');
        currentSnapshot = '';
        $('table-sessions').className = "stat";
        $('table-conversations').className = "stat";
        $('table-packet_count').className = "stat";
        isSnapshotDetailVisible = false;
    }
}

function createCookie(name,value,days) {
    if (days) {
        var date = new Date();
        date.setTime(date.getTime()+(days*24*60*60*1000));
        var expires = "; expires="+date.toGMTString();
    } else {
        var expires = "";
    }
    document.cookie = name+"="+value+expires+"; path=/";
}


</script>

<div id="instructions">
    <table width="756" border="0">
    <tr>
        <td width="426">
        <p><fmt:message key="dashboard.description" /><br /><fmt:message key="dashboard.directions" /></p>
        </td>
        <td width="330" align="right">
            <table class="stat" width="315" cellspacing="0" cellpadding="0">
            <tr>
                <td colspan="6"><img src="images/blank.gif" height="9" width="1" alt="" /></td>
            </tr>
            <tr>
                <td><img src="images/blank.gif" height="1" width="9" alt="" /></td>
                <td><b><fmt:message key="dashboard.timespan" /></b></td>
                <td>
                    <table
                        <% if (timePeriod.equalsIgnoreCase("last60minutes")) { %>
                            class="timeControl"
                        <% }%>
                        id="last60minutes" style="cursor: pointer;">
                    <tr onClick="changeTimePeriod('last60minutes'); return false;">
                        <td><img src="images/icon_clock-1hour.gif"
                                 alt="<fmt:message key="dashboard.timespan.lasthour" />" border="0" /></td>
                        <td> <fmt:message key="dashboard.timespan.lasthour" /></td>
                    </tr>
                    </table>
                </td>
                <td>
                    <table
                        <% if (timePeriod.equalsIgnoreCase("last24hours")) { %>
                            class="timeControl"
                        <% }%>
                        id="last24hours" style="cursor: pointer;">
                    <tr onClick="changeTimePeriod('last24hours'); return false;">
                        <td><img src="images/icon_clock-24hour.gif"
                                 alt="<fmt:message key="dashboard.timespan.last24hours" />" border="0" /></td>
                        <td> <fmt:message key="dashboard.timespan.last24hours" /></td>
                    </tr>
                    </table>
                </td>
                <td>
                    <table
                        <% if (timePeriod.equalsIgnoreCase("last7days")) { %>
                            class="timeControl"
                        <% }%>
                        id="last7days" style="cursor: pointer;">
                    <tr onClick="changeTimePeriod('last7days'); return false;">
                        <td><img src="images/icon_calendar-week.gif"
                                 alt="<fmt:message key="dashboard.timespan.last7days" />" border="0" /></td>
                        <td> <fmt:message key="dashboard.timespan.last7days" /></td>
                    </tr>
                    </table>
                </td>
                <td><img src="images/blank.gif" height="1" width="9" alt="" /></td>
            </tr>
            <tr>
                <td colspan="6"><img src="images/blank.gif" height="9" width="1" alt="" /></td>
            </tr>
            </table>
        </td>
    </tr>
    </table>
    <br />
</div>





<table class="wrapper">
<tr>
    <td colspan="3">


        <div id="snapshot-detail" style="display:none;">
            <div>
            <table cellpadding="0" cellspacing="0" border="0">
            <tr>
                <td colspan="2"><img border="0" width="700" height="25" src="images/blank.gif" alt=""/></td>
            </tr>
            <tr>
                <td colspan="2">
                    <div style="display: block; width: 692px; text-align: right;">
                        <div class="stat_shrink_link" style="background: none;">

                            <a href="#" onclick="hideSnapshotDetail(); return false;">
                                <img src="images/reports_dash-contract-small.gif" alt="" border="0" hspace="2" align="texttop"><fmt:message key="dashboard.snapshot.shrink" />
                            </a>
                        </div>
                    </div>
                </td>
            </tr>
            <tr>
                <td><img border="0" width="1" height="250" src="images/blank.gif" alt=""/></td>
                <td>
                        <a href="#" onclick="hideSnapshotDetail(); return false;">
                        <img border="0" width="700" height="250" src="images/blank.gif" alt="" id="snapshot-detail-image"/></a></td>
            </tr>
            </table>
            </div>
        </div>

        <img src="images/blank.gif" height="14" width="1" alt="" /></td>
</tr>
<tr>
    <td><img src="images/blank.gif" height="1" width="16" alt="" /></td>
    <td>
        <div id="snapshot">
        <table width="705" cellpadding="0" cellspacing="0" border="0">
            <tr>
                <%
                   long[] startAndEnd = GraphEngine.parseTimePeriod(timePeriod);
                   String[] sessionsHighLow = StatsAction.getLowAndHigh("sessions", startAndEnd);
                   String[] conversationsHighLow = StatsAction.getLowAndHigh("conversations", startAndEnd);
                   String[] messageHighLow = StatsAction.getLowAndHigh("packet_count", startAndEnd);
                   String[] serversHighLow = StatsAction.getLowAndHigh("server_sessions", startAndEnd);
                   String[] mucHighLow = StatsAction.getLowAndHigh("muc_rooms", startAndEnd);
                   String[] fileTransferHighLow = StatsAction.getLowAndHigh("proxyTransferRate", startAndEnd);
                   String[] serverBytesHighLow = StatsAction.getLowAndHigh("server_bytes", startAndEnd);
                %>
                <td align="left">
                    <table class="stat" width="220" id="table-sessions">
                        <tr>
                            <td colspan="5"><img src="images/blank.gif" width="1" height="2" border="0" /></td>
                        </tr>
                        <tr>
                            <td colspan="5" align="center">
                                <span class="stats-description">
                                    <fmt:message key="dashboard.spotlights.currentusers" />
                                </span>
                            </td>
                        </tr>
                        <tr>
                            <td width="13"><img src="images/blank.gif" width="13" height="1" border="0" /></td>
                            <td align="left" valign="middle" nowrap width="27%">
                                <fmt:message key="dashboard.spotlights.low" />
                                <span id="sessions.low"><%= sessionsHighLow[0]%></span>
                            </td>
                            <td align="center" width="27%">
                                <span class="stats-current" id="sessions.count">
                                    <%= (int)viewer.getCurrentValue(StatisticsModule.SESSIONS_KEY)[0] %>
                                </span>
                            </td>
                            <td align="right" valign="middle" nowrap  width="27%">
                                <fmt:message key="dashboard.spotlights.high" />
                                <span id="sessions.high"><%= sessionsHighLow[1]%></span>
                            </td>
                            <td width="13"><img src="images/blank.gif" width="13" height="1" border="0" /></td>
                        </tr>
                        <tr>
                            <td colspan="5" align="center">
                                <a href="#" onclick="displaySnapshotDetail('sessions'); return false;">
                                    <img width="200" height="50" style="border: 1px solid #b4b4b4;"
                                         src="graph?stat=<%=sessionKey%>&sparkline=true&format=png"
                                         alt="<fmt:message key="dashboard.spotlights.currentusers" />"
                                         id="sparklines-sessions"/><br>
                                    <div align="left" id="sessions-enlarge" class="stat_enlarge_link"><fmt:message key="dashboard.snapshot.enlarge" /></div></a></td>
                        </tr>

                    </table>
                </td>
                <td align="center">
                    <table class="stat" width="220" id="table-conversations">
                        <tr>
                            <td colspan="5"><img src="images/blank.gif" width="1" height="2" border="0" /></td>
                        </tr>
                        <tr>
                            <td colspan="5" align="center">
                                <span class="stats-description">
                                    <fmt:message key="dashboard.spotlights.activeconversations" />
                                </span>
                            </td>
                        </tr>
                        <tr>
                            <td align="center" width="13"><img src="images/blank.gif" width="13" height="1" border="0" /></td>
                            <td align="left" valign="middle" nowrap width="27%">
                                <fmt:message key="dashboard.spotlights.low" />
                                <span id="conversations.low"><%= conversationsHighLow[0]%></span>

                            </td>
                            <td align="center" width="27%">
                                <span class="stats-current" id="conversations.count">
                                    <%= (int)viewer.getCurrentValue(ConversationManager.CONVERSATIONS_KEY)[0] %>
                                </span>
                            </td>
                            <td align="right" valign="middle" nowrap  width="27%">
                                <fmt:message key="dashboard.spotlights.high" />
                                <span id="conversations.high"><%= conversationsHighLow[1]%></span>
                            </td>
                            <td align="center" width="13"><img src="images/blank.gif" width="13" height="1" border="0" /></td>
                        </tr>
                        <tr>
                            <td colspan="5" align="center"><a href="#"
                                onclick="displaySnapshotDetail('conversations'); return false;"><img
                                    width="200" height="50" style="border: 1px solid #b4b4b4;"
                                    src="graph?stat=conversations&sparkline=true&format=png"
                                    alt="<fmt:message key="dashboard.spotlights.activeconversations" />"
                                    id="sparklines-conversations"/><br>
                                    <div align="left" id="conversations-enlarge" class="stat_enlarge_link"><fmt:message key="dashboard.snapshot.enlarge" /></div></a></td>
                        </tr>
                    </table>
                </td>
                <td align="right">
                    <table class="stat" width="220" id="table-packet_count">
                        <tr>
                            <td colspan="5"><img src="images/blank.gif" width="1" height="2" border="0" /></td>
                        </tr>
                        <tr>
                            <td colspan="5" align="center">
                                <span class="stats-description">
                                    <fmt:message key="dashboard.spotlights.packetactivity" />
                                </span>
                            </td>
                        </tr>
                        <tr>
                            <td align="center" width="13"><img src="images/blank.gif" width="13" height="1" border="0" /></td>
                            <td align="left" valign="middle" nowrap width="27%">
                                <fmt:message key="dashboard.spotlights.low" />
                                <span id="packet_count.low"><%= messageHighLow[0]%></span>

                            </td>
                            <td align="center" width="27%">
                                <span class="stats-current" id="packet_count.count">
                                    <%= (int)viewer.getCurrentValue(StatisticsModule.TRAFFIC_KEY)[0] %>
                                </span>
                            </td>
                            <td align="right" valign="middle" nowrap  width="27%">
                                <fmt:message key="dashboard.spotlights.high" />
                                <span id="packet_count.high"><%= messageHighLow[1]%></span>
                            </td>
                            <td align="center" width="13"><img src="images/blank.gif" width="13" height="1" border="0" /></td>
                        </tr>
                        <tr>
                            <td colspan="5" align="center"><a href="#"
                                onclick="displaySnapshotDetail('packet_count'); return false;"><img
                                    width="200" height="50" style="border: 1px solid #b4b4b4;"
                                    src="graph?stat=packet_count&sparkline=true&format=png"
                                    alt="<fmt:message key="dashboard.spotlights.packetactivity" />"
                                    id="sparklines-packet_count"/><br>
                                    <div align="left" id="packet_count-enlarge" class="stat_enlarge_link"><fmt:message key="dashboard.snapshot.enlarge" /></div></a></td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
        </div>

        <br/>


        <!-- Handle SparkLines Stats -->
        <table width="705" cellpadding="0" cellspacing="0" border="0">
            <tr valign="top">
                <td width="371">
                    <table cellpadding="0" cellspacing="0" border="0" width="371" class="quickstats">
                        <thead>
                            <tr>
                                <th colspan="2">
                                    <fmt:message key="dashboard.quickstats" />
                                </th>
                                <th style="font-weight:normal; font-size: 11px;">
                                    <fmt:message key="dashboard.quickstats.low" />
                                </th>
                                <th>
                                </th>
                                <th style="font-weight:normal; font-size: 11px; padding-right: 8px;">
                                    <fmt:message key="dashboard.quickstats.high" />
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td><b><%= viewer.getStatistic("server_sessions")[0].getName() %></b></td>
                                <td width="1%"><img id="sparklines-server_sessions"
                                         src="graph?stat=server_sessions&sparkline=true&color=dark&format=png"
                                         style="border: 1px solid #b4b4b4;" width="180" height="50" /></td>
                                <td id="server_sessions.low" align="center"><%= serversHighLow[0] %></td>
                                <td><img src="images/blank.gif" border="0" width="7" height="1" alt="" /></td>
                                <td id="server_sessions.high" align="center"><%= serversHighLow[1] %></td>
                            </tr>
                            <tr>
                                <td><b><%= viewer.getStatistic("muc_rooms")[0].getName() %></b></td>
                                <td><img id="sparklines-muc_rooms"
                                         src="graph?stat=muc_rooms&sparkline=true&color=dark&format=png"
                                         style="border: 1px solid #b4b4b4;" width="180" height="50" /></td>
                                <td id="muc_rooms.low" align="center"><%= mucHighLow[0] %></td>
                                <td><img src="images/blank.gif" border="0" width="7" height="1" alt="" /></td>
                                <td id="muc_rooms.high" align="center"><%= mucHighLow[1] %></td>
                            </tr>
                            <tr>
                                <td><b><%= viewer.getStatistic("proxyTransferRate")[0].getName() %></b></td>
                                <td width="1%"><img id="sparklines-proxyTransferRate"
                                         src="graph?stat=proxyTransferRate&sparkline=true&color=dark&format=png"
                                         style="border: 1px solid #b4b4b4;" width="180" height="50" /></td>
                                <td id="proxyTransferRate.low" align="center"><%= fileTransferHighLow[0] %></td>
                                <td><img src="images/blank.gif" border="0" width="7" height="1" alt="" /></td>
                                <td id="proxyTransferRate.high" align="center"><%= fileTransferHighLow[1] %></td>
                            </tr>
                            <tr>
                                <td><b><%= viewer.getStatistic("server_bytes")[0].getName() %></b><br />
                                </td>
                                <td width="1%"><img id="sparklines-server_bytes"
                                         src="graph?stat=server_bytes&sparkline=true&color=dark&format=png"
                                         style="border: 1px solid #b4b4b4;" width="180" height="50" /></td>
                                <td id="server_bytes.low" align="center"><%= serverBytesHighLow[0] %></td>
                                <td><img src="images/blank.gif" border="0" width="7" height="1" alt="" /></td>
                                <td id="server_bytes.high" align="center"><%= serverBytesHighLow[1] %></td>
                            </tr>
                        </tbody>
                    </table>
                <br>
                </td>
                <td width="17"><img src="images/blank.gif" width="17" height="1" border="0" alt="" /></td>
                <td width="317">
                    <table cellpadding="0" cellspacing="0" border="0" width="100%" class="jive-table" style="border: 1px solid #cccccc; border-bottom: none;">
                        <thead>
                        <tr>
                            <th>
                                <fmt:message key="dashboard.currentconversations" />
                                (<a href="conversations.jsp"><fmt:message
                                    key="dashboard.currentconversations.details" /></a>)
                            </th>
                        </tr>
                        </thead>
                        <tr>
                            <td style="padding:0px 0px 0px 8px;background-color:#bbbbbb">
                                <table cellspacing="0" cellpadding="0" border="0">
                                <tr>
                                    <td style="width:147px;color:white;font-size:8pt;">
                                        <b><fmt:message key="dashboard.currentconversations.users" /></b>
                                    </td>
                                    <td align="center" style="width:85px;color:white;font-size:8pt;">
                                        <b><fmt:message key="dashboard.currentconversations.lastactivity" /></b>
                                    </td>
                                    <td></td>
                                    <td align="center" style="width:77px;color:white;font-size:8pt;">
                                        <b><fmt:message key="dashboard.currentconversations.messagecount" /></b>
                                    </td>
                                </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td style="padding:0px">
                                <%
                                // Get handle on the Monitoring plugin
                                Collection<Conversation> conversations = conversationManager.getConversations();
                                String displayStyle = "''";
                                if (conversations.isEmpty()) {
                                    displayStyle = "none";
                                %>
                                    <div id="conversations-scroller-none" style="padding: 10px;">
                                        <fmt:message key="dashboard.currentconversations.none" />
                                    </div>
                                <% } %>
                                <div id="conversations-scroller" style="display:<%= displayStyle %>">
                                   <%
                                       List<Conversation> lConversations = Arrays.asList(
                                               conversations.toArray(new Conversation[conversations.size()]));
                                       Collections.sort(lConversations, conversationComparator);
                                       for (int i = 0; i < 6; i++) {
                                           String participantNames = "";
                                           String activityTime = "";
                                           String messageCount = "";
                                           if (lConversations.size() > i) {
                                               Conversation conversation = lConversations.get(i);
                                               if (conversation.getRoom() == null) {
                                                   Collection<JID> participants = conversation.getParticipants();
                                                   for (JID jid : participants) {
                                                       String identifier = jid.toBareJID();
                                                       try {
                                                           identifier = UserNameManager.getUserName(jid, jid.toBareJID());
                                                       } catch (UserNotFoundException e) {
                                                           // Ignore
                                                       }
                                                       participantNames +=
                                                               StringUtils.abbreviate(identifier, 20) +
                                                                       "<br />";
                                                   }
                                               } else {
                                                   // Display "group conversation" with a link to the room occupants
                                                   /*participantNames = LocaleUtils.getLocalizedString(
                                                           "archive.group_conversation", "monitoring", Arrays.asList(
                                                           "<a href='../../muc-room-occupants.jsp?roomName=" +
                                                                   URLEncoder.encode(conversation.getRoom().getNode(),
                                                                           "UTF-8") + "'>", "</a>"));*/

                                                   participantNames = LocaleUtils.getLocalizedString("dashboard.group_conversation", "monitoring");
                                                   participantNames += "<br/>";
                                                   participantNames += "(<i>" + LocaleUtils.getLocalizedString("muc.room.summary.room") + ": <a href='../../muc-room-occupants.jsp?roomName=" + URLEncoder.encode(conversation.getRoom().getNode(),"UTF-8") + "'>" + conversation.getRoom().getNode() + "</a></i>)";
                                               }
                                               activityTime =
                                                       StatsAction.formatTimeLong(conversation.getLastActivity());
                                               messageCount = Integer.toString(conversation.getMessageCount());
                                           }
                                   %>
                                        <div class="conversation"
                                            <% if (i == 3) {%>style="opacity: 0.7;filter:alpha(opacity=10);" <%}%>
                                            <% if (i == 4) {%>style="opacity: 0.4;filter:alpha(opacity=10);" <%}%>
                                            <% if (i == 5) {%>style="opacity: 0.2;filter:alpha(opacity=10);border-bottom:0px;" <%}%>
                                            >
                                            <table cellspacing="0" cellpadding="0" border="0">
                                            <tr>
                                                <td style="width:8px;"><img src="images/blank.gif" height="38" width="8" alt="" border="0" /></td>
                                                <td style="width:147px;">
                                                     <%= participantNames %>
                                                </td>
                                                <td align="center" style="width:85px;"><%= activityTime %></td>
                                                <td><img src="images/blank.gif" width="6" alt="" border="0" /></td>
                                                <td align="center" style="width:77px;"><%= messageCount %></td>
                                            </tr>
                                            </table>
                                        </div>
                                    <%  } %>
                                </div>
                            </td>
                        </tr>
                    </table>
                <br>
                </td>
            </tr>
            <tr>
                <td></td>
                <td></td>
            </tr>
        </table>
    </td>
    <td><img src="images/blank.gif" height="1" width="16" alt="" /></td>
</tr>
</table>

<br>

<script type="text/javascript">
    window.onload = startupConversations;
</script>

</body>
</html>

<%!
    public static final String COOKIE_TIMEPERIOD = "openfire-dashboard-timeperiod";

    /**
     * Sorts conversations by last modified time
     */
    final Comparator<Conversation> conversationComparator = new Comparator<Conversation>() {
        public int compare(Conversation conv1, Conversation conv2) {
           return conv2.getLastActivity().compareTo(conv1.getLastActivity());
        }
    };


%>