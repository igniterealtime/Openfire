#!/usr/bin/env bash
# See how we are called
if [ "$#" -ne 2 ]; then
    echo "USAGE: sh build/rpm/build_rpms.sh <rpmarch> <fullpath_to_jrefile>"
    echo "  in the case of noarch, second argument is ignored"
    exit 2
fi
export RPMARCH=$1
export JRE_BUNDLE=$2
export RPMBUILD_HOME=$PWD/rpmbuild

# Remove previous rpmbuild folder
if [ -d $RPMBUILD_HOME ]; then
    echo "Removing previous rpmbuild folder"
    rm -rf $RPMBUILD_HOME
fi


# Setup rpmbuild folders
mkdir -p ${RPMBUILD_HOME}/SPECS
mkdir -p ${RPMBUILD_HOME}/SOURCES
mkdir -p ${RPMBUILD_HOME}/BUILD
mkdir -p ${RPMBUILD_HOME}/SRPMS
mkdir -p ${RPMBUILD_HOME}/RPMS

if [ -f $JRE_BUNDLE ]; then
    cp -f $JRE_BUNDLE ${RPMBUILD_HOME}/SOURCES/
fi

# Define some variables
export RPM_BUILDDATE=$(date +'%a %b %d %Y')
export OPENFIRE_REPOVERSION=$(git rev-parse --short HEAD)
export OPENFIRE_FULLVERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
export OPENFIRE_VERSION=$(echo "${OPENFIRE_FULLVERSION}" | cut -d'-' -f1)

# Setup the RPM versioning correctly, so one can update from 
# a alpha,beta,rc build to GA 
# For General Releases we get x.y.z-1
# For Alpha builds we get     x.y.z-0.1.{YYYYMMDD}alpha
# For Beta/RC builds we get   x.y.z-0.2.(beta|rc)
if [[ $OPENFIRE_FULLVERSION = *"SNAPSHOT"* ]]; then
    export OPENFIRE_RELEASE="0.1.$(date +'%Y%m%d')alpha"
elif [[ $OPENFIRE_FULLVERSION = *"beta"* ]]; then
    export OPENFIRE_RELEASE="0.2.beta"
else
    export OPENFIRE_RELEASE="1"
fi

# generate our psuedo source tree, which is actually dist tree from maven
cd distribution/target
cp -r distribution-base openfire
mkdir -p openfire/logs
tar -czf openfire.tar.gz openfire
rm -rf openfire
mv openfire.tar.gz ${RPMBUILD_HOME}/SOURCES/
cd ../..

# Finally build the RPM
rpmbuild -bb \
  --target ${RPMARCH} \
  --define "_topdir ${RPMBUILD_HOME}" \
  --define "JRE_BUNDLE ${JRE_BUNDLE}" \
  --define "OPENFIRE_BUILDDATE ${RPM_BUILDDATE}" \
  --define "OPENFIRE_VERSION ${OPENFIRE_VERSION}" \
  --define "OPENFIRE_RELEASE ${OPENFIRE_RELEASE}" \
  --define "OPENFIRE_SOURCE openfire.tar.gz" \
  --define "OPENFIRE_REPOVERSION ${OPENFIRE_REPOVERSION}" \
  build/rpm/openfire.spec

# Move generated artifacts back into a rpms folder, so bamboo can grab it
mkdir -p distribution/target/rpms
mv ${RPMBUILD_HOME}/RPMS/${RPMARCH}/openfire*rpm distribution/target/rpms/
