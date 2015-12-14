<?php
/*
Width: 720
*/

piklist('field', array(
    'type' => 'group'
    ,'field' => 'address_group'
    ,'label' => 'Address (Grouped)'
    ,'list' => false
    ,'description' => 'A grouped field. Data is not searchable, since it is saved in an array.'
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'address_1'
        ,'columns' => 12
        ,'attributes' => array(
          'placeholder' => 'Street Address'
        )
      )
      ,array(
        'type' => 'text'
        ,'field' => 'address_2'
        ,'columns' => 12
        ,'attributes' => array(
          'placeholder' => 'PO Box, Suite, etc.'
        )
      )
      ,array(
        'type' => 'text'
        ,'field' => 'city'
        ,'columns' => 5
        ,'attributes' => array(
          'placeholder' => 'City'
        )
      )
      ,array(
        'type' => 'select'
        ,'field' => 'state'
        ,'columns' => 4
        ,'choices' => array(
          'AL' => 'Alabama'
          ,'AK' => 'Alaska'
          ,'AZ' => 'Arizona'
          ,'AR' => 'Arkansas'
          ,'CA' => 'California'
          ,'CO' => 'Colorado'
          ,'CT' => 'Connecticut'
          ,'DE' => 'Delaware'
          ,'DC' => 'District Of Columbia'
          ,'FL' => 'Florida'
          ,'GA' => 'Georgia'
          ,'HI' => 'Hawaii'
          ,'ID' => 'Idaho'
          ,'IL' => 'Illinois'
          ,'IN' => 'Indiana'
          ,'IA' => 'Iowa'
          ,'KS' => 'Kansas'
          ,'KY' => 'Kentucky'
          ,'LA' => 'Louisiana'
          ,'ME' => 'Maine'
          ,'MD' => 'Maryland'
          ,'MA' => 'Massachusetts'
          ,'MI' => 'Michigan'
          ,'MN' => 'Minnesota'
          ,'MS' => 'Mississippi'
          ,'MO' => 'Missouri'
          ,'MT' => 'Montana'
          ,'NE' => 'Nebraska'
          ,'NV' => 'Nevada'
          ,'NH' => 'New Hampshire'
          ,'NJ' => 'New Jersey'
          ,'NM' => 'New Mexico'
          ,'NY' => 'New York'
          ,'NC' => 'North Carolina'
          ,'ND' => 'North Dakota'
          ,'OH' => 'Ohio'
          ,'OK' => 'Oklahoma'
          ,'OR' => 'Oregon'
          ,'PA' => 'Pennsylvania'
          ,'RI' => 'Rhode Island'
          ,'SC' => 'South Carolina'
          ,'SD' => 'South Dakota'
          ,'TN' => 'Tennessee'
          ,'TX' => 'Texas'
          ,'UT' => 'Utah'
          ,'VT' => 'Vermont'
          ,'VA' => 'Virginia'
          ,'WA' => 'Washington'
          ,'WV' => 'West Virginia'
          ,'WI' => 'Wisconsin'
          ,'WY' => 'Wyoming'
        )
      )
      ,array(
        'type' => 'text'
        ,'field' => 'postal_code'
        ,'columns' => 3
        ,'attributes' => array(
          'placeholder' => 'Postal Code'
        )
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'field' => 'address_group_add_more'
    ,'add_more' => true
    ,'label' => 'Address (Grouped/Add-More)'
    ,'description' => 'A grouped field using Add-More. No fields labels.'
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'address_1'
        ,'label' => 'Street Address'
        ,'columns' => 12
      )
      ,array(
        'type' => 'text'
        ,'field' => 'address_2'
        ,'label' => 'PO Box, Suite, etc.'
        ,'columns' => 12
      )
      ,array(
        'type' => 'text'
        ,'field' => 'city'
        ,'label' => 'City'
        ,'columns' => 5
      )
      ,array(
        'type' => 'select'
        ,'field' => 'state'
        ,'label' => 'State'
        ,'columns' => 4
        ,'choices' => array(
          'AL' => 'Alabama'
          ,'AK' => 'Alaska'
          ,'AZ' => 'Arizona'
          ,'AR' => 'Arkansas'
          ,'CA' => 'California'
          ,'CO' => 'Colorado'
          ,'CT' => 'Connecticut'
          ,'DE' => 'Delaware'
          ,'DC' => 'District Of Columbia'
          ,'FL' => 'Florida'
          ,'GA' => 'Georgia'
          ,'HI' => 'Hawaii'
          ,'ID' => 'Idaho'
          ,'IL' => 'Illinois'
          ,'IN' => 'Indiana'
          ,'IA' => 'Iowa'
          ,'KS' => 'Kansas'
          ,'KY' => 'Kentucky'
          ,'LA' => 'Louisiana'
          ,'ME' => 'Maine'
          ,'MD' => 'Maryland'
          ,'MA' => 'Massachusetts'
          ,'MI' => 'Michigan'
          ,'MN' => 'Minnesota'
          ,'MS' => 'Mississippi'
          ,'MO' => 'Missouri'
          ,'MT' => 'Montana'
          ,'NE' => 'Nebraska'
          ,'NV' => 'Nevada'
          ,'NH' => 'New Hampshire'
          ,'NJ' => 'New Jersey'
          ,'NM' => 'New Mexico'
          ,'NY' => 'New York'
          ,'NC' => 'North Carolina'
          ,'ND' => 'North Dakota'
          ,'OH' => 'Ohio'
          ,'OK' => 'Oklahoma'
          ,'OR' => 'Oregon'
          ,'PA' => 'Pennsylvania'
          ,'RI' => 'Rhode Island'
          ,'SC' => 'South Carolina'
          ,'SD' => 'South Dakota'
          ,'TN' => 'Tennessee'
          ,'TX' => 'Texas'
          ,'UT' => 'Utah'
          ,'VT' => 'Vermont'
          ,'VA' => 'Virginia'
          ,'WA' => 'Washington'
          ,'WV' => 'West Virginia'
          ,'WI' => 'Wisconsin'
          ,'WY' => 'Wyoming'
        )
      )
      ,array(
        'type' => 'text'
        ,'field' => 'postal_code'
        ,'label' => 'Postal Code'
        ,'columns' => 3
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'label' => 'Address (Un-Grouped)'
    ,'description' => 'An Un-grouped field. Data is saved as individual meta and is searchable.'
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'ungrouped_address_1'
        ,'label' => 'Street Address'
        ,'columns' => 12
      )
      ,array(
        'type' => 'text'
        ,'field' => 'ungrouped_address_2'
        ,'label' => 'PO Box, Suite, etc.'
        ,'columns' => 12
      )
      ,array(
        'type' => 'text'
        ,'field' => 'ungrouped_city'
        ,'label' => 'City'
        ,'columns' => 5
      )
      ,array(
        'type' => 'select'
        ,'field' => 'ungrouped_state'
        ,'label' => 'State'
        ,'columns' => 4
        ,'choices' => array(
          'AL' => 'Alabama'
          ,'AK' => 'Alaska'
          ,'AZ' => 'Arizona'
          ,'AR' => 'Arkansas'
          ,'CA' => 'California'
          ,'CO' => 'Colorado'
          ,'CT' => 'Connecticut'
          ,'DE' => 'Delaware'
          ,'DC' => 'District Of Columbia'
          ,'FL' => 'Florida'
          ,'GA' => 'Georgia'
          ,'HI' => 'Hawaii'
          ,'ID' => 'Idaho'
          ,'IL' => 'Illinois'
          ,'IN' => 'Indiana'
          ,'IA' => 'Iowa'
          ,'KS' => 'Kansas'
          ,'KY' => 'Kentucky'
          ,'LA' => 'Louisiana'
          ,'ME' => 'Maine'
          ,'MD' => 'Maryland'
          ,'MA' => 'Massachusetts'
          ,'MI' => 'Michigan'
          ,'MN' => 'Minnesota'
          ,'MS' => 'Mississippi'
          ,'MO' => 'Missouri'
          ,'MT' => 'Montana'
          ,'NE' => 'Nebraska'
          ,'NV' => 'Nevada'
          ,'NH' => 'New Hampshire'
          ,'NJ' => 'New Jersey'
          ,'NM' => 'New Mexico'
          ,'NY' => 'New York'
          ,'NC' => 'North Carolina'
          ,'ND' => 'North Dakota'
          ,'OH' => 'Ohio'
          ,'OK' => 'Oklahoma'
          ,'OR' => 'Oregon'
          ,'PA' => 'Pennsylvania'
          ,'RI' => 'Rhode Island'
          ,'SC' => 'South Carolina'
          ,'SD' => 'South Dakota'
          ,'TN' => 'Tennessee'
          ,'TX' => 'Texas'
          ,'UT' => 'Utah'
          ,'VT' => 'Vermont'
          ,'VA' => 'Virginia'
          ,'WA' => 'Washington'
          ,'WV' => 'West Virginia'
          ,'WI' => 'Wisconsin'
          ,'WY' => 'Wyoming'
        )
      )
      ,array(
        'type' => 'text'
        ,'field' => 'ungrouped_postal_code'
        ,'label' => 'Postal Code'
        ,'columns' => 3
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'label' => 'Address (Un-Grouped/Add-More)'
    ,'add_more' => true
    ,'description' => 'An Un-grouped field. Data is saved as individual meta and is searchable.'
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'ungrouped_address_1_addmore'
        ,'label' => 'Street Address'
        ,'columns' => 12
      )
      ,array(
        'type' => 'text'
        ,'field' => 'ungrouped_address_2_addmore'
        ,'label' => 'PO Box, Suite, etc.'
        ,'columns' => 12
      )
      ,array(
        'type' => 'text'
        ,'field' => 'ungrouped_city_addmore'
        ,'label' => 'City'
        ,'columns' => 5
      )
      ,array(
        'type' => 'select'
        ,'field' => 'ungrouped_state_addmore'
        ,'label' => 'State'
        ,'columns' => 4
        ,'choices' => array(
          'AL' => 'Alabama'
          ,'AK' => 'Alaska'
          ,'AZ' => 'Arizona'
          ,'AR' => 'Arkansas'
          ,'CA' => 'California'
          ,'CO' => 'Colorado'
          ,'CT' => 'Connecticut'
          ,'DE' => 'Delaware'
          ,'DC' => 'District Of Columbia'
          ,'FL' => 'Florida'
          ,'GA' => 'Georgia'
          ,'HI' => 'Hawaii'
          ,'ID' => 'Idaho'
          ,'IL' => 'Illinois'
          ,'IN' => 'Indiana'
          ,'IA' => 'Iowa'
          ,'KS' => 'Kansas'
          ,'KY' => 'Kentucky'
          ,'LA' => 'Louisiana'
          ,'ME' => 'Maine'
          ,'MD' => 'Maryland'
          ,'MA' => 'Massachusetts'
          ,'MI' => 'Michigan'
          ,'MN' => 'Minnesota'
          ,'MS' => 'Mississippi'
          ,'MO' => 'Missouri'
          ,'MT' => 'Montana'
          ,'NE' => 'Nebraska'
          ,'NV' => 'Nevada'
          ,'NH' => 'New Hampshire'
          ,'NJ' => 'New Jersey'
          ,'NM' => 'New Mexico'
          ,'NY' => 'New York'
          ,'NC' => 'North Carolina'
          ,'ND' => 'North Dakota'
          ,'OH' => 'Ohio'
          ,'OK' => 'Oklahoma'
          ,'OR' => 'Oregon'
          ,'PA' => 'Pennsylvania'
          ,'RI' => 'Rhode Island'
          ,'SC' => 'South Carolina'
          ,'SD' => 'South Dakota'
          ,'TN' => 'Tennessee'
          ,'TX' => 'Texas'
          ,'UT' => 'Utah'
          ,'VT' => 'Vermont'
          ,'VA' => 'Virginia'
          ,'WA' => 'Washington'
          ,'WV' => 'West Virginia'
          ,'WI' => 'Wisconsin'
          ,'WY' => 'Wyoming'
        )
      )
      ,array(
        'type' => 'text'
        ,'field' => 'ungrouped_postal_code_addmore'
        ,'label' => 'Postal Code'
        ,'columns' => 3
      )
    )

   ));

   piklist('field', array(
    'type' => 'group'
    ,'field' => 'editor_test_one'
    ,'label' => 'Editor test 1 with Addmore'
    ,'add_more' => true
    ,'description' => 'A grouped/addmore field test with Editor.'
    ,'fields' => array(
      array(
        'type' => 'checkbox'
        ,'field' => 'editor_test_one_checkbox'
        ,'label' => 'Checkbox'
        ,'columns' => 12
        ,'choices' => array(
          'first' => 'First Choice'
          ,'second' => 'Second Choice'
          ,'third' => 'Third Choice'
        )
      )
      ,array(
        'type' => 'editor'
        ,'field' => 'editor_test_one_editor'
        ,'columns' => 12
        ,'label' => 'Post Content'
        ,'value' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
        ,'options' => array (
          'wpautop' => true
          ,'media_buttons' => true
          ,'tabindex' => ''
          ,'editor_css' => ''
          ,'editor_class' => ''
          ,'teeny' => false
          ,'dfw' => false
          ,'tinymce' => true
          ,'quicktags' => true
        )
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'field' => 'editor_test_two'
    ,'label' => 'Editor test 2 with Addmore'
    ,'add_more' => true
    ,'description' => 'A grouped/addmore field test with Editor.'
    ,'fields' => array(
      array(
        'type' => 'editor'
        ,'field' => 'editor_test_two_editor'
        ,'columns' => 12
        ,'label' => 'Post Content'
        ,'description' => 'This is the standard post box, now placed in a Piklist WorkFlow.'
        ,'value' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
        ,'options' => array (
          'wpautop' => true
          ,'media_buttons' => true
          ,'tabindex' => ''
          ,'editor_css' => ''
          ,'editor_class' => ''
          ,'teeny' => false
          ,'dfw' => false
          ,'tinymce' => true
          ,'quicktags' => true
        )
      )
      ,array(
        'type' => 'checkbox'
        ,'field' => 'editor_test_two_checkbox'
        ,'label' => 'Checkbox'
        ,'columns' => 12
        ,'choices' => array(
          'first' => 'First Choice'
          ,'second' => 'Second Choice'
          ,'third' => 'Third Choice'
        )
      )
    )
  ));

?>