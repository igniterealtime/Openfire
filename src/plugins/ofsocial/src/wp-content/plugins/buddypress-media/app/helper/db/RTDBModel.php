<?php

/**
 * Description of RTDBModel
 * Base class for any Database Model like Media, Album etc.
 *
 * @author udit
 */

if ( ! class_exists( 'RTDBModel' ) ){
	class RTDBModel {

		/**
		 *
		 * @var type
		 *
		 * $table_name - database table linked to the model.
		 *                All the queries will be fired on that table or with the join in this table.
		 * $per_page - number of rows per page to be displayed
		 */
		public $table_name;
		public $per_page;
		public $mu_single_table;

		/**
		 *
		 * @param string  $table_name Table name for model
		 * @param boolean $withprefix Set true if $tablename is with prefix otherwise it will prepend wordpress prefix with "rt_"
		 */
		function __construct( $table_name, $withprefix = false, $per_page = 10, $mu_single_table = false ){
			$this->mu_single_table = $mu_single_table;
			$this->set_table_name( $table_name, $withprefix );
			$this->set_per_page( $per_page );
		}

		/**
		 *
		 * @global object $wpdb
		 *
		 * @param string  $table_name
		 * @param mixed   $withprefix
		 */
		public function set_table_name( $table_name, $withprefix = false ){
			global $wpdb;
			if ( ! $withprefix ){
				$table_name = ( ( $this->mu_single_table ) ? $wpdb->base_prefix : $wpdb->prefix ) . 'rt_' . $table_name;
			}
			$this->table_name = $table_name;
		}

		/**
		 * set number of rows per page for pagination
		 *
		 * @param integer $per_page
		 */
		public function set_per_page( $per_page ){
			$this->per_page = $per_page;
		}

		/**
		 * Magic Method for getting DB rows by particular column.
		 * E.g., get_by_<columnName>(params)
		 *
		 * @global object $wpdb
		 *
		 * @param string  $name - Added get_by_<coulmname>(value,pagging=true,page_no=1)
		 * @param array   $arguments
		 *
		 * @return array  result array
		 */
		function __call( $name, $arguments ){
			$column_name = str_replace( 'get_by_', '', strtolower( $name ) );
			$paging      = false;
			$page        = 1;
			if ( $arguments && ! empty( $arguments ) ){
				if ( ! isset( $arguments[1] ) ){
					$paging = true;
				} else {
					$paging = $arguments[1];
				}

				if ( ! isset( $arguments[2] ) ){
					$page = 1;
				} else {
					$page = $arguments[2];
				}

				$this->per_page           = apply_filters( 'rt_db_model_per_page', $this->per_page, $this->table_name );
				$return_array             = array();
				$return_array['result'] = false;
				global $wpdb;
				$return_array['total'] = intval( $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM " . $this->table_name . " WHERE {$column_name} = %s", $arguments[0] ) ) );
				if ( $return_array['total'] > 0 ){
					$other = '';
					if ( $paging ){
						if ( intval( $this->per_page ) < 0 ){
							$this->per_page = 1;
						}

						$offset = ( $page - 1 ) * $this->per_page;

						if ( ! is_integer( $offset ) ){
							$offset = 0;
						}
						if ( intval( $offset ) < 0 ){
							$offset = 0;
						}

						if ( $offset <= $return_array['total'] ){
							$other = ' LIMIT ' . $offset . ',' . $this->per_page;
						} else {
							return false;
						}
					}
					//echo $wpdb->prepare("SELECT * FROM " . $this->table_name . " WHERE {$column_name} = %s {$other}", $arguments[0]);
					$return_array['result'] = $wpdb->get_results( $wpdb->prepare( "SELECT * FROM " . $this->table_name . " WHERE {$column_name} = %s {$other}", $arguments[0] ), ARRAY_A );
				}

				return $return_array;
			} else {
				return false;
			}
		}

		/**
		 *
		 * @global object  $wpdb
		 *
		 * @param array    $row
		 *
		 * @return integer
		 */
		function insert( $row ){
			global $wpdb;
			$insertdata = array();
			foreach ( $row as $key => $val ) {
				if ( $val != null ){
					$insertdata[ $key ] = $val;
				}
			}

			$wpdb->insert( $this->table_name, $insertdata );

			return $wpdb->insert_id;
		}

		/**
		 *
		 * @global object $wpdb
		 *
		 * @param array   $data
		 * @param array   $where
		 */
		function update( $data, $where ){
			global $wpdb;

			return $wpdb->update( $this->table_name, $data, $where );
		}

		/**
		 * Get all the rows according to the columns set in $columns parameter.
		 * offset and rows per page can also be passed for pagination.
		 *
		 * @global object $wpdb
		 *
		 * @param array   $columns
		 *
		 * @return array
		 */
		function get( $columns, $offset = false, $per_page = false, $order_by = 'id desc' ){
			$select = "SELECT * FROM {$this->table_name}";
			$where  = ' where 2=2 ';
			foreach ( $columns as $colname => $colvalue ) {
				if ( is_array( $colvalue ) ){
					if ( ! isset( $colvalue['compare'] ) ){
						$compare = 'IN';
					} else {
						$compare = $colvalue['compare'];
					}
					if ( ! isset( $colvalue['value'] ) ){
						$colvalue['value'] = $colvalue;
					}
					$col_val_comapare = ( is_array( $colvalue['value'] ) ) ? '(\'' . implode( "','", $colvalue['value'] ) . '\')' : '(\'' . $colvalue['value'] . '\')';
					$where .= " AND {$this->table_name}.{$colname} {$compare} {$col_val_comapare}";
				} else {
					$where .= " AND {$this->table_name}.{$colname} = '{$colvalue}'";
				}
			}
			$sql = $select . $where;

			$sql .= " ORDER BY {$this->table_name}.$order_by";
			if ( false !== $offset ){
				if ( ! is_integer( $offset ) ){
					$offset = 0;
				}
				if ( intval( $offset ) < 0 ){
					$offset = 0;
				}

				if ( ! is_integer( $per_page ) ){
					$per_page = 0;
				}
				if ( intval( $per_page ) < 0 ){
					$per_page = 1;
				}
				$sql .= ' LIMIT ' . $offset . ',' . $per_page;

			}
			global $wpdb;

			return $wpdb->get_results( $sql );
		}

		/**
		 *
		 * @global object $wpdb
		 *
		 * @param array   $where
		 *
		 * @return array
		 */
		function delete( $where ){
			global $wpdb;

			return $wpdb->delete( $this->table_name, $where );
		}


	}
}