package com.rayo.core.verb;

import org.dom4j.Element;

public class ColibriCommand extends AbstractVerbCommand {

    private String videobridge;
   	private String localRTPPort;
   	private String localRTCPPort;
   	private String remoteRTPPort;
   	private String remoteRTCPPort;
   	private String codec;

	public ColibriCommand(String videobridge, String localRTPPort, String localRTCPPort, String remoteRTPPort, String remoteRTCPPort, String codec)
	{
		this.videobridge = videobridge;
		this.localRTPPort = localRTPPort;
		this.localRTCPPort = localRTCPPort;
		this.remoteRTPPort = remoteRTPPort;
		this.remoteRTCPPort = remoteRTCPPort;
		this.codec = codec;
	}

	public String getVideobridge()
	{
		return this.videobridge;
	}

	public String getLocalRTPPort()
	{
		return this.localRTPPort;
	}

	public String getLocalRTCPPort()
	{
		return this.localRTCPPort;
	}

	public String getRemoteRTPPort()
	{
		return this.remoteRTPPort;
	}

	public String getRemoteRTCPPort()
	{
		return this.remoteRTCPPort;
	}

	public String getCodec()
	{
		return this.codec;
	}
}
