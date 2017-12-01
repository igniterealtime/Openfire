package org.jivesoftware.openfire.plugin.rest.controller;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.plugin.rest.entity.SessionsCount;

/**
 * The Class StatisticsController.
 */
public class StatisticsController {
    
    /** The Constant INSTANCE. */
    public static final StatisticsController INSTANCE = new StatisticsController();

    /**
     * Gets the instance.
     *
     * @return the instance
     */
    public static StatisticsController getInstance() {
        return INSTANCE;
    }
    
    /**
     * Gets the concurent sessions.
     *
     * @return the concurent sessions
     */
    public SessionsCount getConcurentSessions() {
        int userSessionsCountLocal = SessionManager.getInstance().getUserSessionsCount(true);
        int userSessionsCountCluster = SessionManager.getInstance().getUserSessionsCount(false);
        
        return new SessionsCount(userSessionsCountLocal, userSessionsCountCluster);
    }
}
