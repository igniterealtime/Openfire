#!/bin/sh

set -x

buddypress_dir=$WP_CORE_DIR/wp-content/plugins/buddypress

# Grab specified version of BP
wget -nv -O /tmp/buddypress.tar.gz https://github.com/BuddyPress/BuddyPress/tarball/$BP_VERSION
mkdir -p $buddypress_dir
tar --strip-components=1 -zxmf /tmp/buddypress.tar.gz -C $buddypress_dir

# go back to the plugin dir
pwd
cd .. 
pwd

set +x
