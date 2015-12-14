<?php
/**
 * A unique identifier is defined to store the options in the database and reference them from the theme.
 */
function optionsframework_option_name() {

	$themename = get_option( 'stylesheet' );
	$themename = preg_replace("/\W/", "_", strtolower($themename) );
	
	if( is_child_theme() ){
		
		$themename = str_replace("_child","",$themename ) ;
		
		}
		
	return $themename;
}

/**
 * Defines an array of options that will be used to generate the settings page and be saved in the database.
 * When creating the 'id' fields, make sure to use all lowercase and no spaces.
 *
 */


function optionsframework_options() {

	// Background Defaults
	
	$page_background = array(
		'color' => '',
		'image' => ONETONE_THEME_BASE_URL.'/images/leftbg.jpg',
		'repeat' => 'no-repeat',
		'position' => 'top left',
		'attachment'=>'fixed' );
	
	$font_color =array('color' =>  '');
	$section_font_color = array('color' => '');


		
	$section_title       = array("","About Us","Services","Gallery","Contact","Custom Section","");
	$section_menu        = array("Home","About Us","Services","Gallery","Contact","Custom Section","");
	$section_slug        = array('home','about-us','services','gallery','contact','custom-section','clients');
	$default_section_num = count($section_menu);
	$section_num         = onetone_options_array('section_num');
	$section_num         = $section_num == ""?7:$section_num;
	$section_background = array(
	     array(
		'color' => '',
		'image' => ONETONE_THEME_BASE_URL.'/images/home-bg01.jpg',
		'repeat' => 'repeat',
		'position' => 'top left',
		'attachment'=>'scroll' ),
		 array(
		'color' => '',
		'image' => ONETONE_THEME_BASE_URL.'/images/home-bg02.jpg',
		'repeat' => 'repeat',
		'position' => 'top left',
		'attachment'=>'scroll' ),
		 array(
		'color' => '',
		'image' => ONETONE_THEME_BASE_URL.'/images/home-bg03.jpg',
		'repeat' => 'repeat',
		'position' => 'top left',
		'attachment'=>'scroll' ),
		 array(
		'color' => '',
		'image' => ONETONE_THEME_BASE_URL.'/images/home-bg02.jpg',
		'repeat' => 'repeat',
		'position' => 'top left',
		'attachment'=>'scroll' ),
		 array(
		'color' => '',
		'image' => ONETONE_THEME_BASE_URL.'/images/home-bg03.jpg',
		'repeat' => 'repeat',
		'position' => 'top left',
		'attachment'=>'scroll' ),
		 array(
		'color' => '',
		'image' => ONETONE_THEME_BASE_URL.'/images/home-bg02.jpg',
		'repeat' => 'repeat',
		'position' => 'top left',
		'attachment'=>'scroll' ),
		 array(
		'color' => '#dddddd',
		'image' => '',
		'repeat' => 'repeat',
		'position' => 'top left',
		'attachment'=>'scroll' )
			);
	$section_css_class = array("section-banner","section-about","","","","","");
	
	
	$section_content   = array('<div class="banner-box"><br/>
        	<h1>TARAY BOGRILOYAT srians</h1>
			<div class="sub-title">
            <span>CRAS URNA LEO, FRINGILLA NEC ALIQUAM AC, VARIUS IN ENIM. MAECENAS NON FELIS AUGUE, 
QUIS SAGITTIS JUSTO. DONEC GRAVIDA, ARCU IN ALIQUET CONVALLIS</span></div>
			<div class="banner-scroll"><a href="#about-us" class="scroll" data-section="about-us"><img src="'.ONETONE_THEME_BASE_URL.'/images/down.png" alt=""></a></div>
            <div class="banner-sns">
			<ul class="">
            	<li><a href="#"><i class="fa fa-2 fa-facebook">&nbsp;</i></a></li>
                <li><a href="#"><i class="fa fa-2 fa-skype">&nbsp;</i></a></li>
                <li><a href="#"><i class="fa fa-2 fa-twitter">&nbsp;</i></a></li>
                <li><a href="#"><i class="fa fa-2 fa-linkedin">&nbsp;</i></a></li>
                <li><a href="#"><i class="fa fa-2 fa-google-plus">&nbsp;</i></a></li>
                <li><a href="#"><i class="fa fa-2 fa-rss">&nbsp;</i></a></li>
            </ul></div>
            </div>',
			'<div class="two_third">
            	<h3>Biography</h3>
                <p>Morbi rutrum, elit ac fermentum egestas, tortor ante vestibulum est, eget 
					scelerisque nisl velit eget tellus. Fusce porta facilisis luctus. Integer neque 
					dolor, rhoncus nec euismod eget, pharetra et tortor. Nulla id pulvinar nunc. 
					Vestibulum auctor nisl vel lectus ullamcorper sed pellentesque dolor 
					eleifend. Praesent lobortis magna vel diam mattis sagittis.Mauris porta odio 
					eu risus scelerisque id facilisis ipsum dictum vitae volutpat. Lorem ipsum 
					dolor sit amet, consectetur adipiscing elit. Sed pulvinar neque eu purus 
					sollicitudin et sollicitudin dui ultricies. Maecenas cursus auctor tellus sit 
					amet blandit. Maecenas a erat ac nibh molestie interdum. Class aptent 
					taciti sociosqu ad litora torquent per conubia nostra, per inceptos 
					himenaeos. Sed lorem enim, ultricies sed sodales id, convallis molestie 
					ipsum. Morbi eget dolor ligula. Vivamus accumsan rutrum nisi nec 
					elementum. Pellentesque at nunc risus. Phasellus ullamcorper 
					bibendum varius. Quisque quis ligula sit amet felis ornare porta. Aenean 
					viverra lacus et mi elementum mollis. Praesent eu justo elit.</p>
            </div>
            <div class="one_third last">
            	<h3>Personal Info</h3>
                <ul>
                	<li class="info-phone">+1123 2456 689</li>
					<li class="info-address">3301 Lorem Ipsum, Dolor Sit St</li>
					<li class="info-email"><a href="#">support@mageewp.com. </a></li>
					<li class="info-website"><a href="#">Mageewp.com</a></li>
                </ul>                	
            </div>',
			'<div class="one_third">
			<!-- Font Awesome Icon-->
            	<i class="fa fa-3 fa-desktop"></i>
				
                <h3>Service 1</h3>
                <p>Donec in vehicula augue. Sed et 
					nisi sem, at semper dolor. 
					Pellentesque habitant morbi 
					tristique senectus et netu..</p>
			</div>
            <div class="one_third">
			<!-- Font Awesome Icon-->
            	<i class="fa fa-3 fa-comments-o"></i>
                <h3>Service 2</h3>
                <p>Donec in vehicula augue. Sed et 
					nisi sem, at semper dolor. 
					Pellentesque habitant morbi 
					tristique senectus et netu..</p>
			</div>
        	<div class="one_third last">
			<!-- Font Awesome Icon-->
            	<i class="fa fa-3 fa-search"></i>
                <h3>Service 3</h3>
                <p>Donec in vehicula augue. Sed et 
					nisi sem, at semper dolor. 
					Pellentesque habitant morbi 
					tristique senectus et netu..</p>
			</div>',
			'<div class="portfolio-list">
        		<ul>
            		<li><a href="#"><img class="port-img" src="'.ONETONE_THEME_BASE_URL.'/images/g1.jpg"></a></li>
                	<li><a href="#"><img class="port-img" src="'.ONETONE_THEME_BASE_URL.'/images/g2.jpg"></a></li>
               		<li><a href="#"><img class="port-img" src="'.ONETONE_THEME_BASE_URL.'/images/g3.jpg"></a></li>
               		<li><a href="#"><img class="port-img" src="'.ONETONE_THEME_BASE_URL.'/images/g4.jpg"></a></li>
               		<li><a href="#"><img class="port-img" src="'.ONETONE_THEME_BASE_URL.'/images/g5.jpg"></a></li>
               		<li><a href="#"><img class="port-img" src="'.ONETONE_THEME_BASE_URL.'/images/g6.jpg"></a></li>
               		<li><a href="#"><img class="port-img" src="'.ONETONE_THEME_BASE_URL.'/images/g7.jpg"></a></li>
                	<li><a href="#"><img class="port-img" src="'.ONETONE_THEME_BASE_URL.'/images/g8.jpg"></a></li>
            	</ul>
        	</div>',
			'<p class="contact-text">INTEGER ALIQUET ARCU SIT AMET SEM PORTA FACILISIS. CURABITUR SAPIEN SAPIEN, 
				BLANDIT IN MOLESTIE ET, SAGITTIS ID LOREM. NULLA MALESUADA MAURIS ID TURPIS</p>
			<div class="contact-area">
			  <form class="contact-form" method="post" action="">
			   <input type="text" name="name" id="name" value="" placeholder="Name" size="22" tabindex="1" aria-required="true">
			   <input type="text" name="email" id="email" value="" placeholder="Email" size="22" tabindex="2" aria-required="true"> 
			   <textarea name="message" id="message" cols="39" rows="7" tabindex="4" placeholder="Message"></textarea>
			   <p class="noticefailed"></p>
			   <input type="hidden" name="sendto" id="sendto" value="YOUR EMAIL HERE(Default Admin Email)">
			   <input type="button" name="submit" id="submit" value="Post">
			  </form>
			 </div>
			',
			'<p>Donec in vehicula augue. Sed et nisi sem, at semper dolor. Pellentesque habitant morbi tristique 
			senectus et netus et malesuada fames ac turpis egestas. Mauris ut urna nibh, a semper 
			neque. Mauris ultrices tempus nisi, et porttitor nulla varius a. Ut turpis magna, 
			feugiat quis ultrices tristique, rhoncus eu leo. In eu quam lacus. Praesent
			Vehicula augue. Sed et nisi sem, at semper dolor. Pellentesque habitant morbi tristique 
			senectus et netus et malesuada fames ac turpis egestas. Mauris ut urna nibh, a semper 
			anews sed ovref neque. Mauris ultrices tempus nisi, et porttitor nulla varius a. Ut turpis magna, 
			feugiat quis ultrices tristique, rhoncus eu leo. In eu quam lacus. dear Praesent Donec in vehicula augue. 
			Sed et nisi sem, at semper dolor. Pellentesque habitant morbi tristique 
			senectus et netus et malesuada fames ac turpis egestas. Mauris ut urna nibh, a semper 
			neque. Mauris ultrices tempus nisi, et porttitor nulla varius a. Ut turpis magna, 
			feugiat quis ultrices tristique, rhoncus eu leo. In eu quam lacus. Praesent
			Vehicula augue. Sed et nisi sem, at semper dolor. Pellentesque habitant morbi tristique 
			senectus et netus et malesuada fames ac turpis egestas. Mauris ut urna nibh, a semper 
			anews sed ovref neque. Mauris ultrices tempus nisi, et porttitor nulla varius a. Ut turpis magna, 
			feugiat quis ultrices tristique, rhoncus eu leo. In eu quam lacus. dear Praesent</p>',
			'<div class="one_fifth"><a href="#"><img src="'.esc_url('http://www.mageewp.com/onetone/wp-content/uploads/sites/17/2015/04/c1.png').'" alt="HTML5" title="HTML5"></a></div>
<div class="one_fifth"><a href="#"><img src="'.esc_url('http://www.mageewp.com/onetone/wp-content/uploads/sites/17/2015/04/c2.png').'" alt="CSS3" title="CSS3"></a></div>
<div class="one_fifth"><a href="#"><img src="'.esc_url('http://www.mageewp.com/onetone/wp-content/uploads/sites/17/2015/04/c3.png').'" alt="Bootstra
p" title="Bootstrap"></a></div>
<div class="one_fifth"><a href="#"><img src="'.esc_url('http://www.mageewp.com/onetone/wp-content/uploads/sites/17/2015/04/c4.png').'" alt="jQuery" title="jQuery"></a></div>
<div class="one_fifth last_column"><a href="#"><img src="'.esc_url('http://www.mageewp.com/onetone/wp-content/uploads/sites/17/2015/04/c5.png').'" alt="WordPress" title="WordPress"></a></div>'
	);
	//$section_background_video = array("ab0TSkLe-E0","","","","","");

	$options = array();
   // HEADER
	$options[] = array(
		'name' => __('General Options', 'onetone'),
		'type' => 'heading');

	$options[] = array(
		'name' => __('Upload Logo', 'onetone'),
		'id' => 'logo',
		'std' => '',
		'type' => 'upload');
		
	$options[] = array(
		'name' => __('Favicon', 'onetone'),
		'desc' => sprintf(__('An icon associated with a URL that is variously displayed, as in a browser\'s address bar or next to the site name in a bookmark list. Learn more about 
					 <a href="%s" target="_blank">Favicon</a>', 'onetone'),esc_url("http://en.wikipedia.org/wiki/Favicon")),
		'id' => 'favicon',
		'type' => 'upload');
		
		$options[] = array(
		'name' =>  __('Post & Page Background', 'onetone'),
		'id' => 'page_background',
		'std' => $page_background,
		'type' => 'background' );
		
			
	$options[] = array(
		'name' =>  __('Header Menu Font Color', 'onetone'),
		'id' => 'font_color',
		'std' => '#ddd',
		'type' => 'color' );
	
	$options[] = array(
		'name' =>  __('Links Color', 'onetone'),
		'id' => 'links_color',
		'std' => '#963',
		'type' => 'color' );
	
	$options[] = array(
		'name' =>  __('Back to Top Button', 'onetone'),
		'id' => 'back_to_top_btn',
		'std' => 'show',
		'class' => 'mini',
		'type' => 'select',
		'options'=>array("show"=>"show","hide"=>"hide")
		);
		
		
	$options[] = array(
		'name' => __('Custom CSS', 'onetone'),
		'desc' => __('The following css code will add to the header before the closing &lt;/head&gt; tag.', 'onetone'),
		'id' => 'custom_css',
		'std' => 'body{margin:0px;}',
		'type' => 'textarea');
	
		////HOME PAGE
		$options[] = array(
		'name' => __('Home Page', 'onetone'),
		'type' => 'heading');
		
		//HOME PAGE SECTION
		
	   $options[] = array(
		'name' => __('Content Sections Num', 'onetone'),
		'desc' => __('The number of home page sections.', 'onetone'),
		'id' => 'section_num',
		'std' => '7',
		'type' => 'text');
		
		$options[] = array('name' => __('Section Background Video', 'onetone'),'std' => 'ab0TSkLe-E0','desc' => __('YouTube Video ID', 'onetone'),'id' => 'section_background_video_0',
		'type' => 'text');
		
		$options[] = array(
		'name' => __('Video Controls', 'onetone'),
		'desc' => __('Display video control buttons.', 'onetone'),
		'id' => 'video_controls',
		'std' => '1',
		'class' => 'mini',
		'options' => array('1'=>'yes','0'=>'no'),
		'type' => 'select');
		
		$video_background_section = array("0"=>"No video background");
		if( is_numeric( $section_num ) ){
		for($i=1; $i <= $section_num; $i++){
			$video_background_section[$i] = "Secion ".$i;
			}
		}
		$options[] = array('name' => __('Video Background Section', 'onetone'),'std' => '1','id' => 'video_background_section',
		'type' => 'select','options'=>$video_background_section);
		
		
		$options[] = array('name' => __('Section 1 Content', 'onetone'),'std' => 'content','id' => 'section_1_content',
		'type' => 'select','options'=>array("content"=>"Content","slider"=>"Slider"));

		if(isset($section_num) && is_numeric($section_num) && $section_num>0){
		$section_num = $section_num;
		}
		else{
		$section_num = $default_section_num;
		}
	
		for($i=0; $i < $section_num; $i++){
		
		if(!isset($section_title[$i])){$section_title[$i] = "";}
		if(!isset($section_menu[$i])){$section_menu[$i] = "";}
		if(!isset($section_background[$i])){$section_background[$i] = array('color' => '',
		'image' => '',
		'repeat' => '',
		'position' => '',
		'attachment'=>'');}
		if(!isset($section_css_class[$i])){$section_css_class[$i] = "";}
		if(!isset($section_content[$i])){$section_content[$i] = "";}
		if(!isset($section_slug[$i])){ $section_slug[$i] = "";}
		
		
		$options[] = array('name' => sprintf(__('Section %s', 'onetone'),($i+1)),'id' => 'slide_group_start_'.$i.'','type' => 'start_group','class'=>'home-section group_close');
		$options[] = array('name' => __('Section Title', 'onetone'),'id' => 'section_title_'.$i.'','type' => 'text','std'=>$section_title[$i],'class'=>'section-item');
		$options[] = array('name' => __('Menu Title', 'onetone'),'id' => 'menu_title_'.$i.'','type' => 'text','std'=>$section_menu[$i],'desc'=>'This title will display in the header menu. It is required','class'=>'section-item');
		$options[] = array('name' => __('Menu Slug', 'onetone'),'id' => 'menu_slug_'.$i.'','type' => 'text','std'=>$section_slug[$i],'desc'=>'The  "slug" is the URL-friendly version of the name. It is usually all lowercase and contains only letters, numbers, and hyphens.','class'=>'section-item');
		
		
		
		$options[] = array('name' =>  __('Section Background', 'onetone'),'id' => 'section_background_'.$i.'','std' => $section_background[$i],'type' => 'background' ,'class'=>'section-item');
		
		$options[] = array('name' => __('Parallax Scrolling Background Image', 'onetone'),'std' => 'no','id' => 'parallax_scrolling_'.$i.'',
		'type' => 'select','class'=>'mini section-item','options'=>array("no"=>"no","yes"=>"yes"));
		
	    if($i == 0){
		
		}
		
		$options[] = array('name' => __('Section Css Class', 'onetone'),'id' => 'section_css_class_'.$i.'','type' => 'text','std'=>$section_css_class[$i],'class'=>'section-item');
	    $options[] = array('name' => __('Section Content', 'onetone'),'id' => 'section_content_'.$i,'std' => $section_content[$i],'type' => 'editor');
		$options[] = array(
		'name' => '',
		'desc' => '<div style="overflow:hidden; background-color:#eee; padding:20px;"><a data-section="'.$i.'" class="delete-section button-primary" style="float:right;" title="Delete">Delete this section</a></div>',
		'id' => 'delete_section_'.$i,
		'std' => '',
		'type' => 'info',
		'class'=>'section-item');
	
		$options[] = array('name' => '','id' => 'slide_group_end_'.$i.'','type' => 'end_group');
		
		}

		//END HOME PAGE SECTION
		
			// Slider
		$options[] = array(
		'name' => __('Slider', 'onetone'),
		'type' => 'heading');
		
	
		
		//HOME PAGE SLIDER
		$options[] = array('name' => __('Slideshow', 'onetone'),'id' => 'group_title','type' => 'title');
		
		$options[] = array('name' => __('Slide 1', 'onetone'),'id' => 'slide_group_start_1','type' => 'start_group','class'=>'group_close');
		$options[] = array('name' => __('Image', 'onetone'),'id' => 'onetone_slide_image_1','type' => 'upload','std'=>ONETONE_THEME_BASE_URL.'/images/banner-1.jpg');
		//$options[] = array('name' => __('Title', 'onetone'),'id' => 'onetone_slide_title_1','type' => 'text','std'=>'Title 1');

		$options[] = array('name' => __('Text', 'onetone'),'id' => 'onetone_slide_text_1','type' => 'editor','std'=>'<h1>The jQuery slider that just slides.</h1><p>No fancy effects or unnecessary markup.</p><a class="btn" href="#download">Download</a>');
		//$options[] = array('name' => __('Link', 'onetone'),'id' => 'onetone_slide_link_1','type' => 'text');
		$options[] = array('name' => '','id' => 'slide_group_end_1','type' => 'end_group');
		
		$options[] = array('name' => __('Slide 2', 'onetone'),'id' => 'slide_group_start_2','type' => 'start_group','class'=>'group_close');
		$options[] = array('name' => __('Image', 'onetone'),'id' => 'onetone_slide_image_2','type' => 'upload','std'=>ONETONE_THEME_BASE_URL.'/images/banner-2.jpg');
		//$options[] = array('name' => __('Title', 'onetone'),'id' => 'onetone_slide_title_2','type' => 'text','std'=>'Title 2');
		$options[] = array('name' => __('Text', 'onetone'),'id' => 'onetone_slide_text_2','type' => 'editor','std'=>'<h1>Fluid, flexible, fantastically minimal.</h1><p>Use any HTML in your slides, extend with CSS. You have full control.</p><a class="btn" href="#download">Download</a>');
		//$options[] = array('name' => __('Link', 'onetone'),'id' => 'onetone_slide_link_2','type' => 'text');
		$options[] = array('name' => '','id' => 'slide_group_end_2','type' => 'end_group');
		
		$options[] = array('name' => __('Slide 3', 'onetone'),'id' => 'slide_group_start_3','type' => 'start_group','class'=>'group_close');
		$options[] = array('name' => __('Image', 'onetone'),'id' => 'onetone_slide_image_3','type' => 'upload','std'=>ONETONE_THEME_BASE_URL.'/images/banner-3.jpg');
		//$options[] = array('name' => __('Title', 'onetone'),'id' => 'onetone_slide_title_3','type' => 'text');
		$options[] = array('name' => __('Text', 'onetone'),'id' => 'onetone_slide_text_3','type' => 'editor','std'=>'<h1>Open-source.</h1><p> Vestibulum auctor nisl vel lectus ullamcorper sed pellentesque dolor eleifend.</p><a class="btn" href="#">Contribute</a>');
		//$options[] = array('name' => __('Link', 'onetone'),'id' => 'onetone_slide_link_3','type' => 'text');
		$options[] = array('name' => '','id' => 'slide_group_end_3','type' => 'end_group');
		
		$options[] = array('name' => __('Slide 4', 'onetone'),'id' => 'slide_group_start_4','type' => 'start_group','class'=>'group_close');
		$options[] = array('name' => __('Image', 'onetone'),'id' => 'onetone_slide_image_4','type' => 'upload','std'=>ONETONE_THEME_BASE_URL.'/images/banner-4.jpg');
		//$options[] = array('name' => __('Title', 'onetone'),'id' => 'onetone_slide_title_4','type' => 'text');
		$options[] = array('name' => __('Text', 'onetone'),'id' => 'onetone_slide_text_4','type' => 'editor','std'=>'<h1>Uh, that\'s about it.</h1><p>I just wanted to show you another slide.</p><a class="btn" href="#download">Download</a>');
		//$options[] = array('name' => __('Link', 'onetone'),'id' => 'onetone_slide_link_4','type' => 'text');
		$options[] = array('name' => '','id' => 'slide_group_end_4','type' => 'end_group');
		
		$options[] = array('name' => __('Slide 5', 'onetone'),'id' => 'slide_group_start_5','type' => 'start_group','class'=>'group_close');
		$options[] = array('name' => __('Image', 'onetone'),'id' => 'onetone_slide_image_5','type' => 'upload');
	  //$options[] = array('name' => __('Title', 'onetone'),'id' => 'onetone_slide_title_5','type' => 'text');
		$options[] = array('name' => __('Text', 'onetone'),'id' => 'onetone_slide_text_5','type' => 'editor');
	  //$options[] = array('name' => __('Link', 'onetone'),'id' => 'onetone_slide_link_5','type' => 'text');
		$options[] = array('name' => '','id' => 'slide_group_end_5','type' => 'end_group');
		

	/*	$options[] = array(
		'name' => __('Slide Height', 'onetone'),
		'id' => 'slide_height',
		'std' => '30%',
		'desc'=>__('Both formats: Percentage or Pixel(e.g. "30%" or "400px").','onetone'),
		'type' => 'text');	*/
		
		$options[] = array(
		'name' => __('Slide Speed', 'onetone'),
		'id' => 'slide_time',
		'std' => '5000',
		'desc'=>__('Milliseconds between the end of the sliding effect and the start of the nex one.','onetone'),
		'type' => 'text');		
		
		//END HOME PAGE SLIDER
		
			// FOOTER
	    $options[] = array(
		'name' => __('Footer', 'onetone'),
		'type' => 'heading');
	
        $options[] = array(
		'name' => __('Enable Footer Widgets Area', 'onetone'),
		'desc' => 'Enable home page footer widgets area',
		'id' => 'enable_footer_widget_area',
		'std' => '0',
		'type' => 'checkbox');
		
		 // 404
		
		$options[] = array(	'name' => __('404 page', 'onetone'),'type' => 'heading');
		$options[] = array(
		'name' => __('404 page content', 'onetone'),
		'desc' => '',
		'id' => 'content_404',
		'std' => '<h2>WHOOPS!</h2>
                        <p>THERE IS NOTHING HERE.<br>PERHAPS YOU WERE GIVEN THE WRONG URL?</p>',
		'type' => 'editor');
		
		
		
	return $options;
}