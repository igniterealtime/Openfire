<?php
/*
Title: Communication
Setting: piklist_wp_helpers
Tab: Users
Order: 605
Flow: WP Helpers Settings Flow
*/

?>

  <p>Change the emails sent from WordPress</p>

<?php

  piklist('field', array(
    'type' => 'text'
    ,'label' => 'Email Address'
    ,'field' => 'mail_from'
    ,'attributes' => array(
      'class' => 'regular-text'
    )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'label' => 'Email Name'
    ,'field' => 'mail_from_name'
    ,'attributes' => array(
      'class' => 'regular-text'
    )
  ));