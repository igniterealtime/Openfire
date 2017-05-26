<?php
/**
 * Alternative theme settings handler
 * An alternative, improved wrapper and handler for the theme settings (get_theme_mod / set_theme_mod). Includes comparision options and also optionally hooks into the CC2 Backup Settings API.
 * @author Fabian Wolf
 * @since 2.0.25
 * @package cc2
 */
 
class ThemeSettings {
	
	/**
	 *
	 * Syntax: $param = array('parameter_name' => variable1, 'param_nam2' => variable2, ... 'param_namN' => variableN );
	 *
	 * @param array $compare		Array('value1', 'value2') => $value1 == $value2
	 * @param bool not_empty 		Compares $compare => !empty( $value1 ) && $value1 == $value2
	 * @param array empty			Array('value1', 'value2') => empty( $value1 ) && value1 == $value2
	 * @param array both_not_empty  Array('value1', 'value2') => !empty( $value1 ) && !empty( $value2 ) && value1 == $value2
	 * @return bool $result			Returns true if both the modifier AND the comparision is true. In any other case, false is returned.
	 */

	public static function compare_theme_setting( $setting_name, $compare_value = null, $modifier = 'not_empty' ) {
		$return = false;
		
		if( self::has_theme_setting( $setting_name ) != false ) {
			$setting_value = self::get_theme_setting( $setting_name );
			
			switch( $modifier ) {
				case 'not_empty': // first value not empty
					if( !empty( $setting_value ) ) {
						$return = true;
					}
				
					break;
				case 'empty': // first value empty
					if( empty( $setting_value ) ) {
						$return = true;
					}
					break;
				case 'strict':
				case 'both_not_empty': // both values not empty
					if( !empty( $setting_value ) && !empty( $compare_value ) ) {
						$return = true;
					}
				
					break;
				default: // no primary check
					break;
			}
			
			// compare didn't fail - or wasnt used anyway
			if( $return != false ) {
				if( $modifier == 'strict' ) {
					if( $setting_value === $compare_value ) {
						$return = true;
					}
					
				} elseif( $modifier == 'strict_string' ) {
					if( !is_object( $setting_value ) && !is_object( $compare_value ) ) {
					
						if( (string) $setting_value === (string) $compare_value ) {
							$return = true;
						}
						
					}
					
				} else {
					if( $setting_value == $compare_value ) {
						$return = true;
					}
				}	
			}
		
		}
	
	
		return $return;
	}
	
	
	public static function get_theme_setting( $setting_name, $default_value = false ) {
		$return = $default_value;
		
		if( !empty( $setting_name ) ) {
			//$return = get_theme_mod( $setting_name );
			$arrThemeMods = self::_get_theme_mods_option();
			
			if( !empty( $arrThemeMods ) && is_array( $arrThemeMods ) && array_key_exists( $setting_name, $arrThemeMods ) ) {
				$return = $arrThemeMods[ $setting_name ];
			}
		}
		
		return $return;
	}
	
	protected static function _get_theme_mods_option() {
		$return = false;
		
		$theme_slug = get_option( 'stylesheet' );
		
		if ( false === ( $mods = get_option( "theme_mods_$theme_slug" ) ) ) {
			$theme_name = get_option( 'current_theme' );
			
			if ( false === $theme_name ) {
				$theme_name = wp_get_theme()->get('Name');
			}
			
			$mods = get_option( "mods_$theme_name" ); // Deprecated location.
		}
		
		if( !empty( $mods ) ) {
			$return = $mods;
		}
		
		return $return;
	}
	
	
	public static function has_theme_setting( $setting_name = null ) {
		$return = false;
		
		if( !empty( $setting_name ) ) {
			$arrThemeMods = self::_get_theme_mods_option();
			
			if( !empty( $arrThemeMods ) && is_array( $arrThemeMods ) ) {
				$return = array_key_exists( $setting_name, $arrThemeMods );
			}
		}
		
		return $return;
	}
	
	public static function set_theme_setting( $setting_name, $value ) {
		$return = false;
		
		if( ThemeSettings::has_setting( $setting_name ) != false ) {
		
			$mods = self::_get_theme_mods_option();
			
			$old_value = isset( $mods[ $setting_name ] ) ? $mods[ $setting_name ] : false;

			/**
			 * Filter the theme mod value on save.
			 *
			 * The dynamic portion of the hook name, `$name`, refers to the key name of
			 * the modification array. For example, 'header_textcolor', 'header_image',
			 * and so on depending on the theme options.
			 *
			 * @since 3.9.0
			 *
			 * @param string $value     The new value of the theme mod.
			 * @param string $old_value The current value of the theme mod.
			 */
			 
			$mods[ $setting_name ] = apply_filters( "pre_set_theme_mod_$setting_name", $value, $old_value );

			$theme = get_option( 'stylesheet' );
			
			$result = update_option( "theme_mods_$theme", $mods );
			
			if( !empty( $result ) && $old_value != $value ) {
				$return = true;
			}
		}
		
		return $return;
	}
		
}

/**
 * Function wrappers for the poor ..
 */

function cc2_compare_theme_setting( $setting_name, $compare_value = '1', $modifier = 'not_empty' ) {
	return ThemeSettings::compare_theme_setting( $setting_name, $compare_value, $modifier );
}

function cc2_get_theme_setting( $setting_name, $default_value = false ) {
	return ThemeSettings::get_theme_setting( $setting_name, $default_value );
}

function cc2_set_theme_setting( $setting_name, $value ) {
	return ThemeSettings::set_theme_setting( $setting_name, $value );
}
