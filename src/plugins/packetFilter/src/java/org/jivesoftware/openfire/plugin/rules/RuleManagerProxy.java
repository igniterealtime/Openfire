package org.jivesoftware.openfire.plugin.rules;


import java.util.List;

public class RuleManagerProxy implements RuleManager {

    private DbRuleManager dbRuleManager = DbRuleManager.getInstance();

    public RuleManagerProxy() {
    }

    public Rule getRuleById(int id) {
        //Pull it from the db
        return dbRuleManager.getRuleById(id);
    }

    public List<Rule> getRules() {
        return  dbRuleManager.getRules();
    }

    public void addRule(Rule rule, Integer order) {
         
    }

    public void addRule(Rule rule) {
        dbRuleManager.addRule(rule);
    }

    public void deleteRule(int ruleId) {
        //Remove rule from storage (db)
        dbRuleManager.deleteRule(ruleId);
    }

    public void moveOne(int srcId, int destId) {
        Rule srcRule = dbRuleManager.getRuleById(srcId);
        Rule destRule = dbRuleManager.getRuleById(destId);

        dbRuleManager.moveOne(srcRule,destRule);
        //rulesUpdated();

    }

    public int getLastOrder() {
        return dbRuleManager.getLastOrderId();
    }

    public void moveRuleOrder(int ruleId,int orderId) {
        dbRuleManager.moveRuleOrder(ruleId,orderId);
       // rulesUpdated();
    }

    public void updateRule(Rule rule) {
       dbRuleManager.updateRule(rule);
       //rulesUpdated();
    }

    public void rulesUpdated() {
        reloadRules();
    }

    private void reloadRules() {
        dbRuleManager.clear();
    }
}
