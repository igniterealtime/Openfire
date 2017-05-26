<?php
/**
 * Implements color schemes
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0-r2
 */


if( class_exists( 'cc2_ColorSchemes' ) ) {
	return;
}

	class cc2_ColorSchemes {
		
		var $arrKnownLocations = array(),
			$arrColorSchemes = array();
		
		/**
         * @static
         * @var    \wp_less Reusable object instance.
         */
        protected static $instance = null;


        /**
         * Creates a new instance. Called on 'after_setup_theme'.
         * May be used to access class methods from outside.
         *
         * @see    __construct()
         * @static
         * @return \wp_less
         */
        public static function init() {
			global $cc2_color_schemes;
			null === self::$instance AND self::$instance = new self;
			
			$cc2_color_schemes = self::$instance;
			
			return self::$instance;
        }
		
		function __construct() {
			// init variables
			$this->init_schemes();
			
			$upload_dir = $upload_dir = wp_upload_dir();
			
			$arrLocations = array(
				$upload_dir['path'] . '/cc2/schemes/' => $upload_dir['url'] . '/cc2/schemes/',
				get_template_directory() . '/includes/schemes/' => get_template_directory_uri() . '/includes/schemes/',
			);
			
			$this->set_known_locations( $arrLocations );
			
			// add filters
			
			add_filter( 'cc2_style_css', array( $this, 'switch_color_scheme') );
		}


		public function set_known_locations( $arrLocations = array() ) {
			$return = false;
			
			if( !empty( $arrLocations ) && is_array( $arrLocations ) ) {
				$this->arrKnownLocations = apply_filters('cc2_color_schemes_set_locations', $arrLocations );
				
				$return = true;
			}
		
			return $return;
		}
		
	
		public function switch_color_scheme( $url ) {
			$return = $url;
			
			$current_scheme = apply_filters('cc2_get_current_color_scheme', $this->get_current_color_scheme() );
			
			//new __debug( array('current_scheme' => $current_scheme ), 'switch_color_scheme' );
			
			if( !empty( $current_scheme ) && isset( $current_scheme['slug'] ) && $current_scheme['slug'] != 'default' ) {
				//new __debug( array('current_scheme' => $current_scheme ), 'switch_color_scheme = true' );
				
				$return = apply_filters('cc2_set_style_url', $current_scheme['style_url'] );
			}
			
			return $return;
		}
		
		
		public function init_schemes() {
			
			if( defined('CC2_THEME_CONFIG' ) ) {
				
				$config = maybe_unserialize( CC2_THEME_CONFIG );
				
				if( !empty( $config['color_schemes'] ) ) {
					$this->arrColorSchemes = $config['color_schemes'];
				}

			}
		}
	
		function get_current_color_scheme( $default = false ) {
			$return = $default;
			
			$arrColorSchemes = apply_filters( 'cc2_get_available_color_schemes', $this->arrColorSchemes );
			
			$current_scheme_slug = get_theme_mod('color_scheme', $default );
			
			
			if( !empty( $current_scheme_slug ) && !empty( $arrColorSchemes ) && !empty( $arrColorSchemes[ $current_scheme_slug ] ) ) {
					
				$return = $arrColorSchemes[ $current_scheme_slug ];
				
				
				if( empty( $return['slug'] ) ) {
					$return['slug'] = $current_scheme_slug;
				}
				
				
				if( empty( $return['output_file'] ) != false ) {
					$strOutputFile = $current_scheme_slug . '.css';
					
					if( !empty( $return['file'] ) ) {
						$strOutputFile = basename( $return['file'], 'less' ) . '.css';
					}
					
					$return['output_file'] = $strOutputFile;
				}
				
				// check paths
				
				/**
				 * NOTE: A (do-)while-loop might be the better choice. Avoids nasty breaks.
				 */
				
				foreach( $this->arrKnownLocations as $strPath => $strURL ) { 
					
					if( file_exists( $strPath . $return['output_file'] ) ) {
						$return['style_path'] = $strPath . $return['output_file'];
						$return['style_url'] = $strURL . $return['output_file'];
						break;
					}
				}
	
			}
			
			return $return;
		}
	}
	
	add_action('cc2_init_color_schemes', array('cc2_ColorSchemes', 'init'), 11 );

