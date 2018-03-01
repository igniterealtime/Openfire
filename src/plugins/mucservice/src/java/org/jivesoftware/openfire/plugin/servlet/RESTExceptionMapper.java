package org.jivesoftware.openfire.plugin.servlet;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.jivesoftware.openfire.exception.ErrorResponse;
import org.jivesoftware.openfire.exception.MUCServiceException;

/**
 * The Class RESTExceptionMapper.
 */
@Provider
public class RESTExceptionMapper implements ExceptionMapper<MUCServiceException> {

    /**
     * Instantiates a new rEST exception mapper.
     */
    public RESTExceptionMapper() {
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
     */
    public Response toResponse(MUCServiceException exception) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setResource(exception.getResource());
        errorResponse.setMessage(exception.getMessage());
        errorResponse.setException(exception.getException());
        return Response.status(Response.Status.NOT_FOUND).entity(errorResponse).type(MediaType.APPLICATION_XML).build();
    }

}
