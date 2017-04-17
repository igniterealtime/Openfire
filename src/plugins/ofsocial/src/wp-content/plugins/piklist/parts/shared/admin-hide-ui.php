
<style type="text/css">
  
  #adminmenuback,
  #adminmenuwrap,
  #wpadminbar,
  #screen-meta-links,
  #wphead,
  #wpfooter,
  #footer,
  .update-nag,
  .wrap h2:first-child,
  .wrap h2:nth-child(0),
  .wrap .icon32 {
    display: none !important;
    margin: 0 !important;
  }
  
  #wpcontent {
    margin-left: 15px !important;
    padding-top: 0px !important;
  }
  
  html.wp-toolbar {
    padding-top: 0px !important;
  }
  
  <?php if (isset($_REQUEST[piklist::$prefix]['embed']) && $_REQUEST[piklist::$prefix]['embed'] == 'true'): ?>
  
    .sidebars-column-1,
    .sidebars-column-2,
    .sidebars-column-3,
    .sidebars-column-4 {
      max-width: none !important;
    }
  
  <?php endif; ?>
  
</style>
