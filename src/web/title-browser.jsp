<%--
  - $RCSfile$
  - $Revision$
  - $Date$
--%>

<script language="JavaScript" type="text/javascript">
function closeWin() {
    if (parent) {
        parent.window.close();
    }
    else {
        window.close();
    }
}
</script>

<div class="jive-admin-page-title">
<table cellpadding="2" cellspacing="0" border="0" width="100%">
<tr>
    <td>
        <%= title %>
    </td>
    <td align="right">
        <span class="jive-breadcrumbs">
        <a href="" onclick="closeWin(); return false;">Close Window</a>
        </span>
    </td>
</tr>
</table>
</div>