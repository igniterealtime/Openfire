<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_WordPress
{
  protected static $user_query_role_relation = 'AND';
  
  public static function _construct()
  {    
    add_action('pre_user_query', array('piklist_wordpress', 'pre_user_query'));

    add_filter('get_meta_sql', array('piklist_wordpress', 'get_meta_sql'), 101, 6);
  }

  public static function pre_user_query(&$query)
  {
    global $wpdb;
    
    if (isset($query->query_vars['roles']) && is_array($query->query_vars['roles']))
    {
      if (isset($query->query_vars['relation']) && in_array(strtolower($query->query_vars['relation']), array('or', 'and')))
      {
        self::$user_query_role_relation = $query->query_vars['relation'];
      }
      
      $capabilities_meta_key = $wpdb->get_blog_prefix($query->query_vars['blog_id']) . 'capabilities';
      
      if (is_array($query->query_vars['meta_query']))
      {
        foreach ($query->query_vars['meta_query'] as $index => $meta_query)
        {
          if ($meta_query['key'] == $capabilities_meta_key)
          {
            unset($query->query_vars['meta_query'][$index]);
          }
        }
      }
      
      $query->query_vars['meta_query'] = is_array($query->query_vars['meta_query']) ? $query->query_vars['meta_query'] : array();
      
      foreach ($query->query_vars['roles'] as $role)
      {
        array_push($query->query_vars['meta_query'], array(
          'key' => $capabilities_meta_key
          ,'value' => '"' . $role . '"'
          ,'compare' => 'like'
        ));
      }
      
      $query->query_vars['role'] = null;
      
      remove_action('pre_user_query', array('piklist_wordpress', 'pre_user_query'));
      
      $query->prepare_query();
      
      add_action('pre_user_query', array('piklist_wordpress', 'pre_user_query'));
    }
  }
  
  public static function get_meta_sql($meta_query, $queries, $type, $primary_table, $primary_id_column, $context)
  {
    global $wpdb;

    if (!apply_filters('piklist_get_meta_sql', true))
    {
      return $meta_query;
    }

    $where = array_filter(preg_split('/$\n?^/m', trim(substr($meta_query['where'], 6, strlen($meta_query['where']) - 8))));
    
    foreach ($queries as $query)
    {
      if (!isset($query['value']))
      {
        return $meta_query;
      }
    }

    if (!empty($where))
    {
      $relation = 'AND';

      $meta_table = substr($primary_table, 0, strlen($primary_table) - 1) . 'meta';
      $meta_prefixes = array($meta_table . '.');

      for ($i = 1; $i < count($where); $i++)
      {
        array_push($meta_prefixes, "mt{$i}.");
      }
    
      for ($i = 0; $i < count($where); $i++)
      {
        if ($i > 0)
        {
          if ($i == 1)
          {
            $relation = trim(substr($where[$i], 0, strpos($where[$i], '(')));
          }
      
          $where[$i] = substr($where[$i], strpos($where[$i], '('));
        }
      
        $where[$i] = str_replace($meta_prefixes, array(''), $where[$i]);
      }

      $ids = array();
      $user_ids = array();
      $query = false;
      $role_query = false;
      foreach ($where as $clause)
      {      
        if ($type == 'user' && stristr($clause, $wpdb->prefix . 'capabilities'))
        {
          if (self::$user_query_role_relation == 'AND' && !empty($user_ids))
          {
            $clause .= " AND {$type}_id IN (" . implode(',', $user_ids) . ")";
          }

          $result_ids = $wpdb->get_col("SELECT {$type}_id FROM {$meta_table} WHERE {$clause} GROUP BY {$type}_id");
          $user_ids = self::$user_query_role_relation == 'AND' && $role_query ? array_intersect($user_ids, $result_ids) : array_merge($user_ids, $result_ids);
          $role_query = true;
        }
        else
        {        
          if ($relation == 'AND' && !empty($ids))
          {
            $clause .= " AND {$type}_id IN (" . implode(',', $ids) . ")";
          }

          $result_ids = $wpdb->get_col("SELECT {$type}_id FROM {$meta_table} WHERE {$clause} GROUP BY {$type}_id");
          $ids = $relation == 'AND' && $query ? array_intersect($ids, $result_ids) : array_merge($ids, $result_ids);
          $query = true;
        }
      }

      if ($role_query)
      {
        $ids = empty($ids) ? $user_ids : array_intersect($ids, $user_ids);
      }

      $ids = array_unique($ids);

      $meta_query['join'] = '';
      $meta_query['where'] = ' ' . (isset($_REQUEST['meta_relation']) ? strtoupper($_REQUEST['meta_relation']) : 'AND'). " \n\t{$primary_table}.{$primary_id_column} IN (\n\t\t" . (!empty($ids) ? implode("\n\t\t,", $ids) : '-1') . "\n\t) \n";
    }

    return $meta_query;
  }
}