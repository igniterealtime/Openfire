<?php
/*
Title: Default
Order: 10
Flow: User Test
Default: true
*/
  
  piklist('include_user_profile_fields', array(
    'meta_boxes' => array(
      'Personal Options'
      ,'Name'
      ,'Contact Info'
      ,'About the user'
      ,'About Yourself'
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Workflow Tab'
  ));

?>