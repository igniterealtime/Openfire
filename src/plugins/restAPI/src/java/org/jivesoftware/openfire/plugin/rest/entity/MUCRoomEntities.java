package org.jivesoftware.openfire.plugin.rest.entity;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement(name = "chatRooms")
public class MUCRoomEntities {
    List<MUCRoomEntity> mucRooms;

    public MUCRoomEntities() {
    }

    public MUCRoomEntities(List<MUCRoomEntity> mucRooms) {
        this.mucRooms = mucRooms;
    }

    @XmlElement(name = "chatRoom")
    @JsonProperty(value = "chatRooms")
    public List<MUCRoomEntity> getMucRooms() {
        return mucRooms;
    }

    public void setMucRooms(List<MUCRoomEntity> mucRooms) {
        this.mucRooms = mucRooms;
    }
}
