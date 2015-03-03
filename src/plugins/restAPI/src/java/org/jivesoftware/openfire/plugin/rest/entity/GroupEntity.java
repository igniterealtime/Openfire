package org.jivesoftware.openfire.plugin.rest.entity;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * The Class GroupEntity.
 */
@XmlRootElement(name = "group")
@XmlType(propOrder = { "name", "description" })
public class GroupEntity {

	/** The name. */
	private String name;

	/** The description. */
	private String description;

	/**
	 * Instantiates a new group entity.
	 */
	public GroupEntity() {
	}

	/**
	 * Instantiates a new group entity.
	 *
	 * @param name
	 *            the name
	 * @param description
	 *            the description
	 */
	public GroupEntity(String name, String description) {
		this.name = name;
		this.description = description;
	}

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	@XmlElement
	public String getName() {
		return name;
	}

	/**
	 * Sets the name.
	 *
	 * @param name
	 *            the new name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Gets the description.
	 *
	 * @return the description
	 */
	@XmlElement
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description.
	 *
	 * @param description
	 *            the new description
	 */
	public void setDescription(String description) {
		this.description = description;
	}

}
