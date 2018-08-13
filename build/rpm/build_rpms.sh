#!/usr/bin/env bash
# See how we are called
if [ "$#" -ne 2 ]; then
    echo "USAGE: sh build/rpm/build_rpms.sh <rpmarch> <fullpath_to_jrefile>"
    echo "  in the case of noarch, second argument is ignored"
    exit 2
fi
export RPMARCH=$1
export JRE_BUNDLE=$2

# Setup rpmbuild folders
mkdir -p ~/rpmbuild/SPECS
mkdir -p ~/rpmbuild/SOURCES
mkdir -p ~/rpmbuild/BUILD
mkdir -p ~/rpmbuild/SRPMS
mkdir -p ~/rpmbuild/RPMS

if [ -f $JRE_BUNDLE ]; then
    cp -f $JRE_BUNDLE ~/rpmbuild/SOURCES/
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

# Need to copy in plugins that are defaulted to distribute
cp -f plugins/search/target/search.jar distribution/target/distribution-base/plugins/

# generate our psuedo source tree, which is actually dist tree from maven
cd distribution/target
cp -r distribution-base openfire
mkdir -p openfire/logs
tar -czf openfire.tar.gz openfire
rm -rf openfire
mv openfire.tar.gz ~/rpmbuild/SOURCES/
cd ../..

# Finally build the RPM
rpmbuild -bb \
  --target ${RPMARCH} \
  --define "JRE_BUNDLE ${JRE_BUNDLE}" \
  --define "OPENFIRE_BUILDDATE ${RPM_BUILDDATE}" \
  --define "OPENFIRE_VERSION ${OPENFIRE_VERSION}" \
  --define "OPENFIRE_RELEASE ${OPENFIRE_RELEASE}" \
  --define "OPENFIRE_SOURCE openfire.tar.gz" \
  --define "OPENFIRE_REPOVERSION ${OPENFIRE_REPOVERSION}" \
  build/rpm/openfire.spec

# Move generated artifacts back into a rpms folder, so bamboo can grab it
mkdir -p distribution/target/rpms
mv ~/rpmbuild/RPMS/${RPMARCH}/openfire*rpm distribution/target/rpms/
