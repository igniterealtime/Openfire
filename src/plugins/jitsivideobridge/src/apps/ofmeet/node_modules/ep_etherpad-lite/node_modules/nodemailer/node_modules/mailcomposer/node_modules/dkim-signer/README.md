# DKIM Signer

Sign RFC822 messages with DKIM. This module is extracted from [mailcomposer](https://github.com/andris9/mailcomposer).

## Usage

```javascript
// require signer function
var DKIMSign = require("dkim-signer").DKIMSign;

// generate a RFC822 message
var rfc822message = "Subject: test\r\n\r\nHello world";

// setup DKIM options
var dkimOptions = {
    domainName: "müriaad-polüteism.info",
    keySelector: "dkim",
    privateKey: require("fs").readFileSync("./test_private.pem")
};

// generate signature header field
var signature = DKIMSign(rfc822message, dkimOptions);

// join signature header field with the message
console.log(signature + "\r\n" + rfc822message);
```

## License

**MIT**