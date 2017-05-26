<?php
/*
Title: Admin Bar
Setting: piklist_wp_helpers
Order: 120
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'hide_admin_bar'
    ,'label' => 'Frontend Admin Bar'
    ,'choices' => array(
      'true' => 'Remove'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'show_admin_bar_components'
    ,'label' => 'Remove Admin Bar Components'
    ,'choices' => array(
      'new-content' => 'Add New'
      ,'comments' => 'Comment Bubble'
      ,'my-account' => 'Greeting'
      ,'wp-logo' => 'Logo'
      ,'my-sites' => 'My Sites'
      ,'search' => 'Search (Front-End Only)'
      ,'site-name' => 'Site Name'
      ,'updates' => 'Updates'
    )
  ));

  //TODO: Conditional with above
  piklist('field', array(
    'type' => 'text'
    ,'field' => 'change_howdy'
    ,'label' => 'Admin Bar greeting'
    ,'description' => 'Leave blank for default "Howdy,"'
    ,'attributes' => array(
      'class' => 'regular-text'
    )
  ));