//
//  wildfirePrefPane.h
//  Preference panel for Wildfire
//
//  Created by Daniel Henninger on 7/7/06.
//  Copyright (c) 2006 Jive Software. All rights reserved.
//
//  Concept taken from MySQL preference panel, as well as some borrowed code.
//

#import <PreferencePanes/PreferencePanes.h>
#import <Security/Authorization.h>
#import <Security/AuthorizationTags.h>
#include <unistd.h>

// 'ps' command to use to check for running wildfire daemon
char *pscmd = "/bin/ps auxww | fgrep -v 'fgrep' | fgrep wildfire/lib/startup.jar";

// The path to the plist file
NSString *plistPath = @"/Library/LaunchDaemons/org.jivesoftware.wildfire.plist";


@interface wildfirePrefPane : NSPreferencePane 
{
	IBOutlet NSButton *startButton;
	IBOutlet NSButton *autoStartCheckbox;
	IBOutlet NSButton *viewAdminButton;
	IBOutlet NSButton *viewAdminButtonSSL;
	IBOutlet NSTextField *statusMessage;
	IBOutlet NSTextField *statusDescription;
	IBOutlet NSProgressIndicator *statusProgress;
	
	AuthorizationRef authorizationRef;
	AuthorizationRights authRights;
	AuthorizationRights *authorizedRights;
	AuthorizationFlags authFlags;
	OSStatus ourStatus;
	
	NSTimer *statusTimer;
}

- (IBAction)toggleServer:(id)sender;
- (IBAction)toggleAutoStart:(id)sender;
- (IBAction)openAdminInterface:(id)sender;
- (IBAction)openAdminInterfaceSSL:(id)sender;
- (void)mainViewDidLoad;
- (void)updateStatus;
- (void)startServer;
- (void)stopServer;
- (void)checkStatus;
- (BOOL)isRunning;

@end
