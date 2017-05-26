<?php
/*
Removes: Options, Demo Post Type, Piklist Tables
*/

  if (!defined('ABSPATH') && !defined('WP_UNINSTALL_PLUGIN'))
  {
    exit;
  }

  global $wpdb;

  delete_option('piklist'); // TODO: check for add-ons from other plugins.
  delete_option('piklist_demo_fields');
  delete_option('piklist_active_plugin_versions');
 

  $wpdb->query("DELETE FROM $wpdb->options WHERE option_name LIKE '%_pik_%';");

  // Delete all Demo posts
  $demos = get_posts(array(
    'numberposts' => -1
    ,'post_type' =>'piklist_demo'
    ,'post_status' => 'all'
  ));
  
  if ($demos)
  {
    foreach ($demos as $post)
    {
      wp_delete_post($post->ID, true);
    }
  }

  $wpdb->query("DROP TABLE IF EXISTS {$wpdb->base_prefix}post_relationships");

  // Pre-0.6.7
  $wpdb->query("DROP TABLE IF EXISTS {$wpdb->base_prefix}piklist_cpt_relate");

  /** Sorry to see you go! **/

?>