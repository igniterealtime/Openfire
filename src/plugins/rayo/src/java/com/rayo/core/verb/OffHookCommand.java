package com.rayo.core.verb;

public class OffHookCommand extends AbstractVerbCommand {

    private Handset handset;

    public void setHandset(Handset handset) {
        this.handset = handset;
    }

    public Handset getHandset() {
        return this.handset;
    }
}
