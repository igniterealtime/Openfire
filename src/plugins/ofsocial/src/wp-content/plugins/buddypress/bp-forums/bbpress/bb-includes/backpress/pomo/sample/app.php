<?php

require_once '../translations.php';
require_once '../mo.php';


function __($text, $domain = 'default') {
	$translations = &get_translations($domain);
	return $translations->translate($text);
}

function _e($text, $domain = 'default') {
	$translations = &get_translations($domain);
	echo $translations->translate($text);
}

function __n($singular, $plural, $count, $domain = 'default') {
	$translations = &get_translations($domain);
	return $translations->translate_plural($singular, $plural, $count);
}

function &load_translations($mo_filename) {
	if (is_readable($mo_filename)) {
		$translations = new MO();
		$translations->import_from_file($mo_filename);
	} else {
		$translations = new Translations();
	}
	return $translations;
}

// get the locale from somewhere: subomain, config, GET var, etc.
// it can be safely empty
$locale = 'bg';
$translations = array();
$empty_translations = & new Translations();

function load_textdomain($domain, $mofile) {
	global $translations;
	$translations[$domain] = &load_translations($mofile);
}

function &get_translations($domain) {
	global $translations, $empty_translations;
	return isset($translations[$domain])? $translations[$domain] : $empty_translations;
}

// load the translations
load_textdomain('default', "languages/$locale.mo");
load_textdomain('side', "languages/$locale-side.mo");

//here comes the real app
$user = 'apok';
$messages = rand(0, 2);

printf(__('Welcome %s!')."\n", $user);

printf(__n('You have one new message.', 'You have %s new messages.', $messages)."\n", $messages);

echo __("A string with low priority!", 'side')."\n";

_e("Bye\n");
?>
