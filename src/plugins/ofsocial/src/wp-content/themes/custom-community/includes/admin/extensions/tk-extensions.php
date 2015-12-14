<?php

/** 
 * The Theme Extensions Admin Page 
 * Based on the Example of Otto @WordPress.org - Thanks Otto! 
 * http://ottopress.com/2012/themeplugin-dependencies/
 * 
 * ***********************************************************  
 */


/**
 * Adding the Admin Page
 * @author Sven Lehnert, Konrad Sroka   
 */
 
add_action( 'admin_menu', 'tk_extensions_admin_menu' );

function tk_extensions_admin_menu() {

    add_theme_page( 'CC Extensions', 'CC Extensions', 'edit_theme_options', 'cc-extensions-options', 'tk_extensions_screen' );
	
}

add_action('init', 'tk_extentions_include');

function tk_extentions_include(){
	include( dirname(__FILE__) . '/plugin-dependency.php' );
}

/**
 * cc Extensions Admin Page
 * @author Sven Lehnert, Konrad Sroka   
 */
 
function tk_extensions_screen() { ?>
	<style>
		div.wrap h2 { margin-bottom: 10px; }
		p.bordered { margin-bottom: 50px; }

		/** free extensions section **/ 
		table.plugins td p { padding: 5px 5px 3px 5px; }		

		/** premium extensions section **/
		#themekraft-shop { margin-top: 50px; }
		
		
	</style>
    <div class="wrap">
        <div id="icon-themes" class="icon32"><br></div>
        <h2>CC Extensions</h2>
        <p style="font-size: 15px; margin-bottom: 30px;"><i>Free and Premium Extensions for your Custom Community 2 Theme.</i></p>
        <div class="clear"></div>
         
        <h2><b><i>Free</i></b> Extensions and Supported Plugins</h2>

        <form method="post" action="options.php">
        	<table class="wp-list-table widefat plugins">
        		<tbody id="the-list">
		            <?php 
		            // adding each free extension - handpicked for you ;) 
					add_free_extension( 'Jetpack by WordPress.com -', 'jetpack', 'http://wordpress.org/extend/plugins/jetpack/' );
					add_free_extension( 'BuddyPress - Social Network Component for WordPress -', 'buddypress', 'http://buddypress.org' );
		           	add_free_extension( 'bbPress - Forums Component for WordPress -', 'bbpress', 'http://bbpress.org/' );
					add_free_extension( 'WP-PageNavi - Page Navigation -', 'wp-pagenavi', 'http://wordpress.org/extend/plugins/wp-pagenavi/' );
					add_free_extension( 'Simple Social Icons', 'simple-social-icons', 'http://www.studiopress.com/plugins/simple-social-icons' );
					add_free_extension( 'NextGEN Gallery', 'nextgen-gallery', 'http://www.nextgen-gallery.com/' );
					add_free_extension( 'WooCommerce', 'woocommerce', 'http://www.nextgen-gallery.com/' );
					?>
				</tbody>
			</table>
        </form>
		
		<div id="fbstream"></div> 
		
		<div id="themekraft-shop">
			<h2><b><i>Premium</i></b> Extensions and Supported Plugins</h2> 	
			<iframe src="http://themekraft.com/products/wordpress-plugins-free-and-premium/wordpress-theme-extensions-free-and-premium/?post_type=product&fbtab" height="1000" width="100%" name="ThemeKraft Shop">ThemeKraft Shop</iframe>
		</div>
        
    </div><!-- end .wrap --><?php

}


/**
 * Adding a free extensions to the list
 * @author Sven Lehnert, Konrad Sroka   
 */
 
function add_free_extension( $name, $slug, $url ) {
    $tpd = new Theme_Plugin_Dependency( $slug, $url ); ?>

    <tr class="<?php if( $tpd->check_active() ) { echo "active"; } else { echo "inactive"; } ?>">
    	<td><p>
		    <?php
		    // echo '<pre>';
		    // print_r($tpd); 
		    // echo '/<pre>';
		    if( $tpd->check_active() ) { 
		        echo $name.' is installed and activated!';
		    } else if( $tpd->check() ) { 
		        echo $name.' is installed, but not activated. <a href="'.$tpd->activate_link().'">Click here to activate the plugin.</a>';
		    } else if( $install_link = $tpd->install_link() ) {
		        echo $name.' is not installed. <a href="'.$install_link.'">Click here to install the plugin.</a>';
		    } else { 
		        echo $name.' is not installed and could not be found in the plugin directory. Please install this plugin manually.'; 
		    } ?> 
		</td></p>
    </tr><?php 
	
}

/**
 * Enqueue the extensions JS
 * @author Sven Lehnert   
 */
 
add_action('admin_enqueue_scripts', 'tk_extensions_js');

function tk_extensions_js(){
	wp_enqueue_script( 'jquery' );
	wp_enqueue_script( 'tk_extensions_js', get_template_directory_uri() . '/admin/extensions/js/cc-extensions.js' );
}

?>