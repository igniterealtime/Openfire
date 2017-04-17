<?php
require_once( 'admin.php' );

require_once( 'includes/functions.bb-plugin.php' );

$plugin_request = 'all';

if ( isset( $_GET['plugin_request'] ) ) {
	$plugin_request = (string) $_GET['plugin_request'];
}

switch ( $plugin_request ) {
	case 'active':
		$_plugin_type = 'normal';
		$_plugin_status = 'active';
		break;
	case 'inactive':
		$_plugin_type = 'normal';
		$_plugin_status = 'inactive';
		break;
	case 'autoload':
		$_plugin_type = 'autoload';
		$_plugin_status = 'all';
		break;
	default:
		$plugin_request = 'all'; // For sanitisation
		$_plugin_type = 'all';
		$_plugin_status = 'all';
		break;
}

$plugin_nav_class = array(
	'all' => '',
	'active' => '',
	'inactive' => '',
	'autoload' => ''
);
$plugin_nav_class[$plugin_request] = ' class="current"';

// Get plugin counts
extract( bb_get_plugin_counts() );

// Get requested plugins
$requested_plugins = bb_get_plugins( 'all', $_plugin_type, $_plugin_status );

// Get currently active 
$active_plugins = (array) bb_get_option( 'active_plugins' );

// Check for missing plugin files and remove them from the active plugins array
$update = false;
foreach ( $active_plugins as $index => $plugin ) {
	if ( !file_exists( bb_get_plugin_path( $plugin ) ) ) {
		$update = true;
		unset( $active_plugins[$index] );
	}
}
if ( $update ) {
	bb_update_option( 'active_plugins', $active_plugins );
}
unset( $update, $index, $plugin );

// Set the action
$action = '';
if( isset( $_GET['action'] ) && !empty( $_GET['action'] ) ) {
	$action = trim( $_GET['action'] );
}

// Set the plugin
$plugin = isset( $_GET['plugin'] ) ? trim( stripslashes( $_GET['plugin'] ) ) : '';

// Deal with user actions
if ( !empty( $action ) ) {
	switch ( $action ) {
		case 'activate':
			// Activation
			bb_check_admin_referer( 'activate-plugin_' . $plugin );

			$result = bb_activate_plugin( $plugin, 'plugins.php?message=error&plugin=' . urlencode( $plugin ) );
			if ( is_wp_error( $result ) )
				bb_die( $result );

			// Overrides the ?message=error one above
			wp_redirect( 'plugins.php?plugin_request=' . $plugin_request . '&message=activate&plugin=' . urlencode( $plugin ) );
			break;

		case 'deactivate':
			// Deactivation
			bb_check_admin_referer( 'deactivate-plugin_' . $plugin );

			// Remove the deactivated plugin
			bb_deactivate_plugins( $plugin );

			// Redirect
			wp_redirect( 'plugins.php?plugin_request=' . $plugin_request . '&message=deactivate&plugin=' . urlencode( $plugin ) );
			break;

		case 'scrape':
			// Scrape php errors from the plugin
			bb_check_admin_referer('scrape-plugin_' . $plugin);

			$valid_path = bb_validate_plugin( $plugin );
			if ( is_wp_error( $valid_path ) )
				bb_die( $valid_path );

			// Pump up the errors and output them to screen
			error_reporting( E_ALL ^ E_NOTICE );
			@ini_set( 'display_errors', true );

			include( $valid_path );
			break;
	}

	// Stop processing
	exit;
}

// Display notices
if ( isset($_GET['message']) ) {
	switch ( $_GET['message'] ) {
		case 'error' :
			bb_admin_notice( __( '<strong>Plugin could not be activated, it produced a Fatal Error</strong>. The error is shown below.' ), 'error' );
			break;
		case 'activate' :
			$plugin_data = bb_get_plugin_data( $plugin );
			bb_admin_notice( sprintf( __( '<strong>"%s" plugin activated</strong>' ), esc_attr( $plugin_data['name'] ) ) );
			break;
		case 'deactivate' :
			$plugin_data = bb_get_plugin_data( $plugin );
			bb_admin_notice( sprintf( __( '<strong>"%s" plugin deactivated</strong>' ), esc_attr( $plugin_data['name'] ) ) );
			break;
	}
}

if ( isset( $bb->safemode ) && $bb->safemode === true ) {
	bb_admin_notice( __( '<strong>"Safe mode" is on, all plugins are disabled even if they are listed as active.</strong>' ), 'error' );
}

$bb_admin_body_class = ' bb-admin-plugins';

bb_get_admin_header();
?>

<div class="wrap">

	<h2><?php _e( 'Manage Plugins' ); ?></h2>
	<?php do_action( 'bb_admin_notices' ); ?>

<?php
if ( bb_verify_nonce( $_GET['_scrape_nonce'], 'scrape-plugin_' . $plugin ) ) {
	$scrape_src = esc_attr(
		bb_nonce_url(
			bb_get_uri(
				'bb-admin/plugins.php',
				array(
					'action' => 'scrape',
					'plugin' => urlencode( $plugin )
				),
				BB_URI_CONTEXT_IFRAME_SRC + BB_URI_CONTEXT_BB_ADMIN
			),
			'scrape-plugin_' . $plugin
		)
	);
?>

	<div class="plugin-error"><iframe src="<?php echo $scrape_src; ?>"></iframe></div>

<?php
}
?>

	<div class="table-filter">
		<a<?php echo $plugin_nav_class['all']; ?> href="<?php bb_uri( 'bb-admin/plugins.php', null, BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN ); ?>"><?php printf( __( 'All <span class="count">(%d)</span>' ), $plugin_count_all ); ?></a> |
		<a<?php echo $plugin_nav_class['active']; ?> href="<?php bb_uri( 'bb-admin/plugins.php', array( 'plugin_request' => 'active' ), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN ); ?>"><?php printf( __( 'Active <span class="count">(%d)</span>' ), $plugin_count_active ); ?></a> |
		<a<?php echo $plugin_nav_class['inactive']; ?> href="<?php bb_uri( 'bb-admin/plugins.php', array( 'plugin_request' => 'inactive' ), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN ); ?>"><?php printf( __( 'Inactive <span class="count">(%d)</span>' ), $plugin_count_inactive ); ?></a> |
		<a<?php echo $plugin_nav_class['autoload']; ?> href="<?php bb_uri( 'bb-admin/plugins.php', array( 'plugin_request' => 'autoload' ), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN ); ?>"><?php printf( __( 'Autoloaded <span class="count">(%d)</span>' ), $plugin_count_autoload ); ?></a>
	</div>

<?php
if ( $requested_plugins ) :
?> 

	<table id="plugins-list" class="widefat">
		<thead>
			<tr>
				<th><?php _e( 'Plugin' ); ?></th>
				<th><?php _e( 'Description' ); ?></th>
			</tr>
		</thead>
		<tfoot>
			<tr>
				<th><?php _e( 'Plugin' ); ?></th>
				<th><?php _e( 'Description' ); ?></th>
			</tr>
		</tfoot>
		<tbody>

<?php
	foreach ( $requested_plugins as $plugin => $plugin_data ) :
		$class =  ' class="inactive"';
		$action = 'activate';
		$action_class = 'edit';
		$action_text = __( 'Activate' );
		if ( $plugin_data['autoload'] ) {
			$class =  ' class="autoload"';
		} elseif ( in_array( $plugin, $active_plugins ) ) {
			$class =  ' class="active"';
			$action = 'deactivate';
			$action_class = 'delete';
			$action_text = __( 'Deactivate' );
		}
		$href = esc_attr(
			bb_nonce_url(
				bb_get_uri(
					'bb-admin/plugins.php',
					array(
						'plugin_request' => $plugin_request,
						'action' => $action,
						'plugin' => urlencode($plugin)
					),
					BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN
				),
				$action . '-plugin_' . $plugin
			)
		);
		$meta = array();
		if ( $plugin_data['version'] ) $meta[] = sprintf( __( 'Version %s' ), $plugin_data['version'] );
		if ( $plugin_data['author_link'] ) $meta[] = sprintf( __( 'By %s' ), $plugin_data['author_link'] );
		if ( $plugin_data['uri'] ) $meta[] = '<a href="' . $plugin_data['uri'] . '">' . esc_html__( 'Visit plugin site' ) . '</a>';
		if ( count( $meta ) ) {
			$meta = '<p class="meta">' . join( ' | ', $meta ) . '</p>';
		} else {
			$meta = '';
		}
?>

			<tr<?php echo $class; ?>>
				<td class="plugin-name">
					<span class="row-title"><?php echo $plugin_data['name']; ?></span>
					<div><span class="row-actions"><?php if ( !$plugin_data['autoload'] ) : ?><a class="<?php echo $action_class; ?>" href="<?php echo $href; ?>"><?php echo $action_text; ?></a><?php else : ?><span class="note"><?php _e( 'Autoloaded' ); ?></span><?php endif; ?></span>&nbsp;</div>
				</td>
				<td class="plugin-description">
					<?php echo $plugin_data['description']; ?>
					<?php echo $meta; ?>
				</td>
			</tr>

<?php
	endforeach;
?>

		</tbody>
	</table>

<?php
else :
?>

	<p class="no-results"><?php _e( 'No plugins found.' ); ?></p>

<?php
endif;
?>

</div>

<?php
bb_get_admin_footer();
?>