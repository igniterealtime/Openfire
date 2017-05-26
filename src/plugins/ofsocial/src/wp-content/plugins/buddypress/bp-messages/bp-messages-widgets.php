<?php
/**
 * BuddyPress Messages Widgets.
 *
 * @package BuddyPress
 * @subpackage Messages
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Register widgets for the Messages component.
 *
 * @since 1.9.0
 */
function bp_messages_register_widgets() {
	add_action( 'widgets_init', create_function('', 'return register_widget( "BP_Messages_Sitewide_Notices_Widget" );') );
}
add_action( 'bp_register_widgets', 'bp_messages_register_widgets' );

/**
 * A widget that displays sitewide notices.
 *
 * @since 1.9.0
 */
class BP_Messages_Sitewide_Notices_Widget extends WP_Widget {

	/**
	 * Constructor method.
	 */
	function __construct() {
		parent::__construct(
			'bp_messages_sitewide_notices_widget',
			__( '(BuddyPress) Sitewide Notices', 'buddypress' ),
			array(
				'classname'   => 'widget_bp_core_sitewide_messages buddypress widget',
				'description' => __( 'Display Sitewide Notices posted by the site administrator', 'buddypress' ),
			)
		);
	}

	/**
	 * Render the widget.
	 *
	 * @see WP_Widget::widget() for a description of parameters.
	 *
	 * @param array $args     See {@WP_Widget::widget()}.
	 * @param array $instance See {@WP_Widget::widget()}.
	 */
	public function widget( $args, $instance ) {

		if ( ! is_user_logged_in() ) {
			return;
		}

		// Don't display the widget if there are no Notices to show.
		$notices = BP_Messages_Notice::get_active();
		if ( empty( $notices ) ) {
			return;
		}

		extract( $args );

		$title = ! empty( $instance['title'] ) ? $instance['title'] : '';

		/**
		 * Filters the title of the Messages widget.
		 *
		 * @since 1.9.0
		 * @since 2.3.0 Added 'instance' and 'id_base' to arguments passed to filter.
		 *
		 * @param string $title    The widget title.
		 * @param array  $instance The settings for the particular instance of the widget.
		 * @param string $id_base  Root ID for all widgets of this type.
		 */
		$title = apply_filters( 'widget_title', $title, $instance, $this->id_base );

		echo $before_widget;
		echo $before_title . $title . $after_title; ?>

		<div class="bp-site-wide-message">
			<?php bp_message_get_notices(); ?>
		</div>

		<?php

		echo $after_widget;
	}

	/**
	 * Process the saved settings for the widget.
	 *
	 * @see WP_Widget::update() for a description of parameters and
	 *      return values.
	 *
	 * @param array $new_instance See {@WP_Widget::update()}.
	 * @param array $old_instance See {@WP_Widget::update()}.
	 * @return array $instance See {@WP_Widget::update()}.
	 */
	public function update( $new_instance, $old_instance ) {
		$instance = $old_instance;
		$instance['title'] = strip_tags( $new_instance['title'] );
		return $instance;
	}

	/**
	 * Render the settings form for Appearance > Widgets.
	 *
	 * @see WP_Widget::form() for a description of parameters.
	 *
	 * @param array $instance See {@WP_Widget::form()}.
	 *
	 * @return string Widget form output.
	 */
	public function form( $instance ) {
		$instance = wp_parse_args( (array) $instance, array(
			'title' => '',
		) );

		$title = strip_tags( $instance['title'] ); ?>

		<p>
			<label for="<?php echo $this->get_field_id( 'title' ); ?>"><?php _e( 'Title:', 'buddypress' ); ?></label>
			<input class="widefat" id="<?php echo $this->get_field_id( 'title' ); ?>" name="<?php echo $this->get_field_name( 'title' ); ?>" type="text" value="<?php echo esc_attr( $title ); ?>" />
		</p>

		<?php
	}
}
