<?php
/*
Title: Visitors
Setting: piklist_wp_helpers
Tab: Users
Order: 610
Flow: WP Helpers Settings Flow
*/

?>

  <p>Interact with your visitors</p>

<?php

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'maintenance_mode'
    ,'label' => 'Maintenance Mode'
    ,'value' => 'false'
    ,'description' => 'Place website in maintenance mode and show message'
    ,'choices' => array(
      'true' => 'Enabled'
      ,'false' => 'Disabled'
    )
  ));


  piklist('field', array(
      'type' => 'textarea'
      ,'field' => 'maintenance_mode_message'
      ,'reset' => 'false'
      ,'columns' => 12
      ,'options' => array(
        'wpautop' => true
        ,'media_buttons' => false
        ,'tabindex' => ''
        ,'editor_css' => ''
        ,'editor_class' => ''
        ,'teeny' => true
        ,'dfw' => false
        ,'tinymce' => true
        ,'quicktags' => true
        ,'drag_drop_upload' => false
      )
      ,'attributes' => array(
        'placeholder' => __('This message will display when the site is in Maintenance Mode.', 'piklist-toolbox')
      )
    ,'conditions' => array(
      array(
        'field' => 'maintenance_mode'
        ,'value' => 'true'
      )
    )
  ));


  piklist('field', array(
    'type' => 'checkbox'
    ,'label' => 'Private Website'
    ,'field' => 'private_site'
    ,'choices' => array(
      'true' => 'Only logged in users can see Website.'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'label' => 'Redirect Home'
    ,'field' => 'redirect_to_home'
    ,'choices' => array(
      'true' => 'Redirect users to Home Page after login.'
    )
  ));
  

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'notice_admin'
    ,'label' => 'In Admin Message'
    ,'value' => 'false'
    ,'description' => 'Displays in WordPress admin.'
    ,'choices' => array(
      'true' => 'Enabled'
      ,'false' => 'Disabled'
    )
  ));


  piklist('field', array(
      'type' => 'textarea'
      ,'field' => 'admin_message'
      ,'reset' => 'false'
      ,'columns' => 12
      ,'options' => array(
        'wpautop' => true
        ,'media_buttons' => false
        ,'tabindex' => ''
        ,'editor_css' => ''
        ,'editor_class' => ''
        ,'teeny' => true
        ,'dfw' => false
        ,'tinymce' => true
        ,'quicktags' => true
        ,'drag_drop_upload' => false
      )
      ,'attributes' => array(
        'placeholder' => __('This message will display in the admin of your website.', 'piklist-toolbox')
      )
    ,'conditions' => array(
      array(
        'field' => 'notice_admin'
        ,'value' => 'true'
      )
    )
  ));

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'notice_front'
    ,'value' => 'false'
    ,'label' => 'Frontend Message'
    ,'description' => 'Displays on front of website.'
    ,'choices' => array(
      'true' => 'Enabled'
      ,'false' => 'Disabled'
    )
  ));


  piklist('field', array(
    'type' => 'group'
    ,'reset' => 'false'
    ,'conditions' => array(
      array(
        'field' => 'notice_front'
        ,'value' => 'true'
      )
    )
    ,'fields' => array(
      array(
        'type' => 'textarea'
        ,'field' => 'logged_in_front_message'
        ,'reset' => 'false'
        ,'columns' => 12
        ,'options' => array(
          'wpautop' => true
          ,'media_buttons' => false
          ,'tabindex' => ''
          ,'editor_css' => ''
          ,'editor_class' => ''
          ,'teeny' => true
          ,'dfw' => false
          ,'tinymce' => true
          ,'quicktags' => true
          ,'drag_drop_upload' => false
        )
        ,'attributes' => array(
          'placeholder' => __('This message will display on the front of your website.', 'piklist-toolbox')
        )
      )
      ,array(
        'type' => 'select'
        //,'label' => __('Show to', 'piklist-toolbox')
        ,'field' => 'notice_user_type'
        ,'reset' => 'false'
        ,'columns' => 4
        ,'choices' => array(
          'all' => 'All Users'
          ,'logged_in' => 'Logged in Users'
        )
      )
      ,array(
        'type' => 'select'
        ,'field' => 'notice_browser_type'
        ,'reset' => 'false'
        //,'label' => __('Using this browser', 'piklist-toolbox')
        ,'columns' => 4
        ,'choices' => array(
          'all' => 'All Browsers'
          ,'is_chrome' => 'Chrome'
          ,'is_gecko' => 'Gecko'
          , 'is_IE' => 'IE'
          , 'is_macIE' => 'IE: MAC'
          , 'is_winIE' => 'IE: Windows'
          ,'is_lynx' => 'Lynx'
          , 'is_opera' => 'Opera'
          , 'is_NS4' => 'NS4'
          , 'is_safari' => 'Safari'
          , 'is_iphone' => 'Safari: mobile'
        )
      )
      ,array(
        'type' => 'select'
        //,'label' => __('Message background color', 'piklist-toolbox')
        ,'field' => 'notice_color'
        ,'reset' => 'false'
        ,'columns' => 4
        ,'choices' => array(
          'danger' => 'Red'
          ,'success' => 'Green'
          ,'info' => 'Blue'
        )
      )
    )
  ));