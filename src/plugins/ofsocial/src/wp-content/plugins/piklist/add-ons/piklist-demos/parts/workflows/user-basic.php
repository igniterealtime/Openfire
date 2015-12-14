<?php
/*
Title: Basic
Order: 30
Flow: User Test
*/

  piklist('include_user_profile_fields', array(
    'meta_boxes' => array(
      'Text Fields'
      ,'Taxonomies'
      ,'Upload Fields'
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Workflow Tab'
  ));

?>