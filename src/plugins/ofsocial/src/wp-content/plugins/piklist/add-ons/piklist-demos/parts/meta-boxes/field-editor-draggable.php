<?php
/*
Title: Draggable Editor
Post Type: piklist_demo,piklist_lite_demo
Order: 100
*/

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
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));
