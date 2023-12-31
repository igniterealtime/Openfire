#!/bin/bash
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
OFROOT=$(realpath "$SCRIPTPATH/../../")
DOMAPMAVEN=true
CREATEMAVEN=false
SKIPTESTS=false

usage()
{
	echo "Builds Openfire using a Maven docker container"
    echo ""
    echo "  -m: Don't attempt to map host maven cache to container"
    echo "  -c: Create a local maven cache if one doesn't exist"
    echo "  -s: Skip building or running tests. Use with care."
}

while getopts cms OPTION "$@"; do
    case $OPTION in
        c)
            CREATEMAVEN=true
            ;;
        m)
            DOMAPMAVEN=false
            ;;
        s)
            SKIPTESTS=true
            ;;
        \? ) usage && exit 0;;
        :  ) usage && exit 1;;
        *  ) usage && exit 1;;
    esac
done

if [ $DOMAPMAVEN == true ]; then
    if [ -z "$HOME" ]; then
        echo "Cannot map Maven cache - HOME variable is not set"
    fi
    if [ ! -d "$HOME/.m2" ] && [ $CREATEMAVEN == true ]; then
        mkdir "$HOME/.m2"
    fi
    if [ -d "$HOME/.m2" ]; then
        MAVENMAP="-v=$HOME/.m2:/root/.m2"
    fi
fi

DOCKERCMD=(
    docker run -it --rm
    -v="$OFROOT":/usr/src/openfire
)
if [ -n "$MAVENMAP" ]; then
    DOCKERCMD+=("$MAVENMAP")
fi
DOCKERCMD+=(
    -w /usr/src/openfire
    eclipse-temurin:11-jdk \
    ./mvnw clean package
)
if [ $SKIPTESTS == true ]; then
    DOCKERCMD+=(
        -Dmaven.test.skip -DskipTests
    )
fi

"${DOCKERCMD[@]}"