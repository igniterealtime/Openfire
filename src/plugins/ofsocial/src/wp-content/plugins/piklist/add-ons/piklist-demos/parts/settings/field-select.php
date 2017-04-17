<?php
/*
Title: Select Fields
Setting: piklist_demo_fields
Tab: Lists
Order: 30
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
    ,'add_more' => true
    ,'label' => 'Add More'
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
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