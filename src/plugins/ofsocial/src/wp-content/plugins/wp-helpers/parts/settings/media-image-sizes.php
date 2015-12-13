<?php
/*
Title: Sizes
Setting: piklist_wp_helpers
Tab: Media
Order: 700
Tab Order: 70
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'show_additional_image_sizes'
    ,'label' => 'Image sizes'
    ,'help' => 'Once selected, edit any image in your Media Library to see additional image information'
    ,'list' => true
    ,'choices' => array(
      'true' => 'Show sizes and urls on image edit screen'
    )
  ));