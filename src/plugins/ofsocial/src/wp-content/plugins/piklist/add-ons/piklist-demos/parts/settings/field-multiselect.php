<?php
/*
Title: Multiselect Fields
Setting: piklist_demo_fields
Tab: Lists
Order: 30
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
      'multiple'
    )
  ));
  
  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));
?>