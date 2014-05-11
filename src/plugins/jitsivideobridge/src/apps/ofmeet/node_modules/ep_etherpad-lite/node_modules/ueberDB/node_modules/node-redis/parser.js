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

var utils = require('./utils');

var RedisParser = function RedisParser () {
  this.resetState();

  process.EventEmitter.call(this);

  return this;
};

module.exports = RedisParser;

RedisParser.prototype = Object.create(process.EventEmitter.prototype);

// Reset state, no matter where we are at.
RedisParser.prototype.resetState = function resetState () {
  this.reply     = null;
  this.expected  = null;
  this.multi     = null;
  this.replies   = null;
  this.pos       = null;
  this.flag      = 'TYPE';
  this.data      = null;
  this.last_data = null;
  this.remaining = null;
};

// Handle an incoming buffer.
RedisParser.prototype.onIncoming = function onIncoming (buffer) {
  var char_code,
      pos    = this.pos || 0,
      length = buffer.length;

  // Make sure the buffer is joint properly.
  if ('TYPE' !== this.flag && 'BULK' !== this.flag && null !== this.data) {
    // We need to wind back a step.
    // If we have CR now, it would break the parser.
    if (0 !== this.data.length) {
      char_code = this.data.charCodeAt(this.data.length - 1);
      this.data = this.data.slice(0, -1);
      --pos;
    } else {
      char_code = buffer[pos];
    }
  }

  for (; length > pos;) {
    switch (this.flag) {
    case 'TYPE':
      // What are we doing next?
      switch (buffer[pos++]) {
      // Single line status reply.
      case 43: // + SINGLE
        this.flag = 'SINGLE';
        break;

      // Tells us the length of upcoming data.
      case 36: // $ LENGTH
        this.flag = 'BULK_LENGTH';
        break;

      // Tells us how many args are coming up.
      case 42: // * MULTI
        this.flag = 'MULTI_BULK';
        break;

      case 58: // : INTEGER
        this.flag = 'INTEGER';
        break;

      // Errors
      case 45: // - ERROR
        this.flag = 'ERROR';
        break;
      }
      // Fast forward a char.
      char_code = buffer[pos];
      this.data = '';
      break;

    // Single line status replies.
    case 'SINGLE':
    case 'ERROR':
      // Add char to the data
      this.data += String.fromCharCode(char_code);
      pos++;

      // Optimize for the common use case.
      if ('O' === this.data && 75 === buffer[pos]) { // OK
        // Send off the reply.
        this.data = 'OK';
        this.onData();

        pos += 3; // Skip the `K\r\n`

        // break early.
        break;
      }

      // Otherwise check for CR
      char_code = buffer[pos];
      if (13 === char_code) { // \r CR
        // Send the reply.
        if ('SINGLE' === this.flag) {
          this.onData();
        } else {
          this.onError();
        }

        // Skip \r\n
        pos += 2;
      }
      break;

    // We have a integer coming up. Look for a CR
    // then assume that is the end.
    case 'BULK_LENGTH':
      // We are still looking for more digits.
      // char_code already set by TYPE state.
      this.data += String.fromCharCode(char_code);
      pos++;

      // Is the next char the end? Set next char_code while
      // we are at it.
      char_code = buffer[pos];
      if (13 === char_code) { // \r CR
        // Cast to int
        this.data = +this.data;

        // Null reply?
        if (-1 !== this.data) {
          this.flag      = 'BULK';
          this.last_data = this.data;
          this.data      = null;
        } else {
          this.data = null;
          this.onData();
        }

        // Skip the \r\n
        pos += 2;
      }
      break;

    // Short bulk reply.
    case 'BULK':
      if (null === this.data && length >= (pos + this.last_data)) {
        // Slow slice is slow.
        if (14 > this.last_data) {
          this.data = new Buffer(this.last_data);
          for (var i = 0; i < this.last_data; i++) {
            this.data[i] = buffer[i + pos];
          }
        } else {
          this.data = buffer.slice(pos, this.last_data + pos);
        }

        // Fast forward past data.
        pos += this.last_data + 2;

        // Send it off.
        this.onData();
      } else if (this.data) {
        // Still joining. pos = amount left to go.
        if (this.remaining <= length) {
          // End is within this buffer.
          if (13 < this.remaining) {
            buffer.copy(this.data, this.last_data - this.remaining, 0, this.remaining)
          } else {
            utils.copyBuffer(buffer, this.data, this.last_data - this.remaining, 0, this.remaining);
          }

          // Fast forward past data.
          pos = this.remaining + 2;
          this.remaining = null;

          this.onData();
        } else {
          // We have more to come. Copy what we got then move on,
          // decrementing the amount we have copied from this.remaining
          if (13 < (this.remaining - length)) {
            utils.copyBuffer(buffer, this.data, this.last_data - this.remaining, 0, length);
          } else {
            buffer.copy(this.data, this.last_data - this.remaining, 0, length);
          }

          // More to go.
          this.remaining -= length;
          pos             = length;
        }
      } else {
        // We will have to do a join.
        this.data = new Buffer(this.last_data);

        // Fast copy if small.
        if (15 > this.last_data) {
          utils.copyBuffer(buffer, this.data, 0, pos);
        } else {
          buffer.copy(this.data, 0, pos)
        }

        // Point pos to the amount we need.
        this.remaining = this.last_data - (length - pos);
        pos            = length;
      }
      break;

    // How many bulk's are coming?
    case 'MULTI_BULK':
      // We are still looking for more digits.
      // char_code already set by TYPE state.
      this.data += String.fromCharCode(char_code);
      pos++;

      // Is the next char the end? Set next char_code while
      // we are at it.
      char_code = buffer[pos];
      if (13 === char_code) { // \r CR
        // Cast to int
        this.last_data = +this.data;
        this.data      = null;

        // Are we multi?
        if (null === this.expected) {
          this.expected = this.last_data;
          this.reply    = [];
        } else if (null === this.multi) {
          this.multi    = this.expected;
          this.expected = null;
          this.replies  = [];
        }

        // Skip the \r\n
        pos += 2;
        this.flag = 'TYPE';

        // Zero length replies.
        if (0 === this.last_data) {
          this.expected = this.reply = null;
          this.data     = [];
          this.onData();
          break;
        } else if (-1 === this.last_data) {
          // NIL reply.
          this.expected = this.reply = null;
          this.data     = null;
          this.onData();
          break;
        }

        char_code = buffer[pos];

        // Will have to look ahead to check for another MULTI in case
        // we are a multi transaction.
        if (36 === char_code) { // $ - BULK_LENGTH
          // We are bulk data.
          this.flag = 'BULK_LENGTH';

          // We are skipping the TYPE check. Skip the $
          pos++;
          // We need to set char code and data.
          char_code = buffer[pos];
          this.data = '';
        } else if (null === this.multi && char_code) {
          // Multi trans time.
          this.multi    = this.expected;
          this.expected = null;
          this.replies  = [];
        }
      }
      break;

    case 'INTEGER':
      // We are still looking for more digits.
      // char_code already set by TYPE state.
      this.data += String.fromCharCode(char_code);
      pos++;

      // Is the next char the end? Set next char_code while
      // we are at it.
      char_code = buffer[pos];
      if (13 === char_code) { // \r CR
        // Cast to int
        this.data = +this.data;
        this.onData();

        // Skip the \r\n
        pos += 2;
      }
      break;
    }
  }

  // In case we have multiple packets.
  this.pos = pos - length;
};

// When we have recieved a chunk of response data.
RedisParser.prototype.onData = function onData () {
  if (null !== this.expected) {
    // Decrement the expected data replies and add the data.
    this.reply.push(this.data);
    this.expected--;

    // Finished? Send it off.
    if (0 === this.expected) {
      if (null !== this.multi) {
        this.replies.push(this.reply);
        this.multi--;

        if (0 === this.multi) {
          this.emit('reply', this.replies);
          this.replies = this.multi = null;
        }
      } else {
        this.emit('reply', this.reply);
      }
      this.reply = this.expected = null;
    }
  } else {
    if (null === this.multi) {
      this.emit('reply', this.data);
    } else {
      this.replies.push(this.data);
      this.multi--;

      if (0 === this.multi) {
        this.emit('reply', this.replies);
        this.replies = this.multi = null;
      }
    }
  }

  this.last_data = null;
  this.data      = null;
  this.flag      = 'TYPE';
};

// Found an error.
RedisParser.prototype.onError = function onError () {
  if (null === this.multi) {
    this.emit('error', this.data);
  } else {
    this.replies.push(this.data);
    this.multi--;

    if (0 === this.multi) {
      this.emit('reply', this.replies);
      this.replies = this.multi = null;
    }
  }

  this.last_data = null;
  this.data      = null;
  this.flag      = 'TYPE';
};
