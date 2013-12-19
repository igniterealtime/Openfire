package com.rayo.core.verb;

import org.dom4j.Element;

public class ColibriCommand extends AbstractVerbCommand {

    private String videobridge;
   	private Element conference;

	public ColibriCommand(String videobridge, Element conference)
	{
		this.videobridge = videobridge;
		this.conference = conference;
	}

	public String getVideobridge()
	{
		return this.videobridge;
	}

	public Element getConference()
	{
		return this.conference;
	}
}
