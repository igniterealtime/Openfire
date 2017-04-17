<?php

$_rules = <<<EOF
# BEGIN bbPress

Options -MultiViews

<IfModule mod_rewrite.c>
RewriteEngine On
RewriteBase %PATH%

RewriteRule ^page/([0-9]+)/?$ %PATH%index.php?page=$1 [L,QSA]
RewriteRule ^forum/([^/]+)/page/([0-9]+)/?$ %PATH%forum.php?id=$1&page=$2 [L,QSA]
RewriteRule ^forum/([^/]+)/?$ %PATH%forum.php?id=$1 [L,QSA]
RewriteRule ^forum/?$ %PATH% [R=302,L,QSA]
RewriteRule ^topic/([^/]+)/page/([0-9]+)/?$ %PATH%topic.php?id=$1&page=$2 [L,QSA]
RewriteRule ^topic/([^/]+)/?$ %PATH%topic.php?id=$1 [L,QSA]
RewriteRule ^topic/?$ %PATH% [R=302,L,QSA]
RewriteRule ^tags/([^/]+)/page/([0-9]+)/?$ %PATH%tags.php?tag=$1&page=$2 [L,QSA]
RewriteRule ^tags/([^/]+)/?$ %PATH%tags.php?tag=$1 [L,QSA]
RewriteRule ^tags/?$ %PATH%tags.php [L,QSA]
RewriteRule ^profile/([^/]+)/page/([0-9]+)/?$ %PATH%profile.php?id=$1&page=$2 [L,QSA]
RewriteRule ^profile/([^/]+)/([^/]+)/?$ %PATH%profile.php?id=$1&tab=$2 [L,QSA]
RewriteRule ^profile/([^/]+)/([^/]+)/page/([0-9]+)/?$ %PATH%profile.php?id=$1&tab=$2&page=$3 [L,QSA]
RewriteRule ^profile/([^/]+)/?$ %PATH%profile.php?id=$1 [L,QSA]
RewriteRule ^profile/?$ %PATH%profile.php [L,QSA]
RewriteRule ^view/([^/]+)/page/([0-9]+)/?$ %PATH%view.php?view=$1&page=$2 [L,QSA]
RewriteRule ^view/([^/]+)/?$ %PATH%view.php?view=$1 [L,QSA]
RewriteRule ^rss/?$ %PATH%rss.php [L,QSA]
RewriteRule ^rss/topics/?$ %PATH%rss.php?topics=1 [L,QSA]
RewriteRule ^rss/forum/([^/]+)/?$ %PATH%rss.php?forum=$1 [L,QSA]
RewriteRule ^rss/forum/([^/]+)/topics/?$ %PATH%rss.php?forum=$1&topics=1 [L,QSA]
RewriteRule ^rss/topic/([^/]+)/?$ %PATH%rss.php?topic=$1 [L,QSA]
RewriteRule ^rss/tags/([^/]+)/?$ %PATH%rss.php?tag=$1 [L,QSA]
RewriteRule ^rss/tags/([^/]+)/topics/?$ %PATH%rss.php?tag=$1&topics=1 [L,QSA]
RewriteRule ^rss/profile/([^/]+)/?$ %PATH%rss.php?profile=$1 [L,QSA]
RewriteRule ^rss/view/([^/]+)/?$ %PATH%rss.php?view=$1 [L,QSA]
RewriteCond %{REQUEST_FILENAME} !-f
RewriteCond %{REQUEST_FILENAME} !-d
RewriteRule ^.*$ %PATH%index.php [L]
</IfModule>

# END bbPress
EOF;

$_rules = str_replace( '%PATH%', bb_get_option( 'path' ), $_rules );