
<form 
  method="<?php echo strtolower($method); ?>" 
  action="<?php echo isset($action) ? home_url() . $action : $_SERVER['REQUEST_URI']; ?>" 
  enctype="multipart/form-data"
  id="<?php echo $form_id; ?>"
>

  <?php
  
    piklist('field', array(
      'type' => 'hidden'
      ,'scope' => piklist::$prefix
      ,'field' => 'nonce'
      ,'value' => $nonce
    ));
  
    if (!empty($ids))
    {
      foreach ($ids as $type => $id)
      {
        piklist('field', array(
          'type' => 'hidden'
          ,'scope' => $type
          ,'field' => (in_array($type, array('comment')) ? $type . '_' : '') . (in_array($type, array('taxonomy')) ? 'id' : 'ID')
          ,'value' => $id
        ));
      }
    }
    
    foreach (array('post', 'comment', 'taxonomy', 'user') as $type)
    {
      $field = (in_array($type, array('comment')) ? $type . '_' : '') . (in_array($type, array('taxonomy')) ? 'id' : 'ID');
      if (isset($_REQUEST[$field]))
      {
        piklist_form::$save_ids[$type] = $_REQUEST[$field];
      } 
    }
    
    if (isset($_REQUEST['ID']))
    {
      piklist_form::$save_ids['post'] = $_REQUEST['ID'];
      
      piklist('field', array(
        'type' => 'hidden'
        ,'scope' => 'post'
        ,'field' => 'ID'
        ,'value' => $_REQUEST['ID']
      ));
    }
    
    if (isset($filter) && $filter == 'true')
    {
      piklist('field', array(
        'type' => 'hidden'
        ,'scope' => piklist::$prefix
        ,'field' => 'filter'
        ,'value' => 'true'
      ));
    }
    
  ?>
  
  <?php piklist::render($form); ?>
  
  <?php piklist_form::save_fields(); ?>  
  
</form>