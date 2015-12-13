<?php
/**
 * BuddyPress Activity Classes.
 *
 * @package BuddyPress
 * @subpackage ActivityFeeds
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Create a RSS feed using the activity component.
 *
 * You should only construct a new feed when you've validated that you're on
 * the appropriate screen.
 *
 * @since 1.8.0
 *
 * See {@link bp_activity_action_sitewide_feed()} as an example.
 *
 * @param array $args {
 *   @type string $id               Required. Internal id for the feed; should be alphanumeric only.
 *   @type string $title            Optional. RSS feed title.
 *   @type string $link             Optional. Relevant link for the RSS feed.
 *   @type string $description      Optional. RSS feed description.
 *   @type string $ttl              Optional. Time-to-live. (see inline doc in constructor)
 *   @type string $update_period    Optional. Part of the syndication module.
 *                                            (see inline doc in constructor for more info)
 *   @type string $update_frequency Optional. Part of the syndication module.
 *                                            (see inline doc in constructor for more info)
 *   @type string $max              Optional. Number of feed items to display.
 *   @type array  $activity_args    Optional. Arguments passed to {@link bp_has_activities()}
 * }
 */
class BP_Activity_Feed {

	/**
	 * Holds our custom class properties.
	 *
	 * These variables are stored in a protected array that is magically
	 * updated using PHP 5.2+ methods.
	 *
	 * @see BP_Feed::__construct() This is where $data is added.
	 * @var array
	 */
	protected $data;

	/**
	 * Magic method for checking the existence of a certain data variable.
	 *
	 * @param string $key Property to check.
	 *
	 * @return bool Whether or not data variable exists.
	 */
	public function __isset( $key ) { return isset( $this->data[$key] ); }

	/**
	 * Magic method for getting a certain data variable.
	 *
	 * @param string $key Property to get.
	 *
	 * @return mixed Data in variable if available or null.
	 */
	public function __get( $key ) { return isset( $this->data[$key] ) ? $this->data[$key] : null; }

	/**
	 * Magic method for setting a certain data variable.
	 *
	 * @since 2.4.0
	 *
	 * @param string $key   The property to set.
	 * @param mixed  $value The value to set.
	 */
	public function __set( $key, $value ) { $this->data[$key] = $value; }

	/**
	 * Constructor.
	 *
	 * @param array $args Optional.
	 */
	public function __construct( $args = array() ) {

		/**
		 * Filters if BuddyPress should consider feeds enabled. If disabled, it will return early.
		 *
		 * @since 1.8.0
		 *
		 * @param bool true Default true aka feeds are enabled.
		 */
		if ( false === (bool) apply_filters( 'bp_activity_enable_feeds', true ) ) {
			global $wp_query;

			// Set feed flag to false.
			$wp_query->is_feed = false;

			return false;
		}

		// Setup data.
		$this->data = wp_parse_args( $args, array(
			// Internal identifier for the RSS feed - should be alphanumeric only.
			'id'               => '',

			// RSS title - should be plain-text.
			'title'            => '',

			// Relevant link for the RSS feed.
			'link'             => '',

			// RSS description - should be plain-text.
			'description'      => '',

			// Time-to-live - number of minutes to cache the data before an aggregator
			// requests it again.  This is only acknowledged if the RSS client supports it
			//
			// See: http://www.rssboard.org/rss-profile#element-channel-ttl.
			// See: http://www.kbcafe.com/rss/rssfeedstate.html#ttl.
			'ttl'              => '30',

			// Syndication module - similar to ttl, but not really supported by RSS
			// clients
			//
			// See: http://web.resource.org/rss/1.0/modules/syndication/#description.
			// See: http://www.kbcafe.com/rss/rssfeedstate.html#syndicationmodule.
			'update_period'    => 'hourly',
			'update_frequency' => 2,

			// Number of items to display.
			'max'              => 50,

			// Activity arguments passed to bp_has_activities().
			'activity_args'    => array()
		) );

		/**
		 * Fires before the feed is setup so plugins can modify.
		 *
		 * @since 1.8.0
		 *
		 * @param BP_Activity_Feed $this Current instance of activity feed. Passed by reference.
		 */
		do_action_ref_array( 'bp_activity_feed_prefetch', array( &$this ) );

		// Setup class properties.
		$this->setup_properties();

		// Check if id is valid.
		if ( empty( $this->id ) ) {
			_doing_it_wrong( 'BP_Activity_Feed', __( "RSS feed 'id' must be defined", 'buddypress' ), 'BP 1.8' );
			return false;
		}

		/**
		 * Fires after the feed is setup so plugins can modify.
		 *
		 * @since 1.8.0
		 *
		 * @param BP_Activity_Feed $this Current instance of activity feed. Passed by reference.
		 */
		do_action_ref_array( 'bp_activity_feed_postfetch', array( &$this ) );

		// Setup feed hooks.
		$this->setup_hooks();

		// Output the feed.
		$this->output();

		// Kill the rest of the output.
		die();
	}

	/** SETUP ****************************************************************/

	/**
	 * Setup and validate the class properties.
	 */
	protected function setup_properties() {
		$this->id               = sanitize_title( $this->id );
		$this->title            = strip_tags( $this->title );
		$this->link             = esc_url_raw( $this->link );
		$this->description      = strip_tags( $this->description );
		$this->ttl              = (int) $this->ttl;
		$this->update_period    = strip_tags( $this->update_period );
		$this->update_frequency = (int) $this->update_frequency;

		$this->activity_args    = wp_parse_args( $this->activity_args, array(
			'max'              => $this->max,
			'per_page'         => $this->max,
			'display_comments' => 'stream'
		) );

	}

	/**
	 * Setup some hooks that are used in the feed.
	 *
	 * Currently, these hooks are used to maintain backwards compatibility with
	 * the RSS feeds previous to BP 1.8.
	 */
	protected function setup_hooks() {
		add_action( 'bp_activity_feed_rss_attributes',   array( $this, 'backpat_rss_attributes' ) );
		add_action( 'bp_activity_feed_channel_elements', array( $this, 'backpat_channel_elements' ) );
		add_action( 'bp_activity_feed_item_elements',    array( $this, 'backpat_item_elements' ) );
	}

	/** BACKPAT HOOKS ********************************************************/

	/**
	 * Fire a hook to ensure backward compatibility for RSS attributes.
	 */
	public function backpat_rss_attributes() {

		/**
		 * Fires inside backpat_rss_attributes method for backwards compatibility related to RSS attributes.
		 *
		 * This hook was originally separated out for individual components but has since been abstracted into the BP_Activity_Feed class.
		 *
		 * @since 1.0.0
		 */
		do_action( 'bp_activity_' . $this->id . '_feed' );
	}

	/**
	 * Fire a hook to ensure backward compatibility for channel elements.
	 */
	public function backpat_channel_elements() {

		/**
		 * Fires inside backpat_channel_elements method for backwards compatibility related to RSS channel elements.
		 *
		 * This hook was originally separated out for individual components but has since been abstracted into the BP_Activity_Feed class.
		 *
		 * @since 1.0.0
		 */
		do_action( 'bp_activity_' . $this->id . '_feed_head' );
	}

	/**
	 * Fire a hook to ensure backward compatibility for item elements.
	 */
	public function backpat_item_elements() {
		switch ( $this->id ) {

			// Sitewide and friends feeds use the 'personal' hook.
			case 'sitewide' :
			case 'friends' :
				$id = 'personal';

				break;

			default :
				$id = $this->id;

				break;
		}

		/**
		 * Fires inside backpat_item_elements method for backwards compatibility related to RSS item elements.
		 *
		 * This hook was originally separated out for individual components but has since been abstracted into the BP_Activity_Feed class.
		 *
		 * @since 1.0.0
		 */
		do_action( 'bp_activity_' . $id . '_feed_item' );
	}

	/** HELPERS **************************************************************/

	/**
	 * Output the feed's item content.
	 */
	protected function feed_content() {
		bp_activity_content_body();

		switch ( $this->id ) {

			// Also output parent activity item if we're on a specific feed.
			case 'favorites' :
			case 'friends' :
			case 'mentions' :
			case 'personal' :

				if ( 'activity_comment' == bp_get_activity_action_name() ) :
			?>
				<strong><?php _e( 'In reply to', 'buddypress' ) ?></strong> -
				<?php bp_activity_parent_content() ?>
			<?php
				endif;

				break;
		}
	}

	/**
	 * Sets various HTTP headers related to Content-Type and browser caching.
	 *
	 * Most of this class method is derived from {@link WP::send_headers()}.
	 *
	 * @since 1.9.0
	 */
	protected function http_headers() {
		// Set up some additional headers if not on a directory page
		// this is done b/c BP uses pseudo-pages.
		if ( ! bp_is_directory() ) {
			global $wp_query;

			$wp_query->is_404 = false;
			status_header( 200 );
		}

		// Set content-type.
		@header( 'Content-Type: text/xml; charset=' . get_option( 'blog_charset' ), true );
		send_nosniff_header();

		// Cache-related variables.
		$last_modified      = mysql2date( 'D, d M Y H:i:s O', bp_activity_get_last_updated(), false );
		$modified_timestamp = strtotime( $last_modified );
		$etag               = md5( $last_modified );

		// Set cache-related headers.
		@header( 'Last-Modified: ' . $last_modified );
		@header( 'Pragma: no-cache' );
		@header( 'ETag: ' . '"' . $etag . '"' );

		// First commit of BuddyPress! (Easter egg).
		@header( 'Expires: Tue, 25 Mar 2008 17:13:55 GMT');

		// Get ETag from supported user agents.
		if ( isset( $_SERVER['HTTP_IF_NONE_MATCH'] ) ) {
			$client_etag = wp_unslash( $_SERVER['HTTP_IF_NONE_MATCH'] );

			// Remove quotes from ETag.
			$client_etag = trim( $client_etag, '"' );

			// Strip suffixes from ETag if they exist (eg. "-gzip").
			$etag_suffix_pos = strpos( $client_etag, '-' );
			if ( ! empty( $etag_suffix_pos ) ) {
				$client_etag = substr( $client_etag, 0, $etag_suffix_pos );
			}

		// No ETag found.
		} else {
			$client_etag = false;
		}

		// Get client last modified timestamp from supported user agents.
		$client_last_modified      = empty( $_SERVER['HTTP_IF_MODIFIED_SINCE'] ) ? '' : trim( $_SERVER['HTTP_IF_MODIFIED_SINCE'] );
		$client_modified_timestamp = $client_last_modified ? strtotime( $client_last_modified ) : 0;

		// Set 304 status if feed hasn't been updated since last fetch.
		if ( ( $client_last_modified && $client_etag ) ?
				 ( ( $client_modified_timestamp >= $modified_timestamp ) && ( $client_etag == $etag ) ) :
				 ( ( $client_modified_timestamp >= $modified_timestamp ) || ( $client_etag == $etag ) ) ) {
			$status = 304;
		} else {
			$status = false;
		}

		// If feed hasn't changed as reported by the user agent, set 304 status header.
		if ( ! empty( $status ) ) {
			status_header( $status );

			// Cached response, so stop now!
			if ( $status == 304 ) {
				exit();
			}
		}
	}

	/** OUTPUT ***************************************************************/

	/**
	 * Output the RSS feed.
	 */
	protected function output() {
		$this->http_headers();
		echo '<?xml version="1.0" encoding="' . get_option( 'blog_charset' ) . '"?'.'>';
	?>

<rss version="2.0"
	xmlns:content="http://purl.org/rss/1.0/modules/content/"
	xmlns:atom="http://www.w3.org/2005/Atom"
	xmlns:sy="http://purl.org/rss/1.0/modules/syndication/"
	xmlns:slash="http://purl.org/rss/1.0/modules/slash/"
	<?php

	/**
	 * Fires at the end of the opening RSS tag for feed output so plugins can add extra attributes.
	 *
	 * @since 1.8.0
	 */
	do_action( 'bp_activity_feed_rss_attributes' ); ?>
>

<channel>
	<title><?php echo $this->title; ?></title>
	<link><?php echo $this->link; ?></link>
	<atom:link href="<?php self_link(); ?>" rel="self" type="application/rss+xml" />
	<description><?php echo $this->description ?></description>
	<lastBuildDate><?php echo mysql2date( 'D, d M Y H:i:s O', bp_activity_get_last_updated(), false ); ?></lastBuildDate>
	<generator>https://buddypress.org/?v=<?php bp_version(); ?></generator>
	<language><?php bloginfo_rss( 'language' ); ?></language>
	<ttl><?php echo $this->ttl; ?></ttl>
	<sy:updatePeriod><?php echo $this->update_period; ?></sy:updatePeriod>
 	<sy:updateFrequency><?php echo $this->update_frequency; ?></sy:updateFrequency>
	<?php

	/**
	 * Fires at the end of channel elements list in RSS feed so plugins can add extra channel elements.
	 *
	 * @since 1.8.0
	 */
	do_action( 'bp_activity_feed_channel_elements' ); ?>

	<?php if ( bp_has_activities( $this->activity_args ) ) : ?>
		<?php while ( bp_activities() ) : bp_the_activity(); ?>
			<item>
				<guid isPermaLink="false"><?php bp_activity_feed_item_guid(); ?></guid>
				<title><?php echo stripslashes( bp_get_activity_feed_item_title() ); ?></title>
				<link><?php bp_activity_thread_permalink() ?></link>
				<pubDate><?php echo mysql2date( 'D, d M Y H:i:s O', bp_get_activity_feed_item_date(), false ); ?></pubDate>

				<?php if ( bp_get_activity_feed_item_description() ) : ?>
					<content:encoded><![CDATA[<?php $this->feed_content(); ?>]]></content:encoded>
				<?php endif; ?>

				<?php if ( bp_activity_can_comment() ) : ?>
					<slash:comments><?php bp_activity_comment_count(); ?></slash:comments>
				<?php endif; ?>

				<?php

				/**
				 * Fires at the end of the individual RSS Item list in RSS feed so plugins can add extra item elements.
				 *
				 * @since 1.8.0
				 */
				do_action( 'bp_activity_feed_item_elements' ); ?>
			</item>
		<?php endwhile; ?>

	<?php endif; ?>
</channel>
</rss><?php
	}
}
