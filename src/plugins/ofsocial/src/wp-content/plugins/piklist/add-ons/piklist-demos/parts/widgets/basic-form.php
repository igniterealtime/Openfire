<?php
/*
Width: 720
*/


piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_class_small'
    ,'label' => 'Text'
    ,'value' => 'Lorem'
    ,'help' => 'You can easily add tooltips to your fields with the help parameter.'
    ,'attributes' => array(
      'class' => 'regular-text'
    )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_columns_element'
    ,'label' => 'Columns Element'
    ,'description' => 'columns="6"'
    ,'value' => 'Lorem'
    ,'columns' => 6
 
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_add_more'
    ,'add_more' => true
    ,'label' => 'Text Add More'
    ,'description' => 'add_more="true"'
    ,'value' => 'Lorem'
 
  ));

  piklist('field', array(
    'type' => 'number'
    ,'field' => 'number'
    ,'label' => 'Number'
    ,'description' => 'ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'value' => 5
    ,'attributes' => array(
      'class' => 'small-text'
      ,'step' => 1
      ,'min' => 0
      ,'max' => 10
    )
 
  ));

  piklist('field', array(
    'type' => 'textarea'
    ,'field' => 'demo_textarea_large'
    ,'label' => 'Large Code'
    ,'description' => 'class="large-text code" rows="10" columns="50"'
    ,'value' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'attributes' => array(
      'rows' => 10
      ,'cols' => 50
      ,'class' => 'large-text code'
    )
 
  ));

  piklist('field', array(
    'type' => 'file'
    ,'field' => 'upload_media'
    ,'label' => __('Add File(s)','piklist-demo')
    ,'description' => __('This is the uploader seen in the admin by default.', 'piklist-demo')
    ,'options' => array(
      'modal_title' => __('Add File(s)', 'piklist-demo')
      ,'button' => __('Add', 'piklist-demo')
    )
    ,'validate' => array(
      array(
        'type' => 'limit'
        ,'options' => array(
          'min' => 0
          ,'max' => 2
        )
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'field' => 'slides'
    ,'add_more' => true
    ,'label' => 'Slide Images'
    ,'description' => 'Add the slides for the slideshow.  You can add as many slides as you want, and they can be drag-and-dropped into the order that you would like them to appear.'
    ,'fields'  => array(
      array(
        'type' => 'file'
        ,'field' => 'image'
        ,'label' => __('Slides', 'plugin')
        ,'columns' => 12
      )
      ,array(
        'type' => 'text'
        ,'field' => 'url'
        ,'label' => 'URL'
        ,'columns' => 12
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'field' => 'slides_basic'
    ,'add_more' => true
    ,'label' => 'Slide Images'
    ,'description' => 'Add the slides for the slideshow.  You can add as many slides as you want, and they can be drag-and-dropped into the order that you would like them to appear.'
    ,'fields'  => array(
      array(
        'type' => 'file'
        ,'field' => 'image'
        ,'label' => __('Slides', 'plugin')
        ,'columns' => 12
        ,'options' => array(
          'basic' => true
        )
      )
      ,array(
        'type' => 'text'
        ,'field' => 'url'
        ,'label' => 'URL'
        ,'columns' => 12
      )
    )
  ));

