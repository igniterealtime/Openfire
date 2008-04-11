//
//  openfirePrefPane.h
//  Preference panel for Openfire
//
//  Created by Daniel Henninger on 7/7/06.
//  Copyright (C) 2005-2008 Jive Software. All rights reserved.
//
//  Concept taken from MySQL preference panel, as well as some borrowed code.
//

#import <PreferencePanes/PreferencePanes.h>
#import <Security/Security.h>
#import <SecurityInterface/SFAuthorizationView.h>
#include <unistd.h>

// 'ps' command to use to check for running openfire daemon
char *pscmd = "/bin/ps auxww | fgrep -v 'fgrep' | fgrep openfire/lib/startup.jar";

// The path to the plist file
NSString *plistPath = @"/Library/LaunchDaemons/org.jivesoftware.openfire.plist";


@interface openfirePrefPane : NSPreferencePane 
{
	IBOutlet NSButton *startButton;
	IBOutlet NSButton *autoStartCheckbox;
	IBOutlet NSButton *viewAdminButton;
	IBOutlet NSTextField *statusMessage;
	IBOutlet NSTextField *statusDescription;
	IBOutlet NSProgressIndicator *statusProgress;
	IBOutlet SFAuthorizationView *authView;
	
	NSTimer *statusTimer;
}

- (IBAction)toggleServer:(id)sender;
- (IBAction)toggleAutoStart:(id)sender;
- (IBAction)openAdminInterface:(id)sender;
- (void)mainViewDidLoad;
- (void)updateStatus;
- (void)startServer;
- (void)stopServer;
- (void)checkStatus;
- (BOOL)isRunning;

@end
