package org.jivesoftware.openfire.plugin.rest.entity;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "occupants")
public class OccupantEntities {
	List<OccupantEntity> occupants;

	public OccupantEntities() {
	}

	public OccupantEntities(List<OccupantEntity> occupants) {
		this.occupants = occupants;
	}

	@XmlElement(name = "occupant")
	public List<OccupantEntity> getOccupants() {
		return occupants;
	}

	public void setOccupants(List<OccupantEntity> occupants) {
		this.occupants = occupants;
	}
}
