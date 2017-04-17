<?php
/*
Title: Other
Setting: piklist_wp_helpers
Tab: Writing
Order: 230
Tab Order: 20
Flow: WP Helpers Settings Flow
*/

global $wp_version;

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'screen_layout_columns_post'
    ,'label' => 'Columns on "Add New" screen'
    ,'value' => 'default'
    ,'attributes' => array(
      'class' => 'small-text'
    )
    ,'choices' => array(
      'default' => 'Default'
      ,'1' => '1'
      ,'2' => '2'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'disable_autosave'
    ,'label' => 'Disable Autosave'
    ,'description' => '"Preview mode" depends on Autosave. Disabling Autosave will also disable Preview.'
    ,'choices' => array(
      'true' => 'Stop WordPress from autosaving posts.'
    )
  ));
  
  piklist('field', array(
    'type' => 'number'
    ,'field' => 'edit_posts_per_page'
    ,'label' => 'Posts per page on edit screen.'
    ,'value' => ''
    ,'attributes' => array(
      'class' => 'small-text'
    )
  ));

if ($wp_version >= 3.5)
{

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'xml_rpc'
    ,'label' => 'XML-RPC'
    ,'description' => 'If this site requires Jetpack or the WordPress mobile app, leave XML-RPC On, or choose disable "some" functionality, and select both check boxes.'
    ,'choices' => array(
      'false' => 'XML RPC is on'
      ,'some' => 'Disable some XML RPC functionality'
      ,'true' => 'Fully disable XML RPC'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'xml_rpc_methods'
    ,'choices' => array(
      'system.multicall' => sprintf(__('system.multicall %1$s(Learn More)%3$s', 'wp_helpers'), '<a href="https://blog.cloudflare.com/a-look-at-the-new-wordpress-brute-force-amplification-attack/" target="_blank">', '<a href="http://piklist.com/user-guide/tutorials/replacing-wordpress-post-editor/">', '</a>')
      ,'pingback.ping' => sprintf(__('pingback.ping %1$s(Learn More)%3$s', 'wp_helpers'), '<a href="http://wptavern.com/how-to-prevent-wordpress-from-participating-in-pingback-denial-of-service-attacks" target="_blank">', '<a href="http://piklist.com/user-guide/tutorials/replacing-wordpress-post-editor/">', '</a>')
    )
    ,'conditions' => array(
      array(
        'field' => 'xml_rpc'
        ,'value' => 'some'
      )
    )
  ));
}







