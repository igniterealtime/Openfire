# Notify me plugin
This plugin will notify users when their names are mentioned and prefixed with a specific token

### Usage
    <script type="text/javascript" src="path_to_plugins/notifyme/candy.js"></script>
    <link rel="stylesheet" type="text/css" href="path_to_plugins/notifyme/candy.css" />

    ...

    CandyShop.NotifyMe.init();

### Configuration options
`nameIdentifier` - String - The identifier to look for in a string. Defaults to `'@'`

`playSound` - Boolean - Whether to play a sound when the username is mentioned. Defaults to `true`

`highlightInRoom` - Boolean - Whether to highlight the username when it is mentioned. Defaults to `true`

### Example configurations

    // Highlight my name when it's prefixed with a '+'
    CandyShop.NotifyMe.init({
        nameIdentifier: '+',
        playSound: false
    });

    // Highlight and play a sound if my name is prefixed with a '-'
    CandyShop.NotifyMe.init({
        nameIdentifier: '-'
    });