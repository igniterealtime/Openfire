package org.jivesoftware.admin.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@SuppressWarnings("serial")
public class UserContactAddressesServlet extends HttpServlet {

    private static final String[] SEARCH_FIELDS = {"searchContactType", "searchAddressType", "searchValue", "searchDescription"};

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.getRequestDispatcher("user-contact-addresses.jsp").forward(request, response);
    }
}