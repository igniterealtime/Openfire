# Inline Images
If a user posts a URL to an image, that image gets rendered directly inside of Candy.

![Inline Images](screenshot.png)

## Usage
Include the JavaScript and CSS files:

```HTML
<script type="text/javascript" src="candyshop/inline-images/candy.js"></script>
<link rel="stylesheet" type="text/css" href="candyshop/inline-images/candy.css" />
```

To enable the Inline Images plugin, just add one of the ´init´ methods to your bootstrap:

```JavaScript
// init with default settings:
CandyShop.InlineImages.init();

// customized initialization:
CandyShop.InlineImages.initWithFileExtensions(['png','jpg']);  // only recognize PNG and JPG files as image
CandyShop.InlineImages.initWithMaxImageSize(150);  // resize images to a maximum edge size of 150px
CandyShop.InlineImages.initWithFileExtensionsAndMaxImageSize(['png','jpg'], 150);  // combination of the above examples
```