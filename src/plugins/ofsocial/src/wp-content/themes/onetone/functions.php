<?php

define( 'ONETONE_THEME_BASE_URL', get_template_directory_uri());
define( 'ONETONE_OPTIONS_FRAMEWORK', get_template_directory().'/admin/' ); 
define( 'ONETONE_OPTIONS_FRAMEWORK_URI',  ONETONE_THEME_BASE_URL. '/admin/'); 
define('ONETONE_OPTIONS_PREFIXED' ,'onetone_');

define( 'OPTIONS_FRAMEWORK_DIRECTORY', get_template_directory_uri() . '/admin/' );
require_once dirname( __FILE__ ) . '/admin/options-framework.php';
require_once get_template_directory() . '/includes/admin-options.php';

/**
 * Required: include options framework.
 **/
load_template( trailingslashit( get_template_directory() ) . 'admin/options-framework.php' );

/**
 * Mobile Detect Library
 **/
 if(!class_exists("Mobile_Detect")){
   load_template( trailingslashit( get_template_directory() ) . 'includes/Mobile_Detect.php' );
 }
/**
 * Theme setup
 **/
 
load_template( trailingslashit( get_template_directory() ) . 'includes/theme-setup.php' );

/**
 * Theme Functions
 **/
 
load_template( trailingslashit( get_template_directory() ) . 'includes/theme-functions.php' );

/**
 * Theme breadcrumb
 **/
load_template( trailingslashit( get_template_directory() ) . 'includes/class-breadcrumb.php');
/**
 * Theme widget
 **/
 
load_template( trailingslashit( get_template_directory() ) . 'includes/theme-widget.php' );
