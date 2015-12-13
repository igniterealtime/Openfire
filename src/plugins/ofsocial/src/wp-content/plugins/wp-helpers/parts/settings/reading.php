<?php
/*
Title: Reading
Setting: piklist_wp_helpers
Tab: Reading
Order: 300
Tab Order: 30
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'radio'
    ,'field' => 'excerpt_length_type'
    ,'label' => 'Set excerpt length'
    ,'value' =>  'words'
    ,'list' => true
    ,'choices' => array(
      'words' => 'Words'
      ,'characters' => 'Characters'
    )
  ));

  piklist('field', array(
    'type' => 'number'
    ,'field' => 'excerpt_length'
    ,'description' => 'Set the count, either words or characters for the excerpt'
    ,'value' => ''
    ,'attributes' => array(
      'class' => 'small-text'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'private_title_format'
    ,'label' => 'Remove "Private"'
    ,'choices' => array(
      'true' => 'Remove the "Private:" title prefix from private posts'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'protected_title_format'
    ,'label' => 'Remove "Protected"'
    ,'choices' => array(
      'true' => 'Remove the "Protected:" title prefix from the protected posts'
    )
  ));