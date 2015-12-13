<?php
/*
Title: Debug
Setting: piklist_wp_helpers
Tab: Develop
Order: 99
Tab Order: 999
Flow: WP Helpers Settings Flow
*/

  $setting = get_option('piklist_wp_helpers',false);

  piklist('field', array(
    'type' => 'textarea'
    ,'label' => __('WP Helpers database settings', 'wp-helpers')
    ,'value' => serialize($setting)
    ,'attributes' => array(
      'readonly' => 'readonly'
    )
));