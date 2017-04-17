<?php
require('admin-action.php');

wp_redirect( bb_get_uri('bb-admin/options-permalinks.php', null, BB_URI_CONTEXT_BB_ADMIN + BB_URI_CONTEXT_HEADER) );