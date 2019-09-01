<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2018-2019 Jive Software. All rights reserved.
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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>


<html>
    <head>
        <title><fmt:message key="user.contact_addresses.title"/></title>
        <meta name="pageID" content="user-contact-addresses"/>
        <style type="text/css">
            img.clickable {
                cursor: pointer;
            }
        </style>
    </head>
    <body>
        <p><fmt:message key="user.contact_addresses.info_create"/></p>
        <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                    <tr>
                        <th colspan="2">
                            <span id="editContactTitle" style="display:none"><fmt:message key="user.contact_addresses.edit_contact_title"/></span>
                            <span id="newContactTitle"><fmt:message key="user.contact_addresses.new_contact"/></span>
                        </th>
                    </tr>
                </thead>
                <tbody>
                    <tr valign="top">
                        <td>
                            <fmt:message key="user.contact_addresses.contact_type"/>:
                        </td>
                        <td>
                            <select id="contactType">
                                <option value="admin"><fmt:message key="user.contact_addresses.search_admin"/></option>
                                <option value="abuse"><fmt:message key="user.contact_addresses.search_abuse"/></option>
                                <option value="feedback"><fmt:message key="user.contact_addresses.search_feedback"/></option>
                                <option value="sales"><fmt:message key="user.contact_addresses.search_sales"/></option>
                                <option value="security"><fmt:message key="user.contact_addresses.search_security"/></option>
                                <option value="support"><fmt:message key="user.contact_addresses.search_support"/></option>
                            </select>
                        </td>
                    </tr>
                    <tr valign="top">
                        <td>
                            <fmt:message key="user.contact_addresses.address_type"/>:
                        </td>
                        <td>
                            <select id="addressType">
                                <option value="xmpp"><fmt:message key="user.contact_addresses.search_xmpp"/></option>
                                <option value="mail"><fmt:message key="user.contact_addresses.search_mail"/></option> 
                                <option value="url"><fmt:message key="user.contact_addresses.search_url"/></option>  
                            </select>
                        </td>
                    </tr>
                    <tr valign="top">
                        <td>
                            <label for="editContactValue">
                                <fmt:message key="user.contact_addresses.address_value"/>:
                            </label>
                        </td>
                        <td>
                            <input type="text" id="editContactValue" name="propName" size="40" maxlength="100">
                        </td>
                    </tr>
                    <tr valign="top">
                        <td>
                            <label for="editContactDescription">
                                <fmt:message key="user.contact_addresses.description"/>:
                            </label>
                        </td>
                        <td>
                            <textarea id="editContactDescription" cols="45" rows="5" name="" wrap="soft"></textarea>
                        </td>
                    </tr>
                </tbody>
                <tfoot>
                    <tr>
                        <td colspan="2">
                            <input type="button" value="<fmt:message key="global.save" />" onclick="submitEditForm(true);">
                            <input type="button" value="<fmt:message key="global.cancel" />" onclick="submitEditForm(false);">
                        </td>
                    </tr>
                </tfoot>
            </table>
        </div>
        <br/>
        <p><fmt:message key="user.contact_addresses.info"/></p>
        <br/>
        <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                <tr>
                    <th nowrap><label for="searchContactType"><fmt:message key="user.contact_addresses.contact_type"/></label></th>
                    <th nowrap><label for="searchAddressType"><fmt:message key="user.contact_addresses.address_type"/></label></th>
                    <th nowrap><label for="searchValue"><fmt:message key="user.contact_addresses.address_value"/></label></th>
                    <th nowrap><label for="searchDescription"><fmt:message key="user.contact_addresses.description"/></label></th>
                    <th style="text-align:center;"><fmt:message key="user.contact_addresses.edit"/></th>
                    <th style="text-align:center;"><fmt:message key="global.delete"/></th>
                </tr>
                <tr>
                    <td nowrap>
                        <select id="searchContactType" onchange="submitForm();">
                            <option value=""><fmt:message key="user.contact_addresses.search_all"/></option>
                            <option value="admin"><fmt:message key="user.contact_addresses.search_admin"/></option>
                            <option value="abuse"><fmt:message key="user.contact_addresses.search_abuse"/></option>
                            <option value="feedback"><fmt:message key="user.contact_addresses.search_feedback"/></option>
                            <option value="sales"><fmt:message key="user.contact_addresses.search_sales"/></option>
                            <option value="security"><fmt:message key="user.contact_addresses.search_security"/></option>
                            <option value="support"><fmt:message key="user.contact_addresses.search_support"/></option>
                        </select>
                    </td>
                    <td nowrap>
                        <select id="searchAddressType" onchange="submitForm();">
                            <option value=""><fmt:message key="user.contact_addresses.search_all"/></option>
                            <option value="xmpp"><fmt:message key="user.contact_addresses.search_xmpp"/></option>
                            <option value="mail"><fmt:message key="user.contact_addresses.search_mail"/></option>
                            <option value="url"><fmt:message key="user.contact_addresses.search_url"/></option>   
                        </select>
                    </td>
                    <td nowrap>
                        <input type="search"
                            id="searchValue"
                            size="40"
                            value=""/>
                        <img src="images/search-16x16.png"
                            width="16" height="16"
                            class="clickable"
                            alt="Search" title="Search"
                            style="vertical-align: middle;"
                            onclick="submitForm();"
                        >
                    </td>
                    <td nowrap>
                        <input type="search"
                            id="searchDescription"
                            size="20"
                            value=""/>
                        <img src="images/search-16x16.png"
                            width="16" height="16"
                            class="clickable"
                            alt="Search" title="Search"
                            style="vertical-align: middle;"
                            onclick="submitForm();"
                        >
                    </td>
                    <td nowrap>

                    </td>
                    <td nowrap>

                    </td>
                    <td nowrap>

                    </td>
                </tr>
                </thead>
                <tbody>
                <c:set var="rowClass" value="jive-even"/>
                
                </tbody>
            </table>
        </div>
    </body>
</html>
