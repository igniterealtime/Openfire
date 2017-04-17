<?php

/**
 * Description of BPMediaModel
 *
 * @author joshua
 */
class RTMediaModel extends RTDBModel {

	function __construct(){
		parent::__construct( 'rtm_media', false, 10, true );
		$this->meta_table_name = 'rt_rtm_media_meta';
	}

	/**
	 *
	 * @param string $name
	 * @param array $arguments
	 *
	 * @return array
	 */
	function __call( $name, $arguments ){
		$result = parent::__call( $name, $arguments );
		if ( ! $result['result'] ){
			$result['result'] = $this->populate_results_fallback( $name, $arguments );
		}

		return $result;
	}

	/**
	 *
	 * @global object $wpdb
	 *
	 * @param array  $columns
	 * @param mixed  $offset
	 * @param mixed  $per_page
	 * @param string  $order_by
	 *
	 * @return array
	 */
	function get( $columns, $offset = false, $per_page = false, $order_by = 'media_id desc', $count_flag = false ){
		global $wpdb;
		$select = 'SELECT ';
		if ( $count_flag ){
			$select .= 'count(*) ';
		} else {
			$select .= "{$this->table_name}.* ";
		}

		$from  = " FROM {$this->table_name} ";
		$join  = '';
		$where = ' where 2=2 ';
		if ( is_multisite() ){
			$where .= " AND {$this->table_name}.blog_id = '" . get_current_blog_id() . "' ";
		}
		$temp = 65;

		$columns = apply_filters( 'rtmedia-model-query-columns', $columns, $count_flag );

		foreach ( (array) $columns as $colname => $colvalue ) {
			if ( 'meta_query' == strtolower( $colname ) ){
				foreach ( $colvalue as $meta_query ) {
					if ( ! isset( $meta_query['compare'] ) ){
						$meta_query['compare'] = '=';
					}
					$tbl_alias = chr( $temp ++ );
					if ( is_multisite() ){
						$join .= " LEFT JOIN {$wpdb->base_prefix}{$this->meta_table_name} as {$tbl_alias} ON {$this->table_name}.id = {$tbl_alias}.media_id ";
					} else {
						$join .= " LEFT JOIN {$wpdb->prefix}{$this->meta_table_name} as {$tbl_alias} ON {$this->table_name}.id = {$tbl_alias}.media_id ";
					}
					if ( isset( $meta_query['value'] ) ){
						$where .= " AND  ({$tbl_alias}.meta_key = '{$meta_query["key"]}' and  {$tbl_alias}.meta_value  {$meta_query["compare"]}  '{$meta_query["value"]}' ) ";
					} else {
						$where .= " AND  {$tbl_alias}.meta_key = '{$meta_query["key"]}' ";
					}
				}
			} else {
				if ( is_array( $colvalue ) ){
					if ( ! isset( $colvalue['compare'] ) ){
						$compare = 'IN';
					} else {
						$compare = $colvalue['compare'];
					}

					$tmpVal           = isset( $colvalue['value'] ) ? $colvalue['value'] : $colvalue;
					$col_val_comapare = ( is_array( $tmpVal ) ) ? '(\'' . implode( "','", $tmpVal ) . '\')' : '(\'' . $tmpVal . '\')';
					if ( 'IS NOT' == $compare ){
						$col_val_comapare = ! empty( $colvalue['value'] ) ? $colvalue['value'] : $col_val_comapare;
					}
					$where .= " AND {$this->table_name}.{$colname} {$compare} {$col_val_comapare}";
				} else {
					$where .= " AND {$this->table_name}.{$colname} = '{$colvalue}'";
				}
			}
		}
		$qgroup_by = ' ';
		if ( $order_by ){
			$qorder_by = " ORDER BY {$this->table_name}.{$order_by}";
		} else {
			$qorder_by = '';
		}

		$select    = apply_filters( 'rtmedia-model-select-query', $select, $this->table_name );
		$join      = apply_filters( 'rtmedia-model-join-query', $join, $this->table_name );
		$where     = apply_filters( 'rtmedia-model-where-query', $where, $this->table_name, $join );
		$qgroup_by = apply_filters( 'rtmedia-model-group-by-query', $qgroup_by, $this->table_name );
		$qorder_by = apply_filters( 'rtmedia-model-order-by-query', $qorder_by, $this->table_name );

		$sql = $select . $from . $join . $where . $qgroup_by . $qorder_by;
		if ( false !== $offset ){
			if ( ! is_integer( $offset ) ){
				$offset = 0;
			}

			if ( intval( $offset ) < 0 ){
				$offset = 0;
			}

			if ( ! is_integer( $per_page ) ){
				$per_page = 1;
			}

			if ( intval( $per_page ) < 1 ){
				$per_page = 1;
			}

			//filter added to change the LIMIT
			$limit = apply_filters( 'rtmedia-model-limit-query', ' LIMIT ' . $offset . ',' . $per_page, $offset, $per_page );

			$sql .= $limit;
		}
		if ( ! $count_flag ){
			return $wpdb->get_results( $sql );
		} else {
			return $wpdb->get_var( $sql );
		}
	}

	/**
	 *
	 * @param string $name
	 * @param array $arguments
	 *
	 * @return array
	 */
	function populate_results_fallback( $name, $arguments ){
		$result['result'] = false;
		if ( 'get_by_media_id' == $name && isset( $arguments[0] ) && $arguments[0] ){

			$result['result'][0]->media_id = $arguments[0];

			$post_type = get_post_field( 'post_type', $arguments[0] );
			if ( 'attachment' == $post_type ){
				$post_mime_type                      = explode( '/', get_post_field( 'post_mime_type', $arguments[0] ) );
				$result['result'][0]->media_type = $post_mime_type[0];
			} elseif ( 'bp_media_album' == $post_type ) {
				$result['result'][0]->media_type = 'bp_media_album';
			} else {
				$result['result'][0]->media_type = false;
			}

			$result['result'][0]->context_id = intval( get_post_meta( $arguments[0], 'bp-media-key', true ) );
			if ( $result['result'][0]->context_id > 0 ){
				$result['result'][0]->context = 'profile';
			} else {
				$result['result'][0]->context = 'group';
			}

			$result['result'][0]->activity_id = get_post_meta( $arguments[0], 'bp_media_child_activity', true );

			$result['result'][0]->privacy = get_post_meta( $arguments[0], 'bp_media_privacy', true );
		}

		return $result['result'];
	}

	/**
	 *
	 * @param array $columns
	 * @param mixed $offset
	 * @param mixed $per_page
	 * @param string $order_by
	 *
	 * @return array
	 */
	function get_media( $columns, $offset = false, $per_page = false, $order_by = 'media_id desc', $count_flag = false ){
		if ( is_multisite() ){
			$order_by = 'blog_id' . ( ( $order_by ) ? ',' . $order_by : '' );
		}

		$results = $this->get( $columns, $offset, $per_page, $order_by, $count_flag );

		return $results;
	}

	/**
	 *
	 * @param  integer $author_id
	 * @param  mixed $offset
	 * @param  mixed $per_page
	 * @param  string $order_by
	 *
	 * @return array $results
	 */
	function get_user_albums( $author_id, $offset, $per_page, $order_by = 'media_id desc' ){
		global $wpdb;
		if ( is_multisite() ){
			$order_by = 'blog_id' . ( ( $order_by ) ? ',' . $order_by : '' );
		}

		$sql = "SELECT * FROM {$this->table_name}  ";

		if( is_multisite() ){
			$sub_sql = "SELECT DISTINCT (album_id) FROM {$this->table_name} WHERE media_author = $author_id AND album_id IS NOT NULL AND media_type <> 'album' AND context <> 'group' AND blog_id = '".get_current_blog_id()."'";
		} else {
			$sub_sql = "SELECT DISTINCT (album_id) FROM {$this->table_name} WHERE media_author = $author_id AND album_id IS NOT NULL AND media_type <> 'album' AND context <> 'group'";
		}

		$where = " WHERE (id IN( $sub_sql ) OR (media_author = $author_id ))
			    AND media_type = 'album'
			    AND (context = 'profile' or context is NULL) ";
		if ( is_multisite() ){
			$where .= " AND {$this->table_name}.blog_id = '" . get_current_blog_id() . "' ";
		}
		$where     = apply_filters( 'rtmedia-get-album-where-query', $where, $this->table_name );
		$qorder_by = " ORDER BY {$this->table_name}.$order_by ";
		$sql .= $where . $qorder_by;
		if ( false !== $offset ){
			if ( ! is_integer( $offset ) ){
				$offset = 0;
			}
			if ( intval( $offset ) < 0 ){
				$offset = 0;
			}

			if ( ! is_integer( $per_page ) ){
				$per_page = 1;
			}
			if ( intval( $per_page ) < 1 ){
				$per_page = 1;
			}

			$sql .= ' LIMIT ' . $offset . ',' . $per_page;
		}
        
		$results = $wpdb->get_results( $sql );
        
		return $results;
	}

	/**
	 *
	 * @param  integer $group_id
	 * @param  mixed $offset
	 * @param  mixed $per_page
	 * @param  string $order_by
	 *
	 * @return array $results
	 */
	function get_group_albums( $group_id, $offset, $per_page, $order_by = 'media_id desc' ){
		global $wpdb;
		if ( is_multisite () )
		    $order_by = "blog_id" . (($order_by)? "," . $order_by :'');

		if( is_multisite() ){
			$sub_sql = "SELECT DISTINCT (album_id) FROM {$this->table_name} WHERE context_id = $group_id AND album_id IS NOT NULL AND media_type != 'album' AND context = 'group' AND blog_id = '".get_current_blog_id()."'";
		} else {
			$sub_sql = "SELECT DISTINCT (album_id) FROM {$this->table_name} WHERE context_id = $group_id AND album_id IS NOT NULL AND media_type != 'album' AND context = 'group'";
		}
		$sql = "SELECT * FROM {$this->table_name} WHERE id IN( $sub_sql ) OR (media_type = 'album' AND context_id = $group_id AND context = 'group')";

		if ( is_multisite() ){
			$sql .= " AND  {$this->table_name}.blog_id = '" . get_current_blog_id() . "' ";
		}
		$sql .= " ORDER BY {$this->table_name}.$order_by";

		if ( false !== $offset ){
			if ( ! is_integer( $offset ) ){
				$offset = 0;
			}
			if ( intval( $offset ) < 0 ){
				$offset = 0;
			}

			if ( ! is_integer( $per_page ) ){
				$per_page = 1;
			}
			if ( intval( $per_page ) < 1 ){
				$per_page = 1;
			}

			$sql .= ' LIMIT ' . $offset . ',' . $per_page;
		}
		$results = $wpdb->get_results( $sql );

		return $results;
	}

	/**
	 *
	 * @param  mixed $user_id
	 * @param  mixed $where_query
	 *
	 * @return string $result
	 */
	function get_counts( $user_id = false, $where_query = false ){

		if ( ! $user_id && ! $where_query ){
			return false;
		}
		global $wpdb, $rtmedia;

		$query = "SELECT {$this->table_name}.privacy, ";
		foreach ( $rtmedia->allowed_types as $type ) {
			$query .= "SUM(CASE WHEN {$this->table_name}.media_type LIKE '{$type['name']}' THEN 1 ELSE 0 END) as {$type['name']}, ";
		}
		$query .= "SUM(CASE WHEN {$this->table_name}.media_type LIKE 'album' THEN 1 ELSE 0 END) as album
		FROM
			{$this->table_name} WHERE 2=2 ";

		if ( is_multisite() ){
			$query .= " AND {$this->table_name}.blog_id = '" . get_current_blog_id() . "' ";
		}
        $where_query_sql = '';
		if ( $where_query ){
			foreach ( $where_query as $colname => $colvalue ) {
				if ( 'meta_query' != strtolower( $colname ) ){
					if ( is_array( $colvalue ) ){
						if ( ! isset( $colvalue['compare'] ) ){
							$compare = 'IN';
						} else {
							$compare = $colvalue['compare'];
						}
						if ( ! isset( $colvalue['value'] ) ){
							$colvalue['value'] = $colvalue;
						}

                        $where_query_sql .= " AND {$this->table_name}.{$colname} {$compare} ('" . implode( "','", $colvalue['value'] ) . "')";
					} else {

						//                        if ( $colname == "context" && $colvalue == "profile" ) {
						//                               $query .= " AND {$this->table_name}.{$colname} <> 'group'";
						//                        } else {
						//                            $query .= " AND {$this->table_name}.{$colname} = '{$colvalue}'";
						//                        }
						//profile now shows only profile media so conditional check removed and counts will be fetched according to the available context
                        $where_query_sql .= " AND {$this->table_name}.{$colname} = '{$colvalue}'";
					}
				}
			}
		}
        $where_query_sql = apply_filters( 'rtmedia-get-counts-where-query', $where_query_sql );
        $query = $query . $where_query_sql . ' GROUP BY privacy';
		$result = $wpdb->get_results( $query );
		if ( ! is_array( $result ) ){
			return false;
		}

		return $result;
	}

	/**
	 *
	 * @param  integer $profile_id
	 * @param  string $context
	 *
	 * @return int
	 */
	function get_other_album_count( $profile_id, $context = 'profile' ){
		$global = RTMediaAlbum::get_globals();
		$sql    = "select distinct album_id from {$this->table_name} where 2=2 AND context = '{$context}' ";
		if ( is_multisite() ){
			$sql .= " AND {$this->table_name}.blog_id = '" . get_current_blog_id() . "' ";
		}
		if ( is_array( $global ) && count( $global ) > 0 ){
			$sql .= ' and album_id in (';
			$sep = '';
			foreach ( $global as $id ) {
				$sql .= $sep . $id;
				$sep = ',';
			}
			$sql .= ')';
		}
		if ( 'profile' == $context ){
			$sql .= " AND media_author=$profile_id ";
		} else {
			if ( 'group' == $context ){
				$sql .= " AND context_id=$profile_id ";
			}
		}
		global $wpdb;
		$result = $wpdb->get_results( $sql );
		if ( isset( $result ) ){
			return count( $result );
		} else {
			return 0;
		}
	}

}