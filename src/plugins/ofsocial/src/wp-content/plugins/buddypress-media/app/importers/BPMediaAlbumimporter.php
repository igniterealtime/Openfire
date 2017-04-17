<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of BPMediaBPAlbumImporter
 *
 * @author saurabh
 */
class BPMediaAlbumimporter extends BPMediaImporter {

	function __construct() {
		global $wpdb;
		parent::__construct();
		$table = "{$wpdb->base_prefix}bp_album";
		if ( BPMediaImporter::table_exists( $table ) && BPMediaAlbumimporter::_active( 'bp-album/loader.php' ) != - 1 && ! $this->column_exists( 'import_status' ) ) {
			$this->update_table();
		}
	}

	function update_table() {
		if ( $this->column_exists( 'import_status' ) ) {
			return;
		}
		global $wpdb;

		return $wpdb->query( "ALTER TABLE {$wpdb->base_prefix}bp_album
                            ADD COLUMN import_status BIGINT (20) NOT NULL DEFAULT 0,
                            ADD COLUMN old_activity_id BIGINT (20) NOT NULL DEFAULT 0,
                            ADD COLUMN new_activity_id BIGINT (20) NOT NULL DEFAULT 0,
                            ADD COLUMN favorites TINYINT (1) NOT NULL DEFAULT 0" );
	}

	function column_exists( $column ) {
		global $wpdb;

		return $wpdb->query( "SHOW COLUMNS FROM {$wpdb->base_prefix}bp_album LIKE '$column'" );
	}

	function ui() {
		global $wpdb;
		$bp_album_active = BPMediaImporter::_active( 'bp-album/loader.php' );
		$table = "{$wpdb->base_prefix}bp_album";
		if ( BPMediaImporter::table_exists( $table ) ) {

			$this->progress = new rtProgress();
			$total = BPMediaAlbumimporter::get_total_count();
			//            print_r($total);
			$remaining_comments = $this->get_remaining_comments();
			//            print_r($remaining_comments);
			$finished = BPMediaAlbumimporter::get_completed_media( $total );
			//            print_r($finished);
			$finished_users = BPMediaAlbumimporter::get_completed_users();
			//            print_R($finished_users);
			$finished_comments = $this->get_finished_comments();
			//            print_r($finished_comments);
			$total_comments = ( int ) $finished_comments + ( int ) $remaining_comments;
			//            print_r($total_comments);
			$completed_users_favorites = ( int ) get_site_option( 'bp_media_bp_album_favorite_import_status', 0 );
			//            print_r($completed_users_favorites);
			$users = count_users();
			//            print_r($users);

			echo '<div id="bpmedia-bpalbumimporter">';
			if ( ( $finished[ 0 ]->media != $total[ 0 ]->media ) || ( $users[ 'total_users' ] > $completed_users_favorites ) ) {
				if ( 1 != $bp_album_active ) {
					echo '<div id="setting-error-bp-album-importer" class="error settings-error below-h2">
                        <p><strong>' . __( 'Warning!', 'buddypress-media' ) . '</strong> ' . sprintf( __( 'This import process is irreversible. Although everything is tested, please take a <a target="_blank" href="http://codex.wordpress.org/WordPress_Backups">backup of your database and files</a>, before proceeding. If you don\'t know your way around databases and files, consider <a target="_blank" href="%s">hiring us</a>, or another professional.', 'buddypress-media' ), 'http://rtcamp.com/contact/?purpose=buddypress&utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media' ) . '</p>';
					echo '<p>' . __( 'If you have set "WP_DEBUG" in you wp-config.php file, please make sure it is set to "false", so that it doesn\'t conflict with the import process.', 'buddypress-media' ) . '</p></div>';
					echo '<div class="bp-album-import-accept"><p><strong><label for="bp-album-import-accept"><input type="checkbox" value="accept" name="bp-album-import-accept" id="bp-album-import-accept" /> ' . __( 'I have taken a backup of the database and files of this site.', 'buddypress-media' ) . '</label></strong></p></div>';
					echo '<button id="bpmedia-bpalbumimport" class="button button-primary">';
					_e( 'Start Import', 'buddypress-media' );
					echo '</button>';
					echo '<div class="bp-album-importer-wizard">';
					echo '<div class="bp-album-users">';
					echo '<strong>';
					echo __( 'Users', 'buddypress-media' ) . ': <span class="finished">' . $finished_users[ 0 ]->users . '</span> / <span class="total">' . $total[ 0 ]->users . '</span>';
					echo '</strong>';
					if ( 0 != $total[ 0 ]->users ) {
						$users_progress = $this->progress->progress( $finished_users[ 0 ]->users, $total[ 0 ]->users );
						$this->progress->progress_ui( $users_progress );
					}
					echo '</div>';
					echo '<br />';
					echo '<div class="bp-album-media">';
					echo '<strong>';
					echo __( 'Media', 'buddypress-media' ) . ': <span class="finished">' . $finished[ 0 ]->media . '</span> / <span class="total">' . $total[ 0 ]->media . '</span>';
					echo '</strong>';
					$progress = 100;
					if ( 0 != $total[ 0 ]->media ) {
						$todo = $total[ 0 ]->media - $finished[ 0 ]->media;
						$steps = ceil( $todo / 5 );
						$laststep = $todo % 5;
						$progress = $this->progress->progress( $finished[ 0 ]->media, $total[ 0 ]->media );
						echo '<input type="hidden" value="' . $finished[ 0 ]->media . '" name="finished"/>';
						echo '<input type="hidden" value="' . $total[ 0 ]->media . '" name="total"/>';
						echo '<input type="hidden" value="' . $todo . '" name="todo"/>';
						echo '<input type="hidden" value="' . $steps . '" name="steps"/>';
						echo '<input type="hidden" value="' . $laststep . '" name="laststep"/>';
						$this->progress->progress_ui( $progress );
					}
					echo '</div>';
					echo '<br>';
					echo '<div class="bp-album-comments">';
					if ( 0 != $total_comments ) {
						echo '<strong>';
						echo __( 'Comments', 'buddypress-media' ) . ': <span class="finished">' . $finished_comments . '</span> / <span class="total">' . $total_comments . '</span>';
						echo '</strong>';
						$comments_progress = $this->progress->progress( $finished_comments, $total_comments );
						$this->progress->progress_ui( $comments_progress );
						echo '<br />';
					} else {
						echo '<p><strong>' . __( 'Comments: 0/0 (No comments to import)', 'buddypress-media' ) . '</strong></p>';
					}
					echo '</div>';
					if ( 0 != $completed_users_favorites ) {
						echo '<br />';
						echo '<div class="bp-album-favorites">';
						echo '<strong>';
						echo __( 'User\'s Favorites', 'buddypress-media' ) . ': <span class="finished">' . $completed_users_favorites . '</span> / <span class="total">' . $users[ 'total_users' ] . '</span>';
						echo '</strong>';
						$favorites_progress = $this->progress->progress( $completed_users_favorites, $users[ 'total_users' ] );
						$this->progress->progress_ui( $favorites_progress );
						echo '</div>';
					}
					echo '</div>';
				} else {
					$deactivate_link = wp_nonce_url( admin_url( 'plugins.php?action=deactivate&amp;plugin=' . urlencode( $this->path ) ), 'deactivate-plugin_' . $this->path );
					echo '<p>' . __( 'BP-Album is active on your site and will cause problems with the import.', 'buddypress-media' ) . '</p>';
					echo '<p><a class="button button-primary deactivate-bp-album" href="' . $deactivate_link . '">' . __( 'Click here to deactivate BP-Album and continue importing', 'buddypress-media' ) . '</a></p>';
				}
			} else {
				$corrupt_media = BPMediaAlbumimporter::get_corrupt_media();
				if ( $corrupt_media ) {
					echo '<div class="error below-h2">';
					echo '<p><strong>' . __( 'Some of the media failed to import. The file might be corrupt or deleted.', 'buddypress-media' ) . '</strong></p>';
					echo '<p>' . sprintf( __( 'The following %d BP Album Media id\'s could not be imported', 'buddypress-media' ), count( $corrupt_media ) ) . ': </p>';
					$corrupt_prefix_path = str_replace( '/wp-content', '', WP_CONTENT_URL );
					foreach ( $corrupt_media as $corrupt ) {
						echo '<p>' . $corrupt->id . ' => <a href="' . $corrupt_prefix_path . $corrupt->pic_org_url . '">' . $corrupt->title . '</a></p>';
					}
					echo '</div>';
				} else {
					echo '<div class="bp-album-import-accept i-accept">';
					echo '<p class="info">';
					$message = sprintf( __( 'I just imported bp-album to @rtMediaWP http://rt.cx/rtmedia on %s', 'buddypress-media' ), home_url() );
					echo '<strong>' . __( 'Congratulations!', 'buddypress-media' ) . '</strong> ' . __( 'All media from BP Album has been imported.', 'buddypress-media' );
					echo ' <a href="http://twitter.com/home/?status=' . $message . '" class="button button-import-tweet" target= "_blank">' . __( 'Tweet this', 'buddypress-media' ) . '</a>';
					echo '</p>';
					echo '</div>';
				}
				echo '<p>' . __( 'However, a lot of unnecessary files and a database table are still eating up your resources. If everything seems fine, you can clean this data up.', 'buddypress-media' ) . '</p>';
				echo '<br />';
				echo '<button id="bpmedia-bpalbumimport-cleanup" class="button btn-warning">';
				_e( 'Clean up Now', 'buddypress-media' );
				echo '</button>';
				echo ' <a href="' . esc_url( add_query_arg( array( 'page' => 'bp-media-settings' ), ( is_multisite() ? network_admin_url( 'admin.php' ) : admin_url( 'admin.php' ) ) ) ) . '" id="bpmedia-bpalbumimport-cleanup-later" class="button">';
				_e( 'Clean up Later', 'buddypress-media' );
				echo '</a>';
				echo '<br />';
				echo '<br />';
				echo '<br />';
				echo '<strong>' . __( 'Why don\'t you try adding some instagram like effects to your images?', 'buddypress-media' ) . '</strong>';
				echo '<div class="bp-media-addon">
                <a href="http://rtcamp.com/products/buddypress-media-instagram/?utm_source=dashboard&amp;utm_medium=plugin&amp;utm_campaign=buddypress-media&amp;utm_content=bp-album-importer" title="BuddyPress-Media Instagram" target="_blank">
                    <img width="240" height="184" title="BuddyPress-Media Instagram" alt="BuddyPress-Media Instagram" src="' . $img_src . 'BuddyPressMedia-Instagram.png?ref=bp-album-importer">
                </a>
                <h4><a href="http://rtcamp.com/products/buddypress-media-instagram/?utm_source=dashboard&amp;utm_medium=plugin&amp;utm_campaign=buddypress-media&amp;utm_content=bp-album-importer" title="BuddyPress-Media Instagram" target="_blank">BuddyPress-Media Instagram</a></h4>
                <div class="product_desc">
                    <p>' . __( 'BuddyPress Media Instagram adds Instagram like filters to images uploaded with BuddyPress Media.', 'buddypress-media' ) . '</p>
                    <p><strong>' . __( 'Important', 'buddypress-media' ) . ':</strong> ' . __( 'You need to have ImageMagick installed on your server for this addon to work.', 'buddypress-media' ) . '</p>
                </div>
                <div class="product_footer">
                    <span class="price alignleft"><span class="amount">$19</span></span>
                    <a class="add_to_cart_button  alignright product_type_simple" href="http://rtcamp.com/products/?utm_source=dashboard&amp;utm_medium=plugin&amp;utm_campaign=buddypress-media&amp;utm_content=bp-album-importer&amp;add-to-cart=34379" target="_blank">' . __( 'Buy Now', 'buddypress-media' ) . '</a>
                    <a class="alignleft product_demo_link" href="http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&amp;utm_medium=plugin&amp;utm_campaign=buddypress-media&amp;utm_content=bp-album-importer" title="BuddyPress-Media Instagram" target="_blank">' . __( 'Live Demo', 'buddypress-media' ) . '</a>
                </div></div>';
			}
			echo '</div>';
		} else {
			echo '<p>' . __( 'Looks like you don\'t use BP Album. Is there any other BuddyPress Plugin you want an importer for?', 'buddypress-media' ) . '</p>';
			echo '<p>' . sprintf( __( '<a href="%s">Create an issue</a> on GitHub requesting the same.', 'buddypress-media' ), 'https://github.com/rtCamp/rtMedia/issues/new' ) . '</p>';
		}
	}

	function create_album( $author_id, $album_name = 'Imported Media' ) {
		global $bp_media, $wpdb;

		if ( array_key_exists( 'bp_album_import_name', $bp_media->options ) ) {
			if ( '' != $bp_media->options[ 'bp_album_import_name' ] ) {
				$album_name = $bp_media->options[ 'bp_album_import_name' ];
			}
		}

		$query = "SELECT ID from $wpdb->posts WHERE post_type='bp_media_album' AND post_status = 'publish' AND post_author = $author_id AND post_title LIKE '{$album_name}'";
		$result = $wpdb->get_results( $query );
		if ( count( $result ) < 1 ) {
			$album = new BPMediaAlbum();
			$album->add_album( $album_name, $author_id );
			$album_id = $album->get_id();
		} else {
			$album_id = $result[ 0 ]->ID;
		}
		$wpdb->update( $wpdb->base_prefix . 'bp_activity', array( 'secondary_item_id' => - 999 ), array( 'id' => get_post_meta( $album_id, 'bp_media_child_activity', true ) ) );

		return $album_id;
	}

	static function get_total_count() {
		global $wpdb;
		$table = $wpdb->base_prefix . 'bp_album';
		if ( BPMediaAlbumimporter::table_exists( $table ) ) {
			return $wpdb->get_results( "SELECT COUNT(DISTINCT owner_id) as users, COUNT(id) as media FROM $table" );
		}

		return 0;
	}

	function get_remaining_comments() {
		global $wpdb;
		$bp_album_table = $wpdb->base_prefix . 'bp_album';
		$activity_table = $wpdb->base_prefix . 'bp_activity';
		if ( $this->table_exists( $bp_album_table ) ) {
			return $wpdb->get_var( "SELECT SUM( b.count ) AS total
                                        FROM (
                                            SELECT (
                                                SELECT COUNT( a.id )
                                                FROM $activity_table a
                                                WHERE a.item_id = activity.id
                                                AND a.component =  'activity'
                                                AND a.type =  'activity_comment'
                                            ) AS count
                                            FROM $activity_table AS activity
                                            INNER JOIN $bp_album_table AS album ON ( album.id = activity.item_id )
                                            WHERE activity.component =  'album'
                                            AND activity.type =  'bp_album_picture'
                                            AND album.import_status =0
                                        )b" );
		}

		return 0;
	}

	function get_finished_comments() {
		global $wpdb;
		$bp_album_table = $wpdb->base_prefix . 'bp_album';
		$activity_table = $wpdb->base_prefix . 'bp_activity';
		if ( $this->table_exists( $bp_album_table ) ) {
			return $wpdb->get_var( "SELECT COUNT( activity.id ) AS count
                                        FROM $activity_table AS activity
                                        INNER JOIN $bp_album_table AS album ON ( activity.item_id = album.import_status )
                                        WHERE activity.component =  'activity'
                                        AND activity.type =  'activity_comment'" );
		}

		return 0;
	}

	static function get_completed_users() {
		global $wpdb;
		$table = $wpdb->base_prefix . 'bp_album';
		if ( BPMediaAlbumimporter::table_exists( $table ) ) {
			return $wpdb->get_results( "SELECT COUNT( DISTINCT owner_id ) AS users
                                            FROM $table
                                            WHERE owner_id NOT
                                            IN (
                                                SELECT a.owner_id
                                                FROM $table a
                                                WHERE a.import_status =0
                                            )
                                        " );
		}

		return 0;
	}

	static function get_completed_media() {
		global $wpdb;
		$table = $wpdb->base_prefix . 'bp_album';
		if ( BPMediaAlbumimporter::table_exists( $table ) ) {
			return $wpdb->get_results( "SELECT COUNT(id) as media FROM $table WHERE import_status!=0" );
		}

		return 0;
	}

	static function get_corrupt_media() {
		global $wpdb;
		$table = $wpdb->base_prefix . 'bp_album';
		if ( BPMediaAlbumimporter::table_exists( $table ) ) {
			return $wpdb->get_results( "SELECT id,title,pic_org_url FROM $table WHERE import_status=-1" );
		}

		return 0;
	}

	static function batch_import( $count = 5 ) {
		global $wpdb;
		$table = $wpdb->base_prefix . 'bp_album';
		$bp_album_data = $wpdb->get_results( "SELECT * FROM $table WHERE import_status = 0 ORDER BY owner_id LIMIT $count" );

		return $bp_album_data;
	}

	static function bpmedia_ajax_import_callback() {

		$page = isset( $_GET[ 'page' ] ) ? $_GET[ 'page' ] : 1;
		$count = isset( $_GET[ 'count' ] ) ? $_GET[ 'count' ] : 5;
		$bp_album_data = BPMediaAlbumimporter::batch_import( $count );
		global $wpdb;
		$table = $wpdb->base_prefix . 'bp_album';
		$activity_table = $wpdb->base_prefix . 'bp_activity';
		$activity_meta_table = $wpdb->base_prefix . 'bp_activity_meta';
		$comments = 0;
		foreach ( $bp_album_data as &$bp_album_item ) {

			if ( get_site_option( 'bp_media_bp_album_importer_base_path' ) == '' ) {
				$base_path = pathinfo( $bp_album_item->pic_org_path );
				update_site_option( 'bp_media_bp_album_importer_base_path', $base_path[ 'dirname' ] );
			}
			$bpm_host_wp = new BPMediaHostWordpress();
			$bpm_host_wp->check_and_create_album( 0, 0, $bp_album_item->owner_id );
			$album_id = BPMediaAlbumimporter::create_album( $bp_album_item->owner_id, 'Imported Media' );
			$imported_media_id = BPMediaImporter::add_media( $album_id, $bp_album_item->title, $bp_album_item->description, $bp_album_item->pic_org_path, $bp_album_item->privacy, $bp_album_item->owner_id, 'Imported Media' );
			$wpdb->update( $table, array( 'import_status' => ( $imported_media_id ) ? $imported_media_id : - 1 ), array( 'id' => $bp_album_item->id ), array( '%d' ), array( '%d' ) );
			if ( $imported_media_id ) {
				$comments += ( int ) BPMediaAlbumimporter::update_recorded_time_and_comments( $imported_media_id, $bp_album_item->id, "{$wpdb->base_prefix}bp_album" );
				$bp_album_media_id = $wpdb->get_var( "SELECT activity.id from $activity_table as activity INNER JOIN $table as album ON ( activity.item_id = album.id ) WHERE activity.item_id = $bp_album_item->id AND activity.component = 'album' AND activity.type='bp_album_picture'" );
				$wpdb->update( $table, array( 'old_activity_id' => $bp_album_media_id ), array( 'id' => $bp_album_item->id ), array( '%d' ), array( '%d' ) );
				$bp_new_activity_id = $wpdb->get_var( "SELECT id from $activity_table WHERE item_id = $imported_media_id AND component = 'activity' AND type='activity_update' AND secondary_item_id=0" );
				$wpdb->update( $table, array( 'new_activity_id' => $bp_new_activity_id ), array( 'id' => $bp_album_item->id ), array( '%d' ), array( '%d' ) );
				if ( $wpdb->update( $activity_meta_table, array( 'activity_id' => $bp_new_activity_id ), array( 'activity_id' => $bp_album_media_id, 'meta_key' => 'favorite_count' ), array( '%d' ), array( '%d' ) ) ) {
					$wpdb->update( $table, array( 'favorites' => 1 ), array( 'id' => $bp_album_item->id ), array( '%d' ), array( '%d' ) );
				}
			}
		}

		$finished_users = BPMediaAlbumimporter::get_completed_users();

		echo json_encode( array( 'page' => $page, 'users' => $finished_users[ 0 ]->users, 'comments' => $comments ) );
		die();
	}

	static function bpmedia_ajax_import_favorites() {
		global $wpdb;
		$table = $wpdb->base_prefix . 'bp_album';
		$users = count_users();
		echo json_encode( array( 'favorites' => $wpdb->get_var( "SELECT COUNT(id) from $table WHERE favorites != 0" ), 'users' => $users[ 'total_users' ], 'offset' => ( int ) get_site_option( 'bp_media_bp_album_favorite_import_status', 0 ) ) );
		die();
	}

	static function bpmedia_ajax_import_step_favorites() {
		$offset = isset( $_POST[ 'offset' ] ) ? $_POST[ 'offset' ] : 0;
		$redirect = isset( $_POST[ 'redirect' ] ) ? $_POST[ 'redirect' ] : false;
		global $wpdb;
		$table = $wpdb->base_prefix . 'bp_album';
		$blogusers = get_users( array( 'meta_key' => 'bp_favorite_activities', 'offset' => $offset, 'number' => 1 ) );
		if ( $blogusers ) {
			foreach ( $blogusers as $user ) {
				$favorite_activities = get_user_meta( $user->ID, 'bp_favorite_activities', true );
				if ( $favorite_activities ) {
					$new_favorite_activities = $favorite_activities;
					foreach ( $favorite_activities as $key => $favorite ) {
						if ( $new_act = $wpdb->get_var( "SELECT new_activity_id from $table WHERE old_activity_id = $favorite" ) ) {
							$new_favorite_activities[ $key ] = $new_act;
						}
					}
					update_user_meta( $user->ID, 'bp_favorite_activities', $new_favorite_activities );
				}
				$completed_users_favorites = ( int ) get_site_option( 'bp_media_bp_album_favorite_import_status', 0 ) + 1;
				update_site_option( 'bp_media_bp_album_favorite_import_status', $completed_users_favorites );
			}
		}
		echo $redirect;
		die();
	}

	static function cleanup_after_install() {
		global $wpdb;
		$table = $wpdb->base_prefix . 'bp_album';
		$dir = get_site_option( 'bp_media_bp_album_importer_base_path' );
		BPMediaImporter::cleanup( $table, $dir );
		die();
	}

	static function update_recorded_time_and_comments( $media, $bp_album_id, $table ) {
		global $wpdb;
		if ( function_exists( 'bp_activity_add' ) ) {
			if ( ! is_object( $media ) ) {
				try {
					$media = new BPMediaHostWordpress( $media );
				} catch ( exception $e ) {
					return false;
				}
			}
			$activity_id = get_post_meta( $media->get_id(), 'bp_media_child_activity', true );
			if ( $activity_id ) {
				$date_uploaded = $wpdb->get_var( "SELECT date_uploaded from $table WHERE id = $bp_album_id" );
				$old_activity_id = $wpdb->get_var( "SELECT id from {$wpdb->base_prefix}bp_activity WHERE component = 'album' AND type = 'bp_album_picture' AND item_id = $bp_album_id" );
				if ( $old_activity_id ) {
					$comments = $wpdb->get_results( "SELECT id,secondary_item_id from {$wpdb->base_prefix}bp_activity WHERE component = 'activity' AND type = 'activity_comment' AND item_id = $old_activity_id" );
					foreach ( $comments as $comment ) {
						$update = array( 'item_id' => $activity_id );
						if ( $comment->secondary_item_id == $old_activity_id ) {
							$update[ 'secondary_item_id' ] = $activity_id;
						}
						$wpdb->update( $wpdb->base_prefix . 'bp_activity', $update, array( 'id' => $comment->id ) );
						BP_Activity_Activity::rebuild_activity_comment_tree( $activity_id );
					}
				}
				$wpdb->update( $wpdb->base_prefix . 'bp_activity', array( 'date_recorded' => $date_uploaded ), array( 'id' => $activity_id ) );

				return count( $comments );
			}

			return 0;
		}
	}

	static function bp_album_deactivate() {
		deactivate_plugins( 'bp-album/loader.php' );
		die( true );
	}

}
