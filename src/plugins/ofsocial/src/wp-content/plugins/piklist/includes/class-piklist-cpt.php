<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_CPT
{
  private static $post_types = array();

  private static $taxonomies = array();

  private static $meta_boxes_locked = array();

  private static $meta_boxes_hidden = array();

  private static $meta_box_nonce = null;

  private static $meta_boxes_builtin = array(
    'slug'
    ,'author'
    ,'revision'
    ,'pageparent'
    ,'comments'
    ,'commentstatus'
    ,'postcustom'
  );

  private static $search_data = array();

  public static function _construct()
  {
    add_action('init', array('piklist_cpt', 'init'));
    add_action('add_meta_boxes', array('piklist_cpt', 'register_meta_boxes'));
    add_action('do_meta_boxes', array('piklist_cpt', 'sort_meta_boxes'), 100, 3);
    add_action('save_post', array('piklist_cpt', 'save_post'), -1, 3);
    add_action('pre_get_posts', array('piklist_cpt', 'pre_get_posts'), 100);
    add_action('edit_page_form', array('piklist_cpt', 'edit_form'));
    add_action('edit_form_advanced', array('piklist_cpt', 'edit_form'));
    add_action('piklist_activate', array('piklist_cpt', 'activate'));

    add_filter('posts_join', array('piklist_cpt', 'posts_join'), 10, 2);
    add_filter('posts_where', array('piklist_cpt', 'posts_where'), 10, 2);
    add_filter('post_row_actions', array('piklist_cpt', 'post_row_actions'), 10, 2);
    add_filter('page_row_actions', array('piklist_cpt', 'post_row_actions'), 10, 2);
    add_filter('wp_insert_post_data', array('piklist_cpt', 'wp_insert_post_data'), 100, 2);
  }

  public static function init()
  {
    self::register_tables();
    self::register_taxonomies();
    self::register_post_types();
  }

  public static function register_tables()
  {
    global $wpdb;

    array_push($wpdb->tables, 'post_relationships');

    $wpdb->post_relationships = $wpdb->prefix . 'post_relationships';
  }

  public static function activate()
  {
    piklist::create_table(
      'post_relationships'
      ,'relate_id bigint(20) unsigned NOT NULL auto_increment
        ,post_id bigint(20) unsigned NOT NULL
        ,has_post_id bigint(20) unsigned NOT NULL
        ,PRIMARY KEY (relate_id)
        ,KEY post_id (post_id)
        ,KEY has_post_id (has_post_id)'
    );
  }

  public static function posts_join($join, $query)
  {
    global $wpdb;

    if (isset($query->query_vars['post_belongs']) && $post_id = $query->query_vars['post_belongs'])
    {
      $join .= " LEFT JOIN {$wpdb->prefix}post_relationships ON $wpdb->posts.ID = {$wpdb->prefix}post_relationships.has_post_id";
    }

    if (isset($query->query_vars['post_has']) && $post_id = $query->query_vars['post_has'])
    {
      $join .= " LEFT JOIN {$wpdb->prefix}post_relationships ON $wpdb->posts.ID = {$wpdb->prefix}post_relationships.post_id";
    }

    return $join;
  }

  public static function posts_where($where, $query)
  {
    global $wpdb;

    if (isset($query->query_vars['post_belongs']) && $post_id = $query->query_vars['post_belongs'])
    {
      $where .= " AND {$wpdb->prefix}post_relationships.post_id = $post_id";
    }

    if (isset($query->query_vars['post_has']) && $post_id = $query->query_vars['post_has'])
    {
      $where .= " AND {$wpdb->prefix}post_relationships.has_post_id = $post_id";
    }

    return $where;
  }

  public static function edit_form()
  {
    $fields = array(
      'relate'
      ,'post_id'
      ,'admin_hide_ui'
    );

    foreach ($fields as $field)
    {
      if (isset($_REQUEST[piklist::$prefix][$field]) && !empty($_REQUEST[piklist::$prefix][$field]))
      {
        piklist_form::render_field(array(
          'type' => 'hidden'
          ,'scope' => piklist::$prefix
          ,'field' => $field
          ,'value' => $_REQUEST[piklist::$prefix][$field]
        ));
      }
    }
  }

  public static function register_post_types()
  {
    self::$post_types = apply_filters('piklist_post_types', self::$post_types);

    $check = array();

    foreach (self::$post_types as $post_type => &$configuration)
    {
      $configuration['supports'] = empty($configuration['supports']) ? array(false) : $configuration['supports'];

      register_post_type($post_type, $configuration);

      if (!isset($check[$post_type]) || !$check[$post_type])
      {
        $check[$post_type] = $configuration;
      }

      if (isset($configuration['status']) && !empty($configuration['status']))
      {
        $configuration['status'] = apply_filters('piklist_post_type_statuses', $configuration['status'], $post_type);

        foreach ($configuration['status'] as $status => &$status_data)
        {
          $status_data['label_count'] = _n_noop($status_data['label'] . ' <span class="count">(%s)</span>', $status_data['label'] . ' <span class="count">(%s)</span>');
          $status_data['capability_type'] = $post_type;

          $status_data = wp_parse_args($status_data, array(
            'label' => false
            ,'label_count' => false
            ,'exclude_from_search' => null
            ,'capability_type' => 'post'
            ,'hierarchical' => false
            ,'public' => true
            ,'internal' => null
            ,'has_archive' => false
            ,'protected' => true
            ,'private' => null
            ,'show_in_admin_all' => null
            ,'publicly_queryable' => null
            ,'show_in_admin_status_list' => true
            ,'show_in_admin_all_list' => true
            ,'single_view_cap' => null
            ,'_builtin' => false
          ));

          if ($status != 'draft')
          {
            register_post_status($status, $status_data);
          }
        }
      }

      if (isset($configuration['hide_meta_box']) && !empty($configuration['hide_meta_box']) && is_array($configuration['hide_meta_box']))
      {
        foreach ($configuration['hide_meta_box'] as $meta_box)
        {
          if (!isset(self::$meta_boxes_hidden[$post_type]))
          {
            self::$meta_boxes_hidden[$post_type] = array();
          }
          array_push(self::$meta_boxes_hidden[$post_type], $meta_box . (in_array($meta_box, self::$meta_boxes_builtin) ? 'div' : null));
        }
      }
      
      add_action('admin_head', array('piklist_cpt', 'hide_meta_boxes'), 100);

      if (isset($configuration['title']) && !empty($configuration['title']))
      {
        add_filter('enter_title_here', array('piklist_cpt', 'enter_title_here'));
      }

      if (isset($configuration['page_icon']) && !empty($configuration['page_icon']))
      {
        global $pagenow;

        if (in_array($pagenow, array('edit.php', 'post.php', 'post-new.php')) && !isset($_GET['page']) && isset($_REQUEST['post_type']) && ($_REQUEST['post_type'] == $post_type))
        {
          piklist_admin::$page_icon = array(
            'page_id' => '.icon32.icon32-posts-' . $post_type
            ,'icon_url' => $configuration['page_icon']
          );
        }
      }

      if (isset($configuration['hide_screen_options']) && !empty($configuration['hide_screen_options']))
      {
        add_filter('screen_options_show_screen', array('piklist_cpt', 'hide_screen_options'));
      }

      if (isset($configuration['edit_columns']) && !empty($configuration['edit_columns']))
      {
        add_filter('manage_edit-' . $post_type . '_columns', array('piklist_cpt', 'manage_edit_columns'));
      }

      if (isset($configuration['admin_body_class']) && !empty($configuration['admin_body_class']))
      {
        add_filter('admin_body_class', array('piklist_cpt', 'admin_body_class'),999);
      }

      if (isset($configuration['edit_manage']) && !empty($configuration['edit_manage']))
      {
        add_action('restrict_manage_posts', array('piklist_cpt', 'restrict_manage_posts'));
      }

      if (isset($configuration['post_states']) && !empty($configuration['post_states']))
      {
        add_filter('display_post_states', array('piklist_cpt', 'display_post_states'));
      }

      add_filter('post_updated_messages', array('piklist_cpt', 'post_updated_messages_filter'));

    }

    self::sort_post_statuses();

    self::flush_rewrite_rules(md5(serialize($check)), 'piklist_post_type_rules_flushed');
  }

  public static function register_taxonomies()
  {
    global $wp_taxonomies;

    self::$taxonomies = apply_filters('piklist_taxonomies', self::$taxonomies);

    $check = array();

    foreach (self::$taxonomies as $taxonomy_name => $taxonomy)
    {
      $taxonomy['name'] = isset($taxonomy['name']) || is_numeric($taxonomy_name) ? $taxonomy['name'] : $taxonomy_name;

      $type = isset($taxonomy['object_type']) ? $taxonomy['object_type'] : $taxonomy['post_type'];

      if (!isset($taxonomy['update_count_callback']))
      {
        $taxonomy['update_count_callback'] = '_update_generic_term_count';
      }

      register_taxonomy($taxonomy['name'], $type, $taxonomy['configuration']);

      if (!isset($check[$taxonomy['name']]) || !$check[$taxonomy['name']])
      {
        $check[$taxonomy['name']] = $taxonomy;
      }

      if (isset($taxonomy['configuration']['hide_meta_box']) && !empty($taxonomy['configuration']['hide_meta_box']))
      {
        $object_types = is_array($type) ? $type : array($type);
        foreach ($object_types as $object_type)
        {
          if (!isset(self::$meta_boxes_hidden[$object_type]))
          {
            self::$meta_boxes_hidden[$object_type] = array();
          }
          array_push(self::$meta_boxes_hidden[$object_type], $taxonomy['configuration']['hierarchical'] ? $taxonomy['name'] . 'div' : 'tagsdiv-' . $taxonomy['name']);
        }
      }

      add_action('admin_head', array('piklist_cpt', 'hide_meta_boxes'), 100);

      if (isset($taxonomy['configuration']['page_icon']) && !empty($taxonomy['configuration']['page_icon']))
      {
        global $pagenow;

        if (($pagenow == 'edit-tags.php') && ($_REQUEST['taxonomy'] == $taxonomy['name']))
        {
          piklist_admin::$page_icon = array(
            'page_id' => isset($taxonomy['object_type']) && $taxonomy['object_type'] == 'user' ? '#icon-users.icon32' : '#icon-edit.icon32'
            ,'icon_url' => $taxonomy['configuration']['page_icon']
          );
        }
      }
    }
    
    self::flush_rewrite_rules(md5(serialize($check)), 'piklist_taxonomy_rules_flushed');
  }

  public static function flush_rewrite_rules($check, $option)
  {
    if ($check != get_option($option))
    {
      flush_rewrite_rules(false);
      update_option($option, $check);
    }
  }

  public static function hide_meta_boxes()
  {
    global $pagenow, $wp_meta_boxes, $typenow, $post;
    
    if (in_array($pagenow, array('post.php', 'post-new.php')))
    {
      if (isset(self::$meta_boxes_hidden[$typenow]) && !in_array('submitdiv', self::$meta_boxes_hidden[$typenow]))
      {
        array_push(self::$meta_boxes_hidden[$typenow], 'submitdiv');
      }

      foreach (array('normal', 'advanced', 'side') as $context)
      {
        foreach (array('high', 'core', 'default', 'low') as $priority)
        {
          if (isset($wp_meta_boxes[$typenow][$context][$priority]))
          {
            foreach ($wp_meta_boxes[$typenow][$context][$priority] as $meta_box => $data)
            {
              if ($meta_box == 'submitdiv' && $typenow != 'attachment' && isset(self::$post_types[$typenow]['status']) && !empty(self::$post_types[$typenow]['status']))
              {
                $wp_meta_boxes[$typenow][$context][$priority][$meta_box]['title'] = apply_filters('piklist_post_submit_meta_box_title', $wp_meta_boxes[$typenow][$context][$priority][$meta_box]['title'], $post);
                $wp_meta_boxes[$typenow][$context][$priority][$meta_box]['callback'] = array('piklist_cpt', 'post_submit_meta_box');
              }
              elseif ($meta_box != 'submitdiv' && isset(self::$meta_boxes_hidden[$typenow]) && in_array($meta_box, self::$meta_boxes_hidden[$typenow]))
              {
                unset($wp_meta_boxes[$typenow][$context][$priority][$meta_box]);
              }
            }
          }
        }
      }

      if (isset($wp_meta_boxes[$typenow]['side']['core']['submitdiv']))
      {
        $meta_boxes = array('submitdiv' => $wp_meta_boxes[$typenow]['side']['core']['submitdiv']);

        unset($wp_meta_boxes[$typenow]['side']['core']['submitdiv']);

        foreach ($wp_meta_boxes[$typenow]['side']['core'] as $id => $meta_box)
        {
          $meta_boxes[$id] = $meta_box;
        }

        $wp_meta_boxes[$typenow]['side']['core'] = $meta_boxes;
      }
    }
  }

  public static function post_submit_meta_box()
  {
    global $post, $wp_post_statuses, $typenow;

    $default_statuses = array(
      'draft' => $wp_post_statuses['draft']
      ,'pending' => $wp_post_statuses['pending']
    );

    if ($post->post_status == 'publish' || (!isset(self::$post_types[$typenow]['status']) || (isset(self::$post_types[$typenow]['status']) && isset(self::$post_types[$typenow]['status']['publish']))))
    {
      $default_statuses['publish'] = $wp_post_statuses['publish'];
    }

    $statuses = isset(self::$post_types[$post->post_type]['status']) ? self::$post_types[$post->post_type]['status'] : $default_statuses;

    foreach ($statuses as $status => &$configuration)
    {
      $configuration = (object) $configuration;
    }

    piklist::render('shared/post-submit-meta-box', array(
      'post' => $post
      ,'statuses' => $statuses
    ));
  }

  public static function sort_post_statuses()
  {
    global $wp_post_types, $wp_post_statuses, $typenow;

    $statuses = array();
    $_wp_post_statuses = array();
    $current_post_type = $typenow ? $typenow : (isset($_REQUEST['post_type']) ? $_REQUEST['post_type'] : null);

    foreach (self::$post_types as $post_type => $post_type_data)
    {
      if (isset($post_type_data['status']) && is_array($post_type_data['status']))
      {
        $statuses = $current_post_type == $post_type ? array_merge(array_keys($post_type_data['status']), $statuses) : array_merge($statuses, array_keys($post_type_data['status']));
      }
    }

    $statuses = array_reverse(array_unique(array_reverse($statuses)));

    foreach ($statuses as $status)
    {
      $_wp_post_statuses[$status] = $wp_post_statuses[$status];
    }
    
    foreach ($wp_post_statuses as $status => $data)
    {
      if (!isset($_wp_post_statuses[$status]))
      {
        $_wp_post_statuses = array_merge(array($status => $data), $_wp_post_statuses);
      }
    }
    
    $wp_post_statuses = $_wp_post_statuses;
  }

  public static function manage_edit_columns($columns)
  {
    $post_type = $_REQUEST['post_type'];

    if (isset(self::$post_types[$post_type]))
    {
      return array_merge($columns, self::$post_types[$post_type]['edit_columns']);
    }

    return $columns;
  }

  public static function post_row_actions($actions, $post)
  {
    global $current_screen;

    if (isset($current_screen))
    {
      if (isset(self::$post_types[$current_screen->post_type]) && isset(self::$post_types[$current_screen->post_type]['hide_post_row_actions']))
      {
        foreach (self::$post_types[$current_screen->post_type]['hide_post_row_actions'] as $action)
        {
          unset($actions[$action == 'quick-edit' ? 'inline hide-if-no-js' : $action]);
        }
      }
    }

    return $actions;
  }

  public static function admin_body_class($classes)
  {
    global $pagenow;

    if (in_array($pagenow, array('edit.php', 'post.php', 'post-new.php')) && post_type_exists(get_post_type()))
    {
      $post_type = get_post_type();

      if (!empty(self::$post_types[$post_type]['admin_body_class']))
      {
        $admin_body_class = is_array(self::$post_types[$post_type]['admin_body_class']) ? self::$post_types[$post_type]['admin_body_class'] : array(self::$post_types[$post_type]['admin_body_class']);
        foreach ($admin_body_class as $class)
        {
          $classes .= ' ';
          $classes .= $class;
        }
      }
    }
    return $classes;
  }

  public static function restrict_manage_posts()
  {
    // NOTE: How to handle pre-existing printed info?  Callback function here?
  }

  public static function display_post_states($states)
  {
    global $post, $wp_post_statuses;

    $current_status = get_query_var('post_status');

    if ($current_status != $post->post_status)
    {
      return array($wp_post_statuses[$post->post_status]->label);
    }

    return $states;
  }

  public static function save_post($post_id, $post, $update)
  {
    if (empty($_REQUEST) || !isset($_REQUEST[piklist::$prefix]['nonce']))
    {
      return $post_id;
    }

    if (!wp_verify_nonce($_REQUEST[piklist::$prefix]['nonce'], plugin_basename(piklist::$paths['piklist'] . '/piklist.php')))
    {
      return $post_id;
    }

    if (defined('DOING_AUTOSAVE') && DOING_AUTOSAVE)
    {
      return $post_id;
    }

    if (wp_is_post_revision($post_id) && !wp_is_post_autosave($post_id))
    {
      return $post_id;
    }

    if ($post && $post->post_type == 'page')
    {
      if (!current_user_can('edit_page', $post_id))
      {
        return $post_id;
      }
    }
    elseif (!current_user_can('edit_post', $post_id))
    {
      return $post_id;
    }
    
    remove_action('save_post', array('piklist_cpt', 'save_post'), -1);

    piklist_form::save(array(
      'post' => $post_id
    ));

    add_action('save_post', array('piklist_cpt', 'save_post'), -1, 3);
  }

  public static function register_meta_boxes()
  {
    piklist::process_views('meta-boxes', array('piklist_cpt', 'register_meta_boxes_callback'));
  }

  public static function register_meta_boxes_callback($arguments)
  {
    global $post, $pagenow;

    extract($arguments);

    $current_user = wp_get_current_user();

    $data = get_file_data($path . '/parts/' . $folder . '/' . $part, apply_filters('piklist_get_file_data', array(
              'name' => 'Title'
              ,'context' => 'Context'
              ,'description' => 'Description'
              ,'capability' => 'Capability'
              ,'role' => 'Role'
              ,'priority' => 'Priority'
              ,'order' => 'Order'
              ,'type' => 'Post Type'
              ,'lock' => 'Lock'
              ,'collapse' => 'Collapse'
              ,'status' => 'Status'
              ,'new' => 'New'
              ,'id' => 'ID'
              ,'div' => 'DIV'
              ,'template' => 'Template'
              ,'box' => 'Meta Box'
            ), 'meta-boxes'));

    $data = apply_filters('piklist_add_part', $data, 'meta-boxes');

    $types = empty($data['type']) ? get_post_types() : explode(',', $data['type']);
    
    foreach ($types as $type)
    {
      $type = trim($type);

      $statuses = !empty($data['status']) ? explode(',', $data['status']) : false;
      $ids = !empty($data['id']) ? explode(',', $data['id']) : false;
      $name = !empty($data['name']) ? $data['name'] : 'piklist_meta_' . piklist::slug($part);
      
      if (post_type_exists($type)
        && (!$data['capability'] || ($data['capability'] && current_user_can(strtolower($data['capability']))))
        && (!$data['role'] || ($data['role'] && piklist_user::current_user_role($data['role'])))
        && (!$data['status'] || ($data['status'] && in_array($post->post_status, $statuses)))
        && (!$data['new'] || ($data['new'] && $pagenow != 'post-new.php'))
        && (!$data['id'] || ($data['id'] && in_array($post->ID, $ids)))
        && (!$data['template'] || ($data['template'] && $data['template'] == pathinfo(get_page_template_slug($post->ID), PATHINFO_FILENAME)))
      )
      {
        $id = !empty($data['div']) ? $data['div'] : 'piklist_meta_' . piklist::slug($part);
        $textdomain = isset(piklist_add_on::$available_add_ons[$add_on]) && isset(piklist_add_on::$available_add_ons[$add_on]['TextDomain']) ? piklist_add_on::$available_add_ons[$add_on]['TextDomain'] : null;
         
        add_meta_box(
          $id
          ,!empty($textdomain) ? __($name, $textdomain) : $name
          ,array('piklist_cpt', 'meta_box')
          ,$type
          ,!empty($data['context']) ? $data['context'] : 'normal'
          ,!empty($data['priority']) ? $data['priority'] : 'low'
          ,array(
            'part' => $part
            ,'add_on' => $add_on
            ,'order' => $data['order'] ? $data['order'] : null
            ,'config' => $data
          )
        );

        if (isset($data['box']) && strtolower($data['box']) == 'false')
        {
          add_filter("postbox_classes_{$type}_{$id}", array('piklist_cpt', 'lock_meta_boxes'));
          add_filter("postbox_classes_{$type}_{$id}", array('piklist_cpt', 'no_meta_boxes'));

          if ($name == 'piklist_meta_' . piklist::slug($part))
          {
            add_filter("postbox_classes_{$type}_{$id}", array('piklist_cpt', 'no_title_meta_boxes'));
          }
        }
        else
        {
          if (isset($data['lock']) && strtolower($data['lock']) == 'true')
          {
            add_filter("postbox_classes_{$type}_{$id}", array('piklist_cpt', 'lock_meta_boxes'));
          }
          if (isset($data['collapse']) && strtolower($data['collapse']) == 'true')
          {
            add_filter("postbox_classes_{$type}_{$id}", array('piklist_cpt', 'collapse_meta_boxes'));
          }
        }
        add_filter("postbox_classes_{$type}_{$id}", array('piklist_cpt', 'default_classes'));
      }
    }
  }

  public static function meta_box($post, $meta_box)
  {
    global $typenow;

    if (!self::$meta_box_nonce)
    {
      piklist_form::render_field(array(
        'type' => 'hidden'
        ,'field' => 'nonce'
        ,'value' => wp_create_nonce(plugin_basename(piklist::$paths['piklist'] . '/piklist.php'))
        ,'scope' => piklist::$prefix
      ));

      self::$meta_box_nonce = true;
    }

    do_action('piklist_pre_render_meta_box', $post, $meta_box);

    piklist::render(piklist::$paths[$meta_box['args']['add_on']] . '/parts/meta-boxes/' . $meta_box['args']['part'], array(
      'type' => $typenow
      ,'prefix' => 'piklist'
      ,'plugin' => 'piklist'
    ), false);
    
    do_action('piklist_post_render_meta_box', $post, $meta_box);
  }

  public static function sort_meta_boxes($post_type, $context, $post)
  {
    global $pagenow;

    if (in_array($pagenow, array('edit.php', 'post.php', 'post-new.php')) && post_type_exists(get_post_type()))
    {
      global $wp_meta_boxes;

      foreach (array('high', 'sorted', 'core', 'default', 'low') as $priority)
      {
        if (isset($wp_meta_boxes[$post_type][$context][$priority]))
        {
          uasort($wp_meta_boxes[$post_type][$context][$priority], array('piklist', 'sort_by_args_order'));
        }
      }

      add_filter('get_user_option_meta-box-order_' . $post_type, array('piklist_cpt', 'user_sort_meta_boxes'), 100, 3);
    }
  }

  public static function user_sort_meta_boxes($result, $option, $user)
  {
    global $wp_meta_boxes;

    $post_type = str_replace('meta-box-order_', '', $option);

    $order = array(
      'side' => ''
      ,'normal' => ''
      ,'advanced' => ''
    );

    foreach (array('side', 'normal', 'advanced') as $context)
    {
      foreach (array('high', 'sorted', 'core', 'default', 'low') as $priority)
      {
        if (isset($wp_meta_boxes[$post_type][$context][$priority]) && !empty($wp_meta_boxes[$post_type][$context][$priority]))
        {
          $order[$context] .= (!empty($order[$context]) ? ',' : '') . implode(',', array_keys($wp_meta_boxes[$post_type][$context][$priority]));
        }
      }

    }

    return $order;
  }

  public static function lock_meta_boxes($classes)
  {
    array_push($classes, 'piklist-meta-box-lock hide-all');
    return $classes;
  }

  public static function no_title_meta_boxes($classes)
  {
    array_push($classes, 'piklist-meta-box-no-title');
    return $classes;
  }

  public static function no_meta_boxes($classes)
  {
    array_push($classes, 'piklist-meta-box-none');
    return $classes;
  }

  public static function default_classes($classes)
  {
    array_push($classes, 'piklist-meta-box');
    return $classes;
  }

  public static function collapse_meta_boxes($classes)
  {
    array_push($classes, 'piklist-meta-box-collapse');
    return $classes;
  }

  public static function default_post_title($id)
  {
    $post = get_post($id);

    wp_update_post(array(
      'ID' => $id
      ,'post_title' => ucwords(str_replace(array('-', '_'), ' ', $post->post_type)) . ' ' . $id
    ));
  }

  public static function post_updated_messages_filter($messages)
  {
    global $post, $post_ID;

    $post_type = get_post_type($post_ID);

    $obj = get_post_type_object($post_type);

    $singular = $obj->labels->singular_name;

    $messages[$post_type] = array(
      0 => ''
      ,1 => sprintf(__($singular . ' updated. <a href="%s">View ' . $singular . '</a>'), esc_url(get_permalink($post_ID)))
      ,2 => __('Custom field updated.')
      ,3 => __('Custom field deleted.')
      ,4 => __($singular.' updated.')
      ,5 => isset($_GET['revision']) ? sprintf( __($singular . ' restored to revision from %s'), wp_post_revision_title((int) $_GET['revision'], false )) : false
      ,6 => sprintf(__($singular.' published. <a href="%s">View ' . $singular . '</a>'), esc_url(get_permalink($post_ID)))
      ,7 => __('Page saved.')
      ,8 => sprintf(__($singular . ' submitted. <a target="_blank" href="%s">Preview ' . $singular . '</a>'), esc_url( add_query_arg('preview', 'true', get_permalink($post_ID))))
      ,9 => sprintf(__($singular.' scheduled for: <strong>%1$s</strong>. <a target="_blank" href="%2$s">Preview ' . $singular . '</a>'), date_i18n(__('M j, Y @ G:i'), strtotime($post->post_date)), esc_url(get_permalink($post_ID)))
      ,10 => sprintf(__($singular . ' draft updated. <a target="_blank" href="%s">Preview ' . $singular . '</a>'), esc_url( add_query_arg( 'preview', 'true', get_permalink($post_ID))))
    );

    return $messages;

  }

  public static function enter_title_here($title)
  {
    $post_type = get_post_type();

    return isset(self::$post_types[$post_type]['title']) ? self::$post_types[$post_type]['title'] : $title;
  }

  public static function hide_screen_options()
  {
    global $pagenow;

    if (in_array($pagenow, array('edit.php', 'post.php', 'post-new.php')))
    {
      $post_type = get_post_type();

      if (isset(self::$post_types[$post_type]['hide_screen_options']))
      {
        return self::$post_types[$post_type]['hide_screen_options'] ? false : true;
      }
      else
      {
        return true;
      }
    }
  }

  public static function get_post_statuses($object_type = null)
  {
    global $wp_post_statuses;

    $status_list = array();
    $object_type = $object_type ? $object_type : get_post_type();

    foreach ($wp_post_statuses as $status)
    {
      if (isset($status->capability_type) && $status->capability_type == $object_type)
      {
        array_push($status_list, $status->name);
      }
    }

    return $status_list;
  }

  public static function wp_insert_post_data($data, $post_array)
  {
    if (($data['post_status'] != 'auto-draft') && (($data['post_title'] == 'Auto Draft') || empty($data['post_title'])))
    {
      $data['post_title'] = apply_filters('piklist_empty_post_title', $data['post_title'], $post_array);
    }

    return $data;
  }

  public static function pre_get_posts(&$query)
  {
    if ($query->is_main_query() && isset($_REQUEST) && (isset($_REQUEST[piklist::$prefix]['filter']) && strtolower($_REQUEST[piklist::$prefix]['filter']) == 'true') && isset($_REQUEST[piklist::$prefix]['fields_id']))
    {
      $args = array(
        'meta_query' => array(
          'relation' => isset($_REQUEST[piklist::$prefix . 'post_meta']['relation']) && in_array(strtoupper($_REQUEST[piklist::$prefix . 'post_meta']['relation'][0]), array('AND', 'OR')) ? strtoupper($_REQUEST[piklist::$prefix . 'post_meta']['relation'][0]) : 'AND'
        )
        ,'tax_query' => array(
          'relation' => isset($_REQUEST[piklist::$prefix . 'taxonomy']['relation']) && in_array(strtoupper($_REQUEST[piklist::$prefix . 'taxonomy']['relation'][0]), array('AND', 'OR')) ? strtoupper($_REQUEST[piklist::$prefix . 'taxonomy']['relation'][0]) : 'AND'
        )
      );

      $fields = get_transient(piklist::$prefix . $_REQUEST[piklist::$prefix]['fields_id']);

      self::$search_data = $_REQUEST;

      foreach ($fields as $filter => $_fields)
      {
        foreach ($fields[$filter] as $field => $field_config)
        {
          if (isset($field_config['include_fields']))
          {
            foreach ($field_config['include_fields'] as $include_scope => $include_fields)
            {
              $value = self::$search_data[piklist::$prefix . $filter][$field];
              if (isset($include_field) && $include_field == 's')
              {
                $query->set('s', is_array($value) ? current($value) : $value);
              }
              else
              {
                foreach ($include_fields as $include_field)
                {
                  self::$search_data[piklist::$prefix . $include_scope][$include_field] = $value;
                  $fields[$include_scope][$include_field] = $fields[$filter][$field];
                }
              }
            }
          }
        }
      }

      foreach (self::$search_data as $key => $values)
      {
        $filter = substr($key, strlen(piklist::$prefix));

        switch ($filter)
        {
          case 'post_meta':

            foreach ($values as $meta_key => $meta_value)
            {
              $field = $fields[$filter][$meta_key];

              $meta_value = is_array($meta_value) ? array_filter($meta_value) : $meta_value;

              if (!empty($meta_value) && $meta_key != 'relation')
              {
                if (strstr($meta_key, '__min'))
                {
                  array_push(
                    $args['meta_query']
                    ,array(
                      'key' => str_replace('__min', '', $meta_key)
                      ,'value' => array($meta_value, $_REQUEST[$key][str_replace('__min', '__max', $meta_key)])
                      ,'type' => 'NUMERIC'
                      ,'compare' => 'BETWEEN'
                    )
                  );
                }
                elseif (!strstr($meta_key, '__min') && !strstr($meta_key, '__max'))
                {
                  array_push(
                    $args['meta_query']
                    ,array(
                      'key' => $meta_key
                      ,'value' => is_array($meta_value) && count($meta_value) > 1 ? $meta_value : stripslashes(is_array($meta_value) ? $meta_value[0] : $meta_value)
                      ,'compare' => is_array($meta_value) && count($meta_value) > 1 ? 'IN' : (isset($field['meta_query']['compare']) ? $field['meta_query']['compare'] : '=')
                      ,'type' => is_numeric(is_array($meta_value) ? $meta_value[0] : $meta_value) ? 'NUMERIC' : (isset($field['meta_query']['type']) ? $field['meta_query']['type'] : 'CHAR')
                    )
                  );
                }
              }
            }

          break;

          case 'taxonomy':

            foreach ($values as $taxonomy => $terms)
            {
              $field = $fields[$filter][$taxonomy];

              $terms = is_array($terms) ? array_filter($terms) : $terms;

              if (!empty($terms) && $taxonomy != 'relation')
              {
                array_push(
                  $args['tax_query']
                  ,array(
                    'taxonomy' => $taxonomy
                    ,'terms' => !is_array($terms) && strstr($terms, ',') ? explode(',', $terms) : $terms
                    ,'field' => isset($field['tax_query']['field']) ? $field['tax_query']['field'] : 'term_id'
                    ,'include_children' => isset($field['tax_query']['include_children']) ? $field['tax_query']['include_children'] : true
                    ,'operator' => isset($field['tax_query']['operator']) ? $field['tax_query']['operator'] : 'IN'
                  )
                );
              }
            }

          break;
        }
      }

      if (isset($_REQUEST[piklist::$prefix . 'post_type']))
      {
        $post_type = is_array($_REQUEST[piklist::$prefix . 'post_type']) ? implode(',', array_walk($_REQUEST[piklist::$prefix . 'post_type'], 'esc_attr')) : esc_attr($_REQUEST[piklist::$prefix . 'post_type']);
        $query->set('post_type', $post_type);
      }

      if (isset($_REQUEST[piklist::$prefix . 'posts_per_page']))
      {
        $posts_per_page = (int) $_REQUEST[piklist::$prefix . 'posts_per_page'];
        $query->set('posts_per_page', $posts_per_page);
      }

      if (count($args['meta_query']) > 1)
      {
        $query->set('meta_query', empty($query->query_vars['meta_query']) ? $args['meta_query'] : array_merge($query->query_vars['meta_query'], $args['meta_query']));
      }

      if (count($args['tax_query']) > 1)
      {
        $query->set('tax_query', empty($query->query_vars['tax_query']) ? $args['tax_query'] : array_merge($query->query_vars['tax_query'], $args['tax_query']));
      }

      if (isset(self::$search_data[piklist::$prefix . 'post']))
      {
        add_filter('posts_search', array('piklist_cpt', 'posts_search'), 10, 2);
      }
    }
  }

  public static function posts_search($search, &$query)
  {
    global $wpdb;

    if ($query->is_main_query() && !empty(self::$search_data[piklist::$prefix . 'post']) && empty($query->query_vars['s']))
    {
      $n = !empty($query->query_vars['exact']) ? '' : '%';
      foreach (self::$search_data[piklist::$prefix . 'post'] as $field => $terms)
      {
        $search_terms = '';
        $search_or = '';
        $search_join = empty($search) ? '' : ' OR ';
        foreach ($terms as $term)
        {
          $term = esc_sql(like_escape($term));
          $search_terms .= "{$search_or}$wpdb->posts.$field LIKE '{$n}{$term}{$n}'";
          $search_or = ' OR ';
        }
        $search .= "{$search_join}$search_terms";
      }

      if (!empty($search))
      {
        $search = "AND ({$search})";
        if (!is_user_logged_in())
        {
          $search .= " AND $wpdb->posts.post_password = '' ";
        }
      }
    }

    return $search;
  }
}