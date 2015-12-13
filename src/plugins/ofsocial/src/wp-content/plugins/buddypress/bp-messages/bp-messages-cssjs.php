<?php
/**
 * BuddyPress Messages CSS and JS.
 *
 * @package BuddyPress
 * @subpackage MessagesScripts
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Enqueue the JS for messages autocomplete.
 */
function messages_add_autocomplete_js() {

	// Include the autocomplete JS for composing a message.
	if ( bp_is_messages_component() && bp_is_current_action( 'compose' ) ) {
		add_action( 'wp_head', 'messages_autocomplete_init_jsblock' );

		wp_enqueue_script( 'bp-jquery-autocomplete' );
		wp_enqueue_script( 'bp-jquery-autocomplete-fb' );
		wp_enqueue_script( 'bp-jquery-bgiframe' );
		wp_enqueue_script( 'bp-jquery-dimensions' );
	}
}
add_action( 'bp_enqueue_scripts', 'messages_add_autocomplete_js' );

/**
 * Enqueue the CSS for messages autocomplete.
 *
 * @todo Why do we call wp_print_styles()?
 */
function messages_add_autocomplete_css() {
	if ( bp_is_messages_component() && bp_is_current_action( 'compose' ) ) {
		wp_enqueue_style( 'bp-messages-autocomplete' );
		wp_print_styles();
	}
}
add_action( 'wp_head', 'messages_add_autocomplete_css' );

/**
 * Print inline JS for initializing the messages autocomplete.
 *
 * @todo Why is this here and not in a properly enqueued file?
 */
function messages_autocomplete_init_jsblock() {
?>

	<script type="text/javascript">
		jQuery(document).ready(function() {
			var acfb = jQuery("ul.first").autoCompletefb({urlLookup: ajaxurl});

			jQuery('#send_message_form').submit( function() {
				var users = document.getElementById('send-to-usernames').className;
				document.getElementById('send-to-usernames').value = String(users);
			});
		});
	</script>

<?php
}
