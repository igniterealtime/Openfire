<?php

/**
 * BuddyMobile - Link Page
 *
 * This template displays a list of links
 *
 * @package BuddyMobile
 *
 */

?>

<?php get_header() ?>

	<div id="content">

		<div id="blog-latest" class="page">

			<h2 class="pagetitle"><?php _e( 'Links', 'BuddyMobile' ) ?></h2>

			<ul id="links-list">
				<?php get_links_list(); ?>
			</ul>

		</div>

	</div>

<?php get_footer(); ?>