<?php

/**
 * Description of RTMediaPLUploadHelper
 *
 * Helper class for PL Upload - Upload files via AJAX Request
 *
 * @author Udit Desai <udit.desai@rtcamp.com>
 */
class RTMediaUploadHelper {

	/**
	 *
	 */
	public function __construct() {

	}

	/**
	 *
	 */
	static function file_upload() {

		$end_point = new RTMediaUploadEndpoint();
		$end_point->template_redirect();
	}
}