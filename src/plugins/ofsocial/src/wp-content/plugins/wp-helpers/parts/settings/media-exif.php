<?php
/*
Title: Exif Data
Setting: piklist_wp_helpers
Tab: Media
Order: 750
Tab Order: 70
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'show_exif_data'
    ,'label' => 'Show Exif data'
    ,'help' => 'Once selected, edit any image in your Media Library to see Exif data.'
    ,'list' => true
    ,'choices' => array(
      'true' => 'Show Exif data on image edit screen, if available.'
    )
  ));