<?php
/**
 * iBuddy custom widgets
 *
 * @package iBuddy
 */
?>
<?php

/***** Login Widget *******/

class Login_Widget extends WP_Widget {
  function Login_Widget() {
    $widget_options = array(
    'classname' => 'Login_Widget',
    'description' => 'Displays the login form' );
    parent::WP_Widget("Login_Widget", "iBuddy Login Form", $widget_options);
  }
 
  public function form( $instance ) {
    $instance = wp_parse_args( (array) $instance, array( 'title' => '') );
    $title = $instance['title'];

?>

    <p>
    <label for="<?php echo $this->get_field_id('title'); ?>">Title</label></br>
    <input id="<?php echo $this->get_field_id( 'title' ); ?>" name="<?php echo $this->get_field_name( 'title' ); ?>" type="text" value="<?php echo $title ?>" placeholder="Login" />
    </p>
    

<?php

}
 
  public function update( $new_instance, $old_instance ) {
    $instance = array();
    $instance['title'] = $new_instance['title']; 
    return $instance;
  }
 
  public function widget( $args, $instance ) {
    extract( $args );
    $title = apply_filters( 'widget_title', $instance['title'] );
 
    echo '<aside id="' .$title. '-widget" class="widget widget_categories">';
    echo '<h1 class="widget-title">' .$title. '</h1>';
    echo '<div class="login-widget">'.wp_login_form().'</div>';
    echo '</aside>';
  }
}

add_action( 'widgets_init', create_function( '', 'register_widget("Login_Widget");'));
