<?php
/**
 * BuddyPress - Users Plugins Template
 *
 * 3rd-party plugins should use this template to easily add template
 * support to their plugins for the members component.
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */
?>
			<ul class="options-nav">
			<?php if ( ! bp_is_current_component_core() ) : ?>
				<?php bp_get_options_nav(); ?>
				<?php do_action( 'bp_member_plugin_options_nav' ); ?>
			<?php endif; ?>
			</ul>
		</div> <!-- .item-list-tabs -->

	<div id="item-body">

		<div class="padder">
			<h3><?php do_action( 'bp_template_title' ); ?></h3>

			<?php do_action( 'bp_template_content' ); ?>
		</div>
