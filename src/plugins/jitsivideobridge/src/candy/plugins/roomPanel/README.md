# RoomPanel 
Adds Icon show a lists rooms, also allows to show rooms upon connection and when all rooms are closed.

![RoomPanel](screenshot.png)

## Usage
To enable *RoomPanel* you have to include its JavaScript code and stylesheet: 

```HTML
<script type="text/javascript" src="candyshop/roomPanel/roomPanel.js"></script>
<link rel="stylesheet" type="text/css" href="candyshop/roomPanel/default.css" />
```

Call its `init()` method after Candy has been initialized: 

```JavaScript
Candy.init('/http-bind/');

// enable RoomPanel plugin
CandyShop.RoomPanel.init({
    // domain that hosts the muc rooms, only required if autoDetectRooms is enabled
    mucDomain: 'conference.yourdomain.com',

    // allow you to force a list of rooms, only required if autoDetectRoom is disabled 
    roomList: [
        {
            name: 'my room',
            jid:  'my-room@conference.yourdomain.com'
        },
        {
            name: 'other room',
            jid:  'other-room@conference.yourdomain.com'
        }
    ], 

    // show room list if all rooms are closed, default value is true. [optional]
    showIfAllTabClosed: true,
    
    // detect rooms before showing list, default value is true. [optional] 
    autoDetectRooms: true,

    // how long in seconds before refreshing room list, default value is 600. [optional]
    roomCacheTime: 600
}); 

Candy.Core.connect();

