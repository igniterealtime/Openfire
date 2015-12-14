<?php
/**
 * BuddyPress Admin Component Functions.
 *
 * @package BuddyPress
 * @subpackage CoreAdministration
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Renders the Component Setup admin panel.
 *
 * @package BuddyPress
 * @since 1.6.0
 * @uses bp_core_admin_component_options()
 */
function bp_core_admin_components_settings() {
?>

	<div class="wrap">

		<h2 class="nav-tab-wrapper"><?php bp_core_admin_tabs( __( 'Components', 'buddypress' ) ); ?></h2>
		<form action="" method="post" id="bp-admin-component-form">

			<?php bp_core_admin_components_options(); ?>

			<p class="submit clear">
				<input class="button-primary" type="submit" name="bp-admin-component-submit" id="bp-admin-component-submit" value="<?php esc_attr_e( 'Save Settings', 'buddypress' ) ?>"/>
			</p>

			<?php wp_nonce_field( 'bp-admin-component-setup' ); ?>

		</form>
	</div>

<?php
}

/**
 * Creates reusable markup for component setup on the Components and Pages dashboard panel.
 *
 * @package BuddyPress
 * @since 1.6.0
 * @todo Use settings API
 */
function bp_core_admin_components_options() {

	// Declare local variables
	$deactivated_components = array();

	/**
	 * Filters the array of available components.
	 *
	 * @since 1.5.0
	 *
	 * @param mixed $value Active components.
	 */
	$active_components      = apply_filters( 'bp_active_components', bp_get_option( 'bp-active-components' ) );

	// The default components (if none are previously selected)
	$default_components = array(
		'xprofile' => array(
			'title'       => __( 'Extended Profiles', 'buddypress' ),
			'description' => __( 'Customize your community with fully editable profile fields that allow your users to describe themselves.', 'buddypress' )
		),
		'settings' => array(
			'title'       => __( 'Account Settings', 'buddypress' ),
			'description' => __( 'Allow your users to modify their account and notification settings directly from within their profiles.', 'buddypress' )
		),
		'notifications' => array(
			'title'       => __( 'Notifications', 'buddypress' ),
			'description' => __( 'Notify members of relevant activity with a toolbar bubble and/or via email, and allow them to customize their notification settings.', 'buddypress' )
		),
	);

	$optional_components = bp_core_admin_get_components( 'optional' );
	$required_components = bp_core_admin_get_components( 'required' );
	$retired_components  = bp_core_admin_get_components( 'retired'  );

	// Don't show Forums component in optional components if it's disabled
	if ( ! bp_is_active( 'forums' ) ) {
		unset( $optional_components['forums'] );
	}

	// Merge optional and required together
	$all_components = $optional_components + $required_components;

	// If this is an upgrade from before BuddyPress 1.5, we'll have to convert
	// deactivated components into activated ones.
	if ( empty( $active_components ) ) {
		$deactivated_components = bp_get_option( 'bp-deactivated-components' );
		if ( !empty( $deactivated_components ) ) {

			// Trim off namespace and filename
			$trimmed = array();
			foreach ( array_keys( (array) $deactivated_components ) as $component ) {
				$trimmed[] = str_replace( '.php', '', str_replace( 'bp-', '', $component ) );
			}

			// Loop through the optional components to create an active component array
			foreach ( array_keys( (array) $optional_components ) as $ocomponent ) {
				if ( !in_array( $ocomponent, $trimmed ) ) {
					$active_components[$ocomponent] = 1;
				}
			}
		}
	}

	// On new install, set active components to default
	if ( empty( $active_components ) ) {
		$active_components = $default_components;
	}

	// Core component is always active
	$active_components['core'] = $all_components['core'];
	$inactive_components       = array_diff( array_keys( $all_components ) , array_keys( $active_components ) );

	/** Display ***************************************************************/

	// Get the total count of all plugins
	$all_count = count( $all_components );
	$page      = bp_core_do_network_admin()  ? 'settings.php' : 'options-general.php';
	$action    = !empty( $_GET['action'] ) ? $_GET['action'] : 'all';

	switch( $action ) {
		case 'all' :
			$current_components = $all_components;
			break;
		case 'active' :
			foreach ( array_keys( $active_components ) as $component ) {
				$current_components[$component] = $all_components[$component];
			}
			break;
		case 'inactive' :
			foreach ( $inactive_components as $component ) {
				$current_components[$component] = $all_components[$component];
			}
			break;
		case 'mustuse' :
			$current_components = $required_components;
			break;
		case 'retired' :
			$current_components = $retired_components;
			break;
	} ?>

	<ul class="subsubsub">
		<li><a href="<?php echo esc_url( add_query_arg( array( 'page' => 'bp-components', 'action' => 'all'      ), bp_get_admin_url( $page ) ) ); ?>" <?php if ( $action === 'all'      ) : ?>class="current"<?php endif; ?>><?php printf( _nx( 'All <span class="count">(%s)</span>',      'All <span class="count">(%s)</span>',      $all_count,         'plugins', 'buddypress' ), number_format_i18n( $all_count                    ) ); ?></a> | </li>
		<li><a href="<?php echo esc_url( add_query_arg( array( 'page' => 'bp-components', 'action' => 'active'   ), bp_get_admin_url( $page ) ) ); ?>" <?php if ( $action === 'active'   ) : ?>class="current"<?php endif; ?>><?php printf( _n(  'Active <span class="count">(%s)</span>',   'Active <span class="count">(%s)</span>',   count( $active_components   ), 'buddypress' ), number_format_i18n( count( $active_components   ) ) ); ?></a> | </li>
		<li><a href="<?php echo esc_url( add_query_arg( array( 'page' => 'bp-components', 'action' => 'inactive' ), bp_get_admin_url( $page ) ) ); ?>" <?php if ( $action === 'inactive' ) : ?>class="current"<?php endif; ?>><?php printf( _n(  'Inactive <span class="count">(%s)</span>', 'Inactive <span class="count">(%s)</span>', count( $inactive_components ), 'buddypress' ), number_format_i18n( count( $inactive_components ) ) ); ?></a> | </li>
		<li><a href="<?php echo esc_url( add_query_arg( array( 'page' => 'bp-components', 'action' => 'mustuse'  ), bp_get_admin_url( $page ) ) ); ?>" <?php if ( $action === 'mustuse'  ) : ?>class="current"<?php endif; ?>><?php printf( _n(  'Must-Use <span class="count">(%s)</span>', 'Must-Use <span class="count">(%s)</span>', count( $required_components ), 'buddypress' ), number_format_i18n( count( $required_components ) ) ); ?></a> | </li>
		<li><a href="<?php echo esc_url( add_query_arg( array( 'page' => 'bp-components', 'action' => 'retired'  ), bp_get_admin_url( $page ) ) ); ?>" <?php if ( $action === 'retired'  ) : ?>class="current"<?php endif; ?>><?php printf( _n(  'Retired <span class="count">(%s)</span>',  'Retired <span class="count">(%s)</span>',  count( $retired_components ),  'buddypress' ), number_format_i18n( count( $retired_components  ) ) ); ?></a></li>
	</ul>

	<table class="widefat fixed plugins" cellspacing="0">
		<thead>
			<tr>
				<th scope="col" id="cb" class="manage-column column-cb check-column">&nbsp;</th>
				<th scope="col" id="name" class="manage-column column-name" style="width: 190px;"><?php _e( 'Component', 'buddypress' ); ?></th>
				<th scope="col" id="description" class="manage-column column-description"><?php _e( 'Description', 'buddypress' ); ?></th>
			</tr>
		</thead>

		<tfoot>
			<tr>
				<th scope="col" class="manage-column column-cb check-column">&nbsp;</th>
				<th scope="col" class="manage-column column-name" style="width: 190px;"><?php _e( 'Component', 'buddypress' ); ?></th>
				<th scope="col" class="manage-column column-description"><?php _e( 'Description', 'buddypress' ); ?></th>
			</tr>
		</tfoot>

		<tbody id="the-list">

			<?php if ( !empty( $current_components ) ) : ?>

				<?php foreach ( $current_components as $name => $labels ) : ?>

					<?php if ( !in_array( $name, array( 'core', 'members' ) ) ) :
						$class = isset( $active_components[esc_attr( $name )] ) ? 'active' : 'inactive';
					else :
						$class = 'active';
					endif; ?>

					<tr id="<?php echo esc_attr( $name ); ?>" class="<?php echo esc_attr( $name ) . ' ' . esc_attr( $class ); ?>">
						<th scope="row">

							<?php if ( !in_array( $name, array( 'core', 'members' ) ) ) : ?>

								<input type="checkbox" id="bp_components[<?php echo esc_attr( $name ); ?>]" name="bp_components[<?php echo esc_attr( $name ); ?>]" value="1"<?php checked( isset( $active_components[esc_attr( $name )] ) ); ?> />

							<?php endif; ?>

						</th>
						<td class="plugin-title" style="width: 190px;">
							<span></span>
							<label for="bp_components[<?php echo esc_attr( $name ); ?>]">
								<strong><?php echo esc_html( $labels['title'] ); ?></strong>
							</label>

							<div class="row-actions-visible">

							</div>
						</td>

						<td class="column-description desc">
							<div class="plugin-description">
								<p><?php echo $labels['description']; ?></p>
							</div>
							<div class="active second plugin-version-author-uri">

							</div>
						</td>
					</tr>

				<?php endforeach ?>

			<?php else : ?>

				<tr class="no-items">
					<td class="colspanchange" colspan="3"><?php _e( 'No components found.', 'buddypress' ); ?></td>
				</tr>

			<?php endif; ?>

		</tbody>
	</table>

	<input type="hidden" name="bp_components[members]" value="1" />

	<?php
}

/**
 * Handle saving the Component settings.
 *
 * @since 1.6.0
 * @todo Use settings API when it supports saving network settings
 */
function bp_core_admin_components_settings_handler() {

	// Bail if not saving settings
	if ( ! isset( $_POST['bp-admin-component-submit'] ) )
		return;

	// Bail if nonce fails
	if ( ! check_admin_referer( 'bp-admin-component-setup' ) )
		return;

	// Settings form submitted, now save the settings. First, set active components
	if ( isset( $_POST['bp_components'] ) ) {

		// Load up BuddyPress
		$bp = buddypress();

		// Save settings and upgrade schema
		require_once( $bp->plugin_dir . '/bp-core/admin/bp-core-admin-schema.php' );

		$submitted = stripslashes_deep( $_POST['bp_components'] );
		$bp->active_components = bp_core_admin_get_active_components_from_submitted_settings( $submitted );

		bp_core_install( $bp->active_components );
		bp_core_add_page_mappings( $bp->active_components );
		bp_update_option( 'bp-active-components', $bp->active_components );
	}

	// Where are we redirecting to?
	$base_url = bp_get_admin_url( add_query_arg( array( 'page' => 'bp-components', 'updated' => 'true' ), 'admin.php' ) );

	// Redirect
	wp_redirect( $base_url );
	die();
}
add_action( 'bp_admin_init', 'bp_core_admin_components_settings_handler' );

/**
 * Calculates the components that should be active after save, based on submitted settings.
 *
 * The way that active components must be set after saving your settings must
 * be calculated differently depending on which of the Components subtabs you
 * are coming from:
 * - When coming from All or Active, the submitted checkboxes accurately
 *   reflect the desired active components, so we simply pass them through
 * - When coming from Inactive, components can only be activated - already
 *   active components will not be passed in the $_POST global. Thus, we must
 *   parse the newly activated components with the already active components
 *   saved in the $bp global
 * - When activating a Retired component, the situation is similar to Inactive.
 * - When deactivating a Retired component, no value is passed in the $_POST
 *   global (because the component settings are checkboxes). So, in order to
 *   determine whether a retired component is being deactivated, we retrieve a
 *   list of retired components, and check each one to ensure that its checkbox
 *   is not present, before merging the submitted components with the active
 *   ones.
 *
 * @since 1.7.0
 *
 * @param array $submitted This is the array of component settings coming from the POST
 *                         global. You should stripslashes_deep() before passing to this function.
 *
 * @return array The calculated list of component settings
 */
function bp_core_admin_get_active_components_from_submitted_settings( $submitted ) {
	$current_action = 'all';

	if ( isset( $_GET['action'] ) && in_array( $_GET['action'], array( 'active', 'inactive', 'retired' ) ) ) {
		$current_action = $_GET['action'];
	}

	$current_components = buddypress()->active_components;

	switch ( $current_action ) {
		case 'retired' :
			$retired_components = bp_core_admin_get_components( 'retired' );
			foreach ( array_keys( $retired_components ) as $retired_component ) {
				if ( ! isset( $submitted[ $retired_component ] ) ) {
					unset( $current_components[ $retired_component ] );
				}
			}
			// fall through

		case 'inactive' :
			$components = array_merge( $submitted, $current_components );
			break;

		case 'all' :
		case 'active' :
		default :
			$components = $submitted;
			break;
	}

	return $components;
}

/**
 * Return a list of component information, optionally filtered by type.
 *
 * We use this information both to build the markup for the admin screens, as
 * well as to do some processing on settings data submitted from those screens.
 *
 * @since 1.7.0
 *
 * @param string $type 'all', 'optional', 'retired', 'required'.
 *
 * @return array An array of requested component data
 */
function bp_core_admin_get_components( $type = 'all' ) {

	// Required components
	$required_components = array(
		'core' => array(
			'title'       => __( 'BuddyPress Core', 'buddypress' ),
			'description' => __( 'It&#8216;s what makes <del>time travel</del> BuddyPress possible!', 'buddypress' )
		),
		'members' => array(
			'title'       => __( 'Community Members', 'buddypress' ),
			'description' => __( 'Everything in a BuddyPress community revolves around its members.', 'buddypress' )
		),
	);

	// Retired components
	$retired_components = array(
		'forums' => array(
			'title'       => __( 'Group Forums', 'buddypress' ),
			'description' => sprintf( __( 'BuddyPress Forums are retired. Use %s.', 'buddypress' ), '<a href="https://bbpress.org/">bbPress</a>' )
		),
	);

	// Optional core components
	$optional_components = array(
		'xprofile' => array(
			'title'       => __( 'Extended Profiles', 'buddypress' ),
			'description' => __( 'Customize your community with fully editable profile fields that allow your users to describe themselves.', 'buddypress' )
		),
		'settings' => array(
			'title'       => __( 'Account Settings', 'buddypress' ),
			'description' => __( 'Allow your users to modify their account and notification settings directly from within their profiles.', 'buddypress' )
		),
		'friends'  => array(
			'title'       => __( 'Friend Connections', 'buddypress' ),
			'description' => __( 'Let your users make connections so they can track the activity of others and focus on the people they care about the most.', 'buddypress' )
		),
		'messages' => array(
			'title'       => __( 'Private Messaging', 'buddypress' ),
			'description' => __( 'Allow your users to talk to each other directly and in private. Not just limited to one-on-one discussions, messages can be sent between any number of members.', 'buddypress' )
		),
		'activity' => array(
			'title'       => __( 'Activity Streams', 'buddypress' ),
			'description' => __( 'Global, personal, and group activity streams with threaded commenting, direct posting, favoriting, and @mentions, all with full RSS feed and email notification support.', 'buddypress' )
		),
		'notifications' => array(
			'title'       => __( 'Notifications', 'buddypress' ),
			'description' => __( 'Notify members of relevant activity with a toolbar bubble and/or via email, and allow them to customize their notification settings.', 'buddypress' )
		),
		'groups'   => array(
			'title'       => __( 'User Groups', 'buddypress' ),
			'description' => __( 'Groups allow your users to organize themselves into specific public, private or hidden sections with separate activity streams and member listings.', 'buddypress' )
		),
		'forums'   => array(
			'title'       => __( 'Group Forums (Legacy)', 'buddypress' ),
			'description' => __( 'Group forums allow for focused, bulletin-board style conversations.', 'buddypress' )
		),
		'blogs'    => array(
			'title'       => __( 'Site Tracking', 'buddypress' ),
			'description' => __( 'Record activity for new posts and comments from your site.', 'buddypress' )
		)
	);


	// Add blogs tracking if multisite
	if ( is_multisite() ) {
		$optional_components['blogs']['description'] = __( 'Record activity for new sites, posts, and comments across your network.', 'buddypress' );
	}

	switch ( $type ) {
		case 'required' :
			$components = $required_components;
			break;
		case 'optional' :
			$components = $optional_components;
			break;
		case 'retired' :
			$components = $retired_components;
			break;
		case 'all' :
		default :
			$components = array_merge( $required_components, $optional_components, $retired_components );
			break;

	}

	/**
	 * Filters the list of component information.
	 *
	 * @since 2.0.0
	 *
	 * @param array  $components Array of component information.
	 * @param string $type       Type of component list requested.
	 *                           Possible values include 'all', 'optional',
	 *							 'retired', 'required'.
	 */
	return apply_filters( 'bp_core_admin_get_components', $components, $type );
}
