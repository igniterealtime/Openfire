/**
 * <p>Provides the interfaces and classes necessary to create custom
 * user account data providers for Openfire.</p>
 * <p>User accounts are handled separately from authentication. The three
 * primary interfaces to implement are the UserIDProvider,
 * UserAccountProvider, and UserInfoProvider. An overview of how these
 * providers should be implemented and how they interact is described in
 * the User Account Provider Guide included in the Openfire distribution.</p>
 * <p>There are several Roster (a.k.a. Buddy List) related classes in the
 * user package. Developers are strongly discouraged from implementing
 * custom RosterProvider classes. Roster provider implementation is
 * complicated and should be left to the Jive JDBC implementation if at
 * all possible. There are no disadvantages in implementing user account
 * data with custom providers to integrate Openfire with a CRM or ERP
 * user system, while leaving roster storage in Jive's standard JDBC
 * database tables. (Note: Openfire comes with JDBC and LDAP user account
 * providers 'out of the box'. It is expected that LDAP will accomodate
 * many enterprise integration needs).</p>
 */
package org.jivesoftware.openfire.user;
