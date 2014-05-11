// The MIT License
//
// Copyright (c) 2013 Tim Smart
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files
// (the "Software"), to deal in the Software without restriction,
// including without limitation the rights to use, copy, modify, merge,
// publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
// OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
// IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
// CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
// TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
// SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

var net    = require('net'),
    utils  = require('./utils'),
    Parser = require('./parser');

var RedisClient = function RedisClient(port, host, auth) {
  this.host           = host;
  this.port           = port;
  this.auth           = auth;
  this.stream         = net.createConnection(port, host);
  this.connected      = false;
  // Pub/sub monitor etc.
  this.blocking       = false;
  // Command queue.
  this.max_size       = 1300;
  this.command        = '';
  this.commands       = new utils.Queue();
  // For the retry timer.
  this.retry          = false;
  this.retry_attempts = 0;
  this.retry_delay    = 250;
  this.retry_backoff  = 1.7;
  // If we want to quit.
  this.quitting       = false;
  // For when we have a full send buffer.
  this.paused         = false;
  this.send_buffer    = [];
  this.flushing       = false;
  // channels / patterns for disconnects
  this.pubsub         = { pattern : {}, channel : {} }

  var self = this;

  this.stream.on("connect", function () {
    // Reset the retry backoff.
    self.retry          = false;
    self.retry_delay    = 250;
    self.retry_attempts = 0;
    self.stream.setNoDelay();
    self.stream.setTimeout(0);
    self.connected      = true;

    // Resend commands if we need to.
    var command,
        commands = self.commands.array.slice(self.commands.offset);

    // Send auth.
    if (self.auth) {
      commands.unshift(['AUTH', [self.auth], null]);
    }

    self.commands = new utils.Queue();

    for (var i = 0, il = commands.length; i < il; i++) {
      command = commands[i];
      self.sendCommand(command[0], command[1], command[2]);
    }

    // pubsub?
    var patterns = Object.keys(self.pubsub.pattern)
    var channels = Object.keys(self.pubsub.channel)

    for (var i = 0, il = patterns.length; i < il; i++) {
      self.psubscribe(patterns[i])
    }
    for (var i = 0, il = channels.length; i < il; i++) {
      self.subscribe(channels[i])
    }

    // give connect listeners a chance to run first in case they need to auth
    self.emit("connect");
  });

  this.stream.on("data", function (buffer) {
    try {
      self.parser.onIncoming(buffer);
    } catch (err) {
      self.emit("error", err);
      // Reset state.
      self.parser.resetState();
    }
  });

  // _write
  // So we can pipeline requests.
  this._flush = function () {
    if ('' !== self.command) {
      self.send_buffer.push(self.command);
      self.command = '';
    }

    for (var i = 0, il = self.send_buffer.length; i < il; i++) {
      if (!self.stream.writable || false === self.stream.write(self.send_buffer[i])) {
        return self.send_buffer = self.send_buffer.slice(i + 1);
      }
    }

    self.send_buffer.length = 0;
    self.paused = self.flushing = false;
  };

  // When we can write more.
  this.stream.on('drain', this._flush);

  this.stream.on("error", function (error) {
    if ('ECONNREFUSED' === error.code) self.onDisconnect()
    self.emit("error", error);
  });

  var onClose = function onClose () {
    // Ignore if we are already retrying. Or we want to quit.
    if (self.retry) return;
    self.emit('end');
    self.emit('close');
    if (self.quitting) {
      for (var i = 0, il = self.commands.length; i < il; i++) {
        self.parser.emit('reply');
      }
      return;
    }

    self.onDisconnect();
  };

  this.stream.on("end", onClose);
  this.stream.on("close", onClose);

  // Setup the parser.
  this.parser = new Parser();

  this.parser.on('reply', function onReply (reply) {
    if (false !== self.blocking) {
      if ('pubsub' === self.blocking) {
        var type = reply[0].toString();

        switch (type) {
        case 'psubscribe':
        case 'punsubscribe':
        case 'subscribe':
        case 'unsubscribe':
          var channel = reply[1].toString(),
              count   = reply[2];

          if (0 === count) {
            self.blocking = false;

            if ('punsubscribe' === type) {
              delete self.pubsub.pattern[channel]
            } else if ('unsubscribe' === type) {
              delete self.pubsub.channel[channel]
            }
          }

          if ('psubscribe' === type) {
            self.pubsub.pattern[channel] = true
          } else if ('subscribe' === type) {
            self.pubsub.channel[channel] = true
          }

          self.emit(type, channel, count);
          self.emit(type + ':' + channel, count);
          break;
        case 'message':
          var key  = reply[1].toString(),
              data = reply[2];
          self.emit('message', key, data);
          self.emit('message:' + key, data);
          break;
        case 'pmessage':
          var pattern = reply[1].toString(),
              key     = reply[2].toString(),
              data    = reply[3];
          self.emit('pmessage', pattern, key, data);
          self.emit('pmessage:' + pattern, key, data);
          break;
        }
      } else {
        self.emit('data', reply);
      }
      return;
    }

    var command = self.commands.shift();
    if (command) {
      switch (command[0]) {
      case 'MONITOR':
        self.blocking = true;
        break;
      case 'SUBSCRIBE':
      case 'PSUBSCRIBE':
        self.blocking = 'pubsub';
        onReply(reply);
        return;
      }

      if (command[2]) {
        command[2](null, reply);
      }
    }
  });

  // DB error
  this.parser.on('error', function (error) {
    var command = self.commands.shift();
    error = new Error(error);
    if (command && command[2]) command[2](error);
    else self.emit('error', error);
  });

  process.EventEmitter.call(this);
};

RedisClient.prototype = Object.create(process.EventEmitter.prototype);

// Exports
exports.RedisClient = RedisClient;

// createClient
exports.createClient = function createClient (port, host, auth) {
  return new RedisClient(port || 6379, host, auth);
};

RedisClient.prototype.connect = function () {
  return this.stream.connect();
};

RedisClient.prototype.onDisconnect = function (error) {
  var self = this;

  // Make sure the stream is reset.
  this.connected = false;
  this.stream.destroy();
  this.parser.resetState();

  // Increment the attempts, so we know what to set the timeout to.
  this.retry_attempts++;

  // Set the retry timer.
  setTimeout(function () {
    self.stream.connect(self.port, self.host);
  }, this.retry_delay);

  this.retry_delay *= this.retry_backoff;
  this.retry        = true;
};

RedisClient.prototype._write = function (data) {
  if (!this.paused) {
    if (false === this.stream.write(data)) {
      this.paused = true;
    }
  } else {
    this.send_buffer.push(data);
  }
};

// We use this so we can watch for a full send buffer.
RedisClient.prototype.write = function write (data, buffer) {
  if (true !== buffer) {
    this.command += data;
    if (this.max_size <= this.command.length) {
      this._write(this.command);
      this.command = '';
    }
  } else {
    if ('' !== this.command) {
      this._write(this.command);
      this.command = '';
    }
    this._write(data);
  }

  if (!this.flushing) {
    process.nextTick(this._flush);
    this.flushing = true;
  }
};

// We make some assumptions:
//
// * command WILL be uppercase and valid.
// * args IS an array
RedisClient.prototype.sendCommand = function (command, args, callback) {
  // Push the command to the stack.
  if (false === this.blocking) {
    this.commands.push([command, args, callback]);
  }

  // Writable?
  if (false === this.connected) return;

  // Do we have to send a multi bulk command?
  // Assume it is a valid command for speed reasons.
  var args_length;

  if (args && 0 < (args_length = args.length)) {
    var arg, arg_type, last,
        previous = ['*', (args_length + 1), '\r\n', '$', command.length, '\r\n', command, '\r\n'];

    for (i = 0, il = args_length; i < il; i++) {
      arg      = args[i];
      arg_type = typeof arg;

      if ('string' === arg_type) {
        // We can send this in one go.
        previous.push('$', Buffer.byteLength(arg), '\r\n', arg, '\r\n');
      } else if ('number' === arg_type) {
        // We can send this in one go.
        previous.push('$', ('' + arg).length, '\r\n', arg, '\r\n');
      } else if (null === arg || 'undefined' === arg_type) {
        // Send NIL
        previous.push('$\r\b\r\b')
        this.write(previous.join(''));
        previous = [];
      } else {
        // Assume we are a buffer.
        previous.push('$', arg.length, '\r\n');
        this.write(previous.join(''));
        this.write(arg, true);
        previous  = ['\r\n'];
      }
    }

    // Anything left?
    this.write(previous.join(''));
  } else {
    // We are just sending a stand alone command.
    this.write(command_buffers[command]);
  }
};

RedisClient.prototype.destroy = function () {
  this.quitting = true;
  return this.stream.destroy();
};

// http://redis.io/commands.json
exports.commands = [
  'APPEND', 'AUTH', 'BGREWRITEAOF', 'BGSAVE', 'BLPOP', 'BRPOP', 'BRPOPLPUSH', 'CONFIG GET',
  'CONFIG SET', 'CONFIG RESETSTAT', 'DBSIZE', 'DEBUG OBJECT', 'DEBUG SEGFAULT', 'DECR',
  'DECRBY', 'DEL', 'DISCARD', 'ECHO', 'EXEC', 'EXISTS', 'EXPIRE', 'EXPIREAT', 'FLUSHALL',
  'FLUSHDB', 'GET', 'GETBIT', 'GETRANGE', 'GETSET', 'HDEL', 'HEXISTS', 'HGET', 'HGETALL',
  'HINCRBY', 'HKEYS', 'HLEN', 'HMGET', 'HMSET', 'HSET', 'HSETNX', 'HVALS', 'INCR', 'INCRBY',
  'INFO', 'KEYS', 'LASTSAVE', 'LINDEX', 'LINSERT', 'LLEN', 'LPOP', 'LPUSH', 'LPUSHX', 'LRANGE',
  'LREM', 'LSET', 'LTRIM', 'MGET', 'MONITOR', 'MOVE', 'MSET', 'MSETNX', 'MULTI', 'PERSIST',
  'PING', 'PSUBSCRIBE', 'PUBLISH', 'PUNSUBSCRIBE', 'QUIT', 'RANDOMKEY', 'RENAME', 'RENAMENX',
  'RPOP', 'RPOPLPUSH', 'RPUSH', 'RPUSHX', 'SADD', 'SAVE', 'SCARD', 'SDIFF', 'SDIFFSTORE', 'SELECT',
  'SET', 'SETBIT', 'SETEX', 'SETNX', 'SETRANGE', 'SHUTDOWN', 'SINTER', 'SINTERSTORE', 'SISMEMBER',
  'SLAVEOF', 'SMEMBERS', 'SMOVE', 'SORT', 'SPOP', 'SRANDMEMBER', 'SREM', 'STRLEN', 'SUBSCRIBE',
  'SUNION', 'SUNIONSTORE', 'SYNC', 'TTL', 'TYPE', 'UNSUBSCRIBE', 'UNWATCH', 'WATCH', 'ZADD',
  'ZCARD', 'ZCOUNT', 'ZINCRBY', 'ZINTERSTORE', 'ZRANGE', 'ZRANGEBYSCORE', 'ZRANK', 'ZREM',
  'ZREMRANGEBYRANK', 'ZREMRANGEBYSCORE', 'ZREVRANGE', 'ZREVRANGEBYSCORE', 'ZREVRANK', 'ZSCORE',
  'ZUNIONSTORE'
];

this.blocking_commands = ["MONITOR"];

// For each command, make a buffer for it.
var command_buffers = {};

exports.commands.forEach(function (command) {
  // Pre-alloc buffers for non-multi commands.
  //command_buffers[command] = new Buffer('*1\r\n$' + command.length + '\r\n' + command + '\r\n');
  command_buffers[command] = '*1\r\n$' + command.length + '\r\n' + command + '\r\n';

  // Don't override stuff.
  if (!RedisClient.prototype[command.toLowerCase()]) {
    RedisClient.prototype[command.toLowerCase()] = function (array, callback) {
      // An array of args.
      // Assume we only have two args.
      if (Array.isArray(array)) {
        return this.sendCommand(command, array, callback);
      }

      // Arbitary amount of arguments.
      var args    = [];
      args.push.apply(args, arguments);
      callback    = 'function' === typeof args[args.length - 1];

      if (callback) {
        callback  = args.pop();
      } else {
        callback  = null;
      }

      this.sendCommand(command, args, callback);
    };
  }
});

// Overwrite quit
RedisClient.prototype.quit = RedisClient.prototype.end =
function (callback) {
  this.quitting = true;
  return this.sendCommand('QUIT', null, callback);
};

