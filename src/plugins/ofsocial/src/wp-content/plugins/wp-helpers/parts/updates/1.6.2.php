<?php
/*
 * If settings exist for Toolbox:
 ** copy them to WP Helpers.
 ** delete piklist_toolbox_settings option
 ** redirect to the plugins page
 */

  $piklist_toolbox_settings = get_option('piklist_toolbox_settings');

  if (!empty($piklist_toolbox_settings)):
  
    add_option('piklist_wp_helpers', $piklist_toolbox_settings, '', 'no');

    delete_option('piklist_toolbox_settings');

		wp_redirect(admin_url('plugins.php'));

		exit;

  endif;