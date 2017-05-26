
<?php
  piklist('field', array(
    'type' => 'hidden'
    ,'scope' => piklist::$prefix
    ,'field' => 'fields_id'
    ,'value' => $fields_id
    ,'widget' => false
    ,'attributes' => array(
      'class' => 'piklist-fields-id'
    )
  ));
?>

<script type="text/javascript">

  if (typeof piklist_fields == 'undefined')
  {
    var piklist_fields = [];
  }

  piklist_fields['<?php echo $fields_id; ?>'] = <?php echo json_encode($fields); ?>;

</script>