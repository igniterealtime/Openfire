<?php
$buddymobile_options = get_option('buddymobile_plugin_options');

add_filter('show_admin_bar', '__return_false');
define( 'BP_DTHEME_DISABLE_CUSTOM_HEADER', true );

remove_filter( 'wp_nav_menu', 'remove_ul' );

function buddymobile_undo_jcropper() {
	remove_action( 'wp_print_scripts', 'bp_core_add_jquery_cropper' );
}
add_action('wp', 'buddymobile_undo_jcropper', 0 );

function buddymobile_redo_jcropper() {
	global $bp;

	if ( ! isset( $bp->avatar_admin ) ) {
		$bp->avatar_admin = new stdClass();
	}
	if ( $bp->avatar_admin->step = 'crop-image' ) {
		wp_enqueue_style( 'jcrop' );
		wp_enqueue_script( 'jcrop', array( 'jquery' ) );
		add_action( 'wp_head', 'buddymobile_core_add_cropper_inline_js', 10 );
	}
}
add_action('bp_include', 'buddymobile_redo_jcropper', 10 );

if ( function_exists( 'add_theme_support' ) ) {
	add_theme_support( 'post-thumbnails' );
}

if ( function_exists( 'add_image_size' ) ) {
	add_image_size( 'mobile-thumb', 500, 200, true );
}


function iphone_load_scripts() {
	wp_enqueue_script( 'mobile-theme-js', plugins_url() . '/buddymobile/themes/mobile/iphone/js/theme.js?cache=1', array( 'jquery' ) );

	if ( !empty( $buddymobile_options['add2homescreen'] ) ) {
			wp_enqueue_script( 'addhome-theme-js', plugins_url() . '/buddymobile/themes/mobile/iphone/js/add2home.js', array( 'jquery' ) );
	}

}
add_action( 'wp_enqueue_scripts', 'iphone_load_scripts' );


function buddymobile_custom_styles() {
	global $buddymobile_options;

	echo "<style type='text/css'>";

	if ( !empty( $buddymobile_options['toolbar-color'] ) ) {

		$color = $buddymobile_options['toolbar-color'];

			echo "#topbar { background-color: $color !important; }";
			echo "#content a, .footer a, #content a:visited { color: $color !important; }";
			echo "div.item-list-tabs ul li a span { background-color: $color !important; }";
			echo "#footer a { color: $color !important; }";
	}

	if ( !empty( $buddymobile_options['background-color'] ) ) {

		$color = $buddymobile_options['background-color'];

			echo "body { background-color: $color !important; }";
	}

	echo '</style>';

}
add_action( 'wp_head', 'buddymobile_custom_styles' );

function bp_notification_badge() {

	if ( is_user_logged_in() ) {

	 	echo '<div id="notifications-list"><ul>';

	 	$notifications = bp_core_get_notifications_for_user( bp_loggedin_user_id() );

	        if ( $notifications ) {
	            $counter = 0;
	            for ( $i = 0; $i < count($notifications); $i++ ) {
	                $badge = count($notifications);
	                echo '<li>'.$notifications[$i].'</li>';
	            }

	             echo '</ul></div>';

	            //echo '<span id="notifications-badge">'.$badge.'</span>';
	        }

	}

}


function buddymobile_core_add_cropper_inline_js() {
	global $bp;

		if ( wp_is_mobile() ) {

		$imageraw = wp_get_image_editor( bp_core_avatar_upload_path() . buddypress()->avatar_admin->image->dir );

		if ( ! is_wp_error( $imageraw ) ) {
		    $imageraw->resize( 280, 300, false );
		    $imageraw->save( bp_core_avatar_upload_path() . buddypress()->avatar_admin->image->dir );
	    }
    }


	// Bail if no image was uploaded
	$image = apply_filters( 'bp_inline_cropper_image', getimagesize( bp_core_avatar_upload_path() . buddypress()->avatar_admin->image->dir ) );
	if ( empty( $image ) )
		return;

	//
	$full_height = bp_core_avatar_full_height();
	$full_width  = bp_core_avatar_full_width();

	// Calculate Aspect Ratio
	if ( !empty( $full_height ) && ( $full_width != $full_height ) ) {
		$aspect_ratio = $full_width / $full_height;
	} else {
		$aspect_ratio = 1;
	}

	// Default cropper coordinates
	$crop_left   = $image[0] / 4;
	$crop_top    = $image[1] / 4;
	$crop_right  = $image[0] - $crop_left;
	$crop_bottom = $image[1] - $crop_top; ?>

	<script type="text/javascript">
		jQuery(window).load( function(){
			jQuery('#avatar-to-crop').Jcrop({
				onChange: showPreview,
				onSelect: showPreview,
				onSelect: updateCoords,
				aspectRatio: <?php echo $aspect_ratio; ?>,
				setSelect: [ <?php echo $crop_left; ?>, <?php echo $crop_top; ?>, <?php echo $crop_right; ?>, <?php echo $crop_bottom; ?> ]
			});
			updateCoords({x: <?php echo $crop_left; ?>, y: <?php echo $crop_top; ?>, w: <?php echo $crop_right; ?>, h: <?php echo $crop_bottom; ?>});
		});

		function updateCoords(c) {
			jQuery('#x').val(c.x);
			jQuery('#y').val(c.y);
			jQuery('#w').val(c.w);
			jQuery('#h').val(c.h);
		}

		function showPreview(coords) {
			if ( parseInt(coords.w) > 0 ) {
				var fw = <?php echo $full_width; ?>;
				var fh = <?php echo $full_height; ?>;
				var rx = fw / coords.w;
				var ry = fh / coords.h;

				jQuery( '#avatar-crop-preview' ).css({
					width: Math.round(rx * <?php echo $image[0]; ?>) + 'px',
					height: Math.round(ry * <?php echo $image[1]; ?>) + 'px',
					marginLeft: '-' + Math.round(rx * coords.x) + 'px',
					marginTop: '-' + Math.round(ry * coords.y) + 'px'
				});
			}
		}
	</script>

<?php
}