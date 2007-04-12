#include <unistd.h>
#include <stdbool.h>
#include <CoreFoundation/CoreFoundation.h>

static void CFQRelease(CFTypeRef cf)
// A version of CFRelease that's tolerant of NULL.
{
    if (cf != NULL) {
        CFRelease(cf);
    }
}

static int GetPathToSelf(char **pathToSelfPtr)
// A drop-in replacement for GetPathToSelf() from MoreAuthSample's MoreSecurity.c,
// implemented using CoreFoundation. From CocoaDev.com
{
	int err = 0;
	
	assert( pathToSelfPtr != NULL);
	assert(*pathToSelfPtr == NULL);
	
	CFBundleRef mainBundle = CFBundleGetMainBundle();
	CFURLRef executableURL = NULL;
	CFStringRef pathStringRef = NULL;
	
	char *path = NULL;
	
	if (mainBundle != NULL) {
		executableURL =  CFBundleCopyExecutableURL(mainBundle);
		if (executableURL != NULL) {
			pathStringRef = CFURLCopyFileSystemPath(executableURL, kCFURLPOSIXPathStyle);
			if (pathStringRef != NULL) {
				CFIndex pathSize = CFStringGetLength(pathStringRef) + 1;
				path = (char *)calloc(pathSize,1);
				if (path != NULL) {
					Boolean gotCString = CFStringGetCString(pathStringRef, path, pathSize, kCFStringEncodingUTF8);
					if (!gotCString) {
						free(path);
						path = NULL;
					}
				}
			}
		}
	}
	
	*pathToSelfPtr = path;
	
	// Do the CF memory management.
	CFQRelease(executableURL);
	CFQRelease(pathStringRef);
	
	assert(*pathToSelfPtr != NULL);
	if (*pathToSelfPtr == NULL)
	{
		err = -1;
	}
	return err;
}

static bool openfireIsRunning()
{
    FILE *ps;
    char buff[1024];
    
    if((ps=popen("/bin/ps auxww | fgrep -v 'fgrep' | fgrep openfire/lib/startup.jar", "r")) == NULL)
    {
        return false;
    }
    else
    {
		bool running = false;
        if(fgets(buff, 1024, ps)) {
			running = true;
		}
        pclose(ps);
        return running;
    }
}

static void toggleStartOpenfireAtBoot()
{
	CFURLRef propFile = CFURLCreateWithFileSystemPath(kCFAllocatorDefault,
													  CFSTR("/Library/LaunchDaemons/org.jivesoftware.openfire.plist"),
													  kCFURLPOSIXPathStyle,
													  false);
	CFDataRef xmlData;
    CFURLCreateDataAndPropertiesFromResource (kCFAllocatorDefault,
											  propFile,
											  &xmlData,
											  NULL,
											  NULL,
											  NULL);
	CFPropertyListRef props = CFPropertyListCreateFromXMLData(kCFAllocatorDefault,
															  xmlData,
															  kCFPropertyListMutableContainersAndLeaves,
															  NULL);
	if(CFGetTypeID(props) == CFDictionaryGetTypeID()) {
		CFMutableDictionaryRef propsDict = (CFMutableDictionaryRef)props;
		CFBooleanRef currentValue = CFDictionaryGetValue(propsDict,CFSTR("RunAtLoad"));
		CFDictionaryReplaceValue(propsDict, CFSTR("RunAtLoad"), ((currentValue == kCFBooleanTrue) ? kCFBooleanFalse : kCFBooleanTrue));
		
		CFQRelease(xmlData);
		xmlData = CFPropertyListCreateXMLData(kCFAllocatorDefault,(CFPropertyListRef)propsDict);
		CFURLWriteDataAndPropertiesToResource(propFile,xmlData,NULL,NULL);
	}
	CFQRelease(props);
	CFQRelease(xmlData);
	CFQRelease(propFile);
}

static void toggleOpenfire()
{
	char *args[4];
	args[0] = "launchctl";
	if(openfireIsRunning())
		args[1] = "unload";
	else
		args[1] = "load";
	args[2] = "/Library/LaunchDaemons/org.jivesoftware.openfire.plist";
	args[3] = NULL;
	
	execv("/bin/launchctl", args);
}

int main(int inArgsCount, char * const inArgs[])
{
	char* selfPath = NULL;
	int err = GetPathToSelf(&selfPath);
	const char* correctPath = "/Library/PreferencePanes/Openfire.prefPane/Contents/MacOS/HelperTool";
	if(err == 0 && strncmp(correctPath, selfPath, strlen(correctPath)) == 0) {
		setuid(0);

		if(inArgs[1] && strlen(inArgs[1]) == strlen("boot") && strncmp("boot", inArgs[1], strlen("boot")) == 0) {
			toggleStartOpenfireAtBoot();
		} else {
			toggleOpenfire();
		}
	}
	
	return 1;
}