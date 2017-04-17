jQuery( document ).ready(function() {

	var bbp_topic_id = jQuery( '#bbp_topic_id' );

	bbp_topic_id.suggest(
		bbp_topic_id.data( 'ajax-url' ),
		{
			onSelect: function() {
				var value = this.value;
				bbp_topic_id.val( value.substr( 0, value.indexOf( ' ' ) ) );
			}
		}
	);
} );