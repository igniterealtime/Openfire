<script type=text/javascript>
jQuery(document).ready( function($) {
   var WPHelpPointer = {
      pointers:[{
         target:'<?php echo $anchor; ?>',
         options:{
            content:'<?php echo $content; ?>',
            position:{
               edge:'<?php echo $edge; ?>',
               align:'<?php echo $align; ?>'
            }
         },
         pointer_id:'<?php echo $pointer_id; ?>'
      }]
   };
   $.each(WPHelpPointer.pointers, function(i) {
       piklist_help_pointer_open(i);
   });

   function piklist_help_pointer_open(i) {
       pointer = WPHelpPointer.pointers[i];
       options = $.extend( pointer.options, {
           close: function() {
               $.post( ajaxurl, {
                   pointer: pointer.pointer_id,
                   action: 'dismiss-wp-pointer'
               });
           }
       });
       $(pointer.target).pointer( options ).pointer('open');
   }
});
</script>