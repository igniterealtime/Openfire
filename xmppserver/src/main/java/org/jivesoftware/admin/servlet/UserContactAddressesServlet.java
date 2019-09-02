package org.jivesoftware.admin.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jivesoftware.util.ParamUtils;

@SuppressWarnings("serial")
public class UserContactAddressesServlet extends HttpServlet {

    private static final String[] SEARCH_FIELDS = {"searchContactType", "searchAddressType", "searchValue", "searchDescription"};

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.getRequestDispatcher("user-contact-addresses.jsp").forward(request, response);
    }


    /**
     * Represents the contact addresses being searched for
     */
    public static final class Search {

        private final String contactType;
        private final String addressType;
        private final String value;
        private final String description;
  

        public Search(final HttpServletRequest request) {
            this.contactType = ParamUtils.getStringParameter(request, "searchContactType", "").trim();
            this.addressType= ParamUtils.getStringParameter(request, "searchAddressType", "").trim();
            this.value = ParamUtils.getStringParameter(request, "searchValue", "").trim();
            this.description = ParamUtils.getStringParameter(request, "searchDescription", "").trim();
        }

        public String getContactType() {
            return contactType;
        }

        public String getaddressType() {
            return addressType;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }
}