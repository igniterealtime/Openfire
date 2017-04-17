/* global BP_Group_Admin, group_id, isRtl */

(function($) {
	function add_member_to_list( e, ui ) {
		$('#bp-groups-new-members-list').append('<li data-login="' + ui.item.value + '"><a href="#" class="bp-groups-remove-new-member">x</a> ' + ui.item.label + '</li>');
	}

	var id = 'undefined' !== typeof group_id ? '&group_id=' + group_id : '';
	$(document).ready( function() {
		window.warn_on_leave = false;

		/* Initialize autocomplete */
		$( '.bp-suggest-user' ).autocomplete({
			source:    ajaxurl + '?action=bp_group_admin_member_autocomplete' + id,
			delay:     500,
			minLength: 2,
			position:  ( 'undefined' !== typeof isRtl && isRtl ) ? { my: 'right top', at: 'right bottom', offset: '0, -1' } : { offset: '0, -1' },
			open:      function() { $(this).addClass('open'); },
			close:     function() { $(this).removeClass('open'); $(this).val(''); },
			select:    function( event, ui ) { add_member_to_list( event, ui ); }
		});

		/* Replace noscript placeholder */
		$( '#bp-groups-new-members' ).prop( 'placeholder', BP_Group_Admin.add_member_placeholder );

		/* Remove a member on 'x' click */
		$( '#bp_group_add_members' ).on( 'click', '.bp-groups-remove-new-member', function( e ) {
			e.preventDefault();
			$( e.target.parentNode ).remove();
		} );

		/* Warn before leaving unsaved changes */
		$(document).on( 'change', 'input#bp-groups-name, input#bp-groups-description, select.bp-groups-role, #bp-groups-settings-section-status input[type="radio"]', function() {
			window.warn_on_leave = true;
		});

		$( 'input#save' ).on( 'click', function() {
			/* Check for users to add */
			var users_to_add = [];

			$( '#bp-groups-new-members-list li' ).each( function() {
				users_to_add.push( $(this).data('login' ) );
			} );

			/* There are users to add, include a comma separated list of users login in the main field */
			if ( users_to_add.length ) {
				$( '#bp-groups-new-members' ).val( '' ).val( users_to_add.join( ', ' ) );
			}

			window.warn_on_leave = false;
		});

		window.onbeforeunload = function() {
			if ( window.warn_on_leave ) {
				return BP_Group_Admin.warn_on_leave;
			}
		};
	});
})(jQuery);
