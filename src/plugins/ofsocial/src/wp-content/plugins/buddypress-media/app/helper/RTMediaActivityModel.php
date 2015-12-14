<?php
/**
 * Created by PhpStorm.
 * User: ritz <ritesh.patel@rtcamp.com>
 * Date: 11/9/14
 * Time: 2:32 PM
 */

if ( ! class_exists( 'RTDBModel' ) ){
	return;
}

class RTMediaActivityModel extends RTDBModel {

	function __construct() {
		parent::__construct( 'rtm_activity', false, 10, true );
	}

	function get( $columns, $offset = false, $per_page = false, $order_by = 'activity_id DESC' ) {
		$columns['blog_id'] = get_current_blog_id();
		return parent::get( $columns, $offset, $per_page, $order_by );
	}

	function insert( $row ) {
		$row['blog_id'] = get_current_blog_id();
		return parent::insert( $row );
	}

	function update( $data, $where ) {
		$where['blog_id'] = get_current_blog_id();
		return parent::update( $data, $where );
	}

	public function check( $activity_id = '' ) {
		if ( $activity_id == '' ){
			return false;
		}

		$columns = array(
			'activity_id' => $activity_id,
			'blog_id' => get_current_blog_id(),
		);

		$results = $this->get( $columns );

		if ( $results ){
			return true;
		} else {
			return false;
		}
	}

} 