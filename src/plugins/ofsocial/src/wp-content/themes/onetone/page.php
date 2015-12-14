<?php
/**
* The sigle template file.
*
*/
   get_header('site');  

?>
<div id="post-<?php the_ID(); ?>" <?php post_class("clear"); ?>>
<div class="site-main">
  <div class="main-content">
    <div class="content-area">
      <div class="site-content" role="main">
        <header class="archive-header">
          <h1 class="archive-title"><?php onetone_get_breadcrumb();?></h1>
        </header>
        <article class="post-entry">
          <div class="entry-main">
		  <?php if (have_posts()) :?>
          <?php	
		  while ( have_posts() ) : the_post();
		  ?>
            <div class="entry-header">
              <h1 class="entry-title"><?php the_title();?></h1>
              <div class="entry-meta"> 
                  <span class="entry-date-sub"><?php echo get_the_time("M d, Y"); ?></span> 
                  <span class="entry-category"><?php _e("Categories","onetone");?> : <?php the_category(', '); ?></span> 
                  <span class="entry-author"><?php _e("Author","onetone");?>: <?php echo get_the_author_link();?></span> 
                  <span class="entry-comments"><?php  comments_popup_link( 'No comments yet', '1 comment', '% comments', 'comments-link', '');?></span> 
              </div>
            </div>
            <div class="entry-content">
              <!--post content-->
             <?php the_content();?>
              <!--post econtent end-->
            </div>
			<?php endwhile;?>
            <?php endif;?>
          </div>
          
        </article>
        <nav class="post-navigation"> </nav>
        <div class="comments-area">
         <?php
      wp_link_pages( array( 'before' => '<div class="page-links"><span class="page-links-title">' . __( 'Pages:', 'onetone' ) . '</span>', 'after' => '</div>', 'link_before' => '<span>', 'link_after' => '</span>' ) );

	comments_template(); 

?>
          <!--comment-respond end-->
        </div>
        <!--comments-area end-->
      </div>
    </div>
  </div>
  <!--main-->
 <?php get_sidebar();?>
  <!--sidebar-->
</div>
</div>
<?php get_footer('site'); ?>