<?php

/**
 * Description of RTMediaUploadException
 *
 * @author joshua
 */
class RTMediaUploadException extends Exception {
	/**
	 *
	 * @var type
	 *
	 * Exception for Invalid context while uploading any media
	 */
	var $upload_err_invalid_context = 9;

	/**
	 * Constructs the class.
	 *
	 * @param type $code
	 * @param type $msg
	 */
	public function __construct( $code, $msg = false ){
		$message = $this->codeToMessage( $code, $msg );
		parent::__construct( $message, $code );
	}

	/**
	 * Error specific Message generated for the exception depending upon the code passed.
	 * Native Error Codes defined in PHP core module are used for uploading a standard file
	 *
	 * @param type $code
	 * @param type $msg
	 *
	 * @return type
	 */
	private function codeToMessage( $code, $msg ){
		switch ( $code ) {
			case UPLOAD_ERR_INI_SIZE:
			case UPLOAD_ERR_FORM_SIZE:
				$message = apply_filters( 'bp_media_file_size_error', __( 'The uploaded file exceeds the MAX_FILE_SIZE directive that was specified in the HTML form', 'buddypress-media' ) );
				break;
			case UPLOAD_ERR_NO_FILE:
				$message = apply_filters( 'bp_media_file_null_error', __( 'No file was uploaded', 'buddypress-media' ) );
				break;
			case UPLOAD_ERR_PARTIAL:
			case UPLOAD_ERR_NO_TMP_DIR:
			case UPLOAD_ERR_CANT_WRITE:
				$message = apply_filters( 'bp_media_file_internal_error', __( 'Uploade failed due to internal server error.', 'buddypress-media' ) );
				break;
			case UPLOAD_ERR_EXTENSION:
				$message = apply_filters( 'bp_media_file_extension_error', __( 'File type not allowed.', 'buddypress-media' ) );
				break;

			case $this->upload_err_invalid_context:
				$message = apply_filters( 'rtmedia_invalid_context_error', __( 'Invalid Context for upload.', 'buddypress-media' ) );
				break;
			default:
				$msg     = $msg ? $msg : __( 'Unknown file upload error.', 'buddypress-media' );
				$message = apply_filters( 'bp_media_file_unknown_error', $msg );
				break;
		}

		return $message;
	}
}