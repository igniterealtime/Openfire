package org.jivesoftware.openfire.plugin.rules;


import java.util.List;

public class RuleManagerProxy implements RuleManager {

    private DbRuleManager dbRuleManager = DbRuleManager.getInstance();
    //private List<Rule> rules = null;

    public RuleManagerProxy() {
       // rules = dbRuleManager.getRules();
    }

    public Rule getRuleById(int id) {
        //Pull it from the db
        return dbRuleManager.getRuleById(id);
    }

    public List<Rule> getRules() {
        return  dbRuleManager.getRules();
    }

    public void addRule(Rule rule, Integer order) {
       /*if (order != null && order.intValue() > 0) {
            DbRuleManager.getInstance().addRule(rule,order);
        }
        rules.add(order.intValue(),rule);
         */
         
    }

    public void addRule(Rule rule) {
        dbRuleManager.addRule(rule);
        //rulesUpdated();
    }

    public void deleteRule(String ruleId) {
        //Remove rule from storage (db)
        dbRuleManager.deleteRule(ruleId);

        //Recreate array.
        /*for (Rule rule : rules) {
            if (rule == null) break;
            if (rule.getRuleId().equals(ruleId)) {
                rules.remove(rule);
            }
        } */
    }

    public void moveOne(int srcId, int destId) {
        Rule srcRule = dbRuleManager.getRuleById(srcId);
        Rule destRule = dbRuleManager.getRuleById(destId);

        dbRuleManager.moveOne(srcRule,destRule);
        //rulesUpdated();

    }

    public int getLastOrder() {
        //Get the last rule in the "Cache" and return it's order
        //if (rules.size() == 0) return 0;
       // return rules.get(rules.size()-1).getOrder();
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
