<?php
/**
 * BuddyPress Blogs Widgets.
 *
 * @package BuddyPress
 * @subpackage BlogsWidgets
 */

// Exit if accessed directly
defined( 'ABSPATH' ) || exit;

/**
 * Register the widgets for the Blogs component.
 */
function bp_blogs_register_widgets() {
	global $wpdb;

	if ( bp_is_active( 'activity' ) && bp_is_root_blog( $wpdb->blogid ) ) {
		add_action( 'widgets_init', create_function( '', 'return register_widget("BP_Blogs_Recent_Posts_Widget");' ) );
	}
}
add_action( 'bp_register_widgets', 'bp_blogs_register_widgets' );

/**
 * The Recent Networkwide Posts widget.
 */
class BP_Blogs_Recent_Posts_Widget extends WP_Widget {

	/**
	 * Constructor method.
	 */
	public function __construct() {
		$widget_ops = array(
			'description' => __( 'A list of recently published posts from across your network.', 'buddypress' ),
			'classname'   => 'widget_bp_blogs_widget buddypress widget',
		);
		parent::__construct( false, $name = _x( '(BuddyPress) Recent Networkwide Posts', 'widget name', 'buddypress' ), $widget_ops );
	}

	/**
	 * Display the networkwide posts widget.
	 *
	 * @see WP_Widget::widget() for description of parameters.
	 *
	 * @param array $args     Widget arguments.
	 * @param array $instance Widget settings, as saved by the user.
	 */
	public function widget( $args, $instance ) {

		$title = ! empty( $instance['title'] )
			? esc_html( $instance['title'] )
			: __( 'Recent Networkwide Posts', 'buddypress' );

		if ( ! empty( $instance['link_title'] ) ) {
			$title = '<a href="' . bp_get_blogs_directory_permalink() . '">' . esc_html( $title ) . '</a>';
		}

		/**
		 * Filters the Blogs Recent Posts widget title.
		 *
		 * @since 2.2.0
		 * @since 2.3.0 Added 'instance' and 'id_base' to arguments passed to filter.
		 *
		 * @param string $title    The widget title.
		 * @param array  $instance The settings for the particular instance of the widget.
		 * @param string $id_base  Root ID for all widgets of this type.
		 */
		$title = apply_filters( 'widget_title', $title, $instance, $this->id_base );

		echo $args['before_widget'];
		echo $args['before_title'] . $title . $args['after_title'];

		if ( empty( $instance['max_posts'] ) || empty( $instance['max_posts'] ) ) {
			$instance['max_posts'] = 10;
		}

		$after_widget = $args['after_widget'];

		// Override some of the contextually set parameters for bp_has_activities()
		$args = array(
			'action'     => 'new_blog_post',
			'max'        => $instance['max_posts'],
			'per_page'   => $instance['max_posts'],
			'user_id'    => 0,
			'scope'      => false,
			'object'     => false,
			'primary_id' => false
		); ?>

		<?php if ( bp_has_activities( $args ) ) : ?>

			<ul id="blog-post-list" class="activity-list item-list">

				<?php while ( bp_activities() ) : bp_the_activity(); ?>

					<li>
						<div class="activity-content" style="margin: 0">
							<div class="activity-header"><?php bp_activity_action(); ?></div>

							<?php if ( bp_get_activity_content_body() ) : ?>

								<div class="activity-inner"><?php bp_activity_content_body(); ?></div>

							<?php endif; ?>

						</div>
					</li>

				<?php endwhile; ?>

			</ul>

		<?php else : ?>

			<div id="message" class="info">
				<p><?php _e( 'Sorry, there were no posts found. Why not write one?', 'buddypress' ); ?></p>
			</div>

		<?php endif; ?>

		<?php echo $after_widget; ?>
	<?php
	}

	/**
	 * Update the networkwide posts widget options.
	 *
	 * @param array $new_instance The new instance options.
	 * @param array $old_instance The old instance options.
	 *
	 * @return array $instance The parsed options to be saved.
	 */
	public function update( $new_instance, $old_instance ) {
		$instance               = $old_instance;
		$instance['title']      = strip_tags( $new_instance['title'] );
		$instance['max_posts']  = strip_tags( $new_instance['max_posts'] );
		$instance['link_title'] = (bool) $new_instance['link_title'];

		return $instance;
	}

	/**
	 * Output the networkwide posts widget options form.
	 *
	 * @param array $instance Settings for this widget.
	 *
	 * @return string
	 */
	public function form( $instance ) {
		$instance = wp_parse_args( (array) $instance, array(
			'title'      => __( 'Recent Networkwide Posts', 'buddypress' ),
			'max_posts'  => 10,
			'link_title' => false,
		) );

		$title      = strip_tags( $instance['title'] );
		$max_posts  = strip_tags( $instance['max_posts'] );
		$link_title = (bool) $instance['link_title'];

		?>

		<p><label for="<?php echo $this->get_field_id( 'title' ); ?>"><?php _ex( 'Title:', 'Label for the Title field of the Recent Networkwide Posts widget', 'buddypress' ); ?> <input class="widefat" id="<?php echo $this->get_field_id( 'title' ); ?>" name="<?php echo $this->get_field_name( 'title' ); ?>" type="text" value="<?php echo esc_attr( $title ); ?>" style="width: 100%;" /></label></p>
		<p><label for="<?php echo $this->get_field_id( 'link_title' ); ?>"><input type="checkbox" name="<?php echo $this->get_field_name( 'link_title' ); ?>" value="1" <?php checked( $link_title ); ?> /> <?php _e( 'Link widget title to Blogs directory', 'buddypress' ); ?></label></p>
		<p><label for="<?php echo $this->get_field_id( 'max_posts' ); ?>"><?php _e( 'Max posts to show:', 'buddypress' ); ?> <input class="widefat" id="<?php echo $this->get_field_id( 'max_posts' ); ?>" name="<?php echo $this->get_field_name( 'max_posts' ); ?>" type="text" value="<?php echo esc_attr( $max_posts ); ?>" style="width: 30%" /></label></p>
		<?php
	}
}
