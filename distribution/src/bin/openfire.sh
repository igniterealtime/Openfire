#!/bin/sh
# tries to determine arguments to launch openfire

# shellcheck disable=SC2166

# OS specific support
cygwin=false
darwin=false
linux=false
case "$(uname)" in
  CYGWIN*)
    cygwin=true
    ;;
  Darwin*)
    darwin=true
    ;;
  Linux*)
    linux=true
    ;;
esac

if [ -z "$JAVA_HOME" ] ; then
  echo "JAVA_HOME is empty, trying to find it"
  if $darwin ; then
    JAVA_HOME=/usr/libexec/java_home
  fi
  if $linux; then
    JAVA_HOME=$(LC_ALL=C update-alternatives --display java \
       | grep best \
       | grep -oe "\/.*\/bin\/java" \
       | sed 's/\/bin\/java//g')
    if [ -z "$JAVA_HOME" ] ; then
      echo "Unable to get preferred JAVA_HOME from java alternative"
      # shellcheck disable=SC2039
      shopt -s nullglob
      jdks=$(ls -r1d /usr/java/j* /usr/lib/jvm/* 2>/dev/null)
      for jdk in $jdks; do
        if [ -f "$jdk/bin/java" ]; then
          JAVA_HOME="$jdk"
          break
        fi
      done
    fi
  fi
  echo "JAVA_HOME is set to $JAVA_HOME"
fi

#if openfire home is not set or is not a directory
if [ -z "$OPENFIRE_HOME" -o ! -d "$OPENFIRE_HOME" ]; then
  echo "OPENFIRE_HOME is empty, trying to find it"
  if [ -d /opt/openfire ] ; then
    OPENFIRE_HOME="/opt/openfire"
  fi

  if [ -d /usr/local/openfire ] ; then
    OPENFIRE_HOME="/usr/local/openfire"
  fi

  if [ -d "${HOME}/opt/openfire" ] ; then
    OPENFIRE_HOME="${HOME}/opt/openfire"
  fi

  #resolve links - $0 may be a link in openfire's home
  PRG="$0"

  # need this for relative symlinks
  while [ -h "$PRG" ] ; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
      PRG="$link"
    else
      PRG=$(dirname "$PRG")"/$link"
    fi
  done

  #assumes we are in the bin directory
  OPENFIRE_HOME=$(dirname "$PRG")/..

  #make it fully qualified
  OPENFIRE_HOME=$(cd "$OPENFIRE_HOME" && pwd)
  echo "OPENFIRE_HOME is set to $OPENFIRE_HOME"
fi
OPENFIRE_OPTS="${OPENFIRE_OPTS} -DopenfireHome=\"${OPENFIRE_HOME}\""


# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
  [ -n "$OPENFIRE_HOME" ] &&
    OPENFIRE_HOME=$(cygpath --unix "$OPENFIRE_HOME")
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME=$(cygpath --unix "$JAVA_HOME")
fi

#set the OPENFIRE_LIB location
OPENFIRE_LIB="${OPENFIRE_HOME}/lib"
OPENFIRE_OPTS="${OPENFIRE_OPTS} -Dopenfire.lib.dir=\"${OPENFIRE_LIB}\""


if [ -z "$JAVACMD" ] ; then
  if [ -n "$JAVA_HOME"  ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
    else
      JAVACMD="$JAVA_HOME/bin/java"
    fi
  else
    JAVACMD=$(which java 2> /dev/null )
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

# Note: you can combine options, eg: -devboot -debug
for arguments in "$@"
do
  echo "Option: $arguments"
  case $arguments in
    -debug)
      echo "Starting debug mode"
      JAVACMD="$JAVACMD -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
      ;;
    -remotedebug)
      echo "Starting remote debug mode"
      JAVACMD="$JAVACMD -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=\*:5005"
      ;;
    -demoboot)
      echo "Starting demoboot"
      cp "$OPENFIRE_HOME/conf/openfire-demoboot.xml" "$OPENFIRE_HOME/conf/openfire.xml"
      ;;
    -devboot)
      HOSTNAME=$(hostname)
      sed "s/example.org/$HOSTNAME/g" "$OPENFIRE_HOME/conf/openfire-demoboot.xml" > "$OPENFIRE_HOME/conf/openfire.xml"
      ;;
    *)
      # unknown option, pass through the Java command
      JAVACMD="$JAVACMD $arguments"
      ;;
  esac
done

# Java security config
OPENFIRE_OPTS="${OPENFIRE_OPTS} -Djava.security.properties=${OPENFIRE_HOME}/resources/security/java.security"

# Enable OCSP Stapling
OPENFIRE_OPTS="${OPENFIRE_OPTS} -Djdk.tls.server.enableStatusRequestExtension=true"

# Enable the CRL Distribution Points extension
OPENFIRE_OPTS="${OPENFIRE_OPTS} -Dcom.sun.security.enableCRLDP=true"

# Auto-upgrade legacy encrypted XML properties to use random IV (secure by default)
# Admins can disable by setting: -Dopenfire.xmlproperties.encryption.autoupgrade=false
OPENFIRE_OPTS="${OPENFIRE_OPTS} -Dopenfire.xmlproperties.encryption.autoupgrade=true"

JAVACMD="${JAVACMD} -Dlog4j.configurationFile=${OPENFIRE_LIB}/log4j2.xml -Dlog4j2.formatMsgNoLookups=true -Djdk.tls.ephemeralDHKeySize=matched -Djsse.SSLEngine.acceptLargeFragments=true -Djava.net.preferIPv6Addresses=system"

if [ -z "$LOCALCLASSPATH" ] ; then
  LOCALCLASSPATH=$OPENFIRE_LIB/startup.jar
else
  LOCALCLASSPATH=$OPENFIRE_LIB/startup.jar:$LOCALCLASSPATH
fi

# For Cygwin, switch paths to appropriate format before running java
if $cygwin; then
  if [ "$OS" = "Windows_NT" ] && cygpath -m .>/dev/null 2>/dev/null ; then
    format=mixed
  else
    format=windows
  fi
  OPENFIRE_HOME=$(cygpath --$format "$OPENFIRE_HOME")
  OPENFIRE_LIB=$(cygpath --$format "$OPENFIRE_LIB")
  JAVA_HOME=$(cygpath --$format "$JAVA_HOME")
  LOCALCLASSPATH=$(cygpath --path --$format "$LOCALCLASSPATH")
  if [ -n "$CLASSPATH" ] ; then
    CLASSPATH=$(cygpath --path --$format "$CLASSPATH")
  fi
  CYGHOME=$(cygpath --$format "$HOME")

  # add a second backslash to variables terminated by a backslash under cygwin
  case "$OPENFIRE_HOME" in
    *\\ )
    OPENFIRE_HOME="$OPENFIRE_HOME\\"
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

openfire_exec_command="exec $JAVACMD -server $OPENFIRE_OPTS -classpath \"$LOCALCLASSPATH\" -jar \"$OPENFIRE_LIB/startup.jar\""
# shellcheck disable=SC2086
eval $openfire_exec_command
