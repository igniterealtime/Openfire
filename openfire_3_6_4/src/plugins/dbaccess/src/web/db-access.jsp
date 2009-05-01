<%@ page import="org.jivesoftware.database.DbConnectionManager" %>
<%@ page import="java.sql.*" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    // Get parameters
    boolean execute = request.getParameter("execute") != null;
    String sql = request.getParameter("sql");
%>

<html>
    <head>
        <title>DB Access Tool</title>
        <meta name="pageID" content="db-access"/>
    </head>
    <body>

<div class="information">
    Do <b>NOT</b> use this to edit your database unless you know what you are doing.  Openfire will not necessarily
    handle changes to it's database out from under it while it is running.  Most likely you were asked to try a
    couple of commands by whoever recommended this plugin, so please try to stick to that (or read-only activities).
</div>

<div>
    <h3>SQL Statement:</h3>
    <form action="db-access.jsp" method="post">
        <textarea rows="10" cols="80" name="sql"><%= sql != null ? sql : "" %></textarea>
        <br />
        <input type="submit" name="execute" value="Execute SQL"/>
    </form>
</div>

<div>
    <h3>SQL Output:</h3>
    <div style="width: 100%; height: 200px; border: 1.0px solid #000000; overflow: scroll" id="output">
<%
    // Handle an execution
    if (execute) {
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            stmt = con.createStatement();

            // SQL
            out.println("<p>Your query: <b>" + sql + "</b></p>");
            stmt.execute(sql);
            rs = stmt.getResultSet();
            if (rs == null) {
                // print updatecount
                out.println("<p>Result: updateCount = <b>" + stmt.getUpdateCount() + "</p>");
            } else {
                // process resultset
                out.println("<br>Your response:");

                ResultSetMetaData md = rs.getMetaData();
                int count = md.getColumnCount();
                out.println("<table border=1>");
                out.print("<tr>");
                for (int i=1; i<=count; i++) {
                    out.print("<th>");
                    out.print(md.getColumnName(i));
                }
                out.println("</tr>");
                while (rs.next()) {
                    out.print("<tr>");
                    for (int i=1; i<=count; i++) {
                        out.print("<td>");
                        out.print(rs.getString(i));
                    }
                    out.println("</tr>");
                }
            }
            out.println("</table>");
            // rs.close();
        } catch (SQLException ex) {
            out.print("<B>" + getClass() + ": SQL Error:</B>\n" + ex);
//            out.print("<pre>");
//            ex.printStackTrace(out);
//            out.print("</pre>");
        }
        finally {
            DbConnectionManager.closeConnection(rs, stmt, con);
        }
    }
%>
    </div>
</div>

</body>
</html>