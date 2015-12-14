<article class="post-entry">
					    <div class="entry-main">
							<div class="entry-header">
								<h1 class="entry-title"><a href="<?php the_permalink();?>"><?php the_title();?></a></h1>                            
								<div class="entry-meta">
                                    <span class="entry-date-sub"><a href="<?php echo get_day_link('', '', ''); ?>"><?php echo get_the_date("M d, Y");?></a></span>
									<span class="entry-category"><?php _e("Categories","onetone");?> : <?php the_category(', '); ?></span>
									<span class="entry-author"><?php _e("Author","onetone");?>: <?php echo get_the_author_link();?></span>
									<span class="entry-comments"><a href="<?php the_permalink();?>#comments"><?php  comments_popup_link( 'No comments yet', '1 comment', '% comments', 'comments-link', '');?></a></span>
								</div>
							</div>
							<div class="entry-content">
							<?php if (  has_post_thumbnail() ) {the_post_thumbnail();}   ?>
							<?php the_excerpt();?>
							</div>
							<div class="entry-footer">
								<div class="entry-meta">
									<span class="entry-more"><a href="<?php the_permalink();?>">>><?php _e("Read More","onetone");?></a></span>
								</div>
							</div>
						</div>
						<div class="entry-aside">
                        	<div class="entry-meta">
							<div class="entry-date">
							<?php 
				$archive_year  = get_the_time('Y'); 
				$archive_month = get_the_time('M'); 
				$archive_day   = get_the_time('d'); 
				?>
							 <?php echo $archive_year;?><br />
                <?php echo $archive_month;?>, <?php echo $archive_day;?>
							</div>
							
                            </div>
						</div>
					</article>