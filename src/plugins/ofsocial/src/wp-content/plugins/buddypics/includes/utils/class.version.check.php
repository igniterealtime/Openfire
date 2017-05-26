<?php

/**
 * BP-ALBUM VERSIONS CLASS
 * Checks that all plugins, API's, and services on the host system are the minimum
 * versions needed for BP-Album to run properly
 *
 * @since 0.1.8.12
 * @package BP-Album
 * @subpackage Util
 * @license GPL v2.0
 * @link http://code.google.com/p/buddypress-media/
 *
 * ========================================================================================================
 */

class BPA_version {

	var $min_php_ver = "5.2.0";	    // Minimum PHP Version required to run BP-Album
	var $min_sql_ver = "5.0.15";	    // Minimum SQL Version required to run BP-Album
	var $min_wp_ver = "3.2";	    // Minimum WordPress Version required to run BP-Album
	var $min_bp_ver = "1.5";	    // Minimum BuddyPress Version required to run BP-Album
	var $min_gd_ver = "2";	    // Minimum GDLib Version required to run BP-Album


	public function  __construct() {}


	/**
	 * Compares two version numbers using a supplied comparison operator. This
	 * function does not have the problematic "3.1" < "3.1.0" behavior that
	 * PHP's version of the function displays.
	 *
	 * @since 0.1.8.12
	 * @param string $ver1 | reference string
	 * @param string $ver2 | comparison string
	 * @param string $op | comparison operator: ">=", "<=", ">", "<", "=", "!="
	 * @return bool $result| result of $ver1 [comparison] $ver2
	 */

	public function checkVersion($ver1, $ver2, $op, &$error=null) {


		$valid_ops = array(">=", "<=", ">", "<", "=", "!=");

		if( array_search($op, $valid_ops) === false ){

			$error = array(
					'numeric'=>1,
					'text'=>"Called with invalid comparison operator. ",
					'data'=>array("ver1"=>$ver1, "ver2"=>$ver2, "op"=>$op),
					'file'=>__FILE__, 'line'=>__LINE__, 'method'=>__METHOD__,
			);
			return false;
		}


		// Pre-process the version strings
		// ================================================================================

		$versions = array( "ver1"=>$ver1, "ver2"=>$ver2 );

		foreach($versions as $key => $val){


			// Make absolutely sure PHP treats the input data as a string
			$val = (string)$val;

			// Make sure nobody tries to slip in a rouge Omega symbol
			$val = str_replace("Ω", "", $val);

			// Convert all plausible separator characters to Omega symbols (Ω). It has to be done this way
			// because every single separator charactor has a control function in PCRE
			$separators = array("-", "_", "+", "/", ",", ".", "\\");
			$val = str_replace($separators, "Ω", $val);

			// Remove any remaining non-alphanumeric characters, including spaces
			$val = preg_replace( '|[^a-zA-Z0-9Ω]|', '', $val );

			// Convert each group of one or more Ω separator characters into single "." character. This handles
			// accidental or repeated separators in the version string.
			$val = preg_replace('|Ω+|', '.', $val);

			// Convert all letters to lower case
			$val = strtolower($val);

			$versions[$key] = $val;


		}
		unset($key, $val);


		// Explode version strings into arrays. Add padding keys filled with (int)0 so each
		// version array has the same number of numeric groups.
		// ================================================================================

		$v1 = explode('.', $versions["ver1"]);
		$v1_size = count($v1);

		$v2 = explode('.', $versions["ver2"]);
		$v2_size = count($v2);

		if($v1_size > $v2_size){

			$diff = $v1_size - $v2_size;

			for($i=1; $i<=$diff; $i++){
				$v2[] = "0";
			}
		}
		elseif($v2_size > $v1_size){

			$diff = $v2_size - $v1_size;

			for($i=1; $i<=$diff; $i++){
				$v1[] = "0";
			}
		}

		// Do the comparison operation. Note array is LTR ordered, and the
		// left-most term is the most significant term.
		// =======================================================================

		$lt_found = false;
		$gt_found = false;
		$gt_pos = 0;
		$lt_pos = 0;

		switch($op){

			// Greater Than or Equal To ">="
			//========================================================
			case ">=" : {

				foreach ($v1 as $key => $val_1) {

					$val_2 = $v2[$key];

					if( $val_1 < $val_2){

						if($lt_pos < $key){
							$lt_pos = $key;
						}

						$lt_found = true;
					}
					elseif( $val_1 > $val_2){

						if($gt_pos < $key){
							$gt_pos = $key;
						}

						$gt_found = true;
					}
				}


				if(!$lt_found && !$gt_found){
					$result = true;
				}
				elseif($gt_found && !$lt_found){
					$result = true;
				}
				elseif($gt_found && $lt_found){

				    if ($v1 > $v2){

				    return true;
				    }
				    else{
					return false;
					}
				}
				else {
					if($gt_pos < $lt_pos){
						$result = true;
					}
					else {
						$result = false;
					}
				}

			} break;

			// Less Than or Equal To "<="
			//========================================================
			case "<=" : {

				foreach ($v1 as $key => $val_1) {

					$val_2 = $v2[$key];

					if( $val_1 < $val_2){

						if($lt_pos < $key){
							$lt_pos = $key;
						}

						$lt_found = true;
					}
					elseif( $val_1 > $val_2){

						if($gt_pos < $key){
							$gt_pos = $key;
						}

						$gt_found = true;
					}
				}


				if(!$lt_found && !$gt_found){
					$result = true;
				}
				elseif($lt_found && !$gt_found){
					$result = true;
				}
				elseif($gt_found && $lt_found){

				    if ($v2 > $v1){

				    return true;
				    }
				    else{
					return false;
					}
				}
				else {
					if($lt_pos < $gt_pos){
						$result = true;
					}
					else {
						$result = false;
					}
				}

			} break;

			// Greater Than ">"
			//========================================================
			case ">"  : {

				foreach ($v1 as $key => $val_1) {

					$val_2 = $v2[$key];

					if( $val_1 < $val_2){

						if($lt_pos < $key){
							$lt_pos = $key;
						}

						$lt_found = true;
					}
					elseif( $val_1 > $val_2){

						if($gt_pos < $key){
							$gt_pos = $key;
						}

						$gt_found = true;
					}
				}


				if(!$gt_found){
					$result = false;
				}
				elseif($gt_found && !$lt_found){
					$result = true;
				}
				elseif($gt_found && $lt_found){

				    if ($v1 < $v2){

				    return false;
				    }
				    else{
					return true;
					}
				}
				else {
					if($gt_pos < $lt_pos){
						$result = true;
					}
					else {
						$result = false;
					}
				}


			} break;

			// Less Than "<"
			//========================================================
			case "<"  : {

				foreach ($v1 as $key => $val_1) {

					$val_2 = $v2[$key];

					if( $val_1 < $val_2){

						if($lt_pos < $key){
							$lt_pos = $key;
						}

						$lt_found = true;
					}
					elseif( $val_1 > $val_2){

						if($gt_pos < $key){
							$gt_pos = $key;
						}

						$gt_found = true;
					}
				}


				if(!$lt_found){
					$result = false;
				}
				elseif($lt_found && !$gt_found){
					$result = true;
				}
				elseif($gt_found && $lt_found){

				    if ($v2 > $v1){

				    return true;
				    }
				    else{
					return false;
					}
				}
				else {
					if($lt_pos < $gt_pos){
						$result = true;
					}
					else {
						$result = false;
					}
				}

			} break;

			// Equal To "="
			//========================================================
			case "="  : {

				$result = true;

				foreach ($v1 as $key => $val_1) {

					$val_2 = $v2[$key];

					// If any of the blocks is not equal,
					// then the entire string is fails

					if( $val_1 != $val_2 ){
						$result = false;
					}

				}


			} break;

			// Not Equal To "!="
			//========================================================
			case "!=" : {

				$result = false;

				foreach ($v1 as $key => $val_1) {

					$val_2 = $v2[$key];

					// If any of the blocks is not equal,
					// then the entire string passes

					if( ($val_1 != $val_2) ){
						$result = true;
					}
				}

			} break;

		} // END: switch($op)

		return $result;


	} // END function checkVersionNumber()


	/**
	 * Get which version of PHP is installed
	 *
	 * @since 0.1.8.12
	 * @return string | PHP Version
	 */

	public function getPHPVersion() {

		return PHP_VERSION;
	}


	/**
	 * Get which version of WordPress is installed
	 *
	 * @since 0.1.8.12
	 * @return string | WordPress Version
	 */

	public function getWPVersion() {

		global $wp_version;
		return $wp_version;
	}


	/**
	 * Get which version of BuddyPress is installed
	 *
	 * @since 0.1.8.12
	 * @return string | BuddyPress Version
	 */

	public function getBPVersion() {

		return BP_VERSION;
	}


	/**
	 * Get which version of MySQL is installed
	 *
	 * @since 0.1.8.12
	 * @return string | MySQL Version
	 */

	public function getSQLVersion() {

		global $wpdb;
		return $wpdb->db_version();
	}


	/**
	 * Get which version of Apache is installed
	 *
	 * @since 0.1.8.12
	 * @return string | Apache Version
	 */

	public function getApacheVersion() {

		return apache_get_version();
	}


	/**
	 * Get which version of GD is installed
	 *
	 * @since 0.1.8.12
	 * @return string | GD version, or 0 if not installed.
	 */

	public function getGDVersion() {

		// Handle GD not being installed or loaded
		if(!extension_loaded('gd')) {
			return 0;
		}

		// If installed version of PHP is current, we can use gd_info()
		if(function_exists('gd_info')) {

			$ver_info = gd_info();
			preg_match('/\d/', $ver_info['GD Version'], $match);
			$gd_ver = $match[0];
			return $match[0];
		}

		// If gd_info() is disabled, the user's PHP installation is probably out of date, but its still
		// worth checking in case someone has a really unusual setup. If the user has phpinfo() enabled on
		// their system, use it to fetch the GD version number. Otherwise, quit.
		if(!preg_match('/phpinfo/', ini_get('disable_functions'))) {

			ob_start();
			phpinfo(8);
			$info = ob_get_contents();
			ob_end_clean();
			$info = stristr($info, 'gd version');
			preg_match('/\d/', $info, $match);
			$gd_ver = $match[0];
			return $match[0];

		}
		else {

			return 0;
		}

	}


	/**
	 * Checks that PHP is the minimum version needed for BP-Album to run properly
	 *
	 * @since 0.1.8.12
	 * @return bool | False on failure. True on success.
	 */

	public function phpOK() {

		if( self::checkVersion( self::getPHPVersion(), $this->min_php_ver, '>=') == true )
		{
			return true;
		}
		else {
			return false;
		}

	}


	/**
	 * Checks that MySQL is the minimum version needed for BP-Album to run properly
	 *
	 * @since 0.1.8.12
	 * @return bool | False on failure. True on success.
	 */

	public function sqlOK() {

		if( self::checkVersion( self::getSQLVersion(), $this->min_sql_ver, '>=') == true )
		{
			return true;
		}
		else {
			return false;
		}

	}


	/**
	 * Checks that WordPress is the minimum version needed for BP-Album to run properly
	 *
	 * @since 0.1.8.12
	 * @return bool | False on failure. True on success.
	 */

	public function wpOK() {

		if( self::checkVersion( self::getWPVersion(), $this->min_wp_ver, '>=') == true )
		{
			return true;
		}
		else {
			return false;
		}

	}


	/**
	 * Checks that BuddyPress is the minimum version needed for BP-Album to run properly
	 *
	 * @since 0.1.8.12
	 * @return bool | False on failure. True on success.
	 */

	public function bpOK() {

		if( self::checkVersion( self::getBPVersion(), $this->min_bp_ver, '>=') == true )
		{
			return true;
		}
		else {
			return false;
		}

	}


	/**
	 * Checks that GD is the minimum version needed for BP-Album to run properly
	 *
	 * @since 0.1.8.12
	 * @return bool | False on failure. True on success.
	 */

	public function gdOK() {

		if( self::checkVersion( self::getGDVersion(), $this->min_gd_ver, '>=') == true )
		{
			return true;
		}
		else {
			return false;
		}

	}


	/**
	 * Checks that all plugins, API's, and services on the host system are the minimum
	 * versions needed for BP-Album to run properly
	 *
	 * @since 0.1.8.12
	 * @return bool | False on failure. True on success.
	 */

	public function allOK() {

		if( (self::phpOK() == true)
		&&  (self::sqlOK() == true)
		&&  (self::wpOK() == true)
		&&  (self::bpOK() == true)
		&&  (self::gdOK() == true))
		{
			return true;
		}
		else {
			return false;
		}

	}

} // End of class BPA_version

?>