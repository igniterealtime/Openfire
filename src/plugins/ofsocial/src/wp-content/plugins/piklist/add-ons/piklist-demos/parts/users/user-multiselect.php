dv<?php
/*
Title: Multiselect Fields
Capability: manage_options
Order: 20
Collapse: false
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
  ));

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'multiselect_add_more'
    ,'label' => 'Multiselect Add More'
    ,'add_more' => true
    ,'description' => 'A grouped field. Data is not searchable, since it is saved in an array.'
    ,'field' => 'multiselect_add_more_field'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
    ,'attributes' => array(
      'multiple' => 'multiple'
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));
?>