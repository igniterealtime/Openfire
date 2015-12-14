<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of BPMediaImporter
 *
 * @author saurabh
 */
class BPMediaImporter {

	/**
	 *
	 */
	var $active;
	var $import_steps;

	function __construct(){

	}

	static function table_exists( $table ){
		global $wpdb;

		if ( 1 == $wpdb->query( "SHOW TABLES LIKE '" . $table . "'" ) ){
			return true;
		}

		return false;
	}

	static function _active( $path ){
		if ( ! function_exists( 'is_plugin_inactive' ) ){
			require_once( ABSPATH . '/wp-admin/includes/plugin.php' );
		}
		if ( is_plugin_active( $path ) ){
			return 1;
		}
		$plugins = get_plugins();
		if ( array_key_exists( $path, $plugins ) ){
			return 0;
		} else {
			return - 1;
		}
	}

	static function file_array( $filepath ){

		$path_info          = pathinfo( $filepath );
		$filetype           = wp_check_filetype( $filepath );
		$file['error']    = '';
		$file['name']     = $path_info['basename'];
		$file['type']     = $filetype['type'];
		$file['tmp_name'] = $filepath;
		$file['size']     = filesize( $filepath );

		return $file;
	}

	static function make_copy( $filepath ){
		$upload_dir = wp_upload_dir();
		$path_info  = pathinfo( $filepath );
		$tmp_dir    = trailingslashit( $upload_dir['basedir'] ) . 'bp-album-importer';
		$newpath    = trailingslashit( $tmp_dir ) . $path_info['basename'];
		if ( ! is_dir( $tmp_dir ) ){
			wp_mkdir_p( $tmp_dir );
		}
		if ( file_exists( $filepath ) ){
			if ( copy( $filepath, $newpath ) ){
				return BPMediaImporter::file_array( $newpath );
			}
		}

		return 0;
	}

	function create_album( $album_name = '', $author_id = 1 ){

		global $bp_media;

		if ( array_key_exists( 'bp_album_import_name', $bp_media->options ) ){
			if ( '' != $bp_media->options['bp_album_import_name'] ){
				$album_name = $bp_media->options['bp_album_import_name'];
			}
		}
		$found_album = BuddyPressMedia::get_wall_album();

		if ( count( $found_album ) < 1 ){
			$album = new BPMediaAlbum();
			$album->add_album( $album_name, $author_id );
			$album_id = $album->get_id();
		} else {
			$album_id = $found_album[0]->ID;
		}

		return $album_id;
	}

	static function add_media( $album_id, $title = '', $description = '', $filepath = '', $privacy = 0, $author_id = false, $album_name = false ){

		$files = BPMediaImporter::make_copy( $filepath );
		if ( $files ){
			global $wpdb;
			$bp_imported_media = new BPMediaHostWordpress();
			//            add_filter('bp_media_force_hide_activity', create_function('', 'return true;'));
			$imported_media_id = $bp_imported_media->insertmedia( $title, $description, $album_id, 0, false, false, $files, $author_id, $album_name );

			wp_update_post( $args = array( 'ID' => $imported_media_id, 'post_author' => $author_id ) );

			$bp_album_privacy = $privacy;
			if ( $bp_album_privacy == 10 ){
				$bp_album_privacy = 6;
			}

			$privacy = new BPMediaPrivacy();
			$privacy->save( $bp_album_privacy, $imported_media_id );

			return $imported_media_id;
		}

		return 0;
	}

	static function cleanup( $table, $directory ){
		global $wpdb;
		$wpdb->query( "DROP TABLE IF EXISTS $table" );
		$wpdb->query( $wpdb->prepare( "
                DELETE FROM {$wpdb->base_prefix}bp_activity
		 WHERE component = %s
		", 'album' ) );
		if ( is_dir( $directory ) ){
			BPMediaImporter::delete( $directory );
		}
	}

	static function delete( $path ){
		if ( true === is_dir( $path ) ){
			$files = array_diff( scandir( $path ), array( '.', '..' ) );

			foreach ( $files as $file ) {
				BPMediaImporter::delete( realpath( $path ) . '/' . $file );
			}

			return rmdir( $path );
		} else {
			if ( true === is_file( $path ) ){
				return unlink( $path );
			}
		}

		return false;
	}

}
