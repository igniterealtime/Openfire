<?php

/**
 * Slider init
 * 
 * @hook init
 * 
 * @author Konrad Sroka
 * @package cc2
 * @since 2.0
 *
 */
 
// Future enhancement: Slideshow class with seperate templates 
//require_once( get_stylesheet_directory() . '/includes/cc-slider.class.php' );
/*
if( class_exists('cc2_Slider') ) :
	add_action( 'wp_head', array( 'cc2_Slider', 'init' ) );
else :
	add_action( 'wp_head', 'cc_slider_init' );
endif;
*/

add_action('wp_enqueue_scripts', 'cc2_slider_load_assets' );

function cc2_slider_load_assets() {
	wp_enqueue_script('bootstrap');
}


add_action( 'wp_head', 'cc_slider_init' );

function cc_slider_init() {
	
// check where to hook the slider 
$slideshow_position = get_theme_mod( 'cc_slider_position' );

if( 'always' === get_theme_mod( 'cc_slider_display' ) || ( is_home() && 'bloghome' === get_theme_mod( 'cc_slider_display' ) ) || ( is_front_page() && 'home' === get_theme_mod( 'cc_slider_display' ) ) ) :

	switch ($slideshow_position) {
	
		case 'cc_before_header':
			add_action( 'cc_before_header', 'cc_slider' );
			break;
			
		case 'cc_after_header':
			add_action( 'cc_after_header', 'cc_slider' );
			break;
		
		case 'cc_first_inside_main_content':
			add_action( 'cc_first_inside_main_content', 'cc_slider' );
			break;
		
		case 'cc_first_inside_main_content_inner':
			add_action( 'cc_first_inside_main_content_inner', 'cc_slider' );
			break;
		
		default:
			add_action( 'cc_before_header', 'cc_slider' );		
			break;
	} 

endif;

}

/**
 * The Slider
 * 
 * @hook function cc_slider_init()
 * 
 * @author Konrad Sroka
 * @package cc2
 * @since 2.0
 *
 */

function cc_slider() {

?>
<div id="cc-slider-container" class="cc-slider">
	<div class="cc-slider-wrap">
		
		<?php
		$slides = array();
		
		// just copy a line and modify to add more slides!

        $cc_slider_options = get_option('cc_slider_options');
        $slideshow_template = get_theme_mod('cc_slideshow_template');

		//new __debug( array( 'options' => $cc_slider_options, 'slideshow_template' => $slideshow_template) );

        if(!empty($cc_slider_options) && $slideshow_template != 'none' && count($cc_slider_options[$slideshow_template]) > 0 ){
            if( isset($cc_slider_options[$slideshow_template]['meta-data']['slideshow_type']) && $cc_slider_options[$slideshow_template]['meta-data']['slideshow_type'] == 'post-slider') {
                $args = array (
                    'posts_per_page' => -1
                );

                if(isset($cc_slider_options[$slideshow_template]['query']['post_type']))
                    $args['post_type'] = $cc_slider_options[$slideshow_template]['query']['post_type'];

                if(isset($cc_slider_options[$slideshow_template]['query']['post_type']) && $cc_slider_options[$slideshow_template]['query']['post_type'] == 'post' && isset($cc_slider_options[$slideshow_template]['query']['cat']))
                    $args['cat'] = $cc_slider_options[$slideshow_template]['query']['cat'];


                // The Query
                query_posts($args);

                // The Loop
                while ( have_posts() ) : the_post();
                    $img_src = '';
                    $img_src = wp_get_attachment_url( get_post_thumbnail_id( get_the_id() ));

                    if(!empty($img_src)) :
                        array_push( $slides, array( 'src' => $img_src,	'title' => get_the_title(),'excerpt' => get_the_excerpt()	) );
					endif;

                endwhile;

                // Reset Query
                wp_reset_query();
            } elseif(isset($cc_slider_options[$slideshow_template]['slides'])) {
                foreach ($cc_slider_options[$slideshow_template]['slides'] as $slide) {
                    $post = get_post($slide['id']);

                    $alt = get_post_meta($slide['id'], '_wp_attachment_image_alt', true);
                    array_push( $slides, array( 'src' => $slide['url'],	'title' => $post->post_title,'excerpt' => $post->post_excerpt ) );
                }

            }

        } else {
            array_push( $slides, array( 
				'src' => get_template_directory_uri() . '/includes/slider/images/slider-demo-3.jpg',
				'title' => 'What you waitin\' for?',
				'excerpt' => 'Create your slideshow now!'
			) );
			
            array_push( $slides, array( 'src' => get_template_directory_uri() . '/includes/slider/images/slider-demo-1.jpg',	'title' => 'A Super Lightweight Slider',		'excerpt' => '100% WordPress and Bootstrap' 		) );
            array_push( $slides, array( 'src' => get_template_directory_uri() . '/includes/slider/images/slider-demo-2.jpg',	'title' => 'Visualize Your Ideas.',				'excerpt' => 'And customize everything.' 			) );
        }

		//new __debug( $slides, 'slides' );

        cc_add_slide( 'index', $slides );
		?>
		
	</div>
</div>
<?php
}

function cc_add_slide($name, $slides){
		
	// echo '<pre>';
	// print_r($slides);
	// echo '</pre>';
	
	?>
			
		<script>
		
		//jQuery(document).ready(function(jQuery) {  
		// shorthand
		jQuery(function() {


			// the sliding intervall 
			jQuery( '#carousel-<?php echo $name ?>-generic' ).carousel({
			  interval: <?php echo get_theme_mod( 'cc_sliding_time' ) ?>
			})

			// first the starting slide 
						
				// the title effect 
				<?php if( 'hide' !== get_theme_mod( 'slider_effect_title' ) && 'no-effect' !== get_theme_mod( 'slider_effect_title' ) ) : ?>
					jQuery( '#carousel-<?php echo $name ?>-generic .item.active h1' ).addClass( 'animated <?php echo get_theme_mod('slider_effect_title'); ?>' ).css( 'display', 'block' );
				<?php endif; ?>
				
				// the excerpt effect 
				<?php if( 'hide' !== get_theme_mod( 'slider_effect_excerpt' ) && 'no-effect' !== get_theme_mod( 'slider_effect_excerpt' ) ) : ?>
					jQuery( '#carousel-<?php echo $name ?>-generic .item.active p' ).addClass( 'animated' );
					setTimeout(function(){
						jQuery( '#carousel-<?php echo $name ?>-generic .item.active p' ).addClass( '<?php echo get_theme_mod('slider_effect_excerpt'); ?>' ).css( 'display', 'block' );
					},300);
				<?php endif; ?>
				
			// effects on slide.bs  
			jQuery('#carousel-<?php echo $name ?>-generic').on('slide.bs.carousel', function () {
			})
			
			// effects on slid.bs
			jQuery('#carousel-<?php echo $name ?>-generic').on('slid.bs.carousel', function () {
				
				<?php if( 'hide' !== get_theme_mod( 'slider_effect_title' ) && 'no-effect' !== get_theme_mod( 'slider_effect_title' ) ) : ?>
					jQuery( '#carousel-<?php echo $name ?>-generic .item h1' ).removeClass( 'animated <?php echo get_theme_mod('slider_effect_title'); ?>' ).css( 'display', 'none' );
				<?php endif; ?>
				
				<?php if( 'hide' !== get_theme_mod( 'slider_effect_excerpt' ) && 'no-effect' !== get_theme_mod( 'slider_effect_excerpt' ) ) : ?>
					jQuery( '#carousel-<?php echo $name ?>-generic .item p' ).removeClass( 'animated <?php echo get_theme_mod('slider_effect_excerpt'); ?>' ).css( 'display', 'none' );
				<?php endif; ?>
				
				<?php if( 'hide' !== get_theme_mod( 'slider_effect_title' ) && 'no-effect' !== get_theme_mod( 'slider_effect_title' ) ) : ?>
					jQuery( '#carousel-<?php echo $name ?>-generic .item.active h1' ).addClass( 'animated <?php echo get_theme_mod('slider_effect_title'); ?>' ).css( 'display', 'block' );
				<?php endif; ?>
				
				<?php if( 'hide' !== get_theme_mod( 'slider_effect_excerpt' ) && 'no-effect' !== get_theme_mod( 'slider_effect_excerpt' ) ) : ?>
					setTimeout(function(){
						jQuery( '#carousel-<?php echo $name ?>-generic .item.active p' ).addClass( 'animated <?php echo get_theme_mod('slider_effect_excerpt'); ?>' ).css( 'display', 'block' );
					},300);
				<?php endif; ?>
				
			})
		});
		
	</script>
	
	<?php // add one extra div, if slider position is first inside main content
	if ( 'cc_first_inside_main_content' === get_theme_mod( 'cc_slider_position' ) ) { ?>
		<div class="col-md-12">
	<?php }

	if( sizeof( $slides ) == 1 ) { ?>
		<div class="carousel-fallback">			
			<img src="<?php echo $slides[0]['src']; ?>" alt="Image<?php echo ( !empty($slides[0]['title']) ? ': ' . $slides[0]['title'] : '') ; ?>" title="<?php echo ( !empty($slides[0]['excerpt']) ? $slides[0]['excerpt'] : $slides[0]['title'] ); ?>" />
		</div>
		
	<?php } else {

		if( 'slides-only' === get_theme_mod('cc2_slideshow_style') ) { ?>

			<div id="carousel-<?php echo $name ?>-generic" class="carousel slide carousel-fade" data-ride="carousel">
				  <!-- Indicators -->
				<ol class="carousel-indicators">
					<?php
					foreach ($slides as $key => $slide) {
						echo '<li data-target="#carousel-' . $name . '-generic" data-slide-to="' . $key . '"></li>';
					}
					?>
				</ol>

				<!-- Wrapper for slides -->
				<div class="carousel-inner">
					<?php
					foreach ($slides as $key => $slide) { ?>
						<div class="item <?php if($key == 1) echo 'active'; ?>">
							<img src="<?php echo $slide['src'] ?>" alt="<?php echo $slide['title'] ?>">

							<div class="carousel-caption">
								<div class="cc-slider-title"><h1 id="title-<?php echo $key; ?>" class="cc-slider-title"><span class="textwrap"><?php echo $slide['title'] ?></span></h1></div>
								<div class="cc-slider-excerpt"><p id="excerpt-<?php echo $key; ?>" class="cc-slider-excerpt"><span class="textwrap"><?php echo $slide['excerpt'] ?></span></p></div>
							</div>
						</div>
					<?php } ?>

				</div>

				<!-- Controls -->
				<a class="left carousel-control" href="#carousel-<?php echo $name ?>-generic" data-slide="prev">
					<span class="glyphicon glyphicon-chevron-left"></span>
				</a>
				<a class="right carousel-control" href="#carousel-<?php echo $name ?>-generic" data-slide="next">
					<span class="glyphicon glyphicon-chevron-right"></span>
				</a>
			</div>

		<?php } elseif( 'bubble-preview' === get_theme_mod('cc2_slideshow_style') ) { ?>

				<div id="carousel-<?php echo $name ?>-generic" class="carousel slide carousel-fade cc-slider-bubbles-wrap" data-ride="carousel">

					<!-- An extra wrapper for them slides and controls -->
					<div class="cc-slider-secret-wrap">

						<!-- Wrapper for slides -->
						<div class="carousel-inner">
							<?php
							foreach ($slides as $key => $slide) { ?>
								<div class="item <?php if($key == 1) echo 'active'; ?>">
									<img src="<?php echo $slide['src'] ?>" alt="<?php echo $slide['title'] ?>">

									<div class="carousel-caption">
										<div class="cc-slider-title"><h1 id="title-<?php echo $key; ?>" class="cc-slider-title"><span class="textwrap"><?php echo $slide['title'] ?></span></h1></div>
										<div class="cc-slider-excerpt"><p id="excerpt-<?php echo $key; ?>" class="cc-slider-excerpt"><span class="textwrap"><?php echo $slide['excerpt'] ?></span></p></div>
									</div>
								</div>
							<?php } ?>

						</div>

						<!-- Controls -->
						<a class="left carousel-control" href="#carousel-<?php echo $name ?>-generic" data-slide="prev">
							<span class="glyphicon glyphicon-chevron-left"></span>
						</a>
						<a class="right carousel-control" href="#carousel-<?php echo $name ?>-generic" data-slide="next">
							<span class="glyphicon glyphicon-chevron-right"></span>
						</a>

					</div>

					<!-- Bubbles -->
					<ol class="carousel-indicators cc-slider-bubbles">
						<?php
						foreach ($slides as $key => $slide) {
							echo '<li class="cc-slider-bubble-wrap" data-target="#carousel-' . $name . '-generic" data-slide-to="' . $key . '">'; ?>

							<img class="cc-slider-bubble" src="<?php echo $slide['src'] ?>" width="280" alt="<?php echo $slide['title'] ?>">
							<div class="cc-slider-bubble-active"></div>
							<div class="cc-slider-bubble-title-wrap">
								<span class="cc-slider-bubble-title animated"><?php echo $slide['title'] ?></span>
							</div>
							<?php
							echo '</li>';

						}
						?>
					</ol>
				</div>


		<?php } elseif( 'side-preview' === get_theme_mod('cc2_slideshow_style') ){ ?>
			<div id="carousel-<?php echo $name ?>-generic" class="carousel slide carousel-fade cc-slider-side-preview-wrap" data-ride="carousel">

				<!-- Wrapper for slides -->
				<div class="carousel-inner cc-carousel-inner-side-preview">
					<?php
					foreach ($slides as $key => $slide) { ?>
						<div class="item <?php if($key == 1) echo 'active'; ?>">
							<img src="<?php echo $slide['src'] ?>" alt="<?php echo $slide['title'] ?>">

							<div class="carousel-caption">
								<div class="cc-slider-title"><h1 id="title-<?php echo $key; ?>" class="cc-slider-title"><span class="textwrap"><?php echo $slide['title'] ?></span></h1></div>
								<div class="cc-slider-excerpt"><p id="excerpt-<?php echo $key; ?>" class="cc-slider-excerpt"><span class="textwrap"><?php echo $slide['excerpt'] ?></span></p></div>
							</div>
						</div>
					<?php } ?>

				</div>

				<!-- Side Preview -->
				<ol class="carousel-indicators cc-slider-side-preview">
					<?php
					foreach ($slides as $key => $slide) {
						echo '<li class="cc-slider-side-preview-wrap" data-target="#carousel-' . $name . '-generic" data-slide-to="' . $key . '">'; ?>

						<div class="cc-slider-side-preview-img-wrap"><img class="cc-slider-side-preview-img" src="<?php echo $slide['src'] ?>" width="200" alt="<?php echo $slide['title'] ?>"></div>
						<div class="cc-slider-side-preview-title-wrap">
							<span class="cc-slider-side-preview-title"><?php echo $slide['title'] ?></span>
							<span class="cc-slider-side-preview-excerpt"><?php echo $slide['excerpt'] ?></span>
						</div>
						<?php
						echo '</li>';

					}
					?>
				</ol>
			</div>

		<?php } ?>
	<?php } // end of (sizeof($slides) > 1 
	?>

	<?php if ( 'cc_first_inside_main_content' === get_theme_mod( 'cc_slider_position' ) ) { ?>
		</div>
	<?php } ?>
	
	<?php
	
	
} 

?>
