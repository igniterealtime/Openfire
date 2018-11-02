package org.jivesoftware.openfire.plugin.service;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.archive.ConversationUtils;
import org.jivesoftware.openfire.reporting.stats.StatsAction;

@Path("monitoring/api")
@Produces(MediaType.APPLICATION_JSON)
public class MonitoringAPI {
    
    private StatsAction statsAction = new StatsAction();
    
    private ConversationUtils conversationUtils = new ConversationUtils();

    @GET
    @Path("/stats/latest")
    public Response getLatest(@QueryParam("count") int count, 
            @QueryParam("mostRecentConversationID") long mostRecentConversationID) {
        return Response.ok(statsAction.getNLatestConversations(
                count, mostRecentConversationID)).build();
    }

    @GET
    @Path("/stats/updated")
    public Response getUpdated(@QueryParam("timePeriod") String timePeriod) {
        return Response.ok(statsAction.getUpdatedStats(timePeriod)).build();
    }
    
    @GET
    @Path("/conversations/{conversationId}")
    public Response getInfo(@PathParam("conversationId") long conversationID) {
        return Response.ok(conversationUtils.getConversationInfo(conversationID, true)).build();
    }
    
    @GET
    @Path("/conversations")
    public Response getConversations() {
        return Response.ok(conversationUtils.getConversations(true)).build();
    }
    
    @GET
    @Path("/buildprogress")
    public Response getBuildProgress() {
        return Response.ok(conversationUtils.getBuildProgress()).build();
    }
}
