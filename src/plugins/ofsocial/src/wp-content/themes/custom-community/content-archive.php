<div class="content-padder">

    <?php if( function_exists( 'tk_loop_designer_the_loop' ) && 'blog-style' !== get_theme_mod( 'cc_list_post_style' ) ) {

        tk_loop_designer_the_loop( get_theme_mod( 'cc_list_post_style' ), 'index', 'show' );

    } else { ?>

        <?php if ( have_posts() ) : ?>

			<?php if( get_theme_mod('display_page_title[archive]', false ) != false ) : ?>

            <header class="page-header">
                <h1 class="page-title">
                    <?php
                    if ( is_category() ) :
                        single_cat_title();

                    elseif ( is_tag() ) :
                        single_tag_title();

                    elseif ( is_author() ) :
                        /* Queue the first post, that way we know
                         * what author we're dealing with (if that is the case).
                        */
                        the_post();
                        printf( __( 'Author: %s', 'cc2' ), '<span class="vcard">' . get_the_author() . '</span>' );
                        /* Since we called the_post() above, we need to
                         * rewind the loop back to the beginning that way
                         * we can run the loop properly, in full.
                         */
                        rewind_posts();

                    elseif ( is_day() ) :
                        printf( __( 'Day: %s', 'cc2' ), '<span>' . get_the_date() . '</span>' );

                    elseif ( is_month() ) :
                        printf( __( 'Month: %s', 'cc2' ), '<span>' . get_the_date( 'F Y' ) . '</span>' );

                    elseif ( is_year() ) :
                        printf( __( 'Year: %s', 'cc2' ), '<span>' . get_the_date( 'Y' ) . '</span>' );

                    elseif ( is_tax( 'post_format', 'post-format-aside' ) ) :
                        _e( 'Asides', 'cc2' );

                    elseif ( is_tax( 'post_format', 'post-format-image' ) ) :
                        _e( 'Images', 'cc2');

                    elseif ( is_tax( 'post_format', 'post-format-video' ) ) :
                        _e( 'Videos', 'cc2' );

                    elseif ( is_tax( 'post_format', 'post-format-quote' ) ) :
                        _e( 'Quotes', 'cc2' );

                    elseif ( is_tax( 'post_format', 'post-format-link' ) ) :
                        _e( 'Links', 'cc2' );

                    else :
                        _e( 'Archives', 'cc2' );

                    endif;
                    ?>
                </h1>
                <?php
                // Show an optional term description.
                $term_description = term_description();
                if ( ! empty( $term_description ) ) :
                    printf( '<div class="taxonomy-description">%s</div>', $term_description );
                endif;
                ?>
            </header><!-- .page-header -->
            
            <?php endif; // endif display_page_title[archive] != false 
            ?>
            
            <div id="featured_posts_index">
                <div id="list_posts_index" class="loop-designer list-posts-all">
                    <div class="index">

                        <?php /* Start the Loop */ ?>
                        <?php while ( have_posts() ) : the_post(); ?>

                            <?php
                            /* Include the Post-Format-specific template for the content.
                             * If you want to overload this in a child theme then include a file
                             * called content-___.php (where ___ is the Post Format name) and that will be used instead.
                             */
                            get_template_part( 'content', get_post_format() );
                            ?>

                        <?php endwhile; ?>

                    </div>
                    <?php 
					do_action('cc2_have_posts_after_loop_archive' );
					?>
                </div>
            </div>

        <?php else : ?>

            <?php get_template_part( 'no-results', 'index' ); ?>

        <?php endif; ?>

    <?php } ?>
</div><!-- .content-padder -->
