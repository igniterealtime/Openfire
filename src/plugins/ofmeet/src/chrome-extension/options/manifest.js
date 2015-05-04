this.manifest = {
    "name": "Openfire Meetings Options",
    "icon": "../icon128.png",
    "settings": [
        {
            "tab": i18n.get("information"),
            "group": i18n.get("connection"),
            "name": "server",
            "type": "text",
            "label": i18n.get("server"),
            "text": i18n.get("x-characters")
        },
        {
            "tab": i18n.get("information"),
            "group": i18n.get("connection"),
            "name": "domain",
            "type": "text",
            "label": i18n.get("domain"),
            "text": i18n.get("x-characters-pw")
        },    
        {
            "tab": i18n.get("information"),
            "group": i18n.get("login"),
            "name": "username",
            "type": "text",
            "label": i18n.get("username"),
            "text": i18n.get("x-characters")
        },
        {
            "tab": i18n.get("information"),
            "group": i18n.get("login"),
            "name": "password",
            "type": "text",
            "label": i18n.get("password"),
            "text": i18n.get("x-characters-pw"),
            "masked": true
        },
        {
            "tab": i18n.get("information"),
            "group": i18n.get("login"),
            "name": "myDescription",
            "type": "description",
            "text": i18n.get("description")
        },
        {
            "tab": i18n.get("information"),
            "group": i18n.get("login"),
            "name": "connect",
            "type": "button",
            "label": i18n.get("connect"),
            "text": i18n.get("login")
        },        
        {
            "tab": i18n.get("information"),
            "group": i18n.get("logout"),
            "name": "myCheckbox",
            "type": "checkbox",
            "label": i18n.get("enable")
        },
        {
            "tab": i18n.get("information"),
            "group": i18n.get("logout"),
            "name": "disconnect",
            "type": "button",
            "label": i18n.get("disconnect"),
            "text": i18n.get("logout")
        },
        {
            "tab": i18n.get("Audio Hardware"),
            "group": i18n.get("Sip Speaker"),
            "name": "sipSpeakerAddress",
            "type": "text",
            "label": i18n.get("Sip address"),
            "text": i18n.get("x-characters")
        },
        {
            "tab": i18n.get("Audio Hardware"),
            "group": i18n.get("Sip Speaker"),
            "name": "sipEnableCheckbox",
            "type": "checkbox",
            "label": i18n.get("Enable Sip Device")
        },       
        {
            "tab": i18n.get("Audio Hardware"),
            "group": i18n.get("Sip Speaker"),
            "name": "savesipSpeakerAddress",
            "type": "button",
            "label": i18n.get("Save settings"),
            "text": i18n.get("Save")
        }

    ],
    "alignment": [
        [
            "username",
            "password"
        ]
    ]
};
