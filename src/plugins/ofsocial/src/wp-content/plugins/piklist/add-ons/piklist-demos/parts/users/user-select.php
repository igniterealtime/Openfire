<?php
/*
Title: Select Fields
Capability: manage_options
Order: 30
Collapse: false
*/

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'select'
    ,'label' => 'Select'
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
  ));

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'select_add_more'
    ,'label' => 'Select Add More'
    ,'add_more' => true
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));
?>