<?php
/*
Title: Upload Fields
Capability: manage_options
Order: 10
*/

  // Any field with the scope set to the field name of the upload field will be treated as related
  // data to the upload. Below we see we are setting the post_status and post_title, where the
  // post_status is pulled dynamically on page load, hence the current status of the content is
  // applied. Have fun! ;)
  //
  // NOTE: If the post_status of an attachment is anything but inherit or private it will NOT be
  // shown on the Media page in the admin, but it is in the database and can be found using query_posts
  // or get_posts or get_post etc....

  piklist('field', array(
    'type' => 'file'
    ,'field' => 'upload_basic'
    ,'scope' => 'post_meta'
    ,'label' => __('Add File','piklist')
    ,'description' => __('This is the basic upload field.', 'piklist')
    ,'options' => array(
      'basic' => true
    )
  ));

  piklist('field', array(
    'type' => 'file'
    ,'field' => 'upload_media'
    ,'scope' => 'post_meta'
    ,'label' => __('Add File(s)','piklist')
    ,'description' => __('This is the uploader seen in the admin by default.', 'piklist')
    ,'options' => array(
      'modal_title' => __('Add File(s)', 'piklist')
      ,'button' => __('Add', 'piklist')
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

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));