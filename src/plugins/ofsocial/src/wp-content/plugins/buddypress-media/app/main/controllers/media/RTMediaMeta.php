<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaMetaQuery
 *
 * @author saurabh
 */
class RTMediaMeta {

	/**
	 *
	 */
	public function __construct() {
		$this->model = new RTDBModel( 'rtm_media_meta', false, 10, true );
	}

	public function get_meta( $id = false, $key = false ) {
		if ( $id === false ){
			return false;
		}
		if ( $key === false ){
			return $this->get_all_meta( $id );
		} else {
			return $this->get_single_meta( $id, $key );
		}
	}

	private function get_all_meta( $id = false ) {
		if ( $id === false ){
			return false;
		}

		return maybe_unserialize( $this->model->get( array( 'media_id' => $id ) ) );
	}

	private function get_single_meta( $id = false, $key = false ) {
		if ( $id === false ){
			return false;
		}
		if ( $key === false ){
			return false;
		}
		$value = $this->model->get( array( 'media_id' => $id, 'meta_key' => $key ) );
		if ( isset( $value[ 0 ] ) ){
			return maybe_unserialize( $value[ 0 ]->meta_value );
		} else {
			return false;
		}
	}

	public function add_meta( $id = false, $key = false, $value = false, $duplicate = false ) {
		return $this->update_meta( $id, $key, $value, $duplicate );
	}

	public function update_meta( $id = false, $key = false, $value = false, $duplicate = false ) {
		if ( $id === false ){
			return false;
		}
		if ( $key === false ){
			return false;
		}
		if ( $value === false ){
			return false;
		}
		$value = maybe_serialize( $value );

		if ( $duplicate === true ){
			$media_meta = $this->model->insert( array( 'media_id' => $id, 'meta_key' => $key, 'meta_value' => $value ) );
		} else {
			if ( $this->get_single_meta( $id, $key ) ){
				$meta       = array( 'meta_value' => $value );
				$where      = array( 'media_id' => $id, 'meta_key' => $key );
				$media_meta = $this->model->update( $meta, $where );
			} else {
				$media_meta = $this->model->insert( array( 'media_id' => $id, 'meta_key' => $key, 'meta_value' => $value ) );
			}
		}

		return $media_meta;
	}

	public function delete_meta( $id = false, $key = false ) {
		if ( $id === false ){
			return false;
		}
		if ( $key === false ){
			$where = array( 'media_id' => $id );
		} else {
			$where = array( 'media_id' => $id, 'meta_key' => $key );
		}

		return $this->model->delete( $where );
	}

}