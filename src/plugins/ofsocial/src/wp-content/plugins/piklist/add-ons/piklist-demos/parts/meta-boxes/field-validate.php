<?php
/*
Title: Validation Fields <span class="piklist-title-right">Order 10</span>
Post Type: piklist_demo
Order: 10
*/

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_required'
    ,'label' => 'Text Required'
    ,'description' => "required => true"
    ,'attributes' => array(
      'class' => 'large-text'
    )
    ,'required' => true
  ));

  piklist('field', array(
    'type'    => 'group'
    ,'field'   => 'group_required'
    ,'label'   => 'Group Required'
    ,'add_more'=> true
    ,'fields'  => array(
      array(
        'type' => 'text'
        ,'field' => 'name'
        ,'columns' => 8
        ,'attributes' => array(
          'placeholder' => 'Name'
        )
      )
      ,array(
        'type' => 'checkbox'
        ,'field' => 'hierarchical'
        ,'required' => true
        ,'columns' => 4
        ,'choices' => array(
          'true' => 'Hierarchical'
        )
      )
    )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'label' => 'File Name'
    ,'field' => 'file_name'
    ,'description' => 'Converts multiple words to a valid file name'
    ,'sanitize' => array(
      array(
        'type' => 'file_name'
      )
    )
    ,'attributes' => array(
      'class' => 'large-text'
    )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'validate_emaildomain'
    ,'label' => 'Email address'
    ,'description' => __('Validate Email and Email Domain')
    ,'attributes' => array(
      'class' => 'large-text'
    )
    ,'validate' => array(
      array(
        'type' => 'email'
      )
      ,array(
        'type' => 'email_domain'
      )
    )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'validate_file_exists'
    ,'label' => __('File exists?')
    ,'description' => 'Test with: http://wordpress.org/plugins/about/readme.txt'
    ,'attributes' => array(
      'class' => 'large-text'
    )
    ,'validate' => array(
      array(
        'type' => 'file_exists'
      )
    )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'validate_image'
    ,'label' => __('Image')
    ,'description' => 'Test with: http://piklist.com/wp-content/themes/piklistcom-base/images/piklist-logo@2x.png'
    ,'attributes' => array(
      'class' => 'large-text'
    )
    ,'validate' => array(
      array(
        'type' => 'image'
      )
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'checkbox'
    ,'label' => 'Checkbox'
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
    ,'validate' => array(
      array(
        'type' => 'limit'
        ,'options' => array(
          'min' => 2
          ,'max' => 2
        )
      )
    )
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));

  piklist('field', array(
    'type' => 'file'
    ,'field' => 'upload_media'
    ,'label' => __('Add File(s)','piklist-demo')
    ,'description' => 'No more than one file is allowed'
    ,'options' => array(
      'modal_title' => __('Add File(s)','piklist-demo')
      ,'button' => __('Add','piklist-demo')
    )
    ,'attributes' => array(
      'class' => 'large-text'
    )
    ,'validate' => array(
      array(
        'type' => 'limit'
        ,'options' => array(
          'min' => 0
          ,'max' => 1
        )
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'field' => 'address_group_add_more'
    ,'add_more' => true
    ,'label' => 'Grouped/Add-More with Limit'
    ,'description' => 'No more than two add-mores are allowed'
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'group_field_1'
        ,'label' => 'Field 1'
        ,'columns' => 12
      )
      ,array(
        'type' => 'text'
        ,'field' => 'group_field_2'
        ,'label' => 'Field 2'
        ,'columns' => 12
      )
    )
    ,'validate' => array(
      array(
        'type' => 'limit'
        ,'options' => array(
          'min' => 1
          ,'max' => 2
        )
      )
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