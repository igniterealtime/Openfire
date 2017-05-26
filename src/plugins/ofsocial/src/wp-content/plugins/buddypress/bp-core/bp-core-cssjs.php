<?php
/**
 * Core component CSS & JS.
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Register scripts commonly used by BuddyPress.
 *
 * @since 2.1.0
 */
function bp_core_register_common_scripts() {
	$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : '.min';
	$url = buddypress()->plugin_url . 'bp-core/js/';

	/**
	 * Filters the BuddyPress Core javascript files to register.
	 *
	 * @since 2.1.0
	 *
	 * @param array $value Array of javascript file information to register.
	 */
	$scripts = apply_filters( 'bp_core_register_common_scripts', array(

		// Legacy
		'bp-confirm'        => array( 'file' => "{$url}confirm{$min}.js",        'dependencies' => array( 'jquery' ), 'footer' => false ),
		'bp-widget-members' => array( 'file' => "{$url}widget-members{$min}.js", 'dependencies' => array( 'jquery' ), 'footer' => false ),
		'bp-jquery-query'   => array( 'file' => "{$url}jquery-query{$min}.js",   'dependencies' => array( 'jquery' ), 'footer' => false ),
		'bp-jquery-cookie'  => array( 'file' => "{$url}jquery-cookie{$min}.js",  'dependencies' => array( 'jquery' ), 'footer' => false ),
		'bp-jquery-scroll-to' => array( 'file' => "{$url}jquery-scroll-to{$min}.js", 'dependencies' => array( 'jquery' ), 'footer' => false ),

		// 2.1
		'jquery-caret' => array( 'file' => "{$url}jquery.caret{$min}.js", 'dependencies' => array( 'jquery' ), 'footer' => true ),
		'jquery-atwho' => array( 'file' => "{$url}jquery.atwho{$min}.js", 'dependencies' => array( 'jquery', 'jquery-caret' ), 'footer' => true ),

		// 2.3
		'bp-plupload' => array( 'file' => "{$url}bp-plupload{$min}.js", 'dependencies' => array( 'plupload', 'jquery', 'json2', 'wp-backbone' ), 'footer' => true ),
		'bp-avatar'   => array( 'file' => "{$url}avatar{$min}.js", 'dependencies' => array( 'jcrop' ), 'footer' => true ),
		'bp-webcam'   => array( 'file' => "{$url}webcam{$min}.js", 'dependencies' => array( 'bp-avatar' ), 'footer' => true ),

		// 2.4
		'bp-cover-image' => array( 'file' => "{$url}cover-image{$min}.js", 'dependencies' => array(), 'footer' => true ),

	) );

	$version = bp_get_version();
	foreach ( $scripts as $id => $script ) {
		wp_register_script( $id, $script['file'], $script['dependencies'], $version, $script['footer'] );
	}
}
add_action( 'bp_enqueue_scripts',       'bp_core_register_common_scripts', 1 );
add_action( 'bp_admin_enqueue_scripts', 'bp_core_register_common_scripts', 1 );

/**
 * Register styles commonly used by BuddyPress.
 *
 * @since 2.1.0
 */
function bp_core_register_common_styles() {
	$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : '.min';
	$url = buddypress()->plugin_url . 'bp-core/css/';

	/**
	 * Filters the URL for the Admin Bar stylesheet.
	 *
	 * @since 1.1.0
	 *
	 * @param string $value URL for the Admin Bar stylesheet.
	 */
	$admin_bar_file = apply_filters( 'bp_core_admin_bar_css', "{$url}admin-bar{$min}.css" );

	/**
	 * Filters the BuddyPress Core stylesheet files to register.
	 *
	 * @since 2.1.0
	 *
	 * @param array $value Array of stylesheet file information to register.
	 */
	$styles = apply_filters( 'bp_core_register_common_styles', array(
		'bp-admin-bar' => array(
			'file'         => $admin_bar_file,
			'dependencies' => array( 'admin-bar' )
		),
		'bp-avatar' => array(
			'file'         => "{$url}avatar{$min}.css",
			'dependencies' => array( 'jcrop' )
		),
	) );

	foreach ( $styles as $id => $style ) {
		wp_register_style( $id, $style['file'], $style['dependencies'], bp_get_version() );

		wp_style_add_data( $id, 'rtl', true );
		if ( $min ) {
			wp_style_add_data( $id, 'suffix', $min );
		}
	}
}
add_action( 'bp_enqueue_scripts',       'bp_core_register_common_styles', 1 );
add_action( 'bp_admin_enqueue_scripts', 'bp_core_register_common_styles', 1 );

/**
 * Load the JS for "Are you sure?" .confirm links.
 */
function bp_core_confirmation_js() {
	if ( is_multisite() && ! bp_is_root_blog() ) {
		return false;
	}

	wp_enqueue_script( 'bp-confirm' );

	wp_localize_script( 'bp-confirm', 'BP_Confirm', array(
		'are_you_sure' => __( 'Are you sure?', 'buddypress' ),
	) );

}
add_action( 'bp_enqueue_scripts',       'bp_core_confirmation_js' );
add_action( 'bp_admin_enqueue_scripts', 'bp_core_confirmation_js' );

/**
 * Enqueues the css and js required by the Avatar UI.
 *
 * @since  2.3.0
 */
function bp_core_avatar_scripts() {
	if ( ! bp_avatar_is_front_edit() ) {
		return false;
	}

	// Enqueue the Attachments scripts for the Avatar UI
	bp_attachments_enqueue_scripts( 'BP_Attachment_Avatar' );

	// Add Some actions for Theme backcompat
	add_action( 'bp_after_profile_avatar_upload_content', 'bp_avatar_template_check' );
	add_action( 'bp_after_group_admin_content',           'bp_avatar_template_check' );
	add_action( 'bp_after_group_avatar_creation_step',    'bp_avatar_template_check' );
}
add_action( 'bp_enqueue_scripts', 'bp_core_avatar_scripts' );

/**
 * Enqueues the css and js required by the Cover Image UI.
 *
 * @since  2.4.0
 */
function bp_core_cover_image_scripts() {
	if ( ! bp_attachments_cover_image_is_edit() ) {
		return false;
	}

	// Enqueue the Attachments scripts for the Cover Image UI
	bp_attachments_enqueue_scripts( 'BP_Attachment_Cover_Image' );
}
add_action( 'bp_enqueue_scripts', 'bp_core_cover_image_scripts' );

/**
 * Enqueues jCrop library and hooks BP's custom cropper JS.
 */
function bp_core_add_jquery_cropper() {
	wp_enqueue_style( 'jcrop' );
	wp_enqueue_script( 'jcrop', array( 'jquery' ) );
	add_action( 'wp_head', 'bp_core_add_cropper_inline_js' );
	add_action( 'wp_head', 'bp_core_add_cropper_inline_css' );
}

/**
 * Output the inline JS needed for the cropper to work on a per-page basis.
 */
function bp_core_add_cropper_inline_js() {

	/**
	 * Filters the return value of getimagesize to determine if an image was uploaded.
	 *
	 * @since 1.1.0
	 *
	 * @param array $value Array of data found by getimagesize.
	 */
	$image = apply_filters( 'bp_inline_cropper_image', getimagesize( bp_core_avatar_upload_path() . buddypress()->avatar_admin->image->dir ) );
	if ( empty( $image ) ) {
		return;
	}

	// Get avatar full width and height
	$full_height = bp_core_avatar_full_height();
	$full_width  = bp_core_avatar_full_width();

	// Calculate Aspect Ratio
	if ( !empty( $full_height ) && ( $full_width != $full_height ) ) {
		$aspect_ratio = $full_width / $full_height;
	} else {
		$aspect_ratio = 1;
	}

	// Default cropper coordinates

	// Smaller than full-width: cropper defaults to entire image
	if ( $image[0] < $full_width ) {
		$crop_left  = 0;
		$crop_right = $image[0];

	// Less than 2x full-width: cropper defaults to full-width
	} elseif ( $image[0] < ( $full_width * 2 ) ) {
		$padding_w  = round( ( $image[0] - $full_width ) / 2 );
		$crop_left  = $padding_w;
		$crop_right = $image[0] - $padding_w;

	// Larger than 2x full-width: cropper defaults to 1/2 image width
	} else {
		$crop_left  = round( $image[0] / 4 );
		$crop_right = $image[0] - $crop_left;
	}

	// Smaller than full-height: cropper defaults to entire image
	if ( $image[1] < $full_height ) {
		$crop_top    = 0;
		$crop_bottom = $image[1];

	// Less than double full-height: cropper defaults to full-height
	} elseif ( $image[1] < ( $full_height * 2 ) ) {
		$padding_h   = round( ( $image[1] - $full_height ) / 2 );
		$crop_top    = $padding_h;
		$crop_bottom = $image[1] - $padding_h;

	// Larger than 2x full-height: cropper defaults to 1/2 image height
	} else {
		$crop_top    = round( $image[1] / 4 );
		$crop_bottom = $image[1] - $crop_top;
	}

	?>

	<script type="text/javascript">
		jQuery(window).load( function(){
			jQuery('#avatar-to-crop').Jcrop({
				onChange: showPreview,
				onSelect: updateCoords,
				aspectRatio: <?php echo (int) $aspect_ratio; ?>,
				setSelect: [ <?php echo (int) $crop_left; ?>, <?php echo (int) $crop_top; ?>, <?php echo (int) $crop_right; ?>, <?php echo (int) $crop_bottom; ?> ]
			});
		});

		function updateCoords(c) {
			jQuery('#x').val(c.x);
			jQuery('#y').val(c.y);
			jQuery('#w').val(c.w);
			jQuery('#h').val(c.h);
		}

		function showPreview(coords) {
			if ( parseInt(coords.w) > 0 ) {
				var fw = <?php echo (int) $full_width; ?>;
				var fh = <?php echo (int) $full_height; ?>;
				var rx = fw / coords.w;
				var ry = fh / coords.h;

				jQuery( '#avatar-crop-preview' ).css({
					width: Math.round(rx * <?php echo (int) $image[0]; ?>) + 'px',
					height: Math.round(ry * <?php echo (int) $image[1]; ?>) + 'px',
					marginLeft: '-' + Math.round(rx * coords.x) + 'px',
					marginTop: '-' + Math.round(ry * coords.y) + 'px'
				});
			}
		}
	</script>

<?php
}

/**
 * Output the inline CSS for the BP image cropper.
 *
 * @package BuddyPress Core
 */
function bp_core_add_cropper_inline_css() {
?>

	<style type="text/css">
		.jcrop-holder { float: left; margin: 0 20px 20px 0; text-align: left; }
		#avatar-crop-pane { width: <?php echo bp_core_avatar_full_width() ?>px; height: <?php echo bp_core_avatar_full_height() ?>px; overflow: hidden; }
		#avatar-crop-submit { margin: 20px 0; }
		.jcrop-holder img,
		#avatar-crop-pane img,
		#avatar-upload-form img,
		#create-group-form img,
		#group-settings-form img { border: none !important; max-width: none !important; }
	</style>

<?php
}

/**
 * Define the 'ajaxurl' JS variable, used by themes as an AJAX endpoint.
 *
 * @since 1.1.0
 */
function bp_core_add_ajax_url_js() {
?>

	<script type="text/javascript">var ajaxurl = '<?php echo bp_core_ajax_url(); ?>';</script>

<?php
}
add_action( 'wp_head', 'bp_core_add_ajax_url_js' );

/**
 * Get the proper value for BP's ajaxurl.
 *
 * Designed to be sensitive to FORCE_SSL_ADMIN and non-standard multisite
 * configurations.
 *
 * @since 1.7.0
 *
 * @return string AJAX endpoint URL.
 */
function bp_core_ajax_url() {

	/**
	 * Filters the proper value for BuddyPress' ajaxurl.
	 *
	 * @since 1.7.0
	 *
	 * @param string $value Proper ajaxurl value for BuddyPress.
	 */
	return apply_filters( 'bp_core_ajax_url', admin_url( 'admin-ajax.php', is_ssl() ? 'admin' : 'http' ) );
}

/**
 * Get the JavaScript dependencies for buddypress.js.
 *
 * @since 2.0.0
 *
 * @uses apply_filters() to allow other component to load extra dependencies.
 *
 * @return array The JavaScript dependencies.
 */
function bp_core_get_js_dependencies() {

	/**
	 * Filters the javascript dependencies for buddypress.js.
	 *
	 * @since 2.0.0
	 *
	 * @param array $value Array of javascript dependencies for buddypress.js.
	 */
	return apply_filters( 'bp_core_get_js_dependencies', array(
		'jquery',
		'bp-confirm',
		'bp-widget-members',
		'bp-jquery-query',
		'bp-jquery-cookie',
		'bp-jquery-scroll-to'
	) );
}

/**
 * Add inline css to display the component's single item cover image
 *
 * @since 2.4.0
 *
 * @param  bool $return true to get the inline css
 * @return string|array the inline css or an associative array containing
 *                      the css rules and the style handle
 */
function bp_add_cover_image_inline_css( $return = false ) {
	$bp = buddypress();

	// Find the component of the current item
	if ( bp_is_user() ) {

		// User is not allowed to upload cover images
		// no need to carry on
		if ( bp_disable_cover_image_uploads() ) {
			return;
		}

		$cover_image_object = array(
			'component' => 'xprofile',
			'object' => $bp->displayed_user
		);
	} elseif ( bp_is_group() ) {

		// Users are not allowed to upload cover images for their groups
		// no need to carry on
		if ( bp_disable_group_cover_image_uploads() ) {
			return;
		}

		$cover_image_object = array(
			'component' =>'groups',
			'object' => $bp->groups->current_group
		);
	} else {
		$cover_image_object = apply_filters( 'bp_current_cover_image_object_inline_css', array() );
	}

	// Bail if no component were found.
	if ( empty( $cover_image_object['component'] ) || empty( $cover_image_object['object'] ) || ! bp_is_active( $cover_image_object['component'], 'cover_image' ) ) {
		return;
	}

	// Get the settings of the cover image feature for the current component
	$params = bp_attachments_get_cover_image_settings( $cover_image_object['component'] );

	// Bail if no params.
	if ( empty( $params ) ) {
		return;
	}

	// Try to call the callback
	if ( is_callable( $params['callback'] ) ) {

		$object_dir = $cover_image_object['component'];

		if ( 'xprofile' === $object_dir ) {
			$object_dir = 'members';
		}

		$cover_image = bp_attachments_get_attachment( 'url', array(
			'object_dir' => $object_dir,
			'item_id'    => $cover_image_object['object']->id,
		) );

		if ( empty( $cover_image ) ) {
			if ( ! empty( $params['default_cover'] ) ) {
				$cover_image = $params['default_cover'];
			}
		}

		$inline_css = call_user_func_array( $params['callback'], array( array(
			'cover_image' => esc_url( $cover_image ),
			'component'   => sanitize_key( $cover_image_object['component'] ),
			'object_id'   => (int) $cover_image_object['object']->id,
			'width'       => (int) $params['width'],
			'height'      => (int) $params['height'],
		) ) );

		// Finally add the inline css to the handle
		if ( ! empty( $inline_css ) ) {

			// Used to get the css when Ajax setting the cover image
			if ( true === $return ) {
				return array(
					'css_rules' => '<style type="text/css">' . "\n" . $inline_css . "\n" . '</style>',
					'handle'    => $params['theme_handle'],
				);
			}

			wp_add_inline_style( $params['theme_handle'], $inline_css );
		} else {
			return false;
		}
	}
}
add_action( 'bp_enqueue_scripts', 'bp_add_cover_image_inline_css', 11 );
