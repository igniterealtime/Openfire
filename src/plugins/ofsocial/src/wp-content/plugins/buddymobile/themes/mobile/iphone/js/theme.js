var $j = jQuery.noConflict();

$j(document).ready(function(){

	$j('#menu').live('touchend', function(event){
   		$j('#mobileNav').toggleClass('show top');
   		$j('#loginNav').removeClass('show top');

   		var top = window.pageYOffset;
	});

	$j('li#back').live('touchend', function(event){
		history.back();
	});

	$j('#mobileNav ul li a').live('touchend', function(event){
   		$j(this).addClass('navLoad');

	});

	$j('#login').live('touchend', function(event){
   		$j('#loginNav').toggleClass('show top');
   		$j('#mobileNav').removeClass('show top');
	});

	$j('#content').live('touchend', function(event){
   		//$j('#mobileNav').removeClass('show');
   		//$j('#loginNav').removeClass('show');
	});

	$j('#status').live('touchend', function(event){
   		$j('#mobileNav').removeClass('show top');
   		$j('#buddypress form#whats-new-form #whats-new-content').toggleClass('show');
	});

	$j('#theme-switch').live('touchend', function(event){
		$j.cookie( 'bpthemeswitch', 'normal', {path: '/'} );
	});

	$j('#theme-switch-site').live('touchend', function(event){
		$j.cookie( 'bpthemeswitch', 'mobile', {path: '/'} );
	});
	
	$j( window ).on( 'click', handleClick );
	
	var comp = new RegExp(location.host);

	function handleClick(e) {
	
    	var target = $j(e.target).closest('a');
    	
    	if( comp.test( target.attr('href') ) ){
    
        		e.preventDefault();
				window.location = target.attr('href');
		}
	}

});