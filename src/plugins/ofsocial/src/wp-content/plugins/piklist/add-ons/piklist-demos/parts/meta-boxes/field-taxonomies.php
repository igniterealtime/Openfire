<?php
/*
Title: Taxonomies
Post Type: piklist_demo,piklist_lite_demo
Order: 20
Priority: default
Context: side
Collapse: true
*/
?>

<h3 class="demo-highlight">
  <?php _e('Create your own Taxonomy metabox, and display the terms as a checkbox, radio, select or any field you can imagine.','piklist-demo');?>
</h3>

<?php

  piklist('field', array(
    'type' => 'radio'
    ,'scope' => 'taxonomy'
    ,'field' => 'piklist_demo_type'
    ,'label' => 'Demo Types'
    ,'description' => 'Terms will appear when they are added to <a href="' . network_admin_url() . 'edit-tags.php?taxonomy=piklist_demo_type&post_type=piklist_demo">the Demo taxonomy</a>.'
    ,'choices' => piklist(
      get_terms('piklist_demo_type', array(
        'hide_empty' => false
      ))
      ,array(
        'term_id'
        ,'name'
      )
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));
  
?>