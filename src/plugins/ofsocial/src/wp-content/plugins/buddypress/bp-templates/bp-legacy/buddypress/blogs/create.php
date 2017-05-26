<?php
/**
 * BuddyPress - Blogs Create
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */

/**
 * Fires at the top of the blog creation template file.
 *
 * @since 1.6.0
 */
do_action( 'bp_before_create_blog_content_template' ); ?>

<?php

/** This action is documented in bp-templates/bp-legacy/buddypress/activity/index.php */
do_action( 'template_notices' ); ?>

<?php

/**
 * Fires before the display of the blog creation form.
 *
 * @since 1.1.0
 */
do_action( 'bp_before_create_blog_content' ); ?>

<?php if ( bp_blog_signup_enabled() ) : ?>

	<?php bp_show_blog_signup_form(); ?>

<?php else: ?>

	<div id="message" class="info">
		<p><?php _e( 'Site registration is currently disabled', 'buddypress' ); ?></p>
	</div>

<?php endif; ?>

<?php

/**
 * Fires after the display of the blog creation form.
 *
 * @since 1.1.0
 */
do_action( 'bp_after_create_blog_content' ); ?>

<?php

/**
 * Fires at the bottom of the blog creation template file.
 *
 * @since 1.6.0
 */
do_action( 'bp_after_create_blog_content_template' ); ?>
