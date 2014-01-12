# Colors
Send and receive colored messages.

![Color Picker](screenshot.png)

## Usage
To enable *Colors* you have to include its JavaScript code and stylesheet: 

```HTML
<script type="text/javascript" src="candyshop/colors/candy.js"></script>
<link rel="stylesheet" type="text/css" href="candyshop/colors/candy.css" />
```

Call its `init()` method after Candy has been initialized: 

```JavaScript
Candy.init('/http-bind/');

// enable Colors plugin (default: 8 colors)
CandyShop.Colors.init(); 

Candy.Core.connect();
```

To enable less or more colors just call `CandyShop.Colors.init(<number-of-colors>)`.
