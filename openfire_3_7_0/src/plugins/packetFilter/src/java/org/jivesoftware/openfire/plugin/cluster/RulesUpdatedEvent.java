package org.jivesoftware.openfire.plugin.cluster;

import org.jivesoftware.openfire.plugin.rules.RuleManager;
import org.jivesoftware.openfire.plugin.rules.RuleManagerProxy;
import org.jivesoftware.util.cache.ClusterTask;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;


public class RulesUpdatedEvent implements ClusterTask {



    public RulesUpdatedEvent() {

    }

    public Object getResult() {
        return null;
    }

    public void run() {
       RuleManager ruleManager = new RuleManagerProxy();
       ruleManager.rulesUpdated();
    }

    public void writeExternal(ObjectOutput objectOutput) throws IOException {

    }

    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
       
    }
}
