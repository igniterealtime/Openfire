/**
 * Implementation of XEP-0352 "Client State Indication"
 *
 * It is common for IM clients to be logged in and 'online' even while the user is not interacting with the application. This protocol allows the client to indicate to the server when the user is not actively using the client, allowing the server to optimise traffic to the client accordingly. This can save bandwidth and resources on both the client and server.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0352.html">XEP-0352: Client State Indication</a>
 */
package org.jivesoftware.openfire.csi;
