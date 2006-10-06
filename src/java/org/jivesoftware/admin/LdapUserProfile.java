/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.admin;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.JiveGlobals;

/**
 * Bean that stores the vcard mapping. It is also responsible for saving the mapping
 * as an XML property and retrieving it.
 *
 * @author Gaston Dombiak
 */
public class LdapUserProfile {

    private String name;
    private String email;
    private String fullName;
    private String nickname;
    private String birthday;
    private String home_street;
    private String home_city;
    private String home_state;
    private String home_zip;
    private String home_country;
    private String home_phone;
    private String home_mobile;
    private String home_fax;
    private String home_pager;
    private String business_street;
    private String business_city;
    private String business_state;
    private String business_zip;
    private String business_country;
    private String business_job_title;
    private String business_department;
    private String business_phone;
    private String business_mobile;
    private String business_fax;
    private String business_pager;
    private String business_web_page;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getHome_street() {
        return home_street;
    }

    public void setHome_street(String home_street) {
        this.home_street = home_street;
    }

    public String getHome_city() {
        return home_city;
    }

    public void setHome_city(String home_city) {
        this.home_city = home_city;
    }

    public String getHome_state() {
        return home_state;
    }

    public void setHome_state(String home_state) {
        this.home_state = home_state;
    }

    public String getHome_zip() {
        return home_zip;
    }

    public void setHome_zip(String home_zip) {
        this.home_zip = home_zip;
    }

    public String getHome_country() {
        return home_country;
    }

    public void setHome_country(String home_country) {
        this.home_country = home_country;
    }

    public String getHome_phone() {
        return home_phone;
    }

    public void setHome_phone(String home_phone) {
        this.home_phone = home_phone;
    }

    public String getHome_mobile() {
        return home_mobile;
    }

    public void setHome_mobile(String home_mobile) {
        this.home_mobile = home_mobile;
    }

    public String getHome_fax() {
        return home_fax;
    }

    public void setHome_fax(String home_fax) {
        this.home_fax = home_fax;
    }

    public String getHome_pager() {
        return home_pager;
    }

    public void setHome_pager(String home_pager) {
        this.home_pager = home_pager;
    }

    public String getBusiness_street() {
        return business_street;
    }

    public void setBusiness_street(String business_street) {
        this.business_street = business_street;
    }

    public String getBusiness_city() {
        return business_city;
    }

    public void setBusiness_city(String business_city) {
        this.business_city = business_city;
    }

    public String getBusiness_state() {
        return business_state;
    }

    public void setBusiness_state(String business_state) {
        this.business_state = business_state;
    }

    public String getBusiness_zip() {
        return business_zip;
    }

    public void setBusiness_zip(String business_zip) {
        this.business_zip = business_zip;
    }

    public String getBusiness_country() {
        return business_country;
    }

    public void setBusiness_country(String business_country) {
        this.business_country = business_country;
    }

    public String getBusiness_job_title() {
        return business_job_title;
    }

    public void setBusiness_job_title(String business_job_title) {
        this.business_job_title = business_job_title;
    }

    public String getBusiness_department() {
        return business_department;
    }

    public void setBusiness_department(String business_department) {
        this.business_department = business_department;
    }

    public String getBusiness_phone() {
        return business_phone;
    }

    public void setBusiness_phone(String business_phone) {
        this.business_phone = business_phone;
    }

    public String getBusiness_mobile() {
        return business_mobile;
    }

    public void setBusiness_mobile(String business_mobile) {
        this.business_mobile = business_mobile;
    }

    public String getBusiness_fax() {
        return business_fax;
    }

    public void setBusiness_fax(String business_fax) {
        this.business_fax = business_fax;
    }

    public String getBusiness_pager() {
        return business_pager;
    }

    public void setBusiness_pager(String business_pager) {
        this.business_pager = business_pager;
    }

    public String getBusiness_web_page() {
        return business_web_page;
    }

    public void setBusiness_web_page(String business_web_page) {
        this.business_web_page = business_web_page;
    }

    /**
     * Sets default mapping values when using an Active Directory server.
     */
    public void initForActiveDirectory() {
        name = "{cn}";
        email = "{mail}";
        fullName = "{displayName}";
        nickname = "";
        birthday = "";
        home_street = "{homePostalAddress}";
        home_city = "";
        home_state = "";
        home_zip = "{homeZip}";
        home_country = "{countryCode}";
        home_phone = "{homePhone}";
        home_mobile = "";
        home_fax = "";
        home_pager = "";
        business_street = "{postalAddress}";
        business_city = "{l}";
        business_state = "{st}";
        business_zip = "{postalCode}";
        business_country = "{countryCode}";
        business_job_title = "{title}";
        business_department = "{department}";
        business_phone = "{otherTelephone}";
        business_mobile = "{mobile}";
        business_fax = "{facsimileTelephoneNumber}";
        business_pager = "{pager}";
        business_web_page = "";
    }

    /**
     * Sets default mapping values when using an Active Directory server.
     */
    public void initForOpenLDAP() {
        name = "{cn}";
        email = "{mail}";
        fullName = "{displayName}";
        nickname = "{uid}";
        birthday = "";
        home_street = "{homePostalAddress}";
        home_city = "";
        home_state = "";
        home_zip = "";
        home_country = "";
        home_phone = "{homePhone}";
        home_mobile = "";
        home_fax = "";
        home_pager = "";
        business_street = "{postalAddress}";
        business_city = "{l}";
        business_state = "{st}";
        business_zip = "{postalCode}";
        business_country = "";
        business_job_title = "{title}";
        business_department = "{departmentNumber}";
        business_phone = "{telephoneNumber}";
        business_mobile = "{mobile}";
        business_fax = "";
        business_pager = "{pager}";
        business_web_page = "";
    }

    /**
     * Saves current configuration as XML properties.
     */
    public void saveXMLProperties() {
        Element vCard = DocumentHelper.createElement(QName.get("vCard", "vcard-temp"));
        Element subelement;

        // Add name
        if (name != null && name.trim().length() > 0) {
            subelement = vCard.addElement("N");
            Element given = subelement.addElement("GIVEN");
            if (name.trim().startsWith("{")) {
                String xmlValue = name.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                given.addAttribute("attrs", xmlValue.trim());
                given.setText("{0}");
            }
            else {
                given.setText(name.trim());
            }
        }
        // Add email
        if (email != null && email.trim().length() > 0) {
            subelement = vCard.addElement("EMAIL");
            subelement.addElement("INTERNET");
            Element userID = subelement.addElement("USERID");
            if (email.trim().startsWith("{")) {
                String xmlValue = email.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                userID.addAttribute("attrs", xmlValue.trim());
                userID.setText("{0}");
            }
            else {
                userID.setText(name.trim());
            }
        }
        // Add Full Name
        subelement = vCard.addElement("FN");
        if (fullName.trim().startsWith("{")) {
            String xmlValue = fullName.replaceAll("\\{", "");
            xmlValue = xmlValue.replaceAll("}", "");
            subelement.addAttribute("attrs", xmlValue.trim());
            subelement.setText("{0}");
        }
        else {
            subelement.setText(fullName.trim());
        }
        // Add nickname
        if (nickname != null && nickname.trim().length() > 0) {
            subelement = vCard.addElement("NICKNAME");
            if (nickname.trim().startsWith("{")) {
                String xmlValue = nickname.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                subelement.addAttribute("attrs", xmlValue.trim());
                subelement.setText("{0}");
            }
            else {
                subelement.setText(nickname.trim());
            }
        }
        // Add birthday
        if (birthday != null && birthday.trim().length() > 0) {
            subelement = vCard.addElement("BDAY");
            if (birthday.trim().startsWith("{")) {
                String xmlValue = birthday.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                subelement.addAttribute("attrs", xmlValue.trim());
                subelement.setText("{0}");
            }
            else {
                subelement.setText(birthday.trim());
            }
        }
        // Add home address
        subelement = vCard.addElement("ADR");
        subelement.addElement("HOME");
        if (home_street != null && home_street.trim().length() > 0) {
            Element street = subelement.addElement("STREET");
            if (home_street.trim().startsWith("{")) {
                String xmlValue = home_street.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                street.addAttribute("attrs", xmlValue.trim());
                street.setText("{0}");
            }
            else {
                street.setText(home_street.trim());
            }
        }
        if (home_city != null && home_city.trim().length() > 0) {
            Element city = subelement.addElement("LOCALITY");
            if (home_city.trim().startsWith("{")) {
                String xmlValue = home_city.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                city.addAttribute("attrs", xmlValue.trim());
                city.setText("{0}");
            }
            else {
                city.setText(home_city.trim());
            }
        }
        if (home_state != null && home_state.trim().length() > 0) {
            Element city = subelement.addElement("REGION");
            if (home_state.trim().startsWith("{")) {
                String xmlValue = home_state.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                city.addAttribute("attrs", xmlValue.trim());
                city.setText("{0}");
            }
            else {
                city.setText(home_state.trim());
            }
        }
        if (home_zip != null && home_zip.trim().length() > 0) {
            Element city = subelement.addElement("PCODE");
            if (home_zip.trim().startsWith("{")) {
                String xmlValue = home_zip.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                city.addAttribute("attrs", xmlValue.trim());
                city.setText("{0}");
            }
            else {
                city.setText(home_zip.trim());
            }
        }
        if (home_country != null && home_country.trim().length() > 0) {
            Element city = subelement.addElement("CTRY");
            if (home_country.trim().startsWith("{")) {
                String xmlValue = home_country.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                city.addAttribute("attrs", xmlValue.trim());
                city.setText("{0}");
            }
            else {
                city.setText(home_country.trim());
            }
        }
        // Add business address
        subelement = vCard.addElement("ADR");
        subelement.addElement("WORK");
        if (business_street != null && business_street.trim().length() > 0) {
            Element street = subelement.addElement("STREET");
            if (business_street.trim().startsWith("{")) {
                String xmlValue = business_street.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                street.addAttribute("attrs", xmlValue.trim());
                street.setText("{0}");
            }
            else {
                street.setText(business_street.trim());
            }
        }
        if (business_city != null && business_city.trim().length() > 0) {
            Element city = subelement.addElement("LOCALITY");
            if (business_city.trim().startsWith("{")) {
                String xmlValue = business_city.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                city.addAttribute("attrs", xmlValue.trim());
                city.setText("{0}");
            }
            else {
                city.setText(business_city.trim());
            }
        }
        if (business_state != null && business_state.trim().length() > 0) {
            Element city = subelement.addElement("REGION");
            if (business_state.trim().startsWith("{")) {
                String xmlValue = business_state.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                city.addAttribute("attrs", xmlValue.trim());
                city.setText("{0}");
            }
            else {
                city.setText(business_state.trim());
            }
        }
        if (business_zip != null && business_zip.trim().length() > 0) {
            Element city = subelement.addElement("PCODE");
            if (business_zip.trim().startsWith("{")) {
                String xmlValue = business_zip.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                city.addAttribute("attrs", xmlValue.trim());
                city.setText("{0}");
            }
            else {
                city.setText(home_zip.trim());
            }
        }
        if (business_country != null && business_country.trim().length() > 0) {
            Element city = subelement.addElement("CTRY");
            if (business_country.trim().startsWith("{")) {
                String xmlValue = business_country.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                city.addAttribute("attrs", xmlValue.trim());
                city.setText("{0}");
            }
            else {
                city.setText(business_country.trim());
            }
        }
        // Add home phone
        if (home_phone != null && home_phone.trim().length() > 0) {
            subelement = vCard.addElement("TEL");
            subelement.addElement("HOME");
            subelement.addElement("VOICE");
            Element number = subelement.addElement("NUMBER");
            if (home_phone.trim().startsWith("{")) {
                String xmlValue = home_phone.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                number.addAttribute("attrs", xmlValue.trim());
                number.setText("{0}");
            }
            else {
                number.setText(home_phone.trim());
            }
        }
        // Add home mobile
        if (home_mobile != null && home_mobile.trim().length() > 0) {
            subelement = vCard.addElement("TEL");
            subelement.addElement("HOME");
            subelement.addElement("VOICE");
            subelement.addElement("CELL");
            Element number = subelement.addElement("NUMBER");
            if (home_mobile.trim().startsWith("{")) {
                String xmlValue = home_mobile.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                number.addAttribute("attrs", xmlValue.trim());
                number.setText("{0}");
            }
            else {
                number.setText(home_mobile.trim());
            }
        }
        // Add home fax
        if (home_fax != null && home_fax.trim().length() > 0) {
            subelement = vCard.addElement("TEL");
            subelement.addElement("HOME");
            subelement.addElement("FAX");
            Element number = subelement.addElement("NUMBER");
            if (home_fax.trim().startsWith("{")) {
                String xmlValue = home_fax.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                number.addAttribute("attrs", xmlValue.trim());
                number.setText("{0}");
            }
            else {
                number.setText(home_fax.trim());
            }
        }
        // Add home pager
        if (home_pager != null && home_pager.trim().length() > 0) {
            subelement = vCard.addElement("TEL");
            subelement.addElement("HOME");
            subelement.addElement("PAGER");
            Element number = subelement.addElement("NUMBER");
            if (home_pager.trim().startsWith("{")) {
                String xmlValue = home_pager.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                number.addAttribute("attrs", xmlValue.trim());
                number.setText("{0}");
            }
            else {
                number.setText(home_pager.trim());
            }
        }
        // Add business phone
        if (business_phone != null && business_phone.trim().length() > 0) {
            subelement = vCard.addElement("TEL");
            subelement.addElement("WORK");
            subelement.addElement("VOICE");
            Element number = subelement.addElement("NUMBER");
            if (business_phone.trim().startsWith("{")) {
                String xmlValue = business_phone.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                number.addAttribute("attrs", xmlValue.trim());
                number.setText("{0}");
            }
            else {
                number.setText(business_phone.trim());
            }
        }
        // Add business mobile
        if (business_mobile != null && business_mobile.trim().length() > 0) {
            subelement = vCard.addElement("TEL");
            subelement.addElement("WORK");
            subelement.addElement("VOICE");
            subelement.addElement("CELL");
            Element number = subelement.addElement("NUMBER");
            if (business_mobile.trim().startsWith("{")) {
                String xmlValue = business_mobile.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                number.addAttribute("attrs", xmlValue.trim());
                number.setText("{0}");
            }
            else {
                number.setText(business_mobile.trim());
            }
        }
        // Add business fax
        if (business_fax != null && business_fax.trim().length() > 0) {
            subelement = vCard.addElement("TEL");
            subelement.addElement("WORK");
            subelement.addElement("FAX");
            Element number = subelement.addElement("NUMBER");
            if (business_fax.trim().startsWith("{")) {
                String xmlValue = business_fax.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                number.addAttribute("attrs", xmlValue.trim());
                number.setText("{0}");
            }
            else {
                number.setText(business_fax.trim());
            }
        }
        // Add business pager
        if (business_pager != null && business_pager.trim().length() > 0) {
            subelement = vCard.addElement("TEL");
            subelement.addElement("WORK");
            subelement.addElement("PAGER");
            Element number = subelement.addElement("NUMBER");
            if (business_pager.trim().startsWith("{")) {
                String xmlValue = business_pager.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                number.addAttribute("attrs", xmlValue.trim());
                number.setText("{0}");
            }
            else {
                number.setText(business_pager.trim());
            }
        }
        // Add job title
        if (business_job_title != null && business_job_title.trim().length() > 0) {
            subelement = vCard.addElement("TITLE");
            if (business_job_title.trim().startsWith("{")) {
                String xmlValue = business_job_title.replaceAll("\\{", "");
                xmlValue = xmlValue.replaceAll("}", "");
                subelement.addAttribute("attrs", xmlValue.trim());
                subelement.setText("{0}");
            }
            else {
                subelement.setText(business_job_title.trim());
            }
        }
        // TODO Add job department
        // TODO Add web page

        String vcardXML = vCard.asXML();
        StringBuilder sb = new StringBuilder(vcardXML.length());
        sb.append("<![CDATA[").append(vcardXML).append("]]>");

        JiveGlobals.setXMLProperty("ldap.vcard-mapping", sb.toString());

    }
}
