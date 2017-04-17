<?php
/*
Post Type: piklist_demo
Order: 100
Lock: true
Meta box: false
*/

  piklist('field', array(
    'type' => 'editor'
    ,'field' => 'post_content'
    ,'scope' => 'post'
    ,'label' => 'Post Content'
    ,'description' => 'This is the standard WordPress Editor, placed in a Metabox, which is placed in a Piklist WorkFlow tab. By default, Piklist formats the editor like any other field with a label to the left.'
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
      ,'drag_drop_upload' => true
    )
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));
  
  piklist('field', array(
    'type' => 'editor'
    ,'field' => 'post_content_add_more'
    ,'label' => 'Post Content Add More'
    ,'add_more' => true
    ,'description' => 'This is the teeny editor used in an add-more repeater field.'
    ,'value' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'options' => array (
      'media_buttons' => true
      ,'teeny' => true
      ,'textarea_rows' => 5
      ,'drag_drop_upload' => true
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));
  
?>