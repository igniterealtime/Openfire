<?php
/**
* The main template file.
*
*/
 ?>
<?php
if ( 'page' == get_option( 'show_on_front' ) && ( '' != get_option( 'page_for_posts' ) ) && $wp_query->get_queried_object_id() == get_option( 'page_for_posts' ) ) {
get_header('site'); 
?>
<div class="site-main">
		<div class="main-content">
			<div class="content-area">
				<div class="site-content" role="main">
					<header class="archive-header">
						<h1 class="archive-title"><?php onetone_get_breadcrumb();?></h1>
					</header>
					<?php if (have_posts()) :?>
                    <?php while ( have_posts() ) : the_post(); 
					
					    get_template_part("content","article");
					?>
                   <?php endwhile;?>
                   <?php endif;?>
					
					<nav class="paging-navigation">
						<div class="loop-pagination">
							<?php if(function_exists("onetone_native_pagenavi")){onetone_native_pagenavi("echo",$wp_query);}?>
						</div>
					</nav>
				</div>
			</div>
		</div>
		<!--main--> 

		<?php get_sidebar();?>
		<!--sidebar--> 
	</div>
<?php
get_footer('site');
}else{
?>

<?php 
get_header();
?>
<div class="container home-wrapper">
<?php
 global $onetone_options, $allowedposttags ;
 $allowedposttags['input']  = array ( 'class' => 1, 'id'=> 1, 'style' => 1, 'type' => 1, 'value' => 1 ,'placeholder'=> 1,'size'=> 1,'tabindex'=> 1,'aria-required'=> 1);
 $allowedposttags['iframe'] = array(
'align' => true,
'width' => true,
'height' => true,
'frameborder' => true,
'name' => true,
'src' => true,
'id' => true,
'class' => true,
'style' => true,
'scrolling' => true,
'marginwidth' => true,
'marginheight' => true,

);
 
 
 $video_array               = array();
 $section_num               = onetone_options_array( 'section_num' ); 
 $section_background_video  = onetone_options_array( 'section_background_video_0' );
 $video_background_section  = onetone_options_array( 'video_background_section' );
 $video_background_section  = $video_background_section == ""?1:$video_background_section;
 $video_controls            = onetone_options_array( 'video_controls' );
 $video_controls            = $video_controls == ""?1:$video_controls;
 $section_1_content         = onetone_options_array( 'section_1_content' );
 

 if(isset($section_num) && is_numeric($section_num ) && $section_num >0):
 for( $i = 0; $i < $section_num ;$i++){
	 
	 if( $section_1_content == 'slider' && $i == 0 ){
		 
		echo onetone_get_default_slider(); 
		 
		 }else{
 
 $section_title       = onetone_options_array( 'section_title_'.$i );
 $section_menu        = onetone_options_array( 'menu_title_'.$i );
 $section_background  = onetone_options_array( 'section_background_'.$i );
 $parallax_scrolling  = onetone_options_array( 'parallax_scrolling_'.$i );
 $section_css_class   = onetone_options_array( 'section_css_class_'.$i );
 $section_content     = onetone_options_array( 'section_content_'.$i );
  if(!isset($section_content) || $section_content=="") 
 $section_content     = onetone_options_array( 'sction_content_'.$i );
 $section_slug        = onetone_options_array( 'menu_slug_'.$i );

 if( $section_slug )
  $section_slug =  sanitize_title($section_slug );
  else
  $section_slug = 'section-'.($i+1);
  
 $background = onetone_get_background($section_background);
 $sanitize_title = $section_slug; 
 
 $css_class = isset($section_css_class)?$section_css_class:"";
 
  $background_video = '';
  $video_wrap = '';
  $video_enable = 0;
  $detect = new Mobile_Detect;
  if($section_background_video != "" && $video_background_section == ($i+1) && !$detect->isMobile() && !$detect->isTablet()){
	$video_enable = 1;  
  }
  
  if( $parallax_scrolling == "yes" ){
	 $css_class  .= ' onetone-parallax';
	 $background .= 'background-attachment:fixed;background-position:50% 0;background-repeat:no-repeat;';
	 }
  
 if($video_enable == 1){

    $background_video  = array("videoId"=>$section_background_video,"mute"=>false,"start"=>3 ,"container" =>"section.onetone-".$sanitize_title,"playerid"=>$sanitize_title);
    $video_section_item = "section.onetone-".$sanitize_title;
    $video_array[]  =  array("options"=>$background_video,  "video_section_item"=>$video_section_item );
	$background = "";
	$video_wrap = "video-section";
	}
 
 ?>
 <section id="<?php echo $section_slug;?>" class="section <?php echo esc_attr($css_class);?> onetone-<?php echo $sanitize_title;?> <?php echo $video_wrap;?>"  style=" <?php echo $background; ?>">
    	<div class="home-container page_container" >
		<?php if($section_title){?>
        	<h1><?php echo esc_attr($section_title);?></h1>
            <?php } ?>
            <?php echo do_shortcode(wp_kses( $section_content, $allowedposttags  ));?>
            
        </div>
		<div class="clear"></div>
     <?php 
	  if( $video_enable == 1 && $video_controls == 1 ){
	  echo '<p class="black-65" id="video-controls">
		  <a class="tubular-play" href="#"><i class="fa fa-play "></i></a>&nbsp; &nbsp;&nbsp;&nbsp;
		  <a class="tubular-pause" href="#"><i class="fa fa-pause "></i></a>&nbsp;&nbsp;&nbsp;&nbsp;
		  <a class="tubular-volume-up" href="#"><i class="fa fa-volume-up "></i></a>&nbsp;&nbsp;&nbsp;&nbsp;
		  <a class="tubular-volume-down" href="#"><i class="fa fa-volume-off "></i></a> 
	  </p>';
	 }
	 ?>
    </section>
 <?php
 }
 }
  if($video_array !="" && $video_array != NULL ){
  wp_localize_script( 'onetone-bigvideo', 'onetone_bigvideo',$video_array);
  
		}

 endif;
 ?>
<div class="clear"></div>  
</div>
<?php get_footer();}?>