<?php

/**
 * BuddyPress - Users Header
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */

?>

<div id="item-header-avatar">
	<a href="<?php bp_displayed_user_link(); ?>">

		<?php bp_displayed_user_avatar( 'type=thumb' ); ?>

	</a>
</div><!-- #item-header-avatar -->

<div id="item-header-content">

	<h2 class="user-nicename">@<?php bp_displayed_user_username(); ?></h2>
	<span class="activity"><?php bp_last_activity( bp_displayed_user_id() ); ?></span>


	<div id="item-meta">

		<div id="item-buttons">

			<?php do_action( 'bp_member_header_actions' ); ?>

		</div><!-- #item-buttons -->

		<?php do_action( 'bp_profile_header_meta' ); ?>

	</div><!-- #item-meta -->

</div><!-- #item-header-content -->