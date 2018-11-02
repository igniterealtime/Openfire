package org.jivesoftware.openfire.plugin.rules;

import java.util.List;

public interface RuleManager {

    public Rule getRuleById(int id);

    public List<Rule> getRules();

    public void addRule(Rule rule, Integer order);

    public void addRule(Rule rule);

    public void deleteRule(int ruleId);

    public void moveOne(int srcId, int destId);
    public int getLastOrder();

    public void moveRuleOrder(int ruleId,int orderId) ;

    public void updateRule(Rule rule);

    public void rulesUpdated();
}
