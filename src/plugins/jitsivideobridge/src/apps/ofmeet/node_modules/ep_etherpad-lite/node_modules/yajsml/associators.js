/*!

  Copyright (c) 2011 Chad Weider

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  SOFTWARE.

*/

function hasOwnProperty(o, k) {
  return Object.prototype.hasOwnProperty.call(o, k);
}

/*
  Produce fully structured module mapings from a simple description.

  INPUT:
  { '/module/path/1.js':
    [ '/module/path/1.js'
    , '/module/path/2.js'
    , '/module/path/3.js'
    , '/module/path/4.js'
    ]
  , '/module/path/4.js':
    [ '/module/path/3.js'
    , '/module/path/4.js'
    , '/module/path/5.js'
    ]
  }

  OUTPUT:
  [ { '/module/path/1.js':
      [ '/module/path/1.js'
      , '/module/path/2.js'
      , '/module/path/3.js'
      , '/module/path/4.js'
      ]
    , '/module/path/4.js':
      [ '/module/path/3.js'
      , '/module/path/4.js'
      , '/module/path/5.js'
      ]
    }
  , { '/module/path/1.js': '/module/path/1.js'
    , '/module/path/2.js': '/module/path/1.js'
    , '/module/path/3.js': '/module/path/4.js'
    , '/module/path/4.js': '/module/path/4.js'
    , '/module/path/5.js': '/module/path/4.js'
    }
  ]

*/
function associationsForSimpleMapping(mapping) {
  var packageModuleMap = {};
  var modulePackageMap = {};

  for (var primaryKey in mapping) {
    if (hasOwnProperty(packageModuleMap, primaryKey)) {
      throw new Error("A packaging is for the primary key "
        + JSON.stringify(primaryKey) + " is already defined.");
    } else {
      var modules = mapping[primaryKey].concat([]);
      packageModuleMap[primaryKey] = modules;
      modules.forEach(function (key) {
        // Don't overwrite in this case.
        if (!mapping.hasOwnProperty(key) || key == primaryKey) {
          modulePackageMap[key] = primaryKey;
        }
      });
    }
  }
  return [packageModuleMap, modulePackageMap];
}


/*
  Inverse of `associationsForComplexMapping`.

  INPUT:
  [ { '/module/path/1.js':
      [ '/module/path/1.js'
      , '/module/path/2.js'
      , '/module/path/3.js'
      , '/module/path/4.js'
      ]
    , '/module/path/4.js':
      [ '/module/path/3.js'
      , '/module/path/4.js'
      , '/module/path/5.js'
      ]
    }
  , { '/module/path/1.js': '/module/path/1.js'
    , '/module/path/2.js': '/module/path/1.js'
    , '/module/path/3.js': '/module/path/4.js'
    , '/module/path/4.js': '/module/path/4.js'
    , '/module/path/5.js': '/module/path/4.js'
    }
  ]

  OUTPUT:
  [ [ '/module/path/1.js'
    , '/module/path/4.js'
    ]
  , { '/module/path/1.js': [0, [true, false]]
    , '/module/path/2.js': [0, [true, false]]
    , '/module/path/3.js': [1, [true, true]]
    , '/module/path/4.js': [1, [true, true]]
    , '/module/path/5.js': [1, [false, true]]
    }
  ]
*/
function complexMappingForAssociations(associations) {
  var packageModuleMap = associations[0];
  var modulePackageMap = associations[1];

  var packages = [];
  var mapping = {};

  for (var key in packageModuleMap) {
    packages.push(key);
  }

  var blankMapping = [];
  for (var i = 0, ii = packages.length; i < ii; i++) {
    blankMapping[i] = false;
  }
  for (var i = 0, ii = packages.length; i < ii; i++) {
    packageModuleMap[packages[i]].forEach(function (key) {
      if (!hasOwnProperty(mapping, key)) {
        mapping[key] = [i, blankMapping.concat([])];
      }
      mapping[key][0] = i;
      mapping[key][1][i] = true;
    });
  }

  return [packages, mapping];
}

/*
  Produce fully structured module mapings from association description.

  INPUT:
  [ [ '/module/path/1.js'
    , '/module/path/4.js'
    ]
  , { '/module/path/1.js': [0, [true, false]]
    , '/module/path/2.js': [0, [true, false]]
    , '/module/path/3.js': [1, [true, true]]
    , '/module/path/4.js': [1, [true, true]]
    , '/module/path/5.js': [1, [false, true]]
    }
  ]

  OUTPUT:
  [ { '/module/path/1.js':
      [ '/module/path/1.js'
      , '/module/path/2.js'
      , '/module/path/3.js'
      , '/module/path/4.js'
      ]
    , '/module/path/4.js':
      [ '/module/path/3.js'
      , '/module/path/4.js'
      , '/module/path/5.js'
      ]
    }
  , { '/module/path/1.js': '/module/path/1.js'
    , '/module/path/2.js': '/module/path/1.js'
    , '/module/path/3.js': '/module/path/4.js'
    , '/module/path/4.js': '/module/path/4.js'
    , '/module/path/5.js': '/module/path/4.js'
    }
  ]
*/
function associationsForComplexMapping(packages, associations) {
  var packageSet = {};
  packages.forEach(function (package, i) {
    if (package === undefined) {
      // BAD: Package has no purpose.
    } else if (hasOwnProperty(packageSet, package)) {
      // BAD: Duplicate package.
    } else if (!hasOwnProperty(associations, package)) {
      // BAD: Package primary doesn't exist for this package
    } else if (associations[package][0] != i) {
      // BAD: Package primary doesn't agree
    }
    packageSet[package] = true;
  })

  var packageModuleMap = {};
  var modulePackageMap = {};
  for (var path in associations) {
    if (hasOwnProperty(associations, path)) {
      var association = associations[path];

      modulePackageMap[path] = packages[association[0]];
      association[1].forEach(function (include, i) {
        if (include) {
          var package = packages[i];
          if (!hasOwnProperty(packageModuleMap, package)) {
            packageModuleMap[package] = [];
          }
          packageModuleMap[package].push(path);
        }
      });
    }
  }

  return [packageModuleMap, modulePackageMap];
}

/*
  I determine which modules are associated with one another for a JS module
  server.

  INPUT:
  [ { '/module/path/1.js':
      [ '/module/path/1.js'
      , '/module/path/2.js'
      , '/module/path/3.js'
      , '/module/path/4.js'
      ]
    , '/module/path/4.js':
      [ '/module/path/3.js'
      , '/module/path/4.js'
      , '/module/path/5.js'
      ]
    }
  , { '/module/path/1.js': '/module/path/1.js'
    , '/module/path/2.js': '/module/path/1.js'
    , '/module/path/3.js': '/module/path/4.js'
    , '/module/path/4.js': '/module/path/4.js'
    , '/module/path/5.js': '/module/path/4.js'
    }
  ]
*/
function StaticAssociator(associations, next) {
  this._packageModuleMap = associations[0];
  this._modulePackageMap = associations[1];
  this._next = next || new IdentityAssociator();
}
StaticAssociator.prototype = new function () {
  function preferredPath(modulePath) {
    if (hasOwnProperty(this._modulePackageMap, modulePath)) {
      return this._modulePackageMap[modulePath];
    } else {
      return this._next.preferredPath(modulePath);
    }
  }
  function associatedModulePaths(modulePath) {
    var modulePath = this.preferredPath(modulePath);
    if (hasOwnProperty(this._packageModuleMap, modulePath)) {
      return this._packageModuleMap[modulePath];
    } else {
      return this._next.associatedModulePaths(modulePath);
    }
  }
  this.preferredPath = preferredPath;
  this.associatedModulePaths = associatedModulePaths;
}();

function IdentityAssociator() {
  // empty
}
IdentityAssociator.prototype = new function () {
  function preferredPath(modulePath) {
    return modulePath;
  }
  function associatedModulePaths(modulePath) {
    return [modulePath];
  }
  this.preferredPath = preferredPath;
  this.associatedModulePaths = associatedModulePaths;
}

function SimpleAssociator() {
  // empty
}
SimpleAssociator.prototype = new function () {
  function preferredPath(modulePath) {
    return this.associatedModulePaths(modulePath)[0];
  }
  function associatedModulePaths(modulePath) {
    var modulePath = modulePath.replace(/\.js$|(?:^|\/)index\.js$|.\/+$/, '');
    return [modulePath, modulePath + '.js', modulePath + '/index.js'];
  }
  this.preferredPath = preferredPath;
  this.associatedModulePaths = associatedModulePaths;
}

exports.StaticAssociator = StaticAssociator;
exports.IdentityAssociator = IdentityAssociator;
exports.SimpleAssociator = SimpleAssociator;

exports.associationsForSimpleMapping = associationsForSimpleMapping;
exports.complexMappingForAssociations = complexMappingForAssociations;
exports.associationsForComplexMapping = associationsForComplexMapping;
