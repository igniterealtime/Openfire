/*! Quill Editor v0.17.6
 *  https://quilljs.com/
 *  Copyright (c) 2014, Jason Chen
 *  Copyright (c) 2013, salesforce.com
 */
!function(e){if("object"==typeof exports&&"undefined"!=typeof module)module.exports=e();else if("function"==typeof define&&define.amd)define([],e);else{var f;"undefined"!=typeof window?f=window:"undefined"!=typeof global?f=global:"undefined"!=typeof self&&(f=self),f.Quill=e()}}(function(){var define,module,exports;return (function e(t,n,r){function s(o,u){if(!n[o]){if(!t[o]){var a=typeof require=="function"&&require;if(!u&&a)return a(o,!0);if(i)return i(o,!0);throw new Error("Cannot find module '"+o+"'")}var f=n[o]={exports:{}};t[o][0].call(f.exports,function(e){var n=t[o][1][e];return s(n?n:e)},f,f.exports,e,t,n,r)}return n[o].exports}var i=typeof require=="function"&&require;for(var o=0;o<r.length;o++)s(r[o]);return s})({"M4+//f":[function(_dereq_,module,exports){
(function (global){
/**
 * @license
 * Lo-Dash 2.4.1 (Custom Build) <http://lodash.com/>
 * Build: `lodash modern include="difference,flatten,intersection,last,all,each,indexOf,invoke,map,pluck,reduce,bind,defer,partial,clone,defaults,has,keys,omit,values,isArray,isElement,isEqual,isNumber,isObject,isString,uniqueId" --debug --output .build/lodash.js`
 * Copyright 2012-2013 The Dojo Foundation <http://dojofoundation.org/>
 * Based on Underscore.js 1.5.2 <http://underscorejs.org/LICENSE>
 * Copyright 2009-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & Editors
 * Available under MIT license <http://lodash.com/license>
 */
;(function() {

  /** Used as a safe reference for `undefined` in pre ES5 environments */
  var undefined;

  /** Used to pool arrays and objects used internally */
  var arrayPool = [],
      objectPool = [];

  /** Used to generate unique IDs */
  var idCounter = 0;

  /** Used to prefix keys to avoid issues with `__proto__` and properties on `Object.prototype` */
  var keyPrefix = +new Date + '';

  /** Used as the size when optimizations are enabled for large arrays */
  var largeArraySize = 75;

  /** Used as the max size of the `arrayPool` and `objectPool` */
  var maxPoolSize = 40;

  /** Used to match regexp flags from their coerced string values */
  var reFlags = /\w*$/;

  /** Used to detected named functions */
  var reFuncName = /^\s*function[ \n\r\t]+\w/;

  /** Used to detect functions containing a `this` reference */
  var reThis = /\bthis\b/;

  /** `Object#toString` result shortcuts */
  var argsClass = '[object Arguments]',
      arrayClass = '[object Array]',
      boolClass = '[object Boolean]',
      dateClass = '[object Date]',
      funcClass = '[object Function]',
      numberClass = '[object Number]',
      objectClass = '[object Object]',
      regexpClass = '[object RegExp]',
      stringClass = '[object String]';

  /** Used to identify object classifications that `_.clone` supports */
  var cloneableClasses = {};
  cloneableClasses[funcClass] = false;
  cloneableClasses[argsClass] = cloneableClasses[arrayClass] =
  cloneableClasses[boolClass] = cloneableClasses[dateClass] =
  cloneableClasses[numberClass] = cloneableClasses[objectClass] =
  cloneableClasses[regexpClass] = cloneableClasses[stringClass] = true;

  /** Used as the property descriptor for `__bindData__` */
  var descriptor = {
    'configurable': false,
    'enumerable': false,
    'value': null,
    'writable': false
  };

  /** Used to determine if values are of the language type Object */
  var objectTypes = {
    'boolean': false,
    'function': true,
    'object': true,
    'number': false,
    'string': false,
    'undefined': false
  };

  /** Used as a reference to the global object */
  var root = (objectTypes[typeof window] && window) || this;

  /** Detect free variable `exports` */
  var freeExports = objectTypes[typeof exports] && exports && !exports.nodeType && exports;

  /** Detect free variable `module` */
  var freeModule = objectTypes[typeof module] && module && !module.nodeType && module;

  /** Detect the popular CommonJS extension `module.exports` */
  var moduleExports = freeModule && freeModule.exports === freeExports && freeExports;

  /** Detect free variable `global` from Node.js or Browserified code and use it as `root` */
  var freeGlobal = objectTypes[typeof global] && global;
  if (freeGlobal && (freeGlobal.global === freeGlobal || freeGlobal.window === freeGlobal)) {
    root = freeGlobal;
  }

  /*--------------------------------------------------------------------------*/

  /**
   * The base implementation of `_.indexOf` without support for binary searches
   * or `fromIndex` constraints.
   *
   * @private
   * @param {Array} array The array to search.
   * @param {*} value The value to search for.
   * @param {number} [fromIndex=0] The index to search from.
   * @returns {number} Returns the index of the matched value or `-1`.
   */
  function baseIndexOf(array, value, fromIndex) {
    var index = (fromIndex || 0) - 1,
        length = array ? array.length : 0;

    while (++index < length) {
      if (array[index] === value) {
        return index;
      }
    }
    return -1;
  }

  /**
   * An implementation of `_.contains` for cache objects that mimics the return
   * signature of `_.indexOf` by returning `0` if the value is found, else `-1`.
   *
   * @private
   * @param {Object} cache The cache object to inspect.
   * @param {*} value The value to search for.
   * @returns {number} Returns `0` if `value` is found, else `-1`.
   */
  function cacheIndexOf(cache, value) {
    var type = typeof value;
    cache = cache.cache;

    if (type == 'boolean' || value == null) {
      return cache[value] ? 0 : -1;
    }
    if (type != 'number' && type != 'string') {
      type = 'object';
    }
    var key = type == 'number' ? value : keyPrefix + value;
    cache = (cache = cache[type]) && cache[key];

    return type == 'object'
      ? (cache && baseIndexOf(cache, value) > -1 ? 0 : -1)
      : (cache ? 0 : -1);
  }

  /**
   * Adds a given value to the corresponding cache object.
   *
   * @private
   * @param {*} value The value to add to the cache.
   */
  function cachePush(value) {
    var cache = this.cache,
        type = typeof value;

    if (type == 'boolean' || value == null) {
      cache[value] = true;
    } else {
      if (type != 'number' && type != 'string') {
        type = 'object';
      }
      var key = type == 'number' ? value : keyPrefix + value,
          typeCache = cache[type] || (cache[type] = {});

      if (type == 'object') {
        (typeCache[key] || (typeCache[key] = [])).push(value);
      } else {
        typeCache[key] = true;
      }
    }
  }

  /**
   * Creates a cache object to optimize linear searches of large arrays.
   *
   * @private
   * @param {Array} [array=[]] The array to search.
   * @returns {null|Object} Returns the cache object or `null` if caching should not be used.
   */
  function createCache(array) {
    var index = -1,
        length = array.length,
        first = array[0],
        mid = array[(length / 2) | 0],
        last = array[length - 1];

    if (first && typeof first == 'object' &&
        mid && typeof mid == 'object' && last && typeof last == 'object') {
      return false;
    }
    var cache = getObject();
    cache['false'] = cache['null'] = cache['true'] = cache['undefined'] = false;

    var result = getObject();
    result.array = array;
    result.cache = cache;
    result.push = cachePush;

    while (++index < length) {
      result.push(array[index]);
    }
    return result;
  }

  /**
   * Gets an array from the array pool or creates a new one if the pool is empty.
   *
   * @private
   * @returns {Array} The array from the pool.
   */
  function getArray() {
    return arrayPool.pop() || [];
  }

  /**
   * Gets an object from the object pool or creates a new one if the pool is empty.
   *
   * @private
   * @returns {Object} The object from the pool.
   */
  function getObject() {
    return objectPool.pop() || {
      'array': null,
      'cache': null,
      'false': false,
      'null': false,
      'number': null,
      'object': null,
      'push': null,
      'string': null,
      'true': false,
      'undefined': false
    };
  }

  /**
   * Releases the given array back to the array pool.
   *
   * @private
   * @param {Array} [array] The array to release.
   */
  function releaseArray(array) {
    array.length = 0;
    if (arrayPool.length < maxPoolSize) {
      arrayPool.push(array);
    }
  }

  /**
   * Releases the given object back to the object pool.
   *
   * @private
   * @param {Object} [object] The object to release.
   */
  function releaseObject(object) {
    var cache = object.cache;
    if (cache) {
      releaseObject(cache);
    }
    object.array = object.cache =object.object = object.number = object.string =null;
    if (objectPool.length < maxPoolSize) {
      objectPool.push(object);
    }
  }

  /**
   * Slices the `collection` from the `start` index up to, but not including,
   * the `end` index.
   *
   * Note: This function is used instead of `Array#slice` to support node lists
   * in IE < 9 and to ensure dense arrays are returned.
   *
   * @private
   * @param {Array|Object|string} collection The collection to slice.
   * @param {number} start The start index.
   * @param {number} end The end index.
   * @returns {Array} Returns the new array.
   */
  function slice(array, start, end) {
    start || (start = 0);
    if (typeof end == 'undefined') {
      end = array ? array.length : 0;
    }
    var index = -1,
        length = end - start || 0,
        result = Array(length < 0 ? 0 : length);

    while (++index < length) {
      result[index] = array[start + index];
    }
    return result;
  }

  /*--------------------------------------------------------------------------*/

  /**
   * Used for `Array` method references.
   *
   * Normally `Array.prototype` would suffice, however, using an array literal
   * avoids issues in Narwhal.
   */
  var arrayRef = [];

  /** Used for native method references */
  var objectProto = Object.prototype;

  /** Used to resolve the internal [[Class]] of values */
  var toString = objectProto.toString;

  /** Used to detect if a method is native */
  var reNative = RegExp('^' +
    String(toString)
      .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      .replace(/toString| for [^\]]+/g, '.*?') + '$'
  );

  /** Native method shortcuts */
  var fnToString = Function.prototype.toString,
      hasOwnProperty = objectProto.hasOwnProperty,
      push = arrayRef.push,
      unshift = arrayRef.unshift;

  /** Used to set meta data on functions */
  var defineProperty = (function() {
    // IE 8 only accepts DOM elements
    try {
      var o = {},
          func = isNative(func = Object.defineProperty) && func,
          result = func(o, o, o) && func;
    } catch(e) { }
    return result;
  }());

  /* Native method shortcuts for methods with the same name as other `lodash` methods */
  var nativeCreate = isNative(nativeCreate = Object.create) && nativeCreate,
      nativeIsArray = isNative(nativeIsArray = Array.isArray) && nativeIsArray,
      nativeKeys = isNative(nativeKeys = Object.keys) && nativeKeys,
      nativeMax = Math.max;

  /** Used to lookup a built-in constructor by [[Class]] */
  var ctorByClass = {};
  ctorByClass[arrayClass] = Array;
  ctorByClass[boolClass] = Boolean;
  ctorByClass[dateClass] = Date;
  ctorByClass[funcClass] = Function;
  ctorByClass[objectClass] = Object;
  ctorByClass[numberClass] = Number;
  ctorByClass[regexpClass] = RegExp;
  ctorByClass[stringClass] = String;

  /*--------------------------------------------------------------------------*/

  /**
   * Creates a `lodash` object which wraps the given value to enable intuitive
   * method chaining.
   *
   * In addition to Lo-Dash methods, wrappers also have the following `Array` methods:
   * `concat`, `join`, `pop`, `push`, `reverse`, `shift`, `slice`, `sort`, `splice`,
   * and `unshift`
   *
   * Chaining is supported in custom builds as long as the `value` method is
   * implicitly or explicitly included in the build.
   *
   * The chainable wrapper functions are:
   * `after`, `assign`, `bind`, `bindAll`, `bindKey`, `chain`, `compact`,
   * `compose`, `concat`, `countBy`, `create`, `createCallback`, `curry`,
   * `debounce`, `defaults`, `defer`, `delay`, `difference`, `filter`, `flatten`,
   * `forEach`, `forEachRight`, `forIn`, `forInRight`, `forOwn`, `forOwnRight`,
   * `functions`, `groupBy`, `indexBy`, `initial`, `intersection`, `invert`,
   * `invoke`, `keys`, `map`, `max`, `memoize`, `merge`, `min`, `object`, `omit`,
   * `once`, `pairs`, `partial`, `partialRight`, `pick`, `pluck`, `pull`, `push`,
   * `range`, `reject`, `remove`, `rest`, `reverse`, `shuffle`, `slice`, `sort`,
   * `sortBy`, `splice`, `tap`, `throttle`, `times`, `toArray`, `transform`,
   * `union`, `uniq`, `unshift`, `unzip`, `values`, `where`, `without`, `wrap`,
   * and `zip`
   *
   * The non-chainable wrapper functions are:
   * `clone`, `cloneDeep`, `contains`, `escape`, `every`, `find`, `findIndex`,
   * `findKey`, `findLast`, `findLastIndex`, `findLastKey`, `has`, `identity`,
   * `indexOf`, `isArguments`, `isArray`, `isBoolean`, `isDate`, `isElement`,
   * `isEmpty`, `isEqual`, `isFinite`, `isFunction`, `isNaN`, `isNull`, `isNumber`,
   * `isObject`, `isPlainObject`, `isRegExp`, `isString`, `isUndefined`, `join`,
   * `lastIndexOf`, `mixin`, `noConflict`, `parseInt`, `pop`, `random`, `reduce`,
   * `reduceRight`, `result`, `shift`, `size`, `some`, `sortedIndex`, `runInContext`,
   * `template`, `unescape`, `uniqueId`, and `value`
   *
   * The wrapper functions `first` and `last` return wrapped values when `n` is
   * provided, otherwise they return unwrapped values.
   *
   * Explicit chaining can be enabled by using the `_.chain` method.
   *
   * @name _
   * @constructor
   * @category Chaining
   * @param {*} value The value to wrap in a `lodash` instance.
   * @returns {Object} Returns a `lodash` instance.
   * @example
   *
   * var wrapped = _([1, 2, 3]);
   *
   * // returns an unwrapped value
   * wrapped.reduce(function(sum, num) {
   *   return sum + num;
   * });
   * // => 6
   *
   * // returns a wrapped value
   * var squares = wrapped.map(function(num) {
   *   return num * num;
   * });
   *
   * _.isArray(squares);
   * // => false
   *
   * _.isArray(squares.value());
   * // => true
   */
  function lodash() {
    // no operation performed
  }

  /**
   * An object used to flag environments features.
   *
   * @static
   * @memberOf _
   * @type Object
   */
  var support = lodash.support = {};

  /**
   * Detect if functions can be decompiled by `Function#toString`
   * (all but PS3 and older Opera mobile browsers & avoided in Windows 8 apps).
   *
   * @memberOf _.support
   * @type boolean
   */
  support.funcDecomp = !isNative(root.WinRTError) && reThis.test(function() { return this; });

  /**
   * Detect if `Function#name` is supported (all but IE).
   *
   * @memberOf _.support
   * @type boolean
   */
  support.funcNames = typeof Function.name == 'string';

  /*--------------------------------------------------------------------------*/

  /**
   * The base implementation of `_.bind` that creates the bound function and
   * sets its meta data.
   *
   * @private
   * @param {Array} bindData The bind data array.
   * @returns {Function} Returns the new bound function.
   */
  function baseBind(bindData) {
    var func = bindData[0],
        partialArgs = bindData[2],
        thisArg = bindData[4];

    function bound() {
      // `Function#bind` spec
      // http://es5.github.io/#x15.3.4.5
      if (partialArgs) {
        // avoid `arguments` object deoptimizations by using `slice` instead
        // of `Array.prototype.slice.call` and not assigning `arguments` to a
        // variable as a ternary expression
        var args = slice(partialArgs);
        push.apply(args, arguments);
      }
      // mimic the constructor's `return` behavior
      // http://es5.github.io/#x13.2.2
      if (this instanceof bound) {
        // ensure `new bound` is an instance of `func`
        var thisBinding = baseCreate(func.prototype),
            result = func.apply(thisBinding, args || arguments);
        return isObject(result) ? result : thisBinding;
      }
      return func.apply(thisArg, args || arguments);
    }
    setBindData(bound, bindData);
    return bound;
  }

  /**
   * The base implementation of `_.clone` without argument juggling or support
   * for `thisArg` binding.
   *
   * @private
   * @param {*} value The value to clone.
   * @param {boolean} [isDeep=false] Specify a deep clone.
   * @param {Function} [callback] The function to customize cloning values.
   * @param {Array} [stackA=[]] Tracks traversed source objects.
   * @param {Array} [stackB=[]] Associates clones with source counterparts.
   * @returns {*} Returns the cloned value.
   */
  function baseClone(value, isDeep, callback, stackA, stackB) {
    if (callback) {
      var result = callback(value);
      if (typeof result != 'undefined') {
        return result;
      }
    }
    // inspect [[Class]]
    var isObj = isObject(value);
    if (isObj) {
      var className = toString.call(value);
      if (!cloneableClasses[className]) {
        return value;
      }
      var ctor = ctorByClass[className];
      switch (className) {
        case boolClass:
        case dateClass:
          return new ctor(+value);

        case numberClass:
        case stringClass:
          return new ctor(value);

        case regexpClass:
          result = ctor(value.source, reFlags.exec(value));
          result.lastIndex = value.lastIndex;
          return result;
      }
    } else {
      return value;
    }
    var isArr = isArray(value);
    if (isDeep) {
      // check for circular references and return corresponding clone
      var initedStack = !stackA;
      stackA || (stackA = getArray());
      stackB || (stackB = getArray());

      var length = stackA.length;
      while (length--) {
        if (stackA[length] == value) {
          return stackB[length];
        }
      }
      result = isArr ? ctor(value.length) : {};
    }
    else {
      result = isArr ? slice(value) : assign({}, value);
    }
    // add array properties assigned by `RegExp#exec`
    if (isArr) {
      if (hasOwnProperty.call(value, 'index')) {
        result.index = value.index;
      }
      if (hasOwnProperty.call(value, 'input')) {
        result.input = value.input;
      }
    }
    // exit for shallow clone
    if (!isDeep) {
      return result;
    }
    // add the source value to the stack of traversed objects
    // and associate it with its clone
    stackA.push(value);
    stackB.push(result);

    // recursively populate clone (susceptible to call stack limits)
    (isArr ? forEach : forOwn)(value, function(objValue, key) {
      result[key] = baseClone(objValue, isDeep, callback, stackA, stackB);
    });

    if (initedStack) {
      releaseArray(stackA);
      releaseArray(stackB);
    }
    return result;
  }

  /**
   * The base implementation of `_.create` without support for assigning
   * properties to the created object.
   *
   * @private
   * @param {Object} prototype The object to inherit from.
   * @returns {Object} Returns the new object.
   */
  function baseCreate(prototype, properties) {
    return isObject(prototype) ? nativeCreate(prototype) : {};
  }
  // fallback for browsers without `Object.create`
  if (!nativeCreate) {
    baseCreate = (function() {
      function Object() {}
      return function(prototype) {
        if (isObject(prototype)) {
          Object.prototype = prototype;
          var result = new Object;
          Object.prototype = null;
        }
        return result || root.Object();
      };
    }());
  }

  /**
   * The base implementation of `_.createCallback` without support for creating
   * "_.pluck" or "_.where" style callbacks.
   *
   * @private
   * @param {*} [func=identity] The value to convert to a callback.
   * @param {*} [thisArg] The `this` binding of the created callback.
   * @param {number} [argCount] The number of arguments the callback accepts.
   * @returns {Function} Returns a callback function.
   */
  function baseCreateCallback(func, thisArg, argCount) {
    if (typeof func != 'function') {
      return identity;
    }
    // exit early for no `thisArg` or already bound by `Function#bind`
    if (typeof thisArg == 'undefined' || !('prototype' in func)) {
      return func;
    }
    var bindData = func.__bindData__;
    if (typeof bindData == 'undefined') {
      if (support.funcNames) {
        bindData = !func.name;
      }
      bindData = bindData || !support.funcDecomp;
      if (!bindData) {
        var source = fnToString.call(func);
        if (!support.funcNames) {
          bindData = !reFuncName.test(source);
        }
        if (!bindData) {
          // checks if `func` references the `this` keyword and stores the result
          bindData = reThis.test(source);
          setBindData(func, bindData);
        }
      }
    }
    // exit early if there are no `this` references or `func` is bound
    if (bindData === false || (bindData !== true && bindData[1] & 1)) {
      return func;
    }
    switch (argCount) {
      case 1: return function(value) {
        return func.call(thisArg, value);
      };
      case 2: return function(a, b) {
        return func.call(thisArg, a, b);
      };
      case 3: return function(value, index, collection) {
        return func.call(thisArg, value, index, collection);
      };
      case 4: return function(accumulator, value, index, collection) {
        return func.call(thisArg, accumulator, value, index, collection);
      };
    }
    return bind(func, thisArg);
  }

  /**
   * The base implementation of `createWrapper` that creates the wrapper and
   * sets its meta data.
   *
   * @private
   * @param {Array} bindData The bind data array.
   * @returns {Function} Returns the new function.
   */
  function baseCreateWrapper(bindData) {
    var func = bindData[0],
        bitmask = bindData[1],
        partialArgs = bindData[2],
        partialRightArgs = bindData[3],
        thisArg = bindData[4],
        arity = bindData[5];

    var isBind = bitmask & 1,
        isBindKey = bitmask & 2,
        isCurry = bitmask & 4,
        isCurryBound = bitmask & 8,
        key = func;

    function bound() {
      var thisBinding = isBind ? thisArg : this;
      if (partialArgs) {
        var args = slice(partialArgs);
        push.apply(args, arguments);
      }
      if (partialRightArgs || isCurry) {
        args || (args = slice(arguments));
        if (partialRightArgs) {
          push.apply(args, partialRightArgs);
        }
        if (isCurry && args.length < arity) {
          bitmask |= 16 & ~32;
          return baseCreateWrapper([func, (isCurryBound ? bitmask : bitmask & ~3), args, null, thisArg, arity]);
        }
      }
      args || (args = arguments);
      if (isBindKey) {
        func = thisBinding[key];
      }
      if (this instanceof bound) {
        thisBinding = baseCreate(func.prototype);
        var result = func.apply(thisBinding, args);
        return isObject(result) ? result : thisBinding;
      }
      return func.apply(thisBinding, args);
    }
    setBindData(bound, bindData);
    return bound;
  }

  /**
   * The base implementation of `_.difference` that accepts a single array
   * of values to exclude.
   *
   * @private
   * @param {Array} array The array to process.
   * @param {Array} [values] The array of values to exclude.
   * @returns {Array} Returns a new array of filtered values.
   */
  function baseDifference(array, values) {
    var index = -1,
        indexOf = getIndexOf(),
        length = array ? array.length : 0,
        isLarge = length >= largeArraySize && indexOf === baseIndexOf,
        result = [];

    if (isLarge) {
      var cache = createCache(values);
      if (cache) {
        indexOf = cacheIndexOf;
        values = cache;
      } else {
        isLarge = false;
      }
    }
    while (++index < length) {
      var value = array[index];
      if (indexOf(values, value) < 0) {
        result.push(value);
      }
    }
    if (isLarge) {
      releaseObject(values);
    }
    return result;
  }

  /**
   * The base implementation of `_.flatten` without support for callback
   * shorthands or `thisArg` binding.
   *
   * @private
   * @param {Array} array The array to flatten.
   * @param {boolean} [isShallow=false] A flag to restrict flattening to a single level.
   * @param {boolean} [isStrict=false] A flag to restrict flattening to arrays and `arguments` objects.
   * @param {number} [fromIndex=0] The index to start from.
   * @returns {Array} Returns a new flattened array.
   */
  function baseFlatten(array, isShallow, isStrict, fromIndex) {
    var index = (fromIndex || 0) - 1,
        length = array ? array.length : 0,
        result = [];

    while (++index < length) {
      var value = array[index];

      if (value && typeof value == 'object' && typeof value.length == 'number'
          && (isArray(value) || isArguments(value))) {
        // recursively flatten arrays (susceptible to call stack limits)
        if (!isShallow) {
          value = baseFlatten(value, isShallow, isStrict);
        }
        var valIndex = -1,
            valLength = value.length,
            resIndex = result.length;

        result.length += valLength;
        while (++valIndex < valLength) {
          result[resIndex++] = value[valIndex];
        }
      } else if (!isStrict) {
        result.push(value);
      }
    }
    return result;
  }

  /**
   * The base implementation of `_.isEqual`, without support for `thisArg` binding,
   * that allows partial "_.where" style comparisons.
   *
   * @private
   * @param {*} a The value to compare.
   * @param {*} b The other value to compare.
   * @param {Function} [callback] The function to customize comparing values.
   * @param {Function} [isWhere=false] A flag to indicate performing partial comparisons.
   * @param {Array} [stackA=[]] Tracks traversed `a` objects.
   * @param {Array} [stackB=[]] Tracks traversed `b` objects.
   * @returns {boolean} Returns `true` if the values are equivalent, else `false`.
   */
  function baseIsEqual(a, b, callback, isWhere, stackA, stackB) {
    // used to indicate that when comparing objects, `a` has at least the properties of `b`
    if (callback) {
      var result = callback(a, b);
      if (typeof result != 'undefined') {
        return !!result;
      }
    }
    // exit early for identical values
    if (a === b) {
      // treat `+0` vs. `-0` as not equal
      return a !== 0 || (1 / a == 1 / b);
    }
    var type = typeof a,
        otherType = typeof b;

    // exit early for unlike primitive values
    if (a === a &&
        !(a && objectTypes[type]) &&
        !(b && objectTypes[otherType])) {
      return false;
    }
    // exit early for `null` and `undefined` avoiding ES3's Function#call behavior
    // http://es5.github.io/#x15.3.4.4
    if (a == null || b == null) {
      return a === b;
    }
    // compare [[Class]] names
    var className = toString.call(a),
        otherClass = toString.call(b);

    if (className == argsClass) {
      className = objectClass;
    }
    if (otherClass == argsClass) {
      otherClass = objectClass;
    }
    if (className != otherClass) {
      return false;
    }
    switch (className) {
      case boolClass:
      case dateClass:
        // coerce dates and booleans to numbers, dates to milliseconds and booleans
        // to `1` or `0` treating invalid dates coerced to `NaN` as not equal
        return +a == +b;

      case numberClass:
        // treat `NaN` vs. `NaN` as equal
        return (a != +a)
          ? b != +b
          // but treat `+0` vs. `-0` as not equal
          : (a == 0 ? (1 / a == 1 / b) : a == +b);

      case regexpClass:
      case stringClass:
        // coerce regexes to strings (http://es5.github.io/#x15.10.6.4)
        // treat string primitives and their corresponding object instances as equal
        return a == String(b);
    }
    var isArr = className == arrayClass;
    if (!isArr) {
      // unwrap any `lodash` wrapped values
      var aWrapped = hasOwnProperty.call(a, '__wrapped__'),
          bWrapped = hasOwnProperty.call(b, '__wrapped__');

      if (aWrapped || bWrapped) {
        return baseIsEqual(aWrapped ? a.__wrapped__ : a, bWrapped ? b.__wrapped__ : b, callback, isWhere, stackA, stackB);
      }
      // exit for functions and DOM nodes
      if (className != objectClass) {
        return false;
      }
      // in older versions of Opera, `arguments` objects have `Array` constructors
      var ctorA = a.constructor,
          ctorB = b.constructor;

      // non `Object` object instances with different constructors are not equal
      if (ctorA != ctorB &&
            !(isFunction(ctorA) && ctorA instanceof ctorA && isFunction(ctorB) && ctorB instanceof ctorB) &&
            ('constructor' in a && 'constructor' in b)
          ) {
        return false;
      }
    }
    // assume cyclic structures are equal
    // the algorithm for detecting cyclic structures is adapted from ES 5.1
    // section 15.12.3, abstract operation `JO` (http://es5.github.io/#x15.12.3)
    var initedStack = !stackA;
    stackA || (stackA = getArray());
    stackB || (stackB = getArray());

    var length = stackA.length;
    while (length--) {
      if (stackA[length] == a) {
        return stackB[length] == b;
      }
    }
    var size = 0;
    result = true;

    // add `a` and `b` to the stack of traversed objects
    stackA.push(a);
    stackB.push(b);

    // recursively compare objects and arrays (susceptible to call stack limits)
    if (isArr) {
      // compare lengths to determine if a deep comparison is necessary
      length = a.length;
      size = b.length;
      result = size == length;

      if (result || isWhere) {
        // deep compare the contents, ignoring non-numeric properties
        while (size--) {
          var index = length,
              value = b[size];

          if (isWhere) {
            while (index--) {
              if ((result = baseIsEqual(a[index], value, callback, isWhere, stackA, stackB))) {
                break;
              }
            }
          } else if (!(result = baseIsEqual(a[size], value, callback, isWhere, stackA, stackB))) {
            break;
          }
        }
      }
    }
    else {
      // deep compare objects using `forIn`, instead of `forOwn`, to avoid `Object.keys`
      // which, in this case, is more costly
      forIn(b, function(value, key, b) {
        if (hasOwnProperty.call(b, key)) {
          // count the number of properties.
          size++;
          // deep compare each property value.
          return (result = hasOwnProperty.call(a, key) && baseIsEqual(a[key], value, callback, isWhere, stackA, stackB));
        }
      });

      if (result && !isWhere) {
        // ensure both objects have the same number of properties
        forIn(a, function(value, key, a) {
          if (hasOwnProperty.call(a, key)) {
            // `size` will be `-1` if `a` has more properties than `b`
            return (result = --size > -1);
          }
        });
      }
    }
    stackA.pop();
    stackB.pop();

    if (initedStack) {
      releaseArray(stackA);
      releaseArray(stackB);
    }
    return result;
  }

  /**
   * Creates a function that, when called, either curries or invokes `func`
   * with an optional `this` binding and partially applied arguments.
   *
   * @private
   * @param {Function|string} func The function or method name to reference.
   * @param {number} bitmask The bitmask of method flags to compose.
   *  The bitmask may be composed of the following flags:
   *  1 - `_.bind`
   *  2 - `_.bindKey`
   *  4 - `_.curry`
   *  8 - `_.curry` (bound)
   *  16 - `_.partial`
   *  32 - `_.partialRight`
   * @param {Array} [partialArgs] An array of arguments to prepend to those
   *  provided to the new function.
   * @param {Array} [partialRightArgs] An array of arguments to append to those
   *  provided to the new function.
   * @param {*} [thisArg] The `this` binding of `func`.
   * @param {number} [arity] The arity of `func`.
   * @returns {Function} Returns the new function.
   */
  function createWrapper(func, bitmask, partialArgs, partialRightArgs, thisArg, arity) {
    var isBind = bitmask & 1,
        isBindKey = bitmask & 2,
        isCurry = bitmask & 4,
        isCurryBound = bitmask & 8,
        isPartial = bitmask & 16,
        isPartialRight = bitmask & 32;

    if (!isBindKey && !isFunction(func)) {
      throw new TypeError;
    }
    if (isPartial && !partialArgs.length) {
      bitmask &= ~16;
      isPartial = partialArgs = false;
    }
    if (isPartialRight && !partialRightArgs.length) {
      bitmask &= ~32;
      isPartialRight = partialRightArgs = false;
    }
    var bindData = func && func.__bindData__;
    if (bindData && bindData !== true) {
      // clone `bindData`
      bindData = slice(bindData);
      if (bindData[2]) {
        bindData[2] = slice(bindData[2]);
      }
      if (bindData[3]) {
        bindData[3] = slice(bindData[3]);
      }
      // set `thisBinding` is not previously bound
      if (isBind && !(bindData[1] & 1)) {
        bindData[4] = thisArg;
      }
      // set if previously bound but not currently (subsequent curried functions)
      if (!isBind && bindData[1] & 1) {
        bitmask |= 8;
      }
      // set curried arity if not yet set
      if (isCurry && !(bindData[1] & 4)) {
        bindData[5] = arity;
      }
      // append partial left arguments
      if (isPartial) {
        push.apply(bindData[2] || (bindData[2] = []), partialArgs);
      }
      // append partial right arguments
      if (isPartialRight) {
        unshift.apply(bindData[3] || (bindData[3] = []), partialRightArgs);
      }
      // merge flags
      bindData[1] |= bitmask;
      return createWrapper.apply(null, bindData);
    }
    // fast path for `_.bind`
    var creater = (bitmask == 1 || bitmask === 17) ? baseBind : baseCreateWrapper;
    return creater([func, bitmask, partialArgs, partialRightArgs, thisArg, arity]);
  }

  /**
   * Gets the appropriate "indexOf" function. If the `_.indexOf` method is
   * customized, this method returns the custom method, otherwise it returns
   * the `baseIndexOf` function.
   *
   * @private
   * @returns {Function} Returns the "indexOf" function.
   */
  function getIndexOf() {
    var result = (result = lodash.indexOf) === indexOf ? baseIndexOf : result;
    return result;
  }

  /**
   * Checks if `value` is a native function.
   *
   * @private
   * @param {*} value The value to check.
   * @returns {boolean} Returns `true` if the `value` is a native function, else `false`.
   */
  function isNative(value) {
    return typeof value == 'function' && reNative.test(value);
  }

  /**
   * Sets `this` binding data on a given function.
   *
   * @private
   * @param {Function} func The function to set data on.
   * @param {Array} value The data array to set.
   */
  var setBindData = !defineProperty ? noop : function(func, value) {
    descriptor.value = value;
    defineProperty(func, '__bindData__', descriptor);
  };

  /*--------------------------------------------------------------------------*/

  /**
   * Checks if `value` is an `arguments` object.
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {*} value The value to check.
   * @returns {boolean} Returns `true` if the `value` is an `arguments` object, else `false`.
   * @example
   *
   * (function() { return _.isArguments(arguments); })(1, 2, 3);
   * // => true
   *
   * _.isArguments([1, 2, 3]);
   * // => false
   */
  function isArguments(value) {
    return value && typeof value == 'object' && typeof value.length == 'number' &&
      toString.call(value) == argsClass || false;
  }

  /**
   * Checks if `value` is an array.
   *
   * @static
   * @memberOf _
   * @type Function
   * @category Objects
   * @param {*} value The value to check.
   * @returns {boolean} Returns `true` if the `value` is an array, else `false`.
   * @example
   *
   * (function() { return _.isArray(arguments); })();
   * // => false
   *
   * _.isArray([1, 2, 3]);
   * // => true
   */
  var isArray = nativeIsArray || function(value) {
    return value && typeof value == 'object' && typeof value.length == 'number' &&
      toString.call(value) == arrayClass || false;
  };

  /**
   * A fallback implementation of `Object.keys` which produces an array of the
   * given object's own enumerable property names.
   *
   * @private
   * @type Function
   * @param {Object} object The object to inspect.
   * @returns {Array} Returns an array of property names.
   */
  var shimKeys = function(object) {
    var index, iterable = object, result = [];
    if (!iterable) return result;
    if (!(objectTypes[typeof object])) return result;
      for (index in iterable) {
        if (hasOwnProperty.call(iterable, index)) {
          result.push(index);
        }
      }
    return result
  };

  /**
   * Creates an array composed of the own enumerable property names of an object.
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {Object} object The object to inspect.
   * @returns {Array} Returns an array of property names.
   * @example
   *
   * _.keys({ 'one': 1, 'two': 2, 'three': 3 });
   * // => ['one', 'two', 'three'] (property order is not guaranteed across environments)
   */
  var keys = !nativeKeys ? shimKeys : function(object) {
    if (!isObject(object)) {
      return [];
    }
    return nativeKeys(object);
  };

  /*--------------------------------------------------------------------------*/

  /**
   * Assigns own enumerable properties of source object(s) to the destination
   * object. Subsequent sources will overwrite property assignments of previous
   * sources. If a callback is provided it will be executed to produce the
   * assigned values. The callback is bound to `thisArg` and invoked with two
   * arguments; (objectValue, sourceValue).
   *
   * @static
   * @memberOf _
   * @type Function
   * @alias extend
   * @category Objects
   * @param {Object} object The destination object.
   * @param {...Object} [source] The source objects.
   * @param {Function} [callback] The function to customize assigning values.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {Object} Returns the destination object.
   * @example
   *
   * _.assign({ 'name': 'fred' }, { 'employer': 'slate' });
   * // => { 'name': 'fred', 'employer': 'slate' }
   *
   * var defaults = _.partialRight(_.assign, function(a, b) {
   *   return typeof a == 'undefined' ? b : a;
   * });
   *
   * var object = { 'name': 'barney' };
   * defaults(object, { 'name': 'fred', 'employer': 'slate' });
   * // => { 'name': 'barney', 'employer': 'slate' }
   */
  var assign = function(object, source, guard) {
    var index, iterable = object, result = iterable;
    if (!iterable) return result;
    var args = arguments,
        argsIndex = 0,
        argsLength = typeof guard == 'number' ? 2 : args.length;
    if (argsLength > 3 && typeof args[argsLength - 2] == 'function') {
      var callback = baseCreateCallback(args[--argsLength - 1], args[argsLength--], 2);
    } else if (argsLength > 2 && typeof args[argsLength - 1] == 'function') {
      callback = args[--argsLength];
    }
    while (++argsIndex < argsLength) {
      iterable = args[argsIndex];
      if (iterable && objectTypes[typeof iterable]) {
      var ownIndex = -1,
          ownProps = objectTypes[typeof iterable] && keys(iterable),
          length = ownProps ? ownProps.length : 0;

      while (++ownIndex < length) {
        index = ownProps[ownIndex];
        result[index] = callback ? callback(result[index], iterable[index]) : iterable[index];
      }
      }
    }
    return result
  };

  /**
   * Creates a clone of `value`. If `isDeep` is `true` nested objects will also
   * be cloned, otherwise they will be assigned by reference. If a callback
   * is provided it will be executed to produce the cloned values. If the
   * callback returns `undefined` cloning will be handled by the method instead.
   * The callback is bound to `thisArg` and invoked with one argument; (value).
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {*} value The value to clone.
   * @param {boolean} [isDeep=false] Specify a deep clone.
   * @param {Function} [callback] The function to customize cloning values.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {*} Returns the cloned value.
   * @example
   *
   * var characters = [
   *   { 'name': 'barney', 'age': 36 },
   *   { 'name': 'fred',   'age': 40 }
   * ];
   *
   * var shallow = _.clone(characters);
   * shallow[0] === characters[0];
   * // => true
   *
   * var deep = _.clone(characters, true);
   * deep[0] === characters[0];
   * // => false
   *
   * _.mixin({
   *   'clone': _.partialRight(_.clone, function(value) {
   *     return _.isElement(value) ? value.cloneNode(false) : undefined;
   *   })
   * });
   *
   * var clone = _.clone(document.body);
   * clone.childNodes.length;
   * // => 0
   */
  function clone(value, isDeep, callback, thisArg) {
    // allows working with "Collections" methods without using their `index`
    // and `collection` arguments for `isDeep` and `callback`
    if (typeof isDeep != 'boolean' && isDeep != null) {
      thisArg = callback;
      callback = isDeep;
      isDeep = false;
    }
    return baseClone(value, isDeep, typeof callback == 'function' && baseCreateCallback(callback, thisArg, 1));
  }

  /**
   * Assigns own enumerable properties of source object(s) to the destination
   * object for all destination properties that resolve to `undefined`. Once a
   * property is set, additional defaults of the same property will be ignored.
   *
   * @static
   * @memberOf _
   * @type Function
   * @category Objects
   * @param {Object} object The destination object.
   * @param {...Object} [source] The source objects.
   * @param- {Object} [guard] Allows working with `_.reduce` without using its
   *  `key` and `object` arguments as sources.
   * @returns {Object} Returns the destination object.
   * @example
   *
   * var object = { 'name': 'barney' };
   * _.defaults(object, { 'name': 'fred', 'employer': 'slate' });
   * // => { 'name': 'barney', 'employer': 'slate' }
   */
  var defaults = function(object, source, guard) {
    var index, iterable = object, result = iterable;
    if (!iterable) return result;
    var args = arguments,
        argsIndex = 0,
        argsLength = typeof guard == 'number' ? 2 : args.length;
    while (++argsIndex < argsLength) {
      iterable = args[argsIndex];
      if (iterable && objectTypes[typeof iterable]) {
      var ownIndex = -1,
          ownProps = objectTypes[typeof iterable] && keys(iterable),
          length = ownProps ? ownProps.length : 0;

      while (++ownIndex < length) {
        index = ownProps[ownIndex];
        if (typeof result[index] == 'undefined') result[index] = iterable[index];
      }
      }
    }
    return result
  };

  /**
   * Iterates over own and inherited enumerable properties of an object,
   * executing the callback for each property. The callback is bound to `thisArg`
   * and invoked with three arguments; (value, key, object). Callbacks may exit
   * iteration early by explicitly returning `false`.
   *
   * @static
   * @memberOf _
   * @type Function
   * @category Objects
   * @param {Object} object The object to iterate over.
   * @param {Function} [callback=identity] The function called per iteration.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {Object} Returns `object`.
   * @example
   *
   * function Shape() {
   *   this.x = 0;
   *   this.y = 0;
   * }
   *
   * Shape.prototype.move = function(x, y) {
   *   this.x += x;
   *   this.y += y;
   * };
   *
   * _.forIn(new Shape, function(value, key) {
   *   console.log(key);
   * });
   * // => logs 'x', 'y', and 'move' (property order is not guaranteed across environments)
   */
  var forIn = function(collection, callback, thisArg) {
    var index, iterable = collection, result = iterable;
    if (!iterable) return result;
    if (!objectTypes[typeof iterable]) return result;
    callback = callback && typeof thisArg == 'undefined' ? callback : baseCreateCallback(callback, thisArg, 3);
      for (index in iterable) {
        if (callback(iterable[index], index, collection) === false) return result;
      }
    return result
  };

  /**
   * Iterates over own enumerable properties of an object, executing the callback
   * for each property. The callback is bound to `thisArg` and invoked with three
   * arguments; (value, key, object). Callbacks may exit iteration early by
   * explicitly returning `false`.
   *
   * @static
   * @memberOf _
   * @type Function
   * @category Objects
   * @param {Object} object The object to iterate over.
   * @param {Function} [callback=identity] The function called per iteration.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {Object} Returns `object`.
   * @example
   *
   * _.forOwn({ '0': 'zero', '1': 'one', 'length': 2 }, function(num, key) {
   *   console.log(key);
   * });
   * // => logs '0', '1', and 'length' (property order is not guaranteed across environments)
   */
  var forOwn = function(collection, callback, thisArg) {
    var index, iterable = collection, result = iterable;
    if (!iterable) return result;
    if (!objectTypes[typeof iterable]) return result;
    callback = callback && typeof thisArg == 'undefined' ? callback : baseCreateCallback(callback, thisArg, 3);
      var ownIndex = -1,
          ownProps = objectTypes[typeof iterable] && keys(iterable),
          length = ownProps ? ownProps.length : 0;

      while (++ownIndex < length) {
        index = ownProps[ownIndex];
        if (callback(iterable[index], index, collection) === false) return result;
      }
    return result
  };

  /**
   * Checks if the specified property name exists as a direct property of `object`,
   * instead of an inherited property.
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {Object} object The object to inspect.
   * @param {string} key The name of the property to check.
   * @returns {boolean} Returns `true` if key is a direct property, else `false`.
   * @example
   *
   * _.has({ 'a': 1, 'b': 2, 'c': 3 }, 'b');
   * // => true
   */
  function has(object, key) {
    return object ? hasOwnProperty.call(object, key) : false;
  }

  /**
   * Checks if `value` is a DOM element.
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {*} value The value to check.
   * @returns {boolean} Returns `true` if the `value` is a DOM element, else `false`.
   * @example
   *
   * _.isElement(document.body);
   * // => true
   */
  function isElement(value) {
    return value && value.nodeType === 1 || false;
  }

  /**
   * Performs a deep comparison between two values to determine if they are
   * equivalent to each other. If a callback is provided it will be executed
   * to compare values. If the callback returns `undefined` comparisons will
   * be handled by the method instead. The callback is bound to `thisArg` and
   * invoked with two arguments; (a, b).
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {*} a The value to compare.
   * @param {*} b The other value to compare.
   * @param {Function} [callback] The function to customize comparing values.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {boolean} Returns `true` if the values are equivalent, else `false`.
   * @example
   *
   * var object = { 'name': 'fred' };
   * var copy = { 'name': 'fred' };
   *
   * object == copy;
   * // => false
   *
   * _.isEqual(object, copy);
   * // => true
   *
   * var words = ['hello', 'goodbye'];
   * var otherWords = ['hi', 'goodbye'];
   *
   * _.isEqual(words, otherWords, function(a, b) {
   *   var reGreet = /^(?:hello|hi)$/i,
   *       aGreet = _.isString(a) && reGreet.test(a),
   *       bGreet = _.isString(b) && reGreet.test(b);
   *
   *   return (aGreet || bGreet) ? (aGreet == bGreet) : undefined;
   * });
   * // => true
   */
  function isEqual(a, b, callback, thisArg) {
    return baseIsEqual(a, b, typeof callback == 'function' && baseCreateCallback(callback, thisArg, 2));
  }

  /**
   * Checks if `value` is a function.
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {*} value The value to check.
   * @returns {boolean} Returns `true` if the `value` is a function, else `false`.
   * @example
   *
   * _.isFunction(_);
   * // => true
   */
  function isFunction(value) {
    return typeof value == 'function';
  }

  /**
   * Checks if `value` is the language type of Object.
   * (e.g. arrays, functions, objects, regexes, `new Number(0)`, and `new String('')`)
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {*} value The value to check.
   * @returns {boolean} Returns `true` if the `value` is an object, else `false`.
   * @example
   *
   * _.isObject({});
   * // => true
   *
   * _.isObject([1, 2, 3]);
   * // => true
   *
   * _.isObject(1);
   * // => false
   */
  function isObject(value) {
    // check if the value is the ECMAScript language type of Object
    // http://es5.github.io/#x8
    // and avoid a V8 bug
    // http://code.google.com/p/v8/issues/detail?id=2291
    return !!(value && objectTypes[typeof value]);
  }

  /**
   * Checks if `value` is a number.
   *
   * Note: `NaN` is considered a number. See http://es5.github.io/#x8.5.
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {*} value The value to check.
   * @returns {boolean} Returns `true` if the `value` is a number, else `false`.
   * @example
   *
   * _.isNumber(8.4 * 5);
   * // => true
   */
  function isNumber(value) {
    return typeof value == 'number' ||
      value && typeof value == 'object' && toString.call(value) == numberClass || false;
  }

  /**
   * Checks if `value` is a string.
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {*} value The value to check.
   * @returns {boolean} Returns `true` if the `value` is a string, else `false`.
   * @example
   *
   * _.isString('fred');
   * // => true
   */
  function isString(value) {
    return typeof value == 'string' ||
      value && typeof value == 'object' && toString.call(value) == stringClass || false;
  }

  /**
   * Creates a shallow clone of `object` excluding the specified properties.
   * Property names may be specified as individual arguments or as arrays of
   * property names. If a callback is provided it will be executed for each
   * property of `object` omitting the properties the callback returns truey
   * for. The callback is bound to `thisArg` and invoked with three arguments;
   * (value, key, object).
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {Object} object The source object.
   * @param {Function|...string|string[]} [callback] The properties to omit or the
   *  function called per iteration.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {Object} Returns an object without the omitted properties.
   * @example
   *
   * _.omit({ 'name': 'fred', 'age': 40 }, 'age');
   * // => { 'name': 'fred' }
   *
   * _.omit({ 'name': 'fred', 'age': 40 }, function(value) {
   *   return typeof value == 'number';
   * });
   * // => { 'name': 'fred' }
   */
  function omit(object, callback, thisArg) {
    var result = {};
    if (typeof callback != 'function') {
      var props = [];
      forIn(object, function(value, key) {
        props.push(key);
      });
      props = baseDifference(props, baseFlatten(arguments, true, false, 1));

      var index = -1,
          length = props.length;

      while (++index < length) {
        var key = props[index];
        result[key] = object[key];
      }
    } else {
      callback = lodash.createCallback(callback, thisArg, 3);
      forIn(object, function(value, key, object) {
        if (!callback(value, key, object)) {
          result[key] = value;
        }
      });
    }
    return result;
  }

  /**
   * Creates an array composed of the own enumerable property values of `object`.
   *
   * @static
   * @memberOf _
   * @category Objects
   * @param {Object} object The object to inspect.
   * @returns {Array} Returns an array of property values.
   * @example
   *
   * _.values({ 'one': 1, 'two': 2, 'three': 3 });
   * // => [1, 2, 3] (property order is not guaranteed across environments)
   */
  function values(object) {
    var index = -1,
        props = keys(object),
        length = props.length,
        result = Array(length);

    while (++index < length) {
      result[index] = object[props[index]];
    }
    return result;
  }

  /*--------------------------------------------------------------------------*/

  /**
   * Checks if the given callback returns truey value for **all** elements of
   * a collection. The callback is bound to `thisArg` and invoked with three
   * arguments; (value, index|key, collection).
   *
   * If a property name is provided for `callback` the created "_.pluck" style
   * callback will return the property value of the given element.
   *
   * If an object is provided for `callback` the created "_.where" style callback
   * will return `true` for elements that have the properties of the given object,
   * else `false`.
   *
   * @static
   * @memberOf _
   * @alias all
   * @category Collections
   * @param {Array|Object|string} collection The collection to iterate over.
   * @param {Function|Object|string} [callback=identity] The function called
   *  per iteration. If a property name or object is provided it will be used
   *  to create a "_.pluck" or "_.where" style callback, respectively.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {boolean} Returns `true` if all elements passed the callback check,
   *  else `false`.
   * @example
   *
   * _.every([true, 1, null, 'yes']);
   * // => false
   *
   * var characters = [
   *   { 'name': 'barney', 'age': 36 },
   *   { 'name': 'fred',   'age': 40 }
   * ];
   *
   * // using "_.pluck" callback shorthand
   * _.every(characters, 'age');
   * // => true
   *
   * // using "_.where" callback shorthand
   * _.every(characters, { 'age': 36 });
   * // => false
   */
  function every(collection, callback, thisArg) {
    var result = true;
    callback = lodash.createCallback(callback, thisArg, 3);

    var index = -1,
        length = collection ? collection.length : 0;

    if (typeof length == 'number') {
      while (++index < length) {
        if (!(result = !!callback(collection[index], index, collection))) {
          break;
        }
      }
    } else {
      forOwn(collection, function(value, index, collection) {
        return (result = !!callback(value, index, collection));
      });
    }
    return result;
  }

  /**
   * Iterates over elements of a collection, executing the callback for each
   * element. The callback is bound to `thisArg` and invoked with three arguments;
   * (value, index|key, collection). Callbacks may exit iteration early by
   * explicitly returning `false`.
   *
   * Note: As with other "Collections" methods, objects with a `length` property
   * are iterated like arrays. To avoid this behavior `_.forIn` or `_.forOwn`
   * may be used for object iteration.
   *
   * @static
   * @memberOf _
   * @alias each
   * @category Collections
   * @param {Array|Object|string} collection The collection to iterate over.
   * @param {Function} [callback=identity] The function called per iteration.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {Array|Object|string} Returns `collection`.
   * @example
   *
   * _([1, 2, 3]).forEach(function(num) { console.log(num); }).join(',');
   * // => logs each number and returns '1,2,3'
   *
   * _.forEach({ 'one': 1, 'two': 2, 'three': 3 }, function(num) { console.log(num); });
   * // => logs each number and returns the object (property order is not guaranteed across environments)
   */
  function forEach(collection, callback, thisArg) {
    var index = -1,
        length = collection ? collection.length : 0;

    callback = callback && typeof thisArg == 'undefined' ? callback : baseCreateCallback(callback, thisArg, 3);
    if (typeof length == 'number') {
      while (++index < length) {
        if (callback(collection[index], index, collection) === false) {
          break;
        }
      }
    } else {
      forOwn(collection, callback);
    }
    return collection;
  }

  /**
   * Invokes the method named by `methodName` on each element in the `collection`
   * returning an array of the results of each invoked method. Additional arguments
   * will be provided to each invoked method. If `methodName` is a function it
   * will be invoked for, and `this` bound to, each element in the `collection`.
   *
   * @static
   * @memberOf _
   * @category Collections
   * @param {Array|Object|string} collection The collection to iterate over.
   * @param {Function|string} methodName The name of the method to invoke or
   *  the function invoked per iteration.
   * @param {...*} [arg] Arguments to invoke the method with.
   * @returns {Array} Returns a new array of the results of each invoked method.
   * @example
   *
   * _.invoke([[5, 1, 7], [3, 2, 1]], 'sort');
   * // => [[1, 5, 7], [1, 2, 3]]
   *
   * _.invoke([123, 456], String.prototype.split, '');
   * // => [['1', '2', '3'], ['4', '5', '6']]
   */
  function invoke(collection, methodName) {
    var args = slice(arguments, 2),
        index = -1,
        isFunc = typeof methodName == 'function',
        length = collection ? collection.length : 0,
        result = Array(typeof length == 'number' ? length : 0);

    forEach(collection, function(value) {
      result[++index] = (isFunc ? methodName : value[methodName]).apply(value, args);
    });
    return result;
  }

  /**
   * Creates an array of values by running each element in the collection
   * through the callback. The callback is bound to `thisArg` and invoked with
   * three arguments; (value, index|key, collection).
   *
   * If a property name is provided for `callback` the created "_.pluck" style
   * callback will return the property value of the given element.
   *
   * If an object is provided for `callback` the created "_.where" style callback
   * will return `true` for elements that have the properties of the given object,
   * else `false`.
   *
   * @static
   * @memberOf _
   * @alias collect
   * @category Collections
   * @param {Array|Object|string} collection The collection to iterate over.
   * @param {Function|Object|string} [callback=identity] The function called
   *  per iteration. If a property name or object is provided it will be used
   *  to create a "_.pluck" or "_.where" style callback, respectively.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {Array} Returns a new array of the results of each `callback` execution.
   * @example
   *
   * _.map([1, 2, 3], function(num) { return num * 3; });
   * // => [3, 6, 9]
   *
   * _.map({ 'one': 1, 'two': 2, 'three': 3 }, function(num) { return num * 3; });
   * // => [3, 6, 9] (property order is not guaranteed across environments)
   *
   * var characters = [
   *   { 'name': 'barney', 'age': 36 },
   *   { 'name': 'fred',   'age': 40 }
   * ];
   *
   * // using "_.pluck" callback shorthand
   * _.map(characters, 'name');
   * // => ['barney', 'fred']
   */
  function map(collection, callback, thisArg) {
    var index = -1,
        length = collection ? collection.length : 0;

    callback = lodash.createCallback(callback, thisArg, 3);
    if (typeof length == 'number') {
      var result = Array(length);
      while (++index < length) {
        result[index] = callback(collection[index], index, collection);
      }
    } else {
      result = [];
      forOwn(collection, function(value, key, collection) {
        result[++index] = callback(value, key, collection);
      });
    }
    return result;
  }

  /**
   * Retrieves the value of a specified property from all elements in the collection.
   *
   * @static
   * @memberOf _
   * @type Function
   * @category Collections
   * @param {Array|Object|string} collection The collection to iterate over.
   * @param {string} property The name of the property to pluck.
   * @returns {Array} Returns a new array of property values.
   * @example
   *
   * var characters = [
   *   { 'name': 'barney', 'age': 36 },
   *   { 'name': 'fred',   'age': 40 }
   * ];
   *
   * _.pluck(characters, 'name');
   * // => ['barney', 'fred']
   */
  var pluck = map;

  /**
   * Reduces a collection to a value which is the accumulated result of running
   * each element in the collection through the callback, where each successive
   * callback execution consumes the return value of the previous execution. If
   * `accumulator` is not provided the first element of the collection will be
   * used as the initial `accumulator` value. The callback is bound to `thisArg`
   * and invoked with four arguments; (accumulator, value, index|key, collection).
   *
   * @static
   * @memberOf _
   * @alias foldl, inject
   * @category Collections
   * @param {Array|Object|string} collection The collection to iterate over.
   * @param {Function} [callback=identity] The function called per iteration.
   * @param {*} [accumulator] Initial value of the accumulator.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {*} Returns the accumulated value.
   * @example
   *
   * var sum = _.reduce([1, 2, 3], function(sum, num) {
   *   return sum + num;
   * });
   * // => 6
   *
   * var mapped = _.reduce({ 'a': 1, 'b': 2, 'c': 3 }, function(result, num, key) {
   *   result[key] = num * 3;
   *   return result;
   * }, {});
   * // => { 'a': 3, 'b': 6, 'c': 9 }
   */
  function reduce(collection, callback, accumulator, thisArg) {
    if (!collection) return accumulator;
    var noaccum = arguments.length < 3;
    callback = lodash.createCallback(callback, thisArg, 4);

    var index = -1,
        length = collection.length;

    if (typeof length == 'number') {
      if (noaccum) {
        accumulator = collection[++index];
      }
      while (++index < length) {
        accumulator = callback(accumulator, collection[index], index, collection);
      }
    } else {
      forOwn(collection, function(value, index, collection) {
        accumulator = noaccum
          ? (noaccum = false, value)
          : callback(accumulator, value, index, collection)
      });
    }
    return accumulator;
  }

  /*--------------------------------------------------------------------------*/

  /**
   * Creates an array excluding all values of the provided arrays using strict
   * equality for comparisons, i.e. `===`.
   *
   * @static
   * @memberOf _
   * @category Arrays
   * @param {Array} array The array to process.
   * @param {...Array} [values] The arrays of values to exclude.
   * @returns {Array} Returns a new array of filtered values.
   * @example
   *
   * _.difference([1, 2, 3, 4, 5], [5, 2, 10]);
   * // => [1, 3, 4]
   */
  function difference(array) {
    return baseDifference(array, baseFlatten(arguments, true, true, 1));
  }

  /**
   * Flattens a nested array (the nesting can be to any depth). If `isShallow`
   * is truey, the array will only be flattened a single level. If a callback
   * is provided each element of the array is passed through the callback before
   * flattening. The callback is bound to `thisArg` and invoked with three
   * arguments; (value, index, array).
   *
   * If a property name is provided for `callback` the created "_.pluck" style
   * callback will return the property value of the given element.
   *
   * If an object is provided for `callback` the created "_.where" style callback
   * will return `true` for elements that have the properties of the given object,
   * else `false`.
   *
   * @static
   * @memberOf _
   * @category Arrays
   * @param {Array} array The array to flatten.
   * @param {boolean} [isShallow=false] A flag to restrict flattening to a single level.
   * @param {Function|Object|string} [callback=identity] The function called
   *  per iteration. If a property name or object is provided it will be used
   *  to create a "_.pluck" or "_.where" style callback, respectively.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {Array} Returns a new flattened array.
   * @example
   *
   * _.flatten([1, [2], [3, [[4]]]]);
   * // => [1, 2, 3, 4];
   *
   * _.flatten([1, [2], [3, [[4]]]], true);
   * // => [1, 2, 3, [[4]]];
   *
   * var characters = [
   *   { 'name': 'barney', 'age': 30, 'pets': ['hoppy'] },
   *   { 'name': 'fred',   'age': 40, 'pets': ['baby puss', 'dino'] }
   * ];
   *
   * // using "_.pluck" callback shorthand
   * _.flatten(characters, 'pets');
   * // => ['hoppy', 'baby puss', 'dino']
   */
  function flatten(array, isShallow, callback, thisArg) {
    // juggle arguments
    if (typeof isShallow != 'boolean' && isShallow != null) {
      thisArg = callback;
      callback = (typeof isShallow != 'function' && thisArg && thisArg[isShallow] === array) ? null : isShallow;
      isShallow = false;
    }
    if (callback != null) {
      array = map(array, callback, thisArg);
    }
    return baseFlatten(array, isShallow);
  }

  /**
   * Gets the index at which the first occurrence of `value` is found using
   * strict equality for comparisons, i.e. `===`. If the array is already sorted
   * providing `true` for `fromIndex` will run a faster binary search.
   *
   * @static
   * @memberOf _
   * @category Arrays
   * @param {Array} array The array to search.
   * @param {*} value The value to search for.
   * @param {boolean|number} [fromIndex=0] The index to search from or `true`
   *  to perform a binary search on a sorted array.
   * @returns {number} Returns the index of the matched value or `-1`.
   * @example
   *
   * _.indexOf([1, 2, 3, 1, 2, 3], 2);
   * // => 1
   *
   * _.indexOf([1, 2, 3, 1, 2, 3], 2, 3);
   * // => 4
   *
   * _.indexOf([1, 1, 2, 2, 3, 3], 2, true);
   * // => 2
   */
  function indexOf(array, value, fromIndex) {
    if (typeof fromIndex == 'number') {
      var length = array ? array.length : 0;
      fromIndex = (fromIndex < 0 ? nativeMax(0, length + fromIndex) : fromIndex || 0);
    } else if (fromIndex) {
      var index = sortedIndex(array, value);
      return array[index] === value ? index : -1;
    }
    return baseIndexOf(array, value, fromIndex);
  }

  /**
   * Creates an array of unique values present in all provided arrays using
   * strict equality for comparisons, i.e. `===`.
   *
   * @static
   * @memberOf _
   * @category Arrays
   * @param {...Array} [array] The arrays to inspect.
   * @returns {Array} Returns an array of shared values.
   * @example
   *
   * _.intersection([1, 2, 3], [5, 2, 1, 4], [2, 1]);
   * // => [1, 2]
   */
  function intersection() {
    var args = [],
        argsIndex = -1,
        argsLength = arguments.length,
        caches = getArray(),
        indexOf = getIndexOf(),
        trustIndexOf = indexOf === baseIndexOf,
        seen = getArray();

    while (++argsIndex < argsLength) {
      var value = arguments[argsIndex];
      if (isArray(value) || isArguments(value)) {
        args.push(value);
        caches.push(trustIndexOf && value.length >= largeArraySize &&
          createCache(argsIndex ? args[argsIndex] : seen));
      }
    }
    var array = args[0],
        index = -1,
        length = array ? array.length : 0,
        result = [];

    outer:
    while (++index < length) {
      var cache = caches[0];
      value = array[index];

      if ((cache ? cacheIndexOf(cache, value) : indexOf(seen, value)) < 0) {
        argsIndex = argsLength;
        (cache || seen).push(value);
        while (--argsIndex) {
          cache = caches[argsIndex];
          if ((cache ? cacheIndexOf(cache, value) : indexOf(args[argsIndex], value)) < 0) {
            continue outer;
          }
        }
        result.push(value);
      }
    }
    while (argsLength--) {
      cache = caches[argsLength];
      if (cache) {
        releaseObject(cache);
      }
    }
    releaseArray(caches);
    releaseArray(seen);
    return result;
  }

  /**
   * Gets the last element or last `n` elements of an array. If a callback is
   * provided elements at the end of the array are returned as long as the
   * callback returns truey. The callback is bound to `thisArg` and invoked
   * with three arguments; (value, index, array).
   *
   * If a property name is provided for `callback` the created "_.pluck" style
   * callback will return the property value of the given element.
   *
   * If an object is provided for `callback` the created "_.where" style callback
   * will return `true` for elements that have the properties of the given object,
   * else `false`.
   *
   * @static
   * @memberOf _
   * @category Arrays
   * @param {Array} array The array to query.
   * @param {Function|Object|number|string} [callback] The function called
   *  per element or the number of elements to return. If a property name or
   *  object is provided it will be used to create a "_.pluck" or "_.where"
   *  style callback, respectively.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {*} Returns the last element(s) of `array`.
   * @example
   *
   * _.last([1, 2, 3]);
   * // => 3
   *
   * _.last([1, 2, 3], 2);
   * // => [2, 3]
   *
   * _.last([1, 2, 3], function(num) {
   *   return num > 1;
   * });
   * // => [2, 3]
   *
   * var characters = [
   *   { 'name': 'barney',  'blocked': false, 'employer': 'slate' },
   *   { 'name': 'fred',    'blocked': true,  'employer': 'slate' },
   *   { 'name': 'pebbles', 'blocked': true,  'employer': 'na' }
   * ];
   *
   * // using "_.pluck" callback shorthand
   * _.pluck(_.last(characters, 'blocked'), 'name');
   * // => ['fred', 'pebbles']
   *
   * // using "_.where" callback shorthand
   * _.last(characters, { 'employer': 'na' });
   * // => [{ 'name': 'pebbles', 'blocked': true, 'employer': 'na' }]
   */
  function last(array, callback, thisArg) {
    var n = 0,
        length = array ? array.length : 0;

    if (typeof callback != 'number' && callback != null) {
      var index = length;
      callback = lodash.createCallback(callback, thisArg, 3);
      while (index-- && callback(array[index], index, array)) {
        n++;
      }
    } else {
      n = callback;
      if (n == null || thisArg) {
        return array ? array[length - 1] : undefined;
      }
    }
    return slice(array, nativeMax(0, length - n));
  }

  /**
   * Uses a binary search to determine the smallest index at which a value
   * should be inserted into a given sorted array in order to maintain the sort
   * order of the array. If a callback is provided it will be executed for
   * `value` and each element of `array` to compute their sort ranking. The
   * callback is bound to `thisArg` and invoked with one argument; (value).
   *
   * If a property name is provided for `callback` the created "_.pluck" style
   * callback will return the property value of the given element.
   *
   * If an object is provided for `callback` the created "_.where" style callback
   * will return `true` for elements that have the properties of the given object,
   * else `false`.
   *
   * @static
   * @memberOf _
   * @category Arrays
   * @param {Array} array The array to inspect.
   * @param {*} value The value to evaluate.
   * @param {Function|Object|string} [callback=identity] The function called
   *  per iteration. If a property name or object is provided it will be used
   *  to create a "_.pluck" or "_.where" style callback, respectively.
   * @param {*} [thisArg] The `this` binding of `callback`.
   * @returns {number} Returns the index at which `value` should be inserted
   *  into `array`.
   * @example
   *
   * _.sortedIndex([20, 30, 50], 40);
   * // => 2
   *
   * // using "_.pluck" callback shorthand
   * _.sortedIndex([{ 'x': 20 }, { 'x': 30 }, { 'x': 50 }], { 'x': 40 }, 'x');
   * // => 2
   *
   * var dict = {
   *   'wordToNumber': { 'twenty': 20, 'thirty': 30, 'fourty': 40, 'fifty': 50 }
   * };
   *
   * _.sortedIndex(['twenty', 'thirty', 'fifty'], 'fourty', function(word) {
   *   return dict.wordToNumber[word];
   * });
   * // => 2
   *
   * _.sortedIndex(['twenty', 'thirty', 'fifty'], 'fourty', function(word) {
   *   return this.wordToNumber[word];
   * }, dict);
   * // => 2
   */
  function sortedIndex(array, value, callback, thisArg) {
    var low = 0,
        high = array ? array.length : low;

    // explicitly reference `identity` for better inlining in Firefox
    callback = callback ? lodash.createCallback(callback, thisArg, 1) : identity;
    value = callback(value);

    while (low < high) {
      var mid = (low + high) >>> 1;
      (callback(array[mid]) < value)
        ? low = mid + 1
        : high = mid;
    }
    return low;
  }

  /*--------------------------------------------------------------------------*/

  /**
   * Creates a function that, when called, invokes `func` with the `this`
   * binding of `thisArg` and prepends any additional `bind` arguments to those
   * provided to the bound function.
   *
   * @static
   * @memberOf _
   * @category Functions
   * @param {Function} func The function to bind.
   * @param {*} [thisArg] The `this` binding of `func`.
   * @param {...*} [arg] Arguments to be partially applied.
   * @returns {Function} Returns the new bound function.
   * @example
   *
   * var func = function(greeting) {
   *   return greeting + ' ' + this.name;
   * };
   *
   * func = _.bind(func, { 'name': 'fred' }, 'hi');
   * func();
   * // => 'hi fred'
   */
  function bind(func, thisArg) {
    return arguments.length > 2
      ? createWrapper(func, 17, slice(arguments, 2), null, thisArg)
      : createWrapper(func, 1, null, null, thisArg);
  }

  /**
   * Defers executing the `func` function until the current call stack has cleared.
   * Additional arguments will be provided to `func` when it is invoked.
   *
   * @static
   * @memberOf _
   * @category Functions
   * @param {Function} func The function to defer.
   * @param {...*} [arg] Arguments to invoke the function with.
   * @returns {number} Returns the timer id.
   * @example
   *
   * _.defer(function(text) { console.log(text); }, 'deferred');
   * // logs 'deferred' after one or more milliseconds
   */
  function defer(func) {
    if (!isFunction(func)) {
      throw new TypeError;
    }
    var args = slice(arguments, 1);
    return setTimeout(function() { func.apply(undefined, args); }, 1);
  }

  /**
   * Creates a function that, when called, invokes `func` with any additional
   * `partial` arguments prepended to those provided to the new function. This
   * method is similar to `_.bind` except it does **not** alter the `this` binding.
   *
   * @static
   * @memberOf _
   * @category Functions
   * @param {Function} func The function to partially apply arguments to.
   * @param {...*} [arg] Arguments to be partially applied.
   * @returns {Function} Returns the new partially applied function.
   * @example
   *
   * var greet = function(greeting, name) { return greeting + ' ' + name; };
   * var hi = _.partial(greet, 'hi');
   * hi('fred');
   * // => 'hi fred'
   */
  function partial(func) {
    return createWrapper(func, 16, slice(arguments, 1));
  }

  /*--------------------------------------------------------------------------*/

  /**
   * Produces a callback bound to an optional `thisArg`. If `func` is a property
   * name the created callback will return the property value for a given element.
   * If `func` is an object the created callback will return `true` for elements
   * that contain the equivalent object properties, otherwise it will return `false`.
   *
   * @static
   * @memberOf _
   * @category Utilities
   * @param {*} [func=identity] The value to convert to a callback.
   * @param {*} [thisArg] The `this` binding of the created callback.
   * @param {number} [argCount] The number of arguments the callback accepts.
   * @returns {Function} Returns a callback function.
   * @example
   *
   * var characters = [
   *   { 'name': 'barney', 'age': 36 },
   *   { 'name': 'fred',   'age': 40 }
   * ];
   *
   * // wrap to create custom callback shorthands
   * _.createCallback = _.wrap(_.createCallback, function(func, callback, thisArg) {
   *   var match = /^(.+?)__([gl]t)(.+)$/.exec(callback);
   *   return !match ? func(callback, thisArg) : function(object) {
   *     return match[2] == 'gt' ? object[match[1]] > match[3] : object[match[1]] < match[3];
   *   };
   * });
   *
   * _.filter(characters, 'age__gt38');
   * // => [{ 'name': 'fred', 'age': 40 }]
   */
  function createCallback(func, thisArg, argCount) {
    var type = typeof func;
    if (func == null || type == 'function') {
      return baseCreateCallback(func, thisArg, argCount);
    }
    // handle "_.pluck" style callback shorthands
    if (type != 'object') {
      return property(func);
    }
    var props = keys(func),
        key = props[0],
        a = func[key];

    // handle "_.where" style callback shorthands
    if (props.length == 1 && a === a && !isObject(a)) {
      // fast path the common case of providing an object with a single
      // property containing a primitive value
      return function(object) {
        var b = object[key];
        return a === b && (a !== 0 || (1 / a == 1 / b));
      };
    }
    return function(object) {
      var length = props.length,
          result = false;

      while (length--) {
        if (!(result = baseIsEqual(object[props[length]], func[props[length]], null, true))) {
          break;
        }
      }
      return result;
    };
  }

  /**
   * This method returns the first argument provided to it.
   *
   * @static
   * @memberOf _
   * @category Utilities
   * @param {*} value Any value.
   * @returns {*} Returns `value`.
   * @example
   *
   * var object = { 'name': 'fred' };
   * _.identity(object) === object;
   * // => true
   */
  function identity(value) {
    return value;
  }

  /**
   * A no-operation function.
   *
   * @static
   * @memberOf _
   * @category Utilities
   * @example
   *
   * var object = { 'name': 'fred' };
   * _.noop(object) === undefined;
   * // => true
   */
  function noop() {
    // no operation performed
  }

  /**
   * Creates a "_.pluck" style function, which returns the `key` value of a
   * given object.
   *
   * @static
   * @memberOf _
   * @category Utilities
   * @param {string} key The name of the property to retrieve.
   * @returns {Function} Returns the new function.
   * @example
   *
   * var characters = [
   *   { 'name': 'fred',   'age': 40 },
   *   { 'name': 'barney', 'age': 36 }
   * ];
   *
   * var getName = _.property('name');
   *
   * _.map(characters, getName);
   * // => ['barney', 'fred']
   *
   * _.sortBy(characters, getName);
   * // => [{ 'name': 'barney', 'age': 36 }, { 'name': 'fred',   'age': 40 }]
   */
  function property(key) {
    return function(object) {
      return object[key];
    };
  }

  /**
   * Generates a unique ID. If `prefix` is provided the ID will be appended to it.
   *
   * @static
   * @memberOf _
   * @category Utilities
   * @param {string} [prefix] The value to prefix the ID with.
   * @returns {string} Returns the unique ID.
   * @example
   *
   * _.uniqueId('contact_');
   * // => 'contact_104'
   *
   * _.uniqueId();
   * // => '105'
   */
  function uniqueId(prefix) {
    var id = ++idCounter;
    return String(prefix == null ? '' : prefix) + id;
  }

  /*--------------------------------------------------------------------------*/

  lodash.assign = assign;
  lodash.bind = bind;
  lodash.createCallback = createCallback;
  lodash.defaults = defaults;
  lodash.defer = defer;
  lodash.difference = difference;
  lodash.flatten = flatten;
  lodash.forEach = forEach;
  lodash.forIn = forIn;
  lodash.forOwn = forOwn;
  lodash.intersection = intersection;
  lodash.invoke = invoke;
  lodash.keys = keys;
  lodash.map = map;
  lodash.omit = omit;
  lodash.partial = partial;
  lodash.pluck = pluck;
  lodash.property = property;
  lodash.values = values;

  // add aliases
  lodash.collect = map;
  lodash.each = forEach;
  lodash.extend = assign;

  /*--------------------------------------------------------------------------*/

  // add functions that return unwrapped values when chaining
  lodash.clone = clone;
  lodash.every = every;
  lodash.has = has;
  lodash.identity = identity;
  lodash.indexOf = indexOf;
  lodash.isArguments = isArguments;
  lodash.isArray = isArray;
  lodash.isElement = isElement;
  lodash.isEqual = isEqual;
  lodash.isFunction = isFunction;
  lodash.isNumber = isNumber;
  lodash.isObject = isObject;
  lodash.isString = isString;
  lodash.noop = noop;
  lodash.reduce = reduce;
  lodash.sortedIndex = sortedIndex;
  lodash.uniqueId = uniqueId;

  // add aliases
  lodash.all = every;
  lodash.foldl = reduce;
  lodash.inject = reduce;

  /*--------------------------------------------------------------------------*/

  lodash.last = last;

  /*--------------------------------------------------------------------------*/

  /**
   * The semantic version number.
   *
   * @static
   * @memberOf _
   * @type string
   */
  lodash.VERSION = '2.4.1';

  /*--------------------------------------------------------------------------*/

  // some AMD build optimizers like r.js check for condition patterns like the following:
  if (typeof define == 'function' && typeof define.amd == 'object' && define.amd) {
    // Expose Lo-Dash to the global object even when an AMD loader is present in
    // case Lo-Dash is loaded with a RequireJS shim config.
    // See http://requirejs.org/docs/api.html#config-shim
    root._ = lodash;

    // define as an anonymous module so, through path mapping, it can be
    // referenced as the "underscore" module
    define(function() {
      return lodash;
    });
  }
  // check for `exports` after `define` in case a build optimizer adds an `exports` object
  else if (freeExports && freeModule) {
    // in Node.js or RingoJS
    if (moduleExports) {
      (freeModule.exports = lodash)._ = lodash;
    }
    // in Narwhal or Rhino -require
    else {
      freeExports._ = lodash;
    }
  }
  else {
    // in a browser or Rhino
    root._ = lodash;
  }
}.call(this));

}).call(this,typeof self !== "undefined" ? self : typeof window !== "undefined" ? window : {})
},{}],"lodash":[function(_dereq_,module,exports){
module.exports=_dereq_('M4+//f');
},{}],3:[function(_dereq_,module,exports){
/*!
 * EventEmitter2
 * https://github.com/hij1nx/EventEmitter2
 *
 * Copyright (c) 2013 hij1nx
 * Licensed under the MIT license.
 */
;!function(undefined) {

  var isArray = Array.isArray ? Array.isArray : function _isArray(obj) {
    return Object.prototype.toString.call(obj) === "[object Array]";
  };
  var defaultMaxListeners = 10;

  function init() {
    this._events = {};
    if (this._conf) {
      configure.call(this, this._conf);
    }
  }

  function configure(conf) {
    if (conf) {

      this._conf = conf;

      conf.delimiter && (this.delimiter = conf.delimiter);
      conf.maxListeners && (this._events.maxListeners = conf.maxListeners);
      conf.wildcard && (this.wildcard = conf.wildcard);
      conf.newListener && (this.newListener = conf.newListener);

      if (this.wildcard) {
        this.listenerTree = {};
      }
    }
  }

  function EventEmitter(conf) {
    this._events = {};
    this.newListener = false;
    configure.call(this, conf);
  }

  //
  // Attention, function return type now is array, always !
  // It has zero elements if no any matches found and one or more
  // elements (leafs) if there are matches
  //
  function searchListenerTree(handlers, type, tree, i) {
    if (!tree) {
      return [];
    }
    var listeners=[], leaf, len, branch, xTree, xxTree, isolatedBranch, endReached,
        typeLength = type.length, currentType = type[i], nextType = type[i+1];
    if (i === typeLength && tree._listeners) {
      //
      // If at the end of the event(s) list and the tree has listeners
      // invoke those listeners.
      //
      if (typeof tree._listeners === 'function') {
        handlers && handlers.push(tree._listeners);
        return [tree];
      } else {
        for (leaf = 0, len = tree._listeners.length; leaf < len; leaf++) {
          handlers && handlers.push(tree._listeners[leaf]);
        }
        return [tree];
      }
    }

    if ((currentType === '*' || currentType === '**') || tree[currentType]) {
      //
      // If the event emitted is '*' at this part
      // or there is a concrete match at this patch
      //
      if (currentType === '*') {
        for (branch in tree) {
          if (branch !== '_listeners' && tree.hasOwnProperty(branch)) {
            listeners = listeners.concat(searchListenerTree(handlers, type, tree[branch], i+1));
          }
        }
        return listeners;
      } else if(currentType === '**') {
        endReached = (i+1 === typeLength || (i+2 === typeLength && nextType === '*'));
        if(endReached && tree._listeners) {
          // The next element has a _listeners, add it to the handlers.
          listeners = listeners.concat(searchListenerTree(handlers, type, tree, typeLength));
        }

        for (branch in tree) {
          if (branch !== '_listeners' && tree.hasOwnProperty(branch)) {
            if(branch === '*' || branch === '**') {
              if(tree[branch]._listeners && !endReached) {
                listeners = listeners.concat(searchListenerTree(handlers, type, tree[branch], typeLength));
              }
              listeners = listeners.concat(searchListenerTree(handlers, type, tree[branch], i));
            } else if(branch === nextType) {
              listeners = listeners.concat(searchListenerTree(handlers, type, tree[branch], i+2));
            } else {
              // No match on this one, shift into the tree but not in the type array.
              listeners = listeners.concat(searchListenerTree(handlers, type, tree[branch], i));
            }
          }
        }
        return listeners;
      }

      listeners = listeners.concat(searchListenerTree(handlers, type, tree[currentType], i+1));
    }

    xTree = tree['*'];
    if (xTree) {
      //
      // If the listener tree will allow any match for this part,
      // then recursively explore all branches of the tree
      //
      searchListenerTree(handlers, type, xTree, i+1);
    }

    xxTree = tree['**'];
    if(xxTree) {
      if(i < typeLength) {
        if(xxTree._listeners) {
          // If we have a listener on a '**', it will catch all, so add its handler.
          searchListenerTree(handlers, type, xxTree, typeLength);
        }

        // Build arrays of matching next branches and others.
        for(branch in xxTree) {
          if(branch !== '_listeners' && xxTree.hasOwnProperty(branch)) {
            if(branch === nextType) {
              // We know the next element will match, so jump twice.
              searchListenerTree(handlers, type, xxTree[branch], i+2);
            } else if(branch === currentType) {
              // Current node matches, move into the tree.
              searchListenerTree(handlers, type, xxTree[branch], i+1);
            } else {
              isolatedBranch = {};
              isolatedBranch[branch] = xxTree[branch];
              searchListenerTree(handlers, type, { '**': isolatedBranch }, i+1);
            }
          }
        }
      } else if(xxTree._listeners) {
        // We have reached the end and still on a '**'
        searchListenerTree(handlers, type, xxTree, typeLength);
      } else if(xxTree['*'] && xxTree['*']._listeners) {
        searchListenerTree(handlers, type, xxTree['*'], typeLength);
      }
    }

    return listeners;
  }

  function growListenerTree(type, listener) {

    type = typeof type === 'string' ? type.split(this.delimiter) : type.slice();

    //
    // Looks for two consecutive '**', if so, don't add the event at all.
    //
    for(var i = 0, len = type.length; i+1 < len; i++) {
      if(type[i] === '**' && type[i+1] === '**') {
        return;
      }
    }

    var tree = this.listenerTree;
    var name = type.shift();

    while (name) {

      if (!tree[name]) {
        tree[name] = {};
      }

      tree = tree[name];

      if (type.length === 0) {

        if (!tree._listeners) {
          tree._listeners = listener;
        }
        else if(typeof tree._listeners === 'function') {
          tree._listeners = [tree._listeners, listener];
        }
        else if (isArray(tree._listeners)) {

          tree._listeners.push(listener);

          if (!tree._listeners.warned) {

            var m = defaultMaxListeners;

            if (typeof this._events.maxListeners !== 'undefined') {
              m = this._events.maxListeners;
            }

            if (m > 0 && tree._listeners.length > m) {

              tree._listeners.warned = true;
              console.error('(node) warning: possible EventEmitter memory ' +
                            'leak detected. %d listeners added. ' +
                            'Use emitter.setMaxListeners() to increase limit.',
                            tree._listeners.length);
              console.trace();
            }
          }
        }
        return true;
      }
      name = type.shift();
    }
    return true;
  }

  // By default EventEmitters will print a warning if more than
  // 10 listeners are added to it. This is a useful default which
  // helps finding memory leaks.
  //
  // Obviously not all Emitters should be limited to 10. This function allows
  // that to be increased. Set to zero for unlimited.

  EventEmitter.prototype.delimiter = '.';

  EventEmitter.prototype.setMaxListeners = function(n) {
    this._events || init.call(this);
    this._events.maxListeners = n;
    if (!this._conf) this._conf = {};
    this._conf.maxListeners = n;
  };

  EventEmitter.prototype.event = '';

  EventEmitter.prototype.once = function(event, fn) {
    this.many(event, 1, fn);
    return this;
  };

  EventEmitter.prototype.many = function(event, ttl, fn) {
    var self = this;

    if (typeof fn !== 'function') {
      throw new Error('many only accepts instances of Function');
    }

    function listener() {
      if (--ttl === 0) {
        self.off(event, listener);
      }
      fn.apply(this, arguments);
    }

    listener._origin = fn;

    this.on(event, listener);

    return self;
  };

  EventEmitter.prototype.emit = function() {

    this._events || init.call(this);

    var type = arguments[0];

    if (type === 'newListener' && !this.newListener) {
      if (!this._events.newListener) { return false; }
    }

    // Loop through the *_all* functions and invoke them.
    if (this._all) {
      var l = arguments.length;
      var args = new Array(l - 1);
      for (var i = 1; i < l; i++) args[i - 1] = arguments[i];
      for (i = 0, l = this._all.length; i < l; i++) {
        this.event = type;
        this._all[i].apply(this, args);
      }
    }

    // If there is no 'error' event listener then throw.
    if (type === 'error') {

      if (!this._all &&
        !this._events.error &&
        !(this.wildcard && this.listenerTree.error)) {

        if (arguments[1] instanceof Error) {
          throw arguments[1]; // Unhandled 'error' event
        } else {
          throw new Error("Uncaught, unspecified 'error' event.");
        }
        return false;
      }
    }

    var handler;

    if(this.wildcard) {
      handler = [];
      var ns = typeof type === 'string' ? type.split(this.delimiter) : type.slice();
      searchListenerTree.call(this, handler, ns, this.listenerTree, 0);
    }
    else {
      handler = this._events[type];
    }

    if (typeof handler === 'function') {
      this.event = type;
      if (arguments.length === 1) {
        handler.call(this);
      }
      else if (arguments.length > 1)
        switch (arguments.length) {
          case 2:
            handler.call(this, arguments[1]);
            break;
          case 3:
            handler.call(this, arguments[1], arguments[2]);
            break;
          // slower
          default:
            var l = arguments.length;
            var args = new Array(l - 1);
            for (var i = 1; i < l; i++) args[i - 1] = arguments[i];
            handler.apply(this, args);
        }
      return true;
    }
    else if (handler) {
      var l = arguments.length;
      var args = new Array(l - 1);
      for (var i = 1; i < l; i++) args[i - 1] = arguments[i];

      var listeners = handler.slice();
      for (var i = 0, l = listeners.length; i < l; i++) {
        this.event = type;
        listeners[i].apply(this, args);
      }
      return (listeners.length > 0) || !!this._all;
    }
    else {
      return !!this._all;
    }

  };

  EventEmitter.prototype.on = function(type, listener) {

    if (typeof type === 'function') {
      this.onAny(type);
      return this;
    }

    if (typeof listener !== 'function') {
      throw new Error('on only accepts instances of Function');
    }
    this._events || init.call(this);

    // To avoid recursion in the case that type == "newListeners"! Before
    // adding it to the listeners, first emit "newListeners".
    this.emit('newListener', type, listener);

    if(this.wildcard) {
      growListenerTree.call(this, type, listener);
      return this;
    }

    if (!this._events[type]) {
      // Optimize the case of one listener. Don't need the extra array object.
      this._events[type] = listener;
    }
    else if(typeof this._events[type] === 'function') {
      // Adding the second element, need to change to array.
      this._events[type] = [this._events[type], listener];
    }
    else if (isArray(this._events[type])) {
      // If we've already got an array, just append.
      this._events[type].push(listener);

      // Check for listener leak
      if (!this._events[type].warned) {

        var m = defaultMaxListeners;

        if (typeof this._events.maxListeners !== 'undefined') {
          m = this._events.maxListeners;
        }

        if (m > 0 && this._events[type].length > m) {

          this._events[type].warned = true;
          console.error('(node) warning: possible EventEmitter memory ' +
                        'leak detected. %d listeners added. ' +
                        'Use emitter.setMaxListeners() to increase limit.',
                        this._events[type].length);
          console.trace();
        }
      }
    }
    return this;
  };

  EventEmitter.prototype.onAny = function(fn) {

    if (typeof fn !== 'function') {
      throw new Error('onAny only accepts instances of Function');
    }

    if(!this._all) {
      this._all = [];
    }

    // Add the function to the event listener collection.
    this._all.push(fn);
    return this;
  };

  EventEmitter.prototype.addListener = EventEmitter.prototype.on;

  EventEmitter.prototype.off = function(type, listener) {
    if (typeof listener !== 'function') {
      throw new Error('removeListener only takes instances of Function');
    }

    var handlers,leafs=[];

    if(this.wildcard) {
      var ns = typeof type === 'string' ? type.split(this.delimiter) : type.slice();
      leafs = searchListenerTree.call(this, null, ns, this.listenerTree, 0);
    }
    else {
      // does not use listeners(), so no side effect of creating _events[type]
      if (!this._events[type]) return this;
      handlers = this._events[type];
      leafs.push({_listeners:handlers});
    }

    for (var iLeaf=0; iLeaf<leafs.length; iLeaf++) {
      var leaf = leafs[iLeaf];
      handlers = leaf._listeners;
      if (isArray(handlers)) {

        var position = -1;

        for (var i = 0, length = handlers.length; i < length; i++) {
          if (handlers[i] === listener ||
            (handlers[i].listener && handlers[i].listener === listener) ||
            (handlers[i]._origin && handlers[i]._origin === listener)) {
            position = i;
            break;
          }
        }

        if (position < 0) {
          continue;
        }

        if(this.wildcard) {
          leaf._listeners.splice(position, 1);
        }
        else {
          this._events[type].splice(position, 1);
        }

        if (handlers.length === 0) {
          if(this.wildcard) {
            delete leaf._listeners;
          }
          else {
            delete this._events[type];
          }
        }
        return this;
      }
      else if (handlers === listener ||
        (handlers.listener && handlers.listener === listener) ||
        (handlers._origin && handlers._origin === listener)) {
        if(this.wildcard) {
          delete leaf._listeners;
        }
        else {
          delete this._events[type];
        }
      }
    }

    return this;
  };

  EventEmitter.prototype.offAny = function(fn) {
    var i = 0, l = 0, fns;
    if (fn && this._all && this._all.length > 0) {
      fns = this._all;
      for(i = 0, l = fns.length; i < l; i++) {
        if(fn === fns[i]) {
          fns.splice(i, 1);
          return this;
        }
      }
    } else {
      this._all = [];
    }
    return this;
  };

  EventEmitter.prototype.removeListener = EventEmitter.prototype.off;

  EventEmitter.prototype.removeAllListeners = function(type) {
    if (arguments.length === 0) {
      !this._events || init.call(this);
      return this;
    }

    if(this.wildcard) {
      var ns = typeof type === 'string' ? type.split(this.delimiter) : type.slice();
      var leafs = searchListenerTree.call(this, null, ns, this.listenerTree, 0);

      for (var iLeaf=0; iLeaf<leafs.length; iLeaf++) {
        var leaf = leafs[iLeaf];
        leaf._listeners = null;
      }
    }
    else {
      if (!this._events[type]) return this;
      this._events[type] = null;
    }
    return this;
  };

  EventEmitter.prototype.listeners = function(type) {
    if(this.wildcard) {
      var handlers = [];
      var ns = typeof type === 'string' ? type.split(this.delimiter) : type.slice();
      searchListenerTree.call(this, handlers, ns, this.listenerTree, 0);
      return handlers;
    }

    this._events || init.call(this);

    if (!this._events[type]) this._events[type] = [];
    if (!isArray(this._events[type])) {
      this._events[type] = [this._events[type]];
    }
    return this._events[type];
  };

  EventEmitter.prototype.listenersAny = function() {

    if(this._all) {
      return this._all;
    }
    else {
      return [];
    }

  };

  if (typeof define === 'function' && define.amd) {
     // AMD. Register as an anonymous module.
    define(function() {
      return EventEmitter;
    });
  } else if (typeof exports === 'object') {
    // CommonJS
    exports.EventEmitter2 = EventEmitter;
  }
  else {
    // Browser global.
    window.EventEmitter2 = EventEmitter;
  }
}();

},{}],4:[function(_dereq_,module,exports){
(function() {
  var Delta, InsertOp, Op, RetainOp, jsdiff, _;

  _ = _dereq_('lodash');

  jsdiff = _dereq_('diff');

  Op = _dereq_('./op');

  InsertOp = _dereq_('./insert');

  RetainOp = _dereq_('./retain');

  Delta = (function() {
    var _insertInsertCase, _retainRetainCase;

    Delta.getIdentity = function(length) {
      return new Delta(length, length, [new RetainOp(0, length)]);
    };

    Delta.getInitial = function(contents) {
      return new Delta(0, contents.length, [new InsertOp(contents)]);
    };

    Delta.isDelta = function(delta) {
      var op, _i, _len, _ref;
      if ((delta != null) && typeof delta === "object" && typeof delta.startLength === "number" && typeof delta.endLength === "number" && typeof delta.ops === "object") {
        _ref = delta.ops;
        for (_i = 0, _len = _ref.length; _i < _len; _i++) {
          op = _ref[_i];
          if (!(Op.isRetain(op) || Op.isInsert(op))) {
            return false;
          }
        }
        return true;
      }
      return false;
    };

    Delta.makeDelta = function(obj) {
      return new Delta(obj.startLength, obj.endLength, _.map(obj.ops, function(op) {
        if (Op.isInsert(op)) {
          return new InsertOp(op.value, op.attributes);
        } else if (Op.isRetain(op)) {
          return new RetainOp(op.start, op.end, op.attributes);
        } else {
          return null;
        }
      }));
    };

    Delta.makeDeleteDelta = function(startLength, index, length) {
      var ops;
      ops = [];
      if (0 < index) {
        ops.push(new RetainOp(0, index));
      }
      if (index + length < startLength) {
        ops.push(new RetainOp(index + length, startLength));
      }
      return new Delta(startLength, ops);
    };

    Delta.makeInsertDelta = function(startLength, index, value, attributes) {
      var ops;
      ops = [new InsertOp(value, attributes)];
      if (0 < index) {
        ops.unshift(new RetainOp(0, index));
      }
      if (index < startLength) {
        ops.push(new RetainOp(index, startLength));
      }
      return new Delta(startLength, ops);
    };

    Delta.makeRetainDelta = function(startLength, index, length, attributes) {
      var ops;
      ops = [new RetainOp(index, index + length, attributes)];
      if (0 < index) {
        ops.unshift(new RetainOp(0, index));
      }
      if (index + length < startLength) {
        ops.push(new RetainOp(index + length, startLength));
      }
      return new Delta(startLength, ops);
    };

    function Delta(startLength, endLength, ops) {
      var length;
      this.startLength = startLength;
      this.endLength = endLength;
      this.ops = ops;
      if (this.ops == null) {
        this.ops = this.endLength;
        this.endLength = null;
      }
      this.ops = _.map(this.ops, function(op) {
        if (Op.isRetain(op)) {
          return op;
        } else if (Op.isInsert(op)) {
          return op;
        } else {
          throw new Error("Creating delta with invalid op. Expecting an insert or retain.");
        }
      });
      this.compact();
      length = _.reduce(this.ops, function(count, op) {
        return count + op.getLength();
      }, 0);
      if ((this.endLength != null) && length !== this.endLength) {
        throw new Error("Expecting end length of " + length);
      } else {
        this.endLength = length;
      }
    }

    Delta.prototype.apply = function(insertFn, deleteFn, applyAttrFn, context) {
      var index, offset, retains;
      if (insertFn == null) {
        insertFn = (function() {});
      }
      if (deleteFn == null) {
        deleteFn = (function() {});
      }
      if (applyAttrFn == null) {
        applyAttrFn = (function() {});
      }
      if (context == null) {
        context = null;
      }
      if (this.isIdentity()) {
        return;
      }
      index = 0;
      offset = 0;
      retains = [];
      _.each(this.ops, (function(_this) {
        return function(op) {
          if (Op.isInsert(op)) {
            insertFn.call(context, index + offset, op.value, op.attributes);
            return offset += op.getLength();
          } else if (Op.isRetain(op)) {
            if (op.start > index) {
              deleteFn.call(context, index + offset, op.start - index);
              offset -= op.start - index;
            }
            retains.push(new RetainOp(op.start + offset, op.end + offset, op.attributes));
            return index = op.end;
          }
        };
      })(this));
      if (this.endLength < this.startLength + offset) {
        deleteFn.call(context, this.endLength, this.startLength + offset - this.endLength);
      }
      return _.each(retains, (function(_this) {
        return function(op) {
          _.each(op.attributes, function(value, format) {
            if (value === null) {
              return applyAttrFn.call(context, op.start, op.end - op.start, format, value);
            }
          });
          return _.each(op.attributes, function(value, format) {
            if (value != null) {
              return applyAttrFn.call(context, op.start, op.end - op.start, format, value);
            }
          });
        };
      })(this));
    };

    Delta.prototype.applyToText = function(text) {
      var appliedText, delta, op, result, _i, _len, _ref;
      delta = this;
      if (text.length !== delta.startLength) {
        throw new Error("Start length of delta: " + delta.startLength + " is not equal to the text: " + text.length);
      }
      appliedText = [];
      _ref = delta.ops;
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        op = _ref[_i];
        if (Op.isInsert(op)) {
          appliedText.push(op.value);
        } else {
          appliedText.push(text.substring(op.start, op.end));
        }
      }
      result = appliedText.join("");
      if (delta.endLength !== result.length) {
        throw new Error("End length of delta: " + delta.endLength + " is not equal to result text: " + result.length);
      }
      return result;
    };

    Delta.prototype.canCompose = function(delta) {
      return Delta.isDelta(delta) && this.endLength === delta.startLength;
    };

    Delta.prototype.compact = function() {
      var compacted;
      compacted = [];
      _.each(this.ops, function(op) {
        var last;
        if (op.getLength() === 0) {
          return;
        }
        if (compacted.length === 0) {
          return compacted.push(op);
        } else {
          last = _.last(compacted);
          if (Op.isInsert(last) && Op.isInsert(op) && last.attributesMatch(op)) {
            return compacted[compacted.length - 1] = new InsertOp(last.value + op.value, op.attributes);
          } else if (Op.isRetain(last) && Op.isRetain(op) && last.end === op.start && last.attributesMatch(op)) {
            return compacted[compacted.length - 1] = new RetainOp(last.start, op.end, op.attributes);
          } else {
            return compacted.push(op);
          }
        }
      });
      return this.ops = compacted;
    };

    Delta.prototype.compose = function(deltaB) {
      var composed, deltaA, opInB, opsInRange, _i, _len, _ref;
      if (!this.canCompose(deltaB)) {
        throw new Error('Cannot compose delta');
      }
      deltaA = this;
      composed = [];
      _ref = deltaB.ops;
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        opInB = _ref[_i];
        if (Op.isInsert(opInB)) {
          composed.push(opInB);
        } else if (Op.isRetain(opInB)) {
          opsInRange = deltaA.getOpsAt(opInB.start, opInB.getLength());
          opsInRange = _.map(opsInRange, function(opInA) {
            if (Op.isInsert(opInA)) {
              return new InsertOp(opInA.value, opInA.composeAttributes(opInB.attributes));
            } else {
              return new RetainOp(opInA.start, opInA.end, opInA.composeAttributes(opInB.attributes));
            }
          });
          composed = composed.concat(opsInRange);
        } else {
          throw new Error('Invalid op in deltaB when composing');
        }
      }
      return new Delta(deltaA.startLength, deltaB.endLength, composed);
    };

    Delta.prototype.decompose = function(deltaA) {
      var decomposeAttributes, deltaB, deltaC, insertDelta, offset, ops;
      deltaC = this;
      if (!Delta.isDelta(deltaA)) {
        throw new Error("Decompose called when deltaA is not a Delta, type: " + typeof deltaA);
      }
      if (deltaA.startLength !== this.startLength) {
        throw new Error("startLength " + deltaA.startLength + " / startLength " + this.startLength + " mismatch");
      }
      if (!_.all(deltaA.ops, (function(op) {
        return Op.isInsert(op);
      }))) {
        throw new Error("DeltaA has retain in decompose");
      }
      if (!_.all(deltaC.ops, (function(op) {
        return Op.isInsert(op);
      }))) {
        throw new Error("DeltaC has retain in decompose");
      }
      decomposeAttributes = function(attrA, attrC) {
        var decomposedAttributes, key, value;
        decomposedAttributes = {};
        for (key in attrC) {
          value = attrC[key];
          if (attrA[key] === void 0 || attrA[key] !== value) {
            if (attrA[key] !== null && typeof attrA[key] === 'object' && value !== null && typeof value === 'object') {
              decomposedAttributes[key] = decomposeAttributes(attrA[key], value);
            } else {
              decomposedAttributes[key] = value;
            }
          }
        }
        for (key in attrA) {
          value = attrA[key];
          if (attrC[key] === void 0) {
            decomposedAttributes[key] = null;
          }
        }
        return decomposedAttributes;
      };
      insertDelta = deltaA.diff(deltaC);
      ops = [];
      offset = 0;
      _.each(insertDelta.ops, function(op) {
        var offsetC, opsInC;
        opsInC = deltaC.getOpsAt(offset, op.getLength());
        offsetC = 0;
        _.each(opsInC, function(opInC) {
          var d, offsetA, opsInA;
          if (Op.isInsert(op)) {
            d = new InsertOp(op.value.substring(offsetC, offsetC + opInC.getLength()), opInC.attributes);
            ops.push(d);
          } else if (Op.isRetain(op)) {
            opsInA = deltaA.getOpsAt(op.start + offsetC, opInC.getLength());
            offsetA = 0;
            _.each(opsInA, function(opInA) {
              var attributes, e, start;
              attributes = decomposeAttributes(opInA.attributes, opInC.attributes);
              start = op.start + offsetA + offsetC;
              e = new RetainOp(start, start + opInA.getLength(), attributes);
              ops.push(e);
              return offsetA += opInA.getLength();
            });
          } else {
            throw new Error("Invalid delta in deltaB when composing");
          }
          return offsetC += opInC.getLength();
        });
        return offset += op.getLength();
      });
      deltaB = new Delta(insertDelta.startLength, insertDelta.endLength, ops);
      return deltaB;
    };

    Delta.prototype.diff = function(other) {
      var diff, finalLength, insertDelta, ops, originalLength, textA, textC, _ref;
      _ref = _.map([this, other], function(delta) {
        return _.map(delta.ops, function(op) {
          if (op.value != null) {
            return op.value;
          } else {
            return "";
          }
        }).join('');
      }), textA = _ref[0], textC = _ref[1];
      if (!(textA === '' && textC === '')) {
        diff = jsdiff.diffChars(textA, textC);
        if (diff.length <= 0) {
          throw new Error("diffToDelta called with diff with length <= 0");
        }
        originalLength = 0;
        finalLength = 0;
        ops = [];
        _.each(diff, function(part) {
          if (part.added) {
            ops.push(new InsertOp(part.value));
            return finalLength += part.value.length;
          } else if (part.removed) {
            return originalLength += part.value.length;
          } else {
            ops.push(new RetainOp(originalLength, originalLength + part.value.length));
            originalLength += part.value.length;
            return finalLength += part.value.length;
          }
        });
        insertDelta = new Delta(originalLength, finalLength, ops);
      } else {
        insertDelta = new Delta(0, 0, []);
      }
      return insertDelta;
    };

    _insertInsertCase = function(elemA, elemB, indexes, aIsRemote) {
      var length, results;
      results = _.extend({}, indexes);
      length = Math.min(elemA.getLength(), elemB.getLength());
      if (aIsRemote) {
        results.transformOp = new RetainOp(results.indexA, results.indexA + length);
        results.indexA += length;
        if (length === elemA.getLength()) {
          results.elemIndexA++;
        } else if (length < elemA.getLength()) {
          results.elemA = _.last(elemA.split(length));
        } else {
          throw new Error("Invalid elem length in transform");
        }
      } else {
        results.transformOp = _.first(elemB.split(length));
        results.indexB += length;
        if (length === elemB.getLength()) {
          results.elemIndexB++;
        } else {
          results.elemB = _.last(elemB.split(length));
        }
      }
      return results;
    };

    _retainRetainCase = function(elemA, elemB, indexes) {
      var addedAttributes, elemIndexA, elemIndexB, errMsg, indexA, indexB, length, results;
      indexA = indexes.indexA, indexB = indexes.indexB, elemIndexA = indexes.elemIndexA, elemIndexB = indexes.elemIndexB;
      results = _.extend({}, indexes);
      if (elemA.end < elemB.start) {
        results.indexA += elemA.getLength();
        results.elemIndexA++;
      } else if (elemB.end < elemA.start) {
        results.indexB += elemB.getLength();
        results.elemIndexB++;
      } else {
        if (elemA.start < elemB.start) {
          results.indexA += elemB.start - elemA.start;
          elemA = results.elemA = new RetainOp(elemB.start, elemA.end, elemA.attributes);
        } else if (elemB.start < elemA.start) {
          results.indexB += elemA.start - elemB.start;
          elemB = results.elemB = new RetainOp(elemA.start, elemB.end, elemB.attributes);
        }
        errMsg = "RetainOps must have same start length in transform";
        if (elemA.start !== elemB.start) {
          throw new Error(errMsg);
        }
        length = Math.min(elemA.end, elemB.end) - elemA.start;
        addedAttributes = elemA.addAttributes(elemB.attributes);
        results.transformOp = new RetainOp(results.indexA, results.indexA + length, addedAttributes);
        results.indexA += length;
        results.indexB += length;
        if (elemA.end === elemB.end) {
          results.elemIndexA++;
          results.elemIndexB++;
        } else if (elemA.end < elemB.end) {
          results.elemIndexA++;
          results.elemB = _.last(elemB.split(length));
        } else {
          results.elemIndexB++;
          results.elemA = _.last(elemA.split(length));
        }
      }
      if (results.elemIndexA !== indexes.elemIndexA) {
        results.elemA = null;
      }
      if (results.elemIndexB !== indexes.elemIndexB) {
        results.elemB = null;
      }
      return results;
    };

    Delta.prototype.transform = function(deltaA, aIsRemote) {
      var deltaB, elemA, elemB, elemIndexA, elemIndexB, errMsg, indexA, indexB, results, transformEndLength, transformOps, transformStartLength, _applyResults, _buildIndexes;
      if (aIsRemote == null) {
        aIsRemote = false;
      }
      if (!Delta.isDelta(deltaA)) {
        errMsg = "Transform called when deltaA is not a Delta, type: ";
        throw new Error(errMsg + typeof deltaA);
      }
      deltaA = new Delta(deltaA.startLength, deltaA.endLength, deltaA.ops);
      deltaB = new Delta(this.startLength, this.endLength, this.ops);
      transformOps = [];
      indexA = indexB = 0;
      elemIndexA = elemIndexB = 0;
      _applyResults = function(results) {
        if (results.indexA != null) {
          indexA = results.indexA;
        }
        if (results.indexB != null) {
          indexB = results.indexB;
        }
        if (results.elemIndexA != null) {
          elemIndexA = results.elemIndexA;
        }
        if (results.elemIndexB != null) {
          elemIndexB = results.elemIndexB;
        }
        if (results.elemA != null) {
          deltaA.ops[elemIndexA] = results.elemA;
        }
        if (results.elemB != null) {
          deltaB.ops[elemIndexB] = results.elemB;
        }
        if (results.transformOp != null) {
          return transformOps.push(results.transformOp);
        }
      };
      _buildIndexes = function() {
        return {
          indexA: indexA,
          indexB: indexB,
          elemIndexA: elemIndexA,
          elemIndexB: elemIndexB
        };
      };
      while (elemIndexA < deltaA.ops.length && elemIndexB < deltaB.ops.length) {
        elemA = deltaA.ops[elemIndexA];
        elemB = deltaB.ops[elemIndexB];
        if (Op.isInsert(elemA) && Op.isInsert(elemB)) {
          results = _insertInsertCase(elemA, elemB, _buildIndexes(), aIsRemote);
          _applyResults(results);
        } else if (Op.isRetain(elemA) && Op.isRetain(elemB)) {
          results = _retainRetainCase(elemA, elemB, _buildIndexes());
          _applyResults(results);
        } else if (Op.isInsert(elemA) && Op.isRetain(elemB)) {
          transformOps.push(new RetainOp(indexA, indexA + elemA.getLength()));
          indexA += elemA.getLength();
          elemIndexA++;
        } else if (Op.isRetain(elemA) && Op.isInsert(elemB)) {
          transformOps.push(elemB);
          indexB += elemB.getLength();
          elemIndexB++;
        }
      }
      while (elemIndexA < deltaA.ops.length) {
        elemA = deltaA.ops[elemIndexA];
        if (Op.isInsert(elemA)) {
          transformOps.push(new RetainOp(indexA, indexA + elemA.getLength()));
        }
        indexA += elemA.getLength();
        elemIndexA++;
      }
      while (elemIndexB < deltaB.ops.length) {
        elemB = deltaB.ops[elemIndexB];
        if (Op.isInsert(elemB)) {
          transformOps.push(elemB);
        }
        indexB += elemB.getLength();
        elemIndexB++;
      }
      transformStartLength = deltaA.endLength;
      transformEndLength = _.reduce(transformOps, function(transformEndLength, op) {
        return transformEndLength + op.getLength();
      }, 0);
      return new Delta(transformStartLength, transformEndLength, transformOps);
    };

    Delta.prototype.getOpsAt = function(index, length) {
      var changes, getLength, offset, op, opLength, start, _i, _len, _ref;
      changes = [];
      if ((this.savedOpOffset != null) && this.savedOpOffset < index) {
        offset = this.savedOpOffset;
      } else {
        offset = this.savedOpOffset = this.savedOpIndex = 0;
      }
      _ref = this.ops.slice(this.savedOpIndex);
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        op = _ref[_i];
        if (offset >= index + length) {
          break;
        }
        opLength = op.getLength();
        if (index < offset + opLength) {
          start = Math.max(index - offset, 0);
          getLength = Math.min(opLength - start, index + length - offset - start);
          changes.push(op.getAt(start, getLength));
        }
        offset += opLength;
        this.savedOpIndex += 1;
        this.savedOpOffset += opLength;
      }
      return changes;
    };

    Delta.prototype.invert = function(deltaB) {
      var deltaA, deltaC, inverse;
      if (!this.isInsertsOnly()) {
        throw new Error("Invert called on invalid delta containing non-insert ops");
      }
      deltaA = this;
      deltaC = deltaA.compose(deltaB);
      inverse = deltaA.decompose(deltaC);
      return inverse;
    };

    Delta.prototype.isEqual = function(other) {
      if (!other) {
        return false;
      }
      if (this.startLength !== other.startLength || this.endLength !== other.endLength) {
        return false;
      }
      if (!_.isArray(other.ops) || this.ops.length !== other.ops.length) {
        return false;
      }
      return _.all(this.ops, function(op, i) {
        return op.isEqual(other.ops[i]);
      });
    };

    Delta.prototype.isIdentity = function() {
      var index, op, _i, _len, _ref;
      if (this.startLength === this.endLength) {
        if (this.ops.length === 0) {
          return true;
        }
        index = 0;
        _ref = this.ops;
        for (_i = 0, _len = _ref.length; _i < _len; _i++) {
          op = _ref[_i];
          if (!Op.isRetain(op)) {
            return false;
          }
          if (op.start !== index) {
            return false;
          }
          if (!(op.numAttributes() === 0 || (op.numAttributes() === 1 && _.has(op.attributes, 'authorId')))) {
            return false;
          }
          index = op.end;
        }
        if (index !== this.endLength) {
          return false;
        }
        return true;
      }
      return false;
    };

    Delta.prototype.isInsertsOnly = function() {
      return _.every(this.ops, function(op) {
        return Op.isInsert(op);
      });
    };

    Delta.prototype.merge = function(other) {
      var ops;
      ops = _.map(other.ops, (function(_this) {
        return function(op) {
          if (Op.isRetain(op)) {
            return new RetainOp(op.start + _this.startLength, op.end + _this.startLength, op.attributes);
          } else {
            return op;
          }
        };
      })(this));
      ops = this.ops.concat(ops);
      return new Delta(this.startLength + other.startLength, ops);
    };

    Delta.prototype.split = function(index) {
      var leftOps, rightOps;
      if (!this.isInsertsOnly()) {
        throw new Error("Split only implemented for inserts only");
      }
      if (!(0 <= index && index <= this.endLength)) {
        throw new Error("Split at invalid index");
      }
      leftOps = [];
      rightOps = [];
      _.reduce(this.ops, function(offset, op) {
        var left, right, _ref;
        if (offset + op.getLength() <= index) {
          leftOps.push(op);
        } else if (offset >= index) {
          rightOps.push(op);
        } else {
          _ref = op.split(index - offset), left = _ref[0], right = _ref[1];
          leftOps.push(left);
          rightOps.push(right);
        }
        return offset + op.getLength();
      }, 0);
      return [new Delta(0, leftOps), new Delta(0, rightOps)];
    };

    Delta.prototype.toString = function() {
      return "{(" + this.startLength + "->" + this.endLength + ") [" + (this.ops.join(', ')) + "]}";
    };

    return Delta;

  })();

  module.exports = Delta;

}).call(this);

},{"./insert":6,"./op":7,"./retain":8,"diff":11,"lodash":"M4+//f"}],5:[function(_dereq_,module,exports){
(function() {
  var Delta, DeltaGenerator, InsertOp, RetainOp, getUtils, setDomain, _, _domain;

  _ = _dereq_('lodash');

  Delta = _dereq_('./delta');

  InsertOp = _dereq_('./insert');

  RetainOp = _dereq_('./retain');

  _domain = {
    alphabet: "abcdefghijklmnopqrstuvwxyz\n\n\n\n  ",
    booleanAttributes: {
      'bold': [true, false],
      'italic': [true, false],
      'strike': [true, false]
    },
    nonBooleanAttributes: {
      'back-color': ['white', 'black', 'red', 'blue', 'lime', 'teal', 'magenta', 'yellow'],
      'fore-color': ['white', 'black', 'red', 'blue', 'lime', 'teal', 'magenta', 'yellow'],
      'font-name': ['monospace', 'serif'],
      'font-size': ['huge', 'large', 'small']
    },
    defaultAttributeValue: {
      'back-color': 'white',
      'fore-color': 'black',
      'font-name': 'san-serif',
      'font-size': 'normal'
    }
  };

  setDomain = function(domain) {
    if (domain != null) {
      return _domain = domain;
    }
  };

  getUtils = function(domain) {
    domain = domain || _domain;
    if (domain == null) {
      throw new Error("Must provide DeltaGenerator with a domain.");
    }
    if (domain.alphabet == null) {
      throw new Error("Domain must define alphabet.");
    }
    if (domain.booleanAttributes == null) {
      throw new Error("Domain must define booleanAttributes.");
    }
    if (domain.nonBooleanAttributes == null) {
      throw new Error("Domain must define nonBooleanAttributes.");
    }
    if (domain.defaultAttributeValue == null) {
      throw new Error("Domain must define defaultAttributeValue.");
    }
    return {
      getDomain: function(domain) {
        return _domain;
      },
      getRandomString: function(length) {
        var _i, _ref, _results;
        return _.map((function() {
          _results = [];
          for (var _i = 0, _ref = length - 1; 0 <= _ref ? _i <= _ref : _i >= _ref; 0 <= _ref ? _i++ : _i--){ _results.push(_i); }
          return _results;
        }).apply(this), function() {
          return domain.alphabet[_.random(0, domain.alphabet.length - 1)];
        }).join('');
      },
      getRandomLength: function() {
        var rand;
        rand = Math.random();
        if (rand < 0.6) {
          return _.random(1, 2);
        } else if (rand < 0.8) {
          return _.random(3, 4);
        } else if (rand < 0.9) {
          return _.random(5, 9);
        } else {
          return _.random(10, 50);
        }
      },
      insertAt: function(delta, insertionPoint, insertions) {
        var charIndex, head, op, opIndex, tail, _i, _len, _ref, _ref1;
        charIndex = opIndex = 0;
        _ref = delta.ops;
        for (_i = 0, _len = _ref.length; _i < _len; _i++) {
          op = _ref[_i];
          if (charIndex === insertionPoint) {
            break;
          }
          if (insertionPoint < charIndex + op.getLength()) {
            _ref1 = op.split(insertionPoint - charIndex), head = _ref1[0], tail = _ref1[1];
            delta.ops.splice(opIndex, 1, head, tail);
            opIndex++;
            break;
          }
          charIndex += op.getLength();
          opIndex++;
        }
        delta.ops.splice(opIndex, 0, new InsertOp(insertions));
        delta.endLength += insertions.length;
        return delta.compact();
      },
      deleteAt: function(delta, deletionPoint, numToDelete) {
        var charIndex, curDelete, head, newText, op, ops, reachedDeletionPoint, tail, _i, _len, _ref;
        charIndex = 0;
        ops = [];
        _ref = delta.ops;
        for (_i = 0, _len = _ref.length; _i < _len; _i++) {
          op = _ref[_i];
          reachedDeletionPoint = charIndex === deletionPoint || deletionPoint < charIndex + op.getLength();
          if (numToDelete > 0 && reachedDeletionPoint) {
            curDelete = Math.min(numToDelete, op.getLength() - (deletionPoint - charIndex));
            numToDelete -= curDelete;
            if (InsertOp.isInsert(op)) {
              newText = op.value.substring(0, deletionPoint - charIndex) + op.value.substring(deletionPoint - charIndex + curDelete);
              if (newText.length > 0) {
                ops.push(new InsertOp(newText));
              }
            } else {
              if (!RetainOp.isRetain(op)) {
                throw new Error("Expected retain but got " + op);
              }
              head = new RetainOp(op.start, op.start + deletionPoint - charIndex, _.clone(op.attributes));
              tail = new RetainOp(op.start + deletionPoint - charIndex + curDelete, op.end, _.clone(op.attributes));
              if (head.start < head.end) {
                ops.push(head);
              }
              if (tail.start < tail.end) {
                ops.push(tail);
              }
            }
            deletionPoint += curDelete;
          } else {
            ops.push(op);
          }
          charIndex += op.getLength();
        }
        delta.ops = ops;
        return delta.endLength = _.reduce(ops, function(length, op) {
          return length + op.getLength();
        }, 0);
      },
      formatAt: function(delta, formatPoint, numToFormat, attrs, reference) {
        var attr, charIndex, cur, curFormat, head, op, ops, reachedFormatPoint, tail, _formatBooleanAttribute, _formatNonBooleanAttribute, _i, _j, _len, _len1, _limitScope, _ref, _ref1, _splitOpInThree;
        _splitOpInThree = function(elem, splitAt, length, reference) {
          var cur, curStr, head, headStr, marker, newCur, op, origOps, tail, tailStr, _i, _len;
          if (InsertOp.isInsert(elem)) {
            headStr = elem.value.substring(0, splitAt);
            head = new InsertOp(headStr, _.clone(elem.attributes));
            curStr = elem.value.substring(splitAt, splitAt + length);
            cur = new InsertOp(curStr, _.clone(elem.attributes));
            tailStr = elem.value.substring(splitAt + length);
            tail = new InsertOp(tailStr, _.clone(elem.attributes));
            if (curStr.indexOf('\n') !== -1) {
              newCur = curStr.substring(0, curStr.indexOf('\n'));
              tailStr = curStr.substring(curStr.indexOf('\n')) + tailStr;
              cur = new InsertOp(newCur, _.clone(elem.attributes));
              tail = new InsertOp(tailStr, _.clone(elem.attributes));
            }
          } else {
            if (!RetainOp.isRetain(elem)) {
              throw new Error("Expected retain but got " + elem);
            }
            head = new RetainOp(elem.start, elem.start + splitAt, _.clone(elem.attributes));
            cur = new RetainOp(head.end, head.end + length, _.clone(elem.attributes));
            tail = new RetainOp(cur.end, elem.end, _.clone(elem.attributes));
            origOps = reference.getOpsAt(cur.start, cur.getLength());
            if (!_.every(origOps, function(op) {
              return InsertOp.isInsert(op);
            })) {
              throw new Error("Non insert op in backref");
            }
            marker = cur.start;
            for (_i = 0, _len = origOps.length; _i < _len; _i++) {
              op = origOps[_i];
              if (InsertOp.isInsert(op)) {
                if (op.value.indexOf('\n') !== -1) {
                  cur = new RetainOp(cur.start, marker + op.value.indexOf('\n'), _.clone(cur.attributes));
                  tail = new RetainOp(marker + op.value.indexOf('\n'), tail.end, _.clone(tail.attributes));
                  break;
                } else {
                  marker += op.getLength();
                }
              } else {
                throw new Error("Got retainOp in reference delta!");
              }
            }
          }
          return [head, cur, tail];
        };
        _limitScope = function(op, tail, attr, referenceOps) {
          var length, refOp, val, _i, _len, _results;
          length = 0;
          val = referenceOps[0].attributes[attr];
          _results = [];
          for (_i = 0, _len = referenceOps.length; _i < _len; _i++) {
            refOp = referenceOps[_i];
            if (refOp.attributes[attr] !== val) {
              op.end = op.start + length;
              tail.start = op.end;
              break;
            } else {
              _results.push(length += refOp.getLength());
            }
          }
          return _results;
        };
        _formatBooleanAttribute = function(op, tail, attr, reference) {
          var referenceOps;
          if (InsertOp.isInsert(op)) {
            if (op.attributes[attr] != null) {
              return delete op.attributes[attr];
            } else {
              return op.attributes[attr] = true;
            }
          } else {
            if (!RetainOp.isRetain(op)) {
              throw new Error("Expected retain but got " + op);
            }
            if (op.attributes[attr] != null) {
              return delete op.attributes[attr];
            } else {
              referenceOps = reference.getOpsAt(op.start, op.getLength());
              if (!_.every(referenceOps, function(op) {
                return InsertOp.isInsert(op);
              })) {
                throw new Error("Formatting a retain that does not refer to an insert.");
              }
              if (referenceOps.length > 0) {
                _limitScope(op, tail, attr, referenceOps);
                if (referenceOps[0].attributes[attr] != null) {
                  if (!referenceOps[0].attributes[attr]) {
                    throw new Error("Boolean attribute on reference delta should only be true!");
                  }
                  return op.attributes[attr] = null;
                } else {
                  return op.attributes[attr] = true;
                }
              }
            }
          }
        };
        _formatNonBooleanAttribute = (function(_this) {
          return function(op, tail, attr, reference) {
            var getNewAttrVal, referenceOps;
            getNewAttrVal = function(prevVal) {
              if (prevVal != null) {
                return _.first(_.shuffle(_.without(domain.nonBooleanAttributes[attr], prevVal)));
              } else {
                return _.first(_.shuffle(_.without(domain.nonBooleanAttributes[attr], domain.defaultAttributeValue[attr])));
              }
            };
            if (InsertOp.isInsert(op)) {
              return op.attributes[attr] = getNewAttrVal(attr, op.attributes[attr]);
            } else {
              if (!RetainOp.isRetain(op)) {
                throw new Error("Expected retain but got " + op);
              }
              referenceOps = reference.getOpsAt(op.start, op.getLength());
              if (!_.every(referenceOps, function(op) {
                return InsertOp.isInsert(op);
              })) {
                throw new Error("Formatting a retain that does not refer to an insert.");
              }
              if (referenceOps.length > 0) {
                _limitScope(op, tail, attr, referenceOps);
                if ((op.attributes[attr] != null) && Math.random() < 0.5) {
                  return delete op.attributes[attr];
                } else {
                  return op.attributes[attr] = getNewAttrVal(op.attributes[attr]);
                }
              }
            }
          };
        })(this);
        charIndex = 0;
        ops = [];
        _ref = delta.ops;
        for (_i = 0, _len = _ref.length; _i < _len; _i++) {
          op = _ref[_i];
          reachedFormatPoint = charIndex === formatPoint || charIndex + op.getLength() > formatPoint;
          if (numToFormat > 0 && reachedFormatPoint) {
            curFormat = Math.min(numToFormat, op.getLength() - (formatPoint - charIndex));
            numToFormat -= curFormat;
            _ref1 = _splitOpInThree(op, formatPoint - charIndex, curFormat, reference), head = _ref1[0], cur = _ref1[1], tail = _ref1[2];
            ops.push(head);
            ops.push(cur);
            ops.push(tail);
            for (_j = 0, _len1 = attrs.length; _j < _len1; _j++) {
              attr = attrs[_j];
              if (_.has(domain.booleanAttributes, attr)) {
                _formatBooleanAttribute(cur, tail, attr, reference);
              } else if (_.has(domain.nonBooleanAttributes, attr)) {
                _formatNonBooleanAttribute(cur, tail, attr, reference);
              } else {
                throw new Error("Received unknown attribute: " + attr);
              }
            }
            formatPoint += curFormat;
          } else {
            ops.push(op);
          }
          charIndex += op.getLength();
        }
        delta.endLength = _.reduce(ops, function(length, delta) {
          return length + delta.getLength();
        }, 0);
        delta.ops = ops;
        return delta.compact();
      },
      addRandomOp: function(newDelta, referenceDelta) {
        var attrs, finalIndex, numAttrs, opIndex, opLength, rand, shuffled_attrs;
        finalIndex = referenceDelta.endLength - 1;
        opIndex = _.random(0, finalIndex);
        rand = Math.random();
        if (rand < 0.5) {
          opLength = this.getRandomLength();
          this.insertAt(newDelta, opIndex, this.getRandomString(opLength));
        } else if (rand < 0.75) {
          if (referenceDelta.endLength <= 1) {
            return newDelta;
          }
          opIndex = _.random(0, finalIndex - 1);
          opLength = _.random(1, finalIndex - opIndex);
          this.deleteAt(newDelta, opIndex, opLength);
        } else {
          shuffled_attrs = _.shuffle(_.keys(domain.booleanAttributes).concat(_.keys(domain.nonBooleanAttributes)));
          numAttrs = _.random(1, shuffled_attrs.length);
          attrs = shuffled_attrs.slice(0, numAttrs);
          opLength = _.random(1, finalIndex - opIndex);
          this.formatAt(newDelta, opIndex, opLength, attrs, referenceDelta);
        }
        return newDelta;
      },
      getRandomDelta: function(referenceDelta, numOps) {
        var i, newDelta, _i;
        newDelta = new Delta(referenceDelta.endLength, referenceDelta.endLength, [new RetainOp(0, referenceDelta.endLength)]);
        numOps || (numOps = _.random(1, 10));
        for (i = _i = 0; 0 <= numOps ? _i < numOps : _i > numOps; i = 0 <= numOps ? ++_i : --_i) {
          this.addRandomOp(newDelta, referenceDelta);
        }
        return newDelta;
      }
    };
  };

  DeltaGenerator = {
    setDomain: setDomain,
    getUtils: getUtils
  };

  module.exports = DeltaGenerator;

}).call(this);

},{"./delta":4,"./insert":6,"./retain":8,"lodash":"M4+//f"}],6:[function(_dereq_,module,exports){
(function() {
  var InsertOp, Op, _,
    __hasProp = {}.hasOwnProperty,
    __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

  _ = _dereq_('lodash');

  Op = _dereq_('./op');

  InsertOp = (function(_super) {
    __extends(InsertOp, _super);

    function InsertOp(value, attributes) {
      this.value = value;
      if (attributes == null) {
        attributes = {};
      }
      this.attributes = _.clone(attributes);
    }

    InsertOp.prototype.getAt = function(start, length) {
      return new InsertOp(this.value.substr(start, length), this.attributes);
    };

    InsertOp.prototype.getLength = function() {
      return this.value.length;
    };

    InsertOp.prototype.isEqual = function(other) {
      return (other != null) && this.value === other.value && _.isEqual(this.attributes, other.attributes);
    };

    InsertOp.prototype.join = function(other) {
      if (_.isEqual(this.attributes, other.attributes)) {
        return new InsertOp(this.value + second.value, this.attributes);
      } else {
        throw Error;
      }
    };

    InsertOp.prototype.split = function(offset) {
      var left, right;
      left = new InsertOp(this.value.substr(0, offset), this.attributes);
      right = new InsertOp(this.value.substr(offset), this.attributes);
      return [left, right];
    };

    InsertOp.prototype.toString = function() {
      return "{" + this.value + ", " + (this.printAttributes()) + "}";
    };

    return InsertOp;

  })(Op);

  module.exports = InsertOp;

}).call(this);

},{"./op":7,"lodash":"M4+//f"}],7:[function(_dereq_,module,exports){
(function() {
  var Op, _;

  _ = _dereq_('lodash');

  Op = (function() {
    Op.isInsert = function(i) {
      return (i != null) && typeof i.value === "string";
    };

    Op.isRetain = function(r) {
      return (r != null) && typeof r.start === "number" && typeof r.end === "number";
    };

    function Op(attributes) {
      if (attributes == null) {
        attributes = {};
      }
      this.attributes = _.clone(attributes);
    }

    Op.prototype.addAttributes = function(attributes) {
      var addedAttributes, key, value;
      addedAttributes = {};
      for (key in attributes) {
        value = attributes[key];
        if (this.attributes[key] === void 0) {
          addedAttributes[key] = value;
        }
      }
      return addedAttributes;
    };

    Op.prototype.attributesMatch = function(other) {
      var otherAttributes;
      otherAttributes = other.attributes || {};
      return _.isEqual(this.attributes, otherAttributes);
    };

    Op.prototype.composeAttributes = function(attributes) {
      var resolveAttributes;
      resolveAttributes = (function(_this) {
        return function(oldAttrs, newAttrs) {
          var key, resolvedAttrs, value;
          if (!newAttrs) {
            return oldAttrs;
          }
          resolvedAttrs = _.clone(oldAttrs);
          for (key in newAttrs) {
            value = newAttrs[key];
            if (Op.isInsert(_this) && value === null) {
              delete resolvedAttrs[key];
            } else if (typeof value !== 'undefined') {
              if (typeof resolvedAttrs[key] === 'object' && typeof value === 'object' && _.all([resolvedAttrs[key], newAttrs[key]], (function(val) {
                return val !== null;
              }))) {
                resolvedAttrs[key] = resolveAttributes(resolvedAttrs[key], value);
              } else {
                resolvedAttrs[key] = value;
              }
            }
          }
          return resolvedAttrs;
        };
      })(this);
      return resolveAttributes(this.attributes, attributes);
    };

    Op.prototype.numAttributes = function() {
      return _.keys(this.attributes).length;
    };

    Op.prototype.printAttributes = function() {
      return JSON.stringify(this.attributes);
    };

    return Op;

  })();

  module.exports = Op;

}).call(this);

},{"lodash":"M4+//f"}],8:[function(_dereq_,module,exports){
(function() {
  var Op, RetainOp, _,
    __hasProp = {}.hasOwnProperty,
    __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

  _ = _dereq_('lodash');

  Op = _dereq_('./op');

  RetainOp = (function(_super) {
    __extends(RetainOp, _super);

    function RetainOp(start, end, attributes) {
      this.start = start;
      this.end = end;
      if (attributes == null) {
        attributes = {};
      }
      this.attributes = _.clone(attributes);
    }

    RetainOp.prototype.getAt = function(start, length) {
      return new RetainOp(this.start + start, this.start + start + length, this.attributes);
    };

    RetainOp.prototype.getLength = function() {
      return this.end - this.start;
    };

    RetainOp.prototype.isEqual = function(other) {
      return (other != null) && this.start === other.start && this.end === other.end && _.isEqual(this.attributes, other.attributes);
    };

    RetainOp.prototype.split = function(offset) {
      var left, right;
      left = new RetainOp(this.start, this.start + offset, this.attributes);
      right = new RetainOp(this.start + offset, this.end, this.attributes);
      return [left, right];
    };

    RetainOp.prototype.toString = function() {
      return "{{" + this.start + " - " + this.end + "), " + (this.printAttributes()) + "}";
    };

    return RetainOp;

  })(Op);

  module.exports = RetainOp;

}).call(this);

},{"./op":7,"lodash":"M4+//f"}],9:[function(_dereq_,module,exports){
(function() {
  module.exports = {
    Delta: _dereq_('./delta'),
    DeltaGen: _dereq_('./delta_generator'),
    Op: _dereq_('./op'),
    InsertOp: _dereq_('./insert'),
    RetainOp: _dereq_('./retain')
  };

}).call(this);

},{"./delta":4,"./delta_generator":5,"./insert":6,"./op":7,"./retain":8}],10:[function(_dereq_,module,exports){
module.exports = _dereq_('./build/tandem-core')

},{"./build/tandem-core":9}],11:[function(_dereq_,module,exports){
/* See LICENSE file for terms of use */

/*
 * Text diff implementation.
 *
 * This library supports the following APIS:
 * JsDiff.diffChars: Character by character diff
 * JsDiff.diffWords: Word (as defined by \b regex) diff which ignores whitespace
 * JsDiff.diffLines: Line based diff
 *
 * JsDiff.diffCss: Diff targeted at CSS content
 *
 * These methods are based on the implementation proposed in
 * "An O(ND) Difference Algorithm and its Variations" (Myers, 1986).
 * http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.4.6927
 */
var JsDiff = (function() {
  /*jshint maxparams: 5*/
  function clonePath(path) {
    return { newPos: path.newPos, components: path.components.slice(0) };
  }
  function removeEmpty(array) {
    var ret = [];
    for (var i = 0; i < array.length; i++) {
      if (array[i]) {
        ret.push(array[i]);
      }
    }
    return ret;
  }
  function escapeHTML(s) {
    var n = s;
    n = n.replace(/&/g, '&amp;');
    n = n.replace(/</g, '&lt;');
    n = n.replace(/>/g, '&gt;');
    n = n.replace(/"/g, '&quot;');

    return n;
  }

  var Diff = function(ignoreWhitespace) {
    this.ignoreWhitespace = ignoreWhitespace;
  };
  Diff.prototype = {
      diff: function(oldString, newString) {
        // Handle the identity case (this is due to unrolling editLength == 0
        if (newString === oldString) {
          return [{ value: newString }];
        }
        if (!newString) {
          return [{ value: oldString, removed: true }];
        }
        if (!oldString) {
          return [{ value: newString, added: true }];
        }

        newString = this.tokenize(newString);
        oldString = this.tokenize(oldString);

        var newLen = newString.length, oldLen = oldString.length;
        var maxEditLength = newLen + oldLen;
        var bestPath = [{ newPos: -1, components: [] }];

        // Seed editLength = 0
        var oldPos = this.extractCommon(bestPath[0], newString, oldString, 0);
        if (bestPath[0].newPos+1 >= newLen && oldPos+1 >= oldLen) {
          return bestPath[0].components;
        }

        for (var editLength = 1; editLength <= maxEditLength; editLength++) {
          for (var diagonalPath = -1*editLength; diagonalPath <= editLength; diagonalPath+=2) {
            var basePath;
            var addPath = bestPath[diagonalPath-1],
                removePath = bestPath[diagonalPath+1];
            oldPos = (removePath ? removePath.newPos : 0) - diagonalPath;
            if (addPath) {
              // No one else is going to attempt to use this value, clear it
              bestPath[diagonalPath-1] = undefined;
            }

            var canAdd = addPath && addPath.newPos+1 < newLen;
            var canRemove = removePath && 0 <= oldPos && oldPos < oldLen;
            if (!canAdd && !canRemove) {
              bestPath[diagonalPath] = undefined;
              continue;
            }

            // Select the diagonal that we want to branch from. We select the prior
            // path whose position in the new string is the farthest from the origin
            // and does not pass the bounds of the diff graph
            if (!canAdd || (canRemove && addPath.newPos < removePath.newPos)) {
              basePath = clonePath(removePath);
              this.pushComponent(basePath.components, oldString[oldPos], undefined, true);
            } else {
              basePath = clonePath(addPath);
              basePath.newPos++;
              this.pushComponent(basePath.components, newString[basePath.newPos], true, undefined);
            }

            var oldPos = this.extractCommon(basePath, newString, oldString, diagonalPath);

            if (basePath.newPos+1 >= newLen && oldPos+1 >= oldLen) {
              return basePath.components;
            } else {
              bestPath[diagonalPath] = basePath;
            }
          }
        }
      },

      pushComponent: function(components, value, added, removed) {
        var last = components[components.length-1];
        if (last && last.added === added && last.removed === removed) {
          // We need to clone here as the component clone operation is just
          // as shallow array clone
          components[components.length-1] =
            {value: this.join(last.value, value), added: added, removed: removed };
        } else {
          components.push({value: value, added: added, removed: removed });
        }
      },
      extractCommon: function(basePath, newString, oldString, diagonalPath) {
        var newLen = newString.length,
            oldLen = oldString.length,
            newPos = basePath.newPos,
            oldPos = newPos - diagonalPath;
        while (newPos+1 < newLen && oldPos+1 < oldLen && this.equals(newString[newPos+1], oldString[oldPos+1])) {
          newPos++;
          oldPos++;

          this.pushComponent(basePath.components, newString[newPos], undefined, undefined);
        }
        basePath.newPos = newPos;
        return oldPos;
      },

      equals: function(left, right) {
        var reWhitespace = /\S/;
        if (this.ignoreWhitespace && !reWhitespace.test(left) && !reWhitespace.test(right)) {
          return true;
        } else {
          return left === right;
        }
      },
      join: function(left, right) {
        return left + right;
      },
      tokenize: function(value) {
        return value;
      }
  };

  var CharDiff = new Diff();

  var WordDiff = new Diff(true);
  var WordWithSpaceDiff = new Diff();
  WordDiff.tokenize = WordWithSpaceDiff.tokenize = function(value) {
    return removeEmpty(value.split(/(\s+|\b)/));
  };

  var CssDiff = new Diff(true);
  CssDiff.tokenize = function(value) {
    return removeEmpty(value.split(/([{}:;,]|\s+)/));
  };

  var LineDiff = new Diff();
  LineDiff.tokenize = function(value) {
    var retLines = [],
        lines = value.split(/^/m);

    for(var i = 0; i < lines.length; i++) {
      var line = lines[i],
          lastLine = lines[i - 1];

      // Merge lines that may contain windows new lines
      if (line == '\n' && lastLine && lastLine[lastLine.length - 1] === '\r') {
        retLines[retLines.length - 1] += '\n';
      } else if (line) {
        retLines.push(line);
      }
    }

    return retLines;
  };

  return {
    Diff: Diff,

    diffChars: function(oldStr, newStr) { return CharDiff.diff(oldStr, newStr); },
    diffWords: function(oldStr, newStr) { return WordDiff.diff(oldStr, newStr); },
    diffWordsWithSpace: function(oldStr, newStr) { return WordWithSpaceDiff.diff(oldStr, newStr); },
    diffLines: function(oldStr, newStr) { return LineDiff.diff(oldStr, newStr); },

    diffCss: function(oldStr, newStr) { return CssDiff.diff(oldStr, newStr); },

    createPatch: function(fileName, oldStr, newStr, oldHeader, newHeader) {
      var ret = [];

      ret.push('Index: ' + fileName);
      ret.push('===================================================================');
      ret.push('--- ' + fileName + (typeof oldHeader === 'undefined' ? '' : '\t' + oldHeader));
      ret.push('+++ ' + fileName + (typeof newHeader === 'undefined' ? '' : '\t' + newHeader));

      var diff = LineDiff.diff(oldStr, newStr);
      if (!diff[diff.length-1].value) {
        diff.pop();   // Remove trailing newline add
      }
      diff.push({value: '', lines: []});   // Append an empty value to make cleanup easier

      function contextLines(lines) {
        return lines.map(function(entry) { return ' ' + entry; });
      }
      function eofNL(curRange, i, current) {
        var last = diff[diff.length-2],
            isLast = i === diff.length-2,
            isLastOfType = i === diff.length-3 && (current.added !== last.added || current.removed !== last.removed);

        // Figure out if this is the last line for the given file and missing NL
        if (!/\n$/.test(current.value) && (isLast || isLastOfType)) {
          curRange.push('\\ No newline at end of file');
        }
      }

      var oldRangeStart = 0, newRangeStart = 0, curRange = [],
          oldLine = 1, newLine = 1;
      for (var i = 0; i < diff.length; i++) {
        var current = diff[i],
            lines = current.lines || current.value.replace(/\n$/, '').split('\n');
        current.lines = lines;

        if (current.added || current.removed) {
          if (!oldRangeStart) {
            var prev = diff[i-1];
            oldRangeStart = oldLine;
            newRangeStart = newLine;

            if (prev) {
              curRange = contextLines(prev.lines.slice(-4));
              oldRangeStart -= curRange.length;
              newRangeStart -= curRange.length;
            }
          }
          curRange.push.apply(curRange, lines.map(function(entry) { return (current.added?'+':'-') + entry; }));
          eofNL(curRange, i, current);

          if (current.added) {
            newLine += lines.length;
          } else {
            oldLine += lines.length;
          }
        } else {
          if (oldRangeStart) {
            // Close out any changes that have been output (or join overlapping)
            if (lines.length <= 8 && i < diff.length-2) {
              // Overlapping
              curRange.push.apply(curRange, contextLines(lines));
            } else {
              // end the range and output
              var contextSize = Math.min(lines.length, 4);
              ret.push(
                  '@@ -' + oldRangeStart + ',' + (oldLine-oldRangeStart+contextSize)
                  + ' +' + newRangeStart + ',' + (newLine-newRangeStart+contextSize)
                  + ' @@');
              ret.push.apply(ret, curRange);
              ret.push.apply(ret, contextLines(lines.slice(0, contextSize)));
              if (lines.length <= 4) {
                eofNL(ret, i, current);
              }

              oldRangeStart = 0;  newRangeStart = 0; curRange = [];
            }
          }
          oldLine += lines.length;
          newLine += lines.length;
        }
      }

      return ret.join('\n') + '\n';
    },

    applyPatch: function(oldStr, uniDiff) {
      var diffstr = uniDiff.split('\n');
      var diff = [];
      var remEOFNL = false,
          addEOFNL = false;

      for (var i = (diffstr[0][0]==='I'?4:0); i < diffstr.length; i++) {
        if(diffstr[i][0] === '@') {
          var meh = diffstr[i].split(/@@ -(\d+),(\d+) \+(\d+),(\d+) @@/);
          diff.unshift({
            start:meh[3],
            oldlength:meh[2],
            oldlines:[],
            newlength:meh[4],
            newlines:[]
          });
        } else if(diffstr[i][0] === '+') {
          diff[0].newlines.push(diffstr[i].substr(1));
        } else if(diffstr[i][0] === '-') {
          diff[0].oldlines.push(diffstr[i].substr(1));
        } else if(diffstr[i][0] === ' ') {
          diff[0].newlines.push(diffstr[i].substr(1));
          diff[0].oldlines.push(diffstr[i].substr(1));
        } else if(diffstr[i][0] === '\\') {
          if (diffstr[i-1][0] === '+') {
            remEOFNL = true;
          } else if(diffstr[i-1][0] === '-') {
            addEOFNL = true;
          }
        }
      }

      var str = oldStr.split('\n');
      for (var i = diff.length - 1; i >= 0; i--) {
        var d = diff[i];
        for (var j = 0; j < d.oldlength; j++) {
          if(str[d.start-1+j] !== d.oldlines[j]) {
            return false;
          }
        }
        Array.prototype.splice.apply(str,[d.start-1,+d.oldlength].concat(d.newlines));
      }

      if (remEOFNL) {
        while (!str[str.length-1]) {
          str.pop();
        }
      } else if (addEOFNL) {
        str.push('');
      }
      return str.join('\n');
    },

    convertChangesToXML: function(changes){
      var ret = [];
      for ( var i = 0; i < changes.length; i++) {
        var change = changes[i];
        if (change.added) {
          ret.push('<ins>');
        } else if (change.removed) {
          ret.push('<del>');
        }

        ret.push(escapeHTML(change.value));

        if (change.added) {
          ret.push('</ins>');
        } else if (change.removed) {
          ret.push('</del>');
        }
      }
      return ret.join('');
    },

    // See: http://code.google.com/p/google-diff-match-patch/wiki/API
    convertChangesToDMP: function(changes){
      var ret = [], change;
      for ( var i = 0; i < changes.length; i++) {
        change = changes[i];
        ret.push([(change.added ? 1 : change.removed ? -1 : 0), change.value]);
      }
      return ret;
    }
  };
})();

if (typeof module !== 'undefined') {
    module.exports = JsDiff;
}

},{}],12:[function(_dereq_,module,exports){
module.exports={
  "name": "quilljs",
  "version": "0.17.6",
  "description": "Cross browser rich text editor",
  "author": "Jason Chen <jhchen7@gmail.com>",
  "homepage": "http://quilljs.com",
  "contributors": [
    "Byron Milligan <byronner@gmail.com>",
    "Keegan Poppen <keegan.poppen@gmail.com>"
  ],
  "main": "index.js",
  "dependencies": {
    "eventemitter2": "~0.4.13",
    "lodash": "~2.4.1",
    "tandem-core": "~0.6.2"
  },
  "devDependencies": {
    "async": "~0.9.0",
    "coffee-script": "~1.8.0",
    "coffeeify": "~0.7.0",
    "glob": "~4.0.4",
    "grunt": "~0.4.3",
    "grunt-browserify": "~2.1.0",
    "grunt-contrib-clean": "~0.6.0",
    "grunt-contrib-coffee": "~0.11.0",
    "grunt-contrib-compress": "~0.10.0",
    "grunt-contrib-concat": "~0.5.0",
    "grunt-contrib-connect": "~0.8.0",
    "grunt-contrib-copy": "~0.5.0",
    "grunt-contrib-stylus": "~0.18.0",
    "grunt-contrib-uglify": "~0.5.0",
    "grunt-karma": "~0.9.0",
    "grunt-lodash": "~0.3.0",
    "grunt-protractor-runner": "~1.1.0",
    "grunt-sauce-connect-launcher": "~0.3.0",
    "harp": "~0.13.0",
    "istanbul": "~0.3.0",
    "jquery": "~2.1.1",
    "karma": "~0.12.0",
    "karma-chrome-launcher": "~0.1.2",
    "karma-coffee-preprocessor": "~0.2.1",
    "karma-coverage": "~0.2.0",
    "karma-firefox-launcher": "~0.1.3",
    "karma-html2js-preprocessor": "~0.1.0",
    "karma-jasmine": "~0.2.0",
    "karma-phantomjs-launcher": "~0.1.2",
    "karma-safari-launcher": "~0.1.1",
    "karma-sauce-launcher": "~0.2.2",
    "load-grunt-tasks": "~0.6.0",
    "protractor": "~1.1.1",
    "stylus": "~0.48.1",
    "watchify": "~0.10.2"
  },
  "engines": {
    "node": ">=0.10"
  },
  "license": "BSD-3-Clause",
  "repository": {
    "type": "git",
    "url": "https://github.com/quilljs/quill"
  },
  "bugs": {
    "url": "https://github.com/quilljs/quill/issues"
  },
  "scripts": {
    "prepublish": "grunt coffee:quill",
    "postpublish": "grunt clean:coffee",
    "test": "grunt test"
  },
  "keywords": [
    "editor",
    "rich text",
    "wysiwyg"
  ]
}

},{}],13:[function(_dereq_,module,exports){
var Document, Format, Line, LinkedList, Normalizer, Tandem, dom, _;

_ = _dereq_('lodash');

dom = _dereq_('../lib/dom');

Format = _dereq_('./format');

Line = _dereq_('./line');

LinkedList = _dereq_('../lib/linked-list');

Normalizer = _dereq_('../lib/normalizer');

Tandem = _dereq_('tandem-core');

Document = (function() {
  function Document(root, options) {
    this.root = root;
    if (options == null) {
      options = {};
    }
    this.formats = {};
    _.each(options.formats, _.bind(this.addFormat, this));
    this.setHTML(this.root.innerHTML);
  }

  Document.prototype.addFormat = function(name, config) {
    if (!_.isObject(config)) {
      config = Format.FORMATS[name];
    }
    if (this.formats[name] != null) {
      console.warn('Overwriting format', name, this.formats[name]);
    }
    return this.formats[name] = new Format(this.root.ownerDocument, config);
  };

  Document.prototype.appendLine = function(lineNode) {
    return this.insertLineBefore(lineNode, null);
  };

  Document.prototype.findLeafAt = function(index, inclusive) {
    var line, offset, _ref;
    _ref = this.findLineAt(index), line = _ref[0], offset = _ref[1];
    if (line != null) {
      return line.findLeafAt(offset, inclusive);
    } else {
      return [null, offset];
    }
  };

  Document.prototype.findLine = function(node) {
    var line;
    while ((node != null) && (dom.BLOCK_TAGS[node.tagName] == null)) {
      node = node.parentNode;
    }
    line = node != null ? this.lineMap[node.id] : null;
    if ((line != null ? line.node : void 0) === node) {
      return line;
    } else {
      return null;
    }
  };

  Document.prototype.findLineAt = function(index) {
    var curLine, length;
    if (!(this.lines.length > 0)) {
      return [null, index];
    }
    length = this.toDelta().endLength;
    if (index === length) {
      return [this.lines.last, this.lines.last.length];
    }
    if (index > length) {
      return [null, index - length];
    }
    curLine = this.lines.first;
    while (curLine != null) {
      if (index < curLine.length) {
        return [curLine, index];
      }
      index -= curLine.length;
      curLine = curLine.next;
    }
    return [null, index];
  };

  Document.prototype.insertLineBefore = function(newLineNode, refLine) {
    var line;
    line = new Line(this, newLineNode);
    if (refLine != null) {
      if (!dom(newLineNode.parentNode).isElement()) {
        this.root.insertBefore(newLineNode, refLine.node);
      }
      this.lines.insertAfter(refLine.prev, line);
    } else {
      if (!dom(newLineNode.parentNode).isElement()) {
        this.root.appendChild(newLineNode);
      }
      this.lines.append(line);
    }
    this.lineMap[line.id] = line;
    return line;
  };

  Document.prototype.mergeLines = function(line, lineToMerge) {
    if (lineToMerge.length > 1) {
      if (line.length === 1) {
        dom(line.leaves.last.node).remove();
      }
      _.each(dom(lineToMerge.node).childNodes(), function(child) {
        if (child.tagName !== dom.DEFAULT_BREAK_TAG) {
          return line.node.appendChild(child);
        }
      });
    }
    this.removeLine(lineToMerge);
    return line.rebuild();
  };

  Document.prototype.optimizeLines = function() {
    return _.each(this.lines.toArray(), function(line, i) {
      line.optimize();
      return true;
    });
  };

  Document.prototype.rebuild = function() {
    var lineNode, lines, _results;
    lines = this.lines.toArray();
    lineNode = this.root.firstChild;
    if ((lineNode != null) && (dom.LIST_TAGS[lineNode.tagName] != null)) {
      lineNode = lineNode.firstChild;
    }
    _.each(lines, (function(_this) {
      return function(line, index) {
        var newLine, _ref;
        while (line.node !== lineNode) {
          if (line.node.parentNode === _this.root || ((_ref = line.node.parentNode) != null ? _ref.parentNode : void 0) === _this.root) {
            lineNode = Normalizer.normalizeLine(lineNode);
            newLine = _this.insertLineBefore(lineNode, line);
            lineNode = dom(lineNode).nextLineNode(_this.root);
          } else {
            return _this.removeLine(line);
          }
        }
        if (line.outerHTML !== lineNode.outerHTML) {
          line.node = Normalizer.normalizeLine(line.node);
          line.rebuild();
        }
        return lineNode = dom(lineNode).nextLineNode(_this.root);
      };
    })(this));
    _results = [];
    while (lineNode != null) {
      lineNode = Normalizer.normalizeLine(lineNode);
      this.appendLine(lineNode);
      _results.push(lineNode = dom(lineNode).nextLineNode(this.root));
    }
    return _results;
  };

  Document.prototype.removeLine = function(line) {
    if (line.node.parentNode != null) {
      if (dom.LIST_TAGS[line.node.parentNode.tagName] && line.node.parentNode.childNodes.length === 1) {
        dom(line.node.parentNode).remove();
      } else {
        dom(line.node).remove();
      }
    }
    delete this.lineMap[line.id];
    return this.lines.remove(line);
  };

  Document.prototype.setHTML = function(html) {
    html = Normalizer.stripComments(html);
    html = Normalizer.stripWhitespace(html);
    this.root.innerHTML = html;
    this.lines = new LinkedList();
    this.lineMap = {};
    return this.rebuild();
  };

  Document.prototype.splitLine = function(line, offset) {
    var lineNode1, lineNode2, newLine, _ref;
    offset = Math.min(offset, line.length - 1);
    _ref = dom(line.node).split(offset, true), lineNode1 = _ref[0], lineNode2 = _ref[1];
    line.node = lineNode1;
    line.rebuild();
    newLine = this.insertLineBefore(lineNode2, line.next);
    newLine.formats = _.clone(line.formats);
    newLine.resetContent();
    return newLine;
  };

  Document.prototype.toDelta = function() {
    var lines, ops;
    lines = this.lines.toArray();
    ops = _.flatten(_.map(lines, function(line) {
      return _.clone(line.delta.ops);
    }), true);
    return new Tandem.Delta(0, ops);
  };

  return Document;

})();

module.exports = Document;



},{"../lib/dom":22,"../lib/linked-list":23,"../lib/normalizer":24,"./format":15,"./line":17,"lodash":"M4+//f","tandem-core":10}],14:[function(_dereq_,module,exports){
var Document, Editor, Line, Renderer, Selection, Tandem, dom, _;

_ = _dereq_('lodash');

dom = _dereq_('../lib/dom');

Document = _dereq_('./document');

Line = _dereq_('./line');

Renderer = _dereq_('./renderer');

Selection = _dereq_('./selection');

Tandem = _dereq_('tandem-core');

Editor = (function() {
  function Editor(iframeContainer, quill, options) {
    this.iframeContainer = iframeContainer;
    this.quill = quill;
    this.options = options != null ? options : {};
    this.renderer = new Renderer(this.iframeContainer, this.options);
    dom(this.iframeContainer).on('focus', this.focus.bind(this));
    this.root = this.renderer.root;
    this.doc = new Document(this.root, this.options);
    this.delta = this.doc.toDelta();
    this.selection = new Selection(this.doc, this.renderer.iframe, this.quill);
    this.timer = setInterval(_.bind(this.checkUpdate, this), this.options.pollInterval);
    this.quill.on(this.quill.constructor.events.SELECTION_CHANGE, (function(_this) {
      return function(range) {
        return _this.savedRange = range;
      };
    })(this));
    if (!this.options.readOnly) {
      this.enable();
    }
  }

  Editor.prototype.disable = function() {
    return this.enable(false);
  };

  Editor.prototype.enable = function(enabled) {
    if (enabled == null) {
      enabled = true;
    }
    return this.root.setAttribute('contenteditable', enabled);
  };

  Editor.prototype.applyDelta = function(delta, source) {
    var localDelta, tempDelta;
    localDelta = this._update();
    if (localDelta) {
      tempDelta = localDelta;
      localDelta = localDelta.transform(delta, true);
      delta = delta.transform(tempDelta, false);
      this.delta = this.doc.toDelta();
    }
    if (!delta.isIdentity()) {
      if (delta.startLength !== this.delta.endLength) {
        console.warn("Trying to apply delta to incorrect doc length", delta, this.delta);
      }
      delta = this._trackDelta((function(_this) {
        return function() {
          delta.apply(_this._insertAt, _this._deleteAt, _this._formatAt, _this);
          return _this.selection.shiftAfter(0, 0, _.bind(_this.doc.optimizeLines, _this.doc));
        };
      })(this));
      this.delta = this.doc.toDelta();
      this.innerHTML = this.root.innerHTML;
      if (delta && source !== 'silent') {
        this.quill.emit(this.quill.constructor.events.TEXT_CHANGE, delta, source);
      }
    }
    if (localDelta && !localDelta.isIdentity() && source !== 'silent') {
      return this.quill.emit(this.quill.constructor.events.TEXT_CHANGE, localDelta, 'user');
    }
  };

  Editor.prototype.checkUpdate = function(source) {
    var delta, oldDelta;
    if (source == null) {
      source = 'user';
    }
    if ((this.renderer.iframe.parentNode == null) || (this.root.parentNode == null)) {
      return clearInterval(this.timer);
    }
    delta = this._update();
    if (delta) {
      oldDelta = this.delta;
      this.delta = oldDelta.compose(delta);
      this.quill.emit(this.quill.constructor.events.TEXT_CHANGE, delta, source);
    }
    if (delta) {
      source = 'silent';
    }
    return this.selection.update(source);
  };

  Editor.prototype.focus = function() {
    if (dom.isIE(11)) {
      this.selection.setRange(this.selection.range);
    }
    if (dom.isIOS()) {
      this.renderer.iframe.focus();
    }
    return this.root.focus();
  };

  Editor.prototype.getDelta = function() {
    return this.delta;
  };

  Editor.prototype._deleteAt = function(index, length) {
    if (length <= 0) {
      return;
    }
    return this.selection.shiftAfter(index, -1 * length, (function(_this) {
      return function() {
        var curLine, deleteLength, firstLine, mergeFirstLine, nextLine, offset, _ref;
        _ref = _this.doc.findLineAt(index), firstLine = _ref[0], offset = _ref[1];
        curLine = firstLine;
        mergeFirstLine = firstLine.length - offset <= length && offset > 0;
        while ((curLine != null) && length > 0) {
          nextLine = curLine.next;
          deleteLength = Math.min(curLine.length - offset, length);
          if (offset === 0 && length >= curLine.length) {
            _this.doc.removeLine(curLine);
          } else {
            curLine.deleteText(offset, deleteLength);
          }
          length -= deleteLength;
          curLine = nextLine;
          offset = 0;
        }
        if (mergeFirstLine && firstLine.next) {
          return _this.doc.mergeLines(firstLine, firstLine.next);
        }
      };
    })(this));
  };

  Editor.prototype._formatAt = function(index, length, name, value) {
    return this.selection.shiftAfter(index, 0, (function(_this) {
      return function() {
        var formatLength, line, offset, _ref, _results;
        _ref = _this.doc.findLineAt(index), line = _ref[0], offset = _ref[1];
        _results = [];
        while ((line != null) && length > 0) {
          formatLength = Math.min(length, line.length - offset - 1);
          line.formatText(offset, formatLength, name, value);
          length -= formatLength;
          if (length > 0) {
            line.format(name, value);
          }
          length -= 1;
          offset = 0;
          _results.push(line = line.next);
        }
        return _results;
      };
    })(this));
  };

  Editor.prototype._insertAt = function(index, text, formatting) {
    if (formatting == null) {
      formatting = {};
    }
    return this.selection.shiftAfter(index, text.length, (function(_this) {
      return function() {
        var line, lineTexts, offset, _ref;
        text = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
        lineTexts = text.split('\n');
        _ref = _this.doc.findLineAt(index), line = _ref[0], offset = _ref[1];
        return _.each(lineTexts, function(lineText, i) {
          var nextLine;
          if ((line == null) || line.length <= offset) {
            if (i < lineTexts.length - 1 || lineText.length > 0) {
              line = _this.doc.appendLine(_this.root.ownerDocument.createElement(dom.DEFAULT_BLOCK_TAG));
              offset = 0;
              line.insertText(offset, lineText, formatting);
              line.format(formatting);
              nextLine = null;
            }
          } else {
            line.insertText(offset, lineText, formatting);
            if (i < lineTexts.length - 1) {
              nextLine = _this.doc.splitLine(line, offset + lineText.length);
              _.each(_.defaults({}, formatting, line.formats), function(value, format) {
                return line.format(format, formatting[format]);
              });
              offset = 0;
            }
          }
          return line = nextLine;
        });
      };
    })(this));
  };

  Editor.prototype._trackDelta = function(fn) {
    var decompose, decomposeA, decomposeB, decomposeLeft, decomposeRight, ignored, lengthA, lengthB, newDelta, newIndex, newLeftDelta, newRightDelta, oldIndex, oldLeftDelta, oldRightDelta, _ref, _ref1, _ref2, _ref3, _ref4;
    oldIndex = (_ref = this.savedRange) != null ? _ref.start : void 0;
    fn();
    newDelta = this.doc.toDelta();
    try {
      newIndex = (_ref1 = this.selection.getRange()) != null ? _ref1.start : void 0;
      if ((oldIndex != null) && (newIndex != null) && oldIndex <= this.delta.endLength && newIndex <= newDelta.endLength) {
        _ref2 = this.delta.split(oldIndex), oldLeftDelta = _ref2[0], oldRightDelta = _ref2[1];
        _ref3 = newDelta.split(newIndex), newLeftDelta = _ref3[0], newRightDelta = _ref3[1];
        decomposeLeft = newLeftDelta.decompose(oldLeftDelta);
        decomposeRight = newRightDelta.decompose(oldRightDelta);
        decomposeA = decomposeLeft.merge(decomposeRight);
      }
    } catch (_error) {
      ignored = _error;
    }
    decomposeB = newDelta.decompose(this.delta);
    if (decomposeA && decomposeB) {
      _ref4 = _.map([decomposeA, decomposeB], function(delta) {
        return _.reduce(delta.ops, function(count, op) {
          if (op.value != null) {
            count += op.value.length;
          }
          return count;
        }, 0);
      }), lengthA = _ref4[0], lengthB = _ref4[1];
      decompose = lengthA < lengthA ? decomposeA : decomposeB;
    } else {
      decompose = decomposeA || decomposeB;
    }
    return decompose;
  };

  Editor.prototype._update = function() {
    var delta;
    if (this.innerHTML === this.root.innerHTML) {
      return false;
    }
    delta = this._trackDelta((function(_this) {
      return function() {
        _this.selection.preserve(_.bind(_this.doc.rebuild, _this.doc));
        return _this.selection.shiftAfter(0, 0, _.bind(_this.doc.optimizeLines, _this.doc));
      };
    })(this));
    this.innerHTML = this.root.innerHTML;
    if (delta.isIdentity()) {
      return false;
    } else {
      return delta;
    }
  };

  return Editor;

})();

module.exports = Editor;



},{"../lib/dom":22,"./document":13,"./line":17,"./renderer":18,"./selection":19,"lodash":"M4+//f","tandem-core":10}],15:[function(_dereq_,module,exports){
var Format, dom, _;

_ = _dereq_('lodash');

dom = _dereq_('../lib/dom');

Format = (function() {
  Format.types = {
    LINE: 'line'
  };

  Format.FORMATS = {
    bold: {
      tag: 'B',
      prepare: 'bold'
    },
    italic: {
      tag: 'I',
      prepare: 'italic'
    },
    underline: {
      tag: 'U',
      prepare: 'underline'
    },
    strike: {
      tag: 'S',
      prepare: 'strikeThrough'
    },
    color: {
      style: 'color',
      "default": 'rgb(0, 0, 0)',
      prepare: 'foreColor'
    },
    background: {
      style: 'backgroundColor',
      "default": 'rgb(255, 255, 255)',
      prepare: 'backColor'
    },
    font: {
      style: 'fontFamily',
      "default": "'Helvetica', 'Arial', sans-serif",
      prepare: 'fontName'
    },
    size: {
      style: 'fontSize',
      "default": '13px',
      prepare: function(doc, value) {
        return doc.execCommand('fontSize', false, dom.convertFontSize(value));
      }
    },
    link: {
      tag: 'A',
      attribute: 'href'
    },
    image: {
      tag: 'IMG',
      attribute: 'src'
    },
    align: {
      type: Format.types.LINE,
      style: 'textAlign',
      "default": 'left'
    },
    bullet: {
      type: Format.types.LINE,
      exclude: 'list',
      parentTag: 'UL',
      tag: 'LI'
    },
    list: {
      type: Format.types.LINE,
      exclude: 'bullet',
      parentTag: 'OL',
      tag: 'LI'
    }
  };

  function Format(document, config) {
    this.document = document;
    this.config = config;
  }

  Format.prototype.add = function(node, value) {
    var formatNode, inline, parentNode, _ref, _ref1;
    if (!value) {
      return this.remove(node);
    }
    if (this.value(node) === value) {
      return node;
    }
    if (_.isString(this.config.parentTag)) {
      parentNode = this.document.createElement(this.config.parentTag);
      dom(node).wrap(parentNode);
      if (node.parentNode.tagName === ((_ref = node.parentNode.previousSibling) != null ? _ref.tagName : void 0)) {
        dom(node.parentNode.previousSibling).merge(node.parentNode);
      }
      if (node.parentNode.tagName === ((_ref1 = node.parentNode.nextSibling) != null ? _ref1.tagName : void 0)) {
        dom(node.parentNode).merge(node.parentNode.nextSibling);
      }
    }
    if (_.isString(this.config.tag)) {
      formatNode = this.document.createElement(this.config.tag);
      if (dom.VOID_TAGS[formatNode.tagName] != null) {
        if (node.parentNode != null) {
          dom(node).replace(formatNode);
        }
        node = formatNode;
      } else if (this.isType(Format.types.LINE)) {
        node = dom(node).switchTag(this.config.tag);
      } else {
        dom(node).wrap(formatNode);
        node = formatNode;
      }
    }
    if (_.isString(this.config.style) || _.isString(this.config.attribute) || _.isString(this.config["class"])) {
      if (_.isString(this.config["class"])) {
        node = this.remove(node);
      }
      if (dom(node).isTextNode()) {
        inline = this.document.createElement(dom.DEFAULT_INLINE_TAG);
        dom(node).wrap(inline);
        node = inline;
      }
      if (_.isString(this.config.style)) {
        if (value !== this.config["default"]) {
          node.style[this.config.style] = value;
        }
      }
      if (_.isString(this.config.attribute)) {
        node.setAttribute(this.config.attribute, value);
      }
      if (_.isString(this.config["class"])) {
        dom(node).addClass(this.config["class"] + value);
      }
    }
    return node;
  };

  Format.prototype.isType = function(type) {
    return type === this.config.type;
  };

  Format.prototype.match = function(node) {
    var c, _i, _len, _ref, _ref1;
    if (!dom(node).isElement()) {
      return false;
    }
    if (_.isString(this.config.parentTag) && ((_ref = node.parentNode) != null ? _ref.tagName : void 0) !== this.config.parentTag) {
      return false;
    }
    if (_.isString(this.config.tag) && node.tagName !== this.config.tag) {
      return false;
    }
    if (_.isString(this.config.style) && (!node.style[this.config.style] || node.style[this.config.style] === this.config["default"])) {
      return false;
    }
    if (_.isString(this.config.attribute) && !node.hasAttribute(this.config.attribute)) {
      return false;
    }
    if (_.isString(this.config["class"])) {
      _ref1 = dom(node).classes();
      for (_i = 0, _len = _ref1.length; _i < _len; _i++) {
        c = _ref1[_i];
        if (c.indexOf(this.config["class"]) === 0) {
          return true;
        }
      }
      return false;
    }
    return true;
  };

  Format.prototype.prepare = function(value) {
    if (_.isString(this.config.prepare)) {
      return this.document.execCommand(this.config.prepare, false, value);
    } else if (_.isFunction(this.config.prepare)) {
      return this.config.prepare(this.document, value);
    }
  };

  Format.prototype.remove = function(node) {
    var c, _i, _len, _ref;
    if (!this.match(node)) {
      return node;
    }
    if (_.isString(this.config.style)) {
      node.style[this.config.style] = '';
      if (!node.getAttribute('style')) {
        node.removeAttribute('style');
      }
    }
    if (_.isString(this.config.attribute)) {
      node.removeAttribute(this.config.attribute);
    }
    if (_.isString(this.config["class"])) {
      _ref = dom(node).classes();
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        c = _ref[_i];
        if (c.indexOf(this.config["class"]) === 0) {
          dom(node).removeClass(c);
        }
      }
      if (!node.getAttribute('class')) {
        node.removeAttribute('class');
      }
    }
    if (_.isString(this.config.tag)) {
      if (this.isType(Format.types.LINE)) {
        if (_.isString(this.config.parentTag)) {
          if (node.previousSibling != null) {
            dom(node).splitAncestors(node.parentNode.parentNode);
          }
          if (node.nextSibling != null) {
            dom(node.nextSibling).splitAncestors(node.parentNode.parentNode);
          }
        }
        node = dom(node).switchTag(dom.DEFAULT_BLOCK_TAG);
      } else {
        node = dom(node).switchTag(dom.DEFAULT_INLINE_TAG);
        if (dom.EMBED_TAGS[this.config.tag] != null) {
          dom(node).text(dom.EMBED_TEXT);
        }
      }
    }
    if (_.isString(this.config.parentTag)) {
      dom(node.parentNode).unwrap();
    }
    if (node.tagName === dom.DEFAULT_INLINE_TAG && !node.hasAttributes()) {
      node = dom(node).unwrap();
    }
    return node;
  };

  Format.prototype.value = function(node) {
    var c, _i, _len, _ref;
    if (!this.match(node)) {
      return void 0;
    }
    if (_.isString(this.config.attribute)) {
      return node.getAttribute(this.config.attribute) || void 0;
    } else if (_.isString(this.config.style)) {
      return node.style[this.config.style] || void 0;
    } else if (_.isString(this.config["class"])) {
      _ref = dom(node).classes();
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        c = _ref[_i];
        if (c.indexOf(this.config["class"]) === 0) {
          return c.slice(this.config["class"].length);
        }
      }
    } else if (_.isString(this.config.tag)) {
      return true;
    }
    return void 0;
  };

  return Format;

})();

module.exports = Format;



},{"../lib/dom":22,"lodash":"M4+//f"}],16:[function(_dereq_,module,exports){
var Format, Leaf, LinkedList, dom, _,
  __hasProp = {}.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

_ = _dereq_('lodash');

dom = _dereq_('../lib/dom');

Format = _dereq_('./format');

LinkedList = _dereq_('../lib/linked-list');

Leaf = (function(_super) {
  __extends(Leaf, _super);

  Leaf.ID_PREFIX = 'leaf-';

  Leaf.isLeafNode = function(node) {
    return dom(node).isTextNode() || (node.firstChild == null);
  };

  function Leaf(node, formats) {
    this.node = node;
    this.formats = _.clone(formats);
    this.id = _.uniqueId(Leaf.ID_PREFIX);
    this.text = dom(this.node).text();
    this.length = this.text.length;
  }

  Leaf.prototype.deleteText = function(offset, length) {
    var textNode;
    if (!(length > 0)) {
      return;
    }
    this.text = this.text.slice(0, offset) + this.text.slice(offset + length);
    this.length = this.text.length;
    if (dom.EMBED_TAGS[this.node.tagName] != null) {
      textNode = this.node.ownerDocument.createTextNode(this.text);
      return this.node = dom(this.node).replace(textNode);
    } else {
      return dom(this.node).text(this.text);
    }
  };

  Leaf.prototype.insertText = function(offset, text) {
    var textNode;
    this.text = this.text.slice(0, offset) + text + this.text.slice(offset);
    if (dom(this.node).isTextNode()) {
      dom(this.node).text(this.text);
    } else {
      textNode = this.node.ownerDocument.createTextNode(text);
      if (this.node.tagName === dom.DEFAULT_BREAK_TAG) {
        this.node = dom(this.node).replace(textNode);
      } else {
        this.node.appendChild(textNode);
        this.node = textNode;
      }
    }
    return this.length = this.text.length;
  };

  return Leaf;

})(LinkedList.Node);

module.exports = Leaf;



},{"../lib/dom":22,"../lib/linked-list":23,"./format":15,"lodash":"M4+//f"}],17:[function(_dereq_,module,exports){
var Format, Leaf, Line, LinkedList, Normalizer, Tandem, dom, _,
  __hasProp = {}.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

_ = _dereq_('lodash');

dom = _dereq_('../lib/dom');

Format = _dereq_('./format');

Leaf = _dereq_('./leaf');

Line = _dereq_('./line');

LinkedList = _dereq_('../lib/linked-list');

Normalizer = _dereq_('../lib/normalizer');

Tandem = _dereq_('tandem-core');

Line = (function(_super) {
  __extends(Line, _super);

  Line.CLASS_NAME = 'line';

  Line.ID_PREFIX = 'line-';

  function Line(doc, node) {
    this.doc = doc;
    this.node = node;
    this.id = _.uniqueId(Line.ID_PREFIX);
    this.formats = {};
    dom(this.node).addClass(Line.CLASS_NAME);
    this.rebuild();
    Line.__super__.constructor.call(this, this.node);
  }

  Line.prototype.buildLeaves = function(node, formats) {
    return _.each(dom(node).childNodes(), (function(_this) {
      return function(node) {
        var nodeFormats;
        node = Normalizer.normalizeNode(node);
        nodeFormats = _.clone(formats);
        _.each(_this.doc.formats, function(format, name) {
          if (!format.isType(Format.types.LINE) && format.match(node)) {
            return nodeFormats[name] = format.value(node);
          }
        });
        if (Leaf.isLeafNode(node)) {
          return _this.leaves.append(new Leaf(node, nodeFormats));
        } else {
          return _this.buildLeaves(node, nodeFormats);
        }
      };
    })(this));
  };

  Line.prototype.deleteText = function(offset, length) {
    var deleteLength, leaf, _ref;
    if (!(length > 0)) {
      return;
    }
    _ref = this.findLeafAt(offset), leaf = _ref[0], offset = _ref[1];
    while ((leaf != null) && length > 0) {
      deleteLength = Math.min(length, leaf.length - offset);
      leaf.deleteText(offset, deleteLength);
      length -= deleteLength;
      leaf = leaf.next;
      offset = 0;
    }
    return this.rebuild();
  };

  Line.prototype.findLeaf = function(leafNode) {
    var curLeaf;
    curLeaf = this.leaves.first;
    while (curLeaf != null) {
      if (curLeaf.node === leafNode) {
        return curLeaf;
      }
      curLeaf = curLeaf.next;
    }
    return null;
  };

  Line.prototype.findLeafAt = function(offset, inclusive) {
    var leaf;
    if (inclusive == null) {
      inclusive = false;
    }
    if (offset >= this.length - 1) {
      return [this.leaves.last, this.leaves.last.length];
    }
    leaf = this.leaves.first;
    while (leaf != null) {
      if (offset < leaf.length || (offset === leaf.length && inclusive)) {
        return [leaf, offset];
      }
      offset -= leaf.length;
      leaf = leaf.next;
    }
    return [this.leaves.last, offset - this.leaves.last.length];
  };

  Line.prototype.format = function(name, value) {
    var formats;
    if (_.isObject(name)) {
      formats = name;
    } else {
      formats = {};
      formats[name] = value;
    }
    _.each(formats, (function(_this) {
      return function(value, name) {
        var excludeFormat, format;
        format = _this.doc.formats[name];
        if (format.isType(Format.types.LINE)) {
          if (format.config.exclude && _this.formats[format.config.exclude]) {
            excludeFormat = _this.doc.formats[format.config.exclude];
            if (excludeFormat != null) {
              _this.node = excludeFormat.remove(_this.node);
              delete _this.formats[format.config.exclude];
            }
          }
          _this.node = format.add(_this.node, value);
        }
        if (value) {
          return _this.formats[name] = value;
        } else {
          return delete _this.formats[name];
        }
      };
    })(this));
    return this.resetContent();
  };

  Line.prototype.formatText = function(offset, length, name, value) {
    var format, leaf, leafOffset, leftNode, nextLeaf, rightNode, targetNode, _ref, _ref1, _ref2;
    _ref = this.findLeafAt(offset), leaf = _ref[0], leafOffset = _ref[1];
    format = this.doc.formats[name];
    if (!((format != null) && format.config.type !== Format.types.LINE)) {
      return;
    }
    while ((leaf != null) && length > 0) {
      nextLeaf = leaf.next;
      if ((value && leaf.formats[name] !== value) || (!value && (leaf.formats[name] != null))) {
        targetNode = leaf.node;
        if (leaf.formats[name] != null) {
          dom(targetNode).splitAncestors(this.node);
          while (!format.match(targetNode)) {
            targetNode = targetNode.parentNode;
          }
        }
        if (leafOffset > 0) {
          _ref1 = dom(targetNode).split(leafOffset), leftNode = _ref1[0], targetNode = _ref1[1];
        }
        if (leaf.length > leafOffset + length) {
          _ref2 = dom(targetNode).split(length), targetNode = _ref2[0], rightNode = _ref2[1];
        }
        format.add(targetNode, value);
      }
      length -= leaf.length - leafOffset;
      leafOffset = 0;
      leaf = nextLeaf;
    }
    return this.rebuild();
  };

  Line.prototype.insertText = function(offset, text, formats) {
    var leaf, leafOffset, nextNode, node, prevNode, _ref, _ref1;
    if (formats == null) {
      formats = {};
    }
    if (!(text.length > 0)) {
      return;
    }
    _ref = this.findLeafAt(offset), leaf = _ref[0], leafOffset = _ref[1];
    if (_.isEqual(leaf.formats, formats)) {
      leaf.insertText(leafOffset, text);
      return this.resetContent();
    } else {
      node = _.reduce(formats, (function(_this) {
        return function(node, value, name) {
          return _this.doc.formats[name].add(node, value);
        };
      })(this), this.node.ownerDocument.createTextNode(text));
      _ref1 = dom(leaf.node).split(leafOffset), prevNode = _ref1[0], nextNode = _ref1[1];
      if (nextNode) {
        nextNode = dom(nextNode).splitAncestors(this.node).get();
      }
      this.node.insertBefore(node, nextNode);
      return this.rebuild();
    }
  };

  Line.prototype.optimize = function() {
    Normalizer.optimizeLine(this.node);
    return this.rebuild();
  };

  Line.prototype.rebuild = function(force) {
    if (force == null) {
      force = false;
    }
    if (!force && (this.outerHTML != null) && this.outerHTML === this.node.outerHTML) {
      if (_.all(this.leaves.toArray(), (function(_this) {
        return function(leaf) {
          return dom(leaf.node).isAncestor(_this.node);
        };
      })(this))) {
        return false;
      }
    }
    this.node = Normalizer.normalizeNode(this.node);
    if (dom(this.node).length() === 0 && !this.node.querySelector(dom.DEFAULT_BREAK_TAG)) {
      this.node.appendChild(this.node.ownerDocument.createElement(dom.DEFAULT_BREAK_TAG));
    }
    this.leaves = new LinkedList();
    this.formats = _.reduce(this.doc.formats, (function(_this) {
      return function(formats, format, name) {
        if (format.isType(Format.types.LINE)) {
          if (format.match(_this.node)) {
            formats[name] = format.value(_this.node);
          } else {
            delete formats[name];
          }
        }
        return formats;
      };
    })(this), this.formats);
    this.buildLeaves(this.node, {});
    this.resetContent();
    return true;
  };

  Line.prototype.resetContent = function() {
    var ops;
    if (this.node.id !== this.id) {
      this.node.id = this.id;
    }
    this.outerHTML = this.node.outerHTML;
    this.length = 1;
    ops = _.map(this.leaves.toArray(), (function(_this) {
      return function(leaf) {
        _this.length += leaf.length;
        return new Tandem.InsertOp(leaf.text, leaf.formats);
      };
    })(this));
    ops.push(new Tandem.InsertOp('\n', this.formats));
    return this.delta = new Tandem.Delta(0, this.length, ops);
  };

  return Line;

})(LinkedList.Node);

module.exports = Line;



},{"../lib/dom":22,"../lib/linked-list":23,"../lib/normalizer":24,"./format":15,"./leaf":16,"./line":17,"lodash":"M4+//f","tandem-core":10}],18:[function(_dereq_,module,exports){
var DEFAULT_STYLES, LIST_STYLES, Normalizer, Renderer, dom, rule, _;

_ = _dereq_('lodash');

dom = _dereq_('../lib/dom');

Normalizer = _dereq_('../lib/normalizer');

DEFAULT_STYLES = {
  'html': {
    'height': '100%',
    'width': '100%'
  },
  'body': {
    'box-sizing': 'border-box',
    'cursor': 'text',
    'font-family': "'Helvetica', 'Arial', sans-serif",
    'font-size': '13px',
    'height': '100%',
    'line-height': '1.42',
    'margin': '0px',
    'overflow-x': 'hidden',
    'overflow-y': 'auto',
    'padding': '12px 15px'
  },
  '.editor-container': {
    'height': '100%',
    'outline': 'none',
    'position': 'relative',
    'tab-size': '4',
    'white-space': 'pre-wrap'
  },
  '.editor-container div': {
    'margin': '0',
    'padding': '0'
  },
  '.editor-container a': {
    'text-decoration': 'underline'
  },
  '.editor-container b': {
    'font-weight': 'bold'
  },
  '.editor-container i': {
    'font-style': 'italic'
  },
  '.editor-container s': {
    'text-decoration': 'line-through'
  },
  '.editor-container u': {
    'text-decoration': 'underline'
  },
  '.editor-container img': {
    'max-width': '100%'
  },
  '.editor-container blockquote': {
    'margin': '0 0 0 2em',
    'padding': '0'
  },
  '.editor-container ol': {
    'margin': '0 0 0 2em',
    'padding': '0',
    'list-style-type': 'decimal'
  },
  '.editor-container ul': {
    'margin': '0 0 0 2em',
    'padding': '0',
    'list-style-type': 'disc'
  }
};

LIST_STYLES = ['decimal', 'lower-alpha', 'lower-roman'];

rule = '.editor-container ol > li';

_.each([1, 2, 3, 4, 5, 6, 7, 8, 9], function(i) {
  rule += ' > ol';
  DEFAULT_STYLES[rule] = {
    'list-style-type': LIST_STYLES[i % 3]
  };
  return rule += ' > li';
});

if (dom.isIE(10)) {
  DEFAULT_STYLES[dom.DEFAULT_BREAK_TAG] = {
    'display': 'none'
  };
}

Renderer = (function() {
  Renderer.objToCss = function(obj) {
    return _.map(obj, function(value, key) {
      var innerStr;
      innerStr = _.map(value, function(innerValue, innerKey) {
        return "" + innerKey + ": " + innerValue + ";";
      }).join(' ');
      return "" + key + " { " + innerStr + " }";
    }).join("\n");
  };

  Renderer.buildFrame = function(container) {
    var iframe, iframeDoc, root;
    iframe = container.ownerDocument.createElement('iframe');
    dom(iframe).attributes({
      frameBorder: '0',
      height: '100%',
      width: '100%',
      title: 'Quill Rich Text Editor',
      role: 'presentation'
    });
    container.appendChild(iframe);
    iframeDoc = iframe.contentWindow.document;
    iframeDoc.open();
    iframeDoc.write('<!DOCTYPE html>');
    iframeDoc.close();
    root = iframeDoc.createElement('div');
    iframeDoc.body.appendChild(root);
    return [root, iframe];
  };

  function Renderer(container, options) {
    var _ref;
    this.container = container;
    this.options = options != null ? options : {};
    this.container.innerHTML = '';
    _ref = Renderer.buildFrame(this.container), this.root = _ref[0], this.iframe = _ref[1];
    this.root.setAttribute('id', this.options.id);
    this.iframe.setAttribute('name', this.options.id);
    dom(this.root).addClass('editor-container');
    dom(this.container).addClass('ql-container');
    if (dom.isIOS()) {
      dom(this.container).styles({
        'overflow': 'auto',
        '-webkit-overflow-scrolling': 'touch'
      });
    }
    this.addStyles(DEFAULT_STYLES);
    if (this.options.styles != null) {
      _.defer(_.bind(this.addStyles, this, this.options.styles));
    }
  }

  Renderer.prototype.addContainer = function(className, before) {
    var container, refNode;
    if (before == null) {
      before = false;
    }
    refNode = before ? this.root : null;
    container = this.root.ownerDocument.createElement('div');
    dom(container).addClass(className);
    this.root.parentNode.insertBefore(container, refNode);
    return container;
  };

  Renderer.prototype.addStyles = function(css) {
    var link, style;
    if (typeof css === 'object') {
      style = this.root.ownerDocument.createElement('style');
      style.type = 'text/css';
      css = Renderer.objToCss(css);
      style.appendChild(this.root.ownerDocument.createTextNode(css));
      return this.root.ownerDocument.head.appendChild(style);
    } else if (typeof css === 'string') {
      link = this.root.ownerDocument.createElement('link');
      dom(link).attributes({
        type: 'text/css',
        rel: 'stylesheet',
        href: css
      });
      return this.root.ownerDocument.head.appendChild(link);
    }
  };

  return Renderer;

})();

module.exports = Renderer;



},{"../lib/dom":22,"../lib/normalizer":24,"lodash":"M4+//f"}],19:[function(_dereq_,module,exports){
var Leaf, Normalizer, Range, Selection, dom, _;

_ = _dereq_('lodash');

dom = _dereq_('../lib/dom');

Leaf = _dereq_('./leaf');

Normalizer = _dereq_('../lib/normalizer');

Range = _dereq_('../lib/range');

Selection = (function() {
  function Selection(doc, iframe, emitter) {
    this.doc = doc;
    this.iframe = iframe;
    this.emitter = emitter;
    this.document = this.doc.root.ownerDocument;
    this.focus = false;
    this.range = new Range(0, 0);
    this.nullDelay = false;
    this.update('silent');
  }

  Selection.prototype.checkFocus = function() {
    return this.document.activeElement === this.doc.root && document.activeElement === this.iframe;
  };

  Selection.prototype.getRange = function(ignoreFocus) {
    var end, nativeRange, start;
    if (ignoreFocus == null) {
      ignoreFocus = false;
    }
    if (this.checkFocus()) {
      nativeRange = this._getNativeRange();
      if (nativeRange == null) {
        return null;
      }
      start = this._positionToIndex(nativeRange.startContainer, nativeRange.startOffset);
      if (nativeRange.startContainer === nativeRange.endContainer && nativeRange.startOffset === nativeRange.endOffset) {
        end = start;
      } else {
        end = this._positionToIndex(nativeRange.endContainer, nativeRange.endOffset);
      }
      return new Range(Math.min(start, end), Math.max(start, end));
    } else if (ignoreFocus) {
      return this.range;
    } else {
      return null;
    }
  };

  Selection.prototype.preserve = function(fn) {
    var endNode, endOffset, nativeRange, startNode, startOffset, _ref, _ref1, _ref2, _ref3;
    nativeRange = this._getNativeRange();
    if ((nativeRange != null) && this.checkFocus()) {
      _ref = this._encodePosition(nativeRange.startContainer, nativeRange.startOffset), startNode = _ref[0], startOffset = _ref[1];
      _ref1 = this._encodePosition(nativeRange.endContainer, nativeRange.endOffset), endNode = _ref1[0], endOffset = _ref1[1];
      fn();
      _ref2 = this._decodePosition(startNode, startOffset), startNode = _ref2[0], startOffset = _ref2[1];
      _ref3 = this._decodePosition(endNode, endOffset), endNode = _ref3[0], endOffset = _ref3[1];
      return this._setNativeRange(startNode, startOffset, endNode, endOffset);
    } else {
      return fn();
    }
  };

  Selection.prototype.setRange = function(range, source) {
    var endNode, endOffset, startNode, startOffset, _ref, _ref1, _ref2;
    if (range != null) {
      _ref = this._indexToPosition(range.start), startNode = _ref[0], startOffset = _ref[1];
      if (range.isCollapsed()) {
        _ref1 = [startNode, startOffset], endNode = _ref1[0], endOffset = _ref1[1];
      } else {
        _ref2 = this._indexToPosition(range.end), endNode = _ref2[0], endOffset = _ref2[1];
      }
      this._setNativeRange(startNode, startOffset, endNode, endOffset);
    } else {
      this._setNativeRange(null);
    }
    return this.update(source);
  };

  Selection.prototype.shiftAfter = function(index, length, fn) {
    var range;
    range = this.getRange();
    fn();
    if (range != null) {
      range.shift(index, length);
      return this.setRange(range, 'silent');
    }
  };

  Selection.prototype.update = function(source) {
    var emit, focus, range, toEmit;
    focus = this.checkFocus();
    range = this.getRange(true);
    emit = source !== 'silent' && (!Range.compare(range, this.range) || focus !== this.focus);
    toEmit = focus ? range : null;
    if (toEmit === null && source === 'user' && !this.nullDelay) {
      return this.nullDelay = true;
    } else {
      this.nullDelay = false;
      this.range = range;
      this.focus = focus;
      if (emit) {
        return this.emitter.emit(this.emitter.constructor.events.SELECTION_CHANGE, toEmit, source);
      }
    }
  };

  Selection.prototype._decodePosition = function(node, offset) {
    var childIndex;
    if (dom(node).isElement()) {
      childIndex = _.indexOf(dom(node.parentNode).childNodes(), node);
      offset += childIndex;
      node = node.parentNode;
    }
    return [node, offset];
  };

  Selection.prototype._encodePosition = function(node, offset) {
    var text;
    while (true) {
      if (dom(node).isTextNode() || node.tagName === dom.DEFAULT_BREAK_TAG || (dom.EMBED_TAGS[node.tagName] != null)) {
        return [node, offset];
      } else if (offset < node.childNodes.length) {
        node = node.childNodes[offset];
        offset = 0;
      } else if (node.childNodes.length === 0) {
        if (Normalizer.TAGS[node.tagName] == null) {
          text = node.ownerDocument.createTextNode('');
          node.appendChild(text);
          node = text;
        }
        return [node, 0];
      } else {
        node = node.lastChild;
        if (dom(node).isElement()) {
          if (node.tagName === dom.DEFAULT_BREAK_TAG || (dom.EMBED_TAGS[node.tagName] != null)) {
            return [node, 1];
          } else {
            offset = node.childNodes.length;
          }
        } else {
          return [node, dom(node).length()];
        }
      }
    }
  };

  Selection.prototype._getNativeSelection = function() {
    if (this.document.getSelection != null) {
      return this.document.getSelection();
    } else {
      return null;
    }
  };

  Selection.prototype._getNativeRange = function() {
    var range, selection;
    selection = this._getNativeSelection();
    if ((selection != null ? selection.rangeCount : void 0) > 0) {
      range = selection.getRangeAt(0);
      if (dom(range.startContainer).isAncestor(this.doc.root, true)) {
        if (range.startContainer === range.endContainer || dom(range.endContainer).isAncestor(this.doc.root, true)) {
          return range;
        }
      }
    }
    return null;
  };

  Selection.prototype._indexToPosition = function(index) {
    var leaf, offset, _ref;
    if (this.doc.lines.length === 0) {
      return [this.doc.root, 0];
    }
    _ref = this.doc.findLeafAt(index, true), leaf = _ref[0], offset = _ref[1];
    return this._decodePosition(leaf.node, offset);
  };

  Selection.prototype._positionToIndex = function(node, offset) {
    var leaf, leafNode, leafOffset, line, lineOffset, _ref;
    _ref = this._encodePosition(node, offset), leafNode = _ref[0], offset = _ref[1];
    line = this.doc.findLine(leafNode);
    if (line == null) {
      return 0;
    }
    leaf = line.findLeaf(leafNode);
    lineOffset = 0;
    while (line.prev != null) {
      line = line.prev;
      lineOffset += line.length;
    }
    if (leaf == null) {
      return lineOffset;
    }
    leafOffset = 0;
    while (leaf.prev != null) {
      leaf = leaf.prev;
      leafOffset += leaf.length;
    }
    return lineOffset + leafOffset + offset;
  };

  Selection.prototype._setNativeRange = function(startNode, startOffset, endNode, endOffset) {
    var nativeRange, selection;
    selection = this._getNativeSelection();
    if (!selection) {
      return;
    }
    if (startNode != null) {
      if (!this.checkFocus()) {
        this.doc.root.focus();
      }
      nativeRange = this._getNativeRange();
      if ((nativeRange == null) || startNode !== nativeRange.startContainer || startOffset !== nativeRange.startOffset || endNode !== nativeRange.endContainer || endOffset !== nativeRange.endOffset) {
        selection.removeAllRanges();
        nativeRange = this.document.createRange();
        nativeRange.setStart(startNode, startOffset);
        nativeRange.setEnd(endNode, endOffset);
        selection.addRange(nativeRange);
        if (!this.checkFocus()) {
          return this.doc.root.focus();
        }
      }
    } else {
      selection.removeAllRanges();
      return this.doc.root.blur();
    }
  };

  return Selection;

})();

module.exports = Selection;



},{"../lib/dom":22,"../lib/normalizer":24,"../lib/range":26,"./leaf":16,"lodash":"M4+//f"}],20:[function(_dereq_,module,exports){
_dereq_('./modules/authorship');

_dereq_('./modules/image-tooltip');

_dereq_('./modules/keyboard');

_dereq_('./modules/link-tooltip');

_dereq_('./modules/multi-cursor');

_dereq_('./modules/paste-manager');

_dereq_('./modules/toolbar');

_dereq_('./modules/tooltip');

_dereq_('./modules/undo-manager');

module.exports = _dereq_('./quill');



},{"./modules/authorship":27,"./modules/image-tooltip":28,"./modules/keyboard":29,"./modules/link-tooltip":30,"./modules/multi-cursor":31,"./modules/paste-manager":32,"./modules/toolbar":33,"./modules/tooltip":34,"./modules/undo-manager":35,"./quill":36}],21:[function(_dereq_,module,exports){
var ColorPicker, Picker, dom,
  __hasProp = {}.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

dom = _dereq_('./dom');

Picker = _dereq_('./picker');

ColorPicker = (function(_super) {
  __extends(ColorPicker, _super);

  function ColorPicker() {
    ColorPicker.__super__.constructor.apply(this, arguments);
    dom(this.container).addClass('ql-color-picker');
  }

  ColorPicker.prototype.buildItem = function(picker, option, index) {
    var item;
    item = ColorPicker.__super__.buildItem.call(this, picker, option, index);
    item.style.backgroundColor = option.value;
    return item;
  };

  return ColorPicker;

})(Picker);

module.exports = ColorPicker;



},{"./dom":22,"./picker":25}],22:[function(_dereq_,module,exports){
var SelectWrapper, Wrapper, dom, lastKeyEvent, _,
  __bind = function(fn, me){ return function(){ return fn.apply(me, arguments); }; },
  __hasProp = {}.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

_ = _dereq_('lodash');

lastKeyEvent = null;

Wrapper = (function() {
  function Wrapper(node) {
    this.node = node;
    this.trigger = __bind(this.trigger, this);
  }

  Wrapper.prototype.addClass = function(cssClass) {
    if (this.hasClass(cssClass)) {
      return;
    }
    if (this.node.classList != null) {
      this.node.classList.add(cssClass);
    } else if (this.node.className != null) {
      this.node.className = (this.node.className + ' ' + cssClass).trim();
    }
    return this;
  };

  Wrapper.prototype.attributes = function(attributes) {
    var attr, i, value, _i, _len, _ref;
    if (attributes) {
      _.each(attributes, (function(_this) {
        return function(value, name) {
          return _this.node.setAttribute(name, value);
        };
      })(this));
      return this;
    } else {
      if (this.node.attributes == null) {
        return {};
      }
      attributes = {};
      _ref = this.node.attributes;
      for (i = _i = 0, _len = _ref.length; _i < _len; i = ++_i) {
        value = _ref[i];
        attr = this.node.attributes[i];
        attributes[attr.name] = attr.value;
      }
      return attributes;
    }
  };

  Wrapper.prototype.child = function(offset) {
    var child, length;
    child = this.node.firstChild;
    length = dom(child).length();
    while (child != null) {
      if (offset < length) {
        break;
      }
      offset -= length;
      child = child.nextSibling;
      length = dom(child).length();
    }
    if (child == null) {
      child = this.node.lastChild;
      offset = dom(child).length();
    }
    return [child, offset];
  };

  Wrapper.prototype.childNodes = function() {
    return _.map(this.node.childNodes);
  };

  Wrapper.prototype.classes = function() {
    return this.node.className.split(/\s+/);
  };

  Wrapper.prototype.descendants = function() {
    return _.map(this.node.getElementsByTagName('*'));
  };

  Wrapper.prototype.get = function() {
    return this.node;
  };

  Wrapper.prototype.hasClass = function(cssClass) {
    if (this.node.classList != null) {
      return this.node.classList.contains(cssClass);
    } else if (this.node.className != null) {
      return _.indexOf(this.classes(), cssClass) > -1;
    }
    return false;
  };

  Wrapper.prototype.isAncestor = function(ancestor, inclusive) {
    var node;
    if (inclusive == null) {
      inclusive = false;
    }
    if (ancestor === this.node) {
      return inclusive;
    }
    node = this.node;
    while (node) {
      if (node === ancestor) {
        return true;
      }
      node = node.parentNode;
    }
    return false;
  };

  Wrapper.prototype.isElement = function() {
    var _ref;
    return ((_ref = this.node) != null ? _ref.nodeType : void 0) === dom.ELEMENT_NODE;
  };

  Wrapper.prototype.isTextNode = function() {
    var _ref;
    return ((_ref = this.node) != null ? _ref.nodeType : void 0) === dom.TEXT_NODE;
  };

  Wrapper.prototype.length = function() {
    var length;
    if (this.node == null) {
      return 0;
    }
    length = this.text().length;
    if (this.isElement()) {
      length += this.node.querySelectorAll(_.keys(dom.EMBED_TAGS).join(',')).length;
    }
    return length;
  };

  Wrapper.prototype.merge = function(node) {
    var $node;
    $node = dom(node);
    if (this.isElement()) {
      $node.moveChildren(this.node);
      this.normalize();
    } else {
      this.text(this.text() + $node.text());
    }
    $node.remove();
    return this;
  };

  Wrapper.prototype.moveChildren = function(newParent) {
    _.each(this.childNodes(), function(child) {
      return newParent.appendChild(child);
    });
    return this;
  };

  Wrapper.prototype.nextLineNode = function(root) {
    var nextNode;
    nextNode = this.node.nextSibling;
    if ((nextNode == null) && this.node.parentNode !== root) {
      nextNode = this.node.parentNode.nextSibling;
    }
    if ((nextNode != null) && (dom.LIST_TAGS[nextNode.tagName] != null)) {
      nextNode = nextNode.firstChild;
    }
    return nextNode;
  };

  Wrapper.prototype.normalize = function() {
    var $node, curNode, followingNode, nextNode;
    curNode = this.node.firstChild;
    while (curNode != null) {
      nextNode = curNode.nextSibling;
      $node = dom(curNode);
      if ((nextNode != null) && dom(nextNode).isTextNode()) {
        if ($node.text().length === 0) {
          $node.remove();
        } else if ($node.isTextNode()) {
          followingNode = nextNode.nextSibling;
          $node.merge(nextNode);
          nextNode = followingNode;
        }
      }
      curNode = nextNode;
    }
    return this;
  };

  Wrapper.prototype.on = function(eventName, listener) {
    this.node.addEventListener(eventName, function(event) {
      var arg, propogate;
      arg = lastKeyEvent && (eventName === 'keydown' || eventName === 'keyup') ? lastKeyEvent : event;
      propogate = listener(arg);
      if (!propogate) {
        event.preventDefault();
        event.stopPropagation();
      }
      return propogate;
    });
    return this;
  };

  Wrapper.prototype.remove = function() {
    var _ref;
    if ((_ref = this.node.parentNode) != null) {
      _ref.removeChild(this.node);
    }
    this.node = null;
    return null;
  };

  Wrapper.prototype.removeClass = function(cssClass) {
    var classArray;
    if (!this.hasClass(cssClass)) {
      return;
    }
    if (this.node.classList != null) {
      return this.node.classList.remove(cssClass);
    } else if (this.node.className != null) {
      classArray = this.classes();
      classArray.splice(_.indexOf(classArray, cssClass), 1);
      this.node.className = classArray.join(' ');
    }
    return this;
  };

  Wrapper.prototype.replace = function(newNode) {
    this.node.parentNode.replaceChild(newNode, this.node);
    this.node = newNode;
    return newNode;
  };

  Wrapper.prototype.splitAncestors = function(root, force) {
    var nextNode, parentClone, parentNode, refNode;
    if (force == null) {
      force = false;
    }
    if (this.node === root || this.node.parentNode === root) {
      return this;
    }
    if ((this.node.previousSibling != null) || force) {
      parentNode = this.node.parentNode;
      parentClone = parentNode.cloneNode(false);
      parentNode.parentNode.insertBefore(parentClone, parentNode.nextSibling);
      refNode = this.node;
      while (refNode != null) {
        nextNode = refNode.nextSibling;
        parentClone.appendChild(refNode);
        refNode = nextNode;
      }
      return dom(parentClone).splitAncestors(root);
    } else {
      return dom(this.node.parentNode).splitAncestors(root);
    }
  };

  Wrapper.prototype.split = function(offset, force) {
    var after, child, childLeft, childRight, left, nextRight, nodeLength, right, _ref, _ref1;
    if (force == null) {
      force = false;
    }
    nodeLength = this.length();
    offset = Math.max(0, offset);
    offset = Math.min(offset, nodeLength);
    if (!(force || offset !== 0)) {
      return [this.node.previousSibling, this.node, false];
    }
    if (!(force || offset !== nodeLength)) {
      return [this.node, this.node.nextSibling, false];
    }
    if (this.node.nodeType === dom.TEXT_NODE) {
      after = this.node.splitText(offset);
      return [this.node, after, true];
    } else {
      left = this.node;
      right = this.node.cloneNode(false);
      this.node.parentNode.insertBefore(right, left.nextSibling);
      _ref = this.child(offset), child = _ref[0], offset = _ref[1];
      _ref1 = dom(child).split(offset), childLeft = _ref1[0], childRight = _ref1[1];
      while (childRight !== null) {
        nextRight = childRight.nextSibling;
        right.appendChild(childRight);
        childRight = nextRight;
      }
      return [left, right, true];
    }
  };

  Wrapper.prototype.styles = function(styles, overwrite) {
    var obj, styleString;
    if (overwrite == null) {
      overwrite = false;
    }
    if (styles) {
      if (!overwrite) {
        styles = _.defaults(styles, this.styles());
      }
      styleString = _.map(styles, function(style, name) {
        return "" + name + ": " + style;
      }).join('; ') + ';';
      this.node.setAttribute('style', styleString);
      return this;
    } else {
      styleString = this.node.getAttribute('style') || '';
      obj = _.reduce(styleString.split(';'), function(styles, str) {
        var name, value, _ref;
        _ref = str.split(':'), name = _ref[0], value = _ref[1];
        if (name && value) {
          name = name.trim();
          value = value.trim();
          styles[name.toLowerCase()] = value;
        }
        return styles;
      }, {});
      return obj;
    }
  };

  Wrapper.prototype.switchTag = function(newTag) {
    var attributes, newNode;
    newTag = newTag.toUpperCase();
    if (this.node.tagName === newTag) {
      return this;
    }
    newNode = this.node.ownerDocument.createElement(newTag);
    attributes = this.attributes();
    if (dom.VOID_TAGS[newTag] == null) {
      this.moveChildren(newNode);
    }
    this.replace(newNode);
    return this.attributes(attributes).get();
  };

  Wrapper.prototype.text = function(text) {
    if (text != null) {
      switch (this.node.nodeType) {
        case dom.ELEMENT_NODE:
          this.node.textContent = text;
          break;
        case dom.TEXT_NODE:
          this.node.data = text;
      }
      return this;
    } else {
      switch (this.node.nodeType) {
        case dom.ELEMENT_NODE:
          if (this.node.tagName === dom.DEFAULT_BREAK_TAG) {
            return "";
          }
          if (dom.EMBED_TAGS[this.node.tagName] != null) {
            return dom.EMBED_TEXT;
          }
          if (this.node.textContent != null) {
            return this.node.textContent;
          }
          return "";
        case dom.TEXT_NODE:
          return this.node.data || "";
        default:
          return "";
      }
    }
  };

  Wrapper.prototype.textNodes = function() {
    var textNode, textNodes, walker;
    walker = this.node.ownerDocument.createTreeWalker(this.node, NodeFilter.SHOW_TEXT, null, false);
    textNodes = [];
    while (textNode = walker.nextNode()) {
      textNodes.push(textNode);
    }
    return textNodes;
  };

  Wrapper.prototype.toggleClass = function(className, state) {
    if (state == null) {
      state = !this.hasClass(className);
    }
    if (state) {
      this.addClass(className);
    } else {
      this.removeClass(className);
    }
    return this;
  };

  Wrapper.prototype.trigger = function(eventName, options) {
    var event, initFn, modifiers;
    if (options == null) {
      options = {};
    }
    if (_.indexOf(['keypress', 'keydown', 'keyup'], eventName) < 0) {
      event = this.node.ownerDocument.createEvent('Event');
      event.initEvent(eventName, options.bubbles, options.cancelable);
    } else {
      event = this.node.ownerDocument.createEvent('KeyboardEvent');
      lastKeyEvent = _.clone(options);
      if (_.isNumber(options.key)) {
        lastKeyEvent.which = options.key;
      } else if (_.isString(options.key)) {
        lastKeyEvent.which = options.key.toUpperCase().charCodeAt(0);
      } else {
        lastKeyEvent.which = 0;
      }
      if (dom.isIE(10)) {
        modifiers = [];
        if (options.altKey) {
          modifiers.push('Alt');
        }
        if (options.ctrlKey) {
          modifiers.push('Control');
        }
        if (options.metaKey) {
          modifiers.push('Meta');
        }
        if (options.shiftKey) {
          modifiers.push('Shift');
        }
        event.initKeyboardEvent(eventName, options.bubbles, options.cancelable, this.window(), 0, 0, modifiers.join(' '), null, null);
      } else {
        initFn = _.isFunction(event.initKeyboardEvent) ? 'initKeyboardEvent' : 'initKeyEvent';
        event[initFn](eventName, options.bubbles, options.cancelable, this.window(), options.ctrlKey, options.altKey, options.shiftKey, options.metaKey, 0, 0);
      }
    }
    this.node.dispatchEvent(event);
    lastKeyEvent = null;
    return this;
  };

  Wrapper.prototype.unwrap = function() {
    var next, ret;
    ret = this.node.firstChild;
    next = this.node.nextSibling;
    _.each(this.childNodes(), (function(_this) {
      return function(child) {
        return _this.node.parentNode.insertBefore(child, next);
      };
    })(this));
    this.remove();
    return ret;
  };

  Wrapper.prototype.window = function() {
    return this.node.ownerDocument.defaultView || this.node.ownerDocument.parentWindow;
  };

  Wrapper.prototype.wrap = function(wrapper) {
    var parent;
    if (this.node.parentNode != null) {
      this.node.parentNode.insertBefore(wrapper, this.node);
    }
    parent = wrapper;
    while (parent.firstChild != null) {
      parent = wrapper.firstChild;
    }
    parent.appendChild(this.node);
    return this;
  };

  return Wrapper;

})();

SelectWrapper = (function(_super) {
  __extends(SelectWrapper, _super);

  function SelectWrapper() {
    return SelectWrapper.__super__.constructor.apply(this, arguments);
  }

  SelectWrapper.prototype["default"] = function() {
    return this.node.querySelector('option[selected]');
  };

  SelectWrapper.prototype.option = function(option, trigger) {
    var value;
    if (trigger == null) {
      trigger = true;
    }
    value = _.isElement(option) ? option.value : option;
    if (value) {
      this.node.value = value;
    } else {
      this.node.selectedIndex = -1;
    }
    if (trigger) {
      this.trigger('change');
    }
    return this;
  };

  SelectWrapper.prototype.reset = function(trigger) {
    var option;
    if (trigger == null) {
      trigger = true;
    }
    option = this["default"]();
    if (option != null) {
      option.selected = true;
    } else {
      this.node.selectedIndex = 0;
    }
    if (trigger) {
      this.trigger('change');
    }
    return this;
  };

  SelectWrapper.prototype.value = function() {
    if (this.node.selectedIndex > -1) {
      return this.node.options[this.node.selectedIndex].value;
    } else {
      return '';
    }
  };

  return SelectWrapper;

})(Wrapper);

dom = function(node) {
  if ((node != null ? node.tagName : void 0) === 'SELECT') {
    return new SelectWrapper(node);
  } else {
    return new Wrapper(node);
  }
};

dom = _.extend(dom, {
  ELEMENT_NODE: 1,
  NOBREAK_SPACE: "&nbsp;",
  TEXT_NODE: 3,
  ZERO_WIDTH_NOBREAK_SPACE: "\uFEFF",
  DEFAULT_BLOCK_TAG: 'DIV',
  DEFAULT_BREAK_TAG: 'BR',
  DEFAULT_INLINE_TAG: 'SPAN',
  EMBED_TEXT: '!',
  FONT_SIZES: {
    '10px': 1,
    '13px': 2,
    '16px': 3,
    '18px': 4,
    '24px': 5,
    '32px': 6,
    '48px': 7
  },
  KEYS: {
    BACKSPACE: 8,
    TAB: 9,
    ENTER: 13,
    ESCAPE: 27,
    LEFT: 37,
    UP: 38,
    RIGHT: 39,
    DOWN: 40,
    DELETE: 46
  },
  BLOCK_TAGS: {
    'ADDRESS': 'ADDRESS',
    'ARTICLE': 'ARTICLE',
    'ASIDE': 'ASIDE',
    'AUDIO': 'AUDIO',
    'BLOCKQUOTE': 'BLOCKQUOTE',
    'CANVAS': 'CANVAS',
    'DD': 'DD',
    'DIV': 'DIV',
    'DL': 'DL',
    'FIGCAPTION': 'FIGCAPTION',
    'FIGURE': 'FIGURE',
    'FOOTER': 'FOOTER',
    'FORM': 'FORM',
    'H1': 'H1',
    'H2': 'H2',
    'H3': 'H3',
    'H4': 'H4',
    'H5': 'H5',
    'H6': 'H6',
    'HEADER': 'HEADER',
    'HGROUP': 'HGROUP',
    'LI': 'LI',
    'OL': 'OL',
    'OUTPUT': 'OUTPUT',
    'P': 'P',
    'PRE': 'PRE',
    'SECTION': 'SECTION',
    'TABLE': 'TABLE',
    'TBODY': 'TBODY',
    'TD': 'TD',
    'TFOOT': 'TFOOT',
    'TH': 'TH',
    'THEAD': 'THEAD',
    'TR': 'TR',
    'UL': 'UL',
    'VIDEO': 'VIDEO'
  },
  EMBED_TAGS: {
    'IMG': 'IMG'
  },
  LINE_TAGS: {
    'DIV': 'DIV',
    'LI': 'LI'
  },
  LIST_TAGS: {
    'OL': 'OL',
    'UL': 'UL'
  },
  VOID_TAGS: {
    'AREA': 'AREA',
    'BASE': 'BASE',
    'BR': 'BR',
    'COL': 'COL',
    'COMMAND': 'COMMAND',
    'EMBED': 'EMBED',
    'HR': 'HR',
    'IMG': 'IMG',
    'INPUT': 'INPUT',
    'KEYGEN': 'KEYGEN',
    'LINK': 'LINK',
    'META': 'META',
    'PARAM': 'PARAM',
    'SOURCE': 'SOURCE',
    'TRACK': 'TRACK',
    'WBR': 'WBR'
  },
  convertFontSize: function(size) {
    var i, s, sources, targets;
    if (_.isString(size) && size.indexOf('px') > -1) {
      sources = _.keys(dom.FONT_SIZES);
      targets = _.values(dom.FONT_SIZES);
    } else {
      targets = _.keys(dom.FONT_SIZES);
      sources = _.values(dom.FONT_SIZES);
    }
    for (i in sources) {
      s = sources[i];
      if (parseInt(size) <= parseInt(s)) {
        return targets[i];
      }
    }
    return _.last(targets);
  },
  isIE: function(maxVersion) {
    var version;
    version = document.documentMode;
    return version && maxVersion >= version;
  },
  isIOS: function() {
    return /iPhone|iPad/i.test(navigator.userAgent);
  },
  isMac: function() {
    return /Mac/i.test(navigator.platform);
  }
});

module.exports = dom;



},{"lodash":"M4+//f"}],23:[function(_dereq_,module,exports){
var LinkedList, Node;

Node = (function() {
  function Node(data) {
    this.data = data;
    this.prev = this.next = null;
  }

  return Node;

})();

LinkedList = (function() {
  LinkedList.Node = Node;

  function LinkedList() {
    this.length = 0;
    this.first = this.last = null;
  }

  LinkedList.prototype.append = function(node) {
    if (this.first != null) {
      node.next = null;
      this.last.next = node;
    } else {
      this.first = node;
    }
    node.prev = this.last;
    this.last = node;
    return this.length += 1;
  };

  LinkedList.prototype.insertAfter = function(refNode, newNode) {
    newNode.prev = refNode;
    if (refNode != null) {
      newNode.next = refNode.next;
      if (refNode.next != null) {
        refNode.next.prev = newNode;
      }
      refNode.next = newNode;
      if (refNode === this.last) {
        this.last = newNode;
      }
    } else {
      newNode.next = this.first;
      this.first.prev = newNode;
      this.first = newNode;
    }
    return this.length += 1;
  };

  LinkedList.prototype.remove = function(node) {
    if (this.length > 1) {
      if (node.prev != null) {
        node.prev.next = node.next;
      }
      if (node.next != null) {
        node.next.prev = node.prev;
      }
      if (node === this.first) {
        this.first = node.next;
      }
      if (node === this.last) {
        this.last = node.prev;
      }
    } else {
      this.first = this.last = null;
    }
    node.prev = node.next = null;
    return this.length -= 1;
  };

  LinkedList.prototype.toArray = function() {
    var arr, cur;
    arr = [];
    cur = this.first;
    while (cur != null) {
      arr.push(cur);
      cur = cur.next;
    }
    return arr;
  };

  return LinkedList;

})();

module.exports = LinkedList;



},{}],24:[function(_dereq_,module,exports){
var Normalizer, dom, _;

_ = _dereq_('lodash');

dom = _dereq_('./dom');

Normalizer = {
  ALIASES: {
    'STRONG': 'B',
    'EM': 'I',
    'DEL': 'S',
    'STRIKE': 'S'
  },
  ATTRIBUTES: {
    'color': 'color',
    'face': 'fontFamily',
    'size': 'fontSize'
  },
  STYLES: {
    'background-color': 'background-color',
    'color': 'color',
    'font-family': 'font-family',
    'font-size': 'font-size',
    'text-align': 'text-align'
  },
  TAGS: {
    'DIV': 'DIV',
    'BR': 'BR',
    'SPAN': 'SPAN',
    'B': 'B',
    'I': 'I',
    'S': 'S',
    'U': 'U',
    'A': 'A',
    'IMG': 'IMG',
    'OL': 'OL',
    'UL': 'UL',
    'LI': 'LI'
  },
  handleBreaks: function(lineNode) {
    var breaks;
    breaks = _.map(lineNode.querySelectorAll(dom.DEFAULT_BREAK_TAG));
    _.each(breaks, (function(_this) {
      return function(br) {
        if ((br.nextSibling != null) && (!dom.isIE(10) || (br.previousSibling != null))) {
          return dom(br.nextSibling).splitAncestors(lineNode.parentNode);
        }
      };
    })(this));
    return lineNode;
  },
  normalizeLine: function(lineNode) {
    lineNode = Normalizer.wrapInline(lineNode);
    lineNode = Normalizer.handleBreaks(lineNode);
    lineNode = Normalizer.pullBlocks(lineNode);
    lineNode = Normalizer.normalizeNode(lineNode);
    Normalizer.unwrapText(lineNode);
    if ((lineNode != null) && (dom.LIST_TAGS[lineNode.tagName] != null)) {
      lineNode = lineNode.firstChild;
    }
    return lineNode;
  },
  normalizeNode: function(node) {
    if (dom(node).isTextNode()) {
      return node;
    }
    _.each(Normalizer.ATTRIBUTES, function(style, attribute) {
      var value;
      if (node.hasAttribute(attribute)) {
        value = node.getAttribute(attribute);
        if (attribute === 'size') {
          value = dom.convertFontSize(value);
        }
        node.style[style] = value;
        return node.removeAttribute(attribute);
      }
    });
    Normalizer.whitelistStyles(node);
    return Normalizer.whitelistTags(node);
  },
  optimizeLine: function(lineNode) {
    var lineNodeLength, node, nodes, _results;
    lineNodeLength = dom(lineNode).length();
    nodes = dom(lineNode).descendants();
    _results = [];
    while (nodes.length > 0) {
      node = nodes.pop();
      if ((node != null ? node.parentNode : void 0) == null) {
        continue;
      }
      if (dom.EMBED_TAGS[node.tagName] != null) {
        continue;
      }
      if (node.tagName === dom.DEFAULT_BREAK_TAG) {
        if (lineNodeLength !== 0) {
          _results.push(dom(node).remove());
        } else {
          _results.push(void 0);
        }
      } else if (dom(node).length() === 0) {
        nodes.push(node.nextSibling);
        _results.push(dom(node).unwrap());
      } else if ((node.previousSibling != null) && node.tagName === node.previousSibling.tagName) {
        if (_.isEqual(dom(node).attributes(), dom(node.previousSibling).attributes())) {
          nodes.push(node.firstChild);
          _results.push(dom(node.previousSibling).merge(node));
        } else {
          _results.push(void 0);
        }
      } else {
        _results.push(void 0);
      }
    }
    return _results;
  },
  pullBlocks: function(lineNode) {
    var curNode;
    curNode = lineNode.firstChild;
    while (curNode != null) {
      if ((dom.BLOCK_TAGS[curNode.tagName] != null) && curNode.tagName !== 'LI') {
        if (curNode.previousSibling != null) {
          dom(curNode).splitAncestors(lineNode.parentNode);
        }
        if (curNode.nextSibling != null) {
          dom(curNode.nextSibling).splitAncestors(lineNode.parentNode);
        }
        if ((dom.LIST_TAGS[curNode.tagName] == null) || !curNode.firstChild) {
          dom(curNode).unwrap();
          Normalizer.pullBlocks(lineNode);
        } else {
          dom(curNode.parentNode).unwrap();
          if (lineNode.parentNode == null) {
            lineNode = curNode;
          }
        }
        break;
      }
      curNode = curNode.nextSibling;
    }
    return lineNode;
  },
  stripComments: function(html) {
    return html.replace(/<!--[\s\S]*?-->/g, '');
  },
  stripWhitespace: function(html) {
    html = html.replace(/^\s+/, '').replace(/\s+$/, '');
    html = html.replace(/^\s+/, '').replace(/\s+$/, '');
    html = html.replace(/(\r?\n|\r)+/g, ' ');
    html = html.replace(/\>\s+\</g, '><');
    return html;
  },
  whitelistStyles: function(node) {
    var original, styles;
    original = dom(node).styles();
    styles = _.omit(original, function(value, key) {
      return Normalizer.STYLES[key] == null;
    });
    if (_.keys(styles).length < _.keys(original).length) {
      if (_.keys(styles).length > 0) {
        return dom(node).styles(styles, true);
      } else {
        return node.removeAttribute('style');
      }
    }
  },
  whitelistTags: function(node) {
    if (!dom(node).isElement()) {
      return node;
    }
    if (Normalizer.ALIASES[node.tagName] != null) {
      node = dom(node).switchTag(Normalizer.ALIASES[node.tagName]);
    } else if (Normalizer.TAGS[node.tagName] == null) {
      if (dom.BLOCK_TAGS[node.tagName] != null) {
        node = dom(node).switchTag(dom.DEFAULT_BLOCK_TAG);
      } else if (!node.hasAttributes() && (node.firstChild != null)) {
        node = dom(node).unwrap();
      } else {
        node = dom(node).switchTag(dom.DEFAULT_INLINE_TAG);
      }
    }
    return node;
  },
  wrapInline: function(lineNode) {
    var blockNode, nextNode;
    if (dom.BLOCK_TAGS[lineNode.tagName] != null) {
      return lineNode;
    }
    blockNode = lineNode.ownerDocument.createElement(dom.DEFAULT_BLOCK_TAG);
    lineNode.parentNode.insertBefore(blockNode, lineNode);
    while ((lineNode != null) && (dom.BLOCK_TAGS[lineNode.tagName] == null)) {
      nextNode = lineNode.nextSibling;
      blockNode.appendChild(lineNode);
      lineNode = nextNode;
    }
    return blockNode;
  },
  unwrapText: function(lineNode) {
    var spans;
    spans = _.map(lineNode.querySelectorAll(dom.DEFAULT_INLINE_TAG));
    return _.each(spans, function(span) {
      if (!span.hasAttributes()) {
        return dom(span).unwrap();
      }
    });
  }
};

module.exports = Normalizer;



},{"./dom":22,"lodash":"M4+//f"}],25:[function(_dereq_,module,exports){
var Normalizer, Picker, dom, _;

_ = _dereq_('lodash');

dom = _dereq_('./dom');

Normalizer = _dereq_('./normalizer');

Picker = (function() {
  Picker.TEMPLATE = '<span class="ql-picker-label"></span><span class="ql-picker-options"></span>';

  function Picker(select) {
    this.select = select;
    this.container = this.select.ownerDocument.createElement('span');
    this.buildPicker();
    dom(this.container).addClass('ql-picker');
    this.select.style.display = 'none';
    this.select.parentNode.insertBefore(this.container, this.select);
    dom(this.select.ownerDocument).on('click', (function(_this) {
      return function() {
        _this.close();
        return true;
      };
    })(this));
    dom(this.label).on('click', (function(_this) {
      return function() {
        _.defer(function() {
          return dom(_this.container).toggleClass('ql-expanded');
        });
        return false;
      };
    })(this));
    dom(this.select).on('change', (function(_this) {
      return function() {
        var item, option;
        if (_this.select.selectedIndex > -1) {
          item = _this.container.querySelectorAll('.ql-picker-item')[_this.select.selectedIndex];
          option = _this.select.options[_this.select.selectedIndex];
        }
        _this.selectItem(item, false);
        return dom(_this.label).toggleClass('ql-active', option !== dom(_this.select)["default"]());
      };
    })(this));
  }

  Picker.prototype.buildItem = function(picker, option, index) {
    var item;
    item = this.select.ownerDocument.createElement('span');
    item.setAttribute('data-value', option.getAttribute('value'));
    dom(item).addClass('ql-picker-item').text(dom(option).text()).on('click', (function(_this) {
      return function() {
        _this.selectItem(item, true);
        return _this.close();
      };
    })(this));
    if (this.select.selectedIndex === index) {
      this.selectItem(item, false);
    }
    return item;
  };

  Picker.prototype.buildPicker = function() {
    var picker;
    _.each(dom(this.select).attributes(), (function(_this) {
      return function(value, name) {
        return _this.container.setAttribute(name, value);
      };
    })(this));
    this.container.innerHTML = Normalizer.stripWhitespace(Picker.TEMPLATE);
    this.label = this.container.querySelector('.ql-picker-label');
    picker = this.container.querySelector('.ql-picker-options');
    return _.each(this.select.options, (function(_this) {
      return function(option, i) {
        var item;
        item = _this.buildItem(picker, option, i);
        return picker.appendChild(item);
      };
    })(this));
  };

  Picker.prototype.close = function() {
    return dom(this.container).removeClass('ql-expanded');
  };

  Picker.prototype.selectItem = function(item, trigger) {
    var selected, value;
    selected = this.container.querySelector('.ql-selected');
    if (selected != null) {
      dom(selected).removeClass('ql-selected');
    }
    if (item != null) {
      value = item.getAttribute('data-value');
      dom(item).addClass('ql-selected');
      dom(this.label).text(dom(item).text());
      dom(this.select).option(value, trigger);
      return this.label.setAttribute('data-value', value);
    } else {
      this.label.innerHTML = '&nbsp;';
      return this.label.removeAttribute('data-value');
    }
  };

  return Picker;

})();

module.exports = Picker;



},{"./dom":22,"./normalizer":24,"lodash":"M4+//f"}],26:[function(_dereq_,module,exports){
var Range, _;

_ = _dereq_('lodash');

Range = (function() {
  Range.compare = function(r1, r2) {
    if (r1 === r2) {
      return true;
    }
    if (!((r1 != null) && (r2 != null))) {
      return false;
    }
    return r1.equals(r2);
  };

  function Range(start, end) {
    this.start = start;
    this.end = end;
  }

  Range.prototype.equals = function(range) {
    if (range == null) {
      return false;
    }
    return this.start === range.start && this.end === range.end;
  };

  Range.prototype.shift = function(index, length) {
    var _ref;
    return _ref = _.map([this.start, this.end], function(pos) {
      if (index > pos) {
        return pos;
      }
      if (length >= 0) {
        return pos + length;
      } else {
        return Math.max(index, pos + length);
      }
    }), this.start = _ref[0], this.end = _ref[1], _ref;
  };

  Range.prototype.isCollapsed = function() {
    return this.start === this.end;
  };

  return Range;

})();

module.exports = Range;



},{"lodash":"M4+//f"}],27:[function(_dereq_,module,exports){
var Authorship, Quill, Tandem, dom, _;

Quill = _dereq_('../quill');

_ = Quill.require('lodash');

dom = Quill.require('dom');

Tandem = Quill.require('tandem-core');

Authorship = (function() {
  Authorship.DEFAULTS = {
    authorId: null,
    color: 'transparent',
    enabled: false
  };

  function Authorship(quill, options) {
    this.quill = quill;
    this.options = options;
    if (this.options.button != null) {
      this.attachButton(this.options.button);
    }
    if (this.options.enabled) {
      this.enable();
    }
    this.quill.addFormat('author', {
      "class": 'author-'
    });
    if (this.options.authorId == null) {
      return;
    }
    this.quill.on(this.quill.constructor.events.PRE_EVENT, (function(_this) {
      return function(eventName, delta, origin) {
        var attribute, authorDelta;
        if (eventName === _this.quill.constructor.events.TEXT_CHANGE && origin === 'user') {
          _.each(delta.ops, function(op) {
            if (Tandem.InsertOp.isInsert(op) || _.keys(op.attributes).length > 0) {
              return op.attributes['author'] = _this.options.authorId;
            }
          });
          authorDelta = new Tandem.Delta(delta.endLength, [new Tandem.RetainOp(0, delta.endLength)]);
          attribute = {
            author: _this.options.authorId
          };
          delta.apply(function(index, text) {
            return authorDelta = authorDelta.compose(Tandem.Delta.makeRetainDelta(delta.endLength, index, text.length, attribute));
          }, (function() {}), function(index, length, name, value) {
            return authorDelta = authorDelta.compose(Tandem.Delta.makeRetainDelta(delta.endLength, index, length, attribute));
          });
          return _this.quill.updateContents(authorDelta, 'silent');
        }
      };
    })(this));
    this.addAuthor(this.options.authorId, this.options.color);
  }

  Authorship.prototype.addAuthor = function(id, color) {
    var styles;
    styles = {};
    styles[".authorship .author-" + id] = {
      "background-color": "" + color
    };
    return this.quill.addStyles(styles);
  };

  Authorship.prototype.attachButton = function(button) {
    var $button;
    $button = dom(button);
    return $button.on('click', (function(_this) {
      return function() {
        $button.toggleClass('ql-on');
        return _this.enable($dom.hasClass('ql-on'));
      };
    })(this));
  };

  Authorship.prototype.enable = function(enabled) {
    if (enabled == null) {
      enabled = true;
    }
    return dom(this.quill.root).toggleClass('authorship', enabled);
  };

  Authorship.prototype.disable = function() {
    return this.enable(false);
  };

  return Authorship;

})();

Quill.registerModule('authorship', Authorship);

module.exports = Authorship;



},{"../quill":36}],28:[function(_dereq_,module,exports){
var ImageTooltip, Quill, Tandem, Tooltip, dom, _,
  __hasProp = {}.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

Quill = _dereq_('../quill');

Tooltip = _dereq_('./tooltip');

_ = Quill.require('lodash');

dom = Quill.require('dom');

Tandem = Quill.require('tandem-core');

ImageTooltip = (function(_super) {
  __extends(ImageTooltip, _super);

  ImageTooltip.DEFAULTS = {
    styles: {
      '.image-tooltip-container': {
        'margin': '25px',
        'padding': '10px',
        'width': '300px'
      },
      '.image-tooltip-container:after': {
        'clear': 'both',
        'content': '""',
        'display': 'table'
      },
      '.image-tooltip-container .preview': {
        'margin': '10px 0px',
        'position': 'relative',
        'border': '1px dashed #000',
        'height': '200px'
      },
      '.image-tooltip-container .preview span': {
        'display': 'inline-block',
        'position': 'absolute',
        'text-align': 'center',
        'top': '40%',
        'width': '100%'
      },
      '.image-tooltip-container img': {
        'bottom': '0',
        'left': '0',
        'margin': 'auto',
        'max-height': '100%',
        'max-width': '100%',
        'position': 'absolute',
        'right': '0',
        'top': '0'
      },
      '.image-tooltip-container .input': {
        'box-sizing': 'border-box',
        'width': '100%'
      },
      '.image-tooltip-container a': {
        'border': '1px solid black',
        'box-sizing': 'border-box',
        'display': 'inline-block',
        'float': 'left',
        'padding': '5px',
        'text-align': 'center',
        'width': '50%'
      }
    },
    template: '<input class="input" type="textbox"> <div class="preview"> <span>Preview</span> </div> <a href="javascript:;" class="cancel">Cancel</a> <a href="javascript:;" class="insert">Insert</a>'
  };

  function ImageTooltip(quill, options) {
    this.quill = quill;
    this.options = options;
    this.options.styles = _.defaults(this.options.styles, Tooltip.DEFAULTS.styles);
    this.options = _.defaults(this.options, Tooltip.DEFAULTS);
    ImageTooltip.__super__.constructor.call(this, this.quill, this.options);
    this.preview = this.container.querySelector('.preview');
    this.textbox = this.container.querySelector('.input');
    dom(this.container).addClass('image-tooltip-container');
    this.initListeners();
  }

  ImageTooltip.prototype.initListeners = function() {
    dom(this.container.querySelector('.insert')).on('click', _.bind(this.insertImage, this));
    dom(this.container.querySelector('.cancel')).on('click', _.bind(this.hide, this));
    dom(this.textbox).on('input', _.bind(this._preview, this));
    this.initTextbox(this.textbox, this.insertImage, this.hide);
    return this.quill.onModuleLoad('toolbar', (function(_this) {
      return function(toolbar) {
        return toolbar.initFormat('image', _.bind(_this._onToolbar, _this));
      };
    })(this));
  };

  ImageTooltip.prototype.insertImage = function() {
    var index, url;
    url = this._normalizeURL(this.textbox.value);
    if (this.range == null) {
      this.range = new Range(0, 0);
    }
    if (this.range) {
      this.preview.innerHTML = '<span>Preview</span>';
      this.textbox.value = '';
      index = this.range.end;
      this.quill.insertEmbed(index, 'image', url, 'user');
      this.quill.setSelection(index + 1, index + 1);
    }
    return this.hide();
  };

  ImageTooltip.prototype._onToolbar = function(range, value) {
    if (value) {
      if (!this.textbox.value) {
        this.textbox.value = 'http://';
      }
      this.show();
      this.textbox.focus();
      return _.defer((function(_this) {
        return function() {
          return _this.textbox.setSelectionRange(_this.textbox.value.length, _this.textbox.value.length);
        };
      })(this));
    } else {
      return this.quill.deleteText(range, 'user');
    }
  };

  ImageTooltip.prototype._preview = function() {
    var img;
    if (!this._matchImageURL(this.textbox.value)) {
      return;
    }
    if (this.preview.firstChild.tagName === 'IMG') {
      return this.preview.firstChild.setAttribute('src', this.textbox.value);
    } else {
      img = this.preview.ownerDocument.createElement('img');
      img.setAttribute('src', this.textbox.value);
      return this.preview.replaceChild(img, this.preview.firstChild);
    }
  };

  ImageTooltip.prototype._matchImageURL = function(url) {
    return /^https?:\/\/.+\.(jp?g|gif|png)$/.test(url);
  };

  ImageTooltip.prototype._normalizeURL = function(url) {
    if (!/^https?:\/\//.test(url)) {
      url = 'http://' + url;
    }
    return url;
  };

  return ImageTooltip;

})(Tooltip);

Quill.registerModule('image-tooltip', ImageTooltip);

module.exports = ImageTooltip;



},{"../quill":36,"./tooltip":34}],29:[function(_dereq_,module,exports){
var Keyboard, Quill, Tandem, dom, _;

Quill = _dereq_('../quill');

_ = Quill.require('lodash');

dom = Quill.require('dom');

Tandem = Quill.require('tandem-core');

Keyboard = (function() {
  Keyboard.hotkeys = {
    BOLD: {
      key: 'B',
      metaKey: true
    },
    INDENT: {
      key: dom.KEYS.TAB
    },
    ITALIC: {
      key: 'I',
      metaKey: true
    },
    OUTDENT: {
      key: dom.KEYS.TAB,
      shiftKey: true
    },
    UNDERLINE: {
      key: 'U',
      metaKey: true
    }
  };

  function Keyboard(quill, options) {
    this.quill = quill;
    this.hotkeys = {};
    this._initListeners();
    this._initHotkeys();
    this._initDeletes();
  }

  Keyboard.prototype.addHotkey = function(hotkey, callback) {
    var which, _base;
    hotkey = _.isObject(hotkey) ? _.clone(hotkey) : {
      key: hotkey
    };
    hotkey.callback = callback;
    which = _.isNumber(hotkey.key) ? hotkey.key : hotkey.key.toUpperCase().charCodeAt(0);
    if ((_base = this.hotkeys)[which] == null) {
      _base[which] = [];
    }
    return this.hotkeys[which].push(hotkey);
  };

  Keyboard.prototype.toggleFormat = function(range, format) {
    var delta, toolbar, value;
    if (range.isCollapsed()) {
      delta = this.quill.getContents(Math.max(0, range.start - 1), range.end);
    } else {
      delta = this.quill.getContents(range);
    }
    value = delta.ops.length === 0 || !_.all(delta.ops, function(op) {
      return op.attributes[format];
    });
    if (range.isCollapsed()) {
      this.quill.prepareFormat(format, value);
    } else {
      this.quill.formatText(range, format, value, 'user');
    }
    toolbar = this.quill.getModule('toolbar');
    if (toolbar != null) {
      return toolbar.setActive(format, value);
    }
  };

  Keyboard.prototype._initDeletes = function() {
    return _.each([dom.KEYS.DELETE, dom.KEYS.BACKSPACE], (function(_this) {
      return function(key) {
        return _this.addHotkey(key, function() {
          return _this.quill.getLength() > 1;
        });
      };
    })(this));
  };

  Keyboard.prototype._initHotkeys = function() {
    this.addHotkey(Keyboard.hotkeys.INDENT, (function(_this) {
      return function(range) {
        _this._onTab(range, false);
        return false;
      };
    })(this));
    this.addHotkey(Keyboard.hotkeys.OUTDENT, (function(_this) {
      return function(range) {
        return false;
      };
    })(this));
    return _.each(['bold', 'italic', 'underline'], (function(_this) {
      return function(format) {
        return _this.addHotkey(Keyboard.hotkeys[format.toUpperCase()], function(range) {
          _this.toggleFormat(range, format);
          return false;
        });
      };
    })(this));
  };

  Keyboard.prototype._initListeners = function() {
    return dom(this.quill.root).on('keydown', (function(_this) {
      return function(event) {
        var prevent;
        prevent = false;
        _.each(_this.hotkeys[event.which], function(hotkey) {
          var metaKey;
          metaKey = dom.isMac() ? event.metaKey : event.metaKey || event.ctrlKey;
          if (!!hotkey.metaKey !== !!metaKey) {
            return;
          }
          if (!!hotkey.shiftKey !== !!event.shiftKey) {
            return;
          }
          if (!!hotkey.altKey !== !!event.altKey) {
            return;
          }
          return prevent = hotkey.callback(_this.quill.getSelection()) === false || prevent;
        });
        return !prevent;
      };
    })(this));
  };

  Keyboard.prototype._onTab = function(range, shift) {
    var delta;
    if (shift == null) {
      shift = false;
    }
    delta = Tandem.Delta.makeDelta({
      startLength: this.quill.getLength(),
      ops: [
        {
          start: 0,
          end: range.start
        }, {
          value: "\t"
        }, {
          start: range.end,
          end: this.quill.getLength()
        }
      ]
    });
    this.quill.updateContents(delta);
    return this.quill.setSelection(range.start + 1, range.start + 1);
  };

  return Keyboard;

})();

Quill.registerModule('keyboard', Keyboard);

module.exports = Keyboard;



},{"../quill":36}],30:[function(_dereq_,module,exports){
var LinkTooltip, Quill, Tooltip, dom, _,
  __hasProp = {}.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

Quill = _dereq_('../quill');

Tooltip = _dereq_('./tooltip');

_ = Quill.require('lodash');

dom = Quill.require('dom');

LinkTooltip = (function(_super) {
  __extends(LinkTooltip, _super);

  LinkTooltip.DEFAULTS = {
    maxLength: 50,
    styles: {
      '.link-tooltip-container': {
        'padding': '5px 10px'
      },
      '.link-tooltip-container input.input': {
        'width': '170px'
      },
      '.link-tooltip-container input.input, .link-tooltip-container a.done, .link-tooltip-container.editing a.url, .link-tooltip-container.editing a.change': {
        'display': 'none'
      },
      '.link-tooltip-container.editing input.input, .link-tooltip-container.editing a.done': {
        'display': 'inline-block'
      }
    },
    template: '<span class="title">Visit URL:&nbsp;</span> <a href="#" class="url" target="_blank" href="about:blank"></a> <input class="input" type="text"> <span>&nbsp;&#45;&nbsp;</span> <a href="javascript:;" class="change">Change</a> <a href="javascript:;" class="done">Done</a>'
  };

  function LinkTooltip(quill, options) {
    this.quill = quill;
    this.options = options;
    this.options.styles = _.defaults(this.options.styles, Tooltip.DEFAULTS.styles);
    this.options = _.defaults(this.options, Tooltip.DEFAULTS);
    LinkTooltip.__super__.constructor.call(this, this.quill, this.options);
    dom(this.container).addClass('link-tooltip-container');
    this.textbox = this.container.querySelector('.input');
    this.link = this.container.querySelector('.url');
    this.initListeners();
  }

  LinkTooltip.prototype.initListeners = function() {
    this.quill.on(this.quill.constructor.events.SELECTION_CHANGE, (function(_this) {
      return function(range) {
        var anchor;
        if (!((range != null) && range.isCollapsed())) {
          return;
        }
        anchor = _this._findAnchor(range);
        if (anchor) {
          _this.setMode(anchor.href, false);
          return _this.show(anchor);
        } else {
          _this.range = null;
          return _this.hide();
        }
      };
    })(this));
    dom(this.container.querySelector('.done')).on('click', _.bind(this.saveLink, this));
    dom(this.container.querySelector('.change')).on('click', (function(_this) {
      return function() {
        return _this.setMode(_this.link.href, true);
      };
    })(this));
    this.initTextbox(this.textbox, this.saveLink, this.hide);
    return this.quill.onModuleLoad('toolbar', (function(_this) {
      return function(toolbar) {
        return toolbar.initFormat('link', _.bind(_this._onToolbar, _this));
      };
    })(this));
  };

  LinkTooltip.prototype.saveLink = function() {
    var anchor, url;
    url = this._normalizeURL(this.textbox.value);
    if (this.range != null) {
      if (this.range.isCollapsed()) {
        anchor = this._findAnchor(this.range);
        if (anchor != null) {
          anchor.href = url;
        }
      } else {
        this.quill.formatText(this.range, 'link', url, 'user');
      }
    }
    return this.setMode(url, false);
  };

  LinkTooltip.prototype.setMode = function(url, edit) {
    var text;
    if (edit == null) {
      edit = false;
    }
    if (edit) {
      this.textbox.value = url;
      _.defer((function(_this) {
        return function() {
          _this.textbox.focus();
          return _this.textbox.setSelectionRange(url.length, url.length);
        };
      })(this));
    } else {
      this.link.href = url;
      text = url.length > this.options.maxLength ? url.slice(0, this.options.maxLength) + '...' : url;
      dom(this.link).text(text);
    }
    return dom(this.container).toggleClass('editing', edit);
  };

  LinkTooltip.prototype._findAnchor = function(range) {
    var leaf, node, offset, _ref;
    _ref = this.quill.editor.doc.findLeafAt(range.start, true), leaf = _ref[0], offset = _ref[1];
    if (leaf != null) {
      node = leaf.node;
    }
    while (node != null) {
      if (node.tagName === 'A') {
        return node;
      }
      node = node.parentNode;
    }
    return null;
  };

  LinkTooltip.prototype._onToolbar = function(range, value) {
    var nativeRange;
    if (!(range && !range.isCollapsed())) {
      return;
    }
    if (value) {
      this.setMode(this._suggestURL(range), true);
      nativeRange = this.quill.editor.selection._getNativeRange();
      return this.show(nativeRange);
    } else {
      return this.quill.formatText(range, 'link', false, 'user');
    }
  };

  LinkTooltip.prototype._normalizeURL = function(url) {
    if (!/^(https?:\/\/|mailto:)/.test(url)) {
      url = 'http://' + url;
    }
    return url;
  };

  LinkTooltip.prototype._suggestURL = function(range) {
    var text;
    text = this.quill.getText(range);
    return this._normalizeURL(text);
  };

  return LinkTooltip;

})(Tooltip);

Quill.registerModule('link-tooltip', LinkTooltip);

module.exports = LinkTooltip;



},{"../quill":36,"./tooltip":34}],31:[function(_dereq_,module,exports){
var EventEmitter2, MultiCursor, Quill, dom, _,
  __hasProp = {}.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

Quill = _dereq_('../quill');

EventEmitter2 = _dereq_('eventemitter2').EventEmitter2;

_ = Quill.require('lodash');

dom = Quill.require('dom');

MultiCursor = (function(_super) {
  __extends(MultiCursor, _super);

  MultiCursor.DEFAULTS = {
    template: '<span class="cursor-flag"> <span class="cursor-name"></span> </span> <span class="cursor-caret"></span>',
    timeout: 2500
  };

  MultiCursor.events = {
    CURSOR_ADDED: 'cursor-addded',
    CURSOR_MOVED: 'cursor-moved',
    CURSOR_REMOVED: 'cursor-removed'
  };

  function MultiCursor(quill, options) {
    this.quill = quill;
    this.options = options;
    this.cursors = {};
    this.container = this.quill.addContainer('cursor-container', true);
    this.quill.addStyles({
      '.cursor-container': {
        'position': 'absolute',
        'left': '0',
        'top': '0',
        'z-index': '1000'
      },
      '.cursor': {
        'margin-left': '-1px',
        'position': 'absolute'
      },
      '.cursor-flag': {
        'bottom': '100%',
        'position': 'absolute',
        'white-space': 'nowrap'
      },
      '.cursor-name': {
        'display': 'inline-block',
        'color': 'white',
        'padding': '2px 8px'
      },
      '.cursor-caret': {
        'height': '100%',
        'position': 'absolute',
        'width': '2px'
      },
      '.cursor.hidden .cursor-flag': {
        'display': 'none'
      },
      '.cursor.top > .cursor-flag': {
        'bottom': 'auto',
        'top': '100%'
      },
      '.cursor.right > .cursor-flag': {
        'right': '-2px'
      }
    });
    this.quill.on(this.quill.constructor.events.TEXT_CHANGE, _.bind(this._applyDelta, this));
  }

  MultiCursor.prototype.clearCursors = function() {
    _.each(_.keys(this.cursors), _.bind(this.removeCursor, this));
    return this.cursors = {};
  };

  MultiCursor.prototype.moveCursor = function(userId, index) {
    var cursor;
    cursor = this.cursors[userId];
    cursor.index = index;
    dom(cursor.elem).removeClass('hidden');
    clearTimeout(cursor.timer);
    cursor.timer = setTimeout((function(_this) {
      return function() {
        dom(cursor.elem).addClass('hidden');
        return cursor.timer = null;
      };
    })(this), this.options.timeout);
    this._updateCursor(cursor);
    return cursor;
  };

  MultiCursor.prototype.removeCursor = function(userId) {
    var cursor;
    cursor = this.cursors[userId];
    this.emit(MultiCursor.events.CURSOR_REMOVED, cursor);
    if (cursor != null) {
      cursor.elem.parentNode.removeChild(cursor.elem);
    }
    return delete this.cursors[userId];
  };

  MultiCursor.prototype.setCursor = function(userId, index, name, color) {
    var cursor;
    if (this.cursors[userId] == null) {
      this.cursors[userId] = cursor = {
        userId: userId,
        index: index,
        color: color,
        elem: this._buildCursor(name, color)
      };
      this.emit(MultiCursor.events.CURSOR_ADDED, cursor);
    }
    _.defer((function(_this) {
      return function() {
        return _this.moveCursor(userId, index);
      };
    })(this));
    return this.cursors[userId];
  };

  MultiCursor.prototype.shiftCursors = function(index, length, authorId) {
    if (authorId == null) {
      authorId = null;
    }
    return _.each(this.cursors, (function(_this) {
      return function(cursor, id) {
        if (!(cursor && (cursor.index > index || cursor.userId === authorId))) {
          return;
        }
        return cursor.index += Math.max(length, index - cursor.index);
      };
    })(this));
  };

  MultiCursor.prototype.update = function() {
    return _.each(this.cursors, (function(_this) {
      return function(cursor, id) {
        if (cursor == null) {
          return;
        }
        _this._updateCursor(cursor);
        return true;
      };
    })(this));
  };

  MultiCursor.prototype._applyDelta = function(delta) {
    delta.apply((function(_this) {
      return function(index, text, formatting) {
        return _this.shiftCursors(index, text.length, formatting['author']);
      };
    })(this), (function(_this) {
      return function(index, length) {
        return _this.shiftCursors(index, -1 * length, null);
      };
    })(this), (function(_this) {
      return function(index, length, name, value) {
        return _this.shiftCursors(index, 0, null);
      };
    })(this));
    return this.update();
  };

  MultiCursor.prototype._buildCursor = function(name, color) {
    var cursor, cursorCaret, cursorFlag, cursorName;
    cursor = this.container.ownerDocument.createElement('span');
    dom(cursor).addClass('cursor');
    cursor.innerHTML = this.options.template;
    cursorFlag = cursor.querySelector('.cursor-flag');
    cursorName = cursor.querySelector('.cursor-name');
    dom(cursorName).text(name);
    cursorCaret = cursor.querySelector('.cursor-caret');
    cursorCaret.style.backgroundColor = cursorName.style.backgroundColor = color;
    this.container.appendChild(cursor);
    return cursor;
  };

  MultiCursor.prototype._moveCursor = function(cursor, reference, side) {
    var bounds, flag, win;
    if (side == null) {
      side = 'left';
    }
    win = dom(reference).window();
    bounds = reference.getBoundingClientRect();
    cursor.elem.style.top = bounds.top + win.pageYOffset + 'px';
    cursor.elem.style.left = bounds[side] + 'px';
    cursor.elem.style.height = bounds.height + 'px';
    flag = cursor.elem.querySelector('.cursor-flag');
    dom(cursor.elem).toggleClass('top', parseInt(cursor.elem.style.top) <= flag.offsetHeight).toggleClass('left', parseInt(cursor.elem.style.left) <= flag.offsetWidth).toggleClass('right', this.quill.root.offsetWidth - parseInt(cursor.elem.style.left) <= flag.offsetWidth);
    return this.emit(MultiCursor.events.CURSOR_MOVED, cursor);
  };

  MultiCursor.prototype._updateCursor = function(cursor) {
    var didSplit, guide, leaf, leftNode, offset, rightNode, _ref, _ref1;
    this.quill.editor.checkUpdate();
    _ref = this.quill.editor.doc.findLeafAt(cursor.index, true), leaf = _ref[0], offset = _ref[1];
    guide = this.container.ownerDocument.createElement('span');
    if (leaf != null) {
      _ref1 = dom(leaf.node).split(offset), leftNode = _ref1[0], rightNode = _ref1[1], didSplit = _ref1[2];
      dom(guide).text(dom.ZERO_WIDTH_NOBREAK_SPACE);
      leaf.node.parentNode.insertBefore(guide, rightNode);
    } else {
      dom(guide).text(dom.NOBREAK_SPACE);
      this.quill.root.appendChild(guide);
    }
    this._moveCursor(cursor, guide);
    dom(guide).remove();
    if (didSplit) {
      dom(leaf.node.parentNode).normalize();
    }
    return this.quill.editor.selection.update('silent');
  };

  return MultiCursor;

})(EventEmitter2);

Quill.registerModule('multi-cursor', MultiCursor);

module.exports = MultiCursor;



},{"../quill":36,"eventemitter2":3}],32:[function(_dereq_,module,exports){
var Document, PasteManager, Quill, Tandem, dom, _;

Quill = _dereq_('../quill');

Document = _dereq_('../core/document');

_ = Quill.require('lodash');

dom = Quill.require('dom');

Tandem = Quill.require('tandem-core');

PasteManager = (function() {
  function PasteManager(quill, options) {
    this.quill = quill;
    this.options = options;
    this.container = this.quill.addContainer('paste-container');
    this.container.setAttribute('contenteditable', true);
    this.quill.addStyles({
      '.paste-container': {
        'left': '-10000px',
        'position': 'absolute',
        'top': '50%'
      }
    });
    dom(this.quill.root).on('paste', _.bind(this._paste, this));
  }

  PasteManager.prototype._paste = function() {
    var iframe, oldDocLength, range, scrollY;
    oldDocLength = this.quill.getLength();
    range = this.quill.getSelection();
    if (range == null) {
      return;
    }
    this.container.innerHTML = "";
    iframe = dom(this.quill.root).window();
    scrollY = iframe.scrollY;
    this.container.focus();
    return _.defer((function(_this) {
      return function() {
        var delta, doc, lengthAdded, line, lineBottom, offset, _ref;
        doc = new Document(_this.container, _this.quill.options);
        delta = doc.toDelta();
        delta = delta.compose(Tandem.Delta.makeDeleteDelta(delta.endLength, delta.endLength - 1, 1));
        lengthAdded = delta.endLength;
        if (range.start > 0) {
          delta.ops.unshift(new Tandem.RetainOp(0, range.start));
        }
        if (range.end < oldDocLength) {
          delta.ops.push(new Tandem.RetainOp(range.end, oldDocLength));
        }
        delta.endLength += _this.quill.getLength() - (range.end - range.start);
        delta.startLength = oldDocLength;
        _this.quill.updateContents(delta, 'user');
        _this.quill.setSelection(range.start + lengthAdded, range.start + lengthAdded);
        _ref = _this.quill.editor.doc.findLineAt(range.start + lengthAdded), line = _ref[0], offset = _ref[1];
        lineBottom = line.node.offsetTop + line.node.offsetHeight;
        if (lineBottom > scrollY + _this.quill.root.offsetHeight) {
          scrollY = line.node.offsetTop - _this.quill.root.offsetHeight / 2;
        }
        return iframe.scrollTo(0, scrollY);
      };
    })(this));
  };

  return PasteManager;

})();

Quill.registerModule('paste-manager', PasteManager);

module.exports = PasteManager;



},{"../core/document":13,"../quill":36}],33:[function(_dereq_,module,exports){
var Quill, Toolbar, dom, _;

Quill = _dereq_('../quill');

_ = Quill.require('lodash');

dom = Quill.require('dom');

Toolbar = (function() {
  Toolbar.DEFAULTS = {
    container: null
  };

  Toolbar.formats = {
    LINE: {
      'align': 'align',
      'bullet': 'bullet',
      'list': 'list'
    },
    SELECT: {
      'align': 'align',
      'background': 'background',
      'color': 'color',
      'font': 'font',
      'size': 'size'
    },
    TOGGLE: {
      'bold': 'bold',
      'bullet': 'bullet',
      'image': 'image',
      'italic': 'italic',
      'link': 'link',
      'list': 'list',
      'strike': 'strike',
      'underline': 'underline'
    },
    TOOLTIP: {
      'image': 'image',
      'link': 'link'
    }
  };

  function Toolbar(quill, options) {
    this.quill = quill;
    this.options = options;
    if (this.options.container == null) {
      throw new Error('container required for toolbar', this.options);
    }
    this.container = _.isString(this.options.container) ? document.querySelector(this.options.container) : this.options.container;
    this.inputs = {};
    this.preventUpdate = false;
    this.triggering = false;
    _.each(this.quill.options.formats, (function(_this) {
      return function(format) {
        if (Toolbar.formats.TOOLTIP[format] != null) {
          return;
        }
        return _this.initFormat(format, function(range, value) {
          if (_this.triggering) {
            return;
          }
          if (range.isCollapsed()) {
            _this.quill.prepareFormat(format, value);
          } else if (Toolbar.formats.LINE[format] != null) {
            _this.quill.formatLine(range, format, value, 'user');
          } else {
            _this.quill.formatText(range, format, value, 'user');
          }
          return _.defer(function() {
            _this.updateActive(range);
            return _this.setActive(format, value);
          });
        });
      };
    })(this));
    this.quill.on(this.quill.constructor.events.SELECTION_CHANGE, _.bind(this.updateActive, this));
    dom(this.container).addClass('ql-toolbar-container');
    if (dom.isIOS()) {
      dom(this.container).addClass('ios');
    }
    if (dom.isIE(11)) {
      dom(this.container).on('mousedown', (function(_this) {
        return function() {
          return false;
        };
      })(this));
    }
  }

  Toolbar.prototype.initFormat = function(format, callback) {
    var eventName, input, selector;
    selector = ".ql-" + format;
    if (Toolbar.formats.SELECT[format] != null) {
      selector = "select" + selector;
      eventName = 'change';
    } else {
      eventName = 'click';
    }
    input = this.container.querySelector(selector);
    if (input == null) {
      return;
    }
    this.inputs[format] = input;
    return dom(input).on(eventName, (function(_this) {
      return function() {
        var range, value;
        value = eventName === 'change' ? dom(input).value() : !dom(input).hasClass('ql-active');
        _this.preventUpdate = true;
        _this.quill.focus();
        range = _this.quill.getSelection();
        if (range != null) {
          callback(range, value);
        }
        _this.preventUpdate = false;
        return true;
      };
    })(this));
  };

  Toolbar.prototype.setActive = function(format, value) {
    var $input, input, selectValue;
    input = this.inputs[format];
    if (input == null) {
      return;
    }
    $input = dom(input);
    if (input.tagName === 'SELECT') {
      this.triggering = true;
      selectValue = $input.value(input);
      if (_.isArray(value)) {
        value = '';
      }
      if (value !== selectValue) {
        if (value != null) {
          $input.option(value);
        } else {
          $input.reset();
        }
      }
      return this.triggering = false;
    } else {
      return $input.toggleClass('ql-active', value || false);
    }
  };

  Toolbar.prototype.updateActive = function(range) {
    var activeFormats;
    if (!((range != null) && !this.preventUpdate)) {
      return;
    }
    activeFormats = this._getActive(range);
    return _.each(this.inputs, (function(_this) {
      return function(input, format) {
        _this.setActive(format, activeFormats[format]);
        return true;
      };
    })(this));
  };

  Toolbar.prototype._getActive = function(range) {
    var leafFormats, lineFormats;
    leafFormats = this._getLeafActive(range);
    lineFormats = this._getLineActive(range);
    return _.defaults({}, leafFormats, lineFormats);
  };

  Toolbar.prototype._getLeafActive = function(range) {
    var contents, formatsArr, line, offset, _ref;
    if (range.isCollapsed()) {
      _ref = this.quill.editor.doc.findLineAt(range.start), line = _ref[0], offset = _ref[1];
      if (offset === 0) {
        contents = this.quill.getContents(range.start, range.end + 1);
      } else {
        contents = this.quill.getContents(range.start - 1, range.end);
      }
    } else {
      contents = this.quill.getContents(range);
    }
    formatsArr = _.map(contents.ops, 'attributes');
    return this._intersectFormats(formatsArr);
  };

  Toolbar.prototype._getLineActive = function(range) {
    var firstLine, formatsArr, lastLine, offset, _ref, _ref1;
    formatsArr = [];
    _ref = this.quill.editor.doc.findLineAt(range.start), firstLine = _ref[0], offset = _ref[1];
    _ref1 = this.quill.editor.doc.findLineAt(range.end), lastLine = _ref1[0], offset = _ref1[1];
    if ((lastLine != null) && lastLine === firstLine) {
      lastLine = lastLine.next;
    }
    while ((firstLine != null) && firstLine !== lastLine) {
      formatsArr.push(_.clone(firstLine.formats));
      firstLine = firstLine.next;
    }
    return this._intersectFormats(formatsArr);
  };

  Toolbar.prototype._intersectFormats = function(formatsArr) {
    return _.reduce(formatsArr.slice(1), function(activeFormats, formats) {
      var activeKeys, added, formatKeys, intersection, missing;
      activeKeys = _.keys(activeFormats);
      formatKeys = _.keys(formats);
      intersection = _.intersection(activeKeys, formatKeys);
      missing = _.difference(activeKeys, formatKeys);
      added = _.difference(formatKeys, activeKeys);
      _.each(intersection, function(name) {
        if (Toolbar.formats.SELECT[name] != null) {
          if (_.isArray(activeFormats[name])) {
            if (_.indexOf(activeFormats[name], formats[name]) < 0) {
              return activeFormats[name].push(formats[name]);
            }
          } else if (activeFormats[name] !== formats[name]) {
            return activeFormats[name] = [activeFormats[name], formats[name]];
          }
        }
      });
      _.each(missing, function(name) {
        if (Toolbar.formats.TOGGLE[name] != null) {
          return delete activeFormats[name];
        } else if ((Toolbar.formats.SELECT[name] != null) && !_.isArray(activeFormats[name])) {
          return activeFormats[name] = [activeFormats[name]];
        }
      });
      _.each(added, function(name) {
        if (Toolbar.formats.SELECT[name] != null) {
          return activeFormats[name] = [formats[name]];
        }
      });
      return activeFormats;
    }, formatsArr[0] || {});
  };

  return Toolbar;

})();

Quill.registerModule('toolbar', Toolbar);

module.exports = Toolbar;



},{"../quill":36}],34:[function(_dereq_,module,exports){
var Normalizer, Quill, Tooltip, dom, _;

Quill = _dereq_('../quill');

Normalizer = _dereq_('../lib/normalizer');

_ = Quill.require('lodash');

dom = Quill.require('dom');

Tooltip = (function() {
  Tooltip.DEFAULTS = {
    offset: 10,
    styles: {
      '.tooltip': {
        'background-color': '#fff',
        'border': '1px solid #000',
        'top': '0px',
        'white-space': 'nowrap',
        'z-index': '2000'
      },
      '.tooltip a': {
        'cursor': 'pointer',
        'text-decoration': 'none'
      }
    },
    template: ''
  };

  Tooltip.HIDE_MARGIN = '-10000px';

  function Tooltip(quill, options) {
    this.quill = quill;
    this.options = options;
    this.quill.addStyles(this.options.styles);
    this.container = this.quill.addContainer('tooltip');
    this.container.innerHTML = Normalizer.stripWhitespace(this.options.template);
    this.container.style.position = 'absolute';
    dom(this.quill.root).on('focus', _.bind(this.hide, this));
    this.hide();
    this.quill.on(this.quill.constructor.events.TEXT_CHANGE, (function(_this) {
      return function(delta, source) {
        if (source === 'user' && _this.container.style.left !== Tooltip.HIDE_MARGIN) {
          _this.range = null;
          return _this.hide();
        }
      };
    })(this));
  }

  Tooltip.prototype.initTextbox = function(textbox, enterCallback, escapeCallback) {
    return dom(textbox).on('keyup', (function(_this) {
      return function(event) {
        switch (event.which) {
          case dom.KEYS.ENTER:
            return enterCallback.call(_this);
          case dom.KEYS.ESCAPE:
            return escapeCallback.call(_this);
          default:
            return true;
        }
      };
    })(this));
  };

  Tooltip.prototype.hide = function() {
    this.container.style.left = Tooltip.HIDE_MARGIN;
    if (this.range) {
      this.quill.setSelection(this.range);
    }
    return this.range = null;
  };

  Tooltip.prototype.show = function(reference) {
    var left, top, win, _ref, _ref1;
    this.range = this.quill.getSelection();
    _ref = this._position(reference), left = _ref[0], top = _ref[1];
    _ref1 = this._limit(left, top), left = _ref1[0], top = _ref1[1];
    win = dom(this.quill.root).window();
    left += win.pageXOffset;
    top += win.pageYOffset;
    this.container.style.left = "" + left + "px";
    this.container.style.top = "" + top + "px";
    return this.container.focus();
  };

  Tooltip.prototype._getBounds = function() {
    var bounds, scrollX, scrollY, win;
    bounds = this.quill.root.getBoundingClientRect();
    win = dom(this.quill.root).window();
    scrollX = win.pageXOffset;
    scrollY = win.pageYOffset;
    return {
      left: bounds.left + scrollX,
      right: bounds.right + scrollX,
      top: bounds.top + scrollY,
      bottom: bounds.bottom + scrollY,
      width: bounds.width,
      height: bounds.height
    };
  };

  Tooltip.prototype._limit = function(left, top) {
    var editorRect, toolbarRect;
    editorRect = this._getBounds();
    toolbarRect = this.container.getBoundingClientRect();
    left = Math.min(editorRect.right - toolbarRect.width, left);
    left = Math.max(editorRect.left, left);
    top = Math.min(editorRect.bottom - toolbarRect.height, top);
    top = Math.max(editorRect.top, top);
    return [left, top];
  };

  Tooltip.prototype._position = function(reference) {
    var editorRect, left, referenceBounds, toolbarRect, top;
    toolbarRect = this.container.getBoundingClientRect();
    editorRect = this._getBounds();
    if (reference != null) {
      referenceBounds = reference.getBoundingClientRect();
      left = referenceBounds.left + referenceBounds.width / 2 - toolbarRect.width / 2;
      top = referenceBounds.top + referenceBounds.height + this.options.offset;
      if (top + toolbarRect.height > editorRect.bottom) {
        top = referenceBounds.top - toolbarRect.height - this.options.offset;
      }
    } else {
      left = editorRect.left + editorRect.width / 2 - toolbarRect.width / 2;
      top = editorRect.top + editorRect.height / 2 - toolbarRect.height / 2;
    }
    return [left, top];
  };

  return Tooltip;

})();

Quill.registerModule('tooltip', Tooltip);

module.exports = Tooltip;



},{"../lib/normalizer":24,"../quill":36}],35:[function(_dereq_,module,exports){
var Quill, Tandem, UndoManager, _;

Quill = _dereq_('../quill');

_ = Quill.require('lodash');

Tandem = Quill.require('tandem-core');

UndoManager = (function() {
  UndoManager.DEFAULTS = {
    delay: 1000,
    maxStack: 100
  };

  UndoManager.hotkeys = {
    UNDO: {
      key: 'Z',
      metaKey: true
    },
    REDO: {
      key: 'Z',
      metaKey: true,
      shiftKey: true
    }
  };

  function UndoManager(quill, options) {
    this.quill = quill;
    this.options = options != null ? options : {};
    this.lastRecorded = 0;
    this.emittedDelta = null;
    this.clear();
    this.initListeners();
  }

  UndoManager.prototype.initListeners = function() {
    this.quill.onModuleLoad('keyboard', (function(_this) {
      return function(keyboard) {
        keyboard.addHotkey(UndoManager.hotkeys.UNDO, function() {
          _this.undo();
          return false;
        });
        return keyboard.addHotkey(UndoManager.hotkeys.REDO, function() {
          _this.redo();
          return false;
        });
      };
    })(this));
    return this.quill.on(this.quill.constructor.events.TEXT_CHANGE, (function(_this) {
      return function(delta, origin) {
        if (delta.isEqual(_this.emittedDelta)) {
          _this.emittedDelta = null;
          return;
        }
        _this.record(delta, _this.oldDelta);
        return _this.oldDelta = _this.quill.getContents();
      };
    })(this));
  };

  UndoManager.prototype.clear = function() {
    this.stack = {
      undo: [],
      redo: []
    };
    return this.oldDelta = this.quill.getContents();
  };

  UndoManager.prototype.record = function(changeDelta, oldDelta) {
    var change, ignored, timestamp, undoDelta;
    if (changeDelta.isIdentity()) {
      return;
    }
    this.stack.redo = [];
    try {
      undoDelta = oldDelta.invert(changeDelta);
      timestamp = new Date().getTime();
      if (this.lastRecorded + this.options.delay > timestamp && this.stack.undo.length > 0) {
        change = this.stack.undo.pop();
        if (undoDelta.canCompose(change.undo) && change.redo.canCompose(changeDelta)) {
          undoDelta = undoDelta.compose(change.undo);
          changeDelta = change.redo.compose(changeDelta);
        } else {
          this.clear();
          this.lastRecorded = timestamp;
        }
      } else {
        this.lastRecorded = timestamp;
      }
      this.stack.undo.push({
        redo: changeDelta,
        undo: undoDelta
      });
      if (this.stack.undo.length > this.options.maxStack) {
        return this.stack.undo.unshift();
      }
    } catch (_error) {
      ignored = _error;
      return this.clear();
    }
  };

  UndoManager.prototype.redo = function() {
    return this._change('redo', 'undo');
  };

  UndoManager.prototype.undo = function() {
    return this._change('undo', 'redo');
  };

  UndoManager.prototype._getLastChangeIndex = function(delta) {
    var lastIndex;
    lastIndex = 0;
    delta.apply(function(index, text) {
      return lastIndex = Math.max(index + text.length, lastIndex);
    }, function(index, length) {
      return lastIndex = Math.max(index, lastIndex);
    }, function(index, length) {
      return lastIndex = Math.max(index + length, lastIndex);
    });
    return lastIndex;
  };

  UndoManager.prototype._change = function(source, dest) {
    var change, index;
    if (this.stack[source].length > 0) {
      change = this.stack[source].pop();
      this.lastRecorded = 0;
      this.emittedDelta = change[source];
      this.quill.updateContents(change[source], 'user');
      this.emittedDelta = null;
      index = this._getLastChangeIndex(change[source]);
      this.quill.setSelection(index, index);
      return this.stack[dest].push(change);
    }
  };

  return UndoManager;

})();

Quill.registerModule('undo-manager', UndoManager);

module.exports = UndoManager;



},{"../quill":36}],36:[function(_dereq_,module,exports){
var Editor, EventEmitter2, Format, Quill, Range, Tandem, dom, pkg, _,
  __hasProp = {}.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; },
  __slice = [].slice;

_ = _dereq_('lodash');

pkg = _dereq_('../package.json');

EventEmitter2 = _dereq_('eventemitter2').EventEmitter2;

dom = _dereq_('./lib/dom');

Editor = _dereq_('./core/editor');

Format = _dereq_('./core/format');

Range = _dereq_('./lib/range');

Tandem = _dereq_('tandem-core');

Quill = (function(_super) {
  __extends(Quill, _super);

  Quill.version = pkg.version;

  Quill.editors = [];

  Quill.modules = [];

  Quill.themes = [];

  Quill.DEFAULTS = {
    formats: ['align', 'bold', 'italic', 'strike', 'underline', 'color', 'background', 'font', 'size', 'link', 'image', 'bullet', 'list'],
    modules: {
      'keyboard': true,
      'paste-manager': true,
      'undo-manager': true
    },
    pollInterval: 100,
    readOnly: false,
    theme: 'default'
  };

  Quill.events = {
    MODULE_INIT: 'module-init',
    POST_EVENT: 'post-event',
    PRE_EVENT: 'pre-event',
    SELECTION_CHANGE: 'selection-change',
    TEXT_CHANGE: 'text-change'
  };

  Quill.sources = {
    API: 'api',
    SILENT: 'silent',
    USER: 'user'
  };

  Quill.registerModule = function(name, module) {
    if (Quill.modules[name] != null) {
      console.warn("Overwriting " + name + " module");
    }
    return Quill.modules[name] = module;
  };

  Quill.registerTheme = function(name, theme) {
    if (Quill.themes[name] != null) {
      console.warn("Overwriting " + name + " theme");
    }
    return Quill.themes[name] = theme;
  };

  Quill.require = function(name) {
    switch (name) {
      case 'lodash':
        return _;
      case 'dom':
        return dom;
      case 'tandem-core':
        return Tandem;
      default:
        return null;
    }
  };

  function Quill(container, options) {
    var html, moduleOptions, themeClass;
    if (options == null) {
      options = {};
    }
    if (_.isString(container)) {
      container = document.querySelector(container);
    }
    if (container == null) {
      throw new Error('Invalid Quill container');
    }
    moduleOptions = _.defaults(options.modules || {}, Quill.DEFAULTS.modules);
    html = container.innerHTML;
    this.options = _.defaults(options, Quill.DEFAULTS);
    this.options.modules = moduleOptions;
    this.options.id = this.id = "quill-" + (Quill.editors.length + 1);
    this.options.emitter = this;
    this.modules = {};
    this.editor = new Editor(container, this, this.options);
    this.root = this.editor.doc.root;
    Quill.editors.push(this);
    this.setHTML(html, Quill.sources.SILENT);
    themeClass = Quill.themes[this.options.theme];
    if (themeClass == null) {
      throw new Error("Cannot load " + this.options.theme + " theme. Are you sure you registered it?");
    }
    this.theme = new themeClass(this, this.options);
    _.each(this.options.modules, (function(_this) {
      return function(option, name) {
        return _this.addModule(name, option);
      };
    })(this));
  }

  Quill.prototype.addContainer = function(className, before) {
    if (before == null) {
      before = false;
    }
    return this.editor.renderer.addContainer(className, before);
  };

  Quill.prototype.addFormat = function(name, format) {
    return this.editor.doc.addFormat(name, format);
  };

  Quill.prototype.addModule = function(name, options) {
    var moduleClass;
    moduleClass = Quill.modules[name];
    if (moduleClass == null) {
      throw new Error("Cannot load " + name + " module. Are you sure you registered it?");
    }
    if (!_.isObject(options)) {
      options = {};
    }
    options = _.defaults(options, this.theme.constructor.OPTIONS[name] || {}, moduleClass.DEFAULTS || {});
    this.modules[name] = new moduleClass(this, options);
    this.emit(Quill.events.MODULE_INIT, name, this.modules[name]);
    return this.modules[name];
  };

  Quill.prototype.addStyles = function(styles) {
    return this.editor.renderer.addStyles(styles);
  };

  Quill.prototype.deleteText = function(start, end, source) {
    var delta, formats, _ref;
    if (source == null) {
      source = Quill.sources.API;
    }
    _ref = this._buildParams(start, end, {}, source), start = _ref[0], end = _ref[1], formats = _ref[2], source = _ref[3];
    if (!(end > start)) {
      return;
    }
    delta = Tandem.Delta.makeDeleteDelta(this.getLength(), start, end - start);
    return this.editor.applyDelta(delta, source);
  };

  Quill.prototype.emit = function() {
    var args, eventName;
    eventName = arguments[0], args = 2 <= arguments.length ? __slice.call(arguments, 1) : [];
    Quill.__super__.emit.apply(this, [Quill.events.PRE_EVENT, eventName].concat(__slice.call(args)));
    Quill.__super__.emit.apply(this, [eventName].concat(__slice.call(args)));
    return Quill.__super__.emit.apply(this, [Quill.events.POST_EVENT, eventName].concat(__slice.call(args)));
  };

  Quill.prototype.focus = function() {
    return this.editor.focus();
  };

  Quill.prototype.formatLine = function(start, end, name, value, source) {
    var formats, line, offset, _ref, _ref1;
    _ref = this._buildParams(start, end, name, value, source), start = _ref[0], end = _ref[1], formats = _ref[2], source = _ref[3];
    _ref1 = this.editor.doc.findLineAt(end), line = _ref1[0], offset = _ref1[1];
    if (line != null) {
      end += line.length - offset;
    }
    return this.formatText(start, end, formats, source);
  };

  Quill.prototype.formatText = function(start, end, name, value, source) {
    var delta, formats, _ref;
    _ref = this._buildParams(start, end, name, value, source), start = _ref[0], end = _ref[1], formats = _ref[2], source = _ref[3];
    formats = _.reduce(formats, (function(_this) {
      return function(formats, value, name) {
        var format;
        format = _this.editor.doc.formats[name];
        if (!(value && value !== format.config["default"])) {
          formats[name] = null;
        }
        return formats;
      };
    })(this), formats);
    delta = Tandem.Delta.makeRetainDelta(this.getLength(), start, end - start, formats);
    return this.editor.applyDelta(delta, source);
  };

  Quill.prototype.getContents = function(start, end) {
    var ops;
    if (start == null) {
      start = 0;
    }
    if (end == null) {
      end = null;
    }
    if (_.isObject(start)) {
      end = start.end;
      start = start.start;
    } else {
      if (end == null) {
        end = this.getLength();
      }
    }
    ops = this.editor.getDelta().getOpsAt(start, end - start);
    return new Tandem.Delta(0, ops);
  };

  Quill.prototype.getHTML = function() {
    return this.root.innerHTML;
  };

  Quill.prototype.getLength = function() {
    return this.editor.getDelta().endLength;
  };

  Quill.prototype.getModule = function(name) {
    return this.modules[name];
  };

  Quill.prototype.getSelection = function() {
    this.editor.checkUpdate();
    return this.editor.selection.getRange();
  };

  Quill.prototype.getText = function(start, end) {
    if (start == null) {
      start = 0;
    }
    if (end == null) {
      end = null;
    }
    return _.pluck(this.getContents(start, end).ops, 'value').join('');
  };

  Quill.prototype.insertEmbed = function(index, type, url, source) {
    return this.insertText(index, dom.EMBED_TEXT, type, url, source);
  };

  Quill.prototype.insertText = function(index, text, name, value, source) {
    var delta, end, formats, _ref;
    _ref = this._buildParams(index, 0, name, value, source), index = _ref[0], end = _ref[1], formats = _ref[2], source = _ref[3];
    if (!(text.length > 0)) {
      return;
    }
    delta = Tandem.Delta.makeInsertDelta(this.getLength(), index, text, formats);
    return this.editor.applyDelta(delta, source);
  };

  Quill.prototype.onModuleLoad = function(name, callback) {
    if (this.modules[name]) {
      return callback(this.modules[name]);
    }
    return this.on(Quill.events.MODULE_INIT, function(moduleName, module) {
      if (moduleName === name) {
        return callback(module);
      }
    });
  };

  Quill.prototype.prepareFormat = function(name, value) {
    var format, range;
    format = this.editor.doc.formats[name];
    if (format == null) {
      return;
    }
    range = this.getSelection();
    if (!(range != null ? range.isCollapsed() : void 0)) {
      return;
    }
    if (format.isType(Format.types.LINE)) {
      return this.formatLine(range, name, value, Quill.sources.USER);
    } else {
      return format.prepare(value);
    }
  };

  Quill.prototype.setContents = function(delta, source) {
    if (source == null) {
      source = Quill.sources.API;
    }
    if (_.isArray(delta)) {
      delta = {
        startLength: this.getLength(),
        ops: delta
      };
    } else {
      delta.startLength = this.getLength();
    }
    return this.updateContents(delta, source);
  };

  Quill.prototype.setHTML = function(html, source) {
    if (source == null) {
      source = Quill.sources.API;
    }
    if (!html) {
      html = "<" + dom.DEFAULT_BLOCK_TAG + "><" + dom.DEFAULT_BREAK_TAG + "></" + dom.DEFAULT_BLOCK_TAG + ">";
    }
    this.editor.doc.setHTML(html);
    return this.editor.checkUpdate(source);
  };

  Quill.prototype.setSelection = function(start, end, source) {
    var range;
    if (source == null) {
      source = Quill.sources.API;
    }
    if (_.isNumber(start) && _.isNumber(end)) {
      range = new Range(start, end);
    } else {
      range = start;
      source = end || source;
    }
    return this.editor.selection.setRange(range, source);
  };

  Quill.prototype.updateContents = function(delta, source) {
    if (source == null) {
      source = Quill.sources.API;
    }
    delta = Tandem.Delta.makeDelta(delta);
    return this.editor.applyDelta(delta, source);
  };

  Quill.prototype._buildParams = function() {
    var formats, params;
    params = 1 <= arguments.length ? __slice.call(arguments, 0) : [];
    if (_.isObject(params[0])) {
      params.splice(0, 1, params[0].start, params[0].end);
    }
    if (_.isString(params[2])) {
      formats = {};
      formats[params[2]] = params[3];
      params.splice(2, 2, formats);
    }
    if (params[3] == null) {
      params[3] = Quill.sources.API;
    }
    return params;
  };

  return Quill;

})(EventEmitter2);

Quill.registerTheme('default', _dereq_('./themes/default'));

Quill.registerTheme('snow', _dereq_('./themes/snow'));

module.exports = Quill;



},{"../package.json":12,"./core/editor":14,"./core/format":15,"./lib/dom":22,"./lib/range":26,"./themes/default":37,"./themes/snow":38,"eventemitter2":3,"lodash":"M4+//f","tandem-core":10}],37:[function(_dereq_,module,exports){
var DefaultTheme;

DefaultTheme = (function() {
  DefaultTheme.OPTIONS = {};

  function DefaultTheme(quill) {
    this.quill = quill;
    this.editor = this.quill.editor;
    this.editorContainer = this.editor.root;
  }

  return DefaultTheme;

})();

module.exports = DefaultTheme;



},{}],38:[function(_dereq_,module,exports){
var ColorPicker, DefaultTheme, Picker, SnowTheme, dom, _,
  __hasProp = {}.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

_ = _dereq_('lodash');

ColorPicker = _dereq_('../../lib/color-picker');

DefaultTheme = _dereq_('../default');

dom = _dereq_('../../lib/dom');

Picker = _dereq_('../../lib/picker');

SnowTheme = (function(_super) {
  __extends(SnowTheme, _super);

  SnowTheme.COLORS = ["#000000", "#e60000", "#ff9900", "#ffff00", "#008A00", "#0066cc", "#9933ff", "#ffffff", "#facccc", "#ffebcc", "#ffffcc", "#cce8cc", "#cce0f5", "#ebd6ff", "#bbbbbb", "#f06666", "#ffc266", "#ffff66", "#66b966", "#66a3e0", "#c285ff", "#888888", "#a10000", "#b26b00", "#b2b200", "#006100", "#0047b2", "#6b24b2", "#444444", "#5c0000", "#663d00", "#666600", "#003700", "#002966", "#3d1466"];

  SnowTheme.OPTIONS = {
    'multi-cursor': {
      template: '<span class="cursor-flag"> <span class="cursor-triangle top"></span> <span class="cursor-name"></span> <span class="cursor-triangle bottom"></span> </span> <span class="cursor-caret"></span>'
    }
  };

  SnowTheme.STYLES = {
    '.snow .image-tooltip-container a': {
      'border': '1px solid #06c'
    },
    '.snow .image-tooltip-container a.insert': {
      'background-color': '#06c',
      'color': '#fff'
    },
    '.snow .cursor-name': {
      'border-radius': '4px',
      'font-size': '11px',
      'font-family': 'Arial',
      'margin-left': '-50%',
      'padding': '4px 10px'
    },
    '.snow .cursor-triangle': {
      'border-left': '4px solid transparent',
      'border-right': '4px solid transparent',
      'height': '0px',
      'margin-left': '-3px',
      'width': '0px'
    },
    '.snow .cursor.left .cursor-name': {
      'margin-left': '-8px'
    },
    '.snow .cursor.right .cursor-flag': {
      'right': 'auto'
    },
    '.snow .cursor.right .cursor-name': {
      'margin-left': '-100%',
      'margin-right': '-8px'
    },
    '.snow .cursor-triangle.bottom': {
      'border-top': '4px solid transparent',
      'display': 'block',
      'margin-bottom': '-1px'
    },
    '.snow .cursor-triangle.top': {
      'border-bottom': '4px solid transparent',
      'display': 'none',
      'margin-top': '-1px'
    },
    '.snow .cursor.top .cursor-triangle.bottom': {
      'display': 'none'
    },
    '.snow .cursor.top .cursor-triangle.top': {
      'display': 'block'
    },
    '.snow a': {
      'color': '#06c'
    },
    '.snow .tooltip': {
      'border': '1px solid #ccc',
      'box-shadow': '0px 0px 5px #ddd',
      'color': '#222'
    },
    '.snow .tooltip a': {
      'color': '#06c'
    },
    '.snow .tooltip .input': {
      'border': '1px solid #ccc',
      'margin': '0px',
      'padding': '5px'
    },
    '.snow .image-tooltip-container .preview': {
      'border-color': '#ccc',
      'color': '#ccc'
    },
    '.snow .link-tooltip-container a, .snow .link-tooltip-container span': {
      'display': 'inline-block',
      'line-height': '25px'
    }
  };

  function SnowTheme(quill) {
    this.quill = quill;
    SnowTheme.__super__.constructor.apply(this, arguments);
    this.quill.addStyles(SnowTheme.STYLES);
    this.pickers = [];
    this.quill.on(this.quill.constructor.events.SELECTION_CHANGE, (function(_this) {
      return function(range) {
        if (range != null) {
          return _.invoke(_this.pickers, 'close');
        }
      };
    })(this));
    dom(this.quill.root.ownerDocument.body).addClass('snow');
    this.quill.onModuleLoad('multi-cursor', _.bind(this.extendMultiCursor, this));
    this.quill.onModuleLoad('toolbar', _.bind(this.extendToolbar, this));
  }

  SnowTheme.prototype.extendMultiCursor = function(module) {
    return module.on(module.constructor.events.CURSOR_ADDED, function(cursor) {
      var bottomTriangle, topTriangle;
      bottomTriangle = cursor.elem.querySelector('.cursor-triangle.bottom');
      topTriangle = cursor.elem.querySelector('.cursor-triangle.top');
      return bottomTriangle.style.borderTopColor = topTriangle.style.borderBottomColor = cursor.color;
    });
  };

  SnowTheme.prototype.extendToolbar = function(module) {
    _.each(['color', 'background', 'font', 'size', 'align'], (function(_this) {
      return function(format) {
        var picker, select;
        select = module.container.querySelector(".ql-" + format);
        if (select == null) {
          return;
        }
        switch (format) {
          case 'font':
          case 'size':
          case 'align':
            picker = new Picker(select);
            break;
          case 'color':
          case 'background':
            picker = new ColorPicker(select);
            _.each(picker.container.querySelectorAll('.ql-picker-item'), function(item, i) {
              if (i < 7) {
                return dom(item).addClass('ql-primary-color');
              }
            });
        }
        if (picker != null) {
          return _this.pickers.push(picker);
        }
      };
    })(this));
    return _.each(dom(module.container).textNodes(), function(node) {
      if (dom(node).text().trim().length === 0) {
        return dom(node).remove();
      }
    });
  };

  return SnowTheme;

})(DefaultTheme);

module.exports = SnowTheme;



},{"../../lib/color-picker":21,"../../lib/dom":22,"../../lib/picker":25,"../default":37,"lodash":"M4+//f"}]},{},[20])
(20)
});