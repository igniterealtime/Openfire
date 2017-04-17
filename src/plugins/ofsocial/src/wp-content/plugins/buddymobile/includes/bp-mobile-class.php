<?php
/** Constants *****************************************************************/

if ( !class_exists( 'BuddyMobile' ) ) {
	/**
	 * Main BuddyMobile Class
	 *
	 * Tap tap tap... Is this thing on?
	 *
	 * @since BuddyMobile (1.6)
	 */
	class BuddyMobile {

		var $iphoned;
		var $ipadd;

		/** Functions *************************************************************/

		/**
		 * The main BuddyMobile loader
		 *
		 * @since BuddyMobile (1.6)
		 *
		 */
		public function __construct() {


			$buddymobile_options = get_option('buddymobile_plugin_options');


			$this->theme = json_decode( $buddymobile_options['theme'] );
			$this->ipadtheme =  !empty($buddymobile_options['ipad-theme']) ? $buddymobile_options['ipad-theme'] : '' ;

			if( $this->ipadtheme ) {
				$this->ipad_theme = true;
			} else {
				$this->ipad_theme = false;
			}

			if( !$this->theme ) {
				$this->theme->theme = 'iphone';
			}

			$this->constants();
			$this->includes();
			$this->setup_actions();

		}

		/**
		 * BuddyMobile constants
		 *
		 * @since BuddyMobile (1.6)
		 *
		 */
		private function constants() {

				// Path and URL
			if ( !defined( 'BP_MOBILE_PLUGIN_DIR' ) ) {
				define( 'BP_MOBILE_PLUGIN_DIR',  trailingslashit( WP_PLUGIN_DIR . '/buddymobile' )  );
			}

			if ( !defined( 'BP_MOBILE_PLUGIN_URL' ) ) {

				$plugin_url = WP_PLUGIN_URL . '/buddymobile' ;

				// If we're using https, update the protocol. Workaround for WP13941, WP15928, WP19037.
				if ( is_ssl() )
						$plugin_url = str_replace( 'http://', 'https://', $plugin_url );
						define( 'BP_MOBILE_PLUGIN_URL', $plugin_url );
			}

		}

		/**
		 * Include required files
		 *
		 * @since BuddyMobile (1.6)
		 * @access private
		 *
		 */
		private function includes() {

			// Files to include
			$includes = array(
				'/includes/bp-mobile-loader.php',
				'/includes/bp-mobile-actions.php'
			);

			foreach ($includes as $include )
				include( BP_MOBILE_PLUGIN_DIR . $include );

			if ( is_admin() || is_network_admin() ) {
				include( BP_MOBILE_PLUGIN_DIR . '/includes/bp-mobile-admin.php' );
			}

		}

		/**
		 * Setup the default hooks and actions
		 *
		 * @since BuddyMobile (1.6)
		 * @access private
		 *
		 */
		private function setup_actions() {

			// load plugin text domain
			add_action( 'init', array( $this, 'textdomain' ) );

			// Register admin styles and scripts
			add_action( 'admin_print_styles', array( $this, 'register_admin_styles' ) );
			add_action( 'admin_enqueue_scripts', array( $this, 'register_admin_scripts' ) );

			add_action( 'init', array( &$this,'detectiPhone' ) );

			add_filter( 'template', array(&$this, 'get_template') );
			add_filter( 'stylesheet', array(&$this, 'get_stylesheet') );

			$this->plugin_dir = BP_MOBILE_PLUGIN_DIR;
			$this->themes_dir = $this->plugin_dir . 'themes/mobile';
			register_theme_directory( $this->themes_dir );

			$this->iphoned = false;
			$this->ipadd = false;
			$this->detectiPhone();

		}
			/**
		 * Loads the plugin text domain for translation
		 */
		public function textdomain() {
		}

		/**
		 * Registers and enqueues admin-specific styles.
		 */
		public function register_admin_styles() {

			// TODO change 'plugin-name' to the name of your plugin
			//wp_enqueue_style( 'bp-mobile-admin-styles', BP_MOBILE_PLUGIN_DIR . 'css/admin.css' );

		}

		/**
		 * Registers and enqueues admin-specific JavaScript.
		 */
		public function register_admin_scripts() {

			// TODO change 'plugin-name' to the name of your plugin
			//wp_enqueue_script( 'bp-mobile-admin-script', BP_MOBILE_PLUGIN_DIR . 'js/admin.js' );

		}


		public function detectiPhone( $query = '' ) {

			$container = $_SERVER['HTTP_USER_AGENT'] ;

			$useragents = array (
				"iPhone",      		// Apple iPhone
				"iPod",     		// Apple iPod touch
				"incognito",    	// Other iPhone browser
				"webmate",     		// Other iPhone browser
				"Android",     		// 1.5+ Android
				"dream",     		// Pre 1.5 Android
				"CUPCAKE",      	// 1.5+ Android
				"blackberry9500",   // Storm
				"blackberry9530",   // Storm
				"blackberry9520",   // Storm v2
				"blackberry9550",   // Storm v2
				"blackberry 9700",
				"blackberry 9800", 	//curve
				"blackberry 9850",
				"webOS",    		// Palm Pre Experimental
				"s8000",     		// Samsung Dolphin browser
				"bada",      		// Samsung Dolphin browser
				"Googlebot-Mobile"  // the Google mobile crawler
			);

			$ipadagents = array (
				"iPad"
			);
			
			$mobile_cookie = !empty($_COOKIE['bpthemeswitch']) ? $_COOKIE['bpthemeswitch'] : '' ;

			foreach ( $useragents as $useragent ) {
				if ( preg_match("/".$useragent."/i",$container) && $mobile_cookie != 'normal' ){
					$this->iphoned = true;

				}
			}

			if( $this->ipad_theme ) {
			foreach ( $ipadagents as $ipadagent ) {
				if ( preg_match("/".$ipadagent."/i",$container ) && $mobile_cookie != 'normal' ){

						$this->ipadd = true;

				}
			}
			}

		}

		/**
		 * gets proper theme
		 */
		function get_stylesheet( $stylesheet ) {
			if ( $this->iphoned || $this->ipadd ) {
				return $this->theme->theme;
			} else {
				return $stylesheet;
			}
		}

		function get_template( $template ) {
			if ( $this->iphoned || $this->ipadd ) {
				return $this->theme->template;
			} else {
				return $template;
			}
		}

	}
	$bpmobile = new BuddyMobile ;

}