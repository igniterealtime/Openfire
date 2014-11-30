# Notifications
Send HTML5 Notifications when a message is received and the window is not in focus. This only works with webkit browsers.

## Usage
To enable *Notifications* you have to include its JavaScript code and stylesheet: 

```HTML
<script type="text/javascript" src="candyshop/notifications/candy.js"></script>
```

Call its `init()` method after Candy has been initialized: 

```JavaScript
Candy.init('/http-bind/');

CandyShop.Notifications.init(); 

Candy.Core.connect();
```

It is possible to configure the Plugin.

```JavaScript
CandyShop.Notifications.init({
	notifyNormalMessage: false,		// Send a notification for every message. Defaults to false
	notifyPersonalMessage: true,	// Send a notification if the user is mentioned. (Requires NotfiyMe Plugin) Defaults to true
	closeTime: 3000					// Close notification after X milliseconds. Zero means it doesn't close automaticly. Defaults to 3000
});
```