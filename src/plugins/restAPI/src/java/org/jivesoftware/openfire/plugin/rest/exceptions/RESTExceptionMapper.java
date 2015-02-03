package org.jivesoftware.openfire.plugin.rest.exceptions;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class RESTExceptionMapper.
 */
@Provider
public class RESTExceptionMapper implements ExceptionMapper<ServiceException> {

	/** The log. */
	private static Logger LOG = LoggerFactory.getLogger(RESTExceptionMapper.class);

	/**
	 * Instantiates a new REST exception mapper.
	 */
	public RESTExceptionMapper() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
	 */
	public Response toResponse(ServiceException exception) {
		ErrorResponse errorResponse = new ErrorResponse();
		errorResponse.setRessource(exception.getRessource());
		errorResponse.setMessage(exception.getMessage());
		errorResponse.setException(exception.getException());
		LOG.error(
				exception.getException() + ": " + exception.getMessage() + " with ressource "
						+ exception.getRessource(), exception.getException());
		return Response.status(exception.getStatus()).entity(errorResponse).type(MediaType.APPLICATION_XML).build();
	}

}
