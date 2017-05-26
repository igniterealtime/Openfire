<?php
/*
 * Updates for v0.7.2
 * 
 * This file should:
 ** Be written as if updating one site. Piklist will handle multisite.
 ** Only contain PHP code.
 ** Have no functions defined within it.
 */

  global $wpdb;

  $legacy_table = $wpdb->prefix . 'piklist_cpt_relate';

  $count = $wpdb->get_var("SHOW TABLES LIKE '{$legacy_table}'");

  // Does the legacy table exist?
  if (!empty($count))
  {
    // Grab data from legacy table
    $data = $wpdb->get_results("SELECT * FROM {$legacy_table}", ARRAY_A);

    // Move data to new table
    foreach ($data as $row)
    {
      $wpdb->insert(
        $wpdb->post_relationships
        ,$row
        ,array( 
          '%d'
          ,'%d'
          ,'%d'
        ) 
      );
    }

    // Delete legacy table
    $wpdb->query("DROP TABLE IF EXISTS {$legacy_table}");
  }

?>