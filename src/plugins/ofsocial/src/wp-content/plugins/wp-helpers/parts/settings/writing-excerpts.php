<?php
/*
Title: Excerpts
Setting: piklist_wp_helpers
Tab: Writing
Order: 210
Tab Order: 20
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'excerpt_wysiwyg'
    ,'label' => 'Enable WYSIWYG for excerpts'
    ,'choices' => array(
      'true' => 'Add TinyMCE toolbar to Excerpt box for Posts.'
    )
  ));

  piklist('field', array(
    'type' => 'number'
    ,'field' => 'excerpt_box_height'
    ,'label' => 'Height of excerpt box'
    ,'description' => 'px'
    ,'value' => ''
    ,'attributes' => array(
      'class' => 'small-text'
    )
    ,'conditions' => array(
      array(
        'field' => 'excerpt_wysiwyg'
        ,'value' => 'true'
        ,'compare' => '!='
      )
    )
  ));
