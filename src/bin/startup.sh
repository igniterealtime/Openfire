#!/bin/sh

#
# $RCSfile$
# $Revision$
# $Date$
#

# tries to determine arguments to launch messenger

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
case "`uname`" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true
           if [ -z "$JAVA_HOME" ] ; then
             JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Home
           fi
           ;;
esac

#if messenger home is not set or is not a directory
if [ -z "$MESSENGER_HOME" -o ! -d "$MESSENGER_HOME" ]; then

	if [ -d /opt/messenger ] ; then
		MESSENGER_HOME="/opt/messenger"
	fi

	if [ -d /usr/local/messenger ] ; then
		MESSENGER_HOME="/usr/local/messenger"
	fi

	if [ -d ${HOME}/opt/messenger ] ; then
		MESSENGER_HOME="${HOME}/opt/messenger"
	fi

	#resolve links - $0 may be a link in messenger's home
	PRG="0"
	progname=`basename "$0$"`

	# need this for relative symlinks

	# need this for relative symlinks
  	while [ -h "$PRG" ] ; do
    		ls=`ls -ld "$PRG"`
    		link=`expr "$ls" : '.*-> \(.*\)$'`
    		if expr "$link" : '/.*' > /dev/null; then
    			PRG="$link"
    		else
    			PRG=`dirname "$PRG"`"/$link"
    		fi
  	done

	#assumes we are in the bin directory
	MESSENGER_HOME=`dirname "$PRG"`/..

	#make it fully qualified
	MESSENGER_HOME=`cd "$MESSENGER_HOME" && pwd`
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
	[ -n "$MESSENGER_HOME" ] &&
    		MESSENGER_HOME=`cygpath --unix "$MESSENGER_HOME"`
  	[ -n "$JAVA_HOME" ] &&
    		JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
fi

#set the MESSENGER_LIB location
MESSENGER_LIB="${MESSENGER_HOME}/lib"


if [ -z "$JAVACMD" ] ; then
  	if [ -n "$JAVA_HOME"  ] ; then
    		if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
      			# IBM's JDK on AIX uses strange locations for the executables
      			JAVACMD="$JAVA_HOME/jre/sh/java"
    		else
      			JAVACMD="$JAVA_HOME/bin/java"
    		fi
  	else
    		JAVACMD=`which java 2> /dev/null `
    		if [ -z "$JAVACMD" ] ; then
        		JAVACMD=java
    		fi
  	fi
fi

if [ ! -x "$JAVACMD" ] ; then
  	echo "Error: JAVA_HOME is not defined correctly."
  	echo "  We cannot execute $JAVACMD"
  	exit 1
fi

if [ -z "$LOCALCLASSPATH" ] ; then
	LOCALCLASSPATH=$MESSENGER_LIB/startup.jar
else
      	LOCALCLASSPATH=$MESSENGER_LIB/startup.jar:$LOCALCLASSPATH
fi

# For Cygwin, switch paths to appropriate format before running java
if $cygwin; then
  	if [ "$OS" = "Windows_NT" ] && cygpath -m .>/dev/null 2>/dev/null ; then
    		format=mixed
  	else
    		format=windows
  	fi
  	MESSENGER_HOME=`cygpath --$format "$MESSENGER_HOME"`
  	MESSENGER_LIB=`cygpath --$format "$MESSENGER_LIB"`
  	JAVA_HOME=`cygpath --$format "$JAVA_HOME"`
  	LOCALCLASSPATH=`cygpath --path --$format "$LOCALCLASSPATH"`
  	if [ -n "$CLASSPATH" ] ; then
    		CLASSPATH=`cygpath --path --$format "$CLASSPATH"`
  	fi
  	CYGHOME=`cygpath --$format "$HOME"`
fi

# add a second backslash to variables terminated by a backslash under cygwin
if $cygwin; then
  case "$MESSENGER_HOME" in
    *\\ )
    MESSENGER_HOME="$MESSENGER_HOME\\"
    ;;
  esac
  case "$CYGHOME" in
    *\\ )
    CYGHOME="$CYGHOME\\"
    ;;
  esac
  case "$LOCALCLASSPATH" in
    *\\ )
    LOCALCLASSPATH="$LOCALCLASSPATH\\"
    ;;
  esac
  case "$CLASSPATH" in
    *\\ )
    CLASSPATH="$CLASSPATH\\"
    ;;
  esac
fi

messenger_exec_command="exec \"$JAVACMD\" $MESSENGER_OPTS -classpath \"$LOCALCLASSPATH\" -jar \"$MESSENGER_LIB\"/startup.jar"
eval $messenger_exec_command
