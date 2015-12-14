<?php
/**
 * cc2 Updater
 * Small helper library for transition from cc1 to cc2
 * 
 * @author Fabian Wolf
 * @since 2.0r1
 * @package cc2
 */
 
class updateHelper {
	
	var $arrSettings = array(),
		$classPrefix = 'cc2_theme_update_',
		$classVersion = '0.1',
		$is_test_run = false;
	
	function __construct() {
		// nothing to do .. yet

			
		// save settings
		//add_action( 'wp_ajax_' . $this->classPrefix . 'exec', array( $this, 'ajax_run_update') );
		//add_action( 'wp_ajax_nopriv_' . $this->classPrefix . 'exec', array( $this, 'ajax_run_update') );*/
		
	}
	
	
	
	
	public function load_settings( $arrParams ) {		
		
		if( !empty( $arrParams ) ) {
			//$this->arrSettings = $arrParams;
			if( isset( $arrParams['import_options'] ) ) {
				$this->arrImportOptions = $arrParams['import_options'];
			}
			
			if( !empty( $arrParams['test_run'] ) ) {
				$this->is_test_run = $arrParams['test_run'];
			}

		}
	}

	/**
	 * Check if there are actually ANY old settings
	 */
	
	public static function has_importable_settings( $arrImportData = array() ) {
		$return = false;
		
		if( !empty( $arrImportData ) && is_array( $arrImportData) ) {
			// cycle through all update data and look up each old option name
			foreach( $arrImportData as $strSection => $arrSectionData ) {
				if( isset( $arrSectionData['old_option_name'] ) ) {
					$oldThemeOptionData = get_option( $arrSectionData['old_option_name'], false );
					
					if( !empty( $oldThemeOptionData ) ) {
						$arrReturn[ $strSection ] = ( isset($arrSectionData['title']) ? $arrSectionData['title'] : 
							ucwords( str_replace('_', ' ', $strSection ) )
						);
					}	
				}
			}
			
			if( !empty( $arrReturn ) ) {
				$return = $arrReturn;
			}
		}
		
		
		return $return;
	}

	
	/**
	 * Convert a given value to a new value set if possible
	 * 
	 * @param array $old_to_new	Associative array; array( 'old_value' => 'new_value', 'other_old_value' => 'other_new_value', ... )
	 */
	
	public static function convert_value_choice( $value, $old_to_new = array() ) {
		$return = $value;
		
		if( array_key_exists( $value, $old_to_new ) != false ) {
			$return = $old_to_new[ $value ];
		}
		
		return $return;
	}
	
	public static function filter_callback( $value, $filter_callback = null ) {
		$return = $value;
		
		if( !empty( $filter_callback ) ) {
			$return = apply_filters( $filter_callback, $return );
		}
		
		return $return;
	}
	
	public static function handle_callback( $value, $callback = false ) {
		$return = $value;
		
		if( !empty( $callback ) ) {
			if( !is_array( $callback ) ) { // regular function or a method of the current class
				if( function_exists( $callback ) ) {
					$return = call_user_func( $callback, $value );
				}
				/* elseif( method_exists( self, $callback ) ) {
					$return = self::$callback( $value );
				}*/
			} else { // external class
				$return = call_user_func_array( $callback, $value );
			}
		}
		
		return $return;
	}
	
	
	public function update_option( $option_name, $data, $test = false ) {
		$return = array(
			'type' => 'error',
			'message' => 'Missing data!',
		);
		
		if( !empty( $data ) ) {
			if( !empty( $test ) ) {
				
				
				$option_name = $this->classPrefix . 'test_' . $option_name;
				set_transient( $option_name, $data );
				
				$test_result = get_transient( $option_name, $data );
				
				if( $test_result == $data ) {
					$return = array(
						'type' => 'success test-run',
						'message' => 'Test run was successful!',
					);
				}
				delete_transient( $option_name );
			} else { // not a test!
				// incorporate old with new data
				$current_data = get_option( $option_name, array() );
				
				if( !empty( $current_data ) ) {
					foreach( $data as $setting_name => $setting_value ) {
						$current_data[ $setting_name ] = $setting_value;	
					}
				} else {
					$current_data = $data;
				}
				
				
				$result = update_option( $option_name, $current_data, false );
				
				if( $result != false ) {
					$return = array(
						'type' => 'success updated',
						'message' => 'Successfully updated or added any available settings.',
					);
				}
				
			}
		}
		
		return $return;
	}
	
	/**
	 * NOTE: $max_version is actually the maximum version + 1
	 * 
	 * @param string $min_version	Minimum version to test for. 
	 * @param string $max_version	Maximum version - ie. any version LOWER than this one.
	 * 
	 * @return boolean $return		Returns true if version neither was too high nor too low, and false in any other case.
	 */
	
	public static function is_correct_version( $min_version = '2.0', $max_version = '2.1' ) {
		$return = true;

		// check version of current theme
		if( !defined('CC2_THEME' )  ) { // prolly NOT the right theme ^_^
			self::debug ('CC2_THEME missing');
			$return = false;
		} else {
			// update script is only applicable for 2.0, later versions require the respective update script (eg. cc21-update.php)
			//if( version_compare( CC_THEME, '2.1', '>=') ) { // output message and quit
			if( version_compare( CC2_THEME, $max_version, '>=') ) { // output message and quit
				self::debug( array('CC2_THEME' => CC2_THEME, 'max_version' => $max_version, 'comparison result' => version_compare( CC2_THEME, $max_version, '>=') ), 'version compare max vs. current theme' );
				
				$return = false;
			}
			
			// not the first run
			/*if( version_compare( CC_THEME, $max_version, '<') && get_transient('cc2_theme_activated', false ) == true ) {
				// output message and quit
				
				$return = false;
			}*/
		}
		
		return $return;
		
	}
	
	public static function sanitize_array_key( $text = false ) {
		$return = $text;
		
		if( !empty( $return ) ) {
			$return = str_replace( array(' ', '--'), '-', strtolower($return) );
			$return = sanitize_key( $return );
		}
		
		return $return;
	}
	
	/**
	 * Adapted redux version of the regular debug class / plugin
	 * 
	 * Applies the following filter hooks:
	 * 
	 * @hook cc2_debug_return_data_raw				Unfiltered debug data (basically print_r( $data ))
	 * @hook cc2_debug_return_data_filtered			Using the modern htmlentities2 function.
	 * @hook cc2_debug_return_data_htmlentities		If out of some strange reasone the wp-based htmlentities2() is not available (eg. while bootstrapping WP and other nasty stuff), the fallback to the regular, less considerate htmlentities is being used.
	 * @hook cc2_debug_return						The final "product".
	 * 
	 * Actions:
	 * @hook cc2_debug_before_return				Runs AFTER cc2_debug_return, but before the data is being return-ed (ie. echoed)
	 * @hook cc2_debug_shutting_down				Runs at the very end of the function.
	 */
	 
	public static function debug( $data, $title = 'Debug: ', $arrParams = false ) {
		$arrDefaultParams = array(
			'auto_hide' => true,
			'capability' => 'manage_options',
			'class' => 'cc2_theme__debug',
		);
		
		extract( wp_parse_args( $arrDefaultParams, $arrParams ) );
		
		if( $arrParams == false && defined('CC2_THEME_DEBUG') ) {
			$auto_hide = false;
		}
		

		$return = '<div class="' . $class . '"';
		
		if( $auto_hide != false ) {
			$return .= ' style="display:none;"';
		}
		
		$return .= '><p class="debug-title">' . $title . '</p><pre class="debug-content">';
		
		$raw_debug_data = apply_filters('cc2_debug_return_data_raw', print_r($data, true) );
		
		if( function_exists( 'htmlentities2' ) ) {
			$return_debug = apply_filters( 'cc2_debug_return_data_filtered', htmlentities2( $raw_debug_data ) );
		} else {
			$return_debug = apply_filters( 'cc2_debug_return_data_htmlentities', htmlentities(  $raw_debug_data) );
		}
		
		
		
		$return .= $return_debug . '</pre></div>';


		// add filter to output
		$return = apply_filters('cc2_debug_return', $return ); // for hooking up plugins, eg. a logging plugin

		do_action('cc2_debug_before_return' );

		if( !empty( $return ) ) {
			echo $return;
		}
		
		do_action('cc2_debug_shutting_down' );
	}
	
}
