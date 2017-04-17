<?php
/*
Title: Editor
Capability: manage_options
Order: 1
Collapse: false
*/


  piklist('field', array(
    'type' => 'editor'
    ,'field' => 'editor_user'
    ,'label' => 'Post Content'
    ,'description' => 'This is the standard post box, now placed in a Piklist WorkFlow.'
    ,'value' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'options' => array (
      'wpautop' => true
      ,'media_buttons' => true
      ,'tabindex' => ''
      ,'editor_css' => ''
      ,'editor_class' => ''
      ,'teeny' => false
      ,'dfw' => false
      ,'tinymce' => true
      ,'quicktags' => true
    )
  ));

  piklist('field', array(
    'type' => 'editor'
    ,'field' => 'editor_user_add_more'
    ,'label' => 'Post Content Add More'
    ,'add_more' => true
    ,'description' => 'This is the teeny editor with an add more.'
    ,'value' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'options' => array (
      'media_buttons' => true
      ,'teeny' => true
      ,'textarea_rows' => 5
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Setting'
  ));

?>