/**
 * 2011 Peter 'Pita' Martischka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var domain;
try {
  domain = require('domain');
} catch(e){
  //domains only exist in 0.8+
}
 
exports.channels = function(operatorFunction)
{
  this.channels = {};
  this.operatorFunction = operatorFunction;
}

exports.channels.prototype.emit = function(channelname, object)
{
  var _this = this;
  
  //this channel already exists, add it to the queue
  if(_this.channels[channelname])
  {
    if(domain && domain.active){
      object.__domain = domain.active;
    }

    _this.channels[channelname].push(object);
  }
  //this channel is new, create it and start it
  else
  {
    //create the channel array
    _this.channels[channelname] = [];
    
    _this.operatorFunction(object, function iterator()
    {
      //get the next element
      var next = _this.channels[channelname].shift();
      
      //if there is nothing todo anymore in this channel, clean it up
      if(next !== undefined)
      {
        // if this method has a domain, call it in the domain
        if(next.__domain){
          var activeDomain = next.__domain;
          delete next.__domain;

          activeDomain.run(function(){
            _this.operatorFunction(next, iterator);
          });
        } else {
          _this.operatorFunction(next, iterator);
        }
      }
      else
      {
        delete _this.channels[channelname];
      }
    });
  }
}
