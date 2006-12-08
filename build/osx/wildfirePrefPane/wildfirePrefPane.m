//
//  wildfirePrefPane.m
//  Preference panel for Wildfire
//
//  Created by Daniel Henninger on 7/7/06.
//  Copyright (c) 2006 Jive Software. All rights reserved.
//
//  Concept taken from MySQL preference panel, as well as some borrowed code.
//

#import <CoreFoundation/CoreFoundation.h>
#import "wildfirePrefPane.h"

@implementation wildfirePrefPane

- (void)mainViewDidLoad
{
	authRights.count = 0;
	authRights.items = NULL;
	authFlags = kAuthorizationFlagDefaults;
	ourStatus = AuthorizationCreate(&authRights, kAuthorizationEmptyEnvironment, authFlags, &authorizationRef);
	
    [statusProgress setStyle:NSProgressIndicatorSpinningStyle];
    [statusProgress setDisplayedWhenStopped:NO];
	
	BOOL isStartingAtBoot = [[[NSMutableDictionary dictionaryWithContentsOfFile:plistPath] objectForKey:@"RunAtLoad"] boolValue];
	[autoStartCheckbox setState:(isStartingAtBoot ? NSOnState : NSOffState)];

	[self updateStatus];
}

- (void)dealloc
{
	AuthorizationFree(authorizationRef, kAuthorizationFlagDestroyRights);
	authorizationRef = NULL;
	
	[super dealloc];
}

- (void)updateStatus
{
	NSLog(@"updateStatus called");
	if ([self isRunning] == NO)
	{
		NSLog(@"isRunning == NO");
		[statusMessage setStringValue:@"Stopped"];
		[statusMessage setTextColor:[NSColor redColor]];
		[statusDescription setStringValue:@"The Wildfire server is currently stopped.\nTo start it, use the \"Start Wildfire\" button.\nPlease be aware that it may take a few seconds for the server to start up."];
		[startButton setTitle:@"Start Wildfire"];
		[viewAdminButton setHidden:YES];
		[viewAdminButtonSSL setHidden:YES];
	}
	else 
	{
		NSLog(@"isRunning == YES");
		[statusMessage setStringValue:@"Running"];
		[statusMessage setTextColor:[NSColor greenColor]];
		[statusDescription setStringValue:@"The Wildfire server is currently running.\nTo stop it, use the \"Stop Wildfire\" button.\nYou may access the admin interface by using one of the buttons below."];
		[startButton setTitle:@"Stop Wildfire"];
		[viewAdminButton setHidden:NO];
		[viewAdminButtonSSL setHidden:NO];
	}
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
            @"An error occured while detecting a running Wildfire process.",
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

- (IBAction)openAdminInterfaceSSL:(id)sender
{
	NSString *stringURL = @"https://localhost:9091/";
	[[NSWorkspace sharedWorkspace] openURL:[NSURL URLWithString:stringURL]];
}

- (IBAction)toggleAutoStart:(id)sender
{
	NSMutableDictionary *launchSettings = [NSMutableDictionary dictionaryWithContentsOfFile:plistPath];
	
	BOOL isStartingAtBoot = [[launchSettings objectForKey:@"RunAtLoad"] boolValue];
	
	AuthorizationItem authItems[1]; 	// we only want to get authorization for one command
    BOOL authorized = NO;		// are we authorized?
    char *args[6];
	
	char *command = [[[NSBundle bundleForClass:[self class]] pathForResource:@"autostartsetter" ofType:@"sh"] fileSystemRepresentation];
	
    authItems[0].name = kAuthorizationRightExecute;	// we want the right to execute
    authItems[0].value = command;		// the path to the startup script
    authItems[0].valueLength = strlen(command);	// length of the command
    authItems[0].flags = 0;				// no extra flags
    
    authRights.count = 1;		// we have one item
    authRights.items = authItems;	// here is the values for our item
    
    // by setting the kAuthorizationFlagExtendRights flag, we are telling
    // the security framework to bring up the authorization panel and ask for
    // our password in order to get root privelages to execute the startup script
    
    authFlags = kAuthorizationFlagInteractionAllowed | kAuthorizationFlagExtendRights;
    
    // lets find out if we are authorized
    
    ourStatus = AuthorizationCopyRights(authorizationRef,&authRights,
										kAuthorizationEmptyEnvironment, authFlags, NULL);
	
    authorized = (errAuthorizationSuccess==ourStatus);
    
    if (authorized)
    {
        // we are authorized, so let's tell the security framework to execute
        // our command as root
		args[0] = [(isStartingAtBoot ? @"true/false" : @"false/true") cStringUsingEncoding:NSASCIIStringEncoding]; 
        args[1] = NULL;
		
        ourStatus = AuthorizationExecuteWithPrivileges(authorizationRef,
													   command,
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
							  @"Could not start the Wildfire server.",
							  nil);
			[statusTimer invalidate];
            [self checkStatus];
        }
        else
        {
            // wait for the daemon to start so that we can update the status
            //sleep(2);
        }
    }
    else
    {	
        // there was an error getting authentication, either the user entered the
        // wrong password or isn't in the admin group
        NSBeginAlertSheet(@"Authentication Error!", @"OK", nil, nil, [NSApp mainWindow],
						  self, nil, nil, self, @"An error occured while receiving authentication to start Wildfire.",
						  nil);
		[statusTimer invalidate];
        [self checkStatus];
    }
	
	[self updateStatus];
}

- (IBAction)toggleServer:(id)sender
{
	NSLog(@"Toggling WildFire from prefpane");
	[statusProgress startAnimation:self];
    [startButton setEnabled:NO];

    if(![self isRunning])
    {
        [statusMessage setStringValue:@"Starting"];
        [self startServer];
		statusTimer = [NSTimer scheduledTimerWithTimeInterval:6 target:self 
			selector:@selector(checkStatus) userInfo:nil repeats:NO];
    }
    else
    {
        [statusMessage setStringValue:@"Stopping"];
        [self stopServer];
        statusTimer = [NSTimer scheduledTimerWithTimeInterval:6 target:self 
            selector:@selector(checkStatus) userInfo:nil repeats:NO];
    }
    [self updateStatus];
}

- (void)checkStatus
{
	[statusProgress stopAnimation:self];
    [startButton setEnabled:YES];
    [self updateStatus];
}

- (void)startServer
{
    // setup our authorization environment.
    
    AuthorizationItem authItems[1]; 	// we only want to get authorization for one command
    BOOL authorized = NO;		// are we authorized?
	char *args[4];
	
	//lame workaround for a bug
	char *command = "/usr/bin/sudo";
     
    authItems[0].name = kAuthorizationRightExecute;	// we want the right to execute
    authItems[0].value = command;		// the path to the startup script
    authItems[0].valueLength = strlen(command);	// length of the command
    authItems[0].flags = 0;				// no extra flags
    
    authRights.count = 1;		// we have one item
    authRights.items = authItems;	// here is the values for our item
    
    // by setting the kAuthorizationFlagExtendRights flag, we are telling
    // the security framework to bring up the authorization panel and ask for
    // our password in order to get root privelages to execute the startup script
    
    authFlags = kAuthorizationFlagInteractionAllowed | kAuthorizationFlagExtendRights;
    
    // lets find out if we are authorized
    
    ourStatus = AuthorizationCopyRights(authorizationRef,&authRights,
                kAuthorizationEmptyEnvironment, authFlags, NULL);
                                  
    authorized = (errAuthorizationSuccess==ourStatus);
    
    if (authorized)
    {
        // we are authorized, so let's tell the security framework to execute
        // our command as root
		args[0] = "launchctl";
        args[1] = "load";
		args[2] = "/Library/LaunchDaemons/org.jivesoftware.wildfire.plist";
        args[3] = NULL;
        		
        ourStatus = AuthorizationExecuteWithPrivileges(authorizationRef,
                                             command,
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
                @"Could not start the Wildfire server.",
                nil);
			[statusTimer invalidate];
            [self checkStatus];
        }
        else
        {
            // wait for the daemon to start so that we can update the status
            //sleep(2);
        }
    }
    else
    {	
        // there was an error getting authentication, either the user entered the
        // wrong password or isn't in the admin group
        NSBeginAlertSheet(@"Authentication Error!", @"OK", nil, nil, [NSApp mainWindow],
            self, nil, nil, self, @"An error occured while receiving authentication to start Wildfire.",
            nil);
		[statusTimer invalidate];
        [self checkStatus];
    }
}

- (void) stopServer
{
    // setup our authorization environment.
    
    AuthorizationItem authItems[1]; 	// we only want to get authorization for one command
    BOOL authorized = NO;		// are we authorized?
    char *args[4];
	
	//lame workaround for a bug
	char *command = "/usr/bin/sudo";
     
    authItems[0].name = kAuthorizationRightExecute;	// we want the right to execute
    authItems[0].value = command;		// the path to the startup script
    authItems[0].valueLength = strlen(command);	// length of the command
    authItems[0].flags = 0;				// no extra flags
    
    authRights.count = 1;		// we have one item
    authRights.items = authItems;	// here is the values for our item
    
    // by setting the kAuthorizationFlagExtendRights flag, we are telling
    // the security framework to bring up the authorization panel and ask for
    // our password in order to get root privelages to execute the startup script
    
    authFlags = kAuthorizationFlagInteractionAllowed | kAuthorizationFlagExtendRights;
    
    // lets find out if we are authorized
    
    ourStatus = AuthorizationCopyRights(authorizationRef,&authRights,
                kAuthorizationEmptyEnvironment, authFlags, NULL);
                                  
    authorized = (errAuthorizationSuccess==ourStatus);
    
    if (authorized)
    {
        // we are authorized, so let's tell the security framework to execute
        // our command as root
		args[0] = "launchctl";
        args[1] = "unload";
		args[2] = "/Library/LaunchDaemons/org.jivesoftware.wildfire.plist";
		args[3] = NULL;
        
        ourStatus = AuthorizationExecuteWithPrivileges(authorizationRef,
                                             command,
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
                @"Could not stop the Wildfire server.",
                nil);
			[statusTimer invalidate];
            [self checkStatus];
        }
        else
        {
            // wait for the daemon to start so that we can update the status
            //sleep(2);
        }
    }
    else
    {	
        // there was an error getting authentication, either the user entered the
        // wrong password or isn't in the admin group
        NSBeginAlertSheet(@"Authentication Error!", @"OK", nil, nil, [NSApp mainWindow],
            self, nil, nil, self, @"An error occured while receiving authentication to stop Wildfire.",
            nil);
		[statusTimer invalidate];
        [self checkStatus];
    }
}

@end
