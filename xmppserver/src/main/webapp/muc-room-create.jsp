<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2026 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="java.util.function.BiFunction" %>
<%@ page import="java.util.regex.Pattern" %>
<%@ page import="java.util.regex.Matcher" %>
<%@ page import="static java.nio.charset.StandardCharsets.UTF_8" %>
<%@ page import="java.net.URLEncoder,java.net.URLDecoder" %>
<%
    // OF-31: To select the current conference service automatically on room creation,
    // we extract the active service context from the HTTP Referer header. This allows us
    // to preserve context from top-level sidebar navigation links (which are statically
    // defined in admin-sidebar.xml and do not support dynamic parameters) without having
    // to introduce session state or perform intrusive layout changes.
    //
    // The extraction is done here in the redirector page (muc-room-create.jsp), but the
    // validation is kept inside muc-room-edit-form.jsp to preserve clean separation of
    // concerns and fallback behaviors.

    String referrer = request.getHeader("Referer");
    String serviceParam = "";

    BiFunction<String, String, String> extractParam = (url, key) -> {
        Matcher m = Pattern
            .compile("(?:[?&])" + Pattern.quote(key) + "=([^&#]*)")
            .matcher(url);

        return m.find() ? m.group(1) : null;
    };

    if (referrer != null) {
        String val;

        if ((val = extractParam.apply(referrer, "mucname")) != null) {
            try {
                String decodedVal = URLDecoder.decode(
                    val,
                    UTF_8.name()
                );

                String encodedVal = URLEncoder.encode(
                    decodedVal,
                    UTF_8.name()
                );

                serviceParam = "&mucName=" + encodedVal;
            } catch (Exception e) {
                // Ignore parsing errors and let validation handle fallbacks
            }
        } else if ((val = extractParam.apply(referrer, "mucName")) != null) {
            try {
                String decodedVal = URLDecoder.decode(
                    val,
                    UTF_8.name()
                );

                String encodedVal = URLEncoder.encode(
                    decodedVal,
                    UTF_8.name()
                );

                serviceParam = "&mucName=" + encodedVal;
            } catch (Exception e) {
                // Ignore parsing errors and let validation handle fallbacks
            }
        } else if ((val = extractParam.apply(referrer, "roomJID")) != null) {
            try {
                val = URLDecoder.decode(
                    val,
                    UTF_8.name()
                );

                org.xmpp.packet.JID jid = new org.xmpp.packet.JID(val);

                serviceParam = "&mucName=" +
                    URLEncoder.encode(
                        jid.getDomain(),
                        UTF_8.name()
                    );
            } catch (Exception e) {
                // Ignore parsing errors and let validation handle fallbacks
            }
        }
    }

    response.sendRedirect(
        "muc-room-edit-form.jsp?create=true" + serviceParam
    );
%>

