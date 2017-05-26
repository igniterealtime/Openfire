<?php
/*
Title: Field Groups
Capability: manage_options
Order: 100
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

   

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));

?>