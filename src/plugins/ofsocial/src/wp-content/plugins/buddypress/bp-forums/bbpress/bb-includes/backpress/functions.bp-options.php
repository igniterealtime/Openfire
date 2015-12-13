<?php

function backpress_get_option( $option )
{
	if ( !class_exists('BP_Options') ) {
		return;
	}

	return BP_Options::get( $option );
}

function backpress_add_option( $option, $value )
{
	if ( !class_exists('BP_Options') ) {
		return;
	}

	return BP_Options::add( $option, $value );
}

function backpress_update_option( $option, $value )
{
	if ( !class_exists('BP_Options') ) {
		return;
	}

	return BP_Options::update( $option, $value );
}

function backpress_delete_option( $option )
{
	if ( !class_exists('BP_Options') ) {
		return;
	}

	return BP_Options::delete( $option );
}

function backpress_get_transient( $transient )
{
	if ( !class_exists('BP_Transients') ) {
		return;
	}

	return BP_Transients::get( $transient );
}

function backpress_set_transient( $transient, $value, $expiration = 0 )
{
	if ( !class_exists('BP_Transients') ) {
		return;
	}

	return BP_Transients::set( $transient, $value, $expiration );
}

function backpress_delete_transient( $transient )
{
	if ( !class_exists('BP_Transients') ) {
		return;
	}

	return BP_Transients::delete( $transient );
}