/*

Jappix - An open social platform
These are the constants JS scripts for Jappix

-------------------------------------------------

License: AGPL
Authors: Stefan Strigler, Valérian Saliou
Last revision: 27/05/11

*/

// XMPP XMLNS attributes
var NS_PROTOCOL =     'http://jabber.org/protocol/';
var NS_FEATURES =     'http://jabber.org/features/';
var NS_CLIENT =       'jabber:client';
var NS_IQ =           'jabber:iq:';
var NS_X =            'jabber:x:';
var NS_IETF =         'urn:ietf:params:xml:ns:xmpp-';
var NS_XMPP =         'urn:xmpp:';

var NS_STORAGE =      'storage:';
var NS_BOOKMARKS =    NS_STORAGE + 'bookmarks';
var NS_ROSTERNOTES =  NS_STORAGE + 'rosternotes';

var NS_JAPPIX =       'jappix:';
var NS_INBOX =        NS_JAPPIX + 'inbox';
var NS_OPTIONS =      NS_JAPPIX + 'options';

var NS_DISCO_ITEMS =  NS_PROTOCOL + 'disco#items';
var NS_DISCO_INFO =   NS_PROTOCOL + 'disco#info';
var NS_VCARD =        'vcard-temp';
var NS_VCARD_P =      NS_VCARD + ':x:update';
var NS_AUTH =         NS_IQ + 'auth';
var NS_AUTH_ERROR =   NS_IQ + 'auth:error';
var NS_REGISTER =     NS_IQ + 'register';
var NS_SEARCH =       NS_IQ + 'search';
var NS_ROSTER =       NS_IQ + 'roster';
var NS_PRIVACY =      NS_IQ + 'privacy';
var NS_PRIVATE =      NS_IQ + 'private';
var NS_VERSION =      NS_IQ + 'version';
var NS_TIME =         NS_IQ + 'time';
var NS_LAST =         NS_IQ + 'last';
var NS_IQDATA =       NS_IQ + 'data';
var NS_XDATA =        NS_X + 'data';
var NS_DELAY =        NS_X + 'delay';
var NS_EXPIRE =       NS_X + 'expire';
var NS_EVENT =        NS_X + 'event';
var NS_XCONFERENCE =  NS_X + 'conference';
var NS_STATS =        NS_PROTOCOL + 'stats';
var NS_MUC =          NS_PROTOCOL + 'muc';
var NS_MUC_USER =     NS_MUC + '#user';
var NS_MUC_ADMIN =    NS_MUC + '#admin';
var NS_MUC_OWNER =    NS_MUC + '#owner';
var NS_MUC_CONFIG =   NS_MUC + '#roomconfig';
var NS_PUBSUB =       NS_PROTOCOL + 'pubsub';
var NS_PUBSUB_EVENT = NS_PUBSUB + '#event';
var NS_PUBSUB_OWNER = NS_PUBSUB + '#owner';
var NS_PUBSUB_NMI =   NS_PUBSUB + '#node-meta-info';
var NS_PUBSUB_NC =    NS_PUBSUB + '#node_config';
var NS_PUBSUB_RI =    NS_PUBSUB + '#retrieve-items';
var NS_COMMANDS =     NS_PROTOCOL + 'commands';
var NS_BOSH =         NS_PROTOCOL + 'httpbind';
var NS_STREAM =       'http://etherx.jabber.org/streams';
var NS_URN_TIME =     NS_XMPP + 'time';
var NS_URN_PING =     NS_XMPP + 'ping';
var NS_URN_ADATA =    NS_XMPP + 'avatar:data';
var NS_URN_AMETA =    NS_XMPP + 'avatar:metadata';
var NS_URN_MBLOG =    NS_XMPP + 'microblog:0';
var NS_URN_INBOX =    NS_XMPP + 'inbox';
var NS_URN_ARCHIVE =  NS_XMPP + 'archive';
var NS_URN_AR_PREF =  NS_URN_ARCHIVE + ':pref';
var NS_URN_AR_AUTO =  NS_URN_ARCHIVE + ':auto';
var NS_URN_AR_MANUAL =  NS_URN_ARCHIVE + ':manual';
var NS_URN_AR_MANAGE =  NS_URN_ARCHIVE + ':manage';
var NS_URN_DELAY =    NS_XMPP + 'delay';
var NS_URN_RECEIPTS = NS_XMPP + 'receipts';
var NS_RSM =          NS_PROTOCOL + 'rsm';
var NS_IPV6 =         'ipv6';
var NS_XHTML =        'http://www.w3.org/1999/xhtml';
var NS_XHTML_IM =     NS_PROTOCOL + 'xhtml-im';
var NS_CHATSTATES =   NS_PROTOCOL + 'chatstates';
var NS_HTTP_AUTH =    NS_PROTOCOL + 'http-auth';
var NS_ROSTERX =      NS_PROTOCOL + 'rosterx';
var NS_MOOD =         NS_PROTOCOL + 'mood';
var NS_ACTIVITY =     NS_PROTOCOL + 'activity';
var NS_TUNE =         NS_PROTOCOL + 'tune';
var NS_GEOLOC =       NS_PROTOCOL + 'geoloc';
var NS_NICK =         NS_PROTOCOL + 'nick';
var NS_NOTIFY =       '+notify';
var NS_CAPS =         NS_PROTOCOL + 'caps';
var NS_ATOM =         'http://www.w3.org/2005/Atom';

var NS_STANZAS =      NS_IETF + 'stanzas';
var NS_STREAMS =      NS_IETF + 'streams';

var NS_TLS =          NS_IETF + 'tls';
var NS_SASL =         NS_IETF + 'sasl';
var NS_SESSION =      NS_IETF + 'session';
var NS_BIND =         NS_IETF + 'bind';

var NS_FEATURE_IQAUTH = NS_FEATURES + 'iq-auth';
var NS_FEATURE_IQREGISTER = NS_FEATURES + 'iq-register';
var NS_FEATURE_COMPRESS = NS_FEATURES + 'compress';

var NS_COMPRESS =     NS_PROTOCOL + 'compress';

// Available locales
var LOCALES_AVAILABLE_ID = new Array();
var LOCALES_AVAILABLE_NAMES = new Array();

// XML lang
var XML_LANG = null;

// Jappix parameters
var JAPPIX_STATIC = null;
var JAPPIX_VERSION = null;
var JAPPIX_MAX_FILE_SIZE = null;
var JAPPIX_MAX_UPLOAD = null;

// Jappix main configuration
var SERVICE_NAME = null;
var SERVICE_DESC = null;
var JAPPIX_RESOURCE = null;
var LOCK_HOST = null;
var ANONYMOUS = null;
var REGISTRATION = null;
var BOSH_PROXY = null;
var MANAGER_LINK = null;
var ENCRYPTION = null;
var HTTPS_STORAGE = null;
var HTTPS_FORCE = null;
var COMPRESSION = null;
var MULTI_FILES = null;
var DEVELOPER = null;

// Jappix hosts configuration
var HOST_MAIN = null;
var HOST_MUC = null;
var HOST_PUBSUB = null;
var HOST_VJUD = null;
var HOST_ANONYMOUS = null;
var HOST_BOSH = null;
var HOST_BOSH_MAIN = null;
var HOST_BOSH_MINI = null;
var HOST_STATIC = null;
var HOST_UPLOAD = null;

// Anonymous mode
var ANONYMOUS_ROOM = null;
var ANONYMOUS_NICK = null;

// Node parameters
var JAPPIX_LOCATION = getJappixLocation();

// XMPP error stanzas
function STANZA_ERROR(code, type, cond) {
	if (window == this)
		return new STANZA_ERROR(code, type, cond);
	
	this.code = code;
	this.type = type;
	this.cond = cond;
}

var ERR_BAD_REQUEST =
	STANZA_ERROR('400', 'modify', 'bad-request');
var ERR_CONFLICT =
	STANZA_ERROR('409', 'cancel', 'conflict');
var ERR_FEATURE_NOT_IMPLEMENTED =
	STANZA_ERROR('501', 'cancel', 'feature-not-implemented');
var ERR_FORBIDDEN =
	STANZA_ERROR('403', 'auth',   'forbidden');
var ERR_GONE =
	STANZA_ERROR('302', 'modify', 'gone');
var ERR_INTERNAL_SERVER_ERROR =
	STANZA_ERROR('500', 'wait',   'internal-server-error');
var ERR_ITEM_NOT_FOUND =
	STANZA_ERROR('404', 'cancel', 'item-not-found');
var ERR_JID_MALFORMED =
	STANZA_ERROR('400', 'modify', 'jid-malformed');
var ERR_NOT_ACCEPTABLE =
	STANZA_ERROR('406', 'modify', 'not-acceptable');
var ERR_NOT_ALLOWED =
	STANZA_ERROR('405', 'cancel', 'not-allowed');
var ERR_NOT_AUTHORIZED =
	STANZA_ERROR('401', 'auth',   'not-authorized');
var ERR_PAYMENT_REQUIRED =
	STANZA_ERROR('402', 'auth',   'payment-required');
var ERR_RECIPIENT_UNAVAILABLE =
	STANZA_ERROR('404', 'wait',   'recipient-unavailable');
var ERR_REDIRECT =
	STANZA_ERROR('302', 'modify', 'redirect');
var ERR_REGISTRATION_REQUIRED =
	STANZA_ERROR('407', 'auth',   'registration-required');
var ERR_REMOTE_SERVER_NOT_FOUND =
	STANZA_ERROR('404', 'cancel', 'remote-server-not-found');
var ERR_REMOTE_SERVER_TIMEOUT =
	STANZA_ERROR('504', 'wait',   'remote-server-timeout');
var ERR_RESOURCE_CONSTRAINT =
	STANZA_ERROR('500', 'wait',   'resource-constraint');
var ERR_SERVICE_UNAVAILABLE =
	STANZA_ERROR('503', 'cancel', 'service-unavailable');
var ERR_SUBSCRIPTION_REQUIRED =
	STANZA_ERROR('407', 'auth',   'subscription-required');
var ERR_UNEXPECTED_REQUEST =
	STANZA_ERROR('400', 'wait',   'unexpected-request');
