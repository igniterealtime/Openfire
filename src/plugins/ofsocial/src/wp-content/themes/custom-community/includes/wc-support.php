<?php
/** 
 * Function / Class Library for CC2 WooCommerce support
 *
 * @author Fabian Wolf
 * @author Sven Lenhert
 * @since 2.0.25
 * @package cc2
 */
 
if( !class_exists( 'cc2_WooCommerce_Support') ) :

	class cc2_WooCommerce_Support {
		/**
		 * Plugin instance.
		 *
		 * @see get_instance()
		 * @type object
		 */
		protected static $instance = NULL;
			
		/**
		 * Implements Factory pattern
		 * Strongly inspired by WP Maintenance Mode of Frank Bueltge ^_^ (@link https://github.com/bueltge/WP-Maintenance-Mode)
		 * 
		 * Access this plugins working instance
		 *
		 * @wp-hook after_setup_theme
		 * @return object of this class
		 */
		public static function get_instance() {

			NULL === self::$instance and self::$instance = new self;

			return self::$instance;
		}
		
		function __construct() {
			/**
			 * TODO: maybe add a switch to the customizer woocommerce section?
			 */
			/**
			 * NOTE: Future version
			 * 
			//$display_sidebar = get_theme_mod( 'wc_sidebar_position', false );
			*/
			
			$display_sidebar = get_theme_mod('wc_display_sidebar', false );
			
			if( empty( $display_sidebar ) || $display_sidebar == 'disabled' ) {
				remove_action( 'woocommerce_sidebar', 'woocommerce_get_sidebar', 10);
			}
			
			
			
			remove_action( 'woocommerce_before_main_content', 'woocommerce_output_content_wrapper', 10);
			remove_action( 'woocommerce_after_main_content', 'woocommerce_output_content_wrapper_end', 10);

			add_action('woocommerce_before_main_content', array( $this, 'theme_wrapper_top' ), 10);
			add_action('woocommerce_after_main_content', array( $this, 'theme_wrapper_bottom' ), 10);
			
			
			// fixes pagination query issues with WooCommerce < 2.3
			add_filter( 'woocommerce_pagination_args', array( $this, 'fix_pagination' ) );

		}
	
	
		public function theme_wrapper_top() {
			//echo '<!--- ' .basename(__FILE__) . ': ' . __METHOD__ . ' -->';
			locate_template( 'wc-content-top.php', true );
		}
		
		public function theme_wrapper_bottom() {
			//echo '<!--- ' .basename(__FILE__) . ': ' . __METHOD__ . ' -->';
			locate_template( 'wc-content-bottom.php', true );
		}
		
		/**
		 * Fixes pagination query issues in WooCommerce < 2.3
		 * 
		 * NOTE: Says "fixed with 2.2.3", but does still appear in WooCommerce 2.2.11
		 */

		public function fix_pagination( $args = array() ) {
			$return = $args;
		
			//if( !empty( $enable_fix ) && substr( WC_VERSION, 0, 1 ) == '2' && intval(substr( WC_VERSION, 2, 1 ) ) < 3 ) {
			//if( defined( 'WC_VERSION' ) ) {
				$return['base'] = esc_url( str_replace( 999999999, '%#%', remove_query_arg( 'add-to-cart', htmlspecialchars_decode( get_pagenum_link( 999999999 ) ) ) ) );
				$return['base'] = esc_url_raw( str_replace( 999999999, '%#%', remove_query_arg( 'add-to-cart', get_pagenum_link( 999999999, false ) ) ) );
			//}
			
			return $return;
		}
	}

	if( class_exists( 'WooCommerce' ) ) :
		add_action('init', array( 'cc2_WooCommerce_Support', 'get_instance' ) );
	endif;
	
	
endif; // end class_exists

