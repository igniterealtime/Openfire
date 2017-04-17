<?php
/**
 * BuddyPress Widgets.
 *
 * @package BuddyPress
 * @subpackage Friends
 * @since 1.9.0
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Register the friends widget.
 *
 * @since 1.9.0
 */
function bp_friends_register_widgets() {
	if ( ! bp_is_active( 'friends' ) ) {
		return;
	}

	// The Friends widget works only when looking an a displayed user,
	// and the concept of "displayed user" doesn't exist on non-root blogs,
	// so we don't register the widget there.
	if ( ! bp_is_root_blog() ) {
		return;
	}

	add_action( 'widgets_init', create_function( '', 'return register_widget("BP_Core_Friends_Widget");' ) );
}
add_action( 'bp_register_widgets', 'bp_friends_register_widgets' );

/*** MEMBER FRIENDS WIDGET *****************/

/**
 * The User Friends widget class.
 *
 * @since 1.9.0
 */
class BP_Core_Friends_Widget extends WP_Widget {

	/**
	 * Class constructor.
	 */
	function __construct() {
		$widget_ops = array(
			'description' => __( 'A dynamic list of recently active, popular, and newest Friends of the displayed member.  Widget is only shown when viewing a member profile.', 'buddypress' ),
			'classname' => 'widget_bp_core_friends_widget buddypress widget',
		);
		parent::__construct( false, $name = _x( '(BuddyPress) Friends', 'widget name', 'buddypress' ), $widget_ops );

	}

	/**
	 * Display the widget.
	 *
	 * @param array $args Widget arguments.
	 * @param array $instance The widget settings, as saved by the user.
	 */
	function widget( $args, $instance ) {
		extract( $args );

		if ( ! bp_displayed_user_id() ) {
			return;
		}

		$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : '.min';
		wp_enqueue_script( 'bp_core_widget_friends-js', buddypress()->plugin_url . "bp-friends/js/widget-friends{$min}.js", array( 'jquery' ), bp_get_version() );

		$user_id = bp_displayed_user_id();
		$link = trailingslashit( bp_displayed_user_domain() . bp_get_friends_slug() );
		$instance['title'] = sprintf( __( "%s's Friends", 'buddypress' ), bp_get_displayed_user_fullname() );

		if ( empty( $instance['friend_default'] ) ) {
			$instance['friend_default'] = 'active';
		}

		/**
		 * Filters the Friends widget title.
		 *
		 * @since 1.8.0
		 * @since 2.3.0 Added 'instance' and 'id_base' to arguments passed to filter.
		 *
		 * @param string $title    The widget title.
		 * @param array  $instance The settings for the particular instance of the widget.
		 * @param string $id_base  Root ID for all widgets of this type.
		 */
		$title = apply_filters( 'widget_title', $instance['title'], $instance, $this->id_base );

		echo $before_widget;

		$title = $instance['link_title'] ? '<a href="' . esc_url( $link ) . '">' . esc_html( $title ) . '</a>' : esc_html( $title );

		echo $before_title . $title . $after_title;

		$members_args = array(
			'user_id'         => absint( $user_id ),
			'type'            => sanitize_text_field( $instance['friend_default'] ),
			'max'             => absint( $instance['max_friends'] ),
			'populate_extras' => 1,
		);

		?>

		<?php if ( bp_has_members( $members_args ) ) : ?>
			<div class="item-options" id="friends-list-options">
				<a href="<?php bp_members_directory_permalink(); ?>" id="newest-friends" <?php if ( $instance['friend_default'] == 'newest' ) : ?>class="selected"<?php endif; ?>><?php _e( 'Newest', 'buddypress' ); ?></a>
				| <a href="<?php bp_members_directory_permalink(); ?>" id="recently-active-friends" <?php if ( $instance['friend_default'] == 'active' ) : ?>class="selected"<?php endif; ?>><?php _e( 'Active', 'buddypress' ); ?></a>
				| <a href="<?php bp_members_directory_permalink(); ?>" id="popular-friends" <?php if ( $instance['friend_default'] == 'popular' ) : ?>class="selected"<?php endif; ?>><?php _e( 'Popular', 'buddypress' ); ?></a>
			</div>

			<ul id="friends-list" class="item-list">
				<?php while ( bp_members() ) : bp_the_member(); ?>
					<li class="vcard">
						<div class="item-avatar">
							<a href="<?php bp_member_permalink(); ?>" title="<?php bp_member_name(); ?>"><?php bp_member_avatar(); ?></a>
						</div>

						<div class="item">
							<div class="item-title fn"><a href="<?php bp_member_permalink(); ?>" title="<?php bp_member_name(); ?>"><?php bp_member_name(); ?></a></div>
							<div class="item-meta">
								<span class="activity">
								<?php
									if ( 'newest' == $instance['friend_default'] )
										bp_member_registered();
									if ( 'active' == $instance['friend_default'] )
										bp_member_last_active();
									if ( 'popular' == $instance['friend_default'] )
										bp_member_total_friend_count();
								?>
								</span>
							</div>
						</div>
					</li>

				<?php endwhile; ?>
			</ul>
			<?php wp_nonce_field( 'bp_core_widget_friends', '_wpnonce-friends' ); ?>
			<input type="hidden" name="friends_widget_max" id="friends_widget_max" value="<?php echo absint( $instance['max_friends'] ); ?>" />

		<?php else: ?>

			<div class="widget-error">
				<?php _e( 'Sorry, no members were found.', 'buddypress' ); ?>
			</div>

		<?php endif; ?>

		<?php echo $after_widget; ?>
	<?php
	}

	/**
	 * Process a widget save.
	 *
	 * @param array $new_instance The parameters saved by the user.
	 * @param array $old_instance The parameters as previously saved to the database.
	 * @return array $instance The processed settings to save.
	 */
	function update( $new_instance, $old_instance ) {
		$instance = $old_instance;

		$instance['max_friends']    = absint( $new_instance['max_friends'] );
		$instance['friend_default'] = sanitize_text_field( $new_instance['friend_default'] );
		$instance['link_title']	    = (bool) $new_instance['link_title'];

		return $instance;
	}

	/**
	 * Render the widget edit form.
	 *
	 * @param array $instance The saved widget settings.
	 * @return void
	 */
	function form( $instance ) {
		$defaults = array(
			'max_friends' 	 => 5,
			'friend_default' => 'active',
			'link_title' 	 => false
		);
		$instance = wp_parse_args( (array) $instance, $defaults );

		$max_friends 	= $instance['max_friends'];
		$friend_default = $instance['friend_default'];
		$link_title	= (bool) $instance['link_title'];
		?>

		<p><label for="<?php echo $this->get_field_id( 'link_title' ); ?>"><input type="checkbox" name="<?php echo $this->get_field_name('link_title'); ?>" id="<?php echo $this->get_field_id( 'link_title' ); ?>" value="1" <?php checked( $link_title ); ?> /> <?php _e( 'Link widget title to Members directory', 'buddypress' ); ?></label></p>

		<p><label for="<?php echo $this->get_field_id( 'max_friends' ); ?>"><?php _e( 'Max friends to show:', 'buddypress' ); ?> <input class="widefat" id="<?php echo $this->get_field_id( 'max_friends' ); ?>" name="<?php echo $this->get_field_name( 'max_friends' ); ?>" type="text" value="<?php echo absint( $max_friends ); ?>" style="width: 30%" /></label></p>

		<p>
			<label for="<?php echo $this->get_field_id( 'friend_default' ) ?>"><?php _e( 'Default friends to show:', 'buddypress' ); ?></label>
			<select name="<?php echo $this->get_field_name( 'friend_default' ); ?>" id="<?php echo $this->get_field_id( 'friend_default' ); ?>">
				<option value="newest" <?php selected( $friend_default, 'newest' ); ?>><?php _e( 'Newest', 'buddypress' ); ?></option>
				<option value="active" <?php selected( $friend_default, 'active' );?>><?php _e( 'Active', 'buddypress' ); ?></option>
				<option value="popular"  <?php selected( $friend_default, 'popular' ); ?>><?php _e( 'Popular', 'buddypress' ); ?></option>
			</select>
		</p>

	<?php
	}
}

/** Widget AJAX ***************************************************************/

/**
 * Process AJAX pagination or filtering for the Friends widget.
 *
 * @since 1.9.0
 */
function bp_core_ajax_widget_friends() {

	check_ajax_referer( 'bp_core_widget_friends' );

	switch ( $_POST['filter'] ) {
		case 'newest-friends':
			$type = 'newest';
			break;

		case 'recently-active-friends':
			$type = 'active';
			break;

		case 'popular-friends':
			$type = 'popular';
			break;
	}

	$members_args = array(
		'user_id'         => bp_displayed_user_id(),
		'type'            => $type,
		'max'             => absint( $_POST['max-friends'] ),
		'populate_extras' => 1,
	);

	if ( bp_has_members( $members_args ) ) : ?>
		<?php echo '0[[SPLIT]]'; // Return valid result. TODO: remove this. ?>
		<?php while ( bp_members() ) : bp_the_member(); ?>
			<li class="vcard">
				<div class="item-avatar">
					<a href="<?php bp_member_permalink(); ?>"><?php bp_member_avatar(); ?></a>
				</div>

				<div class="item">
					<div class="item-title fn"><a href="<?php bp_member_permalink(); ?>" title="<?php bp_member_name(); ?>"><?php bp_member_name(); ?></a></div>
					<?php if ( 'active' == $type ) : ?>
						<div class="item-meta"><span class="activity"><?php bp_member_last_active(); ?></span></div>
					<?php elseif ( 'newest' == $type ) : ?>
						<div class="item-meta"><span class="activity"><?php bp_member_registered(); ?></span></div>
					<?php elseif ( bp_is_active( 'friends' ) ) : ?>
						<div class="item-meta"><span class="activity"><?php bp_member_total_friend_count(); ?></span></div>
					<?php endif; ?>
				</div>
			</li>
		<?php endwhile; ?>

	<?php else: ?>
		<?php echo "-1[[SPLIT]]<li>"; ?>
		<?php _e( 'There were no members found, please try another filter.', 'buddypress' ); ?>
		<?php echo "</li>"; ?>
	<?php endif;
}
add_action( 'wp_ajax_widget_friends', 'bp_core_ajax_widget_friends' );
add_action( 'wp_ajax_nopriv_widget_friends', 'bp_core_ajax_widget_friends' );
