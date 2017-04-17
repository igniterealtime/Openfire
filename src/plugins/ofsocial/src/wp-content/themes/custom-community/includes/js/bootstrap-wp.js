// first set the body to hide and show everyhthing when fully loaded ;)
//document.write("<style>body{display:none;}</style>");
/**
 * Use specific body classes to fade out the body during the required changes, and fade it back in after they are done
 */
//document.body.insertAdjacentHTML('afterbegin', '<div class="curtain-loader"></div>');

jQuery('html').removeClass('no-js').addClass('js loading-stage');

jQuery(function() {
	
    //jQuery( 'input.search-field' ).addClass( 'form-control' );
	
	// here for each comment reply link of wordpress
	//jQuery( '.comment-reply-link' ).addClass( 'btn btn-primary' );

	// here for the submit button of the comment reply form
	//jQuery( '#commentsubmit' ).addClass( 'btn btn-primary' );	
	
	// The WordPress Default Widgets 
	// Now we'll add some classes for the wordpress default widgets - let's go  
	
	// the search widget
	/**
	 * NOTE: Obsolete, as the search widget defaults to using get_search_form() - and the theme already supplies that with searchform.php
	 */
	/*
	jQuery( 'input.search-field' ).addClass( 'form-control' );
	jQuery( 'input.search-submit' ).addClass( 'btn btn-default' );
	*/
	
	jQuery( '.widget_rss ul' ).addClass( 'media-list' );
	
	jQuery( '.sidebar .widget_meta ul, .sidebar .widget_recent_entries ul, .sidebar .widget_archive ul, .sidebar .widget_categories ul, .sidebar .widget_nav_menu ul, .sidebar .widget_pages ul' ).addClass( 'nav' );

	// reimplement with custom body class

	/*jQuery( '.widget_recent_comments ul#recentcomments' ).css( {
		'list-style': 'none',
		'padding-left': '0' 
	} );*/
	//jQuery( '.widget_recent_comments ul#recentcomments li' ).css( 'padding', '5px 15px');
	
	
	jQuery( 'table#wp-calendar' ).addClass( 'table table-striped');
	
	jQuery(document.body).show();

	/**
	 * NOTE: Please add all NON-styling-related functionalities AFTER the body-show call!
	 */

	jQuery('.cc-slider-bubble-wrap').on('mouseover', function() {
		jQuery( '.cc-slider-bubble-title' ).addClass( 'tada');
	});
	
	jQuery('.cc-slider-bubble-wrap').on('mouseout', function() {
		jQuery( '.cc-slider-bubble-title' ).removeClass( 'tada' );
	});
	
});



