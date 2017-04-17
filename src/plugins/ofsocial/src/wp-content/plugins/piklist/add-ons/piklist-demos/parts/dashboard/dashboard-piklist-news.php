<?php
/*
Title: Piklist News
Capability: manage_options
Network: true
*/

include_once( ABSPATH . WPINC . '/feed.php' );

$rss = fetch_feed('http://piklist.com/feed/');

if (!is_wp_error($rss)) :

    $maxitems = $rss->get_item_quantity(5); 

    $rss_items = $rss->get_items(0, $maxitems);

endif;
?>

<div class="rss-widget">

  <ul>

      <?php if ($maxitems == 0) : ?>

          <li>

            <?php _e('No items', 'piklist-demo'); ?>

          </li>

      <?php else : ?>

          <?php foreach ($rss_items as $item) : ?>

              <?php $title = esc_html($item->get_title()); ?>

              <?php $date = date_i18n(get_option('date_format'), $item->get_date('U')); ?>

              <?php
                $description = str_replace(array("\n", "\r"), ' ', esc_attr(strip_tags( @html_entity_decode($item->get_description(), ENT_QUOTES, get_option('blog_charset')))));
                $description = wp_html_excerpt( $description, 360 );

                if ('[...]' == substr( $description, -5 ))
                {
                  $description = substr($description, 0, -5) . '[&hellip;
                  ]';
                }
                elseif ('[&hellip;]' != substr($description, -10 ))
                {
                  $description .= ' [&hellip;]';
                }                        

                $description = esc_html( $description );
              ?>

              <?php
                $link = $item->get_link();
                while (stristr($link, 'http') != $link)
                {
                  $link = substr($link, 1);
                }
                  $link = esc_url(strip_tags($link));
              ?>

              <li>

                <a class='rsswidget' href='<?php echo esc_url($link); ?>' title='<?php echo $description;?>'>
                  <?php echo esc_html($title); ?>
                </a>

                <span class="rss-date">
                  <?php echo esc_html($date); ?>
                </span>

                <div class="rss-summary">
                  <?php echo esc_html($description); ?>
                </div>

              </li>

          <?php endforeach; ?>

      <?php endif; ?>
      
  </ul>

</div>

<hr>

<?php
  
  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Dashboard Widget'
  ));

?>