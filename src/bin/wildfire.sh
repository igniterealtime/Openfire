#!/bin/sh

#
# $RCSfile$
# $Revision: 1194 $
# $Date: 2005-03-30 13:39:54 -0300 (Wed, 30 Mar 2005) $
#

# tries to determine arguments to launch wildfire

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

#if wildfire home is not set or is not a directory
if [ -z "$WILDFIRE_HOME" -o ! -d "$WILDFIRE_HOME" ]; then

	if [ -d /opt/wildfire ] ; then
		WILDFIRE_HOME="/opt/wildfire"
	fi

	if [ -d /usr/local/wildfire ] ; then
		WILDFIRE_HOME="/usr/local/wildfire"
	fi

	if [ -d ${HOME}/opt/wildfire ] ; then
		WILDFIRE_HOME="${HOME}/opt/wildfire"
	fi

	#resolve links - $0 may be a link in wildfire's home
	PRG="$0"
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
	WILDFIRE_HOME=`dirname "$PRG"`/..

	#make it fully qualified
	WILDFIRE_HOME=`cd "$WILDFIRE_HOME" && pwd`
fi
WILDFIRE_OPTS="${WILDFIRE_OPTS} -DwildfireHome=${WILDFIRE_HOME}"


# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
	[ -n "$WILDFIRE_HOME" ] &&
    		WILDFIRE_HOME=`cygpath --unix "$WILDFIRE_HOME"`
  	[ -n "$JAVA_HOME" ] &&
    		JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
fi

#set the WILDFIRE_LIB location
WILDFIRE_LIB="${WILDFIRE_HOME}/lib"
WILDFIRE_OPTS="${WILDFIRE_OPTS} -Dwildfire.lib.dir=${WILDFIRE_LIB}"


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
	LOCALCLASSPATH=$WILDFIRE_LIB/startup.jar
else
      	LOCALCLASSPATH=$WILDFIRE_LIB/startup.jar:$LOCALCLASSPATH
fi

# For Cygwin, switch paths to appropriate format before running java
if $cygwin; then
  	if [ "$OS" = "Windows_NT" ] && cygpath -m .>/dev/null 2>/dev/null ; then
    		format=mixed
  	else
    		format=windows
  	fi
  	WILDFIRE_HOME=`cygpath --$format "$WILDFIRE_HOME"`
  	WILDFIRE_LIB=`cygpath --$format "$WILDFIRE_LIB"`
  	JAVA_HOME=`cygpath --$format "$JAVA_HOME"`
  	LOCALCLASSPATH=`cygpath --path --$format "$LOCALCLASSPATH"`
  	if [ -n "$CLASSPATH" ] ; then
    		CLASSPATH=`cygpath --path --$format "$CLASSPATH"`
  	fi
  	CYGHOME=`cygpath --$format "$HOME"`
fi

# add a second backslash to variables terminated by a backslash under cygwin
if $cygwin; then
  case "$WILDFIRE_HOME" in
    *\\ )
    WILDFIRE_HOME="$WILDFIRE_HOME\\"
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

wildfire_exec_command="exec \"$JAVACMD\" -server $WILDFIRE_OPTS -classpath \"$LOCALCLASSPATH\" -jar \"$WILDFIRE_LIB\"/startup.jar"
eval $wildfire_exec_command
