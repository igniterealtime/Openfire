# Yajsml #

<pre>
                o          /|
    _  _  __  _  _   _ _  / |
   /  / /  | / _/| / / / / /
  |__/ /\_/\/ /\_\/ / |_/_/
 ___/_/ ___/_/  _______/___
/__/   /__/            \__/
</pre>

Yajsml is yet another (Common)JS module loader. It is a server-side component
that allows JavaScript code to be distributed in a reliable and performant way.
Its three features are:

 - Proxy pass through for individual resource requests.
 - Bulk responses for requests of closely associated resources (e.g.
   dependencies) when a request specifies a JSONP-style callback.
 - Canonical packaged resources where requests for disparate resources may be
   fulfilled through a redirect to one canonical packaged resource (which
   exploits warmed caches).

The tool’s interface is simple enough that there is no need for a prescribed
implementation on the client-side. That said, the
[require-kernel](https://github.com/cweider/require-kernel) is a terse
implementation of a CommonJS module manager that can use all the features in
yajsml.

## License ##
Released under the MIT license.

    Copyright (c) 2011 Chad Weider

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
    THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
