<?php
/*
Width: 720
*/

  piklist('field', array(
    'type' => 'editor'
    ,'field' => 'post_content'
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
    ,'field' => 'post_content'
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

  piklist('field', array(
    'type' => 'editor'
    ,'field' => 'post_content_draggable'
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