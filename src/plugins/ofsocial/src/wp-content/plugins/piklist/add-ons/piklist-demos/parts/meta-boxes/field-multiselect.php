<?php
/*
Title: Multiselect Fields
Post Type: piklist_demo
Order: 20
Collapse: true
*/

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'multiselect'
    ,'label' => 'Multiselect'
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
    ,'attributes' => array(
      'multiple' => 'multiple'
    )
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));
  
  piklist('field', array(
    'type' => 'select'
    ,'field' => 'multiselect_add_more'
    ,'label' => 'Multiselect Add More'
    ,'value' => 'third'
    ,'add_more' => true
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
    ,'attributes' => array(
      'multiple' => 'multiple'
    )
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));
  
  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));
?>