<footer class="site-footer">
		<div class="site-info">
        	<?php printf(__('Powered by <a href="%s">WordPress</a>.','onetone'),esc_url('http://wordpress.org/'));?>
            <?php
			if( is_home() || is_front_page()){
			 printf(__('Designed by <a href="%s">MageeWP Themes</a>.','onetone'),esc_url('http://www.mageewp.com/'));
			}
			?>
		</div>
	</footer>
</div>
<?php
	wp_footer();
?>
</body>
</html>