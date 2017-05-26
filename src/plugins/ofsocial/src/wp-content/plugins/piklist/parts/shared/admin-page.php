<div class="wrap">

  <?php if (isset($page_icon)) : ?>

    <?php
      piklist_admin::$page_icon = array(
        'page_id' => '#icon-' . $section
        ,'icon_url' => $page_icon
      );
    ?>

    <?php $page_icon = $section; ?>

  <?php elseif (isset($icon)) : ?>

    <?php $page_icon = $icon; ?>

  <?php else: ?>

    <?php $page_icon = null; ?>

  <?php endif;?>


  <?php if ($page_icon) screen_icon($page_icon); ?>

  <?php if (isset($single_line) && !$single_line && isset($title)): ?>

    <h2><?php echo esc_html($title); ?></h2>

  <?php else: ?>

    <?php if (!empty($tabs) && count($tabs) > 1): ?>

      <h2 class="nav-tab-wrapper piklist-single-line-tab">

      <?php echo (isset($title)) ? $title : ''; ?>

    <?php elseif (isset($title)): ?>

      <h2><?php echo esc_html($title); ?></h2>

    <?php endif;?>

  <?php endif;?>
        
  <?php echo (isset($single_line) && !$single_line) ? '</h2>' : ''; ?>

  <?php if (!empty($tabs) && count($tabs) > 1): ?>

    <?php echo (isset($single_line) && !$single_line) ? '<h2 class="nav-tab-wrapper">' : ''; ?>
            
      <?php 
        foreach ($tabs as $tab)
        {
          parse_str($_SERVER['QUERY_STRING'], $url_defaults);

          foreach (array('message', 'paged') as $variable)
          {
            unset($url_defaults[$variable]);
          }
          
          $url = array_merge(
                  $url_defaults
                  ,array(
                    'page' => $_REQUEST['page']
                    ,'tab' => isset($tab['page']) ? $tab['page'] : false
                  )
                );   
          ?><a class="nav-tab <?php echo (isset($tab['page']) && (isset($_REQUEST['tab'])) && ($_REQUEST['tab'] == $tab['page'])) || (!isset($_REQUEST['tab']) && !isset($tab['page'])) ? 'nav-tab-active' : null; ?>" href="?<?php echo http_build_query(array_filter($url)); ?>"><?php echo esc_html($tab['title']); ?></a><?php 
        }
      ?>

    </h2>

  <?php elseif ($title): ?>
    
    </h2>
    
  <?php endif; ?>
  
  <?php 
    foreach ($page_sections as $page_section):
      if ($page_section['position'] == 'before' 
          && ((empty($page_section['tab']) && !isset($_REQUEST['tab'])) 
              || (!empty($page_section['tab']) && piklist('dashes', $page_section['tab']) == $_REQUEST['tab'])
              )
          ):
        piklist($page_section['part']);
      endif;
    endforeach;
  ?>

  <?php if (isset($setting) && !empty($setting)): ?>

    <?php if ($save): ?>
      
      <?php if ($notice): ?>
  
        <?php settings_errors(); ?>
      
      <?php endif; ?>
      
      <form action="<?php echo admin_url('options.php'); ?>" method="post" enctype="multipart/form-data">

        <?php settings_fields($setting); ?>

        <?php do_action('piklist_pre_render_settings_form'); ?>

    <?php endif; ?>

      <?php do_settings_sections($setting); ?>

    <?php if ($save): ?>
    
        <?php do_action('piklist_post_render_settings_form'); ?>
        
        <?php do_action('piklist_settings_form'); ?>
       
        <?php submit_button(esc_html($save_text)); ?>
         
      </form>

    <?php endif; ?>
  
  <?php endif; ?>
  
  <?php 
    foreach ($page_sections as $page_section):
      if ($page_section['position'] != 'before' 
          && ((empty($page_section['tab']) && !isset($_REQUEST['tab'])) 
              || (!empty($page_section['tab']) && piklist('dashes', $page_section['tab']) == $_REQUEST['tab'])
              )
          ):
        piklist($page_section['part']);
      endif;
    endforeach;
  ?>
  
</div>
