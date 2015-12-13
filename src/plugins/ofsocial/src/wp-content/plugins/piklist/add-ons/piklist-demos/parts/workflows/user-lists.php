<?php
/*
Title: Lists
Order: 40
Flow: User Test
*/


  piklist('include_user_profile_fields', array(
    'meta_boxes' => array(
      'Multiselect Fields'
      ,'Select Fields'
      ,'Checkbox Fields'
      ,'Radio Fields'
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Workflow Tab'
  ));

?>