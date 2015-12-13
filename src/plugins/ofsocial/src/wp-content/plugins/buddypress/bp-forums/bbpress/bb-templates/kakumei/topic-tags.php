<div id="topic-tags">
<p><?php _e('Tags:'); ?></p>

<?php if ( bb_get_topic_tags() ) : ?>

<?php bb_list_tags(); ?>

<?php else : ?>

<p><?php printf(__('No <a href="%s">tags</a> yet.'), bb_get_tag_page_link() ); ?></p>

<?php endif; ?>

<?php tag_form(); ?>

</div>
