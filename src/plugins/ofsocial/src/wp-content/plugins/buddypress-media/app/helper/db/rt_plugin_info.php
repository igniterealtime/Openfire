<?php

/**
 * Description of rt_plugin_info
 *
 * @author udit
 */

if ( ! class_exists( 'rt_plugin_info' ) ){
	class rt_plugin_info {

		//put your code here
		public $plugin_path;
		public $name; //'Name' - Name of the plugin, must be unique.
		public $title; //'Title' - Title of the plugin and the link to the plugin's web site.
		public $desctipriton; //'Description' - Description of what the plugin does and/or notes from the author.
		public $authro; //'Author' - The author's name
		public $authoruri; //'AuthorURI' - The authors web site address.
		public $version; //'Version' - The plugin version number.
		public $pluginuri; //'PluginURI' - Plugin web site address.
		public $textdomain; //'TextDomain' - Plugin's text domain for localization.
		public $domain_path; //'DomainPath' - Plugin's relative directory path to .mo files.
		public $network; //'Network' - Boolean. Whether the plugin can only be activated network wide.
		public $plugin_data;

		/**
		 * __construct.
		 *
		 * @access public
		 * @param  void
		 *
		 */
		public function __construct( $path = null ) {
			$this->set_current_plugin_path( $path );
			$this->set_plugin_data();
		}

		/**
		 * get_plugin_data.
		 *
		 * @access public
		 * @param  void
		 *
		 */
		public function get_plugin_data() {
			require_once( ABSPATH . 'wp-admin/includes/plugin.php' );
			return @get_plugin_data( $this->plugin_path );
		}

		/**
		 * set_plugin_data.
		 *
		 * @access public
		 * @param  void
		 *
		 */
		public function set_plugin_data() {
			$this->plugin_data = $this->get_plugin_data();
			$this->name = $this->plugin_data['Name'];
			$this->title = $this->plugin_data['Title'];
			$this->desctipriton = $this->plugin_data['Description'];
			$this->author = $this->plugin_data['Author'];
			$this->authoruri = $this->plugin_data['AuthorURI'];
			$this->version = $this->plugin_data['Version'];
			$this->pluginuri = $this->plugin_data['PluginURI'];
			$this->textdomain = $this->plugin_data['TextDomain'];
			$this->domain_path = $this->plugin_data['DomainPath'];
			$this->network = $this->plugin_data['Network'];
		}

		/**
		 * set_current_plugin_path.
		 *
		 * @access public
		 * @param  string $path
		 *
		 */
		public function set_current_plugin_path( $path ) {
			if ( $path != null ){
				$this->plugin_path = $path;
			} else {
				$this->plugin_path = realpath( plugin_dir_path( __FILE__ ) . '../../index.php' );
			}
		}

	}
}