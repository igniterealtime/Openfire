#!/bin/bash
CURRENT_FLAG=`sudo awk '/RunAtLoad/{getline; print}' /Library/LaunchDaemons/org.jivesoftware.wildfire.plist | tr -d '\n' | grep true`

echo "Current flag is $CURRENT_FLAG"

if [ -z "$CURRENT_FLAG" ] ; then
CURRENT_FLAG="false"
NEW_FLAG="true"
else
CURRENT_FLAG="true"
NEW_FLAG="false"
fi

sudo sed -i "" -e "/RunAtLoad/ {
    n
    s/$CURRENT_FLAG/$NEW_FLAG/
}" /Library/LaunchDaemons/org.jivesoftware.wildfire.plist
