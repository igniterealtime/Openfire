<?php

/**
 * Description of RTDBUpdate
 * Required : rt_plugin_info.php
 *
 * @author udit
 * version 1.1
 */

if ( ! class_exists( 'RTDBUpdate' ) ){
	class RTDBUpdate {
		/**
		 *
		 * @var type String
		 */
		public $db_version;
		public $install_db_version;
		public $schema_path;
		public $plugin_path;
		public $db_version_option_name;
		public $rt_plugin_info;
		public $mu_single_table;

		/**
		 * Set db current and installed version and also plugin info in rt_plugin_info variable.
		 *
		 * @param type string $current_version Optional if not defined then will use plugin version
		 */
		public function __construct( $current_version = false, $plugin_path = false, $schema_path = false, $mu_single_table = false ){

			if ( false != $schema_path ){
				$this->schema_path = $schema_path;
			} else {
				$this->schema_path = realpath( dirname( __FILE__ ) . $this->schema_path );
			}

			if ( false != $plugin_path ){
				$this->plugin_path = $plugin_path;
			} else {
				$this->plugin_path = realpath( dirname( __FILE__ ) . $this->plugin_path );
			}

			$this->mu_single_table = $mu_single_table;

			$this->rt_plugin_info = new rt_plugin_info( $this->plugin_path );
			if ( false == $current_version ){
				$current_version = $this->rt_plugin_info->version;
			}
			$this->db_version             = $current_version;
			$this->db_version_option_name = $this->get_db_version_option_name();
			$this->install_db_version     = $this->get_install_db_version();
		}

		/**
		 * Create table using dbDelta.
		 *
		 * @access public
		 *
		 * @param  string $sql
		 *
		 * @return void
		 */
		public function create_table( $sql ){
			require_once( ABSPATH . 'wp-admin/includes/upgrade.php' );
			dbDelta( $sql );
		}

		/**
		 * Get db_version option name.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return string
		 */
		public function get_db_version_option_name(){
			return strtoupper( 'RT_' . str_replace( '-', '_', sanitize_title( $this->rt_plugin_info->name ) ) . '_DB_VERSIONS' );
		}

		/**
		 * Get installed db_version.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return string
		 */
		public function get_install_db_version(){
			return ( $this->mu_single_table ) ? get_site_option( $this->db_version_option_name, '0.0' ) : get_option( $this->db_version_option_name, '0.0' );
		}

		/**
		 * Check upgrade by comparing version db_version.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return bool
		 */
		public function check_upgrade(){
			return version_compare( $this->db_version, $this->install_db_version, '>' );
		}

		/**
		 * Do upgrade by comparing version db_version.
		 * If db_version > install_db_version, then perform.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function do_upgrade(){
			global $wpdb;
			if ( version_compare( $this->db_version, $this->install_db_version, '>' ) ){
				$path = $this->schema_path;
				if ( $handle = opendir( $path ) ){
					while ( false !== ( $entry = readdir( $handle ) ) ) {
						if ( $entry != '.' && $entry != '..' ){
							if (  false !== strpos( $entry, '.schema' )  && file_exists( $path . '/' . $entry ) ){
								if ( is_multisite() ){
									$table_name  = str_replace( '.schema', '', strtolower( $entry ) );
									$check_table = "SHOW TABLES LIKE '%rt_" . $table_name . "'";
									$check_res   = $wpdb->get_results( $check_table, ARRAY_N );
									if ( $check_res && sizeof( $check_res ) > 0 && is_array( $check_res ) && isset( $check_res[0][0] ) ){
										$tb_name    = $check_res[0][0];
										$table_name = ( ( $this->mu_single_table ) ? $wpdb->base_prefix : $wpdb->prefix ) . 'rt_' . $table_name;
										if ( $tb_name != $table_name ){
											$alter_sql = 'ALTER TABLE ' . $tb_name . ' RENAME TO ' . $table_name;
											$wpdb->query( $alter_sql );
										}
									}
								}
								$this->create_table( $this->genrate_sql( $entry, file_get_contents( $path . '/' . $entry ) ) );
							}
						}
					}
					closedir( $handle );
				}
				if ( $this->mu_single_table ){
					update_site_option( $this->db_version_option_name, $this->db_version );
				} else {
					update_option( $this->db_version_option_name, $this->db_version );
				}
				do_action( 'rt_db_upgrade' );
			}
		}

		/**
		 * Check if table_exists.
		 *
		 * @access static
		 *
		 * @param  string $table
		 *
		 * @return bool
		 */
		static function table_exists( $table ){
			global $wpdb;

			if ( 1 == $wpdb->query( "SHOW TABLES LIKE '" . $table . "'" )  ){
				return true;
			}

			return false;
		}

		/**
		 * Genrate sql query.
		 *
		 * @access public
		 *
		 * @param  string $file_name
		 * @param  string $file_content
		 *
		 * @return string sql query
		 */
		public function genrate_sql( $file_name, $file_content ){
			return sprintf( $file_content, $this->genrate_table_name( $file_name ) );
		}

		/**
		 * Genrate table_name.
		 *
		 * @access public
		 *
		 * @param  string $file_name
		 *
		 * @return string
		 */
		public function genrate_table_name( $file_name ){
			global $wpdb;

			return ( ( $this->mu_single_table ) ? $wpdb->base_prefix : $wpdb->prefix ) . 'rt_' . str_replace( '.schema', '', strtolower( $file_name ) );
		}

	}

}