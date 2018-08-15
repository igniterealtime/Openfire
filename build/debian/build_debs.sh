#!/usr/bin/env bash
# CALL LIKE SO: bash build/debian/build_debs.sh
# set -x

pushd .
export OPENFIRE_FULLVERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
export OPENFIRE_VERSION=$(echo "${OPENFIRE_FULLVERSION}" | cut -d'-' -f1)
export DEBIAN_BUILDDATE="$(date +'%a, %d %b %Y %H:%M:%S %z')"
export WORKDIR=$HOME/debian/openfire-${OPENFIRE_VERSION}

if [ -d "$HOME/debian" ]; then
    echo "Removing previous workdir $HOME/debian"
    rm -rf $HOME/debian
fi
mkdir -p $WORKDIR

cp -r distribution/target/distribution-base/. $WORKDIR/
# Need to copy in plugins that are defaulted to distribute
cp -f src/plugins/search/target/search.jar $WORKDIR/plugins/

mkdir -p $WORKDIR/debian
cp build/debian/* $WORKDIR/debian/
# HACK remove out this actual script
rm -f $WORKDIR/debian/build_debs.sh

# make rules executable
chmod 755 $WORKDIR/debian/rules

cd $WORKDIR/debian
# Do some needed replacements
sed -i.bak s/@version@/${OPENFIRE_VERSION}/g changelog
sed -i.bak s/@builddate@/"${DEBIAN_BUILDDATE}"/g changelog
cd ..
dpkg-buildpackage -rfakeroot -us -uc

pushd
mkdir -p distribution/target/debian
mv $HOME/debian/openfire*deb distribution/target/debian/
