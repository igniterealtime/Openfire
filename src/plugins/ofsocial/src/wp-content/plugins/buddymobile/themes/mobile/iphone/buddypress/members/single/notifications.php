<?php

/**
 * BuddyPress - Users Notifications
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */

?>

		<ul class="options-nav">

				<?php bp_get_options_nav(); ?>
				<?php do_action( 'bp_member_plugin_options_nav' ); ?>

			</ul>
		</div> <!-- .item-list-tabs -->


<?php
switch ( bp_current_action() ) :

	// Unread
	case 'unread' :
		bp_get_template_part( 'members/single/notifications/unread' );
		break;

	// Read
	case 'read' :
		bp_get_template_part( 'members/single/notifications/read' );
		break;

	// Any other
	default :
		bp_get_template_part( 'members/single/plugins' );
		break;
endswitch;
