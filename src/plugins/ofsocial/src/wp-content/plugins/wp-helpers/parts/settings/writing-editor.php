<?php
/*
Title: Editor
Setting: piklist_wp_helpers
Tab: Writing
Order: 200
Tab Order: 20
Flow: WP Helpers Settings Flow
*/

global $wp_version;

  $options = get_option('piklist_wp_helpers');

  $emoji = get_option('use_smilies') ? convert_smilies(':-(') : ':-(';

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'disable_emojis'
    ,'label' => 'Disable Emojis'
    ,'choices' => array(
      'true' => 'Disable all Emoji support.' . '&nbsp;' . $emoji
    )
  ));

    // Uncheck the option to use smilies on Settings > Writing
    if(!empty($options['disable_emojis']) && $options['disable_emojis'] == true)
    {
      update_option('use_smilies', null);
    }


  piklist('field', array(
    'type' => 'select'
    ,'field' => 'disable_visual_editor'
    ,'label' => 'Show Editor'
    ,'value' => 'false'
    ,'choices' => array(
      'true' => (($wp_version >= 3.5) ? 'Only show Text editor' : 'Only show HTML editor')
      ,'false' => (($wp_version >= 3.5) ? 'Show Visual and Text editors' : 'Show Visual and HTML editors')
    )
  ));

  piklist('field', array(
    'type' => 'radio'
    ,'field' => 'default_editor'
    ,'label' => 'Set Default Editor'
    ,'value' =>  'tinymce'
    ,'list' => true
    ,'choices' => array(
      'tinymce' => 'Visual'
      ,'html' => 'HTML'
    )
    ,'conditions' => array(
      array(
        'field' => 'disable_visual_editor'
        ,'value' => 'false'
      )
    )
  ));