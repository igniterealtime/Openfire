<?php 

  $name = piklist_form::get_field_name($field, $scope, false, $prefix);

  $_attributes = '';
  foreach ($attributes as $_key => $_value)
  {
    if (substr($_key, 0, strlen('data-piklist-field-')) == 'data-piklist-field-')
    {
      $_attributes .= '" ' . $_key . '="' . $_value;
    }
  }

  wp_editor(
    isset($value) && !empty($value) ? $value : ''
    ,isset($id) ? $id : (piklist::unique_id() . 'piklisteditor' . preg_replace('/[^a-z0-9]+/i', '', $name))
    ,array_merge(
      array(
        'textarea_name' => $name . $_attributes
        ,'editor_height' => 180
        ,'quicktags' => true
        ,'textarea_rows' => 5
      )
      ,isset($options) && is_array($options) ? $options : array()
    )
  );

?>