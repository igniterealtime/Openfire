<?php
/*
Title: Login Screen
Setting: piklist_wp_helpers
Order: 130
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'file'
    ,'field' => 'login_image'
    ,'label' => 'Image'
    ,'description' => 'Image should be no wider than 320px'
    ,'help' => 'Multiple images can be uploaded and will be displayed randomly.'
    ,'options' => array(
      'title' => 'Add Image(s)'
      ,'button' => 'Add Image(s)'
    )
  ));

  piklist('field', array(
    'type' => 'colorpicker'
    ,'field' => 'login_background'
    ,'label' => 'Background color'
    ,'attributes' => array(
      'class' => 'text'
    )
  ));