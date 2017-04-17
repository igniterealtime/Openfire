<?php
/*
Title: Advanced
Order: 60
Flow: User Test
*/

  piklist('include_user_profile_fields', array(
    'meta_boxes' => array(
      'ColorPicker Fields'
      ,'DatePicker Fields'
      ,'Add More Fields'
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Workflow Tab'
  ));

?>