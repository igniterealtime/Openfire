<?php

/**
 * @author Umesh Kumar<umeshsingla05@gmail.com>
 */
if(!class_exists('RTDBModel')){
    return;
}
class RTMediaApiLogin extends RTDBModel {

    function __construct () {
        parent::__construct ( 'rtm_api' );
    }
}