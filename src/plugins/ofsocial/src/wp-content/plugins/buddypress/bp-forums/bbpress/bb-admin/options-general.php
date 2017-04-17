<?php

require_once('admin.php');

if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) && $_POST['action'] == 'update' ) {
	
	bb_check_admin_referer( 'options-general-update' );
	
	foreach ( (array) $_POST as $option => $value ) {
		if ( !in_array( $option, array( '_wpnonce', '_wp_http_referer', 'action', 'submit' ) ) ) {
			$option = trim( $option );
			$value = is_array( $value ) ? $value : trim( $value );
			$value = stripslashes_deep( $value );
			if ( $option == 'uri' && !empty( $value ) ) {
				$value = rtrim( $value, " \t\n\r\0\x0B/" ) . '/';
			}
			if ( $value ) {
				bb_update_option( $option, $value );
			} else {
				bb_delete_option( $option );
			}
		}
	}
	
	$goback = add_query_arg( 'updated', 'true', wp_get_referer() );
	bb_safe_redirect( $goback );
	exit;
}

if ( !empty( $_GET['updated'] ) ) {
	bb_admin_notice( __( '<strong>Settings saved.</strong>' ) );
}

$general_options = array(
	'name' => array(
		'title' => __( 'Site title' ),
		'class' => 'long',
	),
	'description' => array(
		'title' => __( 'Tagline' ),
		'class' => 'long',
		'note' => __( 'In a few words, explain what this site is about.' )
	),
	'uri' => array(
		'title' => __( 'bbPress address (URL)' ),
		'class' => array('long', 'code'),
		'note' => __( 'The full URL of your bbPress install.' ),
	),
	'from_email' => array(
		'title' => __( 'E-mail address' ),
		'note' => __( 'This address is used for admin purposes, like new user notification.' ),
	)
);

$time_options = array(
	'gmt_offset' => array(
		'title' => __( 'Time zone' ),
		'type' => 'select',
		'options' => array(
			'-12'   => '-12:00',
			'-11.5' => '-11:30',
			'-11'   => '-11:00',
			'-10.5' => '-10:30',
			'-10'   => '-10:00',
			'-9.5'  => '-9:30',
			'-9'    => '-9:00',
			'-8.5'  => '-8:30',
			'-8'    => '-8:00',
			'-7.5'  => '-7:30',
			'-7'    => '-7:00',
			'-6.5'  => '-6:30',
			'-6'    => '-6:00',
			'-5.5'  => '-5:30',
			'-5'    => '-5:00',
			'-4.5'  => '-4:30',
			'-4'    => '-4:00',
			'-3.5'  => '-3:30',
			'-3'    => '-3:00',
			'-2.5'  => '-2:30',
			'-2'    => '-2:00',
			'-1.5'  => '-1:30',
			'-1'    => '-1:00',
			'-0.5'  => '-0:30',
			'0'     => '',
			'0.5'   => '+0:30',
			'1'     => '+1:00',
			'1.5'   => '+1:30',
			'2'     => '+2:00',
			'2.5'   => '+2:30',
			'3'     => '+3:00',
			'3.5'   => '+3:30',
			'4'     => '+4:00',
			'4.5'   => '+4:30',
			'5'     => '+5:00',
			'5.5'   => '+5:30',
			'5.75'  => '+5:45',
			'6'     => '+6:00',
			'6.5'   => '+6:30',
			'7'     => '+7:00',
			'7.5'   => '+7:30',
			'8'     => '+8:00',
			'8.5'   => '+8:30',
			'8.75'  => '+8:45',
			'9'     => '+9:00',
			'9.5'   => '+9:30',
			'10'    => '+10:00',
			'10.5'  => '+10:30',
			'11'    => '+11:00',
			'11.5'  => '+11:30',
			'12'    => '+12:00',
			'12.75' => '+12:45',
			'13'    => '+13:00',
			'13.75' => '+13:45',
			'14'    => '+14:00'
		),
		'after' => __( 'hours' )
	),
	'datetime_format' => array(
		'title' => __( 'Date and time format' ),
		'class' => 'short',
		'value' => bb_get_datetime_formatstring_i18n(),
		'after' => bb_datetime_format_i18n( bb_current_time() ),
		'note' => array(
			__( '<a href="http://codex.wordpress.org/Formatting_Date_and_Time">Documentation on date formatting</a>.' ),
			__( 'Click "Save Changes" to update sample output.' )
		)
	),
	'date_format' => array(
		'title' => __( 'Date format' ),
		'class' => 'short',
		'value' => bb_get_datetime_formatstring_i18n( 'date' ),
		'after' => bb_datetime_format_i18n( bb_current_time(), 'date' )
	)
);

if ( !$gmt_offset = bb_get_option( 'gmt_offset' ) ) {
	$gmt_offset = 0;
}

if ( wp_timezone_supported() ) {
	unset( $time_options['gmt_offset'] );

	if ( !$timezone_string = bb_get_option( 'timezone_string' ) ) {
		// set the Etc zone if no timezone string exists
		$_gmt_offset = (integer) round( $gmt_offset );
		if ( $_gmt_offset === 0 ) {
			$timezone_string = 'Etc/UTC';
		} elseif ( $_gmt_offset > 0 ) {
			// Zoneinfo has these signed backwards to common convention
			$timezone_string = 'Etc/GMT-' . abs( $_gmt_offset );
		} else {
			// Zoneinfo has these signed backwards to common convention
			$timezone_string = 'Etc/GMT+' . abs( $_gmt_offset );
		}
		unset( $_gmt_offset );
	}

	// Build the new selector
	$_time_options = array(
		'timezone_string' => array(
			'title' => __( 'Time zone' ),
			'type' => 'select',
			'options' => wp_timezone_choice( $timezone_string ), // This passes a string of html, which gets used verbatim
			'note' => array(
				__( 'Choose a city in the same time zone as you.' ),
				sprintf( __( '<abbr title="Coordinated Universal Time">UTC</abbr> time is <code>%s</code>' ), bb_gmdate_i18n( bb_get_datetime_formatstring_i18n(), bb_current_time() ) ),
				sprintf( __( 'Local time is <code>%s</code>' ), bb_datetime_format_i18n( bb_current_time() ) )
			)
		)
	);

	$_now = localtime( bb_current_time(), true );
	if ( $now['tm_isdst'] ) {
		$_time_options['timezone_string']['note'][] = __( 'This time zone is currently in daylight savings time.' );
	} else {
		$_time_options['timezone_string']['note'][] = __( 'This time zone is currently in standard time.' );
	}

	if ( function_exists( 'timezone_transitions_get' ) ) {
		$timezone_object = new DateTimeZone( $timezone_string );
		$found_transition = false;
		foreach ( timezone_transitions_get( $timezone_object ) as $timezone_transition ) {
			if ( $timezone_transition['ts'] > time() ) {
				$note = $timezone_transition['isdst'] ? __('Daylight savings time begins on <code>%s</code>') : __('Standard time begins on <code>%s</code>');
				$_time_options['timezone_string']['note'][] = sprintf( $note, bb_gmdate_i18n( bb_get_datetime_formatstring_i18n(), $timezone_transition['ts'], false ) );
				break;
			}
		}
	}

	$time_options = array_merge( $_time_options, $time_options );

} else {
	// Tidy up the old style dropdown
	$time_options['gmt_offset']['note'] = array(
		1 => sprintf( __( '<abbr title="Coordinated Universal Time">UTC</abbr> %s is <code>%s</code>' ), $time_options['gmt_offset']['options'][$gmt_offset], bb_datetime_format_i18n( bb_current_time() ) ),
		2 => __( 'Unfortunately, you have to manually update this for Daylight Savings Time.' )
	);

	if ( $gmt_offset ) {
		$time_options['gmt_offset']['note'][0] = sprintf( __( '<abbr title="Coordinated Universal Time">UTC</abbr> time is <code>%s</code>' ), bb_gmdate_i18n( bb_get_datetime_formatstring_i18n(), bb_current_time(), true ) );
		ksort($time_options['gmt_offset']['note']);
	}

	foreach ( $time_options['gmt_offset']['options'] as $_key => $_value ) {
		$time_options['gmt_offset']['options'][$_key] = sprintf( __( 'UTC %s' ), $_value );
	}
}


$bb_admin_body_class = ' bb-admin-settings';

bb_get_admin_header();

?>

<div class="wrap">

<h2><?php _e('General Settings'); ?></h2>
<?php do_action( 'bb_admin_notices' ); ?>

<form class="settings" method="post" action="<?php bb_uri( 'bb-admin/options-general.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN ); ?>">
	<fieldset>
<?php
foreach ( $general_options as $option => $args ) {
	bb_option_form_element( $option, $args );
}
foreach ( $time_options as $option => $args ) {
	bb_option_form_element( $option, $args );
}
?>
	</fieldset>
	<fieldset class="submit">
		<?php bb_nonce_field( 'options-general-update' ); ?>
		<input type="hidden" name="action" value="update" />
		<input class="submit" type="submit" name="submit" value="<?php _e('Save Changes') ?>" />
	</fieldset>
</form>

</div>

<?php

bb_get_admin_footer();
