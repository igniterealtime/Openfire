<%@ taglib uri="core" prefix="c"%>
<%--
  - $RCSfile$
  - $Revision$
  - $Date$
--%>
<c:set var="title" value="${pageScope.title}" />
<c:set var="bc" value="${pageScope.bc}" />
<c:set var="admin" value="${pageScope.admin}" />

<div class="jive-admin-page-title">
<c:set var="sbar" value="${pageScope.ignorebreadcrumbs}" />
<c:set var="image" value="${pageScope.image}" />
<c:if test="${!sbar}">
  <table cellpadding="2" cellspacing="0" border="0" width="100%">
  <tr>
      <td valign="bottom"   align="left">
        <span class="jive-breadcrumbs">
        
        <c:set var="count" value="1" />
        <c:forEach var="row" items="${admin.breadCrumbs}" >
        <c:set var="name" value="${row.key}" />
        <c:set var="value" value="${row.value}" />
        
        <c:choose>
          <c:when test="${count < admin.breadcrumbSize}" >
             <a style="text-decoration: underline;" href="<c:out value="${value}" />"><c:out value="${name}" /></a>
           &raquo;
          </c:when>
        <c:otherwise>
           <c:out value="${name}" />
        </c:otherwise>
        </c:choose>
        <c:set var="count" value="${count+1}" />
       
          
      </c:forEach>        
</span>
      </td>
  </tr>
  </table>
</c:if>
</div>

