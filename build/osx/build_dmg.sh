#!/usr/bin/env bash
# Script to build Openfire Mac DMG artifact
set -x

export OPENFIRE_FULLVERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
export OPENFIRE_VERSION=$(echo "${OPENFIRE_FULLVERSION}" | cut -d'-' -f1)
export VERSION_MAJOR=$(echo "${OPENFIRE_VERSION}" | cut -d'.' -f1)
export VERSION_MINOR=$(echo "${OPENFIRE_VERSION}" | cut -d'.' -f2)
export TARGET_OSX=distribution/build/osx
export MAC_PKG_DIR=${TARGET_OSX}/macpkg
export MAC_DMG_DIR=${TARGET_OSX}/Openfire
export MAC_DMG_FILE=openfire.dmg
export MAC_TEMPLATE=${TARGET_OSX}/template
export MAC_PREFPANE_BUILD=${TARGET_OSX}/prefPane
export COPYRIGHTYEAR=$(date +'%Y')

# replicating ant target mac.delete
rm -rf ${TARGET_OSX}
rm -f $MAC_DMG_FILE

# replicating ant target mac.prefpane
mkdir -p $MAC_PREFPANE_BUILD
cp -R build/osx/openfirePrefPane/* $MAC_PREFPANE_BUILD/
cd $MAC_PREFPANE_BUILD
/usr/bin/xcodebuild -configuration Deployment -target Openfire clean install
cd ../../../..

# replicating ant target mac.prepare
mkdir -p $MAC_PKG_DIR/usr/local/openfire
cp -R distribution/target/distribution-base/. $MAC_PKG_DIR/usr/local/openfire/
mkdir -p $MAC_PKG_DIR/Library/LaunchDaemons
cp build/osx/org.jivesoftware.openfire.plist $MAC_PKG_DIR/Library/LaunchDaemons
mkdir -p $MAC_DMG_DIR
mkdir -p $MAC_PKG_DIR/Library/PreferencePanes
# <copy todir="${mac.pkg.dir}/Library/PreferencePanes">
#            <fileset dir="${mac.prefpane.build}/build/UninstalledProducts/"/>
#        </copy>
#        <chmod perm="o+x">
#            <fileset dir="${mac.pkg.dir}/Library/PreferencePanes/Openfire.prefPane/Contents/MacOS/">
#                <include name="HelperTool"/>
#            </fileset>
#        </chmod>
mkdir -p $MAC_TEMPLATE/.background
cp build/osx/dmgBackground.png $MAC_TEMPLATE/.background/

# replicating ant target mac.pkg
cp build/osx/Info.plist $TARGET_OSX/
sed -i.bak s/@VERSION@/${OPENFIRE_VERSION}/g $TARGET_OSX/Info.plist
sed -i.bak s/@VERSIONMAJOR@/"${VERSION_MAJOR}"/g $TARGET_OSX/Info.plist
sed -i.bak s/@VERSIONMINOR@/"${VERSION_MINOR}"/g $TARGET_OSX/Info.plist
sed -i.bak s/@COPYRIGHT@/"${COPYRIGHTYEAR}"/g $TARGET_OSX/Info.plist

cp build/osx/Description.plist $TARGET_OSX/

# -proj build/osx/openfire.pmproj
/Developer/usr/bin/packagemaker -build \
  -f ${MAC_PKG_DIR} \
  -i ${TARGET_OSX}/Info.plist \
  -d ${TARGET_OSX}/Description.plist \
  -r build/osx/resources \
  -p ${MAC_TEMPLATE}/Openfire.pkg \
  -ds \
  -v

# replicating mac target installer.mac
mkdir -p distribution/target/macosx
hdiutil create -srcfolder "${MAC_TEMPLATE}" -volname 'Openfire' \
  -fs HFS+ -fsargs '-c c=64,a=16,e=16' \
  -format UDRW "${TARGET_OSX}/tmp.dmg"

hdiutil attach "${TARGET_OSX}/tmp.dmg" -readwrite -noverify \
  -noautoopen -noidme -mountpoint "${MAC_DMG_DIR}"

hdiutil detach ${MAC_DMG_DIR} -quiet -force

hdiutil convert ${TARGET_OSX}/tmp.dmg -format UDZO \
  -imagekey zlib-level=9 -o ${MAC_DMG_DIR}
