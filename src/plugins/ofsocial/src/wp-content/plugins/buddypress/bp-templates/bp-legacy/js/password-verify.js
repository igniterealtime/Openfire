/* jshint undef: false */
/* Password Verify */
( function( $ ){
	function check_pass_strength() {
		var pass1 = $( '.password-entry' ).val(),
		    pass2 = $( '.password-entry-confirm' ).val(),
		    strength;

		// Reset classes and result text
		$( '#pass-strength-result' ).removeClass( 'short bad good strong' );
		if ( ! pass1 ) {
			$( '#pass-strength-result' ).html( pwsL10n.empty );
			return;
		}

		strength = wp.passwordStrength.meter( pass1, wp.passwordStrength.userInputBlacklist(), pass2 );

		switch ( strength ) {
			case 2:
				$( '#pass-strength-result' ).addClass( 'bad' ).html( pwsL10n.bad );
				break;
			case 3:
				$( '#pass-strength-result' ).addClass( 'good' ).html( pwsL10n.good );
				break;
			case 4:
				$( '#pass-strength-result' ).addClass( 'strong' ).html( pwsL10n.strong );
				break;
			case 5:
				$( '#pass-strength-result' ).addClass( 'short' ).html( pwsL10n.mismatch );
				break;
			default:
				$( '#pass-strength-result' ).addClass( 'short' ).html( pwsL10n['short'] );
				break;
		}
	}

	// Bind check_pass_strength to keyup events in the password fields
	$( document ).ready( function() {
		$( '.password-entry' ).val( '' ).keyup( check_pass_strength );
		$( '.password-entry-confirm' ).val( '' ).keyup( check_pass_strength );
	});

} )( jQuery );
