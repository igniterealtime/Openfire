<?php
/*
Title: User Profiles
Setting: piklist_wp_helpers
Tab: Users
Order: 600
Tab Order: 60
Flow: WP Helpers Settings Flow
*/


  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'admin_color_scheme'
    ,'label' => 'Admin color scheme option'
    ,'choices' => array(
      'true' => 'Remove: Admin color scheme option'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'profile_fields'
    ,'label' => 'Remove profile fields'
    ,'choices' => array(
      'aim' => 'AIM'
      ,'yim' => 'Yahoo IM'
      ,'jabber' => 'Jabber/Google Talk'
    )
  ));