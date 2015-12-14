<?php
/*
Title: Exif Data
Post Type: piklist_wp_helpers_post
Priority: normal
Capability: default-none
Order: 5
*/
?>

<?php $meta = wp_get_attachment_metadata(); ?>

<?php if($meta) : ?>

	<?php $exif_data = $meta['image_meta']; ?>

	<?php if($exif_data) : ?>

		<ul>

		<?php foreach ($exif_data as $exif => $value) : ?>

			<li>

				<strong>

					<?php echo ucwords(str_replace('_', ' ', $exif)); ?>

				</strong>

				<?php echo ': ' . ucwords(str_replace('_', ' ', $value)); ?>

			</li>

		<?php endforeach; ?>

		</ul>

	<?php endif; ?>

<?php endif;