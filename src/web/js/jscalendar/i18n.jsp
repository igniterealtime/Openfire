<%@ page import="java.util.Calendar,java.text.DateFormat,java.text.SimpleDateFormat,org.jivesoftware.util.JiveGlobals"%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%
    response.setContentType("text/javascript; charset=" + "UTF-8");
    DateFormat fullDay = new SimpleDateFormat("EEEE", JiveGlobals.getLocale());
    DateFormat shortDay = new SimpleDateFormat("EEE", JiveGlobals.getLocale());
    DateFormat fullMonth = new SimpleDateFormat("MMMM", JiveGlobals.getLocale());
    DateFormat shortMonth = new SimpleDateFormat("MMM", JiveGlobals.getLocale());
    Calendar c = Calendar.getInstance();
%>
// full day names (yes we really do want Sunday twice)
Calendar._DN = new Array (
    <% for (int i=1; i<9;i++) {
        c.set(Calendar.DAY_OF_WEEK, i);
    %><%= "\"" + fullDay.format(c.getTime()) + "\"" + (i < 8 ? "," : "") %><% } %>
);

// short day names (yes we really do want Sunday twice)
Calendar._SDN = new Array(
    <% for (int i=1; i<9;i++) {
        c.set(Calendar.DAY_OF_WEEK, i);
    %><%= "\"" + shortDay.format(c.getTime()) + "\"" + (i < 8 ? "," : "") %><% } %>
);

Calendar._FD = 0;

// full month names
Calendar._MN = new Array(
    <% for (int i=0; i<12;i++) {
        c.set(Calendar.MONTH, i);
    %><%= "\"" + fullMonth.format(c.getTime()) + "\"" + (i < 11 ? "," : "") %><% } %>
);

// short month names
Calendar._SMN = new Array(
    <% for (int i=0; i<12;i++) {
        c.set(Calendar.MONTH, i);
    %><%= "\"" + shortMonth.format(c.getTime()) + "\"" + (i < 11 ? "," : "") %><% } %>
);

// tooltips
Calendar._TT = {};
Calendar._TT["INFO"] = "<%= LocaleUtils.getLocalizedString("calendar.info")%>";
Calendar._TT["ABOUT"] = "<%= LocaleUtils.getLocalizedString("calendar.about")%>";
Calendar._TT["PREV_YEAR"] = "<%= LocaleUtils.getLocalizedString("calendar.prev_year")%>";
Calendar._TT["PREV_MONTH"] = "<%= LocaleUtils.getLocalizedString("calendar.prev_month")%>";
Calendar._TT["GO_TODAY"] = "<%= LocaleUtils.getLocalizedString("calendar.go_today")%>";
Calendar._TT["NEXT_MONTH"] = "<%= LocaleUtils.getLocalizedString("calendar.next_month")%>";
Calendar._TT["NEXT_YEAR"] = "<%= LocaleUtils.getLocalizedString("calendar.next_year")%>";
Calendar._TT["SEL_DATE"] = "<%= LocaleUtils.getLocalizedString("calendar.select_date")%>";
Calendar._TT["DRAG_TO_MOVE"] = "<%= LocaleUtils.getLocalizedString("calendar.drag_to_move")%>";
Calendar._TT["PART_TODAY"] = "<%= LocaleUtils.getLocalizedString("calendar.part_today")%>";
Calendar._TT["DAY_FIRST"] = "<%= LocaleUtils.getLocalizedString("calendar.day_first")%>";
Calendar._TT["WEEKEND"] = "<%= LocaleUtils.getLocalizedString("calendar.weekend")%>";
Calendar._TT["CLOSE"] = "<%= LocaleUtils.getLocalizedString("calendar.close")%>";
Calendar._TT["TODAY"] = "<%= LocaleUtils.getLocalizedString("calendar.today")%>";
Calendar._TT["TIME_PART"] = "<%= LocaleUtils.getLocalizedString("calendar.time_part")%>";
Calendar._TT["DEF_DATE_FORMAT"] = "%Y-%m-%d";
Calendar._TT["TT_DATE_FORMAT"] = "%a, %b %e";
Calendar._TT["WK"] = "wk";
Calendar._TT["TIME"] = "<%= LocaleUtils.getLocalizedString("calendar.time")%>";