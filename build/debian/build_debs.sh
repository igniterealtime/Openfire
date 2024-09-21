#!/usr/bin/env bash
# CALL LIKE SO: bash build/debian/build_debs.sh
# set -x

pushd .
# This allows us to pass in the Openfire version from the command-line.
if [ $1 ]; then
    OPENFIRE_FULLVERSION=$1
else
    # extract version from pom.xml
    OPENFIRE_FULLVERSION=$(grep -oP "<version>(.*)</version>" -m 1 pom.xml | cut -d ">" -f 2 | cut -d "<" -f 1)
fi
OPENFIRE_VERSION=$(echo "${OPENFIRE_FULLVERSION}" | cut -d'-' -f1)
DEBIAN_BUILDDATE="$(date +'%a, %d %b %Y %H:%M:%S %z')"
WORKDIR=tmp/debian/openfire-${OPENFIRE_VERSION}

if [ -d "tmp/debian" ]; then
    echo "Removing previous workdir tmp/debian"
    rm -rf tmp/debian
fi
mkdir -p $WORKDIR

cp -r distribution/target/distribution-base/. $WORKDIR/

mkdir -p $WORKDIR/debian
cp -r build/debian/* $WORKDIR/debian/

cd $WORKDIR/debian
# Do some needed replacements
sed -i.bak s/@version@/${OPENFIRE_VERSION}/g changelog
sed -i.bak s/@builddate@/"${DEBIAN_BUILDDATE}"/g changelog
cd ..
# Disable build-dependencies, because they're often not useful here.
dpkg-buildpackage -d -rfakeroot -us -uc

pushd
mkdir -p distribution/target/debian
mv tmp/debian/openfire*deb distribution/target/debian/
