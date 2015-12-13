/* global bp_activity_admin_vars, postboxes, wpAjax */
(function( $ ) {

/**
 * Activity reply object for the activity index screen
 *
 * @since 1.6.0
 */
var activityReply = {

	/**
	 * Attach event handler functions to the relevant elements.
	 *
	 * @since 1.6.0
	 */
	init : function() {
		$(document).on( 'click', '.row-actions a.reply',              activityReply.open );
		$(document).on( 'click', '#bp-activities-container a.cancel', activityReply.close );
		$(document).on( 'click', '#bp-activities-container a.save',   activityReply.send );

		// Close textarea on escape
		$(document).on( 'keyup', '#bp-activities:visible', function( e ) {
			if ( 27 === e.which ) {
				activityReply.close();
			}
		});
	},

	/**
	 * Reveals the entire row when "reply" is pressed.
	 *
	 * @since 1.6.0
	 */
	open : function() {
		// Hide the container row, and move it to the new location
		var box = $( '#bp-activities-container' ).hide();
		$( this ).parents( 'tr' ).after( box );

		// Fade the whole row in, and set focus on the text area.
		box.fadeIn( '300' );
		$( '#bp-activities' ).focus();

		return false;
	},

	/**
	 * Hide and reset the entire row when "cancel", or escape, are pressed.
	 *
	 * @since 1.6.0
	 */
	close : function() {
		// Hide the container row
		$('#bp-activities-container').fadeOut( '200', function () {

			// Empty and unfocus the text area
			$( '#bp-activities' ).val( '' ).blur();

			// Remove any error message and disable the spinner
			$( '#bp-replysubmit .error' ).html( '' ).hide();
			$( '#bp-replysubmit .waiting' ).hide();
		});

		return false;
	},

	/**
	 * Submits "form" via AJAX back to WordPress.
	 *
	 * @since 1.6.0
	 */
	send : function() {
		// Hide any existing error message, and show the loading spinner
		$( '#bp-replysubmit .error' ).hide();
		$( '#bp-replysubmit .waiting' ).show();

		// Grab the nonce
		var reply = {};
		reply['_ajax_nonce-bp-activity-admin-reply'] = $( '#bp-activities-container input[name="_ajax_nonce-bp-activity-admin-reply"]' ).val();

		// Get the rest of the data
		reply.action    = 'bp-activity-admin-reply';
		reply.content   = $( '#bp-activities' ).val();
		reply.parent_id = $( '#bp-activities-container' ).prev().data( 'parent_id' );
		reply.root_id   = $( '#bp-activities-container' ).prev().data( 'root_id' );

		// Make the AJAX call
		$.ajax({
			data    : reply,
			type    : 'POST',
			url     : ajaxurl,

			// Callbacks
			error   : function( r ) { activityReply.error( r ); },
			success : function( r ) { activityReply.show( r ); }
		});

		return false;
	},

	/**
	 * send() error message handler
	 *
	 * @since 1.6.0
	 */
	error : function( r ) {
		var er = r.statusText;
		$('#bp-replysubmit .waiting').hide();

		if ( r.responseText ) {
			er = r.responseText.replace( /<.[^<>]*?>/g, '' );
		}

		if ( er ) {
			$('#bp-replysubmit .error').html( er ).show();
		}
	},

	/**
	 * send() success handler
	 *
	 * @since 1.6.0
	 */
	show : function ( xml ) {
		var bg, id, response;

		// Handle any errors in the response
		if ( typeof( xml ) === 'string' ) {
			activityReply.error( { 'responseText': xml } );
			return false;
		}

		response = wpAjax.parseAjaxResponse( xml );
		if ( response.errors ) {
			activityReply.error( { 'responseText': wpAjax.broken } );
			return false;
		}
		response = response.responses[0];

		// Close and reset the reply row, and add the new Activity item into the list.
		$('#bp-activities-container').fadeOut( '200', function () {

			// Empty and unfocus the text area
			$( '#bp-activities' ).val( '' ).blur();

			// Remove any error message and disable the spinner
			$( '#bp-replysubmit .error' ).html( '' ).hide();
			$( '#bp-replysubmit .waiting' ).hide();

			// Insert new activity item
			$( '#bp-activities-container' ).before( response.data );

			// Get background colour and animate the flash
			id = $( '#activity-' + response.id );
			bg = id.closest( '.widefat' ).css( 'backgroundColor' );
			id.animate( { 'backgroundColor': '#CEB' }, 300 ).animate( { 'backgroundColor': bg }, 300 );
		});
	}
};

$(document).ready( function () {
	// Create the Activity reply object after domready event
	activityReply.init();

	// On the edit screen, unload the close/open toggle js for the action & content metaboxes
	$( '#bp_activity_action h3, #bp_activity_content h3' ).unbind( 'click' );

	// redo the post box toggles to reset the one made by comment.js in favor
	// of activity administration page id so that metaboxes are still collapsible
	// in single Activity Administration screen.
	if ( typeof postboxes !== 'undefined' ) {
		postboxes.add_postbox_toggles( bp_activity_admin_vars.page );
	}
});

})(jQuery);
