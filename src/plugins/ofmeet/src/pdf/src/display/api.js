/* -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*- */
/* vim: set shiftwidth=2 tabstop=2 autoindent cindent expandtab: */
/* Copyright 2012 Mozilla Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* globals CanvasGraphics, combineUrl, createScratchCanvas, error,
           FontLoader, globalScope, info, isArrayBuffer, loadJpegStream,
           MessageHandler, PDFJS, Promise, StatTimer, warn,
           PasswordResponses, Util, loadScript, LegacyPromise,
           FontFace */

'use strict';

/**
 * The maximum allowed image size in total pixels e.g. width * height. Images
 * above this value will not be drawn. Use -1 for no limit.
 * @var {number}
 */
PDFJS.maxImageSize = PDFJS.maxImageSize === undefined ? -1 : PDFJS.maxImageSize;

/**
 * By default fonts are converted to OpenType fonts and loaded via font face
 * rules. If disabled, the font will be rendered using a built in font renderer
 * that constructs the glyphs with primitive path commands.
 * @var {boolean}
 */
PDFJS.disableFontFace = PDFJS.disableFontFace === undefined ?
                        false : PDFJS.disableFontFace;

/**
 * Path for image resources, mainly for annotation icons. Include trailing
 * slash.
 * @var {string}
 */
PDFJS.imageResourcesPath = PDFJS.imageResourcesPath === undefined ?
                           '' : PDFJS.imageResourcesPath;

/**
 * Disable the web worker and run all code on the main thread. This will happen
 * automatically if the browser doesn't support workers or sending typed arrays
 * to workers.
 * @var {boolean}
 */
PDFJS.disableWorker = PDFJS.disableWorker === undefined ?
                      false : PDFJS.disableWorker;

/**
 * Path and filename of the worker file. Required when the worker is enabled in
 * development mode. If unspecified in the production build, the worker will be
 * loaded based on the location of the pdf.js file.
 * @var {string}
 */
PDFJS.workerSrc = PDFJS.workerSrc === undefined ? null : PDFJS.workerSrc;

/**
 * Disable range request loading of PDF files. When enabled and if the server
 * supports partial content requests then the PDF will be fetched in chunks.
 * Enabled (false) by default.
 * @var {boolean}
 */
PDFJS.disableRange = PDFJS.disableRange === undefined ?
                     false : PDFJS.disableRange;

/**
 * Disable pre-fetching of PDF file data. When range requests are enabled PDF.js
 * will automatically keep fetching more data even if it isn't needed to display
 * the current page. This default behavior can be disabled.
 * @var {boolean}
 */
PDFJS.disableAutoFetch = PDFJS.disableAutoFetch === undefined ?
                         false : PDFJS.disableAutoFetch;

/**
 * Enables special hooks for debugging PDF.js.
 * @var {boolean}
 */
PDFJS.pdfBug = PDFJS.pdfBug === undefined ? false : PDFJS.pdfBug;

/**
 * Enables transfer usage in postMessage for ArrayBuffers.
 * @var {boolean}
 */
PDFJS.postMessageTransfers = PDFJS.postMessageTransfers === undefined ?
                             true : PDFJS.postMessageTransfers;

/**
 * Disables URL.createObjectURL usage.
 * @var {boolean}
 */
PDFJS.disableCreateObjectURL = PDFJS.disableCreateObjectURL === undefined ?
                               false : PDFJS.disableCreateObjectURL;

/**
 * Controls the logging level.
 * The constants from PDFJS.VERBOSITY_LEVELS should be used:
 * - errors
 * - warnings [default]
 * - infos
 * @var {number}
 */
PDFJS.verbosity = PDFJS.verbosity === undefined ?
                  PDFJS.VERBOSITY_LEVELS.warnings : PDFJS.verbosity;

/**
 * Document initialization / loading parameters object.
 *
 * @typedef {Object} DocumentInitParameters
 * @property {string}     url   - The URL of the PDF.
 * @property {TypedArray} data  - A typed array with PDF data.
 * @property {Object}     httpHeaders - Basic authentication headers.
 * @property {boolean}    withCredentials - Indicates whether or not cross-site
 *   Access-Control requests should be made using credentials such as cookies
 *   or authorization headers. The default is false.
 * @property {string}     password - For decrypting password-protected PDFs.
 * @property {TypedArray} initialData - A typed array with the first portion or
 *   all of the pdf data. Used by the extension since some data is already
 *   loaded before the switch to range requests.
 */

/**
 * This is the main entry point for loading a PDF and interacting with it.
 * NOTE: If a URL is used to fetch the PDF data a standard XMLHttpRequest(XHR)
 * is used, which means it must follow the same origin rules that any XHR does
 * e.g. No cross domain requests without CORS.
 *
 * @param {string|TypedArray|DocumentInitParameters} source Can be a url to
 * where a PDF is located, a typed array (Uint8Array) already populated with
 * data or parameter object.
 *
 * @param {Object} pdfDataRangeTransport is optional. It is used if you want
 * to manually serve range requests for data in the PDF. See viewer.js for
 * an example of pdfDataRangeTransport's interface.
 *
 * @param {function} passwordCallback is optional. It is used to request a
 * password if wrong or no password was provided. The callback receives two
 * parameters: function that needs to be called with new password and reason
 * (see {PasswordResponses}).
 *
 * @return {Promise} A promise that is resolved with {@link PDFDocumentProxy}
 *   object.
 */
PDFJS.getDocument = function getDocument(source,
                                         pdfDataRangeTransport,
                                         passwordCallback,
                                         progressCallback) {
  var workerInitializedPromise, workerReadyPromise, transport;

  if (typeof source === 'string') {
    source = { url: source };
  } else if (isArrayBuffer(source)) {
    source = { data: source };
  } else if (typeof source !== 'object') {
    error('Invalid parameter in getDocument, need either Uint8Array, ' +
          'string or a parameter object');
  }

  if (!source.url && !source.data)
    error('Invalid parameter array, need either .data or .url');

  // copy/use all keys as is except 'url' -- full path is required
  var params = {};
  for (var key in source) {
    if (key === 'url' && typeof window !== 'undefined') {
      params[key] = combineUrl(window.location.href, source[key]);
      continue;
    }
    params[key] = source[key];
  }

  workerInitializedPromise = new PDFJS.LegacyPromise();
  workerReadyPromise = new PDFJS.LegacyPromise();
  transport = new WorkerTransport(workerInitializedPromise,
      workerReadyPromise, pdfDataRangeTransport, progressCallback);
  workerInitializedPromise.then(function transportInitialized() {
    transport.passwordCallback = passwordCallback;
    transport.fetchDocument(params);
  });
  return workerReadyPromise;
};

/**
 * Proxy to a PDFDocument in the worker thread. Also, contains commonly used
 * properties that can be read synchronously.
 * @class
 */
var PDFDocumentProxy = (function PDFDocumentProxyClosure() {
  function PDFDocumentProxy(pdfInfo, transport) {
    this.pdfInfo = pdfInfo;
    this.transport = transport;
  }
  PDFDocumentProxy.prototype = /** @lends PDFDocumentProxy.prototype */ {
    /**
     * @return {number} Total number of pages the PDF contains.
     */
    get numPages() {
      return this.pdfInfo.numPages;
    },
    /**
     * @return {string} A unique ID to identify a PDF. Not guaranteed to be
     * unique.
     */
    get fingerprint() {
      return this.pdfInfo.fingerprint;
    },
    /**
     * @return {boolean} true if embedded document fonts are in use. Will be
     * set during rendering of the pages.
     */
    get embeddedFontsUsed() {
      return this.transport.embeddedFontsUsed;
    },
    /**
     * @param {number} pageNumber The page number to get. The first page is 1.
     * @return {Promise} A promise that is resolved with a {@link PDFPageProxy}
     * object.
     */
    getPage: function PDFDocumentProxy_getPage(pageNumber) {
      return this.transport.getPage(pageNumber);
    },
    /**
     * @param {{num: number, gen: number}} ref The page reference. Must have
     *   the 'num' and 'gen' properties.
     * @return {Promise} A promise that is resolved with the page index that is
     * associated with the reference.
     */
    getPageIndex: function PDFDocumentProxy_getPageIndex(ref) {
      return this.transport.getPageIndex(ref);
    },
    /**
     * @return {Promise} A promise that is resolved with a lookup table for
     * mapping named destinations to reference numbers.
     */
    getDestinations: function PDFDocumentProxy_getDestinations() {
      return this.transport.getDestinations();
    },
    /**
     * @return {Promise} A promise that is resolved with an array of all the
     * JavaScript strings in the name tree.
     */
    getJavaScript: function PDFDocumentProxy_getJavaScript() {
      var promise = new PDFJS.LegacyPromise();
      var js = this.pdfInfo.javaScript;
      promise.resolve(js);
      return promise;
    },
    /**
     * @return {Promise} A promise that is resolved with an {Array} that is a
     * tree outline (if it has one) of the PDF. The tree is in the format of:
     * [
     *  {
     *   title: string,
     *   bold: boolean,
     *   italic: boolean,
     *   color: rgb array,
     *   dest: dest obj,
     *   items: array of more items like this
     *  },
     *  ...
     * ].
     */
    getOutline: function PDFDocumentProxy_getOutline() {
      var promise = new PDFJS.LegacyPromise();
      var outline = this.pdfInfo.outline;
      promise.resolve(outline);
      return promise;
    },
    /**
     * @return {Promise} A promise that is resolved with an {Object} that has
     * info and metadata properties.  Info is an {Object} filled with anything
     * available in the information dictionary and similarly metadata is a
     * {Metadata} object with information from the metadata section of the PDF.
     */
    getMetadata: function PDFDocumentProxy_getMetadata() {
      var promise = new PDFJS.LegacyPromise();
      var info = this.pdfInfo.info;
      var metadata = this.pdfInfo.metadata;
      promise.resolve({
        info: info,
        metadata: metadata ? new PDFJS.Metadata(metadata) : null
      });
      return promise;
    },
    /**
     * @return {Promise} A promise that is resolved with a TypedArray that has
     * the raw data from the PDF.
     */
    getData: function PDFDocumentProxy_getData() {
      var promise = new PDFJS.LegacyPromise();
      this.transport.getData(promise);
      return promise;
    },
    /**
     * @return {Promise} A promise that is resolved when the document's data
     * is loaded. It is resolved with an {Object} that contains the length
     * property that indicates size of the PDF data in bytes.
     */
    getDownloadInfo: function PDFDocumentProxy_getDownloadInfo() {
      return this.transport.downloadInfoPromise;
    },
    /**
     * Cleans up resources allocated by the document, e.g. created @font-face.
     */
    cleanup: function PDFDocumentProxy_cleanup() {
      this.transport.startCleanup();
    },
    /**
     * Destroys current document instance and terminates worker.
     */
    destroy: function PDFDocumentProxy_destroy() {
      this.transport.destroy();
    }
  };
  return PDFDocumentProxy;
})();

/**
 * Page text content part.
 *
 * @typedef {Object} BidiText
 * @property {string} str - text content.
 * @property {string} dir - text direction: 'ttb', 'ltr' or 'rtl'.
 * @property {number} x - x position of the text on the page.
 * @property {number} y - y position of the text on the page.
 * @property {number} angle - text rotation.
 * @property {number} size - font size.
 */

/**
 * Proxy to a PDFPage in the worker thread.
 * @class
 */
var PDFPageProxy = (function PDFPageProxyClosure() {
  function PDFPageProxy(pageInfo, transport) {
    this.pageInfo = pageInfo;
    this.transport = transport;
    this.stats = new StatTimer();
    this.stats.enabled = !!globalScope.PDFJS.enableStats;
    this.commonObjs = transport.commonObjs;
    this.objs = new PDFObjects();
    this.receivingOperatorList  = false;
    this.cleanupAfterRender = false;
    this.pendingDestroy = false;
    this.renderTasks = [];
  }
  PDFPageProxy.prototype = /** @lends PDFPageProxy.prototype */ {
    /**
     * @return {number} Page number of the page. First page is 1.
     */
    get pageNumber() {
      return this.pageInfo.pageIndex + 1;
    },
    /**
     * @return {number} The number of degrees the page is rotated clockwise.
     */
    get rotate() {
      return this.pageInfo.rotate;
    },
    /**
     * @return {Object} The reference that points to this page. It has 'num' and
     * 'gen' properties.
     */
    get ref() {
      return this.pageInfo.ref;
    },
    /**
     * @return {Array} An array of the visible portion of the PDF page in the
     * user space units - [x1, y1, x2, y2].
     */
    get view() {
      return this.pageInfo.view;
    },
    /**
     * @param {number} scale The desired scale of the viewport.
     * @param {number} rotate Degrees to rotate the viewport. If omitted this
     * defaults to the page rotation.
     * @return {PageViewport} Contains 'width' and 'height' properties along
     * with transforms required for rendering.
     */
    getViewport: function PDFPageProxy_getViewport(scale, rotate) {
      if (arguments.length < 2)
        rotate = this.rotate;
      return new PDFJS.PageViewport(this.view, scale, rotate, 0, 0);
    },
    /**
     * @return {Promise} A promise that is resolved with an {Array} of the
     * annotation objects.
     */
    getAnnotations: function PDFPageProxy_getAnnotations() {
      if (this.annotationsPromise)
        return this.annotationsPromise;

      var promise = new PDFJS.LegacyPromise();
      this.annotationsPromise = promise;
      this.transport.getAnnotations(this.pageInfo.pageIndex);
      return promise;
    },
    /**
     * Begins the process of rendering a page to the desired context.
     * @param {Object} params A parameter object that supports:
     * {
     *   canvasContext(required): A 2D context of a DOM Canvas object.,
     *   textLayer(optional): An object that has beginLayout, endLayout, and
     *                        appendText functions.,
     *   imageLayer(optional): An object that has beginLayout, endLayout and
     *                         appendImage functions.,
     *   continueCallback(optional): A function that will be called each time
     *                               the rendering is paused.  To continue
     *                               rendering call the function that is the
     *                               first argument to the callback.
     * }.
     * @return {RenderTask} An extended promise that is resolved when the page
     * finishes rendering (see RenderTask).
     */
    render: function PDFPageProxy_render(params) {
      var stats = this.stats;
      stats.time('Overall');

      // If there was a pending destroy cancel it so no cleanup happens during
      // this call to render.
      this.pendingDestroy = false;

      // If there is no displayReadyPromise yet, then the operatorList was never
      // requested before. Make the request and create the promise.
      if (!this.displayReadyPromise) {
        this.receivingOperatorList = true;
        this.displayReadyPromise = new LegacyPromise();
        this.operatorList = {
          fnArray: [],
          argsArray: [],
          lastChunk: false
        };

        this.stats.time('Page Request');
        this.transport.messageHandler.send('RenderPageRequest', {
          pageIndex: this.pageNumber - 1
        });
      }

      var internalRenderTask = new InternalRenderTask(complete, params,
                                       this.objs, this.commonObjs,
                                       this.operatorList, this.pageNumber);
      this.renderTasks.push(internalRenderTask);
      var renderTask = new RenderTask(internalRenderTask);

      var self = this;
      this.displayReadyPromise.then(
        function pageDisplayReadyPromise(transparency) {
          if (self.pendingDestroy) {
            complete();
            return;
          }
          stats.time('Rendering');
          internalRenderTask.initalizeGraphics(transparency);
          internalRenderTask.operatorListChanged();
        },
        function pageDisplayReadPromiseError(reason) {
          complete(reason);
        }
      );

      function complete(error) {
        var i = self.renderTasks.indexOf(internalRenderTask);
        if (i >= 0) {
          self.renderTasks.splice(i, 1);
        }

        if (self.cleanupAfterRender) {
          self.pendingDestroy = true;
        }
        self._tryDestroy();

        if (error) {
          renderTask.promise.reject(error);
        } else {
          renderTask.promise.resolve();
        }
        stats.timeEnd('Rendering');
        stats.timeEnd('Overall');
      }

      return renderTask;
    },
    /**
     * @return {Promise} That is resolved with the array of {@link BidiText}
     * objects that represent the page text content.
     */
    getTextContent: function PDFPageProxy_getTextContent() {
      var promise = new PDFJS.LegacyPromise();
      this.transport.messageHandler.send('GetTextContent', {
          pageIndex: this.pageNumber - 1
        },
        function textContentCallback(textContent) {
          promise.resolve(textContent);
        }
      );
      return promise;
    },
    /**
     * Destroys resources allocated by the page.
     */
    destroy: function PDFPageProxy_destroy() {
      this.pendingDestroy = true;
      this._tryDestroy();
    },
    /**
     * For internal use only. Attempts to clean up if rendering is in a state
     * where that's possible.
     * @ignore
     */
    _tryDestroy: function PDFPageProxy__destroy() {
      if (!this.pendingDestroy ||
          this.renderTasks.length !== 0 ||
          this.receivingOperatorList) {
        return;
      }

      delete this.operatorList;
      delete this.displayReadyPromise;
      this.objs.clear();
      this.pendingDestroy = false;
    },
    /**
     * For internal use only.
     * @ignore
     */
    _startRenderPage: function PDFPageProxy_startRenderPage(transparency) {
      this.displayReadyPromise.resolve(transparency);
    },
    /**
     * For internal use only.
     * @ignore
     */
    _renderPageChunk: function PDFPageProxy_renderPageChunk(operatorListChunk) {
      // Add the new chunk to the current operator list.
      for (var i = 0, ii = operatorListChunk.length; i < ii; i++) {
        this.operatorList.fnArray.push(operatorListChunk.fnArray[i]);
        this.operatorList.argsArray.push(operatorListChunk.argsArray[i]);
      }
      this.operatorList.lastChunk = operatorListChunk.lastChunk;

      // Notify all the rendering tasks there are more operators to be consumed.
      for (var i = 0; i < this.renderTasks.length; i++) {
        this.renderTasks[i].operatorListChanged();
      }

      if (operatorListChunk.lastChunk) {
        this.receivingOperatorList = false;
        this._tryDestroy();
      }
    }
  };
  return PDFPageProxy;
})();

/**
 * For internal use only.
 * @ignore
 */
var WorkerTransport = (function WorkerTransportClosure() {
  function WorkerTransport(workerInitializedPromise, workerReadyPromise,
      pdfDataRangeTransport, progressCallback) {
    this.pdfDataRangeTransport = pdfDataRangeTransport;

    this.workerReadyPromise = workerReadyPromise;
    this.progressCallback = progressCallback;
    this.commonObjs = new PDFObjects();

    this.pageCache = [];
    this.pagePromises = [];
    this.embeddedFontsUsed = false;
    this.downloadInfoPromise = new PDFJS.LegacyPromise();
    this.passwordCallback = null;

    // If worker support isn't disabled explicit and the browser has worker
    // support, create a new web worker and test if it/the browser fullfills
    // all requirements to run parts of pdf.js in a web worker.
    // Right now, the requirement is, that an Uint8Array is still an Uint8Array
    // as it arrives on the worker. Chrome added this with version 15.
//#if !SINGLE_FILE
    if (!globalScope.PDFJS.disableWorker && typeof Worker !== 'undefined') {
      var workerSrc = PDFJS.workerSrc;
      if (!workerSrc) {
        error('No PDFJS.workerSrc specified');
      }

      try {
        // Some versions of FF can't create a worker on localhost, see:
        // https://bugzilla.mozilla.org/show_bug.cgi?id=683280
        var worker = new Worker(workerSrc);
        var messageHandler = new MessageHandler('main', worker);
        this.messageHandler = messageHandler;

        messageHandler.on('test', function transportTest(data) {
          var supportTypedArray = data && data.supportTypedArray;
          if (supportTypedArray) {
            this.worker = worker;
            if (!data.supportTransfers) {
              PDFJS.postMessageTransfers = false;
            }
            this.setupMessageHandler(messageHandler);
            workerInitializedPromise.resolve();
          } else {
            globalScope.PDFJS.disableWorker = true;
            this.loadFakeWorkerFiles().then(function() {
              this.setupFakeWorker();
              workerInitializedPromise.resolve();
            }.bind(this));
          }
        }.bind(this));

        var testObj = new Uint8Array([PDFJS.postMessageTransfers ? 255 : 0]);
        // Some versions of Opera throw a DATA_CLONE_ERR on serializing the
        // typed array. Also, checking if we can use transfers.
        try {
          messageHandler.send('test', testObj, null, [testObj.buffer]);
        } catch (ex) {
          info('Cannot use postMessage transfers');
          testObj[0] = 0;
          messageHandler.send('test', testObj);
        }
        return;
      } catch (e) {
        info('The worker has been disabled.');
      }
    }
//#endif    
    // Either workers are disabled, not supported or have thrown an exception.
    // Thus, we fallback to a faked worker.
    globalScope.PDFJS.disableWorker = true;
    this.loadFakeWorkerFiles().then(function() {
      this.setupFakeWorker();
      workerInitializedPromise.resolve();
    }.bind(this));
  }
  WorkerTransport.prototype = {
    destroy: function WorkerTransport_destroy() {
      this.pageCache = [];
      this.pagePromises = [];
      var self = this;
      this.messageHandler.send('Terminate', null, function () {
        FontLoader.clear();
        if (self.worker) {
          self.worker.terminate();
        }
      });
    },

    loadFakeWorkerFiles: function WorkerTransport_loadFakeWorkerFiles() {
      if (!PDFJS.fakeWorkerFilesLoadedPromise) {
        PDFJS.fakeWorkerFilesLoadedPromise = new LegacyPromise();
        // In the developer build load worker_loader which in turn loads all the
        // other files and resolves the promise. In production only the
        // pdf.worker.js file is needed.
//#if !PRODUCTION
        Util.loadScript(PDFJS.workerSrc);
//#endif
//#if PRODUCTION && SINGLE_FILE
//      PDFJS.fakeWorkerFilesLoadedPromise.resolve();
//#endif
//#if PRODUCTION && !SINGLE_FILE
//      Util.loadScript(PDFJS.workerSrc, function() {
//        PDFJS.fakeWorkerFilesLoadedPromise.resolve();
//      });
//#endif
      }
      return PDFJS.fakeWorkerFilesLoadedPromise;
    },

    setupFakeWorker: function WorkerTransport_setupFakeWorker() {
      warn('Setting up fake worker.');
      // If we don't use a worker, just post/sendMessage to the main thread.
      var fakeWorker = {
        postMessage: function WorkerTransport_postMessage(obj) {
          fakeWorker.onmessage({data: obj});
        },
        terminate: function WorkerTransport_terminate() {}
      };

      var messageHandler = new MessageHandler('main', fakeWorker);
      this.setupMessageHandler(messageHandler);

      // If the main thread is our worker, setup the handling for the messages
      // the main thread sends to it self.
      PDFJS.WorkerMessageHandler.setup(messageHandler);
    },

    setupMessageHandler:
      function WorkerTransport_setupMessageHandler(messageHandler) {
      this.messageHandler = messageHandler;

      function updatePassword(password) {
        messageHandler.send('UpdatePassword', password);
      }

      var pdfDataRangeTransport = this.pdfDataRangeTransport;
      if (pdfDataRangeTransport) {
        pdfDataRangeTransport.addRangeListener(function(begin, chunk) {
          messageHandler.send('OnDataRange', {
            begin: begin,
            chunk: chunk
          });
        });

        pdfDataRangeTransport.addProgressListener(function(loaded) {
          messageHandler.send('OnDataProgress', {
            loaded: loaded
          });
        });

        messageHandler.on('RequestDataRange',
          function transportDataRange(data) {
            pdfDataRangeTransport.requestDataRange(data.begin, data.end);
          }, this);
      }

      messageHandler.on('GetDoc', function transportDoc(data) {
        var pdfInfo = data.pdfInfo;
        var pdfDocument = new PDFDocumentProxy(pdfInfo, this);
        this.pdfDocument = pdfDocument;
        this.workerReadyPromise.resolve(pdfDocument);
      }, this);

      messageHandler.on('NeedPassword', function transportPassword(data) {
        if (this.passwordCallback) {
          return this.passwordCallback(updatePassword,
                                       PasswordResponses.NEED_PASSWORD);
        }
        this.workerReadyPromise.reject(data.exception.message, data.exception);
      }, this);

      messageHandler.on('IncorrectPassword', function transportBadPass(data) {
        if (this.passwordCallback) {
          return this.passwordCallback(updatePassword,
                                       PasswordResponses.INCORRECT_PASSWORD);
        }
        this.workerReadyPromise.reject(data.exception.message, data.exception);
      }, this);

      messageHandler.on('InvalidPDF', function transportInvalidPDF(data) {
        this.workerReadyPromise.reject(data.exception.name, data.exception);
      }, this);

      messageHandler.on('MissingPDF', function transportMissingPDF(data) {
        this.workerReadyPromise.reject(data.exception.message, data.exception);
      }, this);

      messageHandler.on('UnknownError', function transportUnknownError(data) {
        this.workerReadyPromise.reject(data.exception.message, data.exception);
      }, this);

      messageHandler.on('DataLoaded', function transportPage(data) {
        this.downloadInfoPromise.resolve(data);
      }, this);

      messageHandler.on('GetPage', function transportPage(data) {
        var pageInfo = data.pageInfo;
        var page = new PDFPageProxy(pageInfo, this);
        this.pageCache[pageInfo.pageIndex] = page;
        var promise = this.pagePromises[pageInfo.pageIndex];
        promise.resolve(page);
      }, this);

      messageHandler.on('GetAnnotations', function transportAnnotations(data) {
        var annotations = data.annotations;
        var promise = this.pageCache[data.pageIndex].annotationsPromise;
        promise.resolve(annotations);
      }, this);

      messageHandler.on('StartRenderPage', function transportRender(data) {
        var page = this.pageCache[data.pageIndex];

        page.stats.timeEnd('Page Request');
        page._startRenderPage(data.transparency);
      }, this);

      messageHandler.on('RenderPageChunk', function transportRender(data) {
        var page = this.pageCache[data.pageIndex];

        page._renderPageChunk(data.operatorList);
      }, this);

      messageHandler.on('commonobj', function transportObj(data) {
        var id = data[0];
        var type = data[1];
        if (this.commonObjs.hasData(id))
          return;

        switch (type) {
          case 'Font':
            var exportedData = data[2];

            var font;
            if ('error' in exportedData) {
              var error = exportedData.error;
              warn('Error during font loading: ' + error);
              this.commonObjs.resolve(id, error);
              break;
            } else {
              font = new FontFace(exportedData);
            }

            FontLoader.bind(
              [font],
              function fontReady(fontObjs) {
                this.commonObjs.resolve(id, font);
              }.bind(this)
            );
            break;
          case 'FontPath':
            this.commonObjs.resolve(id, data[2]);
            break;
          default:
            error('Got unknown common object type ' + type);
        }
      }, this);

      messageHandler.on('obj', function transportObj(data) {
        var id = data[0];
        var pageIndex = data[1];
        var type = data[2];
        var pageProxy = this.pageCache[pageIndex];
        if (pageProxy.objs.hasData(id))
          return;

        switch (type) {
          case 'JpegStream':
            var imageData = data[3];
            loadJpegStream(id, imageData, pageProxy.objs);
            break;
          case 'Image':
            var imageData = data[3];
            pageProxy.objs.resolve(id, imageData);

            // heuristics that will allow not to store large data
            var MAX_IMAGE_SIZE_TO_STORE = 8000000;
            if ('data' in imageData &&
                imageData.data.length > MAX_IMAGE_SIZE_TO_STORE) {
              pageProxy.cleanupAfterRender = true;
            }
            break;
          default:
            error('Got unknown object type ' + type);
        }
      }, this);

      messageHandler.on('DocProgress', function transportDocProgress(data) {
        if (this.progressCallback) {
          this.progressCallback({
            loaded: data.loaded,
            total: data.total
          });
        }
      }, this);

      messageHandler.on('DocError', function transportDocError(data) {
        this.workerReadyPromise.reject(data);
      }, this);

      messageHandler.on('PageError', function transportError(data) {
        var page = this.pageCache[data.pageNum - 1];
        if (page.displayReadyPromise)
          page.displayReadyPromise.reject(data.error);
        else
          error(data.error);
      }, this);

      messageHandler.on('JpegDecode', function(data, deferred) {
        var imageUrl = data[0];
        var components = data[1];
        if (components != 3 && components != 1)
          error('Only 3 component or 1 component can be returned');

        var img = new Image();
        img.onload = (function messageHandler_onloadClosure() {
          var width = img.width;
          var height = img.height;
          var size = width * height;
          var rgbaLength = size * 4;
          var buf = new Uint8Array(size * components);
          var tmpCanvas = createScratchCanvas(width, height);
          var tmpCtx = tmpCanvas.getContext('2d');
          tmpCtx.drawImage(img, 0, 0);
          var data = tmpCtx.getImageData(0, 0, width, height).data;

          if (components == 3) {
            for (var i = 0, j = 0; i < rgbaLength; i += 4, j += 3) {
              buf[j] = data[i];
              buf[j + 1] = data[i + 1];
              buf[j + 2] = data[i + 2];
            }
          } else if (components == 1) {
            for (var i = 0, j = 0; i < rgbaLength; i += 4, j++) {
              buf[j] = data[i];
            }
          }
          deferred.resolve({ data: buf, width: width, height: height});
        }).bind(this);
        img.src = imageUrl;
      });
    },

    fetchDocument: function WorkerTransport_fetchDocument(source) {
      source.disableAutoFetch = PDFJS.disableAutoFetch;
      source.chunkedViewerLoading = !!this.pdfDataRangeTransport;
      this.messageHandler.send('GetDocRequest', {
        source: source,
        disableRange: PDFJS.disableRange,
        maxImageSize: PDFJS.maxImageSize,
        disableFontFace: PDFJS.disableFontFace,
        disableCreateObjectURL: PDFJS.disableCreateObjectURL,
        verbosity: PDFJS.verbosity
      });
    },

    getData: function WorkerTransport_getData(promise) {
      this.messageHandler.send('GetData', null, function(data) {
        promise.resolve(data);
      });
    },

    getPage: function WorkerTransport_getPage(pageNumber, promise) {
      var pageIndex = pageNumber - 1;
      if (pageIndex in this.pagePromises)
        return this.pagePromises[pageIndex];
      var promise = new PDFJS.LegacyPromise();
      this.pagePromises[pageIndex] = promise;
      this.messageHandler.send('GetPageRequest', { pageIndex: pageIndex });
      return promise;
    },

    getPageIndex: function WorkerTransport_getPageIndexByRef(ref) {
      var promise = new PDFJS.LegacyPromise();
      this.messageHandler.send('GetPageIndex', { ref: ref },
        function (pageIndex) {
          promise.resolve(pageIndex);
        }
      );
      return promise;
    },

    getAnnotations: function WorkerTransport_getAnnotations(pageIndex) {
      this.messageHandler.send('GetAnnotationsRequest',
        { pageIndex: pageIndex });
    },

    getDestinations: function WorkerTransport_getDestinations() {
      var promise = new PDFJS.LegacyPromise();
      this.messageHandler.send('GetDestinations', null,
        function transportDestinations(destinations) {
          promise.resolve(destinations);
        }
      );
      return promise;
    },

    startCleanup: function WorkerTransport_startCleanup() {
      this.messageHandler.send('Cleanup', null,
        function endCleanup() {
          for (var i = 0, ii = this.pageCache.length; i < ii; i++) {
            var page = this.pageCache[i];
            if (page) {
              page.destroy();
            }
          }
          this.commonObjs.clear();
          FontLoader.clear();
        }.bind(this)
      );
    }
  };
  return WorkerTransport;

})();

/**
 * A PDF document and page is built of many objects. E.g. there are objects
 * for fonts, images, rendering code and such. These objects might get processed
 * inside of a worker. The `PDFObjects` implements some basic functions to
 * manage these objects.
 * @ignore
 */
var PDFObjects = (function PDFObjectsClosure() {
  function PDFObjects() {
    this.objs = {};
  }

  PDFObjects.prototype = {
    /**
     * Internal function.
     * Ensures there is an object defined for `objId`.
     */
    ensureObj: function PDFObjects_ensureObj(objId) {
      if (this.objs[objId])
        return this.objs[objId];

      var obj = {
        promise: new LegacyPromise(),
        data: null,
        resolved: false
      };
      this.objs[objId] = obj;

      return obj;
    },

    /**
     * If called *without* callback, this returns the data of `objId` but the
     * object needs to be resolved. If it isn't, this function throws.
     *
     * If called *with* a callback, the callback is called with the data of the
     * object once the object is resolved. That means, if you call this
     * function and the object is already resolved, the callback gets called
     * right away.
     */
    get: function PDFObjects_get(objId, callback) {
      // If there is a callback, then the get can be async and the object is
      // not required to be resolved right now
      if (callback) {
        this.ensureObj(objId).promise.then(callback);
        return null;
      }

      // If there isn't a callback, the user expects to get the resolved data
      // directly.
      var obj = this.objs[objId];

      // If there isn't an object yet or the object isn't resolved, then the
      // data isn't ready yet!
      if (!obj || !obj.resolved)
        error('Requesting object that isn\'t resolved yet ' + objId);

      return obj.data;
    },

    /**
     * Resolves the object `objId` with optional `data`.
     */
    resolve: function PDFObjects_resolve(objId, data) {
      var obj = this.ensureObj(objId);

      obj.resolved = true;
      obj.data = data;
      obj.promise.resolve(data);
    },

    isResolved: function PDFObjects_isResolved(objId) {
      var objs = this.objs;

      if (!objs[objId]) {
        return false;
      } else {
        return objs[objId].resolved;
      }
    },

    hasData: function PDFObjects_hasData(objId) {
      return this.isResolved(objId);
    },

    /**
     * Returns the data of `objId` if object exists, null otherwise.
     */
    getData: function PDFObjects_getData(objId) {
      var objs = this.objs;
      if (!objs[objId] || !objs[objId].resolved) {
        return null;
      } else {
        return objs[objId].data;
      }
    },

    clear: function PDFObjects_clear() {
      this.objs = {};
    }
  };
  return PDFObjects;
})();

/**
 * Allows controlling of the rendering tasks.
 * @class
 */
var RenderTask = (function RenderTaskClosure() {
  function RenderTask(internalRenderTask) {
    this.internalRenderTask = internalRenderTask;
    /**
     * Promise for rendering task completion.
     * @type {Promise}
     */
    this.promise = new PDFJS.LegacyPromise();
  }

  RenderTask.prototype = /** @lends RenderTask.prototype */ {
    /**
     * Cancels the rendering task. If the task is currently rendering it will
     * not be cancelled until graphics pauses with a timeout. The promise that
     * this object extends will resolved when cancelled.
     */
    cancel: function RenderTask_cancel() {
      this.internalRenderTask.cancel();
      this.promise.reject(new Error('Rendering is cancelled'));
    }
  };

  return RenderTask;
})();

/**
 * For internal use only.
 * @ignore
 */
var InternalRenderTask = (function InternalRenderTaskClosure() {

  function InternalRenderTask(callback, params, objs, commonObjs, operatorList,
                              pageNumber) {
    this.callback = callback;
    this.params = params;
    this.objs = objs;
    this.commonObjs = commonObjs;
    this.operatorListIdx = null;
    this.operatorList = operatorList;
    this.pageNumber = pageNumber;
    this.running = false;
    this.graphicsReadyCallback = null;
    this.graphicsReady = false;
    this.cancelled = false;
  }

  InternalRenderTask.prototype = {

    initalizeGraphics:
        function InternalRenderTask_initalizeGraphics(transparency) {

      if (this.cancelled) {
        return;
      }
      if (PDFJS.pdfBug && 'StepperManager' in globalScope &&
          globalScope.StepperManager.enabled) {
        this.stepper = globalScope.StepperManager.create(this.pageNumber - 1);
        this.stepper.init(this.operatorList);
        this.stepper.nextBreakPoint = this.stepper.getNextBreakPoint();
      }

      var params = this.params;
      this.gfx = new CanvasGraphics(params.canvasContext, this.commonObjs,
                                    this.objs, params.textLayer,
                                    params.imageLayer);

      this.gfx.beginDrawing(params.viewport, transparency);
      this.operatorListIdx = 0;
      this.graphicsReady = true;
      if (this.graphicsReadyCallback) {
        this.graphicsReadyCallback();
      }
    },

    cancel: function InternalRenderTask_cancel() {
      this.running = false;
      this.cancelled = true;
      this.callback('cancelled');
    },

    operatorListChanged: function InternalRenderTask_operatorListChanged() {
      if (!this.graphicsReady) {
        if (!this.graphicsReadyCallback) {
          this.graphicsReadyCallback = this._continue.bind(this);
        }
        return;
      }

      if (this.stepper) {
        this.stepper.updateOperatorList(this.operatorList);
      }

      if (this.running) {
        return;
      }
      this._continue();
    },

    _continue: function InternalRenderTask__continue() {
      this.running = true;
      if (this.cancelled) {
        return;
      }
      if (this.params.continueCallback) {
        this.params.continueCallback(this._next.bind(this));
      } else {
        this._next();
      }
    },

    _next: function InternalRenderTask__next() {
      if (this.cancelled) {
        return;
      }
      this.operatorListIdx = this.gfx.executeOperatorList(this.operatorList,
                                        this.operatorListIdx,
                                        this._continue.bind(this),
                                        this.stepper);
      if (this.operatorListIdx === this.operatorList.argsArray.length) {
        this.running = false;
        if (this.operatorList.lastChunk) {
          this.gfx.endDrawing();
          this.callback();
        }
      }
    }

  };

  return InternalRenderTask;
})();
