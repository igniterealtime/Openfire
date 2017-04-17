<?php
class Bavotasan_Documentation {
	public function __construct() {
		add_action( 'admin_menu', array( $this, 'admin_menu' ), 1 );
		add_action( 'load-themes.php', array( $this, 'activation_admin_notice' ) );
	}

	/**
	 * Adds an admin notice upon successful activation.
	 * @since 1.0.3
	 */
	public function activation_admin_notice() {
		global $pagenow;

		if ( is_admin() && 'themes.php' == $pagenow && isset( $_GET['activated'] ) )
			add_action( 'admin_notices', array( $this, 'welcome_admin_notice' ), 99 );
	}

	/**
	 * Display an admin notice linking to the welcome screen
	 * @since 1.0.3
	 */
	public function welcome_admin_notice() {
		?>
		<div class="updated fade">
			<p><?php echo sprintf( esc_html__( 'Thanks for choosing %s! You can learn how to use the available theme options on the %sabout page%s.', 'destin' ), BAVOTASAN_THEME_NAME, '<a href="' . esc_url( admin_url( 'themes.php?page=bavotasan_documentation' ) ) . '">', '</a>' ); ?></p>

			<p><a href="<?php echo esc_url( admin_url( 'themes.php?page=bavotasan_documentation' ) ); ?>" class="button" style="text-decoration: none;"><?php printf( __( 'Learn more about %s', 'destin' ), BAVOTASAN_THEME_NAME ); ?></a></p>
		</div>
		<?php
	}

	/**
	 * Add a 'Documentation' menu item to the Appearance panel
	 *
	 * This function is attached to the 'admin_menu' action hook.
	 *
	 * @since 1.0.0
	 */
	public function admin_menu() {
		add_theme_page( sprintf( __( 'Welcome to %s %s', 'destin' ), BAVOTASAN_THEME_NAME, BAVOTASAN_THEME_VERSION ), sprintf( __( 'About %s', 'destin' ), BAVOTASAN_THEME_NAME ), 'edit_theme_options', 'bavotasan_documentation', array( $this, 'bavotasan_documentation' ) );
	}

	public function bavotasan_documentation() {
		?>
		<style>
		.featured-image {
			margin: 20px auto !important;
		}

		.about-wrap .headline-feature h2 {
			text-align: center;
		}

		.about-wrap .dfw h3 {
			text-align: left;
		}

		.changelog.headline-feature.dfw {
			max-width: 68%;
		}

		.changelog.headline-feature.dfw {
			margin-left: auto;
			margin-right: auto;
		}

		.about-wrap ul {
			padding-left: 60px;
			list-style: disc;
			margin-bottom: 20px;
		}

		.about-wrap .theme-badge {
			position: absolute;
			top: 0;
			right: 0;
		}

		.about-wrap .feature-section {
			border: 0;
			padding: 0;
		}

		@media only screen and (max-width: 768px) {
			.changelog.headline-feature.dfw {
				max-width: 100%;
			}

			.about-wrap .theme-badge {
				display: none;
			}
		}
		</style>
		<div class="wrap about-wrap" id="custom-background">
			<h1><?php echo get_admin_page_title(); ?></h1>

			<div class="about-text">
				<?php printf( __( 'Read through the following documentation to learn more about <em>%s</em> and how to use the available theme options.', 'destin' ), BAVOTASAN_THEME_NAME, '<a href="//demos.bavotasan.com/' . sanitize_title( BAVOTASAN_THEME_NAME ) . '">', '</a>' ); ?>
			</div>

			<div class="theme-badge">
				<img src="<?php echo BAVOTASAN_THEME_URL; ?>/library/images/screenshot_sml.jpg" />
			</div>

			<h2 class="nav-tab-wrapper">
				<a href="<?php echo admin_url( 'themes.php?page=bavotasan_documentation' ); ?>" class="nav-tab nav-tab-active"><?php _e( 'Documentation', 'destin' ); ?></a>
				<a href="<?php echo admin_url( 'themes.php?page=bavotasan_preview_pro' ); ?>" class="nav-tab"><?php _e( 'Upgrade', 'destin' ); ?></a>
			</h2>

			<div class="changelog headline-feature dfw">
				<h2><?php _e( 'The Customizer', 'destin' ); ?></h2>

				<div class="featured-image dfw-container">
					<img src="<?php echo BAVOTASAN_THEME_URL; ?>/library/images/customizer.jpg" />
				</div>

				<p><?php printf( __( 'All theme options for <em>%s</em> are controlled under %sAppearance &rarr; Customize%s. From there, you can modify layout, colors, custom menus, and more.', 'destin' ), BAVOTASAN_THEME_NAME, '<a href="' . admin_url( 'customize.php' ) . '">', '</a>' ); ?></p>

				<h3><?php _e( 'Site &amp; Main Content Width', 'destin' ); ?></h3>

				<p><?php _e( 'There are two width options for your site: 1200px &amp; 992px. You can select the width of your main content, based on a 12 column grid. Resizing your main content will also resize your sidebar.', 'destin' ); ?></p>

				<h3><?php _e( 'Sidebar Layout', 'destin' ); ?></h3>

				<p><?php printf( __( 'With the sidebar layout option, you can decide whether to display your sidebar(s) on the left or right of your main content.', 'destin' ), '<a href="' . admin_url( 'widgets.php' ) . '">', '</a>' ); ?></p>
			</div>
			<hr />

			<div class="changelog headline-feature dfw">
				<h2><?php _e( 'Custom Menus', 'destin' ); ?></h2>

				<p><?php printf( __( '<em>%s</em> includes one Custom Menu locations: the Primary top menu.', 'destin' ), BAVOTASAN_THEME_NAME ); ?></p>

				<p><?php printf( __( 'To add a navigation menu to the header, go to %sAppearance &rarr; Menus%s. By default, a list of categories will appear in this location if no custom menu is set.', 'destin' ), '<a href="' . admin_url( 'nav-menus.php' ) . '">', '</a>' ); ?></p>

				<p><?php _e( 'Clicking on a top-level link in the primary navigation will open up the first dropdown list of sub-menu links.', 'destin' ); ?></p>
			</div>
			<hr />

			<div class="changelog headline-feature dfw">
				<h2><?php _e( 'Jetpack', 'destin' ); ?></h2>

				<div class="featured-image dfw-container">
					<img src="<?php echo BAVOTASAN_THEME_URL; ?>/library/images/jetpack.jpg" />
				</div>

				<h3><?php _e( 'Infinite Scroll', 'destin' ); ?></h3>

				<p><?php printf( __( '%sJetpack&rsquo;s Infinite Scroll%s allows your visitors to view all your posts without having to click through to the next page. As they scroll, new posts will be added. To activate go to %sJetpack &rarr; Settings%s. ', 'destin' ), '<a href="//jetpack.me/support/infinite-scroll/">', '</a>', '<a href="' . admin_url( 'admin.php?page=jetpack_modules' ) . '">', '</a>' ); ?></p>

				<h3><?php _e( 'Tiled Galleries', 'destin' ); ?></h3>

				<p><?php printf( __( '%sJetpack&rsquo;s Tiled Galleries%s will display your images in a beautiful mosaic layout. Go to %sJetpack &rarr; Settings%s to turn it on. ', 'destin' ), '<a href="//jetpack.me/support/tiled-galleries/">', '</a>', '<a href="' . admin_url( 'admin.php?page=jetpack_modules' ) . '">', '</a>' ); ?></p>

				<h3><?php _e( 'Carousel', 'destin' ); ?></h3>

				<p><?php printf( __( 'With %sJetpack&rsquo;s Carousel%s, clicking on one of your gallery images will open up a featured lightbox slideshow. Turn it on by going to %sJetpack &rarr; Settings%s. ', 'destin' ), '<a href="//jetpack.me/support/carousel/">', '</a>', '<a href="' . admin_url( 'admin.php?page=jetpack_modules' ) . '">', '</a>' ); ?></p>
			</div>
			<hr />

			<div class="changelog headline-feature dfw">
				<h2><?php _e( 'Home Page Post Title', 'destin' ); ?></h2>

				<div class="featured-image dfw-container">
					<img src="<?php echo BAVOTASAN_THEME_URL; ?>/library/images/post-title.jpg" />
				</div>

				<p><?php _e( 'The post title on the home page has a unique design option where you can have any part of the text appear bolded by surrounding it with a SPAN tag.', 'destin' ); ?></p>
			</div>
			<hr />

			<div class="changelog headline-feature dfw">
				<h2><?php _e( 'Pull Quotes', 'destin' ); ?></h2>

				<div class="featured-image dfw-container">
					<img src="<?php echo BAVOTASAN_THEME_URL; ?>/library/images/pull-quotes.jpg" />
				</div>

				<p><?php _e( 'You can easily include a pull quote within your text by using the following code within the Text/HTML editor:', 'destin' ); ?></p>

				<p><code><?php _e( '&lt;blockquote class="pullquote"&gt;This is a pull quote that will appear within your text.&lt;/blockquote&gt;', 'destin' ); ?></code></p>
				<p><?php _e( 'You can also align it to the right with this code:', 'destin' ); ?></p>

				<p><code><?php _e( '&lt;blockquote class="pullquote alignright"&gt;This is a pull quote that will appear within your text.&lt;/blockquote&gt;', 'destin' ); ?></code></p>
			</div>
			<hr />

			<p><?php printf( __( 'For more information, check out the %sDocumentation &amp; FAQs%s section on my site.', 'destin' ), '<a href="//themes.bavotasan.com/documentation/" target="_blank">', '</a>' ); ?></p>
		</div>
		<?php
	}
}
$bavotasan_documentation = new Bavotasan_Documentation;