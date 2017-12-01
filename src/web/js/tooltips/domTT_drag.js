// {{{ license

/*
 * Copyright 2002-2005 Dan Allen, Mojavelinux.com (dan.allen@mojavelinux.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// }}}
// {{{ globals (DO NOT EDIT)

var domTT_dragEnabled = true;
var domTT_currentDragTarget;
var domTT_dragMouseDown;
var domTT_dragOffsetLeft;
var domTT_dragOffsetTop;

// }}}
// {{{ domTT_dragStart()

function domTT_dragStart(in_this, in_event)
{
    if (typeof(in_event) == 'undefined') { in_event = window.event; }

    var eventButton = in_event[domLib_eventButton];
    if (eventButton != 1 && !domLib_isKHTML)
    {
        return;
    }

    domTT_currentDragTarget = in_this;
    in_this.style.cursor = 'move';

    // upgrade our z-index
    in_this.style.zIndex = ++domLib_zIndex;

    var eventPosition = domLib_getEventPosition(in_event);

    var targetPosition = domLib_getOffsets(in_this);
    domTT_dragOffsetLeft = eventPosition.get('x') - targetPosition.get('left');
    domTT_dragOffsetTop = eventPosition.get('y') - targetPosition.get('top');
    domTT_dragMouseDown = true;
}

// }}}
// {{{ domTT_dragUpdate()

function domTT_dragUpdate(in_event)
{
    if (domTT_dragMouseDown)
    {
        if (domLib_isGecko)
        {
            window.getSelection().removeAllRanges()
        }

        if (domTT_useGlobalMousePosition && domTT_mousePosition != null)
        {
            var eventPosition = domTT_mousePosition;
        }
        else
        {
            if (typeof(in_event) == 'undefined') { in_event = window.event; }
            var eventPosition = domLib_getEventPosition(in_event);
        }

        domTT_currentDragTarget.style.left = (eventPosition.get('x') - domTT_dragOffsetLeft) + 'px';
        domTT_currentDragTarget.style.top = (eventPosition.get('y') - domTT_dragOffsetTop) + 'px';

        // update the collision detection
        domLib_detectCollisions(domTT_currentDragTarget);
    }
}

// }}}
// {{{ domTT_dragStop()

function domTT_dragStop()
{
    if (domTT_dragMouseDown) {
        domTT_dragMouseDown = false; 
        domTT_currentDragTarget.style.cursor = 'default';
        domTT_currentDragTarget = null;
        if (domLib_isGecko)
        {
            window.getSelection().removeAllRanges()
        }
    }
}

// }}}
