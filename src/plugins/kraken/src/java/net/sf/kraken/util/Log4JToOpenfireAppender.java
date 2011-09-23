/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.util;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;
import org.jivesoftware.util.Log;

/**
 * @author Guus der Kinderen, guus@nimbuzz.com
 */
public class Log4JToOpenfireAppender extends AppenderSkeleton {

    /*
	 * (non-Javadoc)
	 *
	 * @see org.apache.log4j.AppenderSkeleton#append(org.apache.log4j.spi.LoggingEvent)
	 */
	@Override
	protected void append(LoggingEvent event)
	{
		final Level l = event.getLevel();
		final String message = (event.getMessage() != null ? event.getMessage().toString() : "");

		Throwable throwable = null;
		if (event.getThrowableInformation() != null)
		{
			throwable = event.getThrowableInformation().getThrowable();
		}

		switch (l.toInt())
		{
			case Priority.OFF_INT:
				// Logging turned off - do nothing.
				break;

			case Priority.FATAL_INT:
			case Priority.ERROR_INT:
				Log.error(message, throwable);
                break;

			case Priority.WARN_INT:
				Log.warn(message, throwable);
                break;

			case Priority.INFO_INT:
				Log.info(message, throwable);
                break;

			default:
				// DEBUG and below (trace, all)
				Log.debug(message, throwable);
				break;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.apache.log4j.AppenderSkeleton#close()
	 */
	//@Override
	public void close()
	{
		// There's nothing here to close.
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.apache.log4j.AppenderSkeleton#requiresLayout()
	 */
	//@Override
	public boolean requiresLayout()
	{
		// we're doing this quick and dirty.
		return false;
	}
    
}
