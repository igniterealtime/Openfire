/**
 * 
 */
package org.jivesoftware.openfire.vcard;

import org.dom4j.Element;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jasonfan
 *
 */
public class HypercareVCardListener implements VCardListener {
	
	private static final Logger Log = LoggerFactory.getLogger(User.class);
	

	/* (non-Javadoc)
	 * @see org.jivesoftware.openfire.vcard.VCardListener#vCardCreated(java.lang.String, org.dom4j.Element)
	 */
	@Override
	public void vCardCreated(String username, Element vCard) {
		Element firstName = vCard.element("N").element("GIVEN");
		Element lastName = vCard.element("N").element("FAMILY");
		String fullName = "";
		if (firstName!= null) {
			if (lastName != null) {
				fullName = firstName.getStringValue() + " " + lastName.getStringValue();
			} else {
				fullName = firstName.getStringValue();
			}
		}
		try {
			User user = UserManager.getInstance().getUser(username);
			user.setName(fullName);
		} catch (UserNotFoundException e) {
			Log.error(e.getMessage(), e);
		}
	}

	/* (non-Javadoc)
	 * @see org.jivesoftware.openfire.vcard.VCardListener#vCardUpdated(java.lang.String, org.dom4j.Element)
	 */
	@Override
	public void vCardUpdated(String username, Element vCard) {
		Element firstName = vCard.element("N").element("GIVEN");
		Element lastName = vCard.element("N").element("FAMILY");
		String fullName = "";
		if (firstName!= null) {
			if (lastName != null) {
				fullName = firstName.getStringValue() + " " + lastName.getStringValue();
			} else {
				fullName = firstName.getStringValue();
			}
		}
		try {
			User user = UserManager.getInstance().getUser(username);
			user.setName(fullName);
		} catch (UserNotFoundException e) {
			Log.error(e.getMessage(), e);
		}
	}

	/* (non-Javadoc)
	 * @see org.jivesoftware.openfire.vcard.VCardListener#vCardDeleted(java.lang.String, org.dom4j.Element)
	 */
	@Override
	public void vCardDeleted(String username, Element vCard) {
		// TODO Auto-generated method stub

	}

}
