package org.jivesoftware.openfire.plugin.servlet;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.UnauthorizedException;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;

/**
 * Jersey HTTP Basic Auth filter
 * 
 * @author Deisss (LGPLv3)
 */
public class AuthFilter implements ContainerRequestFilter {
    /**
     * Apply the filter : check input request, validate or not with user auth
     * 
     * @param containerRequest
     *            The request from Tomcat server
     */
    @Override
    public ContainerRequest filter(ContainerRequest containerRequest) throws WebApplicationException {
        // Get the authentification passed in HTTP headers parameters
        String auth = containerRequest.getHeaderValue("authorization");

        // If the user does not have the right (does not provide any HTTP Basic
        // Auth)
        if (auth == null) {
            throw new WebApplicationException(Status.UNAUTHORIZED);
        }

        // lap : loginAndPassword
        String[] lap = BasicAuth.decode(auth);

        // If login or password fail
        if (lap == null || lap.length != 2) {
            throw new WebApplicationException(Status.UNAUTHORIZED);
        }

        boolean userAdmin = AdminManager.getInstance().isUserAdmin(lap[0], true);

        if (!userAdmin) {
            throw new WebApplicationException(Status.UNAUTHORIZED);
        }

        try {
            AuthFactory.authenticate(lap[0], lap[1]);
        } catch (UnauthorizedException e) {
            throw new WebApplicationException(Status.UNAUTHORIZED);
        } catch (ConnectionException e) {
            throw new WebApplicationException(Status.UNAUTHORIZED);
        } catch (InternalUnauthenticatedException e) {
            throw new WebApplicationException(Status.UNAUTHORIZED);
        }

        return containerRequest;
    }
}
