<?php
/*
 * Page: piklist
 */

global $current_user;
get_currentuserinfo();

?>

<style type="text/css">

  html {
    background-color: #fff;
  }

  ul#adminmenu a.wp-has-current-submenu:after, ul#adminmenu > li.current > a.current:after {
    border-right-color: #fff;
  }

  .about-wrap .feature-section.two-col > div.alt-feature {
    float: right;
  }

  .wrap > h2 {
    display: none;
  }

  img.screenshot {
    width: 95%;
  }

  .icon16.icon-comments:before {
    font-size: 40px;
    padding: 0;
  }

  .piklist-badge {
    color: #DD3726;
    background: url('<?php echo piklist::$urls['piklist']; ?>/parts/img/piklist-logo.png') no-repeat center 0px #fff !important;
    margin-top: 0;
    padding-top: 85px;
    display: inline-block;
    font-size: 14px;
    font-weight: 600;
    height: 40px;
    text-align: center;
    text-rendering: optimizelegibility;
    width: 150px;
    position: absolute;
    right: 0;
    top: 0;
  }

    .piklist-badge span {
      font-size: 16px;
    }

  #mce-EMAIL {
    font-family: monospace;
    font-size: 14px;
    padding: 5px 2px;
    margin: 5px 0;
    width: 100%;
  }

  .piklist-social-links a {
    padding: 5px;
    color: #fff;
    text-decoration: none;
  }

  .piklist-social-links a:hover {
    text-decoration: none;
    color: #F0F0F0;
  } 
  
  .piklist-social-links a.facebook_link {
    background: #3460A1;
  }

  .piklist-social-links a.twitter_link {
    background: #29AAE3;
  }

  .piklist-social-links a.google_plus_link {
    background: #3460A1;
  }

  .piklist-social-links a span.dashicons {
    display: inline-block;
    -webkit-font-smoothing: antialiased;
    line-height: 1;
    font-family: 'Dashicons';
    text-decoration: none;
    font-weight: normal;
    font-style: normal;
    vertical-align: middle;
  }

  /* 3.7 style helpers */
  body.branch-3-7 .about-wrap .feature-section.col {
    margin-bottom: 0;
  }

  body.branch-3-7 .about-wrap hr {
    border: 0;
    height: 0;
    margin: 0;
    border-top: 1px solid rgba(0, 0, 0, 0.1);
  }

  body.branch-3-7 img.screenshot {
    vertical-align: bottom;
  }

  body.branch-3-7 .wrap h2 {
    text-align: center;
  }

  body.branch-3-7 .about-wrap .feature-section.two-col {
    padding-bottom: 0;
  }

  /* 3.6 style helpers */
  body.branch-3-6 .about-wrap .feature-section img {
    border: none;
    box-shadow: none;
    margin: 0;
    vertical-align: bottom;
  }

  body.branch-3-6 .about-wrap .feature-section.two-col {
    padding-bottom: 0px;
  }

  body.branch-3-6 .about-wrap hr {
    border: 0;
    height: 0;
    margin: 0;
    border-top: 1px solid rgba(0, 0, 0, 0.1);
  }

  body.branch-3-6 .about-wrap h3 {
    font-size: 22px;
  }



  @media (max-width: 782px) {

    html,
    #wpwrap {
      background-color: #fff;
    }

    .about-wrap .feature-section.two-col > div.alt-feature {
      float: none;
    }
}

</style>

<div class="wrap about-wrap">

<h1><?php echo __('Welcome to Piklist','piklist') . '&nbsp;'  . piklist::$version; ?></h1>

<div class="about-text"><?php _e('The most powerful framework available for WordPress.','piklist'); ?></div>

<div class="piklist-badge">
  <?php printf(__('%sP%sIKLIST%s','piklist'),'<span>','</span>','<br>');?>
  <?php printf(__('Version %s', 'piklist'), piklist::$version); ?>
</div>

<div class="piklist-social-links">
  <a class="facebook_link" href="http://facebook.com/piklist">
    <span class="dashicons dashicons-facebook-alt"></span>
  </a>
  <a class="twitter_link" href="http://twitter.com/piklist">
    <span class="dashicons dashicons-twitter"></span>
  </a>
  <a class="google_plus_link" href="https://plus.google.com/u/0/b/108403125978548990804/108403125978548990804/posts">
    <span class="dashicons dashicons-googleplus"></span>         
  </a>
</div><!-- .piklist-social-links -->



<div class="changelog">
  <h2 class="about-headline-callout"><?php _e('Now even more powerful than before.','piklist'); ?></h2>
</div>


<div class="changelog">
  <div class="feature-section col two-col">
    <div>
      <h3><?php _e('Post relationships', 'piklist');?></h3>
      <h4><?php _e('You\'ll wish all relationships were this easy.','piklist');?></h4>
      <p><?php printf(__('Post relationships are standard with Piklist and easy to setup. Displaying them in your theme is even easier, since you can use the standard WordPress %sget_posts%s function.','piklist'),'<code>','</code>');?></p>
    </div>
    <div class="last-feature about-colors-img">
      <img class="screenshot" src="<?php echo plugins_url('piklist/parts/img/post-relationships@2x.jpg');?>">
    </div>
  </div>
</div>

<hr>

<div class="changelog">
  <div class="feature-section col two-col">
    <div class="alt-feature">
      <h3><?php _e('Add-Mores');?></h3>
      <h4><?php _e('The infinite repeater field.','piklist');?></h4>
      <p><?php _e('Piklist AddMore fields are the repeater field you always dreamed of. Group together as many fields as you want and make them repeat indefinitely. Or place an Add-More within an Add-More within an Add-more...','piklist');?></p>
    </div>
    <div class="last-feature about-colors-img">
      <img class="screenshot" src="<?php echo plugins_url('piklist/parts/img/add-mores@2x.jpg');?>">
    </div>
  </div>
</div>

<hr>

<div class="changelog">
  <div class="feature-section col two-col">
    <div>
      <h3><?php _e('WorkFlows','piklist');?></h3>
      <h4><?php _e('The tab system you never knew was possible.','piklist');?></h4>
      <p><?php printf(__('Piklist WorkFlows allows you to place tabs anywhere... and with %sanything%s. Tabs can include content from any page or even custom views you create.','piklist'),'<strong>','</strong>');?></p>
    </div>
    <div class="last-feature about-colors-img">
      <img class="screenshot" src="<?php echo plugins_url('piklist/parts/img/workflow-user@2x.jpg');?>">
    </div>
  </div>
</div>

<hr>

<div class="changelog">
  <div class="feature-section col two-col">
    <div class="alt-feature">
      <h3><?php _e('Multiple user roles','piklist');?></h3>
      <h4><?php _e('Better security, more flexibility.','piklist');?></h4>
      <p><?php _e('Powerful web sites and applications require multiple user roles and Piklist supports this out of the box. Standard WordPress functions can be used to validate a user\'s permissions and provide appropriate access to data.','piklist');?></p>
    </div>
    <div class="last-feature about-colors-img">
      <img class="screenshot" src="<?php echo plugins_url('piklist/parts/img/user-roles@2x.jpg');?>">
    </div>
  </div>
</div>

<hr>

<div class="changelog">
  <h2 class="about-headline-callout"><?php _e('Intelligent field system','piklist');?></h2>
  <p class="about-description"><?php _e('Easily create powerful fields just the way you want... and place them wherever you want.','piklist');?></p>

  <div class="feature-section col three-col">

    <div class="col-1">
      <h3><?php _e('Conditional Logic','piklist');?></h3>
        <ul>
          <li><?php _e('Hide/show fields based on another fields value.','piklist');?></li>
          <li><?php _e('Auto-update another field.','piklist');?></li>
        </ul>
    </div>

    <div class="col-2">
      <h3><?php _e('Validate data','piklist');?></h3>
        <ul>
          <li><?php _e('Built in validation rules.','piklist');?></li>
          <li><?php _e('Easily add your own.','piklist');?></li>
          <li><?php _e('Apply multiple rules.','piklist');?></li>
        </ul>
    </div>

    <div class="col-3 last-feature">
      <h3><?php _e('Sanitize before saving','piklist');?></h3>
        <ul>
          <li><?php _e('Use WordPress sanitization functions.','piklist');?></li>
          <li><?php _e('Create your own.','piklist');?></li>
        </ul>
    </div>

  </div>

</div>


<hr>


<div class="changelog">
  <h2 class="about-headline-callout"><?php _e('Customize everything in WordPress.','piklist');?></h2>
  <p class="about-description"><?php _e('Post Types, Taxonomies, User Profiles, Settings, Admin Pages, Widgets, Dashboard, Contextual Help, and more...','piklist');?></p>

  <div class="feature-section col three-col">

    <div class="col-1">
      <h3><?php _e('Fields','piklist');?></h3>
        <ul>
          <li><?php _e('Lock field values.','piklist');?></li>
          <li><?php _e('Define field scopes.','piklist');?></li>
          <li><?php _e('Add Tooltip Help.','piklist');?></li>
          <li><?php _e('Customize field templates.','piklist');?></li>
        </ul>
    </div>

    <div class="col-2">
      <h3><?php _e('Meta Boxes','piklist');?></h3>
        <ul>
          <li><?php _e('Lock meta boxes','piklist');?></li>
          <li><?php _e('Show/hide by user capability or role','piklist');?></li>
          <li><?php _e('Set the order of meta boxes','piklist');?></li>
          <li><?php _e('Hide meta box when creating a new post/term','piklist');?></li>
        </ul>
    </div>

    <div class="col-3 last-feature">
      <h3><?php _e('Post Types','piklist');?></h3>
        <ul>
          <li><?php _e('Create custom post statuses','piklist');?></li>
          <li><?php _e('Change the "Enter title here" text','piklist');?></li>
          <li><?php _e('Custom admin body classes','piklist');?></li>
          <li><?php _e('Hide meta boxes','piklist');?></li>
        </ul>
    </div>

  </div>

  <div class="feature-section col three-col">
    
    <div class="col-1">
      <h3><?php _e('List Tables','piklist');?></h3>
        <ul>
          <li><?php _e('Change column headings','piklist');?></li>
          <li><?php _e('Show post states','piklist');?></li>
          <li><?php _e('Hide the post row actions','piklist');?></li>
        </ul>
    </div>

    <div class="col-2">
      <h3><?php _e('User Profiles','piklist');?></h3>
        <ul>
          <li><?php _e('Profiles can taken advantage of any Piklist field','piklist');?></li>
          <li><?php _e('Show/hide fields by user capability or role','piklist');?></li>
          <li><?php _e('Easily add User Taxonomies','piklist');?></li>
        </ul>
    </div>

    <div class="col-3 last-feature">
      <h3><?php _e('Widgets, Dashboard & Help','piklist');?></h3>
        <ul>
          <li><?php _e('Simply create complex widgets','piklist');?></li>
          <li><?php _e('No object oriented programming required','piklist');?></li>
          <li><?php _e('No help needed to create contextual help','piklist');?></li>
        </ul>
    </div>

  </div>

</div>

<hr>

<div class="changelog">
  <div class="feature-section col three-col">
    <div class="col-1">
      <h2 class="about-headline-callout"><?php _e('Get Started','piklist');?></h2>
      <p class="about-description"><?php _e('The built in demos is a great way to see what Piklist can do, and comes with tons of sample code.','piklist');?></p>
      <a href="<?php echo admin_url('admin.php?page=piklist-core-addons');?>"><?php printf(__('Activate Demos %s','piklist'),'→');?></a>
    </div>
    <div class="col-2">
      <h2 class="about-headline-callout"><?php _e('Get Help','piklist');?></h2>
      <p class="about-description"><?php _e('Visit the Piklist community forums to get answers to your questions, and suggest new features.','piklist');?></p>
      <a href="http://piklist.com/support/"><?php printf(__('Visit Forums %s','piklist'),'→');?></a>
    </div>
    <div class="col-3 last-feature">
      <h2 class="about-headline-callout"><?php _e('Get News','piklist');?></h2>
      <p class="about-description"><?php _e('Piklist updates in your inbox.','piklist');?></p>
        <form action="http://piklist.us5.list-manage.com/subscribe/post?u=48135d6d0775070599e9ddaee&amp;id=19ac927f9d" method="post" id="mc-embedded-subscribe-form" name="mc-embedded-subscribe-form" class="validate" target="_blank" novalidate>
        <label for="mce-EMAIL">
          <?php _e('Send to:', 'piklist');?>
        </label>
        <input type="email" value="<?php echo $current_user->user_email; ?>" name="EMAIL" class="regular-text email" id="mce-EMAIL" placeholder="Enter email address" required>
        <input type="hidden" name="SIGNUP" id="SIGNUP" value="plugin-piklist" />
        <div class="clear">
        <input type="submit" value="<?php _e('Subscribe','piklist');?>" name="subscribe" id="mc-embedded-subscribe" class="button">
        </div><!-- .clear -->
        </form>
    </div>
  </div>
</div>


<hr>

<p class="about-description">
  <?php _e('Piklist is created by a team of passionate individuals.','piklist');?>
</p>

<h4 class="wp-people-group"><?php _e('Project Leaders','piklist');?></h4>

<ul class="wp-people-group " id="wp-people-group-project-leaders">

<li class="wp-person" id="wp-person-miller">
  <a href="http://profiles.wordpress.org/p51labs/">
    <img src="http://0.gravatar.com/avatar/ed33891ef54d14d71cee542af5c64aa3?s=60" style="padding:0 5px 5px 0;" class="gravatar" alt="Kevin Miller" />
  </a>
  <a class="web" href="http://profiles.wordpress.org/p51labs/">Kevin Miller</a>
  <span class="title"><?php _e('Lead Developer','piklist');?></span>
</li>

<li class="wp-person" id="wp-person-bruner">
  <a href="http://profiles.wordpress.org/sbruner">
    <img src="http://www.gravatar.com/avatar/909371185bf3c3cd783b9580f394bd7f?s=60" class="gravatar" alt="Steve Bruner" />
    </a>
  <a class="web" href="http://profiles.wordpress.org/sbruner">Steve Bruner</a>
  <span class="title"><?php _e('Lead Developer','piklist');?></span>
</li>

</ul>

<h4 class="wp-people-group"><?php _e('Contributing Developers','piklist');?></h4>

<ul class="wp-people-group " id="wp-people-group-project-leaders">

  <li class="wp-person" id="wp-person-menard">
    <img src="https://s.gravatar.com/avatar/81f9841b95f38689faf73f1db763e754?s=60" class="gravatar" alt="Jason Adams" />
    <span>Jason Adams</span>
  </li>


  <li class="wp-person" id="wp-person-menard">
    <img src="http://1.gravatar.com/avatar/7b199884c1b4530d05aca31db88b19f6?s=60" class="gravatar" alt="Marcus Eby" />
    <span>Marcus Eby</span>
  </li>

  <li class="wp-person" id="wp-person-menard">
    <img src="http://1.gravatar.com/avatar/fa3dfd09d81f6c8b3494c2f75ef4139d?s=60" class="gravatar" alt="Daniel Ménard" />
    <span>Daniel Ménard</span>
  </li>

  <li class="wp-person" id="wp-person-menard">
    <img src="https://s.gravatar.com/avatar/02120fb28fa6ff0222f939e840e3c970?s=60" class="gravatar" alt="Daniel Rampanelli" />
    <span>Daniel Rampanelli</span>
  </li>

</ul>



<hr>

<p class="about-description">
  <?php _e('Follow Piklist','piklist');?>
</p>


<div class="piklist-social-links">
  <a class="facebook_link" href="http://facebook.com/piklist">
    <span class="dashicons dashicons-facebook-alt"></span>
  </a>
  <a class="twitter_link" href="http://twitter.com/piklist">
    <span class="dashicons dashicons-twitter"></span>
  </a>
  <a class="google_plus_link" href="https://plus.google.com/u/0/b/108403125978548990804/108403125978548990804/posts">
    <span class="dashicons dashicons-googleplus"></span>         
  </a>
</div><!-- .piklist-social-links -->

</div>

  <script type="text/javascript">
  var addthis_share = {
      url_transforms : {
          shorten: {
               twitter: 'bitly'
          }
      }, 
      shorteners : {
          bitly : {}
      }
  }
  var addthis_config = {"data_track_addressbar":false};</script>
  <script type="text/javascript" src="//s7.addthis.com/js/300/addthis_widget.js#pubid=ra-4fc6697407a3afe4"></script>
  <!-- AddThis Button END -->
