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

var channels = require("./channels");

function doOperation(operation, callback)
{
  process.nextTick(callback);
}

var channelObj = new channels.channels(doOperation);

for(var i=0;i<2500000;i++)
{
  for(var channel=0;channel<10;channel++)
  {
    channelObj.emit(channel);
  }
}
