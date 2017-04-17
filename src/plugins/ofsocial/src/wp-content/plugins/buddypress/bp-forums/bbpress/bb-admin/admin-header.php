<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml"<?php bb_language_attributes( '1.1' ); ?>>
<head>
	<meta http-equiv="X-UA-Compatible" content="IE=8" />
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
	<title><?php bb_admin_title() ?></title>
	<link rel="stylesheet" href="<?php bb_uri('bb-admin/style.css', null, BB_URI_CONTEXT_LINK_STYLESHEET_HREF + BB_URI_CONTEXT_BB_ADMIN); ?>" type="text/css" />
<?php if ( 'rtl' == bb_get_option( 'text_direction' ) ) : ?>
	<link rel="stylesheet" href="<?php bb_uri('bb-admin/style-rtl.css', null, BB_URI_CONTEXT_LINK_STYLESHEET_HREF + BB_URI_CONTEXT_BB_ADMIN); ?>" type="text/css" />
<?php endif; do_action('bb_admin_print_scripts'); ?>
	<!--[if IE 6]>
	<style type="text/css">
	ul#bbAdminMenu{ margin: 15px 5px 15px -85px; } body.bb-menu-folded div#bbBody{ margin-left: 110px; }
	</style>
	<![endif]-->
	<link rel="shortcut icon" type="image/ico" href="<?php bb_uri('bb-admin/images/favicon.ico', null, BB_URI_CONTEXT_BB_ADMIN); ?>" />
	<script type="text/javascript">
		//<![CDATA[
		addLoadEvent = function(func){if(typeof jQuery!="undefined")jQuery(document).ready(func);else if(typeof wpOnload!='function'){wpOnload=func;}else{var oldonload=wpOnload;wpOnload=function(){oldonload();func();}}};
		var userSettings = {'url':'<?php echo $bb->cookie_path; ?>','uid':'<?php if ( ! isset($bb_current_user) ) $bb_current_user = bb_get_current_user(); echo $bb_current_user->ID; ?>','time':'<?php echo time(); ?>'};
		//]]>
	</script>
<?php do_action( 'bb_admin_head' ); ?>
</head>

<?php
global $bb_admin_body_class;
if ( 'f' == bb_get_user_setting( 'fm' ) ) {
	$bb_admin_body_class .= ' bb-menu-folded';
}
?>

<body class="bb-admin no-js <?php echo trim( $bb_admin_body_class ); ?>">
	<script type="text/javascript">
		//<![CDATA[
		(function(){
			var c = document.body.className;
			c = c.replace(/no-js/, 'js');
			document.body.className = c;
		})();
		//]]>
	</script>
	<div id="bbWrap">
		<div id="bbContent">
			<div id="bbHead">
				<h1><a href="<?php bb_uri(); ?>"><span><?php bb_option('name'); ?></span> <em><?php _e('Visit Site'); ?></em></a></h1>
				<div id="bbUserInfo">
					<p>
						<?php printf( __('Howdy, %1$s'), bb_get_profile_link( array( 'text' => bb_get_current_user_info( 'name' ) ) ) );?>
						| <?php bb_logout_link( array( 'redirect' => bb_get_uri( null, null, BB_URI_CONTEXT_HEADER ) ) ); ?>
					</p>
				</div>
			</div>

			<div id="bbBody">

<?php bb_admin_menu(); ?>