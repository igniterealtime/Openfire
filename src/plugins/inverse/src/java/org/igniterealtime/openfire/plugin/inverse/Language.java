/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
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
package org.igniterealtime.openfire.plugin.inverse;

import java.util.Locale;

/**
 * Languages as defined by Converse.JS.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public enum Language
{
    Afrikaans ("af"),
    Arabic ("ar"),
    Bahasa_Indonesia ("id"),
    Basque ("eu"),
    Bulgarian ("bg"),
    BrazilianPortuguese ("pt_BR"),
    Catalan ("ca"),
    Czech ("cs"),
    Chinese ("zh_TW"),
    SimplifiedChinese ("zh_CN"),
    Dutch ("nl"),
    English ("en"),
    Flemish ("nl_BE"),
    French ("fr"),
    German ("de"),
    Hebrew ("he"),
    Hindi ("hi"),
    Hungarian ("hu"),
    Italian ("it"),
    Japanese ("ja"),
    Lithuanian ("lt"),
    Norwegian ("nb"),
    Polish ("pl"),
    Romanian ("ro"),
    Russian ("ru"),
    Spanish ("es"),
    Turkish ("tr"),
    Ukrainian ("uk");

    private final String code;

    Language( String code )
    {
        this.code = code;
    }

    public String getCode()
    {
        return code;
    }

    public static Language byConverseCode( final String converseCode )
    {
        for ( final Language language : values() )
        {
            if ( language.getCode().equalsIgnoreCase( converseCode ) )
            {
                return language;
            }
        }

        return null;
    }

    public static Language byLocale( final Locale locale )
    {
        for ( final Language language : values() )
        {
            if ( locale.getLanguage().equals( new Locale( language.getCode() ).getLanguage() ) )
            {
                return language;
            }
        }

        return null;
    }
}
