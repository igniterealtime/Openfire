<?php
/**
 * BuddyPress Attachments functions.
 *
 * @package BuddyPress
 * @subpackage Attachments
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Check if the current WordPress version is using Plupload 2.1.1
 *
 * Plupload 2.1.1 was introduced in WordPress 3.9. Our bp-plupload.js
 * script requires it. So we need to make sure the current WordPress
 * match with our needs.
 *
 * @since  2.3.0
 *
 * @return bool True if WordPress is 3.9+, false otherwise.
 */
function bp_attachments_is_wp_version_supported() {
	return (bool) version_compare( bp_get_major_wp_version(), '3.9', '>=' );
}

/**
 * Get the Attachments Uploads dir data
 *
 * @since  2.4.0
 *
 * @param  string        $data The data to get. Possible values are: 'dir', 'basedir' & 'baseurl'
 *                       Leave empty to get all datas.
 * @return string|array  The needed Upload dir data.
 */
function bp_attachments_uploads_dir_get( $data = '' ) {
	$attachments_dir = 'buddypress';
	$retval          = '';

	if ( 'dir' === $data ) {
		$retval = $attachments_dir;
	} else {
		$upload_data = bp_upload_dir();

		// Return empty string, if Uploads data are not available
		if ( ! $upload_data ) {
			return $retval;
		}

		// Build the Upload data array for BuddyPress attachments
		foreach ( $upload_data as $key => $value ) {
			if ( 'basedir' === $key || 'baseurl' === $key ) {
				$upload_data[ $key ] = trailingslashit( $value ) . $attachments_dir;
			} else {
				unset( $upload_data[ $key ] );
			}
		}

		// Add the dir to the array
		$upload_data['dir'] = $attachments_dir;

		if ( empty( $data ) ) {
			$retval = $upload_data;
		} elseif ( isset( $upload_data[ $data ] ) ) {
			$retval = $upload_data[ $data ];
		}
	}

	/**
	 * Filter here to edit the Attachments upload dir data.
	 *
	 * @since  2.4.0
	 *
	 * @param  string|array $retval      The needed Upload dir data or the full array of data
	 * @param  string       $data        The data requested
	 */
	return apply_filters( 'bp_attachments_uploads_dir_get', $retval, $data );
}

/**
 * Get the max upload file size for any attachment
 *
 * @since  2.4.0
 *
 * @param  string $type A string to inform about the type of attachment
 *                      we wish to get the max upload file size for
 * @return int    max upload file size for any attachment
 */
function bp_attachments_get_max_upload_file_size( $type = '' ) {
	$fileupload_maxk = bp_core_get_root_option( 'fileupload_maxk' );

	if ( '' === $fileupload_maxk ) {
		$fileupload_maxk = 5120000; // 5mb;
	} else {
		$fileupload_maxk = $fileupload_maxk * 1024;
	}

	/**
	 * Filter here to edit the max upload file size.
	 *
	 * @since  2.4.0
	 *
	 * @param  int    $fileupload_maxk Max upload file size for any attachment
	 * @param  string $type            The attachment type (eg: 'avatar' or 'cover_image')
	 */
	return apply_filters( 'bp_attachments_get_max_upload_file_size', $fileupload_maxk, $type );
}

/**
 * Get allowed types for any attachment
 *
 * @since  2.4.0
 *
 * @param  string $type  The extension types to get.
 *                       Default: 'avatar'
 * @return array         The list of allowed extensions for attachments
 */
function bp_attachments_get_allowed_types( $type = 'avatar' ) {
	// Defaults to BuddyPress supported image extensions
	$exts = array( 'jpeg', 'gif', 'png' );

	/**
	 * It's not a BuddyPress feature, get the allowed extensions
	 * matching the $type requested
	 */
	if ( 'avatar' !== $type && 'cover_image' !== $type ) {
		// Reset the default exts
		$exts = array();

		switch ( $type ) {
			case 'video' :
				$exts = wp_get_video_extensions();
			break;

			case 'audio' :
				$exts = wp_get_video_extensions();
			break;

			default:
				$allowed_mimes = get_allowed_mime_types();

				/**
				 * Search for allowed mimes matching the type
				 *
				 * eg: using 'application/vnd.oasis' as the $type
				 * parameter will get all OpenOffice extensions supported
				 * by WordPress and allowed for the current user.
				 */
				if ( '' !== $type ) {
					$allowed_mimes = preg_grep( '/' . addcslashes( $type, '/.+-' ) . '/', $allowed_mimes );
				}

				$allowed_types = array_keys( $allowed_mimes );

				// Loop to explode keys using '|'
				foreach ( $allowed_types as $allowed_type ) {
					$t = explode( '|', $allowed_type );
					$exts = array_merge( $exts, (array) $t );
				}
			break;
		}
	}

	/**
	 * Filter here to edit the allowed extensions by attachment type.
	 *
	 * @since  2.4.0
	 *
	 * @param  array  $exts List of allowed extensions
	 * @param  string $type The requested file type
	 */
	return apply_filters( 'bp_attachments_get_allowed_types', $exts, $type );
}

/**
 * Get allowed attachment mime types.
 *
 * @since 2.4.0
 *
 * @param  string $type         The extension types to get (Optional).
 * @param  array $allowed_types List of allowed extensions
 * @return array                List of allowed mime types
 */
function bp_attachments_get_allowed_mimes( $type = '', $allowed_types = array() ) {
	if ( empty( $allowed_types ) ) {
		$allowed_types = bp_attachments_get_allowed_types( $type );
	}

	$validate_mimes = wp_match_mime_types( join( ',', $allowed_types ), wp_get_mime_types() );
	$allowed_mimes  = array_map( 'implode', $validate_mimes );

	/**
	 * Include jpg type if jpeg is set
	 */
	if ( isset( $allowed_mimes['jpeg'] ) && ! isset( $allowed_mimes['jpg'] ) ) {
		$allowed_mimes['jpg'] = $allowed_mimes['jpeg'];
	}

	return $allowed_mimes;
}

/**
 * Check the uploaded attachment type is allowed
 *
 * @since  2.4.0
 *
 * @param  string $file          Full path to the file.
 * @param  string $filename      The name of the file (may differ from $file due to $file being
 *                               in a tmp directory).
 * @param  array  $allowed_mimes The attachment allowed mimes (Required)
 * @return bool                  True if the attachment type is allowed. False otherwise
 */
function bp_attachments_check_filetype( $file, $filename, $allowed_mimes ) {
	$filetype = wp_check_filetype_and_ext( $file, $filename, $allowed_mimes );

	if ( ! empty( $filetype['ext'] ) && ! empty( $filetype['type'] ) ) {
		return true;
	}

	return false;
}

/**
 * Use the absolute path to an image to set an attachment type for a given item.
 *
 * @since 2.4.0
 *
 * @param  string $type        The attachment type to create (avatar or cover_image). Default: avatar.
 * @param  array  $args {
 *     @type int    $item_id   The ID of the object (Required). Default: 0.
 *     @type string $object    The object type (eg: group, user, blog) (Required). Default: 'user'.
 *     @type string $component The component for the object (eg: groups, xprofile, blogs). Default: ''.
 *     @type string $image     The absolute path to the image (Required). Default: ''.
 *     @type int    $crop_w    Crop width. Default: 0.
 *     @type int    $crop_h    Crop height. Default: 0.
 *     @type int    $crop_x    The horizontal starting point of the crop. Default: 0.
 *     @type int    $crop_y    The vertical starting point of the crop. Default: 0.
 * }
 * @return bool  True on success, false otherwise.
 */
function bp_attachments_create_item_type( $type = 'avatar', $args = array() ) {
	if ( empty( $type ) || ( $type !== 'avatar' && $type !== 'cover_image' ) ) {
		return false;
	}

	$r = bp_parse_args( $args, array(
		'item_id'   => 0,
		'object'    => 'user',
		'component' => '',
		'image'     => '',
		'crop_w'    => 0,
		'crop_h'    => 0,
		'crop_x'    => 0,
		'crop_y'    => 0
	), 'create_item_' . $type );

	if ( empty( $r['item_id'] ) || empty( $r['object'] ) || ! file_exists( $r['image'] ) || ! @getimagesize( $r['image'] ) ) {
		return false;
	}

	// Make sure the file path is safe
	if ( 0 !== validate_file( $r['image'] ) ) {
		return false;
	}

	// Set the component if not already done
	if ( empty( $r['component'] ) ) {
		if ( 'user' === $r['object'] ) {
			$r['component'] = 'xprofile';
		} else {
			$r['component'] = $r['object'] . 's';
		}
	}

	// Get allowed mimes for the Attachment type and check the image one is.
	$allowed_mimes = bp_attachments_get_allowed_mimes( $type );
	$is_allowed    = wp_check_filetype( $r['image'], $allowed_mimes );

	// It's not an image.
	if ( ! $is_allowed['ext'] ) {
		return false;
	}

	// Init the Attachment data
	$attachment_data = array();

	if ( 'avatar' === $type ) {
		// Set crop width for the avatar if not given
		if ( empty( $r['crop_w'] ) ) {
			$r['crop_w'] = bp_core_avatar_full_width();
		}

		// Set crop height for the avatar if not given
		if ( empty( $r['crop_h'] ) ) {
			$r['crop_h'] = bp_core_avatar_full_height();
		}

		if ( is_callable( $r['component'] . '_avatar_upload_dir' ) ) {
			$dir_args = array( $r['item_id'] );

			// In case  of xprofile, we need an extra argument
			if ( 'xprofile' === $r['component'] ) {
				$dir_args = array( false, $r['item_id'] );
			}

			$attachment_data = call_user_func_array( $r['component'] . '_avatar_upload_dir', $dir_args );
		}
	} elseif ( 'cover_image' === $type ) {
		$attachment_data = bp_attachments_uploads_dir_get();

		// The BP Attachments Uploads Dir is not set, stop.
		if ( ! $attachment_data ) {
			return false;
		}

		// Default to members for xProfile
		$object_subdir = 'members';

		if ( 'xprofile' !== $r['component'] ) {
			$object_subdir = sanitize_key( $r['component'] );
		}

		// Set Subdir
		$attachment_data['subdir'] = $object_subdir . '/' . $r['item_id'] . '/cover-image';

		// Set Path
		$attachment_data['path'] = trailingslashit( $attachment_data['basedir'] ) . $attachment_data['subdir'];
	}

	if ( ! isset( $attachment_data['path'] ) || ! isset( $attachment_data['subdir'] ) ) {
		return false;
	}

	// It's not a regular upload, we may need to create some folders
	if ( ! is_dir( $attachment_data['path'] ) ) {
		if ( ! wp_mkdir_p( $attachment_data['path'] ) ) {
			return false;
		}
	}

	// Set the image name and path
	$image_file_name = wp_unique_filename( $attachment_data['path'], basename( $r['image'] ) );
	$image_file_path = $attachment_data['path'] . '/' . $image_file_name;

	// Copy the image file into the avatar dir
	if ( ! copy( $r['image'], $image_file_path ) ) {
		return false;
	}

	// Init the response
	$created = false;

	// It's an avatar, we need to crop it.
	if ( 'avatar' === $type ) {
		$created = bp_core_avatar_handle_crop( array(
			'object'        => $r['object'],
			'avatar_dir'    => trim( dirname( $attachment_data['subdir'] ), '/' ),
			'item_id'       => (int) $r['item_id'],
			'original_file' => trailingslashit( $attachment_data['subdir'] ) . $image_file_name,
			'crop_w'        => $r['crop_w'],
			'crop_h'        => $r['crop_h'],
			'crop_x'        => $r['crop_x'],
			'crop_y'        => $r['crop_y']
		) );

	// It's a cover image we need to fit it to feature's dimensions
	} elseif ( 'cover_image' === $type ) {
		$cover_image = bp_attachments_cover_image_generate_file( array(
			'file'            => $image_file_path,
			'component'       => $r['component'],
			'cover_image_dir' => $attachment_data['path']
		) );

		$created = ! empty( $cover_image['cover_file'] );
	}

	// Remove copied file if it fails
	if ( ! $created ) {
		@unlink( $image_file_path );
	}

	// Return the response
	return $created;
}

/**
 * Get the url or the path for a type of attachment
 *
 * @since  2.4.0
 *
 * @param  string $data whether to get the url or the path
 * @param  array  $args {
 *     @type string $object_dir  The object dir (eg: members/groups). Defaults to members.
 *     @type int    $item_id     The object id (eg: a user or a group id). Defaults to current user.
 *     @type string $type        The type of the attachment which is also the subdir where files are saved.
 *                               Defaults to 'cover-image'
 *     @type string $file        The name of the file.
 * }
 * @return string|bool the url or the path to the attachment, false otherwise
 */
function bp_attachments_get_attachment( $data = 'url', $args = array() ) {
	// Default value
	$attachment_data = false;

	$r = bp_parse_args( $args, array(
		'object_dir' => 'members',
		'item_id'    => bp_loggedin_user_id(),
		'type'       => 'cover-image',
		'file'       => '',
	), 'attachments_get_attachment_src' );

	// Get BuddyPress Attachments Uploads Dir datas
	$bp_attachments_uploads_dir = bp_attachments_uploads_dir_get();

	// The BP Attachments Uploads Dir is not set, stop.
	if ( ! $bp_attachments_uploads_dir ) {
		return $attachment_data;
	}

	$type_subdir = $r['object_dir'] . '/' . $r['item_id'] . '/' . $r['type'];
	$type_dir    = trailingslashit( $bp_attachments_uploads_dir['basedir'] ) . $type_subdir;

	if ( ! is_dir( $type_dir ) ) {
		return $attachment_data;
	}

	if ( ! empty( $r['file'] ) ) {
		if ( ! file_exists( trailingslashit( $type_dir ) . $r['file'] ) ) {
			return $attachment_data;
		}

		if ( 'url' === $data ) {
			$attachment_data = trailingslashit( $bp_attachments_uploads_dir['baseurl'] ) . $type_subdir . '/' . $r['file'];
		} else {
			$attachment_data = trailingslashit( $type_dir ) . $r['file'];
		}

	} else {
		$file = false;

		// Open the directory and get the first file
		if ( $att_dir = opendir( $type_dir ) ) {

			while ( false !== ( $attachment_file = readdir( $att_dir ) ) ) {
				// Look for the first file having the type in its name
				if ( false !== strpos( $attachment_file, $r['type'] ) && empty( $file ) ) {
					$file = $attachment_file;
					break;
				}
			}
		}

		if ( empty( $file ) ) {
			return $attachment_data;
		}

		if ( 'url' === $data ) {
			$attachment_data = trailingslashit( $bp_attachments_uploads_dir['baseurl'] ) . $type_subdir . '/' . $file;
		} else {
			$attachment_data = trailingslashit( $type_dir ) . $file;
		}
	}

	return $attachment_data;
}

/**
 * Delete an attachment for the given arguments
 *
 * @since  2.4.0
 *
 * @param  array $args
 * @see    bp_attachments_get_attachment() For more information on accepted arguments.
 * @return bool True if the attachment was deleted, false otherwise
 */
function bp_attachments_delete_file( $args = array() ) {
	$attachment_path = bp_attachments_get_attachment( 'path', $args );

	if ( empty( $attachment_path ) ) {
		return false;
	}

	@unlink( $attachment_path );
	return true;
}

/**
 * Get the BuddyPress Plupload settings.
 *
 * @since  2.3.0
 *
 * @return array list of BuddyPress Plupload settings.
 */
function bp_attachments_get_plupload_default_settings() {

	$max_upload_size = wp_max_upload_size();

	if ( ! $max_upload_size ) {
		$max_upload_size = 0;
	}

	$defaults = array(
		'runtimes'            => 'html5,flash,silverlight,html4',
		'file_data_name'      => 'file',
		'multipart_params'    => array(
			'action'          => 'bp_upload_attachment',
			'_wpnonce'        => wp_create_nonce( 'bp-uploader' ),
		),
		'url'                 => admin_url( 'admin-ajax.php', 'relative' ),
		'flash_swf_url'       => includes_url( 'js/plupload/plupload.flash.swf' ),
		'silverlight_xap_url' => includes_url( 'js/plupload/plupload.silverlight.xap' ),
		'filters' => array(
			'max_file_size'   => $max_upload_size . 'b',
		),
		'multipart'           => true,
		'urlstream_upload'    => true,
	);

	// WordPress is not allowing multi selection for iOs 7 device.. See #29602.
	if ( wp_is_mobile() && strpos( $_SERVER['HTTP_USER_AGENT'], 'OS 7_' ) !== false &&
		strpos( $_SERVER['HTTP_USER_AGENT'], 'like Mac OS X' ) !== false ) {

		$defaults['multi_selection'] = false;
	}

	$settings = array(
		'defaults' => $defaults,
		'browser'  => array(
			'mobile'    => wp_is_mobile(),
			'supported' => _device_can_upload(),
		),
		'limitExceeded' => is_multisite() && ! is_upload_space_available(),
	);

	/**
	 * Filter the BuddyPress Plupload default settings.
	 *
	 * @since 2.3.0
	 *
	 * @param array $params Default Plupload parameters array.
	 */
	return apply_filters( 'bp_attachments_get_plupload_default_settings', $settings );
}

/**
 * Builds localization strings for the BuddyPress Uploader scripts.
 *
 * @since  2.3.0
 *
 * @return array Plupload default localization strings.
 */
function bp_attachments_get_plupload_l10n() {
	// Localization strings
	return apply_filters( 'bp_attachments_get_plupload_l10n', array(
			'queue_limit_exceeded'      => __( 'You have attempted to queue too many files.', 'buddypress' ),
			'file_exceeds_size_limit'   => __( '%s exceeds the maximum upload size for this site.', 'buddypress' ),
			'zero_byte_file'            => __( 'This file is empty. Please try another.', 'buddypress' ),
			'invalid_filetype'          => __( 'This file type is not allowed. Please try another.', 'buddypress' ),
			'not_an_image'              => __( 'This file is not an image. Please try another.', 'buddypress' ),
			'image_memory_exceeded'     => __( 'Memory exceeded. Please try another smaller file.', 'buddypress' ),
			'image_dimensions_exceeded' => __( 'This is larger than the maximum size. Please try another.', 'buddypress' ),
			'default_error'             => __( 'An error occurred. Please try again later.', 'buddypress' ),
			'missing_upload_url'        => __( 'There was a configuration error. Please contact the server administrator.', 'buddypress' ),
			'upload_limit_exceeded'     => __( 'You may only upload 1 file.', 'buddypress' ),
			'http_error'                => __( 'HTTP error.', 'buddypress' ),
			'upload_failed'             => __( 'Upload failed.', 'buddypress' ),
			'big_upload_failed'         => __( 'Please try uploading this file with the %1$sbrowser uploader%2$s.', 'buddypress' ),
			'big_upload_queued'         => __( '%s exceeds the maximum upload size for the multi-file uploader when used in your browser.', 'buddypress' ),
			'io_error'                  => __( 'IO error.', 'buddypress' ),
			'security_error'            => __( 'Security error.', 'buddypress' ),
			'file_cancelled'            => __( 'File canceled.', 'buddypress' ),
			'upload_stopped'            => __( 'Upload stopped.', 'buddypress' ),
			'dismiss'                   => __( 'Dismiss', 'buddypress' ),
			'crunching'                 => __( 'Crunching&hellip;', 'buddypress' ),
			'unique_file_warning'       => __( 'Make sure to upload a unique file', 'buddypress' ),
			'error_uploading'           => __( '&#8220;%s&#8221; has failed to upload.', 'buddypress' ),
			'has_avatar_warning'        => __( 'If you&#39;d like to delete the existing profile photo but not upload a new one, please use the delete tab.', 'buddypress' )
	) );
}

/**
 * Enqueues the script needed for the Uploader UI.
 *
 * @see  BP_Attachment::script_data() && BP_Attachment_Avatar::script_data() for examples showing how
 * to set specific script data.
 *
 * @since  2.3.0
 *
 * @param  string $class name of the class extending BP_Attachment (eg: BP_Attachment_Avatar).
 *
 * @return null|WP_Error
 */
function bp_attachments_enqueue_scripts( $class = '' ) {
	// Enqueue me just once per page, please.
	if ( did_action( 'bp_attachments_enqueue_scripts' ) ) {
		return;
	}

	if ( ! $class || ! class_exists( $class ) ) {
		return new WP_Error( 'missing_parameter' );
	}

	// Get an instance of the class and get the script data
	$attachment = new $class;
	$script_data  = $attachment->script_data();

	$args = bp_parse_args( $script_data, array(
		'action'            => '',
		'file_data_name'    => '',
		'max_file_size'     => 0,
		'browse_button'     => 'bp-browse-button',
		'container'         => 'bp-upload-ui',
		'drop_element'      => 'drag-drop-area',
		'bp_params'         => array(),
		'extra_css'         => array(),
		'extra_js'          => array(),
		'feedback_messages' => array(),
	), 'attachments_enqueue_scripts' );

	if ( empty( $args['action'] ) || empty( $args['file_data_name'] ) ) {
		return new WP_Error( 'missing_parameter' );
	}

	// Get the BuddyPress uploader strings
	$strings = bp_attachments_get_plupload_l10n();

	// Get the BuddyPress uploader settings
	$settings = bp_attachments_get_plupload_default_settings();

	// Set feedback messages
	if ( ! empty( $args['feedback_messages'] ) ) {
		$strings['feedback_messages'] = $args['feedback_messages'];
	}

	// Use a temporary var to ease manipulation
	$defaults = $settings['defaults'];

	// Set the upload action
	$defaults['multipart_params']['action'] = $args['action'];

	// Set BuddyPress upload parameters if provided
	if ( ! empty( $args['bp_params'] ) ) {
		$defaults['multipart_params']['bp_params'] = $args['bp_params'];
	}

	// Merge other arguments
	$ui_args = array_intersect_key( $args, array(
		'file_data_name' => true,
		'browse_button'  => true,
		'container'      => true,
		'drop_element'   => true,
	) );

	$defaults = array_merge( $defaults, $ui_args );

	if ( ! empty( $args['max_file_size'] ) ) {
		$defaults['filters']['max_file_size'] = $args['max_file_size'] . 'b';
	}

	// Specific to BuddyPress Avatars
	if ( 'bp_avatar_upload' === $defaults['multipart_params']['action'] ) {

		// Include the cropping informations for avatars
		$settings['crop'] = array(
			'full_h'  => bp_core_avatar_full_height(),
			'full_w'  => bp_core_avatar_full_width(),
		);

		// Avatar only need 1 file and 1 only!
		$defaults['multi_selection'] = false;

		// Does the object already has an avatar set
		$has_avatar = $defaults['multipart_params']['bp_params']['has_avatar'];

		// What is the object the avatar belongs to
		$object = $defaults['multipart_params']['bp_params']['object'];

		// Init the Avatar nav
		$avatar_nav = array(
			'upload' => array( 'id' => 'upload', 'caption' => __( 'Upload', 'buddypress' ), 'order' => 0  ),

			// The delete view will only show if the object has an avatar
			'delete' => array( 'id' => 'delete', 'caption' => __( 'Delete', 'buddypress' ), 'order' => 100, 'hide' => (int) ! $has_avatar ),
		);

		// Create the Camera Nav if the WebCam capture feature is enabled
		if ( bp_avatar_use_webcam() && 'user' === $object ) {
			$avatar_nav['camera'] = array( 'id' => 'camera', 'caption' => __( 'Take Photo', 'buddypress' ), 'order' => 10 );

			// Set warning messages
			$strings['camera_warnings'] = array(
				'requesting'  => __( 'Please allow us to access to your camera.', 'buddypress'),
				'loading'     => __( 'Please wait as we access your camera.', 'buddypress' ),
				'loaded'      => __( 'Camera loaded. Click on the "Capture" button to take your photo.', 'buddypress' ),
				'noaccess'    => __( 'It looks like you do not have a webcam or we were unable to get permission to use your webcam. Please upload a photo instead.', 'buddypress' ),
				'errormsg'    => __( 'Your browser is not supported. Please upload a photo instead.', 'buddypress' ),
				'videoerror'  => __( 'Video error. Please upload a photo instead.', 'buddypress' ),
				'ready'       => __( 'Your profile photo is ready. Click on the "Save" button to use this photo.', 'buddypress' ),
				'nocapture'   => __( 'No photo was captured. Click on the "Capture" button to take your photo.', 'buddypress' ),
			);
		}

		/**
		 * Use this filter to add a navigation to a custom tool to set the object's avatar.
		 *
		 * @since 2.3.0
		 *
		 * @param array $avatar_nav An associative array of available nav items where each item is an array organized this way:
		 * $avatar_nav[ $nav_item_id ] {
		 *     @type string $nav_item_id The nav item id in lower case without special characters or space.
		 *     @type string $caption     The name of the item nav that will be displayed in the nav.
		 *     @type int    $order       An integer to specify the priority of the item nav, choose one.
		 *                               between 1 and 99 to be after the uploader nav item and before the delete nav item.
		 *     @type int    $hide        If set to 1 the item nav will be hidden
		 *                               (only used for the delete nav item).
		 * }
		 * @param string $object the object the avatar belongs to (eg: user or group)
		 */
		$settings['nav'] = bp_sort_by_key( apply_filters( 'bp_attachments_avatar_nav', $avatar_nav, $object ), 'order', 'num' );

	// Specific to BuddyPress cover images
	} elseif ( 'bp_cover_image_upload' === $defaults['multipart_params']['action'] ) {

		// Cover images only need 1 file and 1 only!
		$defaults['multi_selection'] = false;

		// Default cover component is xprofile
		$cover_component = 'xprofile';

		// Get the object we're editing the cover image of
		$object = $defaults['multipart_params']['bp_params']['object'];

		// Set the cover component according to the object
		if ( 'group' === $object ) {
			$cover_component = 'groups';
		} elseif ( 'user' !== $object ) {
			$cover_component = apply_filters( 'bp_attachments_cover_image_ui_component', $cover_component );
		}
		// Get cover image advised dimensions
		$cover_dimensions = bp_attachments_get_cover_image_dimensions( $cover_component );

		// Set warning messages
		$strings['cover_image_warnings'] = apply_filters( 'bp_attachments_cover_image_ui_warnings', array(
			'dimensions'  => sprintf(
					__( 'For better results, make sure to upload an image that is larger than %1$spx wide, and %2$spx tall.', 'buddypress' ),
					(int) $cover_dimensions['width'],
					(int) $cover_dimensions['height']
				),
		) );
	}

	// Set Plupload settings
	$settings['defaults'] = $defaults;

	/**
	 * Enqueue some extra styles if required
	 *
	 * Extra styles need to be registered.
	 */
	if ( ! empty( $args['extra_css'] ) ) {
		foreach ( (array) $args['extra_css'] as $css ) {
			if ( empty( $css ) ) {
				continue;
			}

			wp_enqueue_style( $css );
		}
	}

	wp_enqueue_script ( 'bp-plupload' );
	wp_localize_script( 'bp-plupload', 'BP_Uploader', array( 'strings' => $strings, 'settings' => $settings ) );

	/**
	 * Enqueue some extra scripts if required
	 *
	 * Extra scripts need to be registered.
	 */
	if ( ! empty( $args['extra_js'] ) ) {
		foreach ( (array) $args['extra_js'] as $js ) {
			if ( empty( $js ) ) {
				continue;
			}

			wp_enqueue_script( $js );
		}
	}

	/**
	 * Fires at the conclusion of bp_attachments_enqueue_scripts()
	 * to avoid the scripts to be loaded more than once.
	 *
	 * @since 2.3.0
	 */
	do_action( 'bp_attachments_enqueue_scripts' );
}

/**
 * Check the current user's capability to edit an avatar for a given object.
 *
 * @since  2.3.0
 *
 * @param  string $capability The capability to check.
 * @param  array  $args       An array containing the item_id and the object to check.
 *
 * @return bool
 */
function bp_attachments_current_user_can( $capability, $args = array() ) {
	$can = false;

	if ( 'edit_avatar' === $capability || 'edit_cover_image' === $capability ) {
		/**
		 * Needed avatar arguments are set.
		 */
		if ( isset( $args['item_id'] ) && isset( $args['object'] ) ) {
			// Group profile photo
			if ( bp_is_active( 'groups' ) && 'group' === $args['object'] ) {
				if ( bp_is_group_create() ) {
					$can = (bool) groups_is_user_creator( bp_loggedin_user_id(), $args['item_id'] ) || bp_current_user_can( 'bp_moderate' );
				} else {
					$can = (bool) groups_is_user_admin( bp_loggedin_user_id(), $args['item_id'] ) || bp_current_user_can( 'bp_moderate' );
				}
			// User profile photo
			} elseif ( bp_is_active( 'xprofile' ) && 'user' === $args['object'] ) {
				$can = bp_loggedin_user_id() === (int) $args['item_id'] || bp_current_user_can( 'bp_moderate' );
			}
		/**
		 * No avatar arguments, fallback to bp_user_can_create_groups()
		 * or bp_is_item_admin()
		 */
		} else {
			if ( bp_is_group_create() ) {
				$can = bp_user_can_create_groups();
			} else {
				$can = bp_is_item_admin();
			}
		}
	}

	return apply_filters( 'bp_attachments_current_user_can', $can, $capability, $args );
}

/**
 * Send a JSON response back to an Ajax upload request.
 *
 * @since  2.3.0
 *
 * @param  bool  $success  True for a success, false otherwise.
 * @param  bool  $is_html4 True if the Plupload runtime used is html4, false otherwise.
 * @param  mixed $data     Data to encode as JSON, then print and die.
 */
function bp_attachments_json_response( $success, $is_html4 = false, $data = null ) {
	$response = array( 'success' => $success );

	if ( isset( $data ) ) {
		$response['data'] = $data;
	}

	// Send regular json response
	if ( ! $is_html4 ) {
		wp_send_json( $response );

	/**
	 * Send specific json response
	 * the html4 Plupload handler requires a text/html content-type for older IE.
	 * See https://core.trac.wordpress.org/ticket/31037
	 */
	} else {
		echo wp_json_encode( $response );

		wp_die();
	}
}

/**
 * Get an Attachment template part.
 *
 * @since 2.3.0
 *
 * @param string $slug Template part slug. eg 'uploader' for 'uploader.php'.
 *
 * @return bool
 */
function bp_attachments_get_template_part( $slug ) {
	$attachment_template_part = 'assets/_attachments/' . $slug;

	// Load the attachment template in WP Administration screens.
	if ( is_admin() && ( ! defined( 'DOING_AJAX' ) || ! DOING_AJAX ) ) {
		$attachment_admin_template_part = buddypress()->themes_dir . '/bp-legacy/buddypress/' . $attachment_template_part . '.php';

		// Check whether the template part exists.
		if ( ! file_exists( $attachment_admin_template_part ) ) {
			return false;
		}

		// Load the template part.
		require( $attachment_admin_template_part );

	// Load the attachment template in WP_USE_THEMES env.
	} else {
		bp_get_template_part( $attachment_template_part );
	}
}

/** Cover Image ***************************************************************/

/**
 * Get the cover image settings
 *
 * @since  2.4.0
 *
 * @param  string $component the component to get the settings for ("xprofile" for user or "groups")
 * @return array            the cover image settings
 */
function bp_attachments_get_cover_image_settings( $component = 'xprofile' ) {
	// Default parameters
	$args = array();

	// First look in BP Theme Compat
	$cover_image = bp_get_theme_compat_feature( 'cover_image' );

	if ( ! empty( $cover_image ) ) {
		$args = (array) $cover_image;
	}

	/**
	 * Then let people override/set the feature using this dynamic filter
	 *
	 * eg: for the user's profile cover image use :
	 * add_filter( 'bp_before_xprofile_cover_image_settings_parse_args', 'your_filter', 10, 1 );
	 *
	 * @since  2.4.0
	 *
	 * @param array $settings the cover image settings
	 */
	$settings = bp_parse_args( $args, array(
		'components'    => array(),
		'width'         => 1300,
		'height'        => 225,
		'callback'      => '',
		'theme_handle'  => '',
		'default_cover' => '',
	), $component . '_cover_image_settings' );

	if ( empty( $settings['components'] ) || empty( $settings['callback'] ) || empty( $settings['theme_handle'] ) ) {
		return false;
	}

	// Current component is not supported
	if ( ! in_array( $component, $settings['components'] ) ) {
		return false;
	}

	// Finally return the settings
	return $settings;
}

/**
 * Get cover image Width and Height
 *
 * @since  2.4.0
 *
 * @param  string $component the BuddyPress component concerned ("xprofile" for user or "groups")
 * @return array             an associative array containing the advised width and height for the cover image
 */
function bp_attachments_get_cover_image_dimensions( $component = 'xprofile' ) {
	// Let's prevent notices when setting the warning strings
	$default = array( 'width' => 0, 'height' => 0 );

	$settings = bp_attachments_get_cover_image_settings( $component );

	if ( empty( $settings ) ) {
		return false;
	}

	// Get width and height
	$wh = array_intersect_key( $settings, $default );

	/**
	 * Filter here to edit the cover image dimensions if needed.
	 *
	 * @since  2.4.0
	 *
	 * @param  array  $wh        an associative array containing the width and height values
	 * @param  array  $settings  an associative array containing all the feature settings
	 * @param  string $compnent  the requested component
	 */
	return apply_filters( 'bp_attachments_get_cover_image_dimensions', $wh, $settings, $component );
}

/**
 * Are we on a page to edit a cover image ?
 *
 * @since  2.4.0
 *
 * @return bool True if on a page to edit a cover image, false otherwise
 */
function bp_attachments_cover_image_is_edit() {
	$retval = false;

	$current_component = bp_current_component();
	if ( 'profile' === $current_component ) {
		$current_component = 'xprofile';
	}

	if ( ! bp_is_active( $current_component, 'cover_image' ) ) {
		return $retval;
	}

	if ( bp_is_user_change_cover_image() ) {
		$retval = ! bp_disable_cover_image_uploads();
	}

	if ( ( bp_is_group_admin_page() && 'group-cover-image' == bp_get_group_current_admin_tab() )
		|| ( bp_is_group_create() && bp_is_group_creation_step( 'group-cover-image' ) ) ) {
		$retval = ! bp_disable_group_cover_image_uploads();
	}

	return apply_filters( 'bp_attachments_cover_image_is_edit', $retval, $current_component );
}

/**
 * Does the user has a cover image ?
 *
 * @since  2.4.0
 *
 * @param  int $user_id
 * @return bool True if the user has a cover image, false otherwise
 */
function bp_attachments_get_user_has_cover_image( $user_id = 0 ) {
	if ( empty( $user_id ) ) {
		$user_id = bp_displayed_user_id();
	}

	$cover_src = bp_attachments_get_attachment( 'url', array(
		'item_id'   => $user_id,
	) );

	return (bool) apply_filters( 'bp_attachments_get_user_has_cover_image', $cover_src, $user_id );
}

/**
 * Does the group has a cover image ?
 *
 * @since  2.4.0
 *
 * @param  int $group_id
 * @return bool True if the group has a cover image, false otherwise
 */
function bp_attachments_get_group_has_cover_image( $group_id = 0 ) {
	if ( empty( $group_id ) ) {
		$group_id = bp_get_current_group_id();
	}

	$cover_src = bp_attachments_get_attachment( 'url', array(
		'object_dir' => 'groups',
		'item_id'    => $group_id,
	) );

	return (bool) apply_filters( 'bp_attachments_get_user_has_cover_image', $cover_src, $group_id );
}

/**
 * Generate the cover image file.
 *
 * @since 2.4.0
 *
 * @param  array  $args {
 *     @type string $file            The absolute path to the image. Required.
 *     @type string $component       The component for the object (eg: groups, xprofile). Required.
 *     @type string $cover_image_dir The Cover image dir to write the image into. Required.
 * }
 * @param  BP_Attachment_Cover_Image $cover_image_class The class to use to fit the cover image.
 * @return bool|array          An array containing cover image data on success, false otherwise.
 */
function bp_attachments_cover_image_generate_file( $args = array(), $cover_image_class = null ) {
	// Bail if an argument is missing
	if ( empty( $args['file'] ) || empty( $args['component'] ) || empty( $args['cover_image_dir'] ) ) {
		return false;
	}

	// Get advised dimensions for the cover image
	$dimensions = bp_attachments_get_cover_image_dimensions( $args['component'] );

	// No dimensions or the file does not match with the cover image dir, stop!
	if ( false === $dimensions || $args['file'] !== $args['cover_image_dir'] . '/' . wp_basename( $args['file'] ) ) {
		return false;
	}

	if ( ! is_a( $cover_image_class, 'BP_Attachment_Cover_Image' ) ) {
		$cover_image_class = new BP_Attachment_Cover_Image();
	}

	// Make sure the file is inside the Cover Image Upload path.
	if ( false === strpos( $args['file'], $cover_image_class->upload_path ) ) {
		return false;
	}

	// Resize the image so that it fit with the cover image dimensions
	$cover_image  = $cover_image_class->fit( $args['file'], $dimensions );
	$is_too_small = false;

	// Image is too small in width and height
	if ( empty( $cover_image ) ) {
		$cover_file = $cover_image_class->generate_filename( $args['file'] );
		@rename( $args['file'], $cover_file );

		// It's too small!
		$is_too_small = true;
	} elseif ( ! empty( $cover_image['path'] ) ) {
		$cover_file = $cover_image['path'];

		// Image is too small in width or height
		if ( $cover_image['width'] < $dimensions['width'] || $cover_image['height'] < $dimensions['height'] ) {
			$is_too_small = true;
		}
	}

	// We were not able to generate the cover image file.
	if ( empty( $cover_file ) ) {
		return false;
	}

	// Do some clean up with old cover image, now a new one is set.
	$cover_basename = wp_basename( $cover_file );

	if ( $att_dir = opendir( $args['cover_image_dir'] ) ) {
		while ( false !== ( $attachment_file = readdir( $att_dir ) ) ) {
			// skip directories and the new cover image
			if ( 2 < strlen( $attachment_file ) && 0 !== strpos( $attachment_file, '.' ) && $cover_basename !== $attachment_file ) {
				@unlink( $args['cover_image_dir'] . '/' . $attachment_file );
			}
		}
	}

	// Finally return needed data.
	return array(
		'cover_file'     => $cover_file,
		'cover_basename' => $cover_basename,
		'is_too_small'   => $is_too_small
	);
}

/**
 * Ajax Upload and set a cover image
 *
 * @since  2.4.0
 *
 * @return  string|null A json object containing success data if the upload succeeded
 *                      error message otherwise.
 */
function bp_attachments_cover_image_ajax_upload() {
	// Bail if not a POST action
	if ( 'POST' !== strtoupper( $_SERVER['REQUEST_METHOD'] ) ) {
		wp_die();
	}

	/**
	 * Sending the json response will be different if
	 * the current Plupload runtime is html4
	 */
	$is_html4 = false;
	if ( ! empty( $_POST['html4' ] ) ) {
		$is_html4 = true;
	}

	// Check the nonce
	check_admin_referer( 'bp-uploader' );

	// Init the BuddyPress parameters
	$bp_params = array();

	// We need it to carry on
	if ( ! empty( $_POST['bp_params'] ) ) {
		$bp_params = bp_parse_args( $_POST['bp_params'], array(
			'object'  => 'user',
			'item_id' => bp_loggedin_user_id(),
		), 'attachments_cover_image_ajax_upload' );
	} else {
		bp_attachments_json_response( false, $is_html4 );
	}

	// We need the object to set the uploads dir filter
	if ( empty( $bp_params['object'] ) ) {
		bp_attachments_json_response( false, $is_html4 );
	}

	// Capability check
	if ( ! bp_attachments_current_user_can( 'edit_cover_image', $bp_params ) ) {
		bp_attachments_json_response( false, $is_html4 );
	}

	$bp          = buddypress();
	$needs_reset = array();

	// Member's cover image
	if ( 'user' === $bp_params['object'] ) {
		$object_data = array( 'dir' => 'members', 'component' => 'xprofile' );

		if ( ! bp_displayed_user_id() && ! empty( $bp_params['item_id'] ) ) {
			$needs_reset = array( 'key' => 'displayed_user', 'value' => $bp->displayed_user );
			$bp->displayed_user->id = $bp_params['item_id'];
		}

	// Group's cover image
	} elseif ( 'group' === $bp_params['object'] ) {
		$object_data = array( 'dir' => 'groups', 'component' => 'groups' );

		if ( ! bp_get_current_group_id() && ! empty( $bp_params['item_id'] ) ) {
			$needs_reset = array( 'component' => 'groups', 'key' => 'current_group', 'value' => $bp->groups->current_group );
			$bp->groups->current_group = groups_get_group( array(
				'group_id'        => $bp_params['item_id'],
				'populate_extras' => false,
			) );
		}

	// Other object's cover image
	} else {
		$object_data = apply_filters( 'bp_attachments_cover_image_object_dir', array(), $bp_params['object'] );
	}

	// Stop here in case of a missing parameter for the object
	if ( empty( $object_data['dir'] ) || empty( $object_data['component'] ) ) {
		bp_attachments_json_response( false, $is_html4 );
	}

	$cover_image_attachment = new BP_Attachment_Cover_Image();
	$uploaded = $cover_image_attachment->upload( $_FILES );

	// Reset objects
	if ( ! empty( $needs_reset ) ) {
		if ( ! empty( $needs_reset['component'] ) ) {
			$bp->{$needs_reset['component']}->{$needs_reset['key']} = $needs_reset['value'];
		} else {
			$bp->{$needs_reset['key']} = $needs_reset['value'];
		}
	}

	if ( ! empty( $uploaded['error'] ) ) {
		// Upload error response
		bp_attachments_json_response( false, $is_html4, array(
			'type'    => 'upload_error',
			'message' => sprintf( __( 'Upload Failed! Error was: %s', 'buddypress' ), $uploaded['error'] ),
		) );
	}

	// Default error message
	$error_message = __( 'There was a problem uploading the cover image.', 'buddypress' );

	// Get BuddyPress Attachments Uploads Dir datas
	$bp_attachments_uploads_dir = bp_attachments_uploads_dir_get();

	// The BP Attachments Uploads Dir is not set, stop.
	if ( ! $bp_attachments_uploads_dir ) {
		bp_attachments_json_response( false, $is_html4, array(
			'type'    => 'upload_error',
			'message' => $error_message,
		) );
	}

	$cover_subdir = $object_data['dir'] . '/' . $bp_params['item_id'] . '/cover-image';
	$cover_dir    = trailingslashit( $bp_attachments_uploads_dir['basedir'] ) . $cover_subdir;

	if ( ! is_dir( $cover_dir ) ) {
		// Upload error response
		bp_attachments_json_response( false, $is_html4, array(
			'type'    => 'upload_error',
			'message' => $error_message,
		) );
	}

	/**
	 * Generate the cover image so that it fit to feature's dimensions
	 *
	 * Unlike the Avatar, Uploading and generating the cover image is happening during
	 * the same Ajax request, as we already instantiated the BP_Attachment_Cover_Image
	 * class, let's use it.
	 */
	$cover = bp_attachments_cover_image_generate_file( array(
		'file'            => $uploaded['file'],
		'component'       => $object_data['component'],
		'cover_image_dir' => $cover_dir
	), $cover_image_attachment );

	if ( ! $cover ) {
		// Upload error response
		bp_attachments_json_response( false, $is_html4, array(
			'type'    => 'upload_error',
			'message' => $error_message,
		) );
	}

	// Build the url to the file
	$cover_url = trailingslashit( $bp_attachments_uploads_dir['baseurl'] ) . $cover_subdir . '/' . $cover['cover_basename'];

	// Init Feedback code, 1 is success
	$feedback_code = 1;

	// 0 is the size warning
	if ( $cover['is_too_small'] ) {
		$feedback_code = 0;
	}

	// Set the name of the file
	$name = $_FILES['file']['name'];
	$name_parts = pathinfo( $name );
	$name = trim( substr( $name, 0, - ( 1 + strlen( $name_parts['extension'] ) ) ) );

	/**
	 * Fires if the new cover image was successfully uploaded.
	 *
	 * The dynamic portion of the hook will be xprofile in case of a user's
	 * cover image, groups in case of a group's cover image. For instance:
	 * Use add_action( 'xprofile_cover_image_uploaded' ) to run your specific
	 * code once the user has set his cover image.
	 *
	 * @since 2.4.0
	 *
	 * @param int $item_id Inform about the item id the cover image was set for.
	 */
	do_action( $object_data['component'] . '_cover_image_uploaded', (int) $bp_params['item_id'] );

	// Finally return the cover image url to the UI
	bp_attachments_json_response( true, $is_html4, array(
		'name'          => $name,
		'url'           => $cover_url,
		'feedback_code' => $feedback_code,
	) );
}
add_action( 'wp_ajax_bp_cover_image_upload', 'bp_attachments_cover_image_ajax_upload' );

/**
 * Ajax delete a cover image for a given object and item id.
 *
 * @since 2.4.0
 *
 * @return string|null A json object containing success data if the cover image was deleted
 *                     error message otherwise.
 */
function bp_attachments_cover_image_ajax_delete() {
	// Bail if not a POST action.
	if ( 'POST' !== strtoupper( $_SERVER['REQUEST_METHOD'] ) ) {
		wp_send_json_error();
	}

	$cover_image_data = $_POST;

	if ( empty( $cover_image_data['object'] ) || empty( $cover_image_data['item_id'] ) ) {
		wp_send_json_error();
	}

	// Check the nonce
	check_admin_referer( 'bp_delete_cover_image', 'nonce' );

	// Capability check
	if ( ! bp_attachments_current_user_can( 'edit_cover_image', $cover_image_data ) ) {
		wp_send_json_error();
	}

	// Set object for the user's case
	if ( 'user' === $cover_image_data['object'] ) {
		$component = 'xprofile';
		$dir       = 'members';

	// Set it for any other cases
	} else {
		$component = $cover_image_data['object'] . 's';
		$dir       = $component;
	}

	// Handle delete
	if ( bp_attachments_delete_file( array( 'item_id' => $cover_image_data['item_id'], 'object_dir' => $dir, 'type' => 'cover-image' ) ) ) {

		// Defaults no cover image
		$response = array(
			'reset_url'     => '',
			'feedback_code' => 3 ,
		);

		// Get cover image settings in case there's a default header
		$cover_params = bp_attachments_get_cover_image_settings( $component );

		// Check if there's a default cover
		if ( ! empty( $cover_params['default_cover'] ) ) {
			$response['reset_url'] = $cover_params['default_cover'];
		}

		// Finally send the reset url
		wp_send_json_success( $response );

	} else {
		wp_send_json_error( array(
			'feedback_code' => 2,
		) );
	}
}
add_action( 'wp_ajax_bp_cover_image_delete', 'bp_attachments_cover_image_ajax_delete' );
