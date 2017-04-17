<?php
/**
 * bbPress Translation API
 *
 * @package bbPress
 * @subpackage i18n
 */

/**
 * Gets the current locale.
 *
 * If the locale is set, then it will filter the locale in the 'locale' filter
 * hook and return the value.
 *
 * If the locale is not set already, then the BB_LANG constant is used if it is
 * defined. Then it is filtered through the 'locale' filter hook and the value
 * for the locale global set and the locale is returned.
 *
 * The process to get the locale should only be done once but the locale will
 * always be filtered using the 'locale' hook.
 *
 * @since 0.7.2
 * @uses apply_filters() Calls 'locale' hook on locale value.
 * @uses $locale Gets the locale stored in the global.
 *
 * @return string The locale of the blog or from the 'locale' hook.
 */
function bb_get_locale() {
	global $locale;

	if (isset($locale))
		return apply_filters( 'locale', $locale );

	// BB_LANG is defined in bb-config.php
	if (defined('BB_LANG'))
		$locale = BB_LANG;

	if (empty($locale))
		$locale = 'en_US';

	return apply_filters('locale', $locale);
}

if ( !function_exists( 'get_locale' ) ) :
function get_locale() {
	return bb_get_locale();
}
endif;

if ( !function_exists( 'translate' ) ) :
/**
 * Retrieves the translation of $text. If there is no translation, or
 * the domain isn't loaded the original text is returned.
 *
 * @see __() Don't use translate() directly, use __()
 * @since 1.0
 * @uses apply_filters() Calls 'gettext' on domain translated text
 *		with the untranslated text as second parameter.
 *
 * @param string $text Text to translate.
 * @param string $domain Domain to retrieve the translated text.
 * @return string Translated text
 */
function translate( $text, $domain = 'default' ) {
	$translations = &get_translations_for_domain( $domain );
	return apply_filters('gettext', $translations->translate($text), $text, $domain);
}
endif;

if ( !function_exists( 'before_last_bar' ) ) :
/**
 * @since 1.0
 */
function before_last_bar( $string ) {
	$last_bar = strrpos( $string, '|' );
	if ( false == $last_bar )
		return $string;
	else
		return substr( $string, 0, $last_bar );
}
endif;

if ( !function_exists( 'translate_with_context' ) ) :
/**
 * Translate $text like translate(), but assumes that the text
 * contains a context after its last vertical bar.
 *
 * @since 1.0
 * @uses translate()
 *
 * @param string $text Text to translate
 * @param string $domain Domain to retrieve the translated text
 * @return string Translated text
 */
function translate_with_context( $text, $domain = 'default' ) {
	return before_last_bar( translate( $text, $domain ) );

}
endif;

if ( !function_exists( 'translate_with_gettext_context' ) ) :
/**
 * @since 1.0
 */
function translate_with_gettext_context( $text, $context, $domain = 'default' ) {
	$translations = &get_translations_for_domain( $domain );
	return apply_filters( 'gettext_with_context', $translations->translate( $text, $context ), $text, $context, $domain);
}
endif;

if ( !function_exists( '__' ) ) :
/**
 * Retrieves the translation of $text. If there is no translation, or
 * the domain isn't loaded the original text is returned.
 *
 * @see translate() An alias of translate()
 * @since 0.7.2
 *
 * @param string $text Text to translate
 * @param string $domain Optional. Domain to retrieve the translated text
 * @return string Translated text
 */
function __( $text, $domain = 'default' ) {
	return translate( $text, $domain );
}
endif;

if ( !function_exists( 'esc_attr__' ) ) :
/**
 * Retrieves the translation of $text and escapes it for safe use in an attribute.
 * If there is no translation, or the domain isn't loaded the original text is returned.
 *
 * @see translate() An alias of translate()
 * @see esc_attr()
 * @since 1.0
 *
 * @param string $text Text to translate
 * @param string $domain Optional. Domain to retrieve the translated text
 * @return string Translated text
 */
function esc_attr__( $text, $domain = 'default' ) {
	return esc_attr( translate( $text, $domain ) );
}
endif;

if ( !function_exists( 'esc_html__' ) ) :
/**
 * Retrieves the translation of $text and escapes it for safe use in HTML output.
 * If there is no translation, or the domain isn't loaded the original text is returned.
 *
 * @see translate() An alias of translate()
 * @see esc_html()
 * @since 1.0
 *
 * @param string $text Text to translate
 * @param string $domain Optional. Domain to retrieve the translated text
 * @return string Translated text
 */
function esc_html__( $text, $domain = 'default' ) {
	return esc_html( translate( $text, $domain ) );
}
endif;

if ( !function_exists( '_e' ) ) :
/**
 * Displays the returned translated text from translate().
 *
 * @see translate() Echos returned translate() string
 * @since 0.7.2
 *
 * @param string $text Text to translate
 * @param string $domain Optional. Domain to retrieve the translated text
 */
function _e( $text, $domain = 'default' ) {
	echo translate( $text, $domain );
}
endif;

if ( !function_exists( 'esc_attr_e' ) ) :
/**
 * Displays translated text that has been escaped for safe use in an attribute.
 *
 * @see translate() Echoes returned translate() string
 * @see esc_attr()
 * @since 1.0
 *
 * @param string $text Text to translate
 * @param string $domain Optional. Domain to retrieve the translated text
 */
function esc_attr_e( $text, $domain = 'default' ) {
	echo esc_attr( translate( $text, $domain ) );
}
endif;

if ( !function_exists( 'esc_html_e' ) ) :
/**
 * Displays translated text that has been escaped for safe use in HTML output.
 *
 * @see translate() Echoes returned translate() string
 * @see esc_html()
 * @since 1.0
 *
 * @param string $text Text to translate
 * @param string $domain Optional. Domain to retrieve the translated text
 */
function esc_html_e( $text, $domain = 'default' ) {
	echo esc_html( translate( $text, $domain ) );
}
endif;

if ( !function_exists( '_c' ) ) :
/**
 * Retrieve translated string with vertical bar context
 *
 * Quite a few times, there will be collisions with similar translatable text
 * found in more than two places but with different translated context.
 *
 * In order to use the separate contexts, the _c() function is used and the
 * translatable string uses a pipe ('|') which has the context the string is in.
 *
 * When the translated string is returned, it is everything before the pipe, not
 * including the pipe character. If there is no pipe in the translated text then
 * everything is returned.
 *
 * @since 1.0
 *
 * @param string $text Text to translate
 * @param string $domain Optional. Domain to retrieve the translated text
 * @return string Translated context string without pipe
 */
function _c($text, $domain = 'default') {
	return translate_with_context($text, $domain);
}
endif;

if ( !function_exists( '_x' ) ) :
/**
 * @since 1.0
 */
function _x( $single, $context, $domain = 'default' ) {
	return translate_with_gettext_context( $single, $context, $domain );
}
endif;

if ( !function_exists( 'esc_attr_x' ) ) :
function esc_attr_x( $single, $context, $domain = 'default' ) {
	return esc_attr( translate_with_gettext_context( $single, $context, $domain ) );
}
endif;

if ( !function_exists( '__ngettext' ) ) :
/**
 * @deprecated Use _n()
 */
function __ngettext() {
	//_deprecated_function( __FUNCTION__, '2.8', '_n()' );
	$args = func_get_args();
	return call_user_func_array('_n', $args);
}
endif;

if ( !function_exists( '_n' ) ) :
/**
 * Retrieve the plural or single form based on the amount.
 *
 * If the domain is not set in the $l10n list, then a comparsion will be made
 * and either $plural or $single parameters returned.
 *
 * If the domain does exist, then the parameters $single, $plural, and $number
 * will first be passed to the domain's ngettext method. Then it will be passed
 * to the 'ngettext' filter hook along with the same parameters. The expected
 * type will be a string.
 *
 * @since 1.0
 * @uses $l10n Gets list of domain translated string (gettext_reader) objects
 * @uses apply_filters() Calls 'ngettext' hook on domains text returned,
 *		along with $single, $plural, and $number parameters. Expected to return string.
 *
 * @param string $single The text that will be used if $number is 1
 * @param string $plural The text that will be used if $number is not 1
 * @param int $number The number to compare against to use either $single or $plural
 * @param string $domain Optional. The domain identifier the text should be retrieved in
 * @return string Either $single or $plural translated text
 */
function _n($single, $plural, $number, $domain = 'default') {
	$translations = &get_translations_for_domain( $domain );
	$translation = $translations->translate_plural( $single, $plural, $number );
	return apply_filters( 'ngettext', $translation, $single, $plural, $number );
}
endif;

if ( !function_exists( '_nc' ) ) :
/**
 * @see _n() A version of _n(), which supports contexts --
 * strips everything from the translation after the last bar
 * @since 1.0
 */
function _nc( $single, $plural, $number, $domain = 'default' ) {
	return before_last_bar( _n( $single, $plural, $number, $domain ) );
}
endif;

if ( !function_exists( '_nx' ) ) :
/**
 * @since 1.0
 */
function _nx($single, $plural, $number, $context, $domain = 'default') {
	$translations = &get_translations_for_domain( $domain );
	$translation = $translations->translate_plural( $single, $plural, $number, $context );
	return apply_filters( 'ngettext_with_context ', $translation, $single, $plural, $number, $context );
}
endif;

if ( !function_exists( '__ngettext_noop' ) ) :
/**
 * @deprecated Use _n_noop()
 */
function __ngettext_noop() {
	//_deprecated_function( __FUNCTION__, '2.8', '_n_noop()' );
	$args = func_get_args();
	return call_user_func_array('_n_noop', $args);
}
endif;

if ( !function_exists( '_n_noop' ) ) :
/**
 * Register plural strings in POT file, but don't translate them.
 *
 * Used when you want do keep structures with translatable plural strings and
 * use them later.
 *
 * Example:
 *  $messages = array(
 *  	'post' => _n_noop('%s post', '%s posts'),
 *  	'page' => _n_noop('%s pages', '%s pages')
 *  );
 *  ...
 *  $message = $messages[$type];
 *  $usable_text = sprintf(_n($message[0], $message[1], $count), $count);
 *
 * @since 1.0
 * @param $single Single form to be i18ned
 * @param $plural Plural form to be i18ned
 * @return array array($single, $plural)
 */
function _n_noop( $single, $plural ) {
	return array( $single, $plural );
}
endif;

if ( !function_exists( '_nx_noop' ) ) :
/**
 * Register plural strings with context in POT file, but don't translate them.
 *
 * @see _n_noop()
 */
function _nx_noop( $single, $plural, $context ) {
	return array( $single, $plural, $context );
}
endif;

if ( !function_exists( 'load_textdomain' ) ) :
/**
 * Loads MO file into the list of domains.
 *
 * If the domain already exists, the inclusion will fail. If the MO file is not
 * readable, the inclusion will fail.
 *
 * On success, the mofile will be placed in the $l10n global by $domain and will
 * be an gettext_reader object.
 *
 * @since 0.7.2
 * @uses $l10n Gets list of domain translated string (gettext_reader) objects
 * @uses CacheFileReader Reads the MO file
 * @uses gettext_reader Allows for retrieving translated strings
 *
 * @param string $domain Unique identifier for retrieving translated strings
 * @param string $mofile Path to the .mo file
 * @return null On failure returns null and also on success returns nothing.
 */
function load_textdomain($domain, $mofile) {
	global $l10n;

	if ( !is_readable($mofile)) return;
	
	$mo = new MO();
	$mo->import_from_file( $mofile );

	if (isset($l10n[$domain]))
		$mo->merge_with( $l10n[$domain] );
		
	$l10n[$domain] = &$mo;
}
endif;

/**
 * Loads default translated strings based on locale.
 *
 * Loads the .mo file in WP_LANG_DIR constant path from WordPress root. The
 * translated (.mo) file is named based off of the locale.
 *
 * @since 1.5.0
 */
function bb_load_default_textdomain() {
	$locale = bb_get_locale();

	$mofile = BB_LANG_DIR . "/$locale.mo";

	load_textdomain('default', $mofile);
}

if ( !function_exists( 'load_default_textdomain' ) ) :
function load_default_textdomain() {
	bb_load_default_textdomain();
}
endif;

/**
 * Loads the plugin's translated strings.
 *
 * If the path is not given then it will be the root of the plugin directory.
 * The .mo file should be named based on the domain with a dash followed by a
 * dash, and then the locale exactly.
 *
 * @since 0.7.2
 *
 * @param string $domain Unique identifier for retrieving translated strings
 * @param string $path Optional. Absolute path to folder where the .mo file resides
 */
function bb_load_plugin_textdomain($domain, $path = false) {
	$locale = bb_get_locale();

	if ( false === $path ) {
		global $bb;
		$path = $bb->plugin_locations['core']['dir'];
	}

	$mofile = rtrim( trim( $path ), " \t\n\r\0\x0B/" ) . '/'. $domain . '-' . $locale . '.mo';
	load_textdomain($domain, $mofile);
}

if ( !function_exists( 'load_plugin_textdomain' ) ) :
function load_plugin_textdomain( $domain, $path = false ) {
	bb_load_plugin_textdomain( $domain, $path );
}
endif;

/**
 * Loads the theme's translated strings.
 *
 * If the current locale exists as a .mo file in the theme's root directory, it
 * will be included in the translated strings by the $domain.
 *
 * The .mo files must be named based on the locale exactly.
 *
 * @since 0.7.2
 *
 * @param string $domain Unique identifier for retrieving translated strings
 */
function bb_load_theme_textdomain($domain, $path = false) {
	$locale = bb_get_locale();

	$mofile = ( empty( $path ) ) ? bb_get_template( $locale . '.mo' ) : "$path/$locale.mo";
	
	load_textdomain($domain, $mofile);
}

if ( !function_exists( 'load_theme_textdomain' ) ) :
function load_theme_textdomain( $domain, $path = false ) {
	bb_load_theme_textdomain( $domain, $path );
}
endif;

if ( !function_exists( 'get_translations_for_domain' ) ) :
/**
 * Returns the Translations instance for a domain. If there isn't one,
 * returns empty Translations instance.
 *
 * @param string $domain
 * @return object A Translation instance
 */
function &get_translations_for_domain( $domain ) {
	global $l10n;
	$empty =& new Translations;
	if ( isset($l10n[$domain]) )
		return $l10n[$domain];
	else
		return $empty;
}
endif;
