/**
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
package com.rayo.core.verb;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class CreateSpeakerCommand extends BaseVerb
{
    public String codec = null;
    public String mixer = null;
    public String sipuri = null;

    public CreateSpeakerCommand(String sipuri, String mixer, String codec)
    {
        this.sipuri = sipuri;
        this.mixer = mixer;
        this.codec = codec;
    }

    @Override
    public String toString() {

        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("callId", getCallId())
            .append("verbId", getVerbId())
            .append("codec",codec)
            .append("mixer",mixer)
            .append("sipuri",sipuri)
            .toString();
    }
}
