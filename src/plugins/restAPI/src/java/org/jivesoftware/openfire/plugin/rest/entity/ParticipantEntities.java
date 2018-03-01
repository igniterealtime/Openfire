package org.jivesoftware.openfire.plugin.rest.entity;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "participants")
public class ParticipantEntities {
    List<ParticipantEntity> participants;

    public ParticipantEntities() {
    }

    public ParticipantEntities(List<ParticipantEntity> participants) {
        this.participants = participants;
    }

    @XmlElement(name = "participant")
    public List<ParticipantEntity> getParticipants() {
        return participants;
    }

    public void setParticipants(List<ParticipantEntity> participants) {
        this.participants = participants;
    }
}
