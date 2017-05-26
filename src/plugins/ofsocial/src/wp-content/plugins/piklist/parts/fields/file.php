<?php
  $options = array_merge(
              array(
                'button' =>'Add Media'
                ,'modal_title' =>'Add Media'
                ,'basic' => false
                ,'preview_size' => 'thumbnail'
                ,'textarea_rows' => 5
              )
              ,isset($options) && is_array($options) ? $options : array()
            );  
?>

<div class="piklist-field-part">
  
  <?php if ($options['basic'] || !is_admin()): ?>

    <input 
      type="file"
      id="<?php echo piklist_form::get_field_id($field, $scope, $index, $prefix); ?>" 
      name="<?php echo piklist_form::get_field_name($field, $scope, $index, $prefix, $multiple); ?>"
      <?php echo piklist_form::attributes_to_string($attributes); ?>
    />
  
  <?php else: ?>

    <a 
      href="#" 
      class="button piklist-upload-file-button piklist-field-part"
      title="<?php _e($options['modal_title']); ?>"
    >
      <?php _e($options['button']); ?>
    </a>
  
  <?php endif; ?>

  <div class="piklist-upload-file-preview piklist-field-preview <?php echo $errors ? 'piklist-error' : null; ?>">
  
    <?php
      if (empty($value)):
    ?>

        <input 
          type="hidden" 
          id="<?php echo piklist_form::get_field_id($field, $scope, $index, $prefix); ?>" 
          name="<?php echo piklist_form::get_field_name($field, $scope, $index, $prefix, $multiple); ?>"
          <?php echo piklist_form::attributes_to_string($attributes); ?>
        />

    <?php
      else:
        $value = is_array($value) ? $value : array($value);
        for ($_index = 0; $_index < count($value); $_index++):
    ?>

          <input 
            type="hidden" 
            id="<?php echo piklist_form::get_field_id($field, $scope, $_index, $prefix); ?>" 
            name="<?php echo piklist_form::get_field_name($field, $scope, $index, $prefix, $multiple); ?>"
            value="<?php echo esc_attr($value[$_index]); ?>" 
            <?php echo piklist_form::attributes_to_string($attributes); ?>
          />

    <?php 
        endfor;
      endif;
    ?>
  
    <ul class="attachments">

      <?php 
        $value = is_array($value) ? $value : array($value);
        if (!empty($value)):
          foreach ($value as $attachment_id): 
            $mime_type = get_post_mime_type($attachment_id);
            if ($mime_type):
              if (in_array($mime_type, array('image/jpeg', 'image/png', 'image/gif'))):
                $image = wp_get_attachment_image_src($attachment_id, $options['preview_size'], false, true);
      ?>

                <li class="attachment selected">
                   <div class="attachment-preview <?php echo (int) $image[1] > (int) $image[2] ? 'landscape' : 'portrait'; ?>">
                     <div class="centered">
                       <a href="#">
                        <img src="<?php echo $image[0]; ?>" />
                      </a>
                     </div>
                     <a class="check" href="#" title="Deselect" data-attachment-id="<?php echo $attachment_id; ?>" data-attachments="<?php echo piklist_form::get_field_name($field, $scope, $index, $prefix, $multiple); ?>"><div class="media-modal-icon"></div><span><?php _e('Remove'); ?></span></a>
                   </div>
                 </li>

      <?php
              else:
                $attachment_path = get_attached_file($attachment_id);
                $file_type = wp_check_filetype($attachment_path, wp_get_mime_types());

                $icon = includes_url() . 'images/media/' . substr($file_type['type'], 0, strpos($file_type['type'], '/')) . '.png';
                $icon = file_exists($icon) ? $icon : includes_url() . 'images/media/document.png';
      ?>
    
                <li class="attachment selected">
                   <div class="attachment-preview attachment-preview-document landscape type-<?php echo substr($file_type['type'], 0, strpos($file_type['type'], '/')); ?> subtype-<?php echo substr($file_type['type'], strpos($file_type['type'], '/') + 1); ?>">
                     <div class="thumbnail">
                       <div class="centered">
                          <img src="<?php echo $icon; ?>" class="icon" />
                       </div>
                       <div class="filename">
                         <div><?php echo basename($attachment_path); ?></div>
                       </div>
                     </div>
                     <a class="check" href="#" title="Deselect" data-attachment-id="<?php echo $attachment_id; ?>" data-attachments="<?php echo piklist_form::get_field_name($field, $scope, $index, $prefix, $multiple); ?>"><div class="media-modal-icon"></div><span><?php _e('Remove'); ?></span></a>
                   </div>
                 </li>
    
      <?php
              endif;
            endif;
          endforeach;
        endif;
      ?>

    </ul>

  </div>
  
</div>