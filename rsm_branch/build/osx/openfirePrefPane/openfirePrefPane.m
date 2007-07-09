//
//  openfirePrefPane.m
//  Preference panel for Openfire
//
//  Created by Daniel Henninger on 7/7/06.
//  Copyright (c) 2006 Jive Software. All rights reserved.
//
//  Concept taken from MySQL preference panel, as well as some borrowed code.
//

#import <Security/Security.h>
#import <CoreFoundation/CoreFoundation.h>
#import "openfirePrefPane.h"

@implementation openfirePrefPane

- (void)mainViewDidLoad
{
	AuthorizationItem authItems[1]; 	// we only want to get authorization for one command

    authItems[0].name = kAuthorizationRightExecute;	// we want the right to execute
	char *cmd = [[[NSBundle bundleForClass:[self class]] pathForAuxiliaryExecutable:@"HelperTool"] fileSystemRepresentation];
    authItems[0].value = cmd;		// the path to the helper tool
    authItems[0].valueLength = strlen(cmd);	// length of the command
    authItems[0].flags = 0;				// no extra flags
    
	AuthorizationRights authRights;
    authRights.count = 1;		// we have one item
    authRights.items = authItems;	// here is the values for our item
	[authView setAuthorizationRights:&authRights];
	[authView setAutoupdate:YES];
	[authView setDelegate:self];
	[authView updateStatus:self];

    [statusProgress setStyle:NSProgressIndicatorSpinningStyle];
    [statusProgress setDisplayedWhenStopped:NO];

	[self updateStatus];
}

- (BOOL)isRunning
{
    FILE *ps;
    char buff[1024];
    
    if((ps=popen(pscmd, "r")) == NULL)
    {
        // There was an error opening the pipe. Alert the user.
        NSBeginAlertSheet(
            @"Error!",
            @"OK",
            nil,
            nil,
            [NSApp mainWindow],
            self,
            nil,
            nil,
            self,
            @"An error occured while detecting a running Openfire process.",
            nil);
        
        return NO;
    }
    else
    {
		BOOL running = NO;
        if(fgets(buff, 1024, ps)) {
			running = YES;
			printf(buff);
		}
        pclose(ps);
        return running;
    }
}

- (IBAction)openAdminInterface:(id)sender
{
	NSString *stringURL = @"http://localhost:9090/";
	[[NSWorkspace sharedWorkspace] openURL:[NSURL URLWithString:stringURL]];
}

- (IBAction)toggleAutoStart:(id)sender
{
	char *args[2];
	args[0] = "boot"; 
	args[1] = NULL;
	
	OSStatus ourStatus = AuthorizationExecuteWithPrivileges([[authView authorization] authorizationRef],
															[authView authorizationRights]->items[0].value,
															kAuthorizationFlagDefaults, args, NULL);
        
	if(ourStatus != errAuthorizationSuccess)
	{
		// alert user the startup has failed
		NSBeginAlertSheet(
						  @"Error!",
						  @"OK",
						  nil,
						  nil,
						  [NSApp mainWindow],
						  self,
						  nil,
						  nil,
						  self,
						  @"Could not start the Openfire server.",
						  nil);
		[statusTimer invalidate];
		[self checkStatus];
	}
	
	[self updateStatus];
}

- (IBAction)toggleServer:(id)sender
{
	[statusMessage setHidden:YES];
	[statusProgress startAnimation:self];
    [startButton setEnabled:NO];
	
    if(![self isRunning])
    {
        [self startServer];
		statusTimer = [NSTimer scheduledTimerWithTimeInterval:4 target:self 
			selector:@selector(checkStatus) userInfo:nil repeats:NO];
    }
    else
    {
        [self stopServer];
        statusTimer = [NSTimer scheduledTimerWithTimeInterval:4 target:self 
            selector:@selector(checkStatus) userInfo:nil repeats:NO];
    }
    [self updateStatus];
}

- (void)checkStatus
{
	[statusProgress stopAnimation:self];
	[statusMessage setHidden:NO];
    [startButton setEnabled:YES];
    [self updateStatus];
}


- (void)updateStatus
{
	if ([self isRunning] == NO)
	{
		[statusMessage setStringValue:@"Stopped"];
		[statusMessage setTextColor:[NSColor redColor]];
		[statusDescription setStringValue:@"The server may take a few seconds to start up."];
		[startButton setTitle:@"Start Openfire"];
		[viewAdminButton setEnabled:NO];
	}
	else 
	{
		[statusMessage setStringValue:@"Running"];
		[statusMessage setTextColor:[NSColor greenColor]];
		[statusDescription setStringValue:@"The server may take a few seconds to stop."];
		[startButton setTitle:@"Stop Openfire"];
		[viewAdminButton setEnabled:YES];
	}
	BOOL isStartingAtBoot = [[[NSMutableDictionary dictionaryWithContentsOfFile:plistPath] objectForKey:@"RunAtLoad"] boolValue];
	[autoStartCheckbox setState:(isStartingAtBoot ? NSOnState : NSOffState)];
}

- (void)startServer
{
	char *args[0];
	args[1] = NULL;
			
	OSStatus ourStatus = AuthorizationExecuteWithPrivileges([[authView authorization] authorizationRef],
															[authView authorizationRights]->items[0].value,
															kAuthorizationFlagDefaults, args, NULL);
	// wait for the server to start

	if(ourStatus != errAuthorizationSuccess)
	{
		// alert user the startup has failed
		NSBeginAlertSheet(
			@"Error!",
			@"OK",
			nil,
			nil,
			[NSApp mainWindow],
			self,
			nil,
			nil,
			self,
			@"Could not start the Openfire server.",
			nil);
		[statusTimer invalidate];
		[self checkStatus];
	}
}

- (void) stopServer
{
	char *args[1];
	args[0] = NULL;
		
	OSStatus ourStatus = AuthorizationExecuteWithPrivileges([[authView authorization] authorizationRef],
															[authView authorizationRights]->items[0].value,
															kAuthorizationFlagDefaults, args, NULL);
	
	if(ourStatus != errAuthorizationSuccess)
	{
		// alert user the startup has failed
		NSBeginAlertSheet(
			@"Error!",
			@"OK",
			nil,
			nil,
			[NSApp mainWindow],
			self,
			nil,
			nil,
			self,
			@"Could not stop the Openfire server.",
			nil);
		[statusTimer invalidate];
		[self checkStatus];
	}
}

@end
