<?php
/**
 * BuddyPress Notifications Template Functions.
 *
 * @package BuddyPress
 * @subpackage TonificationsTemplate
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Output the notifications component slug.
 *
 * @since 1.9.0
 */
function bp_notifications_slug() {
	echo bp_get_notifications_slug();
}
	/**
	 * Return the notifications component slug.
	 *
	 * @since 1.9.0
	 *
	 * @return string Slug of the Notifications component.
	 */
	function bp_get_notifications_slug() {

		/**
		 * Filters the notifications component slug.
		 *
		 * @since 1.9.0
		 *
		 * @param string $slug Notifications component slug.
		 */
		return apply_filters( 'bp_get_notifications_slug', buddypress()->notifications->slug );
	}

/**
 * Output the notifications permalink.
 *
 * @since 1.9.0
 */
function bp_notifications_permalink() {
	echo bp_get_notifications_permalink();
}
	/**
	 * Return the notifications permalink.
	 *
	 * @since 1.9.0
	 *
	 * @return string Notifications permalink.
	 */
	function bp_get_notifications_permalink() {
		$retval = trailingslashit( bp_loggedin_user_domain() . bp_get_notifications_slug() );

		/**
		 * Filters the notifications permalink.
		 *
		 * @since 1.9.0
		 *
		 * @param string $retval Permalink for the notifications.
		 */
		return apply_filters( 'bp_get_notifications_permalink', $retval );
	}

/**
 * Output the unread notifications permalink.
 *
 * @since 1.9.0
 */
function bp_notifications_unread_permalink() {
	echo bp_get_notifications_unread_permalink();
}
	/**
	 * Return the unread notifications permalink.
	 *
	 * @since 1.9.0
	 *
	 * @return string Unread notifications permalink.
	 */
	function bp_get_notifications_unread_permalink() {
		$retval = trailingslashit( bp_loggedin_user_domain() . bp_get_notifications_slug() . '/unread' );

		/**
		 * Filters the unread notifications permalink.
		 *
		 * @since 1.9.0
		 *
		 * @param string $retval Permalink for the unread notifications.
		 */
		return apply_filters( 'bp_get_notifications_unread_permalink', $retval );
	}

/**
 * Output the read notifications permalink.
 *
 * @since 1.9.0
 */
function bp_notifications_read_permalink() {
	echo bp_get_notifications_read_permalink();
}
	/**
	 * Return the read notifications permalink.
	 *
	 * @since 1.9.0
	 *
	 * @return string Read notifications permalink.
	 */
	function bp_get_notifications_read_permalink() {
		$retval = trailingslashit( bp_loggedin_user_domain() . bp_get_notifications_slug() . '/read' );

		/**
		 * Filters the read notifications permalink.
		 *
		 * @since 1.9.0
		 *
		 * @param string $retval Permalink for the read notifications.
		 */
		return apply_filters( 'bp_get_notifications_unread_permalink', $retval );
	}

/** Main Loop *****************************************************************/

/**
 * The main notifications template loop class.
 *
 * Responsible for loading a group of notifications into a loop for display.
 *
 * @since 1.9.0
 */
class BP_Notifications_Template {

	/**
	 * The loop iterator.
	 *
	 * @since 1.9.0
	 * @var int
	 */
	public $current_notification = -1;

	/**
	 * The number of notifications returned by the paged query.
	 *
	 * @since 1.9.0
	 * @var int
	 */
	public $current_notification_count;

	/**
	 * Total number of notifications matching the query.
	 *
	 * @since 1.9.0
	 * @var int
	 */
	public $total_notification_count;

	/**
	 * Array of notifications located by the query.
	 *
	 * @since 1.9.0
	 * @var array
	 */
	public $notifications;

	/**
	 * The notification object currently being iterated on.
	 *
	 * @since 1.9.0
	 * @var object
	 */
	public $notification;

	/**
	 * A flag for whether the loop is currently being iterated.
	 *
	 * @since 1.9.0
	 * @var bool
	 */
	public $in_the_loop;

	/**
	 * The ID of the user to whom the displayed notifications belong.
	 *
	 * @since 1.9.0
	 * @var int
	 */
	public $user_id;

	/**
	 * The page number being requested.
	 *
	 * @since 1.9.0
	 * @var int
	 */
	public $pag_page;

	/**
	 * The $_GET argument used in URLs for determining pagination.
	 *
	 * @since 1.9.0
	 * @var int
	 */
	public $pag_arg;

	/**
	 * The number of items to display per page of results.
	 *
	 * @since 1.9.0
	 * @var int
	 */
	public $pag_num;

	/**
	 * An HTML string containing pagination links.
	 *
	 * @since 1.9.0
	 * @var string
	 */
	public $pag_links;

	/**
	 * A string to match against.
	 *
	 * @since 1.9.0
	 * @var string
	 */
	public $search_terms;

	/**
	 * A database column to order the results by.
	 *
	 * @since 1.9.0
	 * @var string
	 */
	public $order_by;

	/**
	 * The direction to sort the results (ASC or DESC).
	 *
	 * @since 1.9.0
	 * @var string
	 */
	public $sort_order;

	/**
	 * Array of variables used in this notification query.
	 *
	 * @since 2.2.2
	 * @var array
	 */
	public $query_vars;

	/**
	 * Constructor method.
	 *
	 * @see bp_has_notifications() For information on the array format.
	 *
	 * @since 1.9.0
	 *
	 * @param array $args {
	 *     An array of arguments. See {@link bp_has_notifications()}
	 *     for more details.
	 * }
	 */
	public function __construct( $args = array() ) {

		// Parse arguments.
		$r = wp_parse_args( $args, array(
			'id'                => false,
			'user_id'           => 0,
			'item_id'           => false,
			'secondary_item_id' => false,
			'component_name'    => bp_notifications_get_registered_components(),
			'component_action'  => false,
			'is_new'            => true,
			'search_terms'      => '',
			'order_by'          => 'date_notified',
			'sort_order'        => 'DESC',
			'page_arg'          => 'npage',
			'page'              => 1,
			'per_page'          => 25,
			'max'               => null,
			'meta_query'        => false,
			'date_query'        => false
		) );

		// Sort order direction.
		$orders = array( 'ASC', 'DESC' );
		if ( ! empty( $_GET['sort_order'] ) && in_array( $_GET['sort_order'], $orders ) ) {
			$r['sort_order'] = $_GET['sort_order'];
		} else {
			$r['sort_order'] = in_array( $r['sort_order'], $orders ) ? $r['sort_order'] : 'DESC';
		}

		// Setup variables.
		$this->pag_arg      = sanitize_key( $r['page_arg'] );
		$this->pag_page     = bp_sanitize_pagination_arg( $this->pag_arg, $r['page']     );
		$this->pag_num      = bp_sanitize_pagination_arg( 'num',          $r['per_page'] );
		$this->user_id      = $r['user_id'];
		$this->is_new       = $r['is_new'];
		$this->search_terms = $r['search_terms'];
		$this->order_by     = $r['order_by'];
		$this->sort_order   = $r['sort_order'];
		$this->query_vars   = array(
			'id'                => $r['id'],
			'user_id'           => $this->user_id,
			'item_id'           => $r['item_id'],
			'secondary_item_id' => $r['secondary_item_id'],
			'component_name'    => $r['component_name'],
			'component_action'  => $r['component_action'],
			'meta_query'        => $r['meta_query'],
			'date_query'        => $r['date_query'],
			'is_new'            => $this->is_new,
			'search_terms'      => $this->search_terms,
			'order_by'          => $this->order_by,
			'sort_order'        => $this->sort_order,
			'page'              => $this->pag_page,
			'per_page'          => $this->pag_num,
		);

		// Setup the notifications to loop through.
		$this->notifications            = BP_Notifications_Notification::get( $this->query_vars );
		$this->total_notification_count = BP_Notifications_Notification::get_total_count( $this->query_vars );

		if ( empty( $this->notifications ) ) {
			$this->notification_count       = 0;
			$this->total_notification_count = 0;

		} else {
			if ( ! empty( $r['max'] ) ) {
				if ( $r['max'] >= count( $this->notifications ) ) {
					$this->notification_count = count( $this->notifications );
				} else {
					$this->notification_count = (int) $r['max'];
				}
			} else {
				$this->notification_count = count( $this->notifications );
			}
		}

		if ( (int) $this->total_notification_count && (int) $this->pag_num ) {
			$add_args = array(
				'sort_order' => $this->sort_order,
			);

			$this->pag_links = paginate_links( array(
				'base'      => add_query_arg( $this->pag_arg, '%#%' ),
				'format'    => '',
				'total'     => ceil( (int) $this->total_notification_count / (int) $this->pag_num ),
				'current'   => $this->pag_page,
				'prev_text' => _x( '&larr;', 'Notifications pagination previous text', 'buddypress' ),
				'next_text' => _x( '&rarr;', 'Notifications pagination next text',     'buddypress' ),
				'mid_size'  => 1,
				'add_args'  => $add_args,
			) );
		}
	}

	/**
	 * Whether there are notifications available in the loop.
	 *
	 * @since 1.9.0
	 *
	 * @see bp_has_notifications()
	 *
	 * @return bool True if there are items in the loop, otherwise false.
	 */
	public function has_notifications() {
		if ( $this->notification_count ) {
			return true;
		}

		return false;
	}

	/**
	 * Set up the next notification and iterate index.
	 *
	 * @since 1.9.0
	 *
	 * @return object The next notification to iterate over.
	 */
	public function next_notification() {

		$this->current_notification++;

		$this->notification = $this->notifications[ $this->current_notification ];

		return $this->notification;
	}

	/**
	 * Rewind the blogs and reset blog index.
	 *
	 * @since 1.9.0
	 */
	public function rewind_notifications() {

		$this->current_notification = -1;

		if ( $this->notification_count > 0 ) {
			$this->notification = $this->notifications[0];
		}
	}

	/**
	 * Whether there are notifications left in the loop to iterate over.
	 *
	 * This method is used by {@link bp_notifications()} as part of the
	 * while loop that controls iteration inside the notifications loop, eg:
	 *     while ( bp_notifications() ) { ...
	 *
	 * @since 1.9.0
	 *
	 * @see bp_notifications()
	 *
	 * @return bool True if there are more notifications to show,
	 *              otherwise false.
	 */
	public function notifications() {

		if ( $this->current_notification + 1 < $this->notification_count ) {
			return true;

		} elseif ( $this->current_notification + 1 == $this->notification_count ) {

			/**
			 * Fires right before the rewinding of notification posts.
			 *
			 * @since 1.9.0
			 */
			do_action( 'notifications_loop_end');

			$this->rewind_notifications();
		}

		$this->in_the_loop = false;
		return false;
	}

	/**
	 * Set up the current notification inside the loop.
	 *
	 * Used by {@link bp_the_notification()} to set up the current
	 * notification data while looping, so that template tags used during
	 * that iteration make reference to the current notification.
	 *
	 * @since 1.9.0
	 *
	 * @see bp_the_notification()
	 */
	public function the_notification() {
		$this->in_the_loop  = true;
		$this->notification = $this->next_notification();

		// Loop has just started.
		if ( 0 === $this->current_notification ) {

			/**
			 * Fires if the current notification item is the first in the notification loop.
			 *
			 * @since 1.9.0
			 */
			do_action( 'notifications_loop_start' );
		}
	}
}

/** The Loop ******************************************************************/

/**
 * Initialize the notifications loop.
 *
 * Based on the $args passed, bp_has_notifications() populates
 * buddypress()->notifications->query_loop global, enabling the use of BP
 * templates and template functions to display a list of notifications.
 *
 * @since 1.9.0
 *
 * @param array|string $args {
 *     Arguments for limiting the contents of the notifications loop. Can be
 *     passed as an associative array, or as a URL query string.
 *
 *     See {@link BP_Notifications_Notification::get()} for detailed
 *     information on the arguments.  In addition, also supports:
 *
 *     @type int    $max      Optional. Max items to display. Default: false.
 *     @type string $page_arg URL argument to use for pagination.
 *                            Default: 'npage'.
 * }
 * @return bool
 */
function bp_has_notifications( $args = '' ) {

	// Get the default is_new argument.
	if ( bp_is_current_action( 'unread' ) ) {
		$is_new = 1;
	} elseif ( bp_is_current_action( 'read' ) ) {
		$is_new = 0;

	// Not on a notifications page? default to fetch new notifications.
	} else {
		$is_new = 1;
	}

	// Get the user ID.
	if ( bp_displayed_user_id() ) {
		$user_id = bp_displayed_user_id();
	} else {
		$user_id = bp_loggedin_user_id();
	}

	// Parse the args.
	$r = bp_parse_args( $args, array(
		'id'                => false,
		'user_id'           => $user_id,
		'secondary_item_id' => false,
		'component_name'    => bp_notifications_get_registered_components(),
		'component_action'  => false,
		'is_new'            => $is_new,
		'search_terms'      => isset( $_REQUEST['s'] ) ? stripslashes( $_REQUEST['s'] ) : '',
		'order_by'          => 'date_notified',
		'sort_order'        => 'DESC',
		'meta_query'        => false,
		'date_query'        => false,
		'page'              => 1,
		'per_page'          => 25,

		// These are additional arguments that are not available in
		// BP_Notifications_Notification::get().
		'max'               => false,
		'page_arg'          => 'npage',
	), 'has_notifications' );

	// Get the notifications.
	$query_loop = new BP_Notifications_Template( $r );

	// Setup the global query loop.
	buddypress()->notifications->query_loop = $query_loop;

	/**
	 * Filters whether or not the user has notifications to display.
	 *
	 * @since 1.9.0
	 *
	 * @param bool                      $value      Whether or not there are notifications to display.
	 * @param BP_Notifications_Template $query_loop BP_Notifications_Template object instance.
	 */
	return apply_filters( 'bp_has_notifications', $query_loop->has_notifications(), $query_loop );
}

/**
 * Get the notifications returned by the template loop.
 *
 * @since 1.9.0
 *
 * @return array List of notifications.
 */
function bp_the_notifications() {
	return buddypress()->notifications->query_loop->notifications();
}

/**
 * Get the current notification object in the loop.
 *
 * @since 1.9.0
 *
 * @return object The current notification within the loop.
 */
function bp_the_notification() {
	return buddypress()->notifications->query_loop->the_notification();
}

/** Loop Output ***************************************************************/

/**
 * Output the ID of the notification currently being iterated on.
 *
 * @since 1.9.0
 */
function bp_the_notification_id() {
	echo bp_get_the_notification_id();
}
	/**
	 * Return the ID of the notification currently being iterated on.
	 *
	 * @since 1.9.0
	 *
	 * @return int ID of the current notification.
	 */
	function bp_get_the_notification_id() {

		/**
		 * Filters the ID of the notification currently being iterated on.
		 *
		 * @since 1.9.0
		 *
		 * @param int $id ID of the notification being iterated on.
		 */
		return apply_filters( 'bp_get_the_notification_id', buddypress()->notifications->query_loop->notification->id );
	}

/**
 * Output the associated item ID of the notification currently being iterated on.
 *
 * @since 1.9.0
 */
function bp_the_notification_item_id() {
	echo bp_get_the_notification_item_id();
}
	/**
	 * Return the associated item ID of the notification currently being iterated on.
	 *
	 * @since 1.9.0
	 *
	 * @return int ID of the item associated with the current notification.
	 */
	function bp_get_the_notification_item_id() {

		/**
		 * Filters the associated item ID of the notification currently being iterated on.
		 *
		 * @since 1.9.0
		 *
		 * @param int $item_id ID of the associated item.
		 */
		return apply_filters( 'bp_get_the_notification_item_id', buddypress()->notifications->query_loop->notification->item_id );
	}

/**
 * Output the secondary associated item ID of the notification currently being iterated on.
 *
 * @since 1.9.0
 */
function bp_the_notification_secondary_item_id() {
	echo bp_get_the_notification_secondary_item_id();
}
	/**
	 * Return the secondary associated item ID of the notification currently being iterated on.
	 *
	 * @since 1.9.0
	 *
	 * @return int ID of the secondary item associated with the current notification.
	 */
	function bp_get_the_notification_secondary_item_id() {

		/**
		 * Filters the secondary associated item ID of the notification currently being iterated on.
		 *
		 * @since 1.9.0
		 *
		 * @param int $secondary_item_id ID of the secondary associated item.
		 */
		return apply_filters( 'bp_get_the_notification_secondary_item_id', buddypress()->notifications->query_loop->notification->secondary_item_id );
	}

/**
 * Output the name of the component associated with the notification currently being iterated on.
 *
 * @since 1.9.0
 */
function bp_the_notification_component_name() {
	echo bp_get_the_notification_component_name();
}
	/**
	 * Return the name of the component associated with the notification currently being iterated on.
	 *
	 * @since 1.9.0
	 *
	 * @return int Name of the component associated with the current notification.
	 */
	function bp_get_the_notification_component_name() {

		/**
		 * Filters the name of the component associated with the notification currently being iterated on.
		 *
		 * @since 1.9.0
		 *
		 * @param int $component_name Name of the component associated with the current notification.
		 */
		return apply_filters( 'bp_get_the_notification_component_name', buddypress()->notifications->query_loop->notification->component_name );
	}

/**
 * Output the name of the action associated with the notification currently being iterated on.
 *
 * @since 1.9.0
 */
function bp_the_notification_component_action() {
	echo bp_get_the_notification_component_action();
}
	/**
	 * Return the name of the action associated with the notification currently being iterated on.
	 *
	 * @since 1.9.0
	 *
	 * @return int Name of the action associated with the current notification.
	 */
	function bp_get_the_notification_component_action() {

		/**
		 * Filters the name of the action associated with the notification currently being iterated on.
		 *
		 * @since 1.9.0
		 *
		 * @param int $component_action Name of the action associated with the current notification.
		 */
		return apply_filters( 'bp_get_the_notification_component_action', buddypress()->notifications->query_loop->notification->component_action );
	}

/**
 * Output the timestamp of the current notification.
 *
 * @since 1.9.0
 */
function bp_the_notification_date_notified() {
	echo bp_get_the_notification_date_notified();
}
	/**
	 * Return the timestamp of the current notification.
	 *
	 * @since 1.9.0
	 *
	 * @return string Timestamp of the current notification.
	 */
	function bp_get_the_notification_date_notified() {

		/**
		 * Filters the timestamp of the current notification.
		 *
		 * @since 1.9.0
		 *
		 * @param string $date_notified Timestamp of the current notification.
		 */
		return apply_filters( 'bp_get_the_notification_date_notified', buddypress()->notifications->query_loop->notification->date_notified );
	}

/**
 * Output the timestamp of the current notification.
 *
 * @since 1.9.0
 */
function bp_the_notification_time_since() {
	echo bp_get_the_notification_time_since();
}
	/**
	 * Return the timestamp of the current notification.
	 *
	 * @since 1.9.0
	 *
	 * @return string Timestamp of the current notification.
	 */
	function bp_get_the_notification_time_since() {

		// Get the notified date.
		$date_notified = bp_get_the_notification_date_notified();

		// Notified date has legitimate data.
		if ( '0000-00-00 00:00:00' !== $date_notified ) {
			$retval = bp_core_time_since( $date_notified );

		// Notified date is empty, so return a fun string.
		} else {
			$retval = __( 'Date not found', 'buddypress' );
		}

		/**
		 * Filters the time since value of the current notification.
		 *
		 * @since 1.9.0
		 *
		 * @param string $retval Time since value for current notification.
		 */
		return apply_filters( 'bp_get_the_notification_time_since', $retval );
	}

/**
 * Output full-text description for a specific notification.
 *
 * @since 1.9.0
 */
function bp_the_notification_description() {
	echo bp_get_the_notification_description();
}

	/**
	 * Get full-text description for a specific notification.
	 *
	 * @since 1.9.0
	 *
	 * @return string
	 */
	function bp_get_the_notification_description() {
		$bp           = buddypress();
		$notification = $bp->notifications->query_loop->notification;

		// Callback function exists.
		if ( isset( $bp->{ $notification->component_name }->notification_callback ) && is_callable( $bp->{ $notification->component_name }->notification_callback ) ) {
			$description = call_user_func( $bp->{ $notification->component_name }->notification_callback, $notification->component_action, $notification->item_id, $notification->secondary_item_id, 1 );

		// @deprecated format_notification_function - 1.5
		} elseif ( isset( $bp->{ $notification->component_name }->format_notification_function ) && function_exists( $bp->{ $notification->component_name }->format_notification_function ) ) {
			$description = call_user_func( $bp->{ $notification->component_name }->format_notification_function, $notification->component_action, $notification->item_id, $notification->secondary_item_id, 1 );

		// Allow non BuddyPress components to hook in.
		} else {

			/** This filter is documented in bp-notifications/bp-notifications-functions.php */
			$description = apply_filters_ref_array( 'bp_notifications_get_notifications_for_user', array( $notification->component_action, $notification->item_id, $notification->secondary_item_id, 1 ) );
		}

		/**
		 * Filters the full-text description for a specific notification.
		 *
		 * @since 1.9.0
		 * @since 2.3.0 Added the `$notification` parameter.
		 *
		 * @param string $description  Full-text description for a specific notification.
		 * @param object $notification Notification object.
		 */
		return apply_filters( 'bp_get_the_notification_description', $description, $notification );
	}

/**
 * Output the mark read link for the current notification.
 *
 * @since 1.9.0
 *
 * @uses bp_get_the_notification_mark_read_link()
 */
function bp_the_notification_mark_read_link() {
	echo bp_get_the_notification_mark_read_link();
}
	/**
	 * Return the mark read link for the current notification.
	 *
	 * @since 1.9.0
	 */
	function bp_get_the_notification_mark_read_link() {

		// Start the output buffer.
		ob_start(); ?>

		<a href="<?php bp_the_notification_mark_read_url(); ?>" class="mark-read primary"><?php _e( 'Read', 'buddypress' ); ?></a>

		<?php $retval = ob_get_clean();

		/**
		 * Filters the mark read link for the current notification.
		 *
		 * @since 1.9.0
		 *
		 * @param string $retval HTML for the mark read link for the current notification.
		 */
		return apply_filters( 'bp_get_the_notification_mark_read_link', $retval );
	}

/**
 * Output the URL used for marking a single notification as read.
 *
 * Since this function directly outputs a URL, it is escaped.
 *
 * @since 2.1.0
 *
 * @uses bp_get_the_notification_mark_read_url()
 */
function bp_the_notification_mark_read_url() {
	echo esc_url( bp_get_the_notification_mark_read_url() );
}
	/**
	 * Return the URL used for marking a single notification as read.
	 *
	 * @since 2.1.0
	 */
	function bp_get_the_notification_mark_read_url() {

		// Get the notification ID.
		$id   = bp_get_the_notification_id();

		// Get the args to add to the URL.
		$args = array(
			'action'          => 'read',
			'notification_id' => $id
		);

		// Add the args to the URL.
		$url = add_query_arg( $args, bp_get_notifications_unread_permalink() );

		// Add the nonce.
		$url = wp_nonce_url( $url, 'bp_notification_mark_read_' . $id );

		/**
		 * Filters the URL used for marking a single notification as read.
		 *
		 * @since 2.1.0
		 *
		 * @param string $url URL to use for marking the single notification as read.
		 */
		return apply_filters( 'bp_get_the_notification_mark_read_url', $url );
	}

/**
 * Output the mark unread link for the current notification.
 *
 * @since 1.9.0
 *
 * @uses bp_get_the_notification_mark_unread_link()
 */
function bp_the_notification_mark_unread_link() {
	echo bp_get_the_notification_mark_unread_link();
}
	/**
	 * Return the mark unread link for the current notification.
	 *
	 * @since 1.9.0
	 */
	function bp_get_the_notification_mark_unread_link() {

		// Start the output buffer.
		ob_start(); ?>

		<a href="<?php bp_the_notification_mark_unread_url(); ?>" class="mark-unread primary"><?php _ex( 'Unread',  'Notification screen action', 'buddypress' ); ?></a>

		<?php $retval = ob_get_clean();

		/**
		 * Filters the link used for marking a single notification as unread.
		 *
		 * @since 1.9.0
		 *
		 * @param string $retval HTML for the mark unread link for the current notification.
		 */
		return apply_filters( 'bp_get_the_notification_mark_unread_link', $retval );
	}

/**
 * Output the URL used for marking a single notification as unread.
 *
 * Since this function directly outputs a URL, it is escaped.
 *
 * @since 2.1.0
 *
 * @uses bp_get_the_notification_mark_unread_url()
 */
function bp_the_notification_mark_unread_url() {
	echo esc_url( bp_get_the_notification_mark_unread_url() );
}
	/**
	 * Return the URL used for marking a single notification as unread.
	 *
	 * @since 2.1.0
	 */
	function bp_get_the_notification_mark_unread_url() {

		// Get the notification ID.
		$id   = bp_get_the_notification_id();

		// Get the args to add to the URL.
		$args = array(
			'action'          => 'unread',
			'notification_id' => $id
		);

		// Add the args to the URL.
		$url = add_query_arg( $args, bp_get_notifications_read_permalink() );

		// Add the nonce.
		$url = wp_nonce_url( $url, 'bp_notification_mark_unread_' . $id );

		/**
		 * Filters the URL used for marking a single notification as unread.
		 *
		 * @since 2.1.0
		 *
		 * @param string $url URL to use for marking the single notification as unread.
		 */
		return apply_filters( 'bp_get_the_notification_mark_unread_url', $url );
	}

/**
 * Output the mark link for the current notification.
 *
 * @since 1.9.0
 *
 * @uses bp_get_the_notification_mark_unread_link()
 */
function bp_the_notification_mark_link() {
	echo bp_get_the_notification_mark_link();
}
	/**
	 * Return the mark link for the current notification.
	 *
	 * @since 1.9.0
	 */
	function bp_get_the_notification_mark_link() {

		if ( bp_is_current_action( 'read' ) ) {
			$retval = bp_get_the_notification_mark_unread_link();
		} else {
			$retval = bp_get_the_notification_mark_read_link();
		}

		/**
		 * Filters the mark link for the current notification.
		 *
		 * @since 1.9.0
		 *
		 * @param string $retval The mark link for the current notification.
		 */
		return apply_filters( 'bp_get_the_notification_mark_link', $retval );
	}

/**
 * Output the delete link for the current notification.
 *
 * @since 1.9.0
 *
 * @uses bp_get_the_notification_delete_link()
 */
function bp_the_notification_delete_link() {
	echo bp_get_the_notification_delete_link();
}
	/**
	 * Return the delete link for the current notification.
	 *
	 * @since 1.9.0
	 */
	function bp_get_the_notification_delete_link() {

		// Start the output buffer.
		ob_start(); ?>

		<a href="<?php bp_the_notification_delete_url(); ?>" class="delete secondary confirm"><?php _e( 'Delete', 'buddypress' ); ?></a>

		<?php $retval = ob_get_clean();

		/**
		 * Filters the delete link for the current notification.
		 *
		 * @since 1.9.0
		 *
		 * @param string $retval HTML for the delete link for the current notification.
		 */
		return apply_filters( 'bp_get_the_notification_delete_link', $retval );
	}

/**
 * Output the URL used for deleting a single notification.
 *
 * Since this function directly outputs a URL, it is escaped.
 *
 * @since 2.1.0
 *
 * @uses esc_url()
 * @uses bp_get_the_notification_delete_url()
 */
function bp_the_notification_delete_url() {
	echo esc_url( bp_get_the_notification_delete_url() );
}
	/**
	 * Return the URL used for deleting a single notification.
	 *
	 * @since 2.1.0
	 *
	 * @return string
	 */
	function bp_get_the_notification_delete_url() {

		// URL to add nonce to.
		if ( bp_is_current_action( 'unread' ) ) {
			$link = bp_get_notifications_unread_permalink();
		} elseif ( bp_is_current_action( 'read' ) ) {
			$link = bp_get_notifications_read_permalink();
		}

		// Get the ID.
		$id = bp_get_the_notification_id();

		// Get the args to add to the URL.
		$args = array(
			'action'          => 'delete',
			'notification_id' => $id
		);

		// Add the args.
		$url = add_query_arg( $args, $link );

		// Add the nonce.
		$url = wp_nonce_url( $url, 'bp_notification_delete_' . $id );

		/**
		 * Filters the URL used for deleting a single notification.
		 *
		 * @since 2.1.0
		 *
		 * @param string $url URL used for deleting a single notification.
		 */
		return apply_filters( 'bp_get_the_notification_delete_url', $url );
	}

/**
 * Output the action links for the current notification.
 *
 * @since 1.9.0
 *
 * @param array|string $args Array of arguments.
 */
function bp_the_notification_action_links( $args = '' ) {
	echo bp_get_the_notification_action_links( $args );
}
	/**
	 * Return the action links for the current notification.
	 *
	 * @since 1.9.0
	 *
	 * @param array|string $args {
	 *     @type string $before HTML before the links.
	 *     @type string $after  HTML after the links.
	 *     @type string $sep    HTML between the links.
	 *     @type array  $links  Array of links to implode by 'sep'.
	 * }
	 * @return string HTML links for actions to take on single notifications.
	 */
	function bp_get_the_notification_action_links( $args = '' ) {

		// Parse.
		$r = wp_parse_args( $args, array(
			'before' => '',
			'after'  => '',
			'sep'    => ' | ',
			'links'  => array(
				bp_get_the_notification_mark_link(),
				bp_get_the_notification_delete_link()
			)
		) );

		// Build the links.
		$retval = $r['before'] . implode( $r['links'], $r['sep'] ) . $r['after'];

		/**
		 * Filters the action links for the current notification.
		 *
		 * @since 1.9.0
		 *
		 * @param string $retval HTML links for actions to take on single notifications.
		 */
		return apply_filters( 'bp_get_the_notification_action_links', $retval );
	}

/**
 * Output the pagination count for the current notification loop.
 *
 * @since 1.9.0
 */
function bp_notifications_pagination_count() {
	echo bp_get_notifications_pagination_count();
}
	/**
	 * Return the pagination count for the current notification loop.
	 *
	 * @since 1.9.0
	 *
	 * @return string HTML for the pagination count.
	 */
	function bp_get_notifications_pagination_count() {
		$query_loop = buddypress()->notifications->query_loop;
		$start_num  = intval( ( $query_loop->pag_page - 1 ) * $query_loop->pag_num ) + 1;
		$from_num   = bp_core_number_format( $start_num );
		$to_num     = bp_core_number_format( ( $start_num + ( $query_loop->pag_num - 1 ) > $query_loop->total_notification_count ) ? $query_loop->total_notification_count : $start_num + ( $query_loop->pag_num - 1 ) );
		$total      = bp_core_number_format( $query_loop->total_notification_count );

		if ( 1 == $query_loop->total_notification_count ) {
			$pag = __( 'Viewing 1 notification', 'buddypress' );
		} else {
			$pag = sprintf( _n( 'Viewing %1$s - %2$s of %3$s notification', 'Viewing %1$s - %2$s of %3$s notifications', $query_loop->total_notification_count, 'buddypress' ), $from_num, $to_num, $total );
		}

		/**
		 * Filters the pagination count for the current notification loop.
		 *
		 * @since 1.9.0
		 *
		 * @param string $pag HTML for the pagination count.
		 */
		return apply_filters( 'bp_notifications_pagination_count', $pag );
	}

/**
 * Output the pagination links for the current notification loop.
 *
 * @since 1.9.0
 */
function bp_notifications_pagination_links() {
	echo bp_get_notifications_pagination_links();
}
	/**
	 * Return the pagination links for the current notification loop.
	 *
	 * @since 1.9.0
	 *
	 * @return string HTML for the pagination links.
	 */
	function bp_get_notifications_pagination_links() {

		/**
		 * Filters the pagination links for the current notification loop.
		 *
		 * @since 1.9.0
		 *
		 * @param string $pag_links HTML for the pagination links.
		 */
		return apply_filters( 'bp_get_notifications_pagination_links', buddypress()->notifications->query_loop->pag_links );
	}

/** Form Helpers **************************************************************/

/**
 * Output the form for changing the sort order of notifications.
 *
 * @since 1.9.0
 */
function bp_notifications_sort_order_form() {

	// Setup local variables.
	$orders   = array( 'DESC', 'ASC' );
	$selected = 'DESC';

	// Check for a custom sort_order.
	if ( !empty( $_REQUEST['sort_order'] ) ) {
		if ( in_array( $_REQUEST['sort_order'], $orders ) ) {
			$selected = $_REQUEST['sort_order'];
		}
	} ?>

	<form action="" method="get" id="notifications-sort-order">
		<label for="notifications-sort-order-list"><?php esc_html_e( 'Order By:', 'buddypress' ); ?></label>

		<select id="notifications-sort-order-list" name="sort_order" onchange="this.form.submit();">
			<option value="DESC" <?php selected( $selected, 'DESC' ); ?>><?php _e( 'Newest First', 'buddypress' ); ?></option>
			<option value="ASC"  <?php selected( $selected, 'ASC'  ); ?>><?php _e( 'Oldest First', 'buddypress' ); ?></option>
		</select>

		<noscript>
			<input id="submit" type="submit" name="form-submit" class="submit" value="<?php esc_attr_e( 'Go', 'buddypress' ); ?>" />
		</noscript>
	</form>

<?php
}

/**
 * Output the dropdown for bulk management of notifications.
 *
 * @since 2.2.0
 */
function bp_notifications_bulk_management_dropdown() {
	?>
	<label class="bp-screen-reader-text" for="notification-select"><?php _e( 'Select Bulk Action', 'buddypress' ); ?></label>
	<select name="notification_bulk_action" id="notification-select">
		<option value="" selected="selected"><?php _e( 'Bulk Actions', 'buddypress' ); ?></option>

		<?php if ( bp_is_current_action( 'unread' ) ) : ?>
			<option value="read"><?php _e( 'Mark read', 'buddypress' ); ?></option>
		<?php elseif ( bp_is_current_action( 'read' ) ) : ?>
			<option value="unread"><?php _e( 'Mark unread', 'buddypress' ); ?></option>
		<?php endif; ?>
		<option value="delete"><?php _e( 'Delete', 'buddypress' ); ?></option>
	</select>
	<input type="submit" id="notification-bulk-manage" class="button action" value="<?php esc_attr_e( 'Apply', 'buddypress' ); ?>">
	<?php
}
