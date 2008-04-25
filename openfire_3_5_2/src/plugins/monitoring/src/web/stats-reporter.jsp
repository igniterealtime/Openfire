<%@ page import="org.jivesoftware.openfire.reporting.stats.StatsViewer" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.openfire.stats.Statistic"%>
<%@ page import="javax.servlet.http.Cookie"%>
<%@ page import="java.util.Arrays"%>
<%@ page import="java.util.Collections"%>
<%@ page import="java.util.Comparator"%>
<%@ page import="java.util.List"%>
<%@ page import="org.jivesoftware.openfire.plugin.MonitoringPlugin" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    List<String> statList = Arrays.asList(getStatsViewer().getAllHighLevelStatKeys());
    Collections.sort(statList, statisticComporator);

    String dateRangeType = ParamUtils.getParameter(request,"dateRangeType");
    String dateRangePreset = ParamUtils.getParameter(request,"dateRangePreset");
    String fromDateParam = ParamUtils.getParameter(request,"fromDate");
    String toDateParam = ParamUtils.getParameter(request,"toDate");

    String timePeriod = "last60minutes";
    Cookie timePeriodCookie = CookieUtils.getCookie(request, COOKIE_TIMEPERIOD);
    if (timePeriodCookie != null) {
        String cookieValue = timePeriodCookie.getValue();
        if (cookieValue.startsWith("this") || cookieValue.startsWith("last")) {
            dateRangeType = "preset";
            dateRangePreset = cookieValue;
        }
        else {
            String[] dates = cookieValue.split("to");
            dateRangeType = "specific";
            if (dates.length == 2) {
                fromDateParam = dates[0];
                toDateParam = dates[1];
            }
        }
        timePeriod = cookieValue;
    }

    // Set parameter defaults
    if (dateRangeType == null) {
        dateRangeType = "preset";
    }

%>

<fmt:setLocale value="<%= JiveGlobals.getLocale().getLanguage() %>"/>

<html>
<head>
    <title><fmt:message key="allreports.title" /></title>
    <meta name="pageID" content="stats-reporter"/>
    <script src="/js/prototype.js" type="text/javascript"></script>
    <script src="/js/effects.js" type="text/javascript"></script>
    <script src="/js/scriptaculous.js" type="text/javascript"></script>

    <style type="text/css">
        .dateerror { font-weight: bold; color:red;}
        .dateerrorinput {background-color:red};
        .datenormal { font-weight: normal; color:black;}
    </style>

    <style type="text/css">@import url( /js/jscalendar/calendar-win2k-cold-1.css );</style>
    <script type="text/javascript" src="/js/jscalendar/calendar.js"></script>
    <script type="text/javascript" src="/js/jscalendar/i18n.jsp"></script>
    <script type="text/javascript" src="/js/jscalendar/calendar-setup.js"></script>

    <script type="text/javascript">

        var datesAreValid = true;

        var stats = {};
        <%
        for (String statKey : statList) { %><% Statistic stat = getStatsViewer().getStatistic(statKey)[0]; %>
            stats["<%= statKey %>"] = {"name":"<%= stat.getName() %>","description":"<%= stat.getDescription() %>"};
        <% } %>

        var currentStat = '<%= STAT_DEFAULT %>';
        var currentTimePeriod = '<%= timePeriod %>';
        function viewStat(stat) {
            timePeriod = '';
            timePeriodName = '';
            if ($('drt01').checked == true) {
                // get a preset value
                drselect = $('dateRangePreset');
                timePeriod = drselect[drselect.selectedIndex].value;
                timePeriodName = drselect[drselect.selectedIndex].text;
                datesAreValid = true;
            } else {
                // get a date range
                validateStartAndEndDate();
                if (datesAreValid) {
                    timePeriod = $('fromDate').value + 'to' + $('toDate').value;
                    timePeriodName = $('fromDate').value + ' to ' + $('toDate').value;
                } else {
                    return;
                }
            }

            if (datesAreValid && (stat != currentStat || timePeriod != currentTimePeriod)) {
                var viewElement = $('viewer');
                var pdfViewElement = $('pdfviewer');
                var pdfViewAllElement = $('pdfviewerall');
                viewElement.style.display = "none";
                var i = new Image();
                i.onload = function() {
                    viewElement.src = i.src;
                    pdfViewElement.href = i.src + "&pdf=true";
                    pdfViewAllElement.href = i.src + "&pdf=all";
                    $('graph-header').innerHTML = stats[stat].name + ': ' + timePeriodName;
                    $('graph-description').innerHTML = '<b>' + stats[stat].name + '</b><br /><br />' +
                            stats[stat].description;
                    Effect.Appear('viewer');
                    currentStat = stat;
                    currentTimePeriod = timePeriod;
                    createCookie("<%= COOKIE_TIMEPERIOD %>",currentTimePeriod,1000);
                }
                var d = new Date();
                var t = d.getTime()
                i.src = "graph?stat=" + stat + "&date=" + t + '&timeperiod=' + timePeriod + '&width=500&height=250';
            }
        }


        function createCookie(name,value,days) {
            if (days) {
		        var date = new Date();
                date.setTime(date.getTime()+(days*24*60*60*1000));
                var expires = "; expires="+date.toGMTString();
            }
            else {
                var expires = "";
            }
            document.cookie = name+"="+value+expires+"; path=/";
        }

        function writeTimePeriod() {
            if ($('drt01').checked == true) {
                drselect = $('dateRangePreset');
                document.write(drselect[drselect.selectedIndex].text);
            }
            else {
                // get a date range
                if ($('fromDate').value != '' && $('toDate').value != '') {
                    document.write($('fromDate').value + ' to ' + $('toDate').value);
                }
            }
        }

        function checkPreset() {
            document.statsForm.dateRangeType[0].checked=true;
            document.statsForm.dateRangePreset.disabled = false;
            document.statsForm.fromDate.disabled = true;
            document.statsForm.toDate.disabled = true;
            viewStat(currentStat);
        }

        function checkSpecific() {
            document.statsForm.dateRangeType[1].checked=true
            document.statsForm.fromDate.disabled = false;
            document.statsForm.toDate.disabled = false;
            document.statsForm.dateRangePreset.disabled = true;
            viewStat(currentStat);
        }

        function validateStartAndEndDate() {
            if ($('fromDate').value != '' && $('toDate').value != '') {
                fromDate = $('fromDate').value;
                toDate = $('toDate').value;

                if (!isValidDate(fromDate)) {
                    $('fromDateTitle').className = 'dateerror';
                    $('fromDate').className = 'dateerrorinput';
                    datesAreValid = false;
                    return;
                }

                if (!isValidDate(toDate)) {
                    $('toDateTitle').className = 'dateerror';
                    $('toDate').className = 'dateerrorinput';
                    datesAreValid = false;
                    return;
                }

                if (!isValidCombination(fromDate, toDate)) {
                    $('toDateTitle').className = 'dateerror';
                    $('fromDateTitle').className = 'dateerror';
                    $('toDate').className = 'dateerrorinput';
                    $('fromDate').className = 'dateerrorinput';
                    datesAreValid = false;
                    return;
                }

                datesAreValid = true;
                $('toDate').className = '';
                $('fromDate').className = '';
                $('toDateTitle').className = 'datenormal';
                $('fromDateTitle').className = 'datenormal';
                return;
            }
            else {
                datesAreValid = false;
                return;
            }
        }

        function isValidCombination(startdate, enddate) {
            if (!getDate(startdate) || !getDate(enddate)) {
                return false;
            }
            else {
                return getDate(startdate) < getDate(enddate);
            }
        }

        function getDate(datestring) {
            dateSplit = datestring.split('/');
            if (dateSplit.length < 3) {
                return false;
            }

            var monthLength = new Array(31,28,31,30,31,30,31,31,30,31,30,31);
            var day = parseInt(dateSplit[1]);
	        var month = parseInt(dateSplit[0]);
	        var year = parseInt(dateSplit[2]);

            if (!day || !month || !year)
                return false;

            year = year + 2000;

            if (year/4 == parseInt(year/4))
                monthLength[1] = 29;

            if (day > monthLength[month-1])
                return false;

            monthLength[1] = 28;

            var now = new Date();
            now = now.getTime(); //NN3

            var dateToCheck = new Date();
            dateToCheck.setYear(year);
            dateToCheck.setMonth(month-1);
            dateToCheck.setDate(day);
            var checkDate = dateToCheck.getTime();
            if (now < checkDate) {
                return false;
            }
            else {
                return checkDate;
            }
        }

        function isValidDate(datestring) {
            d = getDate(datestring);
            if (!d) {
                return false;
            }
            else {
                var now = new Date();
                now = now.getTime(); //NN3
                if (now < d) {
                    return false;
                } else {
                    return true;
                }
            }
        }
    </script>
    <style type="text/css">
	    @import "style/style.css";
    </style>
</head>

<body>

<table cellpadding="0" cellspacing="0" border="0" width="753">
<tr>
    <td width="180" valign="top">
        <table cellpadding="0" cellspacing="0" border="0" width="180" class="jive-table">
        <thead>
        <tr>
            <th>
                <fmt:message key="allreports.daterange" />
            </th>
        </tr>
        </thead>
        <tr style="border-bottom: none"><form action="" id="statsForm" name="statsForm">
            <td colspan="2" valign="top" width="180">
                <table>
                    <tr>
                        <td width="1%" valign="top">
                            <input type="radio" name="dateRangeType" value="preset" id="drt01"
                             onclick="checkPreset();"
                             <%= ("preset".equals(dateRangeType) ? "checked" : "") %>>
                        </td>
                        <td width="99%" valign="top">
                            <label for="drt01"><fmt:message key="allreports.daterange.preset" /></label></td>
                    </tr>
                    <tr>
                        <td colspan="2" align="right">
                            <select size="1" name="dateRangePreset" id="dateRangePreset" onchange="checkPreset();">
                                <option value="last60minutes"
                                        <%= ("last60minutes".equals(dateRangePreset) ? "selected" : "") %>
                                    ><fmt:message key="allreports.daterange.preset.last60minutes" /> </option>
                                <option value="last24hours"
                                        <%= ("last24hours".equals(dateRangePreset) ? "selected" : "") %>
                                    ><fmt:message key="allreports.daterange.preset.last24hours" /> </option>
                                <option value="thisweek"
                                        <%= ("thisweek".equals(dateRangePreset) ? "selected" : "") %>
                                    ><fmt:message key="allreports.daterange.preset.thisweek" /> </option>
                                <option value="last7days"
                                        <%= ("last7days".equals(dateRangePreset) ? "selected" : "") %>
                                    ><fmt:message key="allreports.daterange.preset.last7days" /> </option>
                                <option value="lastweek"
                                        <%= ("lastweek".equals(dateRangePreset) ? "selected" : "") %>
                                    ><fmt:message key="allreports.daterange.preset.lastweek" /> </option>
                                <option value="thismonth"
                                        <%= ("thismonth".equals(dateRangePreset) ? "selected" : "") %>
                                    ><fmt:message key="allreports.daterange.preset.thismonth" /> </option>
                                <option value="lastmonth"
                                        <%= ("lastmonth".equals(dateRangePreset) ? "selected" : "") %>
                                    ><fmt:message key="allreports.daterange.preset.lastmonth" /> </option>
                                <option value="last3months"
                                        <%= ("last3months".equals(dateRangePreset) ? "selected" : "") %>
                                    ><fmt:message key="allreports.daterange.preset.last3months" /> </option>
                            </select>
                        </td>
                    </tr>
                    <tr valign="top">
                        <td width="1%" valign="top">
                            <input type="radio" name="dateRangeType" value="specific" id="drt02"
                             onclick="checkSpecific();"
                             <%= ("specific".equals(dateRangeType) ? "checked" : "") %>>
                        </td>
                        <td width="99%"><label for="drt02"><fmt:message key="allreports.daterange.specific" /></label></td>
                    </tr>
                    <tr valign="top">
                        <td colspan="2" align="right">
                            <table border="0" cellspacing="0" cellpadding="0">
                            <tr>
                                <td align="right" id="fromDateTitle" class="datenormal">
                                    <fmt:message key="allreports.daterange.specific.startdate" />
                                </td>
                                <td>
                                    <input type="text" size="10" name="fromDate" id="fromDate" maxlength="10"
                                     onclick="checkSpecific();"
                                     onchange="viewStat(currentStat);"
                                     <%= ("specific".equals(dateRangeType) ? "" : "disabled") %>
                                     value="<%= (fromDateParam != null ? fromDateParam : "") %>"
                                     >
                                </td>
                                <td>
                                    &nbsp;<img src="images/icon_calendarpicker.gif" id="fromDateCal" />
                                </td>
                            </tr>
                            <tr>
                                <td align="right" id="toDateTitle" class="datenormal">
                                    <fmt:message key="allreports.daterange.specific.enddate" />
                                </td>
                                <td>
                                    <input type="text" size="10" name="toDate" id="toDate" maxlength="10"
                                     onclick="checkSpecific();"
                                     onchange="viewStat(currentStat);"
                                     <%= ("specific".equals(dateRangeType) ? "" : "disabled") %>
                                     value="<%= (toDateParam != null ? toDateParam : "") %>">
                                </td>
                                <td>
                                    &nbsp;<img src="images/icon_calendarpicker.gif" id="toDateCal" />
                                </td>
                            </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </form></tr>
        </table>
        <br />
        <table cellpadding="0" cellspacing="0" border="0" width="180" class="jive-table"
               style="border-bottom: 1px solid #bbb;">
        <thead>
        <tr>
            <th>
                <fmt:message key="allreports.selectreport" />
            </th>
        </tr>
        </thead>
        <tbody>
        <% for (String stat : statList) { %>
        <tr
            id="statDetail<%= stat %>"
            <% if (stat.equalsIgnoreCase(STAT_DEFAULT)) { %>
                class="allreports_report_selected"
            <% }
               else { %>
                class="allreports_report_default"
            <% } %>
            >
            <td valign="top"
                onclick="viewStat('<%= stat %>');toggleSelected('<%= stat %>'); return false;"
                onmouseover="toggleMouseOver('<%= stat %>');"
                onmouseout="toggleMouseOver('<%= stat %>');">
                <%= getStatsViewer().getStatistic(stat)[0].getName()%>
            </td>
        </tr>
        <% } %>

        <script type="text/javascript">
            var selectedStat = '<%= STAT_DEFAULT %>';
            function toggleSelected(statname) {
                $('statDetail' + selectedStat).className = 'allreports_report_default';
                $('statDetail' + statname).className = 'allreports_report_selected';
                selectedStat = statname;
            }
            function toggleMouseOver(statname) {
                if (statname != selectedStat) {
                    if ($('statDetail' + statname).className == 'allreports_report_hover') {
                        $('statDetail' + statname).className = 'allreports_report_default';
                    }
                    else {
                        $('statDetail' + statname).className = 'allreports_report_hover';
                    }
                }
            }

        </script>

        </tbody>
        </table>

        <br />
        <table cellpadding="0" cellspacing="0" border="0" width="180" class="jive-table">
        <thead>
        <tr>
            <th style="border-bottom:none">
                <table cellspacing='0' cellpadding='0' border='0' align="center" style="padding:0px;border-bottom:none">
                <tr>
                    <td colspan="2" style="padding:0px;border-bottom:none; font-size: 12px;">
                        <b><fmt:message key="allreports.download.allreports" /></b>
                    </td>
                    <td style="padding:0px;border-bottom:none"><img
                            src="images/blank.gif" alt="" border="0" height="1" width="1" /></td>
                </tr>
                </table>
            </th>
        </tr>
        <tr>
            <th style="border-bottom : 1px #ccc solid;">
                <table cellspacing='0' cellpadding='0' border='0' align="center" style="padding:0px;border-bottom:none">
                <tr>
                    <td style="padding:0px;border-bottom:none; font-size: 12px;"><img
                            src="images/icon_pdf.gif"
                            alt="<fmt:message key="allreports.download.allreports.pdf.format" />" border="0" /></td>
                    <td style="padding:0px;border-bottom:none; font-size: 12px;">&nbsp;
                        <a target="_blank" href="graph?stat=<%= STAT_DEFAULT %>&timeperiod=<%= timePeriod %>&pdf=all"
                           id="pdfviewerall"><fmt:message key="allreports.download.allreports.pdf" /></a></td>
                </tr>
                </table>
            </th>
        </thead>
        </table>


    </td>
    <td width="20">&nbsp;</td>
    <td valign="top" width="553">
        <table cellpadding="0" cellspacing="0" border="0" width="553" class="jive-table">
        <thead>
        <tr>
            <th width="70%" id="graph-header">
                <%= getStatsViewer().getStatistic(STAT_DEFAULT)[0].getName() %>:
                <script type="text/javascript">writeTimePeriod();</script>
            </th>
            <th style="text-align:right; border-bottom : 1px #ccc solid; padding:0px;" nowrap>
                <table cellspacing='0' cellpadding='0' border='0' align="right" style="padding:0px;border-bottom:none;">
                <tr>
                    <td style="padding:0px;border-bottom:none;font-size: 11px;" nowrap>
                        <fmt:message key="allreports.download.singlereport" />&nbsp;&nbsp;</td>
                    <td style="padding:0px;border-bottom:none;"><img
                            src="images/icon_pdf.gif" alt="PDF Format" border="0" /></td>
                    <td style="padding:0px;border-bottom:none;font-size: 11px;">&nbsp;
                        <a target="_blank" href="graph?stat=<%= STAT_DEFAULT %>&timeperiod=<%= timePeriod %>&pdf=true"
                           id="pdfviewer"><fmt:message key="allreports.download.singlereport.pdf" /></a></td>
                    <td style="padding:0px;border-bottom:none;"><img
                            src="images/blank.gif" alt="" border="0" height="1" width="8" /></td>
                </tr>
                </table>
            </th>
        </tr>
        </thead>
        <tr>
            <td colspan="2" style="padding:0px; border-bottom:0px"><img src="/images/blank.gif" alt="" border="0" height="1" width="500" /></td>
        </tr>
        <tr>
            <td colspan="2">
                <table class="noclass">
                <tr>
                    <td><img src="/images/blank.gif" alt="" border="0" height="280" width="1" /></td>
                    <td><img id="viewer" src="graph?stat=<%= STAT_DEFAULT %>&timeperiod=<%= timePeriod %>&width=500&height=250" border="0" /></td>
                </tr>

                <!-- <tr>
                    <td colspan="2">
                        <div id="graph-description_NEW" style="padding: 10px 10px 20px 15px;">
                            <%= getStatsViewer().getStatistic(STAT_DEFAULT)[0].getDescription() %>
                        </div>
                    </td>
                </tr> -->

                </table>
            </td>
        </tr>
        </table>
        <br />

        <table cellpadding="0" cellspacing="0" border="0" width="553" class="jive-table">
        <thead>
        <tr>
            <th style="border-bottom : 1px #ccc solid">
                <fmt:message key="allreports.reportinformation" />
            </th>
        </tr>
        </thead>
        <tr>
            <td align="left" id="graph-description">
                <b><%= getStatsViewer().getStatistic(STAT_DEFAULT)[0].getName() %></b><br /><br />

                <%= getStatsViewer().getStatistic(STAT_DEFAULT)[0].getDescription() %>
            </td>
        </tr>
        </table>
    </td>
</tr>
</table>
<br>

<script type="text/javascript" >
    Calendar.setup(
    {
        inputField  : "fromDate",       // ID of the input field
        ifFormat    : "%m/%d/%y",       // the date format
        button      : "fromDateCal",    // ID of the button
        onUpdate    :  viewStat
    });

    Calendar.setup(
    {
        inputField  : "toDate",         // ID of the input field
        ifFormat    : "%m/%d/%y",       // the date format
        button      : "toDateCal",      // ID of the button
        onUpdate    :  viewStat
    });

</script>


</body>
</html>

<%!
    private StatsViewer statsViewer;

    public static final String STAT_DEFAULT = "sessions";
    public static final String COOKIE_TIMEPERIOD = "openfire-reporting-timeperiod";
    public StatsViewer getStatsViewer() {
        if (statsViewer == null) {
            MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("monitoring");
            statsViewer = (StatsViewer) plugin.getModule(StatsViewer.class);
        }
        return statsViewer;
    }
    /**
     * Sorts Statistics by Name.
     */
    final Comparator<String> statisticComporator = new Comparator<String>() {
        public int compare(String stat1, String stat2) {
            String statName1 = getStatsViewer().getStatistic(stat1)[0].getName();
            String statName2 = getStatsViewer().getStatistic(stat2)[0].getName();

            return statName1.toLowerCase().compareTo(statName2.toLowerCase());
        }
    };
%>