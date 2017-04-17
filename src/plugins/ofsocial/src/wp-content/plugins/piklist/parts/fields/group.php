<?php 
  
  $value_count = null;
  
  if (!empty($value) && is_array($value))
  {
    $cardinalities = array();
  
    foreach ($value as $nested_value)
    {
      $cardinalities[] = count($nested_value);
    }
  
    $value_count = max($cardinalities);
  }

  $columns_to_render = array();
  
  foreach ($fields as $column)
  {
    $index = $index ? $index : 0;
    $_values = null;
    
    $column['prefix'] = $prefix;
    
    if (isset($column['columns']) || !isset($column['label']))
    {
      $column['template'] = isset($column['template']) ? $column['template'] : 'field';
      $column['child_field'] = true;
    }
    
    $column['attributes']['wrapper_class'] = (isset($column['attributes']['wrapper_class']) ? $column['attributes']['wrapper_class'] : null) . ($template != 'field' ? ' piklist-field-part' : null);
    
    if (in_array('piklist-error', $attributes['class']))
    {
      if (!isset($column['attributes']['class']))
      {
        $column['attributes']['class'] = array();
      }
      
      array_push($column['attributes']['class'], 'piklist-error');
    }
    
    if (in_array($column['type'], piklist_form::$field_list_types['multiple_fields']) || (isset($column['attributes']) && in_array('multiple', $column['attributes'])))
    {
      $column['multiple'] = true;
    }
    
    if ($column['type'] == 'html' && !isset($column['field']))
    {
      $column['field'] = piklist::unique_id();
    }
    
    if (!isset($column['scope']) || is_null($column['scope']))
    {
      if (strrpos($column['field'], ':') > 0)
      {
        $field_name = substr($column['field'], strrpos($column['field'], ':') + 1);
      }
      else
      {
        $field_name = $column['field'];
      }

      $column['field'] = (isset($field) ? '' : $field . ':' . ($index ? $index : '0') . ':') . $column['field'];
      $column['scope'] = $scope;
    }
    
    if (piklist_validate::errors()) 
    {
      $_values = piklist_validate::get_request_value($column['field'], $column['scope']);
    }
    
    if (!$_values)
    {
      if (isset($column['save_as']) && is_array($value) && isset($value[$column['save_as']]))
      {
        $_values = $value[$column['save_as']];
      }
      elseif (is_array($value) && isset($value[$field_name]))
      {
        $_values = $value[$field_name];
      }
    }
    
    if (!$_values)
    {
      if (piklist_form::is_widget())
      {
        $_values = isset(piklist_widget::widget()->instance[$column['field']]) ? maybe_unserialize(piklist_widget::widget()->instance[$column['field']]) : (isset($column['value']) ? $column['value'] : null);
      }
      else
      {
        $_values = piklist_form::get_field_value($column['scope'], $column, $column['scope'], piklist_form::get_field_object_id($column));
      }
    }

    if (!$_values && $value && is_array($value) && is_numeric(key($value)))
    {
      $_values = $value;
    }
    
    if (!is_array($_values))
    {
      $_values = array($_values);
    }    
    
    if (isset($column['multiple']) && $column['multiple'])
    {
      foreach ($_values as $_index => $_value)
      {
        $column['index'] = $_index;
        $column['value'] = $_value;
        $column['group_field'] = true;

        if (!isset($columns_to_render[$_index]))
        {
          $columns_to_render[$_index] = array();
        }
      
        if (!empty($field) && !stristr($column['field'], ':'))
        {
          $column['field'] = $field . (substr_count($field, ':') == 1 ? ':' . $column['index'] . ':' : ':') . $column['field'];
        }
      
        array_push($columns_to_render[$_index], $column);
      }
    }
    else
    {
      for ($i = 0; $i < $value_count; $i++)
      {
        if (!isset($_values[$i]))
        {
          $_values[$i] = null;
        }
      }
      
      $first_value = null;
      foreach ($_values as $_index => $_value)
      {
        if (!isset($columns_to_render[$_index]))
        {
          $columns_to_render[$_index] = array();
        }

        if ($column['type'] == 'html')
        {
          if ($_index == 0)
          {
            $first_value = $_value;
          }
          else
          {
            $column['value'] = $first_value;
          }
        }
        else
        {
          $column['value'] = $_value;
        }

        $column['index'] = $_index;
        $column['group_field'] = true;
    
        if (!empty($field) && !stristr($column['field'], ':'))
        {
          $column['field'] = $field . (substr_count($field, ':') == 1 ? ':' . $column['index'] . ':' : ':') . $column['field'];
        }
        
        array_push($columns_to_render[$_index], $column);
      }
    }
  }
  
  foreach ($columns_to_render as $_index => $_columns)
  {
    if (isset($columns_to_render[0]))
    {
      for ($i = 0; $i < count($columns_to_render[0]); $i++)
      {
        if (!isset($columns_to_render[$_index][$i]))
        {
          $columns_to_render[$_index][$i] = $columns_to_render[0][$i];
          $columns_to_render[$_index][$i]['value'] = null;
        }
      }
    }
  }
  
  foreach ($columns_to_render as $column_to_render)
  {
    $group_index = piklist::unique_id();
    $group_add_more = false;
    
    foreach ($column_to_render as $column)
    {
      $column['attributes']['data-piklist-field-group'] = $group_index; 

      if ($column['type'] == 'group')
      {
        foreach ($column['fields'] as &$_field)
        {
          $_field['attributes']['data-piklist-field-sub-group'] = $group_index; 
        }
      }

      if ($column['type'] != 'group' && !$group_add_more && isset($attributes['data-piklist-field-addmore']))
      {
        $column['attributes']['data-piklist-field-addmore'] = $attributes['data-piklist-field-addmore'];
        $group_add_more = true;
        
        if (isset($attributes['data-piklist-field-addmore-actions']))
        {
          $column['attributes']['data-piklist-field-addmore-actions'] = $attributes['data-piklist-field-addmore-actions'];
        }
      }
      
      if (isset($column['add_more']) && $column['add_more'])
      {
        $column['attributes']['data-piklist-field-addmore-single'] = $attributes['data-piklist-field-addmore'];
      }
      
      if ($column['type'] == 'group')
      {
        foreach ($column['fields'] as &$_field)
        {
          $_field['field'] = $column['field'] . ':' . $column['index'] . ':' . $_field['field']; 
        }
      }
      
      if (!empty($conditions))
      {
        if (isset($column['conditions']) && is_array($column['conditions']))
        {
          $column['conditions'] = array_merge($column['conditions'], $conditions);
          
          if (!isset($column['attributes']['class']))
          {
            $column['attributes']['class'] = array();
          }
          elseif (isset($column['attributes']['class']) && !is_array($column['attributes']['class']))
          {
            $column['attributes']['class'] = array($column['attributes']['class']);
          }
          
          array_push($column['attributes']['class'], 'piklist-field-condition');
        }
        else
        {
          $column['conditions'] = $conditions;
        }
      }

      piklist_form::render_field($column);
    }
  }
