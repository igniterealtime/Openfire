import org.jivesoftware.openfire.plugin.rules.RuleManager;
import org.jivesoftware.openfire.plugin.rules.Rule;

import java.util.List;
import java.util.ArrayList;

public class MockRuleManager implements RuleManager {

    List<Rule> rules = new ArrayList<Rule>();
    public Rule getRuleById(int id) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void addRule(Rule rule, Integer order) {
        rules.add(order,rule);
    }

    public void addRule(Rule rule) {
        rules.add(rule);
    }

    public void deleteRule(String ruleId) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void moveOne(int srcId, int destId) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getLastOrder() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void moveRuleOrder(int ruleId, int orderId) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void updateRule(Rule rule) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void rulesUpdated() {
        //To change body of implemented methods use File | Settings | File Templates.
    }


}
