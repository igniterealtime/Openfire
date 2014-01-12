#
# Makefile for Candy
# Candy - Chats are not dead yet
#
# Copyright:
# (c) 2011 Amiado Group AG
#
# Authors:
#   - Patrick Stadler <patrick.stadler@gmail.com>
#   - Michael Weibel <michael.weibel@gmail.com>
#

SHELL=/bin/bash

DOC_DIR = docs
NDPROJ_DIR = .ndproj
SRC_DIR = src
LIBS_DIR = libs

CANDY_BUNDLE = candy.bundle.js
CANDY_BUNDLE_MIN = candy.min.js
CANDY_BUNDLE_LIBRARIES = libs/libs.bundle.js
CANDY_BUNDLE_LIBRARIES_MIN = libs/libs.min.js
CANDY_FILES = $(SRC_DIR)/candy.js $(SRC_DIR)/core.js $(SRC_DIR)/view.js $(SRC_DIR)/util.js $(SRC_DIR)/core/action.js $(SRC_DIR)/core/chatRoom.js $(SRC_DIR)/core/chatRoster.js $(SRC_DIR)/core/chatUser.js $(SRC_DIR)/core/event.js $(SRC_DIR)/view/event.js $(SRC_DIR)/view/observer.js $(SRC_DIR)/view/pane.js $(SRC_DIR)/view/template.js $(SRC_DIR)/view/translation.js
CANDY_LIBS_FILES = $(LIBS_DIR)/strophejs/strophe.js $(LIBS_DIR)/strophejs-plugins/muc/strophe.muc.js $(LIBS_DIR)/mustache.js/mustache.js $(LIBS_DIR)/jquery-i18n/jquery.i18n.js $(LIBS_DIR)/dateformat/dateFormat.js
CANDY_FILES_BUNDLE = $(CANDY_FILES:.js=.bundle)
CANDY_LIBS_FILES_BUNDLE = $(CANDY_LIBS_FILES:.js=.libs-bundle)

all: bundle min

bundle: clean-bundle $(CANDY_FILES_BUNDLE)

%.bundle: %.js
	@@echo -n "Bundling" $< "..."
	@@cat $< >> $(CANDY_BUNDLE)
	@@echo "done"

min: $(CANDY_BUNDLE)
	@@echo -n "Compressing" $(CANDY_BUNDLE) "..."
ifdef YUI_COMPRESSOR
	@@java -jar $(YUI_COMPRESSOR) --type js $(CANDY_BUNDLE) -o $(CANDY_BUNDLE_MIN) --charset utf-8
	@@echo "done ("$(CANDY_BUNDLE_MIN)")"
else
	@@echo "aborted"
	@@echo "** You can safely use the uncompressed bundle ("$(CANDY_BUNDLE)")"
	@@echo "** YUI Compressor is required to build the minified version."
	@@echo "** Please set YUI_COMPRESSOR to the path to the jar file."
endif

libs: libs-bundle libs-min

libs-bundle: clean-libs $(CANDY_LIBS_FILES_BUNDLE)

%.libs-bundle: %.js
	@@echo -n "Bundling" $< "..."
	@@cat $< >> $(CANDY_BUNDLE_LIBRARIES)
	@@echo "done"

libs-min: $(CANDY_BUNDLE_LIBRARIES)
	@@echo -n "Compressing" $(CANDY_BUNDLE_LIBRARIES) "..."
ifdef YUI_COMPRESSOR
	@@java -jar $(YUI_COMPRESSOR) --type js $(CANDY_BUNDLE_LIBRARIES) -o $(CANDY_BUNDLE_LIBRARIES_MIN) --charset utf-8
	@@echo "done ("$(CANDY_BUNDLE_LIBRARIES_MIN)")"
else
	@@echo "aborted"
	@@echo "** You can safely use the uncompressed bundle ("$(CANDY_BUNDLE_LIBRARIES)")"
	@@echo "** YUI Compressor is required to build the minified version."
	@@echo "** Please set YUI_COMPRESSOR to the path to the jar file."
endif

docs:
	@@echo "Building candy documentation ..."
ifdef NATURALDOCS_DIR
	@@if [ ! -d $(NDPROJ_DIR) ]; then mkdir $(NDPROJ_DIR); fi
	@@if [ ! -d $(DOC_DIR) ]; then mkdir $(DOC_DIR); fi
	@@$(NATURALDOCS_DIR)/NaturalDocs -q --exclude-source libs --exclude-source res --exclude-source candy.min.js --exclude-source candy.bundle.js -i . -o html $(DOC_DIR) -p $(NDPROJ_DIR)
	@@rm -r $(NDPROJ_DIR)
	@@echo "Documentation built."
	@@echo
else
	@@echo "aborted"
	@@echo "** NaturalDocs is required to build the documentation."
	@@echo "** Please set NATURALDOCS_DIR to the path to the NaturalDocs executable"
endif

clean: clean-bundle clean-libs

clean-bundle:
	@@echo -n "Cleaning bundles ..."
	@@rm -f $(CANDY_BUNDLE) $(CANDY_BUNDLE_MIN)
	@@echo "done"

clean-libs:
	@@echo -n "Cleaning library bundles ..."
	@@rm -f $(CANDY_BUNDLE_LIBRARIES) $(CANDY_BUNDLE_LIBRARIES_MIN)
	@@echo "done"

clean-docs:
	@@echo -n "Cleaning documentation ..."
	@@rm -rf $(NDPROJ_DIR) $(DOC_DIR)
	@@echo "done"

.PHONY: all docs clean libs
