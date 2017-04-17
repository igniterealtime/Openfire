<?php

/**
 * Description of BuddyPress_Migration
 *
 * @author faishal
 */
class RTMediaMigration {

	public $bmp_table = '';

	function __construct(){
		global $wpdb;
		$this->bmp_table = $wpdb->base_prefix . 'rt_rtm_media';

		add_action( 'admin_menu', array( $this, 'menu' ) );
		add_action( 'wp_ajax_bp_media_rt_db_migration', array( $this, 'migrate_to_new_db' ) );

		if ( isset( $_REQUEST['page'] ) && 'rtmedia-migration' == $_REQUEST['page'] && isset( $_REQUEST['hide'] ) && 'true' == $_REQUEST['hide'] ){
			$this->hide_migration_notice();
			wp_safe_redirect( esc_url_raw( $_SERVER['HTTP_REFERER'] ) );
		}
		if ( false !== rtmedia_get_site_option( 'rt_migration_hide_notice' ) ){
			return true;
		}

		if ( isset( $_REQUEST['force'] ) && 'true' === $_REQUEST['force'] ){
			$pending = false;
		} else {
			$pending = rtmedia_get_site_option( 'rtMigration-pending-count' );
		}

		if ( false === $pending ){
			$total   = $this->get_total_count();
			$done    = $this->get_done_count();
			$pending = $total - $done;
			if ( $pending < 0 ){
				$pending = 0;
			}
			rtmedia_update_site_option( 'rtMigration-pending-count', $pending );
		}
		if ( $pending > 0 ){
			if ( ! ( isset( $_REQUEST['page'] ) && 'rtmedia-migration' == $_REQUEST['page'] ) ){
				add_action( 'admin_notices', array( &$this, 'add_migration_notice' ) );
			}
		}
	}

	function hide_migration_notice(){
		rtmedia_update_site_option( 'rt_migration_hide_notice', true );
	}

	function migrate_image_size_fix(){
		if ( '' == rtmedia_get_site_option( 'rt_image_size_migration_fix', '' ) ){
			global $wpdb;
			$sql = $wpdb->prepare( "update $wpdb->postmeta set meta_value=replace(meta_value	,%s,%s) where meta_key = '_wp_attachment_metadata';", 'bp_media', 'rt_media' );
			$wpdb->get_row( $sql );
			update_option( 'rt_image_size_migration_fix', 'fix' );
		}
	}

	function add_migration_notice(){
		if ( current_user_can( 'manage_options' ) ){
			$this->create_notice( '<p><strong>rtMedia</strong>: ' . __( 'Please Migrate your Database', 'buddypress-media' ) . " <a href='" . admin_url( 'admin.php?page=rtmedia-migration&force=true' ) . "'>" . __( 'Click Here', 'buddypress-media' ) . "</a>.  <a href='" . admin_url( 'admin.php?page=rtmedia-migration&hide=true' ) . "' style='float:right'>" . __( 'Hide', 'buddypress-media' ) . '</a> </p>' );
		}
	}

	function create_notice( $message, $type = 'error' ){
		echo '<div class="' . $type . '">' . $message . '</div>';
	}

	static function table_exists( $table ){
		global $wpdb;

		if ( 1 == $wpdb->query( "SHOW TABLES LIKE '" . $table . "'" ) ){
			return true;
		}

		return false;
	}

	function menu(){
		add_submenu_page( 'rtmedia-setting', __( 'Migration', 'buddypress-media' ), __( 'Migration', 'buddypress-media' ), 'manage_options', 'rtmedia-migration', array( $this, 'test' ) );
	}

	function get_total_count(){
		global $wpdb;
		if ( function_exists( 'bp_core_get_table_prefix' ) ){
			$bp_prefix = bp_core_get_table_prefix();
		} else {
			$bp_prefix = '';
		}
		$sql_album_usercount = "select count(*) FROM $wpdb->usermeta where meta_key ='bp-media-default-album' ";

		$_SESSION['migration_user_album'] = $wpdb->get_var( $sql_album_usercount );
		$count                              = intval( $_SESSION['migration_user_album'] );

		if ( $this->table_exists( $bp_prefix . 'bp_groups_groupmeta' ) ){
			$sql_album_groupcount                = "select count(*) FROM {$bp_prefix}bp_groups_groupmeta where meta_key ='bp_media_default_album'";
			$_SESSION['migration_group_album'] = $wpdb->get_var( $sql_album_groupcount );
			$count += intval( $_SESSION['migration_group_album'] );
		}
		if ( $this->table_exists( $bp_prefix . 'bp_activity' ) ){
			//$sql_bpm_comment_count = "select count(*) from {$bp_prefix}bp_activity where component='activity' and type='activity_comment' and is_spam <> 1 and ;";
			$sql_bpm_comment_count = "SELECT
                                                    count(id)
                                                FROM
                                                    {$bp_prefix}bp_activity left outer join (select distinct
                                                            a.meta_value
                                                        from
                                                            $wpdb->postmeta a
                                                                left join
                                                            $wpdb->posts p ON (a.post_id = p.ID)
                                                        where
                                                            (NOT p.ID IS NULL)
                                                                and a.meta_key = 'bp_media_child_activity') p
							on  {$bp_prefix}bp_activity.item_id = p.meta_value
                                                where
                                                    type = 'activity_comment'
                                                    and is_spam <>1 and
                                                        not p.meta_value is NULL";

			//echo  $sql_bpm_comment_count;

			$_SESSION['migration_activity'] = $wpdb->get_var( $sql_bpm_comment_count );
			$count += intval( $_SESSION['migration_activity'] );
		}

		$sql = "select count(*)
                from
                    {$wpdb->postmeta} a
                        left join
                    {$wpdb->postmeta} b ON ((a.post_id = b.post_id)
                        and (b.meta_key = 'bp_media_privacy'))
                        left join
                    {$wpdb->postmeta} c ON (a.post_id = c.post_id)
                        and (c.meta_key = 'bp_media_child_activity')
                        left join
                    {$wpdb->posts} p ON (a.post_id = p.ID)
                where
                    a.post_id > 0 and  (NOT p.ID IS NULL)
                        and a.meta_key = 'bp-media-key'";

		$_SESSION['migration_media'] = $wpdb->get_var( $sql );
		$count += intval( $_SESSION['migration_media'] );

		// var_dump($_SESSION);
		return $count;
	}

	function get_last_imported(){
		$album    = rtmedia_get_site_option( 'rtmedia-global-albums' );
		$album_id = $album[0];

		global $wpdb;
		$sql = "select a.post_ID
                from
                    {$wpdb->postmeta} a  left join
                    {$wpdb->posts} p ON (a.post_id = p.ID)
                where
                     a.meta_key = 'bp-media-key' and  (NOT p.ID IS NULL) and a.post_id not in (select media_id
                from {$this->bmp_table} where blog_id = %d and media_id <> %d ) order by a.post_ID";
		$sql = $wpdb->prepare( $sql, get_current_blog_id(), $album_id );
		$row = $wpdb->get_row( $sql );
		if ( $row ){
			return $row->post_ID;
		} else {
			return false;
		}
	}

	function get_done_count( $flag = false ){
		global $wpdb;
		$sql = "select count(*)
                from {$this->bmp_table} where blog_id = %d and media_id in (select a.post_id
                from
                    {$wpdb->postmeta} a
                        left join
                    {$wpdb->postmeta} b ON ((a.post_id = b.post_id)
                        and (b.meta_key = 'bp_media_privacy'))
                        left join
                    {$wpdb->postmeta} c ON (a.post_id = c.post_id)
                        and (c.meta_key = 'bp_media_child_activity')
                        left join
                    {$wpdb->posts} p ON (a.post_id = p.ID)
                where
                    a.post_id > 0 and  (NOT p.ID IS NULL)
                        and a.meta_key = 'bp-media-key')";

		$media_count = $wpdb->get_var( $wpdb->prepare( $sql, get_current_blog_id() ) );
		if ( $flag ){
			return $media_count - 1;
		}
		$state = intval( rtmedia_get_site_option( 'rtmedia-migration', '0' ) );
		if ( 5 == $state ){
			$album_count = intval( $_SESSION['migration_user_album'] );
			$album_count += ( isset( $_SESSION['migration_group_album'] ) ) ? intval( $_SESSION['migration_group_album'] ) : 0;
		} else {
			if ( $state > 0 ){
				if ( function_exists( 'bp_core_get_table_prefix' ) ){
					$bp_prefix = bp_core_get_table_prefix();
				} else {
					$bp_prefix = '';
				}
				$pending_count = "select count(*) from $wpdb->posts where post_type='bp_media_album' and ( ID in (select meta_value FROM $wpdb->usermeta where meta_key ='bp-media-default-album') ";
				if ( $this->table_exists( $bp_prefix . 'bp_groups_groupmeta' ) ){
					$pending_count .= " or ID in (select meta_value FROM {$bp_prefix}bp_groups_groupmeta where meta_key ='bp_media_default_album')";
				}
				$pending_count .= ')';
				$pending_count = $wpdb->get_var( $pending_count );

				$album_count = intval( $_SESSION['migration_user_album'] );
				$album_count += ( isset( $_SESSION['migration_group_album'] ) ) ? intval( $_SESSION['migration_group_album'] ) : 0;
				$album_count = $album_count - intval( $pending_count );
			} else {
				$album_count = 0;
			}
		}
		if ( isset( $_SESSION['migration_activity'] ) && intval( $_SESSION['migration_media'] ) == intval( $media_count ) ){
			$comment_sql = $_SESSION['migration_activity'];
		} else {
			$comment_sql = $wpdb->get_var( "select count(*)
                                                from $wpdb->comments a
                                                 where a.comment_post_ID in (select b.media_id from $this->bmp_table b  left join
                                                                                            {$wpdb->posts} p ON (b.media_id = p.ID) where  (NOT p.ID IS NULL) ) and a.comment_agent=''" );
		}

		//echo $media_count . "--" . $album_count . "--" . $comment_sql;
		return $media_count + $album_count + $comment_sql;
	}

	function return_migration(){
		$total   = $this->get_total_count();
		$done    = $this->get_done_count();
		$pending = $total - $done;
		if ( $pending < 0 ){
			$pending = 0;
			$done    = $total;
		}
		if ( $done > $total ){
			$done = $total;
		}
		if ( $done == $total ){
			global $wp_rewrite;
			//Call flush_rules() as a method of the $wp_rewrite object
			$wp_rewrite->flush_rules( true );
		}
		rtmedia_update_site_option( 'rtMigration-pending-count', $pending );
		$pending_time = $this->formatSeconds( $pending );

		echo json_encode( array( 'status' => true, 'done' => $done, 'total' => $total, 'pending' => $pending_time ) );
		die();
	}

	function manage_album(){
		$album = rtmedia_get_site_option( 'rtmedia-global-albums' );
		$stage = intval( rtmedia_get_site_option( 'rtmedia-migration', '0' ) );

		$album_rt_id = $album[0];

		$album_post_type = 'rtmedia_album';

		global $wpdb;

		$album_id = $wpdb->get_var( $wpdb->prepare( "select media_id from $this->bmp_table where id = %d", $album_rt_id ) );

		if ( function_exists( 'bp_core_get_table_prefix' ) ){
			$bp_prefix = bp_core_get_table_prefix();
		} else {
			$bp_prefix = '';
		}

		if ( $stage < 1 ){
			global $wpdb;
			if ( function_exists( 'bp_core_get_table_prefix' ) ){
				$bp_prefix = bp_core_get_table_prefix();
			} else {
				$bp_prefix = '';
			}
			$sql = $wpdb->prepare( "update {$bp_prefix}bp_activity set content=replace(content,%s,%s) where id > 0;", '<ul class="bp-media-list-media">', '<div class="rtmedia-activity-container"><ul class="rtmedia-list large-block-grid-3">' );
			$wpdb->get_row( $sql );
			$sql = $wpdb->prepare( "update {$bp_prefix}bp_activity set content=replace(content,%s,%s) where id > 0;", '</ul>', '</ul></div>' );
			$wpdb->get_row( $sql );

			$sql_group = "update $wpdb->posts set post_parent='{$album_id}' where post_parent in (select meta_value FROM $wpdb->usermeta where meta_key ='bp-media-default-album') ";
			if ( $this->table_exists( $bp_prefix . 'bp_groups_groupmeta' ) ){
				$sql_group .= " or post_parent in (select meta_value FROM {$bp_prefix}bp_groups_groupmeta where meta_key ='bp_media_default_album')";
			}
			$wpdb->query( $sql_group );
			$stage = 1;
			rtmedia_update_site_option( 'rtmedia-migration', $stage );
			$this->return_migration();
		}
		if ( $stage < 2 ){
			$sql_delete = "select * from $wpdb->posts where post_type='bp_media_album' and ID in (select meta_value FROM $wpdb->usermeta where meta_key ='bp-media-default-album') limit 10";
			$results    = $wpdb->get_results( $sql_delete );
			$delete_ids = '';
			$sep        = '';
			foreach ( $results as $result ) {
				$this->search_and_replace( $result->guid, trailingslashit( get_rtmedia_user_link( $result->post_author ) ) . RTMEDIA_MEDIA_SLUG . '/' . $album_rt_id );
				$delete_ids .= $sep . $result->ID;
				$sep = ',';
			}
			if ( '' != $delete_ids ){
				$wpdb->query( "delete from $wpdb->posts where ID in ({$delete_ids})" );
			}
			if ( count( $results ) < 10 ){
				$stage = 2;
			}
			rtmedia_update_site_option( 'rtmedia-migration', $stage );
			$this->return_migration();
		}
		if ( $stage < 3 ){
			if ( $this->table_exists( $bp_prefix . 'bp_groups_groupmeta' ) ){
				$sql_delete = "select * from $wpdb->posts where post_type='bp_media_album' and ID in (select meta_value FROM {$bp_prefix}bp_groups_groupmeta where meta_key ='bp_media_default_album') limit 10";
				$results    = $wpdb->get_results( $sql_delete );
				$delete_ids = '';
				$sep        = '';
				if ( $results ){
					foreach ( $results as $result ) {
						$group_id = abs( intval( get_post_meta( $result->ID, 'bp-media-key', true ) ) );
						$this->search_and_replace( trailingslashit( get_rtmedia_group_link( $group_id ) ) . 'albums/' . $result->ID, trailingslashit( get_rtmedia_group_link( $group_id ) ) . RTMEDIA_MEDIA_SLUG . '/' . $album_rt_id );
						$delete_ids .= $sep . $result->ID;
						$sep = ',';
					}
					if ( '' != $delete_ids ){
						$wpdb->query( "delete from $wpdb->posts where ID in ({$delete_ids})" );
					}
					if ( count( $results ) < 10 ){
						$stage = 3;
					}
				} else {
					$stage = 3;
				}
				rtmedia_update_site_option( 'rtmedia-migration', $stage );
				$this->return_migration();
			} else {
				$stage = 3;
				rtmedia_update_site_option( 'rtmedia-migration', $stage );
				$this->return_migration();
			}
		}

		$sql = "update $wpdb->posts set post_type='{$album_post_type}' where post_type='bp_media_album'";
		if ( false !== $wpdb->query( $sql ) ){
			rtmedia_update_site_option( 'rtmedia-migration', '5' );

			return true;
		}

		return false;
	}

	function test(){
		if ( ! $this->table_exists( $this->bmp_table ) ){
			$obj                     = new RTDBUpdate( false, RTMEDIA_PATH . 'index.php', RTMEDIA_PATH . 'app/schema/', true );
			$obj->install_db_version = '0';
			$obj->do_upgrade( true );
		}
		global $rtmedia_error;
		if ( isset( $rtmedia_error ) && true === $rtmedia_error ){
			?>
			<div class="error"><p> Please Resolve create database error before migration.</p></div>
		<?php
		}

		$prog  = new rtProgress();
		$total = $this->get_total_count();
		$done  = $this->get_done_count();
		if ( $done >= $total ){
			$done = $total;
		} else {
			?>
			<div class="error">
				<p><?php _e( 'Please Backup your <strong>DATABASE</strong> and <strong>UPLOAD</strong> folder before Migration.', 'buddypress-media' ); ?></p>
			</div>
		<?php
		}
		?>

		<div class="wrap">

			<h2><?php _e( 'rtMedia Migration', 'buddypress-media' ); ?></h2>

			<h3><?php _e( 'It will migrate following things', 'buddypress-media' ); ?> </h3>
			User Albums : <?php echo $_SESSION['migration_user_album']; ?><br/>
			<?php if ( isset( $_SESSION['migration_group_album'] ) ){ ?>
				Groups Albums : <?php echo $_SESSION['migration_group_album']; ?><br/>
			<?php } ?>
			Media : <?php echo $_SESSION['migration_media']; ?><br/>
			<?php if ( isset( $_SESSION['migration_activity'] ) ){ ?>
				Comments : <?php echo $_SESSION['migration_activity']; ?><br/>
			<?php } ?>
			<hr/>

			<?php
			echo '<span class="pending">' . $this->formatSeconds( $total - $done ) . '</span><br />';
			echo '<span class="finished">' . $done . '</span>/<span class="total">' . $total . '</span>';
			echo '<img src="images/loading.gif" alt="syncing" id="rtMediaSyncing" style="display:none" />';

			$temp = $prog->progress( $done, $total );
			$prog->progress_ui( $temp, true );
			?>
			<script type="text/javascript">
				jQuery( document ).ready( function ( e ) {
					jQuery( "#toplevel_page_rtmedia-settings" ).addClass( "wp-has-current-submenu" )
					jQuery( "#toplevel_page_rtmedia-settings" ).removeClass( "wp-not-current-submenu" )
					jQuery( "#toplevel_page_rtmedia-settings" ).addClass( "wp-menu-open" )
					jQuery( "#toplevel_page_rtmedia-settings>a" ).addClass( "wp-menu-open" )
					jQuery( "#toplevel_page_rtmedia-settings>a" ).addClass( "wp-has-current-submenu" )

					if ( db_total < 1 )
						jQuery( "#submit" ).attr( 'disabled', "disabled" );
				} )
				function db_start_migration( db_done, db_total ) {


					if ( db_done < db_total ) {
						jQuery( "#rtMediaSyncing" ).show();
						jQuery.ajax( {
							url: rtmedia_admin_ajax,
							type: 'post',
							data: {
								"action": "bp_media_rt_db_migration",
								"done": db_done
							},
							success: function ( sdata ) {

								try {
									data = JSON.parse( sdata );
								} catch ( e ) {
									jQuery( "#submit" ).attr( 'disabled', "" );
								}
								if ( data.status ) {
									done = parseInt( data.done );
									total = parseInt( data.total );
									var progw = Math.ceil( (done / total) * 100 );
									if ( progw > 100 ) {
										progw = 100;
									}
									;
									jQuery( '#rtprogressbar>div' ).css( 'width', progw + '%' );
									jQuery( 'span.finished' ).html( done );
									jQuery( 'span.total' ).html( total );
									jQuery( 'span.pending' ).html( data.pending );
									db_start_migration( done, total );
								} else {
									alert( "Migration completed." );
									jQuery( "#rtMediaSyncing" ).hide();
								}
							},
							error: function () {
								alert( "<?php _e( 'Error During Migration, Please Refresh Page then try again', 'buddypress-media' ); ?>" );
								jQuery( "#submit" ).removeAttr( 'disabled' );
							}
						} );
					} else {
						alert( "Migration completed." );
						jQuery( "#rtMediaSyncing" ).hide();
					}
				}
				var db_done = <?php echo $done; ?>;
				var db_total = <?php echo $total; ?>;
				jQuery( document ).on( 'click', '#submit', function ( e ) {
					e.preventDefault();

					db_start_migration( db_done, db_total );
					jQuery( this ).attr( 'disabled', 'disabled' );
				} );
			</script>
			<hr/>
			<?php if ( ! ( isset( $rtmedia_error ) && true === $rtmedia_error ) ){ ?>
				<input type="button" id="submit" value="<?php esc_attr_e( 'Start', 'buddypress-media' ); ?>"
					   class="button button-primary"/>
			<?php } ?>

		</div>
	<?php
	}

	function migrate_to_new_db( $lastid = 0, $limit = 1 ){

		if ( ! isset( $_SESSION['migration_media'] ) ){
			$this->get_total_count();
		}

		$state = intval( rtmedia_get_site_option( 'rtmedia-migration' ) );
		if ( $state < 5 ){
			if ( $this->manage_album() ){
				$this->migrate_encoding_options();
				$this->return_migration();
			}
		}

		if ( intval( $_SESSION['migration_media'] ) >= $this->get_done_count( true ) ){

			if ( ! $lastid ){
				$lastid = $this->get_last_imported();
				if ( ! $lastid ){
					$this->return_migration();
				}
			}
			global $wpdb;
			$sql = "select
                    a.post_id as 'post_id',
                    b.meta_value as 'privacy',
                    a.meta_value as 'context_id',
                    c.meta_value as 'activity_id',
                    p.post_type,
                    p.post_mime_type,
                    p.post_author as 'media_author',
                    p.post_title as 'media_title',
                    p.post_parent as 'parent'
                from
                    {$wpdb->postmeta} a
                        left join
                    {$wpdb->postmeta} b ON ((a.post_id = b.post_id)
                        and (b.meta_key = 'bp_media_privacy'))
                        left join
                    {$wpdb->postmeta} c ON (a.post_id = c.post_id)
                        and (c.meta_key = 'bp_media_child_activity')
                        left join
                    {$wpdb->posts} p ON (a.post_id = p.ID)
                where
                    a.post_id >= %d and (NOT p.ID is NULL)
                        and a.meta_key = 'bp-media-key'
                order by a.post_id
                limit %d";

			$results = $wpdb->get_results( $wpdb->prepare( $sql, $lastid, $limit ) );

			if ( function_exists( 'bp_core_get_table_prefix' ) ){
				$bp_prefix = bp_core_get_table_prefix();
			} else {
				$bp_prefix = '';
			}
			if ( $results ){

				foreach ( $results as $result ) {
					$this->migrate_single_media( $result );
				}
			}
		} else {
			global $wp_rewrite;
			//Call flush_rules() as a method of the $wp_rewrite object
			$wp_rewrite->flush_rules( true );
			//            echo json_encode(array("status" => false, "done" => $done, "total" => $this->get_total_count()));
			//            die();
		}
		$this->return_migration();
	}

	function migrate_encoding_options(){
		$encoding_mnigration_array = array(
			'bp-media-encoding-api-key' => 'rtmedia-encoding-api-key', 'bp-media-encoding-usage-limit-mail' => 'rtmedia-encoding-usage-limit-mail', 'bp-media-encoding-usage' => 'rtmedia-encoding-usage', 'bpmedia_encoding_service_notice' => 'rtmedia-encoding-service-notice', 'bpmedia_encoding_expansion_notice' => 'rtmedia-encoding-expansion-notice', 'bp_media_ffmpeg_options' => 'rtmedia-ffmpeg-options', 'bp_media_kaltura_options' => 'rtmedia-kaltura-options',
		);
		foreach ( $encoding_mnigration_array as $key => $ma ) {
			if ( false !== ( $value = rtmedia_get_site_option( $key ) ) ){
				rtmedia_update_site_option( $ma, $value );
			}
		}
	}

	function migrate_single_media( $result, $album = false ){
		$blog_id = get_current_blog_id();
		$old     = $result;
		if ( function_exists( 'bp_core_get_table_prefix' ) ){
			$bp_prefix = bp_core_get_table_prefix();
		} else {
			$bp_prefix = '';
		}
		global $wpdb;

		if ( false !== $album && ! ( is_object( $result ) ) ){
			$id = $wpdb->get_var( $wpdb->prepare( "select ID from $this->bmp_table where media_id = %d", $result ) );
			if ( null == $id ){
				$sql    = "select
                        a.post_id as 'post_id',
                        a.meta_value as 'privacy',
                        b.meta_value as 'context_id',
                        c.meta_value as 'activity_id',
                        p.post_type,
                        p.post_mime_type,
                        p.post_author as 'media_author',
                        p.post_title as 'media_title',
                        p.post_parent as 'parent'
                    from
                        {$wpdb->postmeta} a
                            left join
                        {$wpdb->postmeta} b ON ((a.post_id = b.post_id)
                            and (b.meta_key = 'bp-media-key'))
                            left join
                        {$wpdb->postmeta} c ON (a.post_id = c.post_id)
                            and (c.meta_key = 'bp_media_child_activity')
                            left join
                        {$wpdb->posts} p ON (a.post_id = p.ID)
                    where
                        a.post_id = %d and (NOT p.ID IS NULL)
                            and a.meta_key = 'bp_media_privacy'";
				$result = $wpdb->get_row( $wpdb->prepare( $sql, $result ) );
			} else {
				return $id;
			}
		}
		if ( ! isset( $result ) || ! isset( $result->post_id ) ){
			return $old;
		}
		$media_id = $result->post_id;

		if ( intval( $result->context_id ) > 0 ){
			$media_context = 'profile';
			$prefix        = 'users/' . abs( intval( $result->context_id ) );
		} else {
			$media_context = 'group';
			$prefix        = bp_get_groups_root_slug() . abs( intval( $result->context_id ) );
		}

		$old_type = '';
		if ( 'attachment' != $result->post_type ){
			$media_type = 'album';
		} else {
			$mime_type = strtolower( $result->post_mime_type );
			$old_type  = '';
			if ( 0 === strpos( $mime_type, 'image' ) ){
				$media_type = 'photo';
				$old_type   = 'photos';
			} else {
				if ( 0 === strpos( $mime_type, 'audio' ) ){
					$media_type = 'music';
					$old_type   = 'music';
				} else {
					if ( 0 === strpos( $mime_type, 'video' ) ){
						$media_type = 'video';
						$old_type   = 'videos';
					} else {
						$media_type = 'other';
					}
				}
			}
		}

		$activity_data = $wpdb->get_row( $wpdb->prepare( "select * from {$bp_prefix}bp_activity where id= %d", $result->activity_id ) );
		if ( 'album' != $media_type ){
			$this->importmedia( $media_id, $prefix );
		}

		if ( $this->table_exists( $bp_prefix . 'bp_activity' ) && class_exists( 'BP_Activity_Activity' ) ){
			$bp_activity  = new BP_Activity_Activity();
			$activity_sql = $wpdb->prepare( "SELECT
                            *
                        FROM
                            {$bp_prefix}bp_activity
                        where
                                        id in (select distinct
                                    a.meta_value
                                from
                                    $wpdb->postmeta a
                                        left join
                                    $wpdb->posts p ON (a.post_id = p.ID)
                                where
                                    (NOT p.ID IS NULL) and p.ID = %d
                and a.meta_key = 'bp_media_child_activity')", $media_id );
			$all_activity = $wpdb->get_results( $activity_sql );
			remove_all_actions( 'wp_insert_comment' );
			foreach ( $all_activity as $activity ) {
				$comments = $bp_activity->get_activity_comments( $activity->id, $activity->mptt_left, $activity->mptt_right );
				$exclude  = get_post_meta( $media_id, 'rtmedia_imported_activity', true );
				if ( ! is_array( $exclude ) ){
					$exclude = array();
				}
				if ( $comments ){
					$this->insert_comment( $media_id, $comments, $exclude );
				}
			}
		}
		if ( 0 !== intval( $result->parent ) ){
			$album_id = $this->migrate_single_media( $result->parent, true );
		} else {
			$album_id = 0;
		}
		if ( function_exists( 'bp_activity_get_meta' ) ){
			$likes = bp_activity_get_meta( $result->activity_id, 'favorite_count' );
		} else {
			$likes = 0;
		}

		$wpdb->insert( $this->bmp_table, array(
				'blog_id' => $blog_id,
				'media_id' => $media_id,
				'media_type' => $media_type,
				'context' => $media_context,
				'context_id' => abs( intval( $result->context_id ) ),
				'activity_id' => $result->activity_id,
				'privacy' => intval( $result->privacy ) * 10,
				'media_author' => $result->media_author,
				'media_title' => $result->media_title,
				'album_id' => $album_id,
				'likes' => $likes,
			), array( '%d', '%d', '%s', '%s', '%d', '%d', '%d', '%d', '%s', '%d', '%d' ) );
		$last_id = $wpdb->insert_id;

		// Photo tag meta migration
		//$photo_tag_meta = get_post_meta($media_id, "bp-media-user-tags", true);
		//        if($photo_tag_meta && !empty($photo_tag_meta)){
		//            $wpdb->insert(
		//                $wpdb->prefix . "rt_rtm_media_meta", array(
		//                    'media_id' => $media_id,
		//                    'meta_key' => "user-tags",
		//                    "meta_value" =>  maybe_serialize($photo_tag_meta)), array('%d', '%s', '%s'));
		//        }
		if ( 'album' != $media_type && function_exists( 'bp_core_get_user_domain' ) && $activity_data ){
			if ( function_exists( 'bp_core_get_table_prefix' ) ){
				$bp_prefix = bp_core_get_table_prefix();
			} else {
				$bp_prefix = '';
			}

			$activity_data->old_primary_link = $activity_data->primary_link;
			$parent_link                     = get_rtmedia_user_link( $activity_data->user_id );
			$activity_data->primary_link     = $parent_link . RTMEDIA_MEDIA_SLUG . '/' . $last_id;
			$this->search_and_replace( $activity_data->old_primary_link, $activity_data->primary_link );
			$activity_data->action  = str_replace( $activity_data->old_primary_link, $activity_data->primary_link, $activity_data->action );
			$activity_data->content = str_replace( $activity_data->old_primary_link, $activity_data->primary_link, $activity_data->content );
			global $last_baseurl, $last_newurl;

			$replace_img = $last_newurl; //$last_baseurl . "rtMedia/$prefix/";
			if ( false === strpos( $activity_data->content, $replace_img ) ){
				$activity_data->content = str_replace( $last_baseurl, $replace_img, $activity_data->content );
			}
			global $wpdb;
			$wpdb->update( $bp_prefix . 'bp_activity', array(
				'primary_link' => $activity_data->primary_link, 'action' => $activity_data->action, 'content' => $activity_data->content,
			), array( 'id' => $activity_data->id ) );
		} else {
			if ( 'group' == $media_context ){
				$activity_data->old_primary_link = $activity_data->primary_link;
				$parent_link                     = get_rtmedia_group_link( abs( intval( $result->context_id ) ) );
				$parent_link                     = trailingslashit( $parent_link );
				$activity_data->primary_link     = trailingslashit( $parent_link . RTMEDIA_MEDIA_SLUG . '/' . $last_id );
				$this->search_and_replace( $activity_data->old_primary_link, $activity_data->primary_link );
			} else {
				$activity_data->old_primary_link = $activity_data->primary_link;
				$parent_link                     = get_rtmedia_user_link( $activity_data->user_id );
				$parent_link                     = trailingslashit( $parent_link );
				$activity_data->primary_link     = trailingslashit( $parent_link . RTMEDIA_MEDIA_SLUG . '/' . $last_id );
				$this->search_and_replace( $activity_data->old_primary_link, $activity_data->primary_link );
			}
		}
		if ( '' != $old_type ){
			if ( 'group' == $media_context ){
				$parent_link = get_rtmedia_group_link( abs( intval( $result->context_id ) ) );
				$parent_link = trailingslashit( $parent_link );
				$this->search_and_replace( trailingslashit( $parent_link . $old_type . '/' . $media_id ), trailingslashit( $parent_link . RTMEDIA_MEDIA_SLUG . '/' . $last_id ) );
			} else {
				$parent_link = get_rtmedia_user_link( $activity_data->user_id );
				$parent_link = trailingslashit( $parent_link );
				$this->search_and_replace( trailingslashit( $parent_link . $old_type . '/' . $media_id ), trailingslashit( $parent_link . RTMEDIA_MEDIA_SLUG . '/' . $last_id ) );
			}
		}

		return $last_id;
	}

	function importmedia( $id, $prefix ){

		$delete               = false;
		$attached_file        = get_attached_file( $id );
		$attached_file_option = get_post_meta( $id, '_wp_attached_file', true );
		$basename             = wp_basename( $attached_file );
		$file_folder_path     = trailingslashit( str_replace( $basename, '', $attached_file ) );

		$siteurl     = get_option( 'siteurl' );
		$upload_path = trim( get_option( 'upload_path' ) );

		if ( empty( $upload_path ) || 'wp-content/uploads' == $upload_path ){
			$dir = WP_CONTENT_DIR . '/uploads';
		} elseif ( 0 !== strpos( $upload_path, ABSPATH ) ) {
			// $dir is absolute, $upload_path is (maybe) relative to ABSPATH
			$dir = path_join( ABSPATH, $upload_path );
		} else {
			$dir = $upload_path;
		}

		if ( ! $url = get_option( 'upload_url_path' ) ){
			if ( empty( $upload_path ) || ( 'wp-content/uploads' == $upload_path ) || ( $upload_path == $dir ) ){
				$url = WP_CONTENT_URL . '/uploads';
			} else {
				$url = trailingslashit( $siteurl ) . $upload_path;
			}
		}

		// Obey the value of UPLOADS. This happens as long as ms-files rewriting is disabled.
		// We also sometimes obey UPLOADS when rewriting is enabled -- see the next block.
		if ( defined( 'UPLOADS' ) && ! ( is_multisite() && rtmedia_get_site_option( 'ms_files_rewriting' ) ) ){
			$dir = ABSPATH . UPLOADS;
			$url = trailingslashit( $siteurl ) . UPLOADS;
		}

		// If multisite (and if not the main site in a post-MU network)
		if ( is_multisite() && ! ( is_main_site() && defined( 'MULTISITE' ) ) ){

			if ( ! rtmedia_get_site_option( 'ms_files_rewriting' ) ){
				// If ms-files rewriting is disabled (networks created post-3.5), it is fairly straightforward:
				// Append sites/%d if we're not on the main site (for post-MU networks). (The extra directory
				// prevents a four-digit ID from conflicting with a year-based directory for the main site.
				// But if a MU-era network has disabled ms-files rewriting manually, they don't need the extra
				// directory, as they never had wp-content/uploads for the main site.)

				if ( defined( 'MULTISITE' ) ){
					$ms_dir = '/sites/' . get_current_blog_id();
				} else {
					$ms_dir = '/' . get_current_blog_id();
				}

				$dir .= $ms_dir;
				$url .= $ms_dir;
			} elseif ( defined( 'UPLOADS' ) && ! ms_is_switched() ) {
				// Handle the old-form ms-files.php rewriting if the network still has that enabled.
				// When ms-files rewriting is enabled, then we only listen to UPLOADS when:
				//   1) we are not on the main site in a post-MU network,
				//      as wp-content/uploads is used there, and
				//   2) we are not switched, as ms_upload_constants() hardcodes
				//      these constants to reflect the original blog ID.
				//
				// Rather than UPLOADS, we actually use BLOGUPLOADDIR if it is set, as it is absolute.
				// (And it will be set, see ms_upload_constants().) Otherwise, UPLOADS can be used, as
				// as it is relative to ABSPATH. For the final piece: when UPLOADS is used with ms-files
				// rewriting in multisite, the resulting URL is /files. (#WP22702 for background.)

				if ( defined( 'BLOGUPLOADDIR' ) ){
					$dir = untrailingslashit( BLOGUPLOADDIR );
				} else {
					$dir = ABSPATH . UPLOADS;
				}
				$url = trailingslashit( $siteurl ) . 'files';
			}
		}

		$basedir = trailingslashit( $dir );
		$baseurl = trailingslashit( $url );

		$new_file_folder_path = trailingslashit( str_replace( $basedir, $basedir . "rtMedia/$prefix/", $file_folder_path ) );

		$year_month = untrailingslashit( str_replace( $basedir, '', $file_folder_path ) );

		$metadata              = wp_get_attachment_metadata( $id );
		$backup_metadata       = get_post_meta( $id, '_wp_attachment_backup_sizes', true );
		$instagram_thumbs      = get_post_meta( $id, '_instagram_thumbs', true );
		$instagram_full_images = get_post_meta( $id, '_instagram_full_images', true );
		$instagram_metadata    = get_post_meta( $id, '_instagram_metadata', true );
		$encoding_job_id       = get_post_meta( $id, 'bp-media-encoding-job-id', true );
		$ffmpeg_thumbnail_ids  = get_post_meta( $id, 'bp_media_thumbnail_ids', true );
		$ffmpeg_thumbnail      = get_post_meta( $id, 'bp_media_thumbnail', true );
		$ffmpeg_remote_id      = get_post_meta( $id, 'bp_media_ffmpeg_remote_id', true );
		$kaltura_remote_id     = get_post_meta( $id, 'bp_media_kaltura_remote_id', true );

		if ( wp_mkdir_p( $basedir . "rtMedia/$prefix/" . $year_month ) ){
			if ( copy( $attached_file, str_replace( $basedir, $basedir . "rtMedia/$prefix/", $attached_file ) ) ){
				$delete = true;

				if ( isset( $metadata['sizes'] ) ){
					foreach ( $metadata['sizes'] as $size ) {
						if ( ! copy( $file_folder_path . $size['file'], $new_file_folder_path . $size['file'] ) ){
							$delete = false;
						} else {
							$delete_sizes[] = $file_folder_path . $size['file'];
							$this->search_and_replace( trailingslashit( $baseurl . $year_month ) . $size['file'], trailingslashit( $baseurl . "rtMedia/$prefix/" . $year_month ) . $size['file'] );
						}
					}
				}
				if ( $backup_metadata ){
					foreach ( $backup_metadata as $backup_images ) {
						if ( ! copy( $file_folder_path . $backup_images['file'], $new_file_folder_path . $backup_images['file'] ) ){
							$delete = false;
						} else {
							$delete_sizes[] = $file_folder_path . $backup_images['file'];
							$this->search_and_replace( trailingslashit( $baseurl . $year_month ) . $backup_images['file'], trailingslashit( $baseurl . "rtMedia/$prefix/" . $year_month ) . $backup_images['file'] );
						}
					}
				}

				if ( $instagram_thumbs ){
					foreach ( $instagram_thumbs as $key => $insta_thumb ) {
						try {
							if ( ! copy( str_replace( $baseurl, $basedir, $insta_thumb ), str_replace( $baseurl, $basedir . "rtMedia/$prefix/", $insta_thumb ) ) ){
								$delete = false;
							} else {
								$delete_sizes[]              = str_replace( $baseurl, $basedir, $insta_thumb );
								$instagram_thumbs_new[ $key ] = str_replace( $baseurl, $baseurl . "rtMedia/$prefix/", $insta_thumb );
								$this->search_and_replace( trailingslashit( $baseurl . $year_month ) . $insta_thumb, trailingslashit( $baseurl . "rtMedia/$prefix/" . $year_month ) . $insta_thumb );
							}
						} catch ( Exceptio $e ) {
							$delete = false;
						}
					}
				}

				if ( $instagram_full_images ){
					foreach ( $instagram_full_images as $key => $insta_full_image ) {
						if ( ! copy( $insta_full_image, str_replace( $basedir, $basedir . "rtMedia/$prefix/", $insta_full_image ) ) ){
							$delete = false;
						} else {
							$delete_sizes[]                   = $insta_full_image;
							$instagram_full_images_new[ $key ] = str_replace( $basedir, $basedir . "rtMedia/$prefix", $insta_full_image );
							$this->search_and_replace( trailingslashit( $baseurl . $year_month ) . $insta_full_image, trailingslashit( $baseurl . "rtMedia/$prefix/" . $year_month ) . $insta_full_image );
						}
					}
				}

				if ( $instagram_metadata ){
					$instagram_metadata_new = $instagram_metadata;
					foreach ( $instagram_metadata as $wp_size => $insta_metadata ) {
						if ( isset( $insta_metadata['file'] ) ){
							if ( ! copy( $basedir . $insta_metadata['file'], $basedir . "rtMedia/$prefix/" . $insta_metadata['file'] ) ){
								$delete = false;
							} else {
								$delete_sizes[]                              = $basedir . $insta_metadata['file'];
								$instagram_metadata_new[ $wp_size ]['file'] = "rtMedia/$prefix/" . $insta_metadata['file'];
								if ( isset( $insta_metadata['sizes'] ) ){
									foreach ( $insta_metadata['sizes'] as $key => $insta_size ) {
										if ( ! copy( $file_folder_path . $insta_size['file'], $new_file_folder_path . $insta_size['file'] ) ){
											$delete = false;
										} else {
											$delete_sizes[] = $file_folder_path . $insta_size['file'];
											$this->search_and_replace( trailingslashit( $baseurl . $year_month ) . $insta_size['file'], trailingslashit( $baseurl . "rtMedia/$prefix/" . $year_month ) . $insta_size['file'] );
										}
									}
								}
							}
						}
					}
				}

				if ( $delete ){
					if ( file_exists( $attached_file ) ){
						unlink( $attached_file );
					}

					if ( isset( $delete_sizes ) ){
						foreach ( $delete_sizes as $delete_size ) {
							if ( file_exists( $delete_size ) ){
								unlink( $delete_size );
							}
						}
					}
					update_post_meta( $id, '_wp_attached_file', "rtMedia/$prefix/" . $attached_file_option );
					if ( isset( $metadata['file'] ) ){
						$metadata['file'] = "rtMedia/$prefix/" . $metadata['file'];
						wp_update_attachment_metadata( $id, $metadata );
					}
					if ( $instagram_thumbs ){
						update_rtmedia_meta( $id, '_instagram_thumbs', $instagram_thumbs_new );
					}
					if ( $instagram_full_images ){
						update_rtmedia_meta( $id, '_instagram_full_images', $instagram_full_images_new );
					}
					if ( $instagram_metadata ){
						update_rtmedia_meta( $id, '_instagram_metadata', $instagram_metadata_new );
					}
					if ( $encoding_job_id ){
						update_rtmedia_meta( $id, 'rtmedia-encoding-job-id', $encoding_job_id );
					}
					if ( $ffmpeg_thumbnail_ids ){
						update_rtmedia_meta( $id, 'rtmedia-thumbnail-ids', $ffmpeg_thumbnail_ids );
					}
					if ( $ffmpeg_thumbnail ){
						$model = new RTMediaModel();
						$model->update( array( 'cover_art' => $ffmpeg_thumbnail ), array( 'id' => $id ) );
					}
					if ( $ffmpeg_remote_id ){
						update_rtmedia_meta( $id, 'rtmedia-ffmpeg-remote-id', $ffmpeg_remote_id );
					}
					if ( $kaltura_remote_id ){
						update_rtmedia_meta( $id, 'rtmedia-kaltura-remote-id', $kaltura_remote_id );
					}

					$attachment           = array();
					$attachment['ID']   = $id;
					$old_guid             = get_post_field( 'guid', $id );
					$attachment['guid'] = str_replace( $baseurl, $baseurl . "rtMedia/$prefix/", $old_guid );
					/**
					 * For Activity
					 */
					global $last_baseurl, $last_newurl;
					$last_baseurl = $baseurl;
					$last_newurl  = $baseurl . "rtMedia/$prefix/";
					$this->search_and_replace( $old_guid, $attachment['guid'] );
					wp_update_post( $attachment );
				}
			}
		}
	}

	function search_and_replace( $old, $new ){
		global $wpdb;
		if ( function_exists( 'bp_core_get_table_prefix' ) ){
			$bp_prefix = bp_core_get_table_prefix();
		} else {
			$bp_prefix = $wpdb->prefix;
		}
		$sql = $wpdb->prepare( "update {$bp_prefix}bp_activity set action=replace(action,%s,%s) ,content=replace(content,%s,%s), primary_link=replace(primary_link,%s,%s) where id > 0;", $old, $new, $old, $new, $old, $new );
		$wpdb->get_row( $sql );
	}

	function formatSeconds( $secondsLeft ){

		$minuteInSeconds = 60;
		$hourInSeconds   = $minuteInSeconds * 60;
		$dayInSeconds    = $hourInSeconds * 24;

		$days        = floor( $secondsLeft / $dayInSeconds );
		$secondsLeft = $secondsLeft % $dayInSeconds;

		$hours       = floor( $secondsLeft / $hourInSeconds );
		$secondsLeft = $secondsLeft % $hourInSeconds;

		$minutes = floor( $secondsLeft / $minuteInSeconds );

		$seconds = $secondsLeft % $minuteInSeconds;

		$timeComponents = array();

		if ( $days > 0 ){
			$timeComponents[] = $days . __( ' day', 'buddypress-media' ) . ( $days > 1 ? 's' : '' );
		}

		if ( $hours > 0 ){
			$timeComponents[] = $hours . __( ' hour', 'buddypress-media' ) . ( $hours > 1 ? 's' : '' );
		}

		if ( $minutes > 0 ){
			$timeComponents[] = $minutes . __( ' minute', 'buddypress-media' ) . ( $minutes > 1 ? 's' : '' );
		}

		if ( $seconds > 0 ){
			$timeComponents[] = $seconds . __( ' second', 'buddypress-media' ) . ( $seconds > 1 ? 's' : '' );
		}
		if ( count( $timeComponents ) > 0 ){
			$formattedTimeRemaining = implode( ', ', $timeComponents );
			$formattedTimeRemaining = trim( $formattedTimeRemaining );
		} else {
			$formattedTimeRemaining = __( 'No time remaining.', 'buddypress-media' );
		}

		return $formattedTimeRemaining;
	}

	function insert_comment( $media_id, $data, $exclude, $parent_commnet_id = 0 ){
		foreach ( $data as $cmnt ) {
			$comment_id = 0;
			if ( ! key_exists( strval( $cmnt->id ), $exclude ) ){
				$commentdata                    = array(
					'comment_date' => $cmnt->date_recorded,
					'comment_parent' => $parent_commnet_id,
					'user_id' => $cmnt->user_id,
					'comment_content' => $cmnt->content,
					'comment_author_email' => $cmnt->user_email,
					'comment_post_ID' => $media_id,
					'comment_author' => $cmnt->display_name,
					'comment_author_url' => '',
					'comment_author_IP' => '',
				);
				$comment_id                     = wp_insert_comment( $commentdata );
				$exclude[ strval( $cmnt->id ) ] = $comment_id;
			} else {
				$comment_id = $exclude[ strval( $cmnt->id ) ];
			}

			update_post_meta( $media_id, 'rtmedia_imported_activity', $exclude );

			if ( is_array( $cmnt->children ) ){
				$this->insert_comment( $media_id, $cmnt->children, $exclude, $comment_id );
			}
		}
	}

}
