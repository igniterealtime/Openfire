package org.jivesoftware.openfire.plugin.rules;


public class RuleOption {

    private String name;
    private String type;
    private boolean required;
    private String value;


    public RuleOption() {}

    public RuleOption(String name, String type, boolean required, String value) {
        this.name = name;
        this.value = value;
        this.type = type;
        this.required = required;
    }

    

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
