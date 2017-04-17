<?php
/*
Title: Theme
Setting: piklist_wp_helpers
Tab: Appearance
Order: 500
Tab Order: 40
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'enhanced_classes'
    ,'label' => 'Body/Post Classes'
    ,'choices' => array(
      'true' => 'Use enhanced classes.'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'clean_header'
    ,'label' => 'Remove from template header'
    ,'choices' => array(
      'wp_generator' => 'WP Generator (WordPress version)'
      ,'feed_links' => 'Feed Links (Links to general feeds)'
      ,'feed_links_extra' => 'Feed Links extras (Links to additional feeds)'
      ,'rsd_link' => 'RSD Link (Link to the Really Simple Discovery service endpoint)'
      ,'wlwmanifest_link' => 'wlwmanifest (Link to Windows Live Writer manifest file)'
      ,'adjacent_posts_rel_link_wp_head' => 'Relational links for the posts adjacent to the current post.'
    )
  ));