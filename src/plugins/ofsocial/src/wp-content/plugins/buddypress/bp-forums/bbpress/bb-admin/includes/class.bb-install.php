<?php
/**
 * bbPress Installation class
 *
 * @since 0.9
 */
class BB_Install
{
	/**
	 * The file where the class was instantiated
	 *
	 * @var string
	 */
	var $caller;

	/**
	 * Whether or not we need to load some of the includes normally loaded by bb-settings.php
	 *
	 * @var boolean
	 */
	var $load_includes = false;

	/**
	 * An array of available languages to use in the installer
	 *
	 * @var array
	 */
	var $languages = array( 'en_US' => 'en_US' );

	/**
	 * The currently selected language for the installer
	 *
	 * @var string
	 */
	var $language = 'en_US';

	/**
	 * The current step in the install process
	 *
	 * @var integer
	 */
	var $step;

	/**
	 * Info about config files and their locations
	 *
	 * @var array
	 */
	var $configs = array(
		'writable' => false,
		'bb-config.php' => false,
		'config.php' => false
	);

	/**
	 * An array of the current status of each step
	 *
	 * @var array
	 */
	var $step_status = array(
		0 => 'incomplete',
		1 => 'incomplete',
		2 => 'incomplete',
		3 => 'incomplete',
		4 => 'incomplete'
	);

	/**
	 * An array of most strings in use, including errors
	 *
	 * @var array
	 */
	var $strings = array();

	/**
	 * The data being manipulated as we go through the forms
	 *
	 * @var array
	 */
	var $data = array();

	/**
	 * A boolean that can get flagged to stop posting of a form getting processed
	 *
	 * @var boolean
	 */
	var $stop_process = false;

	/**
	 * Keeps track of where the tabindex is up to
	 *
	 * @var integer
	 */
	var $tabindex = 0;

	/**
	 * Constructor
	 *
	 * Loads everything we might need to do the business
	 *
	 * @param string $caller The full path of the file that instantiated the class
	 * @return boolean Always returns true
	 */
	function BB_Install( $caller )
	{
		$this->caller = $caller;

		$this->set_initial_step();
		$this->define_paths();

		// We need to load these when bb-settings.php isn't loaded
		if ( $this->load_includes ) {
			require_once( BACKPRESS_PATH . 'functions.core.php' );
			require_once( BACKPRESS_PATH . 'functions.compat.php' );
			require_once( BACKPRESS_PATH . 'functions.formatting.php' );
			require_once( BACKPRESS_PATH . 'functions.plugin-api.php' );
			require_once( BACKPRESS_PATH . 'class.wp-error.php' );
			require_once( BB_PATH . BB_INC . 'functions.bb-core.php' );
			require_once( BB_PATH . BB_INC . 'functions.bb-meta.php' );
			require_once( BB_PATH . BB_INC . 'class.bp-options.php' );
			require_once( BACKPRESS_PATH . 'functions.bp-options.php' );
			require_once( BACKPRESS_PATH . 'functions.kses.php' );
			require_once( BB_PATH . BB_INC . 'functions.bb-l10n.php' );
			require_once( BB_PATH . BB_INC . 'functions.bb-template.php' );
		}

		$this->get_languages();
		$this->set_language();

		if ( $this->language ) {
			global $locale;
			global $l10n;
			$locale = $this->language;
			unset( $l10n['default'] );

			if ( !class_exists( 'MO' ) ) {
				require_once( BACKPRESS_PATH . 'pomo/mo.php' );
			}
		}

		// Load the default text localization domain. Doing this twice doesn't hurt too much.
		bb_load_default_textdomain();

		// Pull in locale data after loading text domain.
		if ( $this->load_includes ) {
			require_once( BB_PATH . BB_INC . 'class.bb-locale.php' );
		}
		global $bb_locale;
		$bb_locale = new BB_Locale();

		$this->prepare_strings();
		$this->check_prerequisites();
		$this->check_configs();

		if ( $this->step > -1 ) {
			$this->set_step();
			$this->prepare_data();
			$this->process_form();
		}

		return true;
	}

	/**
	 * Set initial step
	 *
	 * Sets the step from the post data and keeps it within range
	 *
	 * @return integer The calculated step
	 */
	function set_initial_step()
	{
		// Set the step based on the $_POST value or 0
		$this->step = $_POST['step'] ? (integer) $_POST['step'] : 0;

		// Make sure the requested step is from 0-4
		if ( 0 > $this->step || 4 < $this->step ) {
			$this->step = 0;
		}
		return $this->step;
	}

	/**
	 * Prepare text strings
	 *
	 * Sets up many of the strings to be used by the class that may
	 * be later subject to change due to processing of the forms
	 */
	function prepare_strings()
	{
		$this->strings = array(
			-1 => array(
				'title'       => __( 'bbPress &rsaquo; Error' ),
				'h1'          => __( 'Oh dear!' ),
				'messages'    => array()
			),
			0 => array(
				'title'       => sprintf( __( '%1$s &rsaquo; %2$s' ), __( 'bbPress installer' ), __( 'Welcome' ) ),
				'h2'          => __( 'Welcome to the bbPress installer' ),
				'status'      => '',
				'messages'    => array(),
				'intro'       => array(
					__( 'We\'re now going to go through a few steps to get you up and running.' ),
					__( 'Ready? Then let\'s get started!' )
				)
			),
			1 => array(
				'title'       => sprintf( __( '%1$s &rsaquo; %2$s' ), __( 'bbPress installer' ), __( 'Step 1' ) ),
				'h2'          => sprintf( __( '%1$s - %2$s' ), __( 'Step 1' ), __( 'Database configuration' ) ),
				'status'      => '',
				'intro'       => array(
					__( 'In this step you need to enter your database connection details. The installer will attempt to create a file called <code>bb-config.php</code> in the root directory of your bbPress installation.' ),
					__( 'If you\'re not sure what to put here, contact your web hosting provider.' )
				),
				'messages'    => array()
			),
			2 => array(
				'title'       => sprintf( __( '%1$s &rsaquo; %2$s' ), __( 'bbPress installer' ), __( 'Step 2' ) ),
				'h2'          => sprintf( __( '%1$s - %2$s' ), __( 'Step 2' ), __( 'WordPress integration (optional)' ) ),
				'status'      => __( '&laquo; skipped' ),
				'intro'       => array(
					__( 'bbPress can integrate login and user data seamlessly with WordPress. You can safely skip this step if you do not wish to integrate with an existing WordPress install.' )
				),
				'messages'    => array(),
				'form_errors' => array()
			),
			3 => array(
				'title'       => sprintf( __( '%1$s &rsaquo; %2$s' ), __( 'bbPress installer' ), __( 'Step 3' ) ),
				'h2'          => sprintf( __( '%1$s - %2$s' ), __( 'Step 3' ), __( 'Site settings' ) ),
				'status'      => '',
				'intro'       => array(
					__( 'Finalize your installation by adding a name, your first user and your first forum.' )
				),
				'messages'    => array(),
				'form_errors' => array(),
				'scripts'     => array()
			),
			4 => array(
				'title'       => sprintf( __( '%1$s &rsaquo; %2$s' ), __( 'bbPress installer' ), __( 'Finished' ) ),
				'h2'          => __( 'Installation complete!' ),
				'messages'    => array()
			)
		);
	}

	/**
	 * Check installation pre-requisites
	 *
	 * Checks for appropriate PHP extensions.
	 *
	 * @return boolean False if any pre-requisites are not met, otherwise true
	 */
	function check_prerequisites()
	{
		// BPDB wants the MySQL extension - this is also checked when BPDB is initialised so may be a bit redundant here
		if ( !extension_loaded( 'mysql' ) ) {
			$this->strings[-1]['messages']['error'][] = __( 'Your PHP installation appears to be missing the MySQL extension which is required for bbPress' );
			$this->step = -1;
		}

		if ( defined( 'BB_IS_WP_LOADED' ) && BB_IS_WP_LOADED ) {
			$this->strings[-1]['messages']['error'][] = __( 'Please complete your installation before attempting to include WordPress within bbPress' );
			$this->step = -1;
		}

		if ( $this->step === -1 ) {
			return false;
		}

		return true;
	}

	/**
	 * Define path constants
	 *
	 * Sets some bbPress constants if they are not otherwise available based
	 * on the classes initiating file path.
	 *
	 * @return boolean False if no path was supplied, otherwise always true
	 */
	function define_paths()
	{
		if ( !$this->caller ) {
			return false;
		}

		if ( !defined( 'BACKPRESS_PATH' ) ) {
			// Define BACKPRESS_PATH
			// Tell us to load includes because bb-settings.php was not loaded
			// bb-settings.php is generally not loaded on steps -1, 0 and 1 but
			// there are exceptions, so this is safer than just reading the step
			$this->load_includes = true;
			define( 'BACKPRESS_PATH', BB_PATH . BB_INC . 'backpress/' );
		}

		// Define the language file directory
		if ( !defined( 'BB_LANG_DIR' ) ) {
			define( 'BB_LANG_DIR', BB_PATH . 'my-languages/' );
		}

		return true;
	}

	/**
	 * Gets an array of available languages form the language directory
	 *
	 * @return array
	 */
	function get_languages()
	{
		foreach ( bb_glob( BB_LANG_DIR . '*.mo' ) as $language ) {
			if ( substr( $language, 0, 18 ) === 'continents-cities-' ) {
				continue;
			}
			$language = str_replace( '.mo', '', basename( $language ) );
			$this->languages[$language] = $language;
		}
		
		return $this->languages;
	}

	/**
	 * Returns a language selector for switching installation languages
	 *
	 * @return string|false Either the html for the selector or false if there are no languages
	 */
	function get_language_selector()
	{
		// Don't provide a selection if there is only english
		if ( 2 > count( $this->languages ) ) {
			return false;
		}

		$r = '<script type="text/javascript" charset="utf-8">' . "\n";
		$r .= "\t" . 'function changeLanguage(selectObj) {' . "\n";
		$r .= "\t\t" . 'var selectedLanguage = selectObj.options[selectObj.selectedIndex].value;' . "\n";
		$r .= "\t\t" . 'location.href = "install.php?language=" + selectedLanguage;' . "\n";
		$r .= "\t" . '}' . "\n";
		$r .= '</script>' . "\n";
		//$r .= '<form id="lang" action="install.php">' . "\n";
		$r .= "\t" . '<fieldset>' . "\n";
		$r .= "\t\t" . '<label class="has-note has-label for-select">' . "\n";
		$r .= "\t\t\t" . '<span>' . __( 'Installation language' ) . '</span>' . "\n";
		$this->tabindex++;
		$r .= "\t\t\t" . '<select class="has-note" onchange="changeLanguage(this);" name="language" tabindex="' . $this->tabindex . '">' . "\n";
		foreach ( $this->languages as $language ) {
			$selected = '';
			if ( $language == $this->language ) {
				$selected = ' selected="selected"';
			}
			$r .= "\t\t\t\t" . '<option value="' . $language . '"' . $selected . '>' . $language . '</option>' . "\n";
		}
		$r .= "\t\t\t" . '</select>' . "\n";
		$r .= "\t\t\t" . '<a class="note-toggle" href="javascript:void(0);" onclick="toggleNote(\'note-language\');">?</a>' . "\n";
		$r .= "\t\t\t" . '<p id="note-language" class="note" style="display:none">' . __( 'Sets the language to be used during the installation process only.' ) . '</p>' . "\n";
		$r .= "\t\t\t" . '<div class="clear"></div>' . "\n";
		$r .= "\t\t" . '</label>' . "\n";
		$r .= "\t" . '</fieldset>' . "\n";
		//$r .= '</form>' . "\n";

		echo $r;
	}

	/**
	 * Sets the current installation language
	 *
	 * @return string The currently set language
	 */
	function set_language()
	{
		if ( isset( $_COOKIE['bb_install_language'] ) && 1 < count( $this->languages ) ) {
			if ( in_array( $_COOKIE['bb_install_language'], $this->languages ) ) {
				$this->language = $_COOKIE['bb_install_language'];
			}
		}

		$language_requested = false;
		if ( isset( $_POST['language'] ) && $_POST['language'] ) {
			$language_requested = (string) $_POST['language'];
		} elseif ( isset( $_GET['language'] ) && $_GET['language'] ) {
			$language_requested = (string) $_GET['language'];
		}

		if ( $language_requested && 1 < count( $this->languages ) ) {
			if ( in_array( $language_requested, $this->languages ) ) {
				$this->language = $language_requested;
				setcookie( 'bb_install_language', $this->language );
			}
		}

		if ( !$this->language || 'en_US' === $this->language ) {
			$this->language = 'en_US';
			setcookie( 'bb_install_language', ' ', time() - 31536000 );
		}

		return $this->language;
	}

	/**
	 * Tests whether database tables exist
	 *
	 * Checks for the existence of the forum table in the database that is
	 * currently configured.
	 *
	 * @return boolean False if the table isn't found, otherwise true
	 */
	function database_tables_are_installed()
	{
		global $bbdb;
		$bbdb->suppress_errors();
		$installed = (boolean) $bbdb->get_results( 'DESCRIBE `' . $bbdb->forums . '`;', ARRAY_A );
		$bbdb->suppress_errors( false );
		return $installed;
	}

	/**
	 * Tests whether an option is set in the database
	 *
	 * @return boolean False if the option isn't set, otherwise true
	 */
	function bb_options_are_set()
	{
		if ( $this->load_includes ) {
			return false;
		}
		if ( !bb_get_option( 'uri' ) ) {
			return false;
		}
		return true;
	}

	/**
	 * Tests whether bbPress is installed
	 *
	 * @return boolean False if bbPress isn't installed, otherwise true
	 */
	function is_installed()
	{
		if ( !$this->database_tables_are_installed() ) {
			return false;
		}
		if ( !$this->bb_options_are_set() ) {
			return false;
		}
		return true;
	}

	/**
	 * Checks for configs and sets variables describing current install state
	 *
	 * @return integer The current step we should be on based on the existence of the config file
	 */
	function check_configs()
	{
		// Check for a config file
		if ( file_exists( BB_PATH . 'bb-config.php' ) ) {
			$this->configs['bb-config.php'] = BB_PATH . 'bb-config.php';
		} elseif ( file_exists( dirname( BB_PATH ) . '/bb-config.php' ) ) {
			$this->configs['bb-config.php'] = dirname( BB_PATH ) . '/bb-config.php';
		}

		// Check for an old config file
		if ( file_exists( BB_PATH . 'config.php' ) ) {
			$this->configs['config.php'] = BB_PATH . 'config.php';
		} elseif ( file_exists( dirname( BB_PATH ) . '/config.php' ) ) {
			$this->configs['config.php'] = dirname( BB_PATH ) . '/config.php';
		}

		if ( $this->configs['config.php'] && !$this->configs['bb-config.php'] ) {
			// There is an old school config file
			// Step -1 is where we send fatal errors from any screen
			$this->strings[-1]['messages']['error'][] = __( 'An old <code>config.php</code> file has been detected in your installation. You should remove it and run the <a href="install.php">installer</a> again. You can use the same database connection details if you do.' );
			$this->step = -1;
			return $this->step;
		}

		// Check if bbPress is already installed
		// Is there a current config file
		if ( $this->configs['bb-config.php'] ) {

			// Is it valid
			if ( $this->validate_current_config() ) {
				// Step 1 is complete
				$this->step_status[1] = 'complete';
				$this->strings[1]['status'] = __( '&laquo; completed' );

				// On step 1 we want to report that the file is good and allow the user to continue
				if ( $this->step === 1 ) {
					// Stop form processing if it is posted
					$this->stop_process = true;

					// Display a nice message saying the config file exists
					$this->strings[1]['messages']['message'][] = __( 'A valid configuration file was found at <code>bb-config.php</code><br />You may continue to the next step.' );
					return $this->step;
				}
			} else {
				// Invalid config files on any step cause us to exit to step 0
				$this->strings[-1]['messages']['error'][] = __( 'An invalid configuration file was found at <code>bb-config.php</code><br />The installation cannot continue.' );
				$this->strings[-1]['messages'][0][] = __( 'Usually this is caused by one of the database connection settings being incorrect. Make sure that the specified user has appropriate permission to access the database.' );
				$this->step = -1;
			}

			// If we have made it this far, then we can check if the database tables exist and have content
			if ( $this->is_installed() ) {
				// The database is installed
				// The following standard functions should be available
				if ( bb_get_option( 'bb_db_version' ) > bb_get_option_from_db( 'bb_db_version' ) ) {
					// The database needs upgrading
					$this->strings[-1]['messages'][0][] = __( 'bbPress is already installed, but appears to require an upgrade.' );
				} else {
					$this->strings[-1]['messages'][0][] = __( 'bbPress is already installed.' );
				}
				$this->strings[-1]['messages'][0][] = sprintf(
					__( 'Perhaps you meant to run the <a href="%s">upgrade script</a> instead?' ),
					bb_get_uri( 'bb-admin/upgrade.php', null, BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN )
				);
				$this->step = -1;
			}

		} else {

			if ( 2 > $this->step && !file_exists( BB_PATH . 'bb-config-sample.php' ) ) {
				// There is no sample config file
				$this->strings[0]['messages']['error'][] = __( 'I could not find the file <code>bb-config-sample.php</code><br />Please upload it to the root directory of your bbPress installation.' );
				$this->step = 0;
			}

			if ( 1 !== $this->step ) {
				// There is no config file, go back to the beginning
				$this->strings[0]['messages']['message'][] = __( 'There doesn\'t seem to be a <code>bb-config.php</code> file. This usually means that you want to install bbPress.' );
				$this->step = 0;
			}

		}

		// Check if the config file path is writable
		if ( file_exists( $this->configs['bb-config.php'] ) ) {
			if ( is_writable( $this->configs['bb-config.php'] ) ) {
				$this->configs['writable'] = true;
			}
		} elseif ( is_writable( BB_PATH ) ) {
			$this->configs['writable'] = true;
		}

		return $this->step;
	}

	/**
	 * Determines if the current config is valid
	 *
	 * @return boolean False if the config is bad, otherwise true
	 */
	function validate_current_config()
	{
		// If we are validating then the config file has already been included
		// So we can just check for valid constants

		// The required constants for a valid config file
		$required_constants = array(
			'BBDB_NAME',
			'BBDB_USER',
			'BBDB_PASSWORD',
			'BBDB_HOST'
		);

		// Check the required constants are defined
		foreach ( $required_constants as $required_constant ) {
			if ( !defined( $required_constant ) ) {
				return false;
			}
		}

		global $bb_table_prefix;

		if ( !isset( $bb_table_prefix ) ) {
			return false;
		}

		// Everthing is OK so far, validate the connection as well
		return $this->validate_current_database();
	}

	/**
	 * Validates the current database settings
	 *
	 * @return boolean False if the current database isn't valid, otherwise true
	 */
	function validate_current_database()
	{
		global $bbdb;
		$db = $bbdb->db_connect( "SELECT * FROM $bbdb->forums LIMIT 1" );

		if ( !is_resource( $db ) ) {
			return false;
		}

		return true;
	}

	/**
	 * Sets up default values for input data as well as labels and notes
	 *
	 * @return void
	 */
	function prepare_data()
	{
		/**
		 * Should be exactly the same as the default value of the KEYS in bb-config-sample.php
		 * @since 1.0
		 */
		$_bb_default_secret_key = 'put your unique phrase here';

		$this->data = array(
			0 => array(
				'form' => array(
					'forward_0_0' => array(
						'value' => __( 'Go to step 1' )
					)
				)
			),
			1 => array(
				'form' => array(
					'bbdb_name' => array(
						'value' => '',
						'label' => __( 'Database name' ),
						'note'  => __( 'The name of the database in which you want to run bbPress.' )
					),
					'bbdb_user' => array(
						'value' => '',
						'label' => __( 'Database user' ),
						'note'  => __( 'The database user that has access to that database.' ),
						'autocomplete' => 'off'
					),
					'bbdb_password' => array(
						'type'  => 'password',
						'value' => '',
						'label' => __( 'Database password' ),
						'note'  => __( 'That database user\'s password.' ),
						'autocomplete' => 'off'
					),
					'bb_lang' => array(
						'value' => '',
						'label' => __( 'Language' ),
						'note' => sprintf( __( 'The language which bbPress will be presented in once installed. Your current installer language choice (%s) will be the same for the rest of the install process.' ), $this->language )
					),
					'toggle_1' => array(
						'value'   => 0,
						'label'   => __( 'Show advanced settings' ),
						'note'    => __( 'These settings usually do not have to be changed.' ),
						'checked' => '',
						'display' => 'none'
					),
					'bbdb_host'        => array(
						'value'        => 'localhost',
						'label'        => __( 'Database host' ),
						'note'         => __( 'The domain name or IP address of the server where the database is located. If the database is on the same server as the web site, then this probably should remain <strong>localhost</strong>.' ),
						'prerequisite' => 'toggle_1'
					),
					'bbdb_charset' => array(
						'value'        => 'utf8',
						'label'        => __( 'Database character set' ),
						'note'         => __( 'The best choice is <strong>utf8</strong>, but you will need to match the character set which you created the database with.' ),
						'prerequisite' => 'toggle_1'
					),
					'bbdb_collate' => array(
						'value'        => '',
						'label'        => __( 'Database character collation' ),
						'note'         => __( 'The character collation value set when the database was created.' ),
						'prerequisite' => 'toggle_1'
					),
					/*
					'bb_auth_key' => array(
						'value'        => $_bb_default_secret_key,
						'label'        => __( 'bbPress "auth" cookie key' ),
						'note'         => __( 'This should be a unique and secret phrase, it will be used to make your bbPress "auth" cookie unique and harder for an attacker to decipher.' ),
						'prerequisite' => 'toggle_1'
					),
					'bb_secure_auth_key' => array(
						'value'        => $_bb_default_secret_key,
						'label'        => __( 'bbPress "secure auth" cookie key' ),
						'note'         => __( 'This should be a unique and secret phrase, it will be used to make your bbPress "secure auth" cookie unique and harder for an attacker to decipher.' ),
						'prerequisite' => 'toggle_1'
					),
					'bb_logged_in_key' => array(
						'value'        => $_bb_default_secret_key,
						'label'        => __( 'bbPress "logged in" cookie key' ),
						'note'         => __( 'This should be a unique and secret phrase, it will be used to make your bbPress "logged in" cookie unique and harder for an attacker to decipher.' ),
						'prerequisite' => 'toggle_1'
					),
					'bb_nonce_key' => array(
						'value'        => $_bb_default_secret_key,
						'label'        => __( 'bbPress "nonce" key' ),
						'note'         => __( 'This should be a unique and secret phrase, it will be used to make form submission harder for an attacker to spoof.' ),
						'prerequisite' => 'toggle_1'
					),
					*/
					'bb_table_prefix' => array(
						'value'        => 'bb_',
						'label'        => __( 'Table name prefix' ),
						'note'         => __( 'If you are running multiple bbPress sites in a single database, you will probably want to change this.' ),
						'prerequisite' => 'toggle_1'
					),
					'config' => array(
						'value' => '',
						'label' => __( 'Contents for <code>bb-config.php</code>' ),
						'note'  => __( 'Once you have created the configuration file, you can check for it below.' )
					),
					'forward_1_0' => array(
						'value' => __( 'Save database configuration file' )
					),
					'back_1_1' => array(
						'value' => __( '&laquo; Go back' )
					),
					'forward_1_1' => array(
						'value' => __( 'Check for configuration file' )
					),
					'forward_1_2' => array(
						'value' => __( 'Go to step 2' )
					)
				)
			),

			2 => array(
				'form' => array(
					'toggle_2_0' => array(
						'value'        => 0,
						'label'        => __( 'Add integration settings' ),
						'note'         => __( 'If you want to integrate bbPress with an existing WordPress site.' ),
						'checked'      => '',
						'display'      => 'none',
						'toggle_value' => array(
							'target'    => 'forward_2_0',
							'off_value' => __( 'Skip WordPress integration' ),
							'on_value'  => __( 'Save WordPress integration settings' )
						)
					),
					'toggle_2_1' => array(
						'value'   => 0,
						'label'   => __( 'Add cookie integration settings' ),
						'note'    => __( 'If you want to allow shared logins with an existing WordPress site.' ),
						'checked' => '',
						'display' => 'none',
						'prerequisite' => 'toggle_2_0'
					),
					'wp_siteurl' => array(
						'value' => '',
						'label' => __( 'WordPress address (URL)' ),
						'note'  => __( 'This value should exactly match the <strong>WordPress address (URL)</strong> setting in your WordPress general settings.' ),
						'prerequisite' => 'toggle_2_1'
					),
					'wp_home' => array(
						'value' => '',
						'label' => __( 'Blog address (URL)' ),
						'note'  => __( 'This value should exactly match the <strong>Blog address (URL)</strong> setting in your WordPress general settings.' ),
						'prerequisite' => 'toggle_2_1'
					),
					'wp_auth_key' => array(
						'value' => '',
						'label' => __( 'WordPress "auth" cookie key' ),
						'note'  => __( 'This value must match the value of the constant named "AUTH_KEY" in your WordPress <code>wp-config.php</code> file. This will replace the bbPress "auth" cookie key set in the first step.' ),
						'prerequisite' => 'toggle_2_1',
						'autocomplete' => 'off'
					),
					'wp_auth_salt' => array(
						'value' => '',
						'label' => __( 'WordPress "auth" cookie salt' ),
						'note'  => __( 'This must match the value of the WordPress setting named "auth_salt" in your WordPress site. Look for the option labeled "auth_salt" in <a href="#" id="getAuthSaltOption" onclick="window.open(this.href); return false;">this WordPress admin page</a>. If you leave this blank the installer will try to fetch the value based on your WordPress database integration settings.' ),
						'prerequisite' => 'toggle_2_1',
						'autocomplete' => 'off'
					),
					'wp_secure_auth_key' => array(
						'value' => '',
						'label' => __( 'WordPress "secure auth" cookie key' ),
						'note'  => __( 'This value must match the value of the constant named "SECURE_AUTH_KEY" in your WordPress <code>wp-config.php</code> file. This will replace the bbPress "secure auth" cookie key set in the first step.' ),
						'prerequisite' => 'toggle_2_1',
						'autocomplete' => 'off'
					),
					'wp_secure_auth_salt' => array(
						'value' => '',
						'label' => __( 'WordPress "secure auth" cookie salt' ),
						'note'  => __( 'This must match the value of the WordPress setting named "secure_auth_salt" in your WordPress site. Look for the option labeled "secure_auth_salt" in <a href="#" id="getSecureAuthSaltOption" onclick="window.open(this.href); return false;">this WordPress admin page</a>. If you leave this blank the installer will try to fetch the value based on your WordPress database integration settings. Sometimes this value is not set in WordPress, in that case you can leave this setting blank as well.' ),
						'prerequisite' => 'toggle_2_1',
						'autocomplete' => 'off'
					),
					'wp_logged_in_key' => array(
						'value' => '',
						'label' => __( 'WordPress "logged in" cookie key' ),
						'note'  => __( 'This value must match the value of the constant named "LOGGED_IN_KEY" in your WordPress <code>wp-config.php</code> file. This will replace the bbPress "logged in" cookie key set in the first step.' ),
						'prerequisite' => 'toggle_2_1',
						'autocomplete' => 'off'
					),
					'wp_logged_in_salt' => array(
						'value' => '',
						'label' => __( 'WordPress "logged in" cookie salt' ),
						'note'  => __( 'This must match the value of the WordPress setting named "logged_in_salt" in your WordPress site. Look for the option labeled "logged_in_salt" in <a href="#" id="getLoggedInSaltOption" onclick="window.open(this.href); return false;">this WordPress admin page</a>. If you leave this blank the installer will try to fetch the value based on your WordPress database integration settings.' ),
						'prerequisite' => 'toggle_2_1',
						'autocomplete' => 'off'
					),
					'toggle_2_2' => array(
						'value'   => 0,
						'label'   => __( 'Add user database integration settings' ),
						'note'    => __( 'If you want to share user data with an existing WordPress site.' ),
						'checked' => '',
						'display' => 'none',
						'prerequisite' => 'toggle_2_0'
					),
					'wp_table_prefix' => array(
						'value' => 'wp_',
						'default_value' => '', // Used when setting is ignored
						'label' => __( 'User database table prefix' ),
						'note'  => __( 'If your bbPress and WordPress sites share the same database, then this is the same value as <code>$table_prefix</code> in your WordPress <code>wp-config.php</code> file. It is usually <strong>wp_</strong>.' ),
						'prerequisite' => 'toggle_2_2'
					),
					'wordpress_mu_primary_blog_id' => array(
						'value' => '',
						'default_value' => '',
						'label' => __( 'WordPress MU primary blog ID' ),
						'note'  => __( 'If you are integrating with a WordPress MU site you need to specify the primary blog ID for that site. It is usually <strong>1</strong>. You should probably leave this blank if you are integrating with a standard WordPress site' ),
						'prerequisite' => 'toggle_2_2'
					),
					'toggle_2_3' => array(
						'value'   => 0,
						'label'   => __( 'Show advanced database settings' ),
						'note'    => __( 'If your bbPress and WordPress site do not share the same database, then you will need to add advanced settings.' ),
						'checked' => '',
						'display' => 'none',
						'prerequisite' => 'toggle_2_2'
					),
					'user_bbdb_name' => array(
						'value' => '',
						'label' => __( 'User database name' ),
						'note'  => __( 'The name of the database in which your user tables reside.' ),
						'prerequisite' => 'toggle_2_3'
					),
					'user_bbdb_user' => array(
						'value' => '',
						'label' => __( 'User database user' ),
						'note'  => __( 'The database user that has access to that database.' ),
						'prerequisite' => 'toggle_2_3',
						'autocomplete' => 'off'
					),
					'user_bbdb_password' => array(
						'type'  => 'password',
						'value' => '',
						'label' => __( 'User database password' ),
						'note'  => __( 'That database user\'s password.' ),
						'prerequisite' => 'toggle_2_3',
						'autocomplete' => 'off'
					),
					'user_bbdb_host' => array(
						'value' => '',
						'label' => __( 'User database host' ),
						'note'  => __( 'The domain name or IP address of the server where the database is located. If the database is on the same server as the web site, then this probably should be <strong>localhost</strong>.' ),
						'prerequisite' => 'toggle_2_3'
					),
					'user_bbdb_charset' => array(
						'value' => '',
						'label' => __( 'User database character set' ),
						'note'  => __( 'The best choice is <strong>utf8</strong>, but you will need to match the character set which you created the database with.' ),
						'prerequisite' => 'toggle_2_3'
					),
					'user_bbdb_collate' => array(
						'value' => '',
						'label' => __( 'User database character collation' ),
						'note'  => __( 'The character collation value set when the user database was created.' ),
						'prerequisite' => 'toggle_2_3'
					),
					'custom_user_table' => array(
						'value' => '',
						'label' => __( 'User database "user" table' ),
						'note'  => __( 'The complete table name, including any prefix.' ),
						'prerequisite' => 'toggle_2_3'
					),
					'custom_user_meta_table' => array(
						'value' => '',
						'label' => __( 'User database "user meta" table' ),
						'note'  => __( 'The complete table name, including any prefix.' ),
						'prerequisite' => 'toggle_2_3'
					),
					'forward_2_0' => array(
						'value' => __( 'Skip WordPress integration' )
					),
					'back_2_1' => array(
						'value' => __( '&laquo; Go back' )
					),
					'forward_2_1' => array(
						'value' => __( 'Go to step 3' )
					)
				)
			),

			3 => array(
				'form' => array(
					'name' => array(
						'value' => '',
						'label' => __( 'Site name' ),
						'note'  => __( 'This is what you are going to call your bbPress site.' )
					),
					'uri' => array(
						'value' => $this->guess_uri(),
						'label' => __( 'Site address (URL)' ),
						'note'  => __( 'We have attempted to guess this; it\'s usually correct, but change it here if you wish.' )
					),
					'keymaster_user_login' => array(
						'value'     => '',
						'maxlength' => 60,
						'label'     => __( '"Key Master" Username' ),
						'note'      => __( 'This is the user login for the initial bbPress administrator (known as a "Key Master").' ),
						'autocomplete' => 'off'
					),
					'keymaster_user_email' => array(
						'value'     => '',
						'maxlength' => 100,
						'label'     => __( '"Key Master" Email address' ),
						'note'      => __( 'The login details will be emailed to this address.' ),
						'autocomplete' => 'off'
					),
					'keymaster_user_type' => array(
						'value' => 'new'
					),
					'forum_name' => array(
						'value'     => '',
						'maxlength' => 150,
						'label'     => __( 'First forum name' ),
						'note'      => __( 'This can be changed after installation, so don\'t worry about it too much.' )
					),
					'forward_3_0' => array(
						'value' => __( 'Save site settings' )
					),
					'back_3_1' => array(
						'value' => __( '&laquo; Go back' )
					),
					'forward_3_1' => array(
						'value' => __( 'Complete the installation' )
					)
				)
			),

			4 => array(
				'form' => array(
					'toggle_4' => array(
						'value' => 0,
						'label' => __( 'Show installation messages' )
					),
					'error_log' => array(
						'value' => '',
						'label' => __( 'Installation errors' )
					),
					'installation_log' => array(
						'value' => '',
						'label' => __( 'Installation log' )
					)
				)
			)
		);
	}

	/**
	 * Guesses the final installed URI based on the location of the install script
	 *
	 * @return string The guessed URI
	 */
	function guess_uri()
	{
		global $bb;

		if ( $bb->uri ) {
			$uri = $bb->uri;
		} else {
			$schema = 'http://';
			if ( isset( $_SERVER['HTTPS'] ) && strtolower( $_SERVER['HTTPS'] ) == 'on' ) {
				$schema = 'https://';
			}
			$uri = preg_replace( '|/bb-admin/.*|i', '/', $schema . $_SERVER['HTTP_HOST'] . $_SERVER['REQUEST_URI'] );
		}

		return rtrim( $uri, " \t\n\r\0\x0B/" ) . '/';
	}

	/**
	 * Writes the given alterations to file
	 *
	 * @param $file_source string The full path to the file to be read from
	 * @param $file_target string The full path to the file to be written to
	 * @param $alterations array An array of arrays containing alterations to be made
	 * @return void
	 */
	function write_lines_to_file( $file_source, $file_target, $alterations )
	{
		if ( !$file_source || !file_exists( $file_source ) || !is_file( $file_source ) ) {
			return -1;
		}

		if ( !$file_target ) {
			$file_target = $file_source;
		}

		if ( !$alterations || !is_array( $alterations ) ) {
			return -2;
		}

		/*
		Alterations array takes the form
		array(
			'1st 20 chars of line' => array( 'Search string', 'Replacement string' ),
			'1st 20 chars of line' => array( 'Search string', 'Replacement string' )
		);
		*/

		// Get the existing lines in the file
		$lines = file( $file_source );

		// Initialise an array to store the modified lines
		$modified_lines = array();

		// Loop through the lines and modify them
		foreach ( $lines as $line ) {
			if ( isset( $alterations[substr( $line, 0, 20 )] ) ) {
				$alteration = $alterations[substr( $line, 0, 20 )];
				$modified_lines[] = str_replace( $alteration[0], $alteration[1], $line );
			} else {
				$modified_lines[] = $line;
			}
		}

		$writable = true;
		if ( file_exists( $file_target ) ) {
			if ( !is_writable( $file_target ) ) {
				$writable = false;
			}
		} else {
			$dir_target = dirname( $file_target );
			if ( file_exists( $dir_target ) ) {
				if ( !is_writable( $dir_target ) || !is_dir( $dir_target ) ) {
					$writable = false;
				}
			} else {
				$writable = false;
			}
		}

		if ( !$writable ) {
			return trim( join( null, $modified_lines ) );
		}

		// Open the file for writing - rewrites the whole file
		$file_handle = fopen( $file_target, 'w' );

		// Write lines one by one to avoid OS specific newline hassles
		foreach ( $modified_lines as $modified_line ) {
			if ( false !== strpos( $modified_line, '?>' ) ) {
				$modified_line = '?>';
			}
			fwrite( $file_handle, $modified_line );
			if ( $modified_line == '?>' ) {
				break;
			}
		}

		// Close the config file
		fclose( $file_handle );

		@chmod( $file_target, 0666 );

		return 1;
	}

	/**
	 * Reports whether the request method is post or not
	 *
	 * @return boolean True if the page was posted, otherwise false
	 */
	function is_posted()
	{
		if ( 'post' === strtolower( $_SERVER['REQUEST_METHOD'] ) ) {
			return true;
		}
		return false;
	}

	/**
	 * Determines which step the installer is on based on user input
	 *
	 * @return boolean Always returns true
	 **/
	function set_step()
	{
		if ( $this->is_posted() ) {
			switch ( $this->step ) {
				case 1:
					$this->set_language();
					if ( $_POST['forward_0_0'] ) {
						$this->stop_process = 1;
					}
					break;

				case 2:
					if ( $_POST['forward_1_2'] ) {
						$this->stop_process = 1;
					}
					break;

				case 3:
					// If this is actually a request to go back to step 2
					if ( $_POST['back_2_1'] ) {
						$this->step = 2;
						break;
					}

					// If we have come forward from step 2 then don't process form 3
					if ( $_POST['forward_2_1'] ) {
						$this->stop_process = true;
					}

					// Determine what the status of the previous step was based on input
					if ( $_POST['toggle_2_0'] ) {
						$this->strings[2]['status'] = __( '&laquo; completed' );
						$this->step_status[2] = 'complete';
					}
					break;

				case 4:
					// Determine what the status of the previous step was based on input
					if ( $_POST['toggle_2_0'] ) {
						$this->strings[2]['status'] = __( '&laquo; completed' );
						$this->step_status[2] = 'complete';
					}

					// If this is actually a request to go back to step 3
					if ( $_POST['back_3_1'] ) {
						$this->step = 3;
						break;
					}

					// We have to have come forward from step 3
					if ( $_POST['forward_3_1'] ) {
						$this->strings[3]['status'] = __( '&laquo; completed' );
						$this->step_status[3] = 'complete';
					} else {
						$this->step = 2;
					}
					break;
			}
		}
		return true;
	}

	/**
	 * Sanitizes all data stored in the data array
	 *
	 * @return boolean Always returns true
	 **/
	function sanitize_form_data()
	{
		foreach ( $this->data as $step => $data ) {
			if ( isset( $data['form'] ) && is_array( $data['form'] ) ) {
				foreach ( $data['form'] as $key => $value ) {
					$this->data[$step]['form'][$key]['value'] = esc_attr( $value['value'] );
				}
			}
		}
		return true;
	}

	/**
	 * Directs processing of the form data based on the current step
	 *
	 * @return boolean Always returns true
	 **/
	function process_form()
	{
		if ( $this->is_posted() && !$this->stop_process ) {
			switch ( $this->step ) {
				case 1:
					$this->process_form_config_file();
					break;

				case 2:
					$this->process_form_wordpress_integration();
					break;

				case 3:
					$this->process_form_site_options();
					break;

				case 4:
					$this->process_form_finalise_installation();
					break;
			}
		}
		return true;
	}

	/**
	 * Takes inputted form data and injects it into the data array
	 *
	 * @param integer $step Which steps data to process
	 * @return boolean Always returns true
	 **/
	function inject_form_values_into_data( $step )
	{
		$data =& $this->data[$step]['form'];

		foreach ( $data as $key => $value ) {
			if ( 'forward_' !== substr( $key, 0, 8 ) && 'back_' !== substr( $key, 0, 5 ) ) {
				if ( isset( $data[$key]['prerequisite'] ) && !$_POST[$data[$key]['prerequisite']] ) {
					if ( isset( $data[$key]['default_value'] ) ) {
						$data[$key]['value'] = $data[$key]['default_value'];
					}
					// do nothing - keep the default value
				} else {
					$data[$key]['value'] = stripslashes_deep( trim( $_POST[$key] ) );
				}
			}
		}

		return true;
	}

	/**
	 * Validates the supplied config file data and writes it to the config file.
	 *
	 * @return void
	 **/
	function process_form_config_file()
	{
		$this->inject_form_values_into_data( 1 );

		$data =& $this->data[1]['form'];
		
		if ( 'en_US' == $data['bb_lang']['value'] ) {
			$data['bb_lang']['value'] = '';
		}

		if ( $data['toggle_1']['value'] ) {
			$data['toggle_1']['checked'] = 'checked="checked"';
			$data['toggle_1']['display'] = 'block';

			// Deal with slashes in the keys
			//$data['bb_auth_key']['value']        = addslashes( stripslashes( $data['bb_auth_key']['value'] ) );
			//$data['bb_secure_auth_key']['value'] = addslashes( stripslashes( $data['bb_secure_auth_key']['value'] ) );
			//$data['bb_logged_in_key']['value']   = addslashes( stripslashes( $data['bb_logged_in_key']['value'] ) );
			//$data['bb_nonce_key']['value']       = addslashes( stripslashes( $data['bb_nonce_key']['value'] ) );
		}

		$requested_prefix = $data['bb_table_prefix']['value'];
		$data['bb_table_prefix']['value'] = preg_replace( '/[^0-9a-zA-Z_]/', '', $data['bb_table_prefix']['value'] );
		if ( $requested_prefix !== $data['bb_table_prefix']['value'] ) {
			$data['toggle_1']['checked'] = 'checked="checked"';
			$data['toggle_1']['display'] = 'block';
			$this->step_status[1] = 'incomplete';
			$this->strings[1]['messages']['error'][] = __( 'The table prefix can only contain letters, numbers and underscores.<br />Please review the suggestion below.' );
			$this->strings[1]['form_errors']['bb_table_prefix'][] = __( '&bull; Based on your input the following prefix is suggested.' );
			return 'incomplete';
		}
		if ( empty( $data['bb_table_prefix']['value'] ) ) {
			$data['bb_table_prefix']['value'] = 'bb_';
			$data['toggle_1']['checked'] = 'checked="checked"';
			$data['toggle_1']['display'] = 'block';
			$this->step_status[1] = 'incomplete';
			$this->strings[1]['messages']['error'][] = __( 'The table prefix can not be blank.<br />Please review the suggestion below.' );
			$this->strings[1]['form_errors']['bb_table_prefix'][] = __( '&bull; The default prefix has been inserted.' );
			return 'incomplete';
		}

		// Stop here if we are going backwards
		if ( $_POST['back_1_1'] ) {
			$this->step_status[1] = 'incomplete';
			return 'incomplete';
		}

		// Test the db connection.
		define( 'BBDB_NAME',     $data['bbdb_name']['value'] );
		define( 'BBDB_USER',     $data['bbdb_user']['value'] );
		define( 'BBDB_PASSWORD', $data['bbdb_password']['value'] );
		define( 'BBDB_HOST',     $data['bbdb_host']['value'] );
		define( 'BBDB_CHARSET',  $data['bbdb_charset']['value'] );
		define( 'BBDB_COLLATE',  $data['bbdb_collate']['value'] );

		// We'll fail here if the values are no good.
		require_once( BACKPRESS_PATH . 'class.bpdb-multi.php' );

		$bbdb =& new BPDB_Multi( array(
			'name'     => BBDB_NAME,
			'user'     => BBDB_USER,
			'password' => BBDB_PASSWORD,
			'host'     => BBDB_HOST,
			'charset'  => defined( 'BBDB_CHARSET' ) ? BBDB_CHARSET : false,
			'collate'  => defined( 'BBDB_COLLATE' ) ? BBDB_COLLATE : false,
			'errors'   => 'suppress'
		) );

		if ( !$bbdb->db_connect( 'SHOW TABLES;' ) ) {
			$bbdb->suppress_errors( false );
			$this->step_status[1] = 'incomplete';
			$this->strings[1]['messages']['error'][] = __( 'There was a problem connecting to the database you specified.<br />Please check the settings, then try again.' );
			return 'error';
		}
		$bbdb->suppress_errors( false );

		$config_result = $this->write_lines_to_file(
			BB_PATH . 'bb-config-sample.php',
			BB_PATH . 'bb-config.php',
			array(
				"define( 'BBDB_NAME',"  => array( "'bbpress'",                     "'" . $data['bbdb_name']['value'] . "'" ),
				"define( 'BBDB_USER',"  => array( "'username'",                    "'" . $data['bbdb_user']['value'] . "'" ),
				"define( 'BBDB_PASSWO"  => array( "'password'",                    "'" . $data['bbdb_password']['value'] . "'" ),
				"define( 'BBDB_HOST',"  => array( "'localhost'",                   "'" . $data['bbdb_host']['value'] . "'" ),
				"define( 'BBDB_CHARSE"  => array( "'utf8'",                        "'" . $data['bbdb_charset']['value'] . "'" ),
				"define( 'BBDB_COLLAT"  => array( "''",                            "'" . $data['bbdb_collate']['value'] . "'" ),
				//"define( 'BB_AUTH_KEY"  => array( "'put your unique phrase here'", "'" . $data['bb_auth_key']['value'] . "'" ),
				//"define( 'BB_SECURE_A"  => array( "'put your unique phrase here'", "'" . $data['bb_secure_auth_key']['value'] . "'" ),
				//"define( 'BB_LOGGED_I"  => array( "'put your unique phrase here'", "'" . $data['bb_logged_in_key']['value'] . "'" ),
				//"define( 'BB_NONCE_KE"  => array( "'put your unique phrase here'", "'" . $data['bb_nonce_key']['value'] . "'" ),
				"\$bb_table_prefix = '" => array( "'bb_'",                         "'" . $data['bb_table_prefix']['value'] . "'" ),
				"define( 'BB_LANG', '"  => array( "''",                            "'" . $data['bb_lang']['value'] . "'" )
			)
		);

		switch ( $config_result ) {
			case -1:
				$this->step_status[1] = 'error';
				$this->strings[1]['messages']['error'][] = __( 'I could not find the file <code>bb-config-sample.php</code><br />Please upload it to the root directory of your bbPress installation.' );
				return 'error';
				break;
			case 1:
				$this->configs['bb-config.php'] = BB_PATH . 'bb-config.php';
				$this->step_status[1] = 'complete';
				$this->strings[1]['messages']['message'][] = __( 'Your settings have been saved to the file <code>bb-config.php</code><br />You can now continue to the next step.' );
				break;
			default:
				// Just write the contents to screen
				$this->data[1]['form']['config']['value'] = $config_result;

				$this->step_status[1] = 'manual';
				$this->strings[1]['messages']['error'][] = __( 'Your settings could not be saved to a configuration file. You will need to save the text shown below into a file named <code>bb-config.php</code> in the root directory of your bbPress installation before you can continue.' );
				break;
		}
	}

	/**
	 * Validates the WordPress integration settings
	 *
	 * @return void
	 **/
	function process_form_wordpress_integration()
	{
		// Check the referer
		bb_check_admin_referer( 'bbpress-installer' );

		$this->inject_form_values_into_data( 2 );

		$data =& $this->data[2]['form'];

		// If there are no settings then goto step 3
		if ( !$data['toggle_2_0']['value'] && !$_POST['back_2_1'] ) {
			$this->step_status[2] = 'complete';
			$this->strings[2]['messages']['message'][] = __( 'You have chosen to skip the WordPress integration step. You can always integrate WordPress later from within the admin area of bbPress.' );
			return 'complete';
		}

		// If integration is selected
		if ( $data['toggle_2_0']['value'] ) {
			$data['toggle_2_0']['checked'] = 'checked="checked"';
			$data['toggle_2_0']['display'] = 'block';
			$data['forward_2_0']['value'] = $data['toggle_2_0']['toggle_value']['on_value'];

			if ( $data['toggle_2_1']['value'] ) {
				$data['toggle_2_1']['checked'] = 'checked="checked"';
				$data['toggle_2_1']['display'] = 'block';

				// Check the wp_siteurl URL for errors
				$data['wp_siteurl']['value'] = $data['wp_siteurl']['value'] ? rtrim( $data['wp_siteurl']['value'], " \t\n\r\0\x0B/" ) . '/' : '';
				$this->strings[2]['form_errors']['wp_siteurl'][] = empty( $data['wp_siteurl']['value'] ) ? 'empty' : false;
				if ( $parsed = parse_url( $data['wp_siteurl']['value'] ) ) {
					$this->strings[2]['form_errors']['wp_siteurl'][] = preg_match( '/https?/i', $parsed['scheme'] ) ? false : 'urlscheme';
					$this->strings[2]['form_errors']['wp_siteurl'][] = empty( $parsed['host'] ) ? 'urlhost' : false;
				} else {
					$this->strings[2]['form_errors']['wp_siteurl'][] = 'urlparse';
				}

				// Check the wp_home URL for errors
				$data['wp_home']['value'] = $data['wp_home']['value'] ? rtrim( $data['wp_home']['value'], " \t\n\r\0\x0B/" ) . '/' : '';
				$this->strings[2]['form_errors']['wp_home'][] = empty( $data['wp_home']['value'] ) ? 'empty' : false;
				if ( $parsed = parse_url( $data['wp_home']['value'] ) ) {
					$this->strings[2]['form_errors']['wp_home'][] = preg_match( '/https?/i', $parsed['scheme'] ) ? false : 'urlscheme';
					$this->strings[2]['form_errors']['wp_home'][] = empty( $parsed['host'] ) ? 'urlhost' : false;
				} else {
					$this->strings[2]['form_errors']['wp_home'][] = 'urlparse';
				}

				// Deal with slashes in the keys
				$data['wp_auth_key']['value']         = addslashes( stripslashes( $data['wp_auth_key']['value'] ) );
				$data['wp_auth_salt']['value']        = addslashes( stripslashes( $data['wp_auth_salt']['value'] ) );
				$data['wp_secure_auth_key']['value']  = addslashes( stripslashes( $data['wp_secure_auth_key']['value'] ) );
				$data['wp_secure_auth_salt']['value'] = addslashes( stripslashes( $data['wp_secure_auth_salt']['value'] ) );
				$data['wp_logged_in_key']['value']    = addslashes( stripslashes( $data['wp_logged_in_key']['value'] ) );
				$data['wp_logged_in_salt']['value']   = addslashes( stripslashes( $data['wp_logged_in_salt']['value'] ) );

				// Check the keys for errors
				$this->strings[2]['form_errors']['wp_auth_key'][]         = empty( $data['wp_auth_key']['value'] ) ? 'empty' : false;
				$this->strings[2]['form_errors']['wp_secure_auth_key'][]  = empty( $data['wp_secure_auth_key']['value'] ) ? 'empty' : false;
				$this->strings[2]['form_errors']['wp_logged_in_key'][]    = empty( $data['wp_logged_in_key']['value'] ) ? 'empty' : false;

				// Salts can be taken from the database if specified
				if ( !$data['toggle_2_2']['value'] ) {
					$this->strings[2]['form_errors']['wp_auth_salt'][]        = empty( $data['wp_auth_salt']['value'] ) ? 'empty' : false;
					// NB; secure_auth_salt is not always set in WordPress
					$this->strings[2]['form_errors']['wp_logged_in_salt'][]   = empty( $data['wp_logged_in_salt']['value'] ) ? 'empty' : false;
				}
			}

			// If database integration is selected
			if ( $data['toggle_2_2']['value'] ) {
				$data['toggle_2_2']['checked'] = 'checked="checked"';
				$data['toggle_2_2']['display'] = 'block';

				// Make the wp_table_prefix valid
				$data['wp_table_prefix']['value'] = preg_replace( '/[^0-9a-zA-Z_]/', '', $data['wp_table_prefix']['value'] );
				$data['wp_table_prefix']['value'] = empty( $data['wp_table_prefix']['value'] ) ? 'wp_' : $data['wp_table_prefix']['value'];

				// Make the wordpress_mu_primary_blog_id valid
				$data['wordpress_mu_primary_blog_id']['value'] = preg_replace( '/[^0-9]/', '', $data['wordpress_mu_primary_blog_id']['value'] );

				// If advanced database integration is selected
				if ( $data['toggle_2_3']['value'] ) {
					$data['toggle_2_3']['checked'] = 'checked="checked"';
					$data['toggle_2_3']['display'] = 'block';
				}
			}

			if ( !$data['toggle_2_1']['value'] && !$data['toggle_2_2']['value'] ) {
				$this->step_status[2] = 'incomplete';
				$this->strings[2]['messages']['error'][] = __( 'You must enter your settings for integration setup to complete. Choose which integration settings you wish to enter from the options below.' );
				$this->strings[2]['form_errors']['toggle_2_1'][] = true;
				$this->strings[2]['form_errors']['toggle_2_2'][] = true;
				return 'incomplete';
			}

			// Remove empty values from the error array
			foreach ( $this->strings[2]['form_errors'] as $input => $types) {
				$types = array_filter( $types);
				if ( !count( $types ) ) {
					unset( $this->strings[2]['form_errors'][$input] );
				}
			}

			// Check for errors and build error messages
			if ( count( $this->strings[2]['form_errors'] ) ) {

				$this->step_status[2] = 'incomplete';
				$this->strings[2]['messages']['error'][] = __( 'Your integration settings have not been processed due to errors with the items marked below.' );

				foreach ( $this->strings[2]['form_errors'] as $input => $types ) {
					$errors = array();

					foreach ( $types as $type ) {
						switch ( $type ) {
							case 'empty':
								// Only return this error when empty
								$errors = array( __( '&bull; This value is required to continue.' ) );
								break(2);
							case 'urlparse':
								$errors[] = __( '&bull; This does not appear to be a valid URL.' );
								break;
							case 'urlscheme':
								$errors[] = __( '&bull; The URL must begin with "http" or "https".' );
								break;
							case 'urlhost':
								$errors[] = __( '&bull; The URL does not contain a host name.' );
								break;
						}
					}

					$this->strings[2]['form_errors'][$input] = $errors;
				}

				return 'incomplete';
			}

			// If database integration is selected
			if ( $data['toggle_2_2']['value'] ) {

				// Test the db connection.

				// Setup variables and constants if available
				global $bb;
				$bb->wp_table_prefix = $data['wp_table_prefix']['value'];
				if ( $data['toggle_2_3']['value'] ) {
					// These may be empty at this particular stage
					if ( !empty( $data['user_bbdb_name']['value'] ) ) {
						$bb->user_bbdb_name = $data['user_bbdb_name']['value'];
					}
					if ( !empty( $data['user_bbdb_user']['value'] ) ) {
						$bb->user_bbdb_user = $data['user_bbdb_user']['value'];
					}
					if ( !empty( $data['user_bbdb_password']['value'] ) ) {
						$bb->user_bbdb_password = $data['user_bbdb_password']['value'];
					}
					if ( !empty( $data['user_bbdb_host']['value'] ) ) {
						$bb->user_bbdb_host = $data['user_bbdb_host']['value'];
					}
					if ( !empty( $data['user_bbdb_charset']['value'] ) ) {
						$bb->user_bbdb_charset = preg_replace( '/[^a-z0-9_-]/i', '', $data['user_bbdb_charset']['value'] );
					}
					if ( !empty( $data['user_bbdb_collate']['value'] ) ) {
						$bb->user_bbdb_collate = preg_replace( '/[^a-z0-9_-]/i', '', $data['user_bbdb_collate']['value'] );
					}
					if ( !empty( $data['custom_user_table']['value'] ) ) {
						$bb->custom_user_table = preg_replace( '/[^a-z0-9_-]/i', '', $data['custom_user_table']['value'] );
					}
					if ( !empty( $data['custom_user_meta_table']['value'] ) ) {
						$bb->custom_user_meta_table = preg_replace( '/[^a-z0-9_-]/i', '', $data['custom_user_meta_table']['value'] );
					}
				}

				// Bring in the database object
				global $bbdb;
				global $bb_table_prefix;

				// Resolve the custom user tables for bpdb
				bb_set_custom_user_tables();

				if ( isset( $bb->custom_databases) && isset( $bb->custom_databases['user'] ) ) {
					$bbdb->add_db_server( 'user', $bb->custom_databases['user'] );
				}

				// Add custom tables if required
				if ( isset( $bb->custom_tables['users'] ) || isset( $bb->custom_tables['usermeta'] ) ) {
					$bbdb->tables = array_merge( $bbdb->tables, $bb->custom_tables );
					if ( is_wp_error( $bbdb->set_prefix( $bb_table_prefix ) ) ) {
						die( __( 'Your user table prefix may only contain letters, numbers and underscores.' ) );
					}
				}

				// Hide errors for the test
				$bbdb->hide_errors();

				$result = $bbdb->query( 'DESCRIBE ' . $bbdb->users . ';' );
				$result_error = $bbdb->get_error();

				// Select from the user table (may fail if there are no records in the table)
				if ( !$result && $result_error ) {
					// We couldn't connect to the database at all

					// Turn errors back on
					$bbdb->show_errors();

					// Set the status
					$this->step_status[2] = 'incomplete';
					if ( !empty( $data['user_bbdb_name']['value'] ) ) {
						$this->strings[2]['form_errors']['user_bbdb_name'][] = true;
					}
					if ( !empty( $data['user_bbdb_user']['value'] ) ) {
						$this->strings[2]['form_errors']['user_bbdb_user'][] = true;
					}
					if ( !empty( $data['user_bbdb_password']['value'] ) ) {
						$this->strings[2]['form_errors']['user_bbdb_password'][] = true;
					}
					if ( !empty( $data['user_bbdb_host']['value'] ) ) {
						$this->strings[2]['form_errors']['user_bbdb_host'][] = true;
					}
					if ( !empty( $data['custom_user_table']['value'] ) ) {
						$this->strings[2]['form_errors']['custom_user_table'][] = true;
					}
					if ( !empty( $data['custom_user_meta_table']['value'] ) ) {
						$this->strings[2]['form_errors']['custom_user_meta_table'][] = true;
					}
					$this->strings[2]['messages']['error'][] = __( 'There was a problem connecting to the WordPress user database you specified. Please check the settings, then try again.' );
					return 'incomplete';
				}

				if ( $result_error ) {
					// The result is an error, presumably telling us the table doesn't exist

					// Turn errors back on
					$bbdb->show_errors();

					// Set the status
					$this->step_status[2] = 'incomplete';

					if ( $data['toggle_2_3']['value'] ) {
						$this->strings[2]['messages']['error'][] = __( 'Existing WordPress user tables could not be found in the WordPress database you specified.' );
					} else {
						$this->strings[2]['messages']['error'][] = __( 'Existing WordPress user tables could not be found in the bbPress database you specified in step 1.<br /><br />This is probably because the database does not already contain working WordPress tables. You may need to specify advanced database settings or leave integration until after installation.' );
					}
					$this->strings[2]['form_errors']['wp_table_prefix'][] = __( '&bull; This may not be a valid user table prefix.' );
					return 'incomplete';
				}

				// Turn errors back on
				$bbdb->show_errors();
			}
		}

		// Stop here if we are going backwards
		if ( $_POST['back_2_1'] ) {
			$this->step_status[2] = 'incomplete';
			return 'incomplete';
		}

		// If we make it this may we are complete, so set the status to complete
		$this->step_status[2] = 'complete';
		$this->strings[2]['messages']['message'][] = __( 'Your WordPress integration cookie and database settings have been successfully validated. They will be saved after the next step.<br /><br />Once you have finished installing, you should visit the WordPress integration section of the bbPress admin area for further options and integration instructions, including user mapping and the correct cookie settings to add to your WordPress configuration file.' );
		return 'complete';
	}

	/**
	 * Validates the site options.
	 *
	 * @return void
	 **/
	function process_form_site_options()
	{
		// Check the referer
		bb_check_admin_referer( 'bbpress-installer' );

		$this->inject_form_values_into_data( 2 );
		$this->inject_form_values_into_data( 3 );

		$data =& $this->data[3]['form'];

		$this->strings[3]['form_errors']['name'][] = empty( $data['name']['value'] ) ? 'empty' : false;

		$data['uri']['value'] = $data['uri']['value'] ? rtrim( $data['uri']['value'], " \t\n\r\0\x0B/" ) . '/' : '';
		$this->strings[3]['form_errors']['uri'][] = empty( $data['uri']['value'] ) ? 'empty' : false;
		if ( $parsed = parse_url( $data['uri']['value'] ) ) {
			$this->strings[3]['form_errors']['uri'][] = preg_match( '/https?/i', $parsed['scheme'] ) ? false : 'urlscheme';
			$this->strings[3]['form_errors']['uri'][] = empty( $parsed['host'] ) ? 'urlhost' : false;
		} else {
			$this->strings[3]['form_errors']['uri'][] = 'urlparse';
		}

		$this->strings[3]['form_errors']['keymaster_user_login'][] = empty( $data['keymaster_user_login']['value'] ) ? 'empty' : false;
		if ( $data['keymaster_user_login']['value'] != sanitize_user( $data['keymaster_user_login']['value'], true ) ) {
			$this->strings[3]['form_errors']['keymaster_user_login'][] = 'userlogin';
		}
		$data['keymaster_user_login']['value'] = sanitize_user( $data['keymaster_user_login']['value'], true );

		// Check for a valid email
		$this->strings[3]['form_errors']['keymaster_user_email'][] = empty( $data['keymaster_user_email']['value'] ) ? 'empty' : false;
		$this->strings[3]['form_errors']['keymaster_user_email'][] = !is_email( $data['keymaster_user_email']['value'] ) ? 'email' : false;

		// Check for a forum name
		if ( !$this->database_tables_are_installed() ) {
			$this->strings[3]['form_errors']['forum_name'][] = empty( $data['forum_name']['value'] ) ? 'empty' : false;
		}

		// Remove empty values from the error array
		foreach ( $this->strings[3]['form_errors'] as $input => $types ) {
			$types = array_filter( $types );
			if ( !count( $types ) ) {
				unset( $this->strings[3]['form_errors'][$input] );
			}
		}

		// Check for errors and build error messages
		if ( count( $this->strings[3]['form_errors'] ) ) {

			$this->step_status[3] = 'incomplete';
			$this->strings[3]['messages']['error'][] = __( 'Your site settings have not been processed due to errors with the items marked below.' );

			foreach ( $this->strings[3]['form_errors'] as $input => $types ) {
				$errors = array();

				foreach ( $types as $type ) {
					switch ( $type ) {
						case 'empty':
							// Only return this error when empty
							$errors = array( __( '&bull; This value is required to continue.' ) );
							break(2);
						case 'urlparse':
							$errors[] = __( '&bull; This does not appear to be a valid URL.' );
							break;
						case 'urlscheme':
							$errors[] = __( '&bull; The URL must begin with "http" or "https".' );
							break;
						case 'urlhost':
							$errors[] = __( '&bull; The URL does not contain a host name.' );
							break;
						case 'userlogin':
							$errors[] = __( '&bull; Contains disallowed characters which have been removed.' );
							break;
						case 'email':
							$errors[] = __( '&bull; The user email address appears to be invalid.' );
							break;
					}
				}

				$this->strings[3]['form_errors'][$input] = $errors;
			}

			return 'incomplete';
		}

		// Stop here if we are going backwards
		if ( $_POST['back_3_1'] ) {
			$this->step_status[3] = 'incomplete';
			return 'incomplete';
		}

		// If we make it this far we are good to go
		$this->step_status[3] = 'complete';
		$this->strings[3]['messages']['message'][] = __( 'Your site settings have been saved and we are now ready to complete the installation. So what are you waiting for?' );
		return 'complete';
	}

	/**
	 * Finalises the installation by creating the database and writing all the supplied data to the database.
	 *
	 * @return void
	 **/
	function process_form_finalise_installation()
	{
		require_once( BB_PATH . 'bb-admin/includes/functions.bb-upgrade.php' );
		require_once( BB_PATH . 'bb-admin/includes/functions.bb-admin.php' );

		$this->inject_form_values_into_data( 2 );
		$this->inject_form_values_into_data( 3 );

		$data2 =& $this->data[2]['form'];
		$data3 =& $this->data[3]['form'];
		$data4 =& $this->data[4]['form'];

		$error_log = array();
		$installation_log = array();

		// Check the referer
		bb_check_admin_referer( 'bbpress-installer' );
		$installation_log[] = __( 'Referrer is OK, beginning installation&hellip;' );

		global $bbdb;

		// Setup user table variables and constants if available
		if ( $data2['toggle_2_2']['value'] ) {

			$installation_log[] = '>>> ' . __( 'Setting up custom user table constants' );

			global $bb;
			global $bb_table_prefix;

			if ( !empty( $data2['wp_table_prefix']['value'] ) ) {
				$bb->wp_table_prefix = $data2['wp_table_prefix']['value'];
			}
			if ( !empty( $data2['user_bbdb_name']['value'] ) ) {
				$bb->user_bbdb_name = $data2['user_bbdb_name']['value'];
			}
			if ( !empty( $data2['user_bbdb_user']['value'] ) ) {
				$bb->user_bbdb_user = $data2['user_bbdb_user']['value'];
			}
			if ( !empty( $data2['user_bbdb_password']['value'] ) ) {
				$bb->user_bbdb_password = $data2['user_bbdb_password']['value'];
			}
			if ( !empty( $data2['user_bbdb_host']['value'] ) ) {
				$bb->user_bbdb_host = $data2['user_bbdb_host']['value'];
			}
			if ( !empty( $data2['user_bbdb_charset']['value'] ) ) {
				$bb->user_bbdb_charset = preg_replace( '/[^a-z0-9_-]/i', '', $data2['user_bbdb_charset']['value'] );
			}
			if ( !empty( $data2['user_bbdb_collate']['value'] ) ) {
				$bb->user_bbdb_collate = preg_replace( '/[^a-z0-9_-]/i', '', $data2['user_bbdb_collate']['value'] );
			}

			bb_set_custom_user_tables();

			// Add custom user database if required
			if ( isset( $bb->custom_databases['user'] ) ) {
				$bbdb->add_db_server( 'user', $bb->custom_databases['user'] );
			}

			// Add custom tables if required
			if ( isset( $bb->custom_tables ) ) {
				$bbdb->tables = array_merge( $bbdb->tables, $bb->custom_tables );
				if ( is_wp_error( $bbdb->set_prefix( $bb_table_prefix ) ) )
					die( __( 'Your user table prefix may only contain letters, numbers and underscores.' ) );
			}
		}

		// Create the database
		$installation_log[] = "\n" . __( 'Step 1 - Creating database tables' );

		if ( !$this->database_tables_are_installed() ) {
			// Hide db errors
			$bbdb->hide_errors();
			// Install the database
			$alterations = bb_install();
			// Show db errors
			$bbdb->show_errors();

			if ( isset( $alterations['errors'] ) && is_array( $alterations['errors'] ) ) {
				$error_log = array_merge( $error_log, $alterations['errors'] );
			}
			if ( isset( $alterations['messages'] ) && is_array( $alterations['messages'] ) ) {
				$installation_log = array_merge( $installation_log, $alterations['messages'] );
			}

			if ( !$this->database_tables_are_installed() ) {
				$installation_log[] = '>>> ' . __( 'Database installation failed!!!' );
				$installation_log[] = '>>>>>> ' . __( 'Halting installation!' );
				$error_log[] = __( 'Database installation failed!!!' );

				$this->step_status[4] = 'incomplete';
				$this->strings[4]['h2'] = __( 'Installation failed!' );
				$this->strings[4]['messages']['error'][] = __( 'The database failed to install. You may need to replace bbPress with a fresh copy and start again.' );

				$data4['installation_log']['value'] = join( "\n", $installation_log );
				$data4['error_log']['value'] = join( "\n", $error_log );

				return 'incomplete';
			}
		} else {
			$installation_log[] = '>>> ' . __( 'Database is already installed!!!' );
		}

		// Integration settings passed from step 2
		// These are already validated provided that the referer checks out
		$installation_log[] = "\n" . __( 'Step 2 - WordPress integration (optional)' );
		if ( $data2['toggle_2_0']['value'] ) {
			if ( $data2['toggle_2_1']['value'] ) {
				bb_update_option( 'wp_siteurl', $data2['wp_siteurl']['value'] );
				$installation_log[] = '>>> ' . __( 'WordPress address (URL):' ) . ' ' . $data2['wp_siteurl']['value'];

				bb_update_option( 'wp_home', $data2['wp_home']['value'] );
				$installation_log[] = '>>> ' . __( 'Blog address (URL):' ) . ' ' . $data2['wp_home']['value'];

				$config_result = $this->write_lines_to_file(
					BB_PATH . 'bb-config.php',
					false,
					array(
						"define( 'BB_AUTH_KEY"  => array( "'" . BB_AUTH_KEY . "'",        "'" . $data2['wp_auth_key']['value'] . "'" ),
						"define( 'BB_SECURE_A"  => array( "'" . BB_SECURE_AUTH_KEY . "'", "'" . $data2['wp_secure_auth_key']['value'] . "'" ),
						"define( 'BB_LOGGED_I"  => array( "'" . BB_LOGGED_IN_KEY . "'",   "'" . $data2['wp_logged_in_key']['value'] . "'" ),
					)
				);

				switch ( $config_result ) {
					case 1:
						$installation_log[] = '>>> ' . __( 'WordPress cookie keys set.' );
						break;
					default:
						$error_log[] = '>>> ' . __( 'WordPress cookie keys not set.' );
						$error_log[] = '>>>>>> ' . __( 'Your "bb-config.php" file was not writable.' );
						$error_log[] = '>>>>>> ' . __( 'You will need to manually re-define "BB_AUTH_KEY", "BB_SECURE_AUTH_KEY" and "BB_LOGGED_IN_KEY" in your "bb-config.php" file.' );
						$installation_log[] = '>>> ' . __( 'WordPress cookie keys not set.' );
						break;
				}

				if ( !empty( $data2['wp_auth_salt']['value'] ) ) {
					bb_update_option( 'bb_auth_salt', $data2['wp_auth_salt']['value'] );
					$installation_log[] = '>>> ' . __( 'WordPress "auth" cookie salt set from input.' );
				}

				if ( !empty( $data2['wp_secure_auth_salt']['value'] ) ) {
					bb_update_option( 'bb_secure_auth_salt', $data2['wp_secure_auth_salt']['value'] );
					$installation_log[] = '>>> ' . __( 'WordPress "secure auth" cookie salt set from input.' );
				}

				if ( !empty( $data2['wp_logged_in_salt']['value'] ) ) {
					bb_update_option( 'bb_logged_in_salt', $data2['wp_logged_in_salt']['value'] );
					$installation_log[] = '>>> ' . __( 'WordPress "logged in" cookie salt set from input.' );
				}
			}

			if ( $data2['toggle_2_2']['value'] ) {
				if (
					!bb_get_option( 'bb_auth_salt' ) ||
					!bb_get_option( 'bb_secure_auth_salt' ) ||
					!bb_get_option( 'bb_logged_in_salt' )
				) {
					$installation_log[] = '>>> ' . __( 'Fetching missing WordPress cookie salts.' );

					$_prefix = $bb->wp_table_prefix;
					if ( !empty( $data2['wordpress_mu_primary_blog_id']['value'] ) ) {
						$_prefix .= $data2['wordpress_mu_primary_blog_id']['value'] . '_';
					}

					if ( isset( $bb->custom_databases['user'] ) ) {
						$bbdb->tables['options'] = array( 'user', $_prefix . 'options' );
					} else {
						$bbdb->tables['options'] = $_prefix . 'options';
					}

					unset( $_prefix );

					$bbdb->set_prefix( $bb_table_prefix );

					if ( !bb_get_option( 'bb_auth_salt' ) ) {
						$wp_auth_salt = $bbdb->get_var( "SELECT `option_value` FROM $bbdb->options WHERE `option_name` = 'auth_salt' LIMIT 1" );
						if ( $wp_auth_salt ) {
							bb_update_option( 'bb_auth_salt', $wp_auth_salt );
							$installation_log[] = '>>>>>> ' . __( 'WordPress "auth" cookie salt set.' );
						} else {
							$error_log[] = '>>> ' . __( 'WordPress "auth" cookie salt not set.' );
							$error_log[] = '>>>>>> ' . __( 'Could not fetch "auth" cookie salt from the WordPress options table.' );
							$error_log[] = '>>>>>> ' . __( 'You will need to manually define the "auth" cookie salt in your database.' );
							$installation_log[] = '>>>>>> ' . __( 'WordPress "auth" cookie salt not set.' );
						}
					}

					if ( !bb_get_option( 'bb_secure_auth_salt' ) ) {
						$wp_secure_auth_salt = $bbdb->get_var( "SELECT `option_value` FROM $bbdb->options WHERE `option_name` = 'secure_auth_salt' LIMIT 1" );
						if ( $wp_secure_auth_salt ) {
							bb_update_option( 'bb_secure_auth_salt', $wp_secure_auth_salt );
							$installation_log[] = '>>>>>> ' . __( 'WordPress "secure auth" cookie salt set.' );
						} else {
							// This cookie salt is sometimes empty so don't error
							$installation_log[] = '>>>>>> ' . __( 'WordPress "secure auth" cookie salt not set.' );
						}
					}

					if ( !bb_get_option( 'bb_logged_in_salt' ) ) {
						$wp_logged_in_salt = $bbdb->get_var( "SELECT `option_value` FROM $bbdb->options WHERE `option_name` = 'logged_in_salt' LIMIT 1" );
						if ( $wp_logged_in_salt ) {
							bb_update_option( 'bb_logged_in_salt', $wp_logged_in_salt );
							$installation_log[] = '>>>>>> ' . __( 'WordPress "logged in" cookie salt set.' );
						} else {
							$error_log[] = '>>> ' . __( 'WordPress "logged in" cookie salt not set.' );
							$error_log[] = '>>>>>> ' . __( 'Could not fetch "logged in" cookie salt from the WordPress options table.' );
							$error_log[] = '>>>>>> ' . __( 'You will need to manually define the "logged in" cookie salt in your database.' );
							$installation_log[] = '>>>>>> ' . __( 'WordPress "logged in" cookie salt not set.' );
						}
					}
				}

				if ( !empty( $data2['wp_table_prefix']['value'] ) ) {
					bb_update_option( 'wp_table_prefix', $data2['wp_table_prefix']['value'] );
					$installation_log[] = '>>> ' . __( 'User database table prefix:' ) . ' ' . $data2['wp_table_prefix']['value'];
				}

				if ( !empty( $data2['wordpress_mu_primary_blog_id']['value'] ) ) {
					bb_update_option( 'wordpress_mu_primary_blog_id', $data2['wordpress_mu_primary_blog_id']['value'] );
					$installation_log[] = '>>> ' . __( 'WordPress MU primary blog ID:' ) . ' ' . $data2['wordpress_mu_primary_blog_id']['value'];
				}

				if ( $data2['toggle_2_3']['value'] ) {
					if ( !empty( $data2['user_bbdb_name']['value'] ) ) {
						bb_update_option( 'user_bbdb_name', $data2['user_bbdb_name']['value'] );
						$installation_log[] = '>>> ' . __( 'User database name:' ) . ' ' . $data2['user_bbdb_name']['value'];
					}
					if ( !empty( $data2['user_bbdb_user']['value'] ) ) {
						bb_update_option( 'user_bbdb_user', $data2['user_bbdb_user']['value'] );
						$installation_log[] = '>>> ' . __( 'User database user:' ) . ' ' . $data2['user_bbdb_user']['value'];
					}
					if ( !empty( $data2['user_bbdb_password']['value'] ) ) {
						bb_update_option( 'user_bbdb_password', $data2['user_bbdb_password']['value'] );
						$installation_log[] = '>>> ' . __( 'User database password:' ) . ' ' . $data2['user_bbdb_password']['value'];
					}
					if ( !empty( $data2['user_bbdb_host']['value'] ) ) {
						bb_update_option( 'user_bbdb_host', $data2['user_bbdb_host']['value'] );
						$installation_log[] = '>>> ' . __( 'User database host:' ) . ' ' . $data2['user_bbdb_host']['value'];
					}
					if ( !empty( $data2['user_bbdb_charset']['value'] ) ) {
						bb_update_option( 'user_bbdb_charset', $data2['user_bbdb_charset']['value'] );
						$installation_log[] = '>>> ' . __( 'User database character set:' ) . ' ' . $data2['user_bbdb_charset']['value'];
					}
					if ( !empty( $data2['user_bbdb_collate']['value'] ) ) {
						bb_update_option( 'user_bbdb_collate', $data2['user_bbdb_collate']['value'] );
						$installation_log[] = '>>> ' . __( 'User database collation:' ) . ' ' . $data2['user_bbdb_collate']['value'];
					}
					if ( !empty( $data2['custom_user_table']['value'] ) ) {
						bb_update_option( 'custom_user_table', $data2['custom_user_table']['value'] );
						$installation_log[] = '>>> ' . __( 'User database "user" table:' ) . ' ' . $data2['custom_user_table']['value'];
					}
					if ( !empty( $data2['custom_user_meta_table']['value'] ) ) {
						bb_update_option( 'custom_user_meta_table', $data2['custom_user_meta_table']['value'] );
						$installation_log[] = '>>> ' . __( 'User database "user meta" table:' ) . ' ' . $data2['custom_user_meta_table']['value'];
					}
				}
			}
		} else {
			$installation_log[] = '>>> ' . __( 'Integration not enabled' );
		}

		// Site settings passed from step 3
		// These are already validated provided that the referer checks out
		$installation_log[] = "\n" . __( 'Step 3 - Site settings' );
		bb_update_option( 'name', $data3['name']['value'] );
		$installation_log[] = '>>> ' . __( 'Site name:' ) . ' ' . $data3['name']['value'];
		bb_update_option( 'uri', $data3['uri']['value'] );
		$installation_log[] = '>>> ' . __( 'Site address (URL):' ) . ' ' . $data3['uri']['value'];
		bb_update_option( 'from_email', $data3['keymaster_user_email']['value'] );
		$installation_log[] = '>>> ' . __( 'From email address:' ) . ' ' . $data3['keymaster_user_email']['value'];

		// Create the key master
		$keymaster_created = false;

		switch ( $data3['keymaster_user_type']['value'] ) {
			case 'new':

				// Check to see if the user login already exists
				if ( $keymaster_user = bb_get_user( $data3['keymaster_user_login']['value'], array( 'by' => 'login' ) ) ) {
					// The keymaster is an existing bbPress user
					$installation_log[] = '>>> ' . __( 'Key master could not be created!' );
					$installation_log[] = '>>>>>> ' . __( 'That login is already taken!' );
					$error_log[] = __( 'Key master could not be created!' );

					if ( $keymaster_user->bb_capabilities['keymaster'] ) {
						// The existing user is a key master - continue
						$bb_current_user = bb_set_current_user( $keymaster_user->ID );
						$installation_log[] = '>>>>>> ' . __( 'Existing key master entered!' );
						$data4['keymaster_user_password']['value'] = __( 'Your bbPress password' );
						$data3['keymaster_user_email']['value'] = $keymaster_user->user_email;
						bb_update_option( 'from_email', $keymaster_user->user_email);
						$installation_log[] = '>>>>>> ' . __( 'Re-setting admin email address.' );
						$keymaster_created = true;
					} else {
						// The existing user is a non-key master user - halt installation
						$installation_log[] = '>>>>>> ' . __( 'Existing user without key master role entered!' );
						$installation_log[] = '>>>>>>>>> ' . __( 'Halting installation!' );
						$this->step_status[4] = 'incomplete';
						$this->strings[4]['h2'] = __( 'Installation failed!' );
						$this->strings[4]['messages']['error'][] = __( 'The key master could not be created. An existing user was found with that user login.' );

						$data4['installation_log']['value'] = join( "\n", $installation_log );
						$data4['error_log']['value'] = join( "\n", $error_log );

						return 'incomplete';
					}

					break;
				}

				// Helper function to let us know the password that was created
				global $keymaster_password;
				function bb_get_keymaster_password( $user_id, $pass ) {
					global $keymaster_password;
					$keymaster_password = $pass;
				}
				add_action( 'bb_new_user', 'bb_get_keymaster_password', 10, 2 );

				// Create the new user (automattically given key master role when BB_INSTALLING is true)
				if ( $keymaster_user_id = bb_new_user( $data3['keymaster_user_login']['value'], $data3['keymaster_user_email']['value'], '' ) ) {
					$bb_current_user = bb_set_current_user( $keymaster_user_id );
					$data4['keymaster_user_password']['value'] = $keymaster_password;
					$installation_log[] = '>>> ' . __( 'Key master created' );
					$installation_log[] = '>>>>>> ' . __( 'Username:' ) . ' ' . $data3['keymaster_user_login']['value'];
					$installation_log[] = '>>>>>> ' . __( 'Email address:' ) . ' ' . $data3['keymaster_user_email']['value'];
					$installation_log[] = '>>>>>> ' . __( 'Password:' ) . ' ' . $data4['keymaster_user_password']['value'];
					$keymaster_created = true;
				} else {
					$installation_log[] = '>>> ' . __( 'Key master could not be created!' );
					$installation_log[] = '>>>>>> ' . __( 'Halting installation!' );
					$error_log[] = __( 'Key master could not be created!' );
					$this->step_status[4] = 'incomplete';
					$this->strings[4]['h2'] = __( 'Installation failed!' );
					$this->strings[4]['messages']['error'][] = __( 'The key master could not be created. You may need to replace bbPress with a fresh copy and start again.' );

					$data4['installation_log']['value'] = join( "\n", $installation_log );
					$data4['error_log']['value'] = join( "\n", $error_log );

					return 'incomplete';
				}
				break;

			case 'old':
				if ( $keymaster_user = bb_get_user( $data3['keymaster_user_login']['value'], array( 'by' => 'login' ) ) ) {
					// The keymaster is an existing bbPress or WordPress user
					$bb_current_user = bb_set_current_user( $keymaster_user->ID );
					$bb_current_user->set_role( 'keymaster' );
					$data4['keymaster_user_password']['value'] = __( 'Your existing password' );
					$installation_log[] = '>>> ' . __( 'Key master role assigned to existing user' );
					$installation_log[] = '>>>>>> ' . __( 'Username:' ) . ' ' . $data3['keymaster_user_login']['value'];
					$installation_log[] = '>>>>>> ' . __( 'Email address:' ) . ' ' . $data3['keymaster_user_email']['value'];
					$installation_log[] = '>>>>>> ' . __( 'Password:' ) . ' ' . $data4['keymaster_user_password']['value'];
					$keymaster_created = true;
				} else {
					$installation_log[] = '>>> ' . __( 'Key master role could not be assigned to existing user!' );
					$installation_log[] = '>>>>>> ' . __( 'Halting installation!' );
					$error_log[] = __( 'Key master could not be created!' );
					$this->step_status[4] = 'incomplete';
					$this->strings[4]['h2'] = __( 'Installation failed!' );
					$this->strings[4]['messages']['error'][] = __( 'The key master could not be assigned. You may need to replace bbPress with a fresh copy and start again.' );

					$data4['installation_log']['value'] = join( "\n", $installation_log );
					$data4['error_log']['value'] = join( "\n", $error_log );

					return 'incomplete';
				}
				break;
		}

		// Don't create an initial forum if any forums already exist
		if (!$bbdb->get_results( 'SELECT `forum_id` FROM `' . $bbdb->forums . '` LIMIT 1;' ) ) {
			if ( $this->language != BB_LANG) {
				global $locale, $l10n;
				$locale = BB_LANG;
				unset( $l10n['default'] );
				bb_load_default_textdomain();
			}

			$description = __( 'Just another bbPress community' );
			bb_update_option( 'description', $description);

			if ( $this->language != BB_LANG) {
				$locale = $this->language;
				unset( $l10n['default'] );
				bb_load_default_textdomain();
			}

			$installation_log[] = '>>> ' . __( 'Description:' ) . ' ' . $description;

			if ( $forum_id = bb_new_forum( array( 'forum_name' => $data3['forum_name']['value'] ) ) ) {
				$installation_log[] = '>>> ' . __( 'Forum name:' ) . ' ' . $data3['forum_name']['value'];

				if ( $this->language != BB_LANG) {
					$locale = BB_LANG;
					unset( $l10n['default'] );
					bb_load_default_textdomain();
				}

				$topic_title = __( 'Your first topic' );
				$topic_id = bb_insert_topic(
					array(
						'topic_title' => $topic_title,
						'forum_id' => $forum_id,
						'tags' => 'bbPress'
					)
				);
				$post_text = __( 'First Post!  w00t.' );
				bb_insert_post(
					array(
						'topic_id' => $topic_id,
						'post_text' => $post_text
					)
				);

				if ( $this->language != BB_LANG ) {
					$locale = $this->language;
					unset( $l10n['default'] );
					bb_load_default_textdomain();
				}

				$installation_log[] = '>>>>>> ' . __( 'Topic:' ) . ' ' . $topic_title;
				$installation_log[] = '>>>>>>>>> ' . __( 'Post:' ) . ' ' . $post_text;
			} else {
				$installation_log[] = '>>> ' . __( 'Forum could not be created!' );
				$error_log[] = __( 'Forum could not be created!' );
			}
		} else {
			$installation_log[] = '>>> ' . __( 'There are existing forums in this database.' );
			$installation_log[] = '>>>>>> ' . __( 'No new forum created.' );
			$error_log[] = __( 'Forums already exist!' );
		}

		if ( defined( 'BB_PLUGIN_DIR' ) && BB_PLUGIN_DIR && !file_exists( BB_PLUGIN_DIR ) ) {
			// Just suppress errors as this is not critical
			if ( @mkdir( BB_PLUGIN_DIR, 0755 ) ) {
				$installation_log[] = '>>> ' . sprintf( __( 'Making plugin directory at %s.' ),  BB_PLUGIN_DIR );
			}
		}

		if ( defined( 'BB_THEME_DIR' ) && BB_THEME_DIR && !file_exists( BB_THEME_DIR ) ) {
			// Just suppress errors as this is not critical
			if ( @mkdir( BB_THEME_DIR, 0755 ) ) {
				$installation_log[] = '>>> ' . sprintf( __( 'Making theme directory at %s.' ),  BB_THEME_DIR );
			}
		}

		if ( $keymaster_created ) {
			$keymaster_email_message = sprintf(
				__( "Your new bbPress site has been successfully set up at:\n\n%1\$s\n\nYou can log in to the key master account with the following information:\n\nUsername: %2\$s\nPassword: %3\$s\n\nWe hope you enjoy your new forums. Thanks!\n\n--The bbPress Team\nhttp://bbpress.org/" ),
				bb_get_uri( null, null, BB_URI_CONTEXT_TEXT ),
				$data3['keymaster_user_login']['value'],
				$data4['keymaster_user_password']['value']
			);

			if ( bb_mail( $data3['keymaster_user_email']['value'], __( 'New bbPress installation' ), $keymaster_email_message ) ) {
				$installation_log[] = '>>> ' . __( 'Key master email sent' );
			} else {
				$installation_log[] = '>>> ' . __( 'Key master email not sent!' );
				$error_log[] = __( 'Key master email not sent!' );
			}
		}

		if ( count( $error_log ) ) {
			$this->strings[4]['h2'] = __( 'Installation completed with some errors!' );
			$this->strings[4]['messages']['error'][] = __( 'Your installation completed with some minor errors. See the error log below for more specific information.' );
			$installation_log[] = "\n" . __( 'There were some errors encountered during installation!' );
		} else {
			$this->strings[4]['messages']['message'][] = __( 'Your installation completed successfully.' );
			$installation_log[] = "\n" . __( 'Installation complete!' );
		}

		$this->step_status[4] = 'complete';

		$data4['installation_log']['value'] = join( "\n", $installation_log );
		$data4['error_log']['value'] = join( "\n", $error_log );

		return 'complete';
	}

	/**
	 * Prints a text input form element.
	 *
	 * @param $key string The key of the data to populate the element with.
	 * @param $direction string Optional. The text direction, only 'ltr' or 'rtl' are acceptable.
	 * @return void
	 **/
	function input_text( $key, $direction = false )
	{
		$data = $this->data[$this->step]['form'][$key];

		$class = '';
		$classes = array();
		if ( isset( $data['note'] ) ) {
			$classes[] = 'has-note';
		}
		if ( isset( $data['label'] ) ) {
			$classes[] = 'has-label';
		}

		if ( isset( $this->data[$this->step]['form'][$key]['type'] ) ) {
			$type = $this->data[$this->step]['form'][$key]['type'];
		} else {
			$type = 'text';
		}
		$classes[] = 'for-input-' . $type;

		if ( isset( $this->strings[$this->step]['form_errors'][$key] ) ) {
			$classes[] = 'error';
		}
		if ( count( $classes ) ) {
			$class = ' class="' . join( ' ', $classes ) . '"';
		}

		$r = "\t" . '<label id="label-' . esc_attr( $key ) . '" for="' . esc_attr( $key ) . '"' . $class . '>' . "\n";

		if ( isset( $data['label'] ) ) {
			$r .= "\t\t" . '<span>' . $data['label'] . '</span>' . "\n";
		}

		if ( isset( $this->strings[$this->step]['form_errors'][$key] ) ) {
			foreach ( $this->strings[$this->step]['form_errors'][$key] as $error ) {
				if ( !is_bool( $error ) ) {
					$r .= "\t\t" . '<span class="error">' . $error . '</span>' . "\n";
				}
			}
		}

		if ( isset( $data['maxlength'] ) && is_integer( $data['maxlength'] ) ) {
			$maxlength = ' maxlength="' . esc_attr( $data['maxlength'] ) . '"';
		}

		if ( $direction && in_array( strtolower( $direction ), array( 'ltr', 'rtl' ) ) ) {
			$direction = ' dir="' . esc_attr( strtolower( $direction ) ) . '"';
		}

		if ( isset( $data['autocomplete'] ) ) {
			$autocomplete = ' autocomplete="' . esc_attr( $data['autocomplete'] ) . '"';
		} else {
			$autocomplete = '';
		}

		$this->tabindex++;
		$r .= "\t\t" . '<input' . $direction . ' type="' . esc_attr( $type ) . '" id="' . esc_attr( $key ) . '" name="' . esc_attr( $key ) . '" class="text' . $has_note_class . '" value="' . esc_attr( $data['value'] ) . '"' . $maxlength . $autocomplete . ' tabindex="' . $this->tabindex . '" />' . "\n";

		if ( isset( $data['note'] ) ) {
			$r .= "\t\t" . '<a class="note-toggle" href="javascript:void(0);" onclick="toggleNote(\'note-' . esc_attr( $key ) . '\');">?</a>' . "\n";
			$r .= "\t\t" . '<p id="note-' . esc_attr( $key ) . '" class="note" style="display:none">' . $data['note'] . '</p>' . "\n";
		}

		$r .= "\t\t" . '<div class="clear"></div>' . "\n";
		$r .= "\t" . '</label>' . "\n";

		echo $r;
	}

	/**
	 * Prints a hidden input form element.
	 *
	 * @param $key string The key of the data to populate the element with.
	 * @return void
	 **/
	function input_hidden( $key )
	{
		$r = "\t" . '<input type="hidden" id="' . esc_attr( $key ) . '" name="' . esc_attr( $key ) . '" value="' . esc_attr( $this->data[$this->step]['form'][$key]['value'] ) . '" />' . "\n";

		echo $r;
	}

	/**
	 * Prints a textarea form element.
	 *
	 * @param $key string The key of the data to populate the element with.
	 * @param $direction string Optional. The text direction, only 'ltr' or 'rtl' are acceptable.
	 * @return void
	 **/
	function textarea( $key, $direction = false)
	{
		$data = $this->data[$this->step]['form'][$key];

		$class = '';
		$classes = array( 'for-textarea' );
		if ( isset( $data['note'] ) ) {
			$classes[] = 'has-note';
		}
		if ( isset( $data['label'] ) ) {
			$classes[] = 'has-label';
		}
		if ( count( $classes ) ) {
			$class = ' class="' . join( ' ', $classes ) . '"';
		}

		$r = "\t" . '<label id="label-' . esc_attr( $key ) . '"' . $class . ' for="' . esc_attr( $key ) . '">' . "\n";

		if ( isset( $data['label'] ) ) {
			$r .= "\t\t" . '<span>' . $data['label'] . '</span>' . "\n";
		}

		if ( isset( $data['note'] ) ) {
			$r .= "\t\t" . '<a class="note-toggle" href="javascript:void(0);" onclick="toggleNote(\'note-' . esc_attr( $key ) . '\');">?</a>' . "\n";
			$r .= "\t\t" . '<p id="note-' . esc_attr( $key ) . '" class="note" style="display:none">' . $data['note'] . '</p>' . "\n";
		}

		if ( $direction && in_array( strtolower( $direction ), array( 'ltr', 'rtl' ) ) ) {
			$direction = ' dir="' . esc_attr( strtolower( $direction ) ) . '"';
		}

		$this->tabindex++;
		$r .= "\t\t" . '<textarea id="' . esc_attr( $key ) . '" rows="5" cols="30"' . $direction . ' tabindex="' . $this->tabindex . '">' . esc_html( $data['value'] ) . '</textarea>' . "\n";

		$r .= "\t" . '</label>' . "\n";

		echo $r;
	}

	/**
	 * Prints a select form element populated with options.
	 *
	 * @param $key string The key of the data to populate the element with.
	 * @return void
	 **/
	function select( $key )
	{
		$data = $this->data[$this->step]['form'][$key];

		$class = '';
		$classes = array( 'for-select' );
		if ( isset( $data['note'] ) ) {
			$classes[] = 'has-note';
		}
		if ( isset( $data['label'] ) ) {
			$classes[] = 'has-label';
		}
		if ( count( $classes ) ) {
			$class = ' class="' . join( ' ', $classes ) . '"';
		}

		$r = "\t" . '<label id="label-' . esc_attr( $key ) . '"' . $class . ' for="' . esc_attr( $key ) . '">' . "\n";

		if ( isset( $data['label'] ) ) {
			$r .= "\t\t" . '<span>' . $data['label'] . '</span>' . "\n";
		}

		if ( isset( $data['options'] ) ) {
			$r .= "\t\t" . '<select id="' . esc_attr( $key ) . '" name="' . esc_attr( $key ) . '"';
			if ( isset( $data['onchange'] ) ) {
				$r .= ' onchange="' . esc_attr( $data['onchange'] ) . '"';
			}
			$this->tabindex++;
			$r .= ' tabindex="' . $this->tabindex . '">' . "\n";

			foreach ( $data['options'] as $value => $display ) {
				if ( $data['value'] == $value ) {
					$selected = ' selected="selected"';
				} else {
					$selected = '';
				}

				$r .= "\t\t\t" . '<option value="' . esc_attr( $value ) . '"' . $selected . '>' . esc_html( $display ) . '</option>' . "\n";
			}

			$r .= "\t\t" . '</select>';
		}

		if ( isset( $data['note'] ) ) {
			$r .= "\t\t" . '<a class="note-toggle" href="javascript:void(0);" onclick="toggleNote(\'note-' . esc_attr( $key ) . '\');">?</a>' . "\n";
			$r .= "\t\t" . '<p id="note-' . esc_attr( $key ) . '" class="note" style="display:none">' . $data['note'] . '</p>' . "\n";
		}

		$r .= "\t\t" . '<div class="clear"></div>' . "\n";
		$r .= "\t" . '</label>' . "\n";

		echo $r;
	}

	/**
	 * Prints an appropriate language selection form element if there are any available.
	 *
	 * @return void
	 **/
	function select_language()
	{
		if ( count( $this->languages ) > 1 ) {
			$this->data[1]['form']['bb_lang']['value'] = $this->language;
			$this->data[1]['form']['bb_lang']['options'] = $this->languages;
			$this->select( 'bb_lang' );
		} else {
			$this->data[1]['form']['bb_lang']['value'] = 'en_US';
			$this->input_hidden( 'bb_lang' );
		}
	}

	/**
	 * Prints an input checkbox which controls display of an optional section of settings.
	 *
	 * @param string $key The identifier of the area to be toggled.
	 * @return void
	 **/
	function input_toggle( $key )
	{
		$data = $this->data[$this->step]['form'][$key];

		$class = '';
		$classes = array( 'for-toggle' );
		if ( isset( $data['note'] ) ) {
			$classes[] = 'has-note';
		}
		if ( isset( $data['label'] ) ) {
			$classes[] = 'has-label';
		}

		$onclick = 'toggleBlock(this, \'' . esc_js( $key . '_target' ) . '\' );';
		if ( isset( $data['toggle_value'] ) ) {
			$onclick .= ' toggleValue(this, \'' . esc_js( $data['toggle_value']['target'] ) . '\', \'' . esc_js( $data['toggle_value']['off_value'] ) . '\', \'' . esc_js( $data['toggle_value']['on_value'] ) . '\' );';
		}

		$checked = $data['checked'] ? ' ' . trim( $data['checked'] ) : '';

		if ( isset( $this->strings[$this->step]['form_errors'][$key] ) ) {
			$classes[] = 'error';
		}
		if ( count( $classes ) ) {
			$class = ' class="' . join( ' ', $classes ) . '"';
		}

		$r = "\t" . '<label id="label-' . esc_attr( $key ) . '"' . $class . ' for="' . esc_attr( $key ) . '">' . "\n";

		$r .= "\t\t" . '<span>' . "\n";
		$this->tabindex++;
		$r .= "\t\t\t" . '<input type="checkbox" id="' . esc_attr( $key ) . '" name="' . esc_attr( $key ) . '" class="checkbox" onclick="' . esc_attr( $onclick ) . '"' . $checked . ' value="1" tabindex="' . $this->tabindex . '" />' . "\n";
		if ( isset( $data['label'] ) ) {
			$r .= "\t\t\t" . $data['label'] . "\n";
		}
		$r .= "\t\t" . '</span>' . "\n";

		if ( isset( $data['note'] ) ) {
			$r .= "\t\t" . '<a class="note-toggle" href="javascript:void(0);" onclick="toggleNote(\'note-' . esc_attr( $key ) . '\');">?</a>' . "\n";
			$r .= "\t\t" . '<p id="note-' . esc_attr( $key ) . '" class="note" style="display:none">' . $data['note'] . '</p>' . "\n";
		}

		$r .= "\t\t" . '<div class="clear"></div>' . "\n";
		$r .= "\t" . '</label>' . "\n";

		echo $r;
	}

	/**
	 * Prints the input buttons which post each step and optionally go back a step.
	 *
	 * @param string $forward The HTML element ID of the forward button.
	 * @param string $back Optional. The HTML element ID of the back button.
	 * @return void
	 **/
	function input_buttons( $forward, $back = false, $step = false )
	{
		$data_back = $back ? $this->data[$this->step]['form'][$back] : false;
		$data_forward = $this->data[$this->step]['form'][$forward];

		$r = '<fieldset class="buttons">' . "\n";

		if ( !$step ) {
			$step = $this->step;
		}
		$r .= "\t" . '<input type="hidden" id="step" name="step" value="' . (int) $step . '" />' . "\n";

		if ( $back) {
			$r .= "\t" . '<label id="label-' . esc_attr( $back ) . '" for="' . esc_attr( $back ) . '" class="back">' . "\n";
			$this->tabindex++;
			$r .= "\t\t" . '<input type="submit" id="' . esc_attr( $back ) . '" name="' . esc_attr( $back ) . '" class="button" value="' . esc_attr( $data_back['value'] ) . '" tabindex="' . $this->tabindex . '" />' . "\n";
			$r .= "\t" . '</label>' . "\n";
		}

		$r .= "\t" . '<label id="label-' . esc_attr( $forward ) . '" for="' . esc_attr( $forward ) . '" class="forward">' . "\n";
		$this->tabindex++;
		$r .= "\t\t" . '<input type="submit" id="' . esc_attr( $forward ) . '" name="' . esc_attr( $forward ) . '" class="button" value="' . esc_attr( $data_forward['value'] ) . '" tabindex="' . $this->tabindex . '" />' . "\n";
		$r .= "\t" . '</label>' . "\n";

		$r .= '</fieldset>' . "\n";

		echo $r;
	}

	/**
	 * Prints hidden input elements containing the data inputted in a given step.
	 *
	 * @param integer $step Optional. The number of the step whose hidden inputs should be printed.
	 * @return void
	 **/
	function hidden_step_inputs( $step = false )
	{
		if ( !$step ) {
			$step = $this->step;
		} elseif ( $step !== $this->step ) {
			$this->inject_form_values_into_data( $step );
		}

		$data = $this->data[$step]['form'];

		$r = '<fieldset>' . "\n";

		foreach ( $data as $key => $value ) {
			if ( 'forward_' !== substr( $key, 0, 8 ) && 'back_' !== substr( $key, 0, 5 ) ) {
				$r .= "\t" . '<input type="hidden" name="' . esc_attr( $key ) . '" value="' . esc_attr( $value['value'] ) . '" />' . "\n";
			}
		}

		$r .= '</fieldset>' . "\n";

		echo $r;
	}

	/**
	 * Rewrites the admin user input into a select element containing existing WordPress administrators.
	 *
	 * @return boolean True if the select element was created, otherwise false.
	 **/
	function populate_keymaster_user_login_from_user_tables()
	{
		$data =& $this->data[3]['form']['keymaster_user_login'];

		// Get the existing WordPress admin users

		// Setup variables and constants if available
		global $bb;
		if ( !empty( $this->data[2]['form']['wp_table_prefix']['value'] ) ) {
			$bb->wp_table_prefix = $this->data[2]['form']['wp_table_prefix']['value'];
		}
		if ( !empty( $this->data[2]['form']['user_bbdb_name']['value'] ) ) {
			$bb->user_bbdb_name = $this->data[2]['form']['user_bbdb_name']['value'];
		}
		if ( !empty( $this->data[2]['form']['user_bbdb_user']['value'] ) ) {
			$bb->user_bbdb_user = $this->data[2]['form']['user_bbdb_user']['value'];
		}
		if ( !empty( $this->data[2]['form']['user_bbdb_password']['value'] ) ) {
			$bb->user_bbdb_password = $this->data[2]['form']['user_bbdb_password']['value'];
		}
		if ( !empty( $this->data[2]['form']['user_bbdb_host']['value'] ) ) {
			$bb->user_bbdb_host = $this->data[2]['form']['user_bbdb_host']['value'];
		}
		if ( !empty( $this->data[2]['form']['user_bbdb_charset']['value'] ) ) {
			$bb->user_bbdb_charset = preg_replace( '/[^a-z0-9_-]/i', '', $this->data[2]['form']['user_bbdb_charset']['value'] );
		}
		if ( !empty( $this->data[2]['form']['user_bbdb_collate']['value'] ) ) {
			$bb->user_bbdb_charset = preg_replace( '/[^a-z0-9_-]/i', '', $this->data[2]['form']['user_bbdb_collate']['value'] );
		}
		if ( !empty( $this->data[2]['form']['custom_user_table']['value'] ) ) {
			$bb->custom_user_table = preg_replace( '/[^a-z0-9_-]/i', '', $this->data[2]['form']['custom_user_table']['value'] );
		}
		if ( !empty( $this->data[2]['form']['custom_user_meta_table']['value'] ) ) {
			$bb->custom_user_meta_table = preg_replace( '/[^a-z0-9_-]/i', '', $this->data[2]['form']['custom_user_meta_table']['value'] );
		}

		global $bbdb;
		global $bb_table_prefix;

		// Resolve the custom user tables for bpdb
		bb_set_custom_user_tables();

		if ( isset( $bb->custom_databases ) && isset( $bb->custom_databases['user'] ) ) {
			$bbdb->add_db_server( 'user', $bb->custom_databases['user'] );
		}

		// Add custom tables if required
		if ( isset( $bb->custom_tables['users'] ) || isset( $bb->custom_tables['usermeta'] ) ) {
			$bbdb->tables = array_merge( $bbdb->tables, $bb->custom_tables );
			if ( is_wp_error( $bbdb->set_prefix( $bb_table_prefix ) ) ) {
				die( __( 'Your user table prefix may only contain letters, numbers and underscores.' ) );
			}
		}

		$bb_keymaster_meta_key       = $bbdb->escape( $bb_table_prefix . 'capabilities' );
		$wp_administrator_meta_key   = $bbdb->escape( $bb->wp_table_prefix . 'capabilities' );
		if ( !empty( $this->data[2]['form']['wordpress_mu_primary_blog_id']['value'] ) ) {
			$wp_administrator_meta_key = $bb->wp_table_prefix . $this->data[2]['form']['wordpress_mu_primary_blog_id']['value'] . '_capabilities';
		}

		$keymaster_query = <<<EOQ
			SELECT
				user_login, user_email, display_name
			FROM
				$bbdb->users
			LEFT JOIN
				$bbdb->usermeta ON
				$bbdb->users.ID = $bbdb->usermeta.user_id
			WHERE
				(
					(
						meta_key = '$wp_administrator_meta_key' AND
						meta_value LIKE '%administrator%'
					) OR
					(
						meta_key = '$bb_keymaster_meta_key' AND
						meta_value LIKE '%keymaster%'
					)
				) AND
				user_email IS NOT NULL AND
				user_email != ''
			ORDER BY
				user_login;
EOQ;
		$bbdb->suppress_errors();

		if ( $keymasters = $bbdb->get_results( $keymaster_query, ARRAY_A ) ) {

			$bbdb->suppress_errors( false );

			if ( count( $keymasters ) ) {
				$email_maps = '';
				$data['options']  = array();
				$data['onchange'] = 'changeKeymasterEmail( this, \'keymaster_user_email\' );';
				$data['note']     = __( 'Please select an existing bbPress Keymaster or WordPress administrator.' );

				$data['options'][''] = '';
				foreach ( $keymasters as $keymaster ) {
					$email_maps .= 'emailMap[\'' . $keymaster['user_login'] . '\'] = \'' . $keymaster['user_email'] . '\';' . "\n\t\t\t\t\t\t\t\t";
					if ( $keymaster['display_name'] ) {
						$data['options'][$keymaster['user_login']] = $keymaster['user_login'] . ' (' . $keymaster['display_name'] . ')';
					} else {
						$data['options'][$keymaster['user_login']] = $keymaster['user_login'];
					}
				}

				$this->strings[3]['scripts']['changeKeymasterEmail'] = <<<EOS
						<script type="text/javascript" charset="utf-8">
							function changeKeymasterEmail( selectObj, target ) {
								var emailMap = new Array;
								emailMap[''] = '';
								$email_maps
								var targetObj = document.getElementById( target );
								var selectedAdmin = selectObj.options[selectObj.selectedIndex].value;
								targetObj.value = emailMap[selectedAdmin];
							}
						</script>
EOS;

				$this->data[3]['form']['keymaster_user_type']['value'] = 'old';

				return true;
			}
		}

		$bbdb->suppress_errors( false );

		return false;
	}

	/**
	 * Sends HTTP headers and prints the page header.
	 *
	 * @return void
	 **/
	function header()
	{
		nocache_headers();

		bb_install_header( $this->strings[$this->step]['title'], $this->strings[$this->step]['h1'], true );
	}

	/**
	 * Prints the page footer.
	 *
	 * @return void
	 **/
	function footer()
	{
		bb_install_footer();
	}

	/**
	 * Prints the returned messages for the current step.
	 *
	 * @return void
	 **/
	function messages()
	{
		if ( isset( $this->strings[$this->step]['messages'] ) ) {
			$messages = $this->strings[$this->step]['messages'];

			// This count works as long as $messages is only two-dimensional
			$count = ( count( $messages, COUNT_RECURSIVE ) - count( $messages ) );
			$i = 0;
			$r = '';
			foreach ( $messages as $type => $paragraphs ) {
				$class = $type ? $type : '';

				foreach ( $paragraphs as $paragraph ) {
					$i++;
					$class = ( $i === $count ) ? ( $class . ' last' ) : $class;
					$r .= '<p class="' . esc_attr( $class ) . '">' . $paragraph . '</p>' . "\n";
				}
			}
			echo $r;
		}
	}

	/**
	 * Prints the introduction paragraphs for the current step.
	 *
	 * @return void
	 **/
	function intro()
	{
		if ( 'incomplete' == $this->step_status[$this->step] && isset( $this->strings[$this->step]['intro'] ) ) {
			$messages = $this->strings[$this->step]['intro'];
			$count = count( $messages );
			$i = 0;
			$r = '';
			foreach ( $messages as $paragraph ) {
				$i++;
				$class = ( $i === $count ) ? 'intro last' : 'intro';
				$r .= '<p class="' . $class . '">' . $paragraph . '</p>' . "\n";
			}
			echo $r;
		}
	}

	/**
	 * Prints the standard header for each step.
	 *
	 * @param integer $step The number of the step whose header should be printed.
	 * @return void
	 **/
	function step_header( $step )
	{
		$class = ( $step == $this->step ) ? 'open' : 'closed';

		$r = '<div id="' . esc_attr( 'step' . $step ) . '" class="' . $class . '">' . "\n";
		$r .= '<h2 class="' . $class . '">' . $this->strings[$step]['h2'] . '</h2>' . "\n";
		$r .= '<div>' . "\n";

		if ( $step < $this->step && $this->strings[$step]['status'] ) {
			$r .= '<p class="status">' . $this->strings[$step]['status'] . '</p>' . "\n";
		}

		echo $r;

		if ( $step == $this->step ) {
			$this->intro();
		}

		$this->tabindex = 0;
	}

	/**
	 * Prints the standard step footer.
	 *
	 * @return void
	 **/
	function step_footer()
	{
		$r = '</div></div>' . "\n";

		echo $r;
	}
} // END class BB_Install
