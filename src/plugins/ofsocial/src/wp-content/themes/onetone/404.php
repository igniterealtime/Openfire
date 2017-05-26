<?php
/**
* The 404 template file.
*
*/
   get_header("site"); 
   global $allowedposttags
?>
<div id="post-error-404" <?php post_class("clear"); ?>>
<div class="site-main">
  <div class="main-content">
    <div class="content-area">
      <div class="site-content" role="main">
        <header class="archive-header">
          <h1 class="archive-title"><p class="breadcrumb"><a href="<?php echo esc_url(home_url('/')); ?>"><?php _e("Home",'onetone');?></a><span class="arrow"> &raquo; </span><span class="current_crumb">404 </span></p></h1>
        </header>
        <article class="post-entry">
          <div class="entry-main">
            <div class="entry-content">
              <!--post content-->
             <?php echo do_shortcode(wp_kses_post(onetone_options_array('content_404'), $allowedposttags) );?>
              <!--post econtent end-->
            </div>
          </div>
          
        </article>
      </div>
    </div>
  </div>
  <!--main-->
  <div class="sidebar">
    <div class="widget-area">
   <?php get_sidebar("post") ;?>
    </div>
  </div>
  <!--sidebar-->
</div>
</div>
<?php get_footer("site"); ?>