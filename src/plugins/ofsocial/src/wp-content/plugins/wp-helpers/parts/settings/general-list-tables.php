<?php
/*
Title: List Tables
Setting: piklist_wp_helpers
Order: 105
Flow: WP Helpers Settings Flow
*/
  
  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'show_ids'
    ,'label' => 'Show ID\'s'
    ,'choices' => array(
      'true' => 'Show ID\'s on edit screens for Posts, Pages, Users, etc.'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'show_featured_image'
    ,'label' => 'Show Featured Image'
    ,'choices' => array(
      'true' => 'Show Featured Image on edit screens for Post Types.'
    )
  ));