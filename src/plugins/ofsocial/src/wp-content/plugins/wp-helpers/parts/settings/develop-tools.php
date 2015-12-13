<?php
/*
Title: Tools
Setting: piklist_wp_helpers
Tab: Develop
Order: 20
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'delete_orphaned_meta'
    ,'label' => __('Orphaned Meta', 'piklist-toolbox')
    ,'description' => __('Schedule for deletion', 'piklist-toolbox')
    ,'choices' => array(
      'true' => __('Delete', 'piklist-toolbox')
    )
  ));