<?php
/**
 * BuddyPress - Groups Requests Loop
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */

?>

<?php if ( bp_group_has_membership_requests( bp_ajax_querystring( 'membership_requests' ) ) ) : ?>

	<div id="pag-top" class="pagination">

		<div class="pag-count" id="group-mem-requests-count-top">

			<?php bp_group_requests_pagination_count(); ?>

		</div>

		<div class="pagination-links" id="group-mem-requests-pag-top">

			<?php bp_group_requests_pagination_links(); ?>

		</div>

	</div>

	<ul id="request-list" class="item-list">
		<?php while ( bp_group_membership_requests() ) : bp_group_the_membership_request(); ?>

			<li>
				<?php bp_group_request_user_avatar_thumb(); ?>
				<h4><?php bp_group_request_user_link(); ?> <span class="comments"><?php bp_group_request_comment(); ?></span></h4>
				<span class="activity"><?php bp_group_request_time_since_requested(); ?></span>

				<?php

				/**
				 * Fires inside the groups membership request list loop.
				 *
				 * @since 1.1.0
				 */
				do_action( 'bp_group_membership_requests_admin_item' ); ?>

				<div class="action">

					<?php bp_button( array( 'id' => 'group_membership_accept', 'component' => 'groups', 'wrapper_class' => 'accept', 'link_href' => bp_get_group_request_accept_link(), 'link_title' => __( 'Accept', 'buddypress' ), 'link_text' => __( 'Accept', 'buddypress' ) ) ); ?>

					<?php bp_button( array( 'id' => 'group_membership_reject', 'component' => 'groups', 'wrapper_class' => 'reject', 'link_href' => bp_get_group_request_reject_link(), 'link_title' => __( 'Reject', 'buddypress' ), 'link_text' => __( 'Reject', 'buddypress' ) ) ); ?>

					<?php

					/**
					 * Fires inside the list of membership request actions.
					 *
					 * @since 1.1.0
					 */
					do_action( 'bp_group_membership_requests_admin_item_action' ); ?>

				</div>
			</li>

		<?php endwhile; ?>
	</ul>

	<div id="pag-bottom" class="pagination">

		<div class="pag-count" id="group-mem-requests-count-bottom">

			<?php bp_group_requests_pagination_count(); ?>

		</div>

		<div class="pagination-links" id="group-mem-requests-pag-bottom">

			<?php bp_group_requests_pagination_links(); ?>

		</div>

	</div>

	<?php else: ?>

		<div id="message" class="info">
			<p><?php _e( 'There are no pending membership requests.', 'buddypress' ); ?></p>
		</div>

	<?php endif; ?>
