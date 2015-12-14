<?php
/*
Title: Editor
Setting: piklist_demo_fields
Tab: Editor
Tab Order: 20
Order: 30
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

  piklist('field', array(
    'type' => 'editor'
    ,'field' => 'post_content_full'
    ,'scope' => 'post'
    ,'template' => 'field'
    ,'value' => 'You can remove the left label when displaying the editor by defining <code>\'template\'=>\'field\'</code> in the field parameters. This will make it look like the default WordPress editor. To learn about replacing the WordPress editor <a href="http://piklist.com/user-guide/tutorials/replacing-wordpress-post-editor/">read our Tutorial</a>.'
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
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));
  
?>