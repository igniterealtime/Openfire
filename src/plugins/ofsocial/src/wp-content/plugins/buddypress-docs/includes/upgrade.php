<?php

/**
 * Upgrade functions
 *
 * @since 1.2
 */

function bp_docs_upgrade_check() {
	$upgrades = array();

	if ( ! bp_get_option( '_bp_docs_done_upgrade_1_2' ) ) {
		// If this is a new install, don't bother
		global $wpdb;
		$count = $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM $wpdb->posts WHERE post_type = %s", bp_docs_get_post_type_name() ) );

		if ( $count ) {
			$upgrades[] = '1.2';
		}
	}

	return $upgrades;
}

function bp_docs_upgrade_init() {
	if ( ! is_admin() || ! current_user_can( 'bp_moderate' ) ) {
		return;
	}

	bp_docs_upgrade_menu();
	add_action( 'admin_notices', 'bp_docs_upgrade_notice' );
}
//add_action( 'admin_menu', 'bp_docs_upgrade_init' );

function bp_docs_upgrade_notice() {
	global $pagenow;

	if ( ! empty( $_GET['bp_docs_upgraded'] ) ) {
		flush_rewrite_rules(); // just in case. Hack
		echo '<div class="updated message"><p>' . __( 'Upgrade complete!', 'bp-docs' ) . '</p></div>';
	}

	$upgrades = bp_docs_upgrade_check();

	if ( empty( $upgrades ) ) {
		return;
	}

	if (
		'edit.php' == $pagenow &&
		isset( $_GET['post_type'] ) &&
		bp_docs_get_post_type_name() == $_GET['post_type'] &&
		isset( $_GET['page'] ) &&
		'bp-docs-upgrade' == $_GET['page']
	   ) {
		return;
	}

	?>
	<div class="message error">
		<p><?php _e( 'Thanks for updating BuddyPress Docs. We need to run a few quick operations before your new Docs is ready to use.', 'bp-docs' ) ?></p>
		<p><strong><a href="<?php echo admin_url( 'edit.php?post_type=bp_doc&page=bp-docs-upgrade' ) ?>"><?php _e( 'Click here to start the upgrade.', 'bp-docs' ) ?></a></strong></p>
	</div>
	<?php
}

function bp_docs_upgrade_menu() {
	add_submenu_page(
		'edit.php?post_type=' . bp_docs_get_post_type_name(),
		__( 'BuddyPress Docs Upgrade', 'bp-docs' ),
		__( 'Upgrade', 'bp-docs' ),
		'bp_moderate',
		'bp-docs-upgrade',
		'bp_docs_upgrade_render'
	);
}

function bp_docs_upgrade_render() {
	$url_base = admin_url( 'edit.php?post_type=' . bp_docs_get_post_type_name() . '&page=bp-docs-upgrade' );

	if ( isset( $_GET['do_upgrade'] ) && 1 == $_GET['do_upgrade'] ) {
		$status = 'upgrade';
	} else if ( isset( $_GET['success'] ) && 1 == $_GET['success'] ) {
		$status = 'complete';
	} else {
		$status = 'none';
	}

	?>
	<div class="wrap">
		<h2><?php _e( 'BuddyPress Docs Upgrade', 'bp-docs' ) ?></h2>

		<?php if ( 'none' == $status ) : ?>
			<?php
				$url = add_query_arg( 'do_upgrade', '1', $url_base );
				$url = wp_nonce_url( $url, 'bp-docs-upgrade' );
			?>

			<p><?php _e( 'Thanks for updating BuddyPress Docs. We need to run a few quick operations before your new Docs is ready to use.', 'bp-docs' ) ?></p>

			<a class="button primary" href="<?php echo $url ?>"><?php _e( 'Start the upgrade', 'bp-docs' ) ?></a>

		<?php elseif ( 'upgrade' == $status ) : ?>

			<?php check_admin_referer( 'bp-docs-upgrade' ) ?>

			<p><?php _e( 'Migrating...', 'bp-docs' ) ?></p>

			<?php
				$upgrade_status = bp_get_option( 'bp_docs_upgrade' );
				$message        = isset( $upgrade_status['message'] ) ? $upgrade_status['message'] : '';

				if ( isset( $upgrade_status['refresh_url'] ) ) {
					$refresh_url = $upgrade_status['refresh_url'];
				} else {
					$refresh_url = add_query_arg( array(
						'do_upgrade' => '1',
						'_wpnonce'   => wp_create_nonce( 'bp-docs-upgrade' ),
					), $url_base );
				}
			?>

			<p><?php echo esc_html( $message ) ?></p>

			<?php bp_docs_do_upgrade() ?>

			<script type='text/javascript'>
				<!--
				function nextpage() {
					location.href = "<?php echo $refresh_url ?>";
				}
				setTimeout( "nextpage()", 2000 );
				//-->
			</script>

		<?php elseif ( 'complete' == $status ) : ?>

			<p><?php printf( __( 'Migration complete! <a href="%s">Dashboard</a>', 'bp-docs' ), admin_url() ) ?></p>

		<?php endif ?>
	</div>
	<?php
}

/**
 * Upgrade class
 */
function bp_docs_do_upgrade() {
	$upgrade_status = bp_get_option( 'bp_docs_upgrade' );
	if ( '' == $upgrade_status ) {
		$upgrade_status = array(
			'upgrades'    => array(),
			'refresh_url' => '',
			'message'     => '',
		);
		$upgrades = bp_docs_upgrade_check();

		foreach ( $upgrades as $upgrade ) {
			$func = 'bp_docs_upgrade_' . str_replace( '.', '_', $upgrade );
			if ( function_exists( $func ) ) {
				$upgrade_status['upgrades'][ $func ] = array(
					'last' => 0,
					'done' => 0,
					'total' => 0,
				);
			}
		}
	}

	// Grab the next available upgrade
	foreach ( $upgrade_status['upgrades'] as $ufunc => $udata ) {
		$the_ufunc = $ufunc;
		$the_udata = $udata;
		break;
	}

	if ( isset( $ufunc ) && isset( $udata ) ) {
		if ( intval( $udata['done'] ) <= intval( $udata['total'] ) ) {
			$new_udata = call_user_func_array( $ufunc, array( $udata ) );
			$upgrade_status['upgrades'][ $ufunc ] = $new_udata;

			if ( isset( $new_udata['message'] ) ) {
				$upgrade_status['message'] = $new_udata['message'];
			}

			if ( isset( $new_udata['refresh_url'] ) ) {
				$upgrade_status['refresh_url'] = $new_udata['refresh_url'];
			}
		} else {
			unset( $upgrade_status['upgrades'][ $ufunc ] );
		}

	} else {
		$upgrade_status['refresh_url'] = add_query_arg( array(
			'bp_docs_upgraded' => 1,
		), admin_url() );
	}

	bp_update_option( 'bp_docs_upgrade', $upgrade_status );
}

//////////////////////////////////////////////////
//
//  1.2
//  - 'read' settings mapped onto taxonomy term
//  - associated group tax terms change
//
//////////////////////////////////////////////////
function bp_docs_upgrade_1_2( $udata = array() ) {
	global $wpdb;

	$url_base = admin_url( add_query_arg( array(
		'post_type' => bp_docs_get_post_type_name(),
		'page' => 'bp-docs-upgrade',
	), 'edit.php' ) );

	if ( empty( $udata['total'] ) ) {
		$udata['total'] = $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM $wpdb->posts WHERE post_type = %s", bp_docs_get_post_type_name() ) );
	}

	if ( ! isset( $udata['done'] ) ) {
		$udata['done'] = 0;
	}

	if ( empty( $udata['group_terms_migrated'] ) ) {
		$tn = bp_docs_get_associated_item_tax_name();

		// Get the group parent term
		$group_parent_term = term_exists( 'group', $tn );

		// Get all the group terms
		if ( $group_parent_term ) {

			// Delete the cached children terms, for good measure
			delete_option( $tn . '_children' );

			$group_terms = get_terms( $tn, array(
				'parent' => intval( $group_parent_term['term_id'] ),
			) );

			foreach ( $group_terms as $group_term ) {
				// Concatenate new term slugs
				$new_desc = sprintf( __( 'Docs associated with the group %s', 'bp-docs' ), $group_term->description );
				$new_slug = 'bp_docs_associated_group_' . $group_term->name;
				$new_name = $group_term->description;

				wp_update_term( $group_term->term_id, $tn, array(
					'description' => $new_desc,
					'slug'        => $new_slug,
					'name'        => $new_name,
					'parent'      => 0,
				) );
			}
		}

		// Store that we're done
		$udata['group_terms_migrated'] = 1;
		$udata['message'] = __( 'Group terms migrated. Now migrating Doc access terms....', 'bp-docs' );
		$udata['refresh_url'] = add_query_arg( array(
			'do_upgrade' => '1',
			'_wpnonce'   => wp_create_nonce( 'bp-docs-upgrade' ),
		), $url_base );
		$udata['total'] = 0;

	} else if ( intval( $udata['done'] ) < intval( $udata['total'] ) ) {
		$counter = 0;
		while ( $counter < 5 ) {
			$next_doc_id = $wpdb->get_var( $wpdb->prepare( "SELECT ID FROM $wpdb->posts WHERE post_type = %s AND ID > %d LIMIT 1", bp_docs_get_post_type_name(), intval( $udata['last'] ) ) );
			if ( ! $next_doc_id ) {
				$udata['done'] = $udata['total'];
				$all_done = true;
				break;
			}

			// Set the 'read' setting to a taxonomy
			$doc_settings = get_post_meta( $next_doc_id, 'bp_docs_settings', true );

			if ( isset( $doc_settings['read'] ) ) {
				$read_setting = $doc_settings['read'];
			} else {
				$group = groups_get_group( 'group_id=' . bp_docs_get_associated_group_id( $next_doc_id ) );
				if ( ! empty( $group->status ) && 'public' != $group->status ) {
					$read_setting = 'group-members';

					// Sanitize settings as well
					foreach ( $doc_settings as $doc_settings_key => $doc_settings_value ) {
						if ( in_array( $doc_settings_value, array( 'anyone', 'loggedin' ) ) ) {
							$doc_settings[ $doc_settings_key ] = 'group-members';
						}
					}
					$doc_settings['read'] = 'group-members';
					update_post_meta( $next_doc_id, 'bp_docs_settings', $doc_settings );
				} else {
					$read_setting = 'anyone';
				}
			}
			bp_docs_update_doc_access( $next_doc_id, $read_setting );

			// Count the total number of edits
			$count = $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM $wpdb->posts WHERE post_type = 'revision' AND post_status = 'inherit' AND post_parent = %d", $next_doc_id ) );
			update_post_meta( $next_doc_id, 'bp_docs_revision_count', $count + 1 );

			$counter++;
			$udata['done']++;
			$udata['last'] = $next_doc_id;
			$udata['message'] = sprintf( __( 'Migrated %s of %s Docs. Migrating....', 'bp-docs' ), $udata['done'], $udata['total'] );
			$udata['refresh_url'] = add_query_arg( array(
				'do_upgrade' => '1',
				'_wpnonce'   => wp_create_nonce( 'bp-docs-upgrade' ),
			), $url_base );
		}
	} else {
		$all_done = true;
		$udata['refresh_url'] = add_query_arg( array(
			'bp_docs_upgraded' => 1,
		), admin_url() );
	}

	if ( isset( $all_done ) ) {
		bp_update_option( '_bp_docs_done_upgrade_1_2', 1 );
	}

	return $udata;
}

