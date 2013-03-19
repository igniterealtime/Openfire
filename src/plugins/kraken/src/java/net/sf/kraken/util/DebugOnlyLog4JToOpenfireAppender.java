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
 * This is a modified version of the standard appender that forced everything to go to the debug log.
 * TODO: It's possible this could be done via config options but I can't tell.
 * @author Guus der Kinderen, guus@nimbuzz.com
 * @author Daniel Henninger
 */
public class DebugOnlyLog4JToOpenfireAppender extends AppenderSkeleton {

    /*
	 * (non-Javadoc)
	 *
	 * @see org.apache.log4j.AppenderSkeleton#append(org.apache.log4j.spi.LoggingEvent)
	 */
	@Override
	protected void append(LoggingEvent event)
	{
		final Level l = event.getLevel();
		final String message = event.getMessage().toString();

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
				Log.debug(message, throwable);
                break;

			case Priority.WARN_INT:
				Log.debug(message, throwable);
                break;

			case Priority.INFO_INT:
				Log.debug(message, throwable);
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
