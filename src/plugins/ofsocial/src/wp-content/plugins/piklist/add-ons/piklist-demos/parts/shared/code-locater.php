
<div class="piklist-demo-help-source">

  <p>
  	<?php printf(__('The code that built this %s can be found here:','piklist-demo'), '<strong>' . $type . '</strong>'); ?><br>
    <code><?php echo str_replace(ABSPATH, '', $location); ?></code>
  </p>
  
</div>