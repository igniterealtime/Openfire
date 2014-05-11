This is [Unicode Normalizer] in a Common JS module. I'm not affiliated with
Matsuza, the author of Unicode Normalizer.

Installation
------------

    npm install unorm


Usage example
-------------

For a longer example, see `example.js`.

    unorm = require('unorm');

    text =
      'The \u212B symbol invented by A. J. \u00C5ngstr\u00F6m ' +
      '(1814, L\u00F6gd\u00F6, \u2013 1874) denotes the length ' +
      '10\u207B\u00B9\u2070 m.';

    combining = /[\u0300-\u036F]/g; // Use XRegExp('\\p{M}', 'g'); see example.js.

    console.log('Regular:  ' + text);
    console.log('NFC:      ' + unorm.nfc(text));
    console.log('NFD:      ' + unorm.nfd(text));
    console.log('NFKC:     ' + unorm.nfkc(text));
    console.log('NFKD: *   ' + unorm.nfkd(text).replace(combining, ''));
    console.log(' * = Combining characters removed from decomposed form.');


License
-------

This project includes the software package **Unicode Normalizer 1.0.0**. The
software dual licensed under the MIT and GPL licenses. Here is the MIT license:

    Copyright (c) 2008 Matsuza <matsuza@gmail.com>
    
    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to
    deal in the Software without restriction, including without limitation the
    rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
    sell copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:
    
    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.
    
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
    IN THE SOFTWARE.


[Unicode Normalizer]: http://coderepos.org/share/browser/lang/javascript/UnicodeNormalizer
