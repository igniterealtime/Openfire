<?php
/*
Title: Image Sizes
Post Type: piklist_wp_helpers_post
Priority: normal
Capability: default-none
Order: 1
*/

?>

<?php $attachment_id = $post->ID; ?>

<?php $sizes = get_intermediate_image_sizes(); ?>

<?php if(!empty($sizes)) : ?>

	<?php foreach ($sizes as $size => $type) : ?>

		  <ul>

		    <?php $image_attributes = wp_get_attachment_image_src($attachment_id, $type);?>

		    <li>

		    	<?php echo '<strong>' . ucwords(str_replace(array('-','_'), ' ', $type)) . ': </strong>'; ?>
		     
		      <?php echo  __('Width', 'wp-helpers') . ': ' . $image_attributes[1] . 'px'; ?>

		      <?php echo  __('Height', 'wp-helpers') . ': ' . $image_attributes[2] . 'px'; ?>

		    </li>

		    <li>

		      <input type="text" readonly="readonly" class="large-text" value="<?php echo $image_attributes[0]; ?>">

		    </li>

		  </ul>

	<?php endforeach; ?>

<?php endif;