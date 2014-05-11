#!/bin/bash
#
# This script need:
#  npm install jsdom
#  npm install -g uglify-js
# gzip, node and npm
#
# GPL 2012, Iván Eixarch
GITLANGUAGES="https://github.com/joker-x/languages4translatewiki"
set -e
SCRIPTPATH=$( cd $(dirname $0) ; pwd -P )
cd "$SCRIPTPATH"
echo "Scraping translatewiki to languages.json..."
JSON=$(node scrap.js)
HEADER=$(cat header.js)
FOOTER=$(cat footer.js)
cd ..
echo -e "${JSON}" > languages.json
echo "Building languages.js..."
echo -e "${HEADER}\nvar langs = ${JSON}\n${FOOTER}" > languages.js 
echo "Uglifying to languages.min.js"
MINJS=$(uglifyjs languages.js)
echo -e "/* From: ${GITLANGUAGES} */\n${MINJS}" > languages.min.js
echo "Gziping..."
gzip -9 -c languages.min.js > languages.min.js.gz
gzip -9 -c languages.json > languages.json.gz
touch languages.min.js languages.min.js.gz
touch languages.json languages.json.gz
read -p "Pulse ENTER to test it" KEY
node test/node-example.js

