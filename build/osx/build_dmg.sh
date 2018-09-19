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
cp build/osx/distribution.plist $TARGET_OSX/
sed -i.bak s/%VERSION%/${OPENFIRE_VERSION}/g $TARGET_OSX/distribution.plist
sed -i.bak s/%VERSIONMAJOR%/"${VERSION_MAJOR}"/g $TARGET_OSX/distribution.plist
sed -i.bak s/%VERSIONMINOR%/"${VERSION_MINOR}"/g $TARGET_OSX/distribution.plist
sed -i.bak s/%COPYRIGHT%/"${COPYRIGHTYEAR}"/g $TARGET_OSX/distribution.plist

# -proj build/osx/openfire.pmproj

pkgbuild --identifier "com.jivesoftware.openfire" \
         --version "${OPENFIRE_FULLVERSION}" \
         --root "${MAC_PKG_DIR}" \
         ${TARGET_OSX}/Openfire.pkg
cat << EOF > ${TARGET_OSX}/requirements.plist
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>os</key>
  <array>
    <string>10.6</string>
  </array>
</dict>
</plist>
EOF
productbuild --synthesize \
  --product ${TARGET_OSX}/requirements.plist \
  --package ${TARGET_OSX}/Openfire.pkg \
  ${TARGET_OSX}/distribution.plist
productbuild \
  --distribution ${TARGET_OSX}/distribution.plist \
  --resources build/osx/resources \
  --package-path ${TARGET_OSX}/Openfire.pkg \
  ${MAC_TEMPLATE}/Openfire.pkg

exit 200
# replicating mac target installer.mac
mkdir -p distribution/target/macosx
hdiutil create -srcfolder "${MAC_TEMPLATE}" -volname 'Openfire' \
  -fs HFS+ -fsargs '-c c=64,a=16,e=16' \
  -format UDRW "${TARGET_OSX}/tmp.dmg"

hdiutil attach "${TARGET_OSX}/tmp.dmg" -readwrite -noverify \
  -noautoopen -noidme -mountpoint "${MAC_DMG_DIR}"

#echo '
#   tell application "Finder"
#     tell disk "'${title}'"
#           open
#           set current view of container window to icon view
#           set toolbar visible of container window to false
#           set statusbar visible of container window to false
#           set the bounds of container window to {400, 100, 885, 430}
#           set theViewOptions to the icon view options of container window
#           set arrangement of theViewOptions to not arranged
#           set icon size of theViewOptions to 72
#           set background picture of theViewOptions to file ".background:'${backgroundPictureName}'"
#           make new alias file at container window to POSIX file "/Applications" with properties {name:"Applications"}
#           set position of item "'${applicationName}'" of container window to {100, 100}
#           set position of item "Applications" of container window to {375, 100}
#           update without registering applications
#           delay 5
#           close
#     end tell
#   end tell
#' | osascript

hdiutil detach ${MAC_DMG_DIR} -quiet -force

hdiutil convert ${TARGET_OSX}/tmp.dmg -format UDZO \
  -imagekey zlib-level=9 -o ${MAC_DMG_DIR}
