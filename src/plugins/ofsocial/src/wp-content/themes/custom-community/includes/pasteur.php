<?php
/**
 * Custom Community Sanitization function cc2_Pasteur_library. Reduced for better memory handling.
 *
 * Handles all the default and also the more fine-tuned sanitization of values, mostly for the Theme Customization API thou.
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0.25
 */

if( function_exists( 'cc2_Pasteur_none' ) ) {
	return;
}

//class cc2_Pasteur {
	
	
	/**
	 * @param bool $silent	Stay silent (= log error) or output error (loud).
	 */

	 function cc2_Pasteur_error_msg( $msg = '', $additional_data = array(), $error_level = E_USER_NOTICE, $silent = true ) {
		if( !empty( $msg ) ) {
			$strMessage = '' . $msg;
			$strAddMessage = '';
			
			if( !empty( $additional_data ) && strlen( $msg ) < 600 ) {
				if( !is_array($additional_data ) ) {
					$additional_data = (array) $additional_data;
				}
				
			
				$strAddMessage = str_replace( array("\n", "\t"), '', print_r( $additional_data, true ) );
				
				if( substr( $strMessage, -1 ) != ':' && substr( $strMessage, strlen( $strMessage ) - 2 ) != ': ' ) {
					$strMessage .= ': ';
				}
				
				if( !empty( $strAddMessage ) && strlen( $strAddMessage ) + strlen( $strMessage ) > 1024 ) {
					$strAddMessage = substr($strAddMessage, 0, ( 1024 - strlen( $strMessage ) ) );
				}
				
			}
			
			
			
			$strMessage .= $strAddMessage;
			
			// correct error level 
			if( $error_level < 256 ) {
				switch( $error_level ) {
					
					case 1:
						// E_USER_ERROR = 256
						$error_level = E_USER_ERROR;
						break;
					case 8: // E_NOTICE 
						$error_level = E_USER_NOTICE;
						break;
					case 2: // E_WARNING = 2
					case 8: // E_PARSE
					default:
						//E_USER_WARNING = 512
						$error_level = E_USER_WARNING;
						break;
				}
			} elseif( $error_level == E_DEPRECATED ) {
				$error_level = E_USER_DEPRECATED;
			}
			
			if( !empty( $strMessage ) ) {
			
				
				trigger_error( $strMessage, $error_level );
				
			}
			
		}
	}
	

	/**
	 * Generic sanitizer for the Theme Customizer
	 */

	 function cc2_Pasteur_sanitize_value( $value, $wp_settings_instance = false ) {
		$return = $value;
		
		if( !empty( $value ) ) { 
			$return = cc2_Pasteur_sanitize_text( $value );
		}
		
		
		return $return;
	}
	
	
	
	 function cc2_Pasteur_sanitize_truethy_falsy( $value ) {
		$return = 1;
		
		if( empty( $value ) != false ) {
			$return = 0;
		}
		
		
		return $return;
	}
	
	/**
	 * Truethy + falsey
	 * 
	 * Return integer values because set_theme_mod sometimes doesn't properly preserve boolean values
	 */
	 function cc2_Pasteur_sanitize_boolean( $value, $wp_settings_instance = false ) {
		
		$return = 1;
		
		if( empty( $value ) != false ) {
			$return = '';
		}
		
		return $return;
	}

	
	
	 function __cc2_Pasteur_sanitize_boolean( $value, $wp_settings_instance = false ) {
		$return = true;
		
		if( empty( $value ) != false ) { // 0 = false = null = empty; everything else IS NOT empty and thus true ^^
			$return = false;
		}
		
		return $return;
	}
	
	/**
	 * Alias for @method sanitize_boolean
	 */
	
	 function cc2_Pasteur_sanitize_bool( $value, $wp_settings_instance = false ) {
		return cc2_Pasteur_sanitize_boolean( $value, $wp_settings_instance );
	}
	
	/**
	 * NOTE: Basically a wrapper for sanitize text field
	 */
	 function cc2_Pasteur_sanitize_text( $value, $wp_settings_instance = false ) {
		$return = $value;
		
		if( !empty( $value ) ) {
			$return = sanitize_text_field( $value );
		}
		
	
		return $return;
	}
	
	/**
	 * HEXadecimal = sanitize_hex_color_no_hash
	 */
	
	 function cc2_Pasteur_sanitize_hex( $value, $wp_settings_instance = false ) {
		$return = sanitize_hex_color_no_hash( $value );
		
		return $return;
	}
	
	/**
	 * HEXadecimal COLOR = sanitize_hex_color
	 */
	
	 function cc2_Pasteur_sanitize_hex_color( $value, $wp_settings_instance = false ) {
		return sanitize_hex_color( $value );
	}

	 function cc2_Pasteur_sanitize_scheme_slug( $value, $wp_settings_instance = false ) {
		return sanitize_key( $value );
		
	}
	
	 function cc2_Pasteur_sanitize_hex_with_transparency( $value, $wp_settings_instance = false ) {
		$return = $value;
		
		if( is_string( $value ) && strtolower( trim( $value ) ) == 'transparent' ) {
			$return = 'transparent';
		} else {
			$return = sanitize_hex_color_no_hash( $value, $wp_settings_instance );	
		}
		
		return $return;
	}
	
	 function cc2_Pasteur_is_hex( $value, $default = false ) {
		$return = $default;
		
		if( substr( $value, 0, 1) == '#' ) {
			$value = substr( $value, 1 );
		}
		
		if( ctype_xdigit( $value ) != false ) {
			$return = true;
		}
		
		return $return;
	}
	
	 function cc2_Pasteur_maybe_hex( $value ) {
		$return = $value;
		
		
		if( !empty( $return ) && strpos( $return, '#' ) === false && cc2_Pasteur_is_hex( $value ) != false ) {
			$return = '#' . $return;
		}
		
	
		return $return;
	}
	
	 function cc2_Pasteur_sanitize_hex_color_with_transparency( $value, $wp_settings_instance = false ) {
		$return = $value;
		
		if( !empty( $value ) ) {
		
			if( strtolower( trim( $value ) ) == 'transparent' ) {
				$return = 'transparent';
			
			} elseif( cc2_Pasteur_is_hex( $value ) != false ) {
				$return = cc2_Pasteur_maybe_hex( $value );
			}
		}
		
		
		return $return;
	}
		
	
	/**
	 * The literal "do nothing" filter
	 */
	
	 function cc2_Pasteur_passthrough( $value, $wp_settings_instance = false ) {
		
		return $value;
	}
	
	 function cc2_Pasteur_sanitize_raw( $value, $wp_settings_instance = false ) {
		return cc2_Pasteur_passthrough( $value, $wp_settings_instance );
	}
	
	/**
	 * The even MORE literal "do nothing" filter - takes value, returns void(). Primarily reserved for Customizer Labels, Descriptions and other only cleary information-only, dysfunctional "form fields"
	 * @param [mixed]$value
	 * @return (void)
	 */
	 function cc2_Pasteur_none( $value, $wp_settings_instance = false ) {
		
		
		return;
	}
	
	
	 function cc2_Pasteur_sanitize_css( $value, $wp_settings_instance = false ) {
		return cc2_Pasteur__sanitize_css( $value);
	}
	

	 function cc2_Pasteur__sanitize_css( $value, $wp_settings_instance = false ) {
		$return = $value;
		
		/**
		 * Strip out all html tags etc.
		 * Based upon @link http://css-tricks.com/snippets/php/sanitize-database-inputs/
		 */
		
		$search = array(
			'@<script[^>]*?>.*?</script>@si',   // Strip out javascript
			'@<[\/\!]*?[^<>]*?>@si',            // Strip out HTML tags
			'@<style[^>]*?>.*?</style>@siU',    // Strip style tags properly
			'@<![\s\S]*?--[ \t\n\r]*>@'         // Strip multi-line comments
		);

		$return = preg_replace($search, '', $return);
		
		//$return =. "\n" . '/' . '* passed _sanitize_css *' . '/' ."\n";
		
		return $return;
	}
	

//}


/**
 * NOTE: Fallbacks - because of the rather short-sighted structure of the Theme Customization API functions.
 */


if( !function_exists( 'sanitize_hex_color' ) ) {
/**
 * Sanitizes a hex color.
 *
 * Returns either '', a 3 or 6 digit hex color (with #), or null.
 * For sanitizing values without a #, see sanitize_hex_color_no_hash().
 *
 * @since 3.4.0
 *
 * @param string $color
 * @return string|null
 */
	function sanitize_hex_color( $color ) {
		if ( '' === $color )
			return '';

		// 3 or 6 hex digits, or the empty string.
		if ( preg_match('|^#([A-Fa-f0-9]{3}){1,2}$|', $color ) )
			return $color;

		return null;
	}

}

if( !function_exists( 'sanitize_hex_color_no_hash' ) ) {

/**
 * Sanitizes a hex color without a hash. Use sanitize_hex_color() when possible.
 *
 * Saving hex colors without a hash puts the burden of adding the hash on the
 * UI, which makes it difficult to use or upgrade to other color types such as
 * rgba, hsl, rgb, and html color names.
 *
 * Returns either '', a 3 or 6 digit hex color (without a #), or null.
 *
 * @since 3.4.0
 * @uses sanitize_hex_color()
 *
 * @param string $color
 * @return string|null
 */
	function sanitize_hex_color_no_hash( $color ) {
		$color = ltrim( $color, '#' );

		if ( '' === $color )
			return '';

		return sanitize_hex_color( '#' . $color ) ? $color : null;
	}

};

if( !function_exists( 'maybe_hash_hex_color' ) ) {

/**
 * Ensures that any hex color is properly hashed.
 * Otherwise, returns value untouched.
 *
 * This method should only be necessary if using sanitize_hex_color_no_hash().
 *
 * @since 3.4.0
 *
 * @param string $color
 * @return string
 */
	function maybe_hash_hex_color( $color ) {
		if ( $unhashed = sanitize_hex_color_no_hash( $color ) ) {
			return '#' . $unhashed;
		}
		return $color;
	}
}

