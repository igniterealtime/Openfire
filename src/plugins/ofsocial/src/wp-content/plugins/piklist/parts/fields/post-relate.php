
<!-- TODO: Show Associated -->
<!-- TODO: Remove Relationships if unset on form element -->

<ul class="wp-tab-bar">
  <li class="wp-tab-active"><a href="#"><?php _e('Most Recent', 'piklist'); ?></a></li>
  <li><a href="#"><?php _e('All', 'piklist'); ?></a></li>
</ul>

<div class="wp-tab-panel">

  <?php

    $query = new WP_Query(array(
      'post_type' => $scope
      ,'post_belongs' => $post->ID
      ,'posts_per_page' => -1
      ,'suppress_filters' => false
    ));

    $values = empty($query->posts) ? null : piklist(
      $query->posts
      ,'ID'
    );
  
    piklist('field', array(
      'type' => 'checkbox'
      ,'scope' => 'relate'
      ,'field' => $scope . '_post_id'
      ,'value' => $values
      ,'template' => 'field'
      ,'choices' => piklist(
        get_posts(array(
          'post_type' => $scope
          ,'numberposts' => 15
          ,'orderby' => 'date'
          ,'order' => 'DESC'
        ))
        ,array('ID', 'post_title')
      )
      ,'attributes' => array(
        'class' => $scope . '_post_id'
      )
    ));
  ?>

</div>

<div class="wp-tab-panel">
  
  <?php
    piklist('field', array(
      'type' => 'checkbox'
      ,'scope' => 'relate'
      ,'field' => $scope . '_post_id'
      ,'value' => $values
      ,'template' => 'field'
      ,'choices' => piklist(
        get_posts(array(
          'post_type' => $scope
          ,'numberposts' => -1
          ,'orderby' => 'title'
          ,'order' => 'ASC'
        ))
        ,array('ID', 'post_title')
      )
      ,'attributes' => array(
        'class' => $scope . '_post_id'
      )
    ));
  ?>

  <!-- TODO: ADD Pagination -->
  
</div>

<!-- TODO: ADD Ajax Search -->

<?php
  piklist('field', array(
    'type' => 'hidden'
    ,'scope' => 'relate'
    ,'field' => $scope . '_relate'
    ,'value' => 'has'
  ));

  piklist('field', array(
    'type' => 'hidden'
    ,'scope' => 'relate'
    ,'field' => $scope . '_relate_remove'
    ,'attributes' => array(
      'class' => $scope . '_relate_remove'
    )
  ));
  
  wp_reset_postdata();
?>

<script type="text/javascript">
  
  // TODO: Put in piklist-admin.js after testing
  (function($)
  {
    $(document).ready(function()
    {
      $('.<?php echo $scope . '_post_id'; ?>').click(function(event)
      {        
        event.stopPropagation();
        
        var ids = $('.<?php echo $scope . '_relate_remove'; ?>').val().split(','),
          current_value = $(this).val();

        $(':input.<?php echo $scope . '_post_id'; ?>[value="' + current_value + '"]').prop('checked', $(this).is(':checked'));
        
        if ($(this).is(':checked'))
        {        
          var _ids = $.grep(ids, function(value, key)
                     { 
                       return value != current_value && value; 
                     });
        }
        else
        {
          var _ids = $('.<?php echo $scope . '_relate_remove'; ?>').val().split(',');
          _ids.push(current_value);
        }
        
        _ids = _ids.filter(function(value) 
              {
                return value;
              }).join(',');
        
        $('.<?php echo $scope . '_relate_remove'; ?>').val(_ids);        
      });
    });
  })(jQuery);

</script>