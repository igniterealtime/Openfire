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
cp -a $TARGET_OSX/prefPane/build/UninstalledProducts/macosx/* ${MAC_PKG_DIR}/Library/PreferencePanes/
mkdir -p $MAC_TEMPLATE/.background
cp build/osx/dmgBackground.png $MAC_TEMPLATE/.background/

# replicating ant target mac.pkg
pkgbuild --identifier "com.jivesoftware.openfire" \
         --version "${OPENFIRE_FULLVERSION}" \
         --root "${MAC_PKG_DIR}" \
         ${TARGET_OSX}/PrefPane.pkg

## NOTE: this would generate a distribution.plist, but by keeping a copy in git
## we can instead tweak its metadata a bit.
#cat << EOF > ${TARGET_OSX}/requirements.plist
#<?xml version="1.0" encoding="UTF-8"?>
#<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
#<plist version="1.0">
#<dict>
#  <key>os</key>
#  <array>
#    <string>10.6</string>
#  </array>
#</dict>
#</plist>
#EOF
#productbuild --synthesize \
#  --product ${TARGET_OSX}/requirements.plist \
#  --package ${TARGET_OSX}/PrefPane.pkg \
#  ${TARGET_OSX}/distribution.plist

cp build/osx/distribution.plist $TARGET_OSX/
sed -i.bak s/%VERSION%/${OPENFIRE_VERSION}/g $TARGET_OSX/distribution.plist
#sed -i.bak s/%VERSIONMAJOR%/"${VERSION_MAJOR}"/g $TARGET_OSX/distribution.plist
#sed -i.bak s/%VERSIONMINOR%/"${VERSION_MINOR}"/g $TARGET_OSX/distribution.plist
sed -i.bak s/%COPYRIGHT%/"${COPYRIGHTYEAR}"/g $TARGET_OSX/distribution.plist

# -proj build/osx/openfire.pmproj

productbuild \
  --distribution ${TARGET_OSX}/distribution.plist \
  --resources build/osx/resources \
  --package-path ${TARGET_OSX} \
  ${MAC_TEMPLATE}/Openfire.pkg

# replicating mac target installer.mac
mkdir -p distribution/target/macosx
hdiutil create -srcfolder "${MAC_TEMPLATE}" -volname 'Openfire' \
  -fs HFS+ -fsargs '-c c=64,a=16,e=16' \
  -format UDRW "${TARGET_OSX}/tmp.dmg"

hdiutil attach "${TARGET_OSX}/tmp.dmg" -readwrite -noverify \
  -noautoopen -noidme -mountpoint "${MAC_DMG_DIR}"

# OF-386 - commented out since it wasn't working with our Bamboo remote agent
# OF-1587 - an attempted fix for pretty DMG background
osascript build/osx/dmg_openfire.scpt Openfire build/osx 648 500 450 205 128

hdiutil detach ${MAC_DMG_DIR} -quiet -force

hdiutil convert ${TARGET_OSX}/tmp.dmg -format UDZO \
  -imagekey zlib-level=9 -o ${MAC_DMG_DIR}
