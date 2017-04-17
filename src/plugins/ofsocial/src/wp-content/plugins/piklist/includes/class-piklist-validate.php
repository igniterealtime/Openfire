<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Validate
{
  private static $errors = array();
  
  private static $request = array();

  private static $submission = array();
  
  private static $fields = array();

  private static $id = false;
  
  private static $parameter = 'piklist_validate';

  private static $validation_rules = array();

  private static $sanitization_rules = array();
    
  public static function _construct()
  {
    add_action('init', array('piklist_validate', 'init'));
    add_action('admin_head', array('piklist_validate', 'admin_head'));
    add_action('admin_notices', array('piklist_validate', 'admin_notices'));
    add_action('piklist_widget_notices', array('piklist_validate', 'admin_notices'));

    add_filter('wp_redirect', array('piklist_validate', 'wp_redirect'), 10, 2);
    add_filter('piklist_validation_rules', array('piklist_validate', 'validation_rules'));
    add_filter('piklist_sanitization_rules', array('piklist_validate', 'sanitization_rules'));
  }
  
  public static function init()
  {
    self::$validation_rules = apply_filters('piklist_validation_rules', self::$validation_rules);
    self::$sanitization_rules = apply_filters('piklist_sanitization_rules', self::$sanitization_rules);
    
    self::get_data();
  }
  
  public static function get($variable)
  {
    return isset(self::$$variable) ? self::$$variable : false;
  }

  public static function wp_redirect($location, $status)
  {
    global $pagenow;
    
    if (self::$id && $status == 302)
    {
      if ($pagenow == 'edit-tags.php')
      {
        $location = preg_replace('/&?piklist_validate=[^&]*/', '', $_SERVER['HTTP_REFERER']);
      }

      $location .= (stristr($location, '?') ? (substr($location, -1) == '&' ? '' : '&') : '?') . 'piklist_validate=' . self::$id;
    }
    else
    {
      if ($pagenow == 'edit-tags.php')
      {
        foreach (array('action', 'tag_ID', self::$parameter) as $variable)
        {
          $location = preg_replace('/&?' . $variable . '=[^&]*/', '', $location);
        }
      }
    }

    return $location;
  }
  
  public static function admin_head()
  {
    if (!empty(self::$submission['errors']))
    {
      piklist::render('shared/admin-notice-updated-hide');
    }
  }
  
  public static function admin_notices()
  {
    if (!empty(self::$submission['errors']))
    {
      $notices = array();
      foreach (self::$submission['errors'] as $type => $fields)
      {
        foreach ($fields as $field => $errors)
        {
          array_push($notices, current($errors));
        }
      }
      
      piklist::render('shared/admin-notice', array(
        'type' => 'error'
        ,'notices' => $notices
      ));
    }
  }
  
  public static function check(&$stored_data = null)
  {
    if (!isset($_REQUEST[piklist::$prefix]['fields_id']) || !$fields_data = get_transient(piklist::$prefix . $_REQUEST[piklist::$prefix]['fields_id'])) 
    {
      return false;
    }
    
    $fields_id = $_REQUEST[piklist::$prefix]['fields_id'];
    
    foreach ($fields_data as $type => &$fields)
    {
      foreach ($fields as &$field)
      {
        if (!is_null($stored_data))
        {
          $request_data = &$stored_data;
        }
        else
        {
          if (isset($_REQUEST['widget-id']) && isset($_REQUEST['multi_number']) && isset($_REQUEST['widget_number']))
          {
            $widget_index = !empty($_REQUEST['multi_number']) ? $_REQUEST['multi_number'] : $_REQUEST['widget_number'];
            $request_data = &$_REQUEST[piklist::$prefix . $field['scope']][$widget_index];
          }
          elseif (isset($field['scope']) && !empty($field['scope']))
          {
            $request_data = &$_REQUEST[piklist::$prefix . $field['scope']];
          }
          else
          {
            $request_data = &$_REQUEST;
          }
        }

        if (isset($request_data) && isset($field['field']))
        {
          $field['request_value'] = !strstr($field['field'], ':') ? (isset($request_data[$field['field']]) ? $request_data[$field['field']] : null) : piklist::array_path_get($request_data, explode(':', $field['field']));
          $field['valid'] = true;
          
          if (stristr($field['field'], ':0:'))
          {
            $_field = $field['field'];
            $value = array();
            $index = 0;
            
            do 
            {
              $_value = piklist::array_path_get($request_data, explode(':', $_field));
              if (isset($_value[$index]) && count($_value[$index]) > 1 && in_array($field['type'], piklist_form::$field_list_types['multiple_value']) && $field['add_more'])
              {
                $_value[$index] = array_values(array_filter($_value[$index]));
              }
              
              if (isset($_value[$index]))
              {
                array_push($value, $_value);
                
                piklist::array_path_set($request_data, explode(':', $_field), $_value);
                
                $_field = strrev(implode(strrev(':' . ($index + 1) . ':'), explode(':' . $index . ':', strrev($_field), 2)));
              }
              else
              {
                break;
              }
              
              $index++;
            } 
            while (isset($_value[$index]));
            
            $field['request_value'] = $_value;
          }
          elseif ($field['type'] == 'group' && empty($field['field']))
          {
            $field['request_value'] = array();
            
            foreach ($field['fields'] as $_field)
            {
              $field['request_value'][$_field['field']] = !strstr($_field['field'], ':') ? (isset($request_data[$_field['field']]) ? $request_data[$_field['field']] : null) : piklist::array_path_get($request_data, explode(':', $_field['field']));              
            }
          }
          else
          {
            $index = 0;
            
            do 
            {
              if (isset($field['request_value'][$index]) && count($field['request_value'][$index]) > 1 && $field['type'] == 'checkbox')
              {
                $field['request_value'][$index] = array_values(array_filter($field['request_value'][$index]));
              }
              
              $index++;
            } 
            while (isset($field['request_value'][$index]));
          
            piklist::array_path_set($request_data, explode(':', $field['field']), $field['request_value']);
          }

          if (isset($field['sanitize']))
          {
            foreach ($field['sanitize'] as $sanitize)
            {
              if (isset(self::$sanitization_rules[$sanitize['type']]))
              {
                $sanitization = array_merge(self::$sanitization_rules[$sanitize['type']], $sanitize);
                
                if (isset($sanitization['callback']))
                {
                  foreach ($field['request_value'] as $request_value)
                  {
                    $request_value = call_user_func_array($sanitization['callback'], array($request_value, $field, isset($sanitize['options']) ? $sanitize['options'] : array()));
                    $request_value = is_array($request_value) ? $request_value : array($request_value);
                    
                    piklist::array_path_set($request_data, explode(':', $field['field']), $request_value);
                  }
                }
              }
            }
          }
          
          self::add_request_value($field);
          
          if (isset($field['required']) && $field['required'])
          {
            for ($index = 0; $index < count($field['request_value']); $index++)
            {
              $request_value = is_array($field['request_value'][$index]) ? array_filter($field['request_value'][$index]) : $field['request_value'][$index];
              
              if (empty($request_value))
              {
                self::add_error($field, $index, __('is a required field.', 'piklist'));
              }
            }
          }
                    
          if (isset($field['validate']))
          {
            foreach ($field['validate'] as $validate)
            {
              if (isset(self::$validation_rules[$validate['type']]))
              {
                $validation = array_merge(self::$validation_rules[$validate['type']], $validate);
                $request_values = $field['request_value'];
                
                if ($field['type'] == 'group')
                {
                  $_request_values = array();
                  
                  foreach ($request_values as $key => $values)
                  {
                    foreach ($values as $index => $value)
                    {
                      if (!isset($_request_values[$index]))
                      {
                        $_request_values[$index] = array();
                      }
                      
                      $_request_values[$index][$key] = $value;
                    }
                  }
                  
                  $request_values = array($_request_values);
                }
                
                
                if (isset($validation['rule']))
                {
                  for ($index = 0; $index < count($request_values); $index++)
                  {
                    if (!empty($request_values[$index]) && !preg_match($validation['rule'], $request_values[$index]))
                    {
                      self::add_error($field, $index, $validation['message']);
                    }
                  }
                }
      
                if (isset($validation['callback']))
                {
                  for ($index = 0; $index < count($request_values); $index++)
                  {
                    if (!empty($request_values[$index]) || ($field['type'] != 'group' && $field['add_more']))
                    {
                      $validation_result = call_user_func_array($validation['callback'], array($request_values[$index], $field, isset($validate['options']) ? $validate['options'] : array()));
        
                      if ($validation_result !== true)
                      {
                        self::add_error($field, $index, isset($validation['message']) ? $validation['message'] : (is_string($validation_result) ? $validation_result : __('is not valid input', 'piklist')));
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    self::set_data($fields_id);
    
    return !empty(self::$submission['errors']) ? false : $fields_data;
  }
  
  private static function add_request_value($field)
  {
    if (!isset(self::$submission['request'][$field['scope']][$field['field']]))
    {
      self::$submission['request'][$field['scope']][$field['field']] = array();
    }
    
    self::$submission['request'][$field['scope']][$field['field']] = $field['request_value'];
  }
  
  public static function get_request_value($field, $scope)
  {
    return isset(self::$submission['request'][$scope][$field]) ? self::$submission['request'][$scope][$field] : null;
  }
  
  private static function add_error(&$field, $index, $message)
  {
    $field['valid'] = false;
    
    $name = isset($field['label']) && !empty($field['label']) ? $field['label'] : (isset($field['attributes']['placeholder']) ? $field['attributes']['placeholder'] : __(ucwords($field['type'])));
    
    if (!isset(self::$submission['errors'][$field['scope']][$field['field']]))
    {
      self::$submission['errors'][$field['scope']][$field['field']] = array();
    }
    
    self::$submission['errors'][$field['scope']][$field['field']][$index] = '<strong>' . $name . '</strong>' . "&nbsp;" . $message;
  }
  
  private static function set_data($fields_id)
  {
    if (!empty(self::$submission['errors']))
    {
      self::$id = substr(md5($fields_id), 0, 10);
      
      $set = set_transient(piklist::$prefix . 'validation_' . self::$id, self::$submission);
    }
  }
  
  public static function get_data()
  {
    if (isset($_REQUEST[self::$parameter]))
    {
      self::$id = $_REQUEST[self::$parameter];
      
      self::$submission = get_transient(piklist::$prefix . 'validation_' . self::$id);
      
      delete_transient(piklist::$prefix . 'validation_' . self::$id);
    }
  }
  
  public static function errors()
  {
    return empty(self::$submission['errors']) ? false : true;
  } 
  
  public static function get_errors($field, $scope)
  {
    return isset(self::$submission['errors'][$scope][$field]) ? self::$submission['errors'][$scope][$field] : false;
  }
    

  
  

  /**
   * Included Validation Callbacks
   */
  
  public static function validation_rules()
  {
    $validation_rules = array(
      'email' => array(
        'name' => __('Email Address', 'piklist')
        ,'description' => __('Verifies that the input is in the proper format for an email address.', 'piklist')
        ,'callback' => array('piklist_validate', 'validate_email')
      )
      ,'email_domain' => array(
        'name' => __('Email Domain', 'piklist')
        ,'description' => __('Verifies that the email domain entered is a valid domain.', 'piklist')
        ,'callback' => array('piklist_validate', 'validate_email_domain')
      )
      ,'file_exists' => array(
        'name' => __('File Exists?', 'piklist')
        ,'description' => __('Verifies that the file path entered leads to an actual file.', 'piklist')
        ,'callback' => array('piklist_validate', 'validate_file_exists')
      )
      ,'html' => array(
        'name' => __('Valid HTML', 'piklist')
        ,'description' => __('Verifies that the data entered is valid HTML.', 'piklist')
        ,'rule' => "/^<([a-z]+)([^<]+)*(?:>(.*)<\/\1>|\s+\/>)$/"
        ,'message' => __('is not valid HTML', 'piklist')
      )
      ,'image' => array(
        'name' => __('Is Image?', 'piklist')
        ,'description' => __('Verifies that the file path entered leads to an image file.', 'piklist')
        ,'callback' => array('piklist_validate', 'validate_image')
      )
      ,'ip_address' => array(
        'name' => __('IP Address', 'piklist')
        ,'description' => __('Verifies that the data entered is a valid IP Address.', 'piklist')
        ,'rule' => "/^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/"
        ,'message' => __('is not a valid ip address.', 'piklist')
      )
      ,'limit' => array(
        'name' => __('Entry Limit', 'piklist')
        ,'description' => __('Verifies that the number of items added are within the defined limit.', 'piklist')
        ,'callback' => array('piklist_validate', 'validate_limit')
      )
      ,'range' => array(
        'name' => __('Range', 'piklist')
        ,'description' => __('Verifies that the data entered is within the defined range.', 'piklist')
        ,'callback' => array('piklist_validate', 'validate_range')
      )
      ,'safe_text' => array(
        'name' => __('Alphanumeric', 'piklist')
        ,'description' => __('Verifies that the data entered is alphanumeric.', 'piklist')
        ,'rule' => "/^[a-zA-Z0-9 .-]+$/"
        ,'message' => __('contains invalid characters. Must contain only letters and numbers.', 'piklist')
      )
      ,'url' => array(
        'name' => __('URL', 'piklist')
        ,'description' => __('Verifies that the data entered is a valid URL.', 'piklist')
        ,'rule' => "/^(https?:\/\/)?([\da-z\.-]+)\.([a-z\.]{2,6})([\/\w \.-]*)*\/?$/"
        ,'message' => __('is not a valid url.', 'piklist')
      )
    );

    return $validation_rules;
  }


  /**
   * Validate email address
   * @param  $email
   * @param  $field 
   * @param  $arguments
   * @return bool true if string is a valid email address, message otherwise.
   */
  public static function validate_email($value, $field, $arguments)
  {
    return is_email($value) ? true : __('does not contain a valid Email Address.', 'piklist');
  }

  /**
   * Validate email address domain
   *
   * When checkdnsrr() returns false, it also returns a php warning.
   * The warning is being suppressed, since it will return a validation message.
   * 
   * @param  $email
   * @param  $field 
   * @param  $arguments
   * @return bool true if string is a valid email domain, message otherwise.
   */
  public static function validate_email_domain($value, $field, $arguments)
  {
    return (bool) @checkdnsrr(preg_replace('/^[^@]++@/', '', $value), 'MX') ? true : __('does not contain a valid Email Domain.', 'piklist');
  }

  /**
   * Validate a file exists
   *
   * When file_get_contents() returns false, it also returns a php warning.
   * The warning is being suppressed, since it will return a validation message.
   * 
   * @param  $file
   * @param  $field 
   * @param  $arguments
   * @return bool true if $file exists, message otherwise.
   */
  public static function validate_file_exists($value, $field, $arguments)
  {
    return @file_get_contents($value) ? true : __('contains a file that does not exist.', 'piklist');
  }

  /**
   * Validate an image file exists
   *
   * When exif_imagetype() returns false, it also returns a php warning.
   * The warning is being suppressed, since it will return a validation message.
   * 
   * @param  $file
   * @param  $field 
   * @param  $arguments
   * @return bool true if string is an image file, message otherwise.
   */
  public static function validate_image($value, $field, $arguments)
  {
    return @exif_imagetype($value) ? true : __('contains a file that is not an image.', 'piklist');
  }

  /**
   * Validate how many items are in request value
   *
   * Request value can be any Piklist field.
   * 
   * @param  $value
   * @param  $field 
   * @param  $arguments
   * @return bool true if value is within limit, message otherwise.
   */
  public static function validate_limit($value, $field, $arguments)
  {
    extract($arguments);

    $min = isset($min) ? $min : 1;
    $grammar = $field['type'] == 'file' || $field['add_more'] == 1 ? __('added', 'piklist') : __('selected', 'piklist');
    $count = ($field['type'] != 'group' && !$field['multiple']) ? count($field['request_value']) : count($value);
    
    if ($count < $min || (isset($max) && $count > $max))
    {
      if (isset($max) && ($min == $max))
      {
        return sprintf(__('must have exactly %1$s items %2$s.', 'piklist'), $min, $grammar);
      }
      else
      {
        return sprintf(__('must have between %1$s and %2$s items %3$s.', 'piklist'), $min, $max, $grammar);
      }
    }

    return true;
  }

  /**
   * Validate if a numbered value is within a range.
   * 
   * @param  $value
   * @param  $field 
   * @param  $arguments
   * @return bool true if value is within range, message otherwise.
   */
  public static function validate_range($value, $field, $arguments)
  {
    extract($arguments);

    $min = isset($arguments['min']) ? $arguments['min'] : 1;
    $max = isset($arguments['max']) ? $arguments['max'] : 10;

    if (($field['request_value'][0] >= $min) && ($field['request_value'][0] <= $max))
    {
      return true;
    }
    else
    {
      return sprintf(__('contains a value that is not between %s and %s', 'piklist'), $min, $max);
    }
  }
  
  
  
  /**
   * Included Sanitization Callbacks
   */
  
  public static function sanitization_rules()
  {
    $sanitization_rules = array(
      'email' => array(
        'name' => __('Email address', 'piklist')
        ,'description' => __('Strips out all characters that are not allowable in an email address.', 'piklist')
        ,'callback' => array('piklist_validate', 'sanitize_email')
      )
      ,'file_name' => array(
        'name' => __('File name', 'piklist')
        ,'description' => __('Removes or replaces special characters that are illegal in filenames.', 'piklist')
        ,'callback' => array('piklist_validate', 'sanitize_file_name')
      )
      ,'html_class' => array(
        'name' => __('HTML class', 'piklist')
        ,'description' => __('Removes all characters that are not allowable in an HTML classname.', 'piklist')
        ,'callback' => array('piklist_validate', 'sanitize_html_class')
      )
      ,'text_field' => array(
        'name' => __('Text field', 'piklist')
        ,'description' => __('Removes all HTML markup, as well as extra whitespace, leaving only plain text.', 'piklist')
        ,'callback' => array('piklist_validate', 'sanitize_text_field')
      )
      ,'title' => array(
        'name' => __('Post title', 'piklist')
        ,'description' => __('Removes all HTML and PHP tags, returning a title that is suitable for a url', 'piklist')
        ,'callback' => array('piklist_validate', 'sanitize_title')
      )
      ,'user' => array(
        'name' => __('Username', 'piklist')
        ,'description' => __('Removes all unsafe characters for a username.', 'piklist')
        ,'callback' => array('piklist_validate', 'sanitize_user')
      )
      ,'wp_kses' => array(
        'name' => __('wp_kses', 'piklist')
        ,'description' => __('Makes sure that only the allowed HTML element names, attribute names and attribute values plus only sane HTML entities are accepted.', 'piklist')
        ,'callback' => array('piklist_validate', 'sanitize_wp_kses')
      )
      ,'wp_filter_kses' => array(
        'name' => __('wp_filter_kses', 'piklist')
        ,'description' => __('Makes sure only default HTML elements are accepted.', 'piklist')
        ,'callback' => array('piklist_validate', 'sanitize_wp_filter_kses')
      )
      ,'wp_kses_post' => array(
        'name' => __('wp_kses_post', 'piklist')
        ,'description' => __('Makes sure only appropriate HTML elements for post content are accepted.', 'piklist')
        ,'callback' => array('piklist_validate', 'sanitize_wp_kses_post')
      )
    );

    return $sanitization_rules;
  }

  public static function sanitize_email($value, $field, $arguments)
  {   
    return sanitize_email($value);
  }

  public static function sanitize_file_name($value, $field, $arguments)
  {
    return sanitize_file_name($value);
  }

  public static function sanitize_html_class($value, $field, $arguments)
  {
    extract($arguments);
    
    return sanitize_html_class($value, isset($fallback) ? $fallback : null);
  }
  
  public static function sanitize_text_field($value, $field, $arguments)
  {
    return sanitize_text_field($value);
  }

  public static function sanitize_title($value, $field, $arguments)
  {
    extract($arguments);
    
    return sanitize_title($value, isset($fallback) ? $fallback : null, isset($context) ? $context : null);
  }

  public static function sanitize_user($value, $field, $arguments)
  {
    extract($arguments);
    
    return sanitize_user($value, isset($strict) ? $strict : null);
  }

  public static function sanitize_wp_kses($value, $field, $arguments)
  {
    extract($arguments);
    
    return wp_kses($value, isset($allowed_html) ? $allowed_html : null, isset($allowed_protocols) ? $allowed_protocols : null);
  }

  public static function sanitize_wp_kses_post($value, $field, $arguments)
  {
    return wp_kses_post($value);
  }

  public static function sanitize_wp_filter_kses($value, $field, $arguments)
  {
    return wp_kses_data($value);
  }

}