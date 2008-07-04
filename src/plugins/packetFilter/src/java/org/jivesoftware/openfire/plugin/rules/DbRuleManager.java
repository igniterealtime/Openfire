package org.jivesoftware.openfire.plugin.rules;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.plugin.cluster.RulesUpdatedEvent;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.CacheFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DbRuleManager {
    //SQL Statments

    private static final String UPDATE_RULE =
            "UPDATE ofPfRules" +
                    " SET type=?,tojid=?,fromjid=?,rulef=?,disabled=?,log=?,description=?,ruleorder=?,sourcetype=?,desttype=? WHERE id=?";

    private static final String DELETE_RULE =
            "DELETE FROM ofPfRules WHERE id=?";

    private static final String INSERT_RULE =
            "INSERT INTO ofPfRules(ruleorder,type,tojid,fromjid,rulef,disabled,log,description,sourcetype,desttype) VALUES(?,?,?,?,?,?,?,?,?,?)";

    private static final String COUNT =
            "select count(*) from ofPfRules";

    private static final String GET_RULES =
            "SELECT rulef,id,type,tojid,fromjid,disabled,log,description,ruleorder,sourcetype,desttype from ofPfRules order by ruleorder";

    private static final String RULE_OPTIONS =
            "SELECT optionKey, optionValue, optionRequired, classType from ofPfRulesOptions where ruleId = ?";

    /*private static final String GET_RULE_BY_ID =
            "SELECT rule,id,type,tojid,fromjid,disabled,log,description,ruleorder from ofPfRules where id=?";*/

    private static final String GET_RULE_BY_ORDER_ID =
            "SELECT ruleorder,rulef,id,type,tojid,fromjid,disabled,log,description,sourcetype,desttype from ofPfRules where ruleorder=? order by ruleorder DESC";


    private static final String GET_LAST_ORDERID =
            "SELECT ruleorder from ofPfRules order by ruleorder DESC";

    private static final DbRuleManager DB_RULE_MANAGER = new DbRuleManager();

    private List<Rule> rules = new CopyOnWriteArrayList<Rule>();

    private DbRuleManager() {
        rules = getRules();
    }

    public static DbRuleManager getInstance() {
        return DB_RULE_MANAGER;
    }


    public List<Rule> getRules() {
        if (rules.isEmpty()) {
            synchronized (rules) {
                if (rules.isEmpty()) {
                    Connection con = null;
                    PreparedStatement pstmt = null;
                    ResultSet rs = null;
                    try {
                        con = DbConnectionManager.getConnection();
                        pstmt = con.prepareStatement(GET_RULES);
                        rs = pstmt.executeQuery();

                        while (rs.next()) {
                            Rule rule = null;

                            String ruleType = rs.getString(1);
                            if (ruleType.equals(Reject.class.getName()))
                                rule = new Reject();
                            else if (ruleType.equals(Pass.class.getName()))
                                rule = new Pass();
                            else if (ruleType.equals(Drop.class.getName()))
                                rule = new Drop();
 

                            rule.setRuleId(rs.getString(2));
                            rule.setPacketType(Rule.PacketType.valueOf(rs.getString(3)));
                            rule.setDestination(rs.getString(4));
                            rule.setSource(rs.getString(5));
                            rule.isDisabled(rs.getBoolean(6));
                            rule.doLog(rs.getBoolean(7));
                            rule.setDescription(rs.getString(8));
                            rule.setOrder(rs.getInt(9));
                            rule.setSourceType(Rule.SourceDestType.valueOf(rs.getString(10)));
                            rule.setDestType(Rule.SourceDestType.valueOf(rs.getString(11)));
                            
                            rules.add(rule);

                        }


                    } catch (SQLException sqle) {
                        Log.error(sqle);
                    }
                    finally {
                        DbConnectionManager.closeConnection(pstmt, con);
                    }
                }
            }
        }

        return rules;
    }

    private void getSavedOptions() {
        if (rules != null) {
            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                for (Rule rule : rules) {
                    Log.info("getting options for rule " + rule.getRuleId());
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(RULE_OPTIONS);
                    pstmt.setInt(1, Integer.parseInt(rule.getRuleId()));

                    rs = pstmt.executeQuery();
                    List<RuleOption> savedOptions = new ArrayList<RuleOption>();
                    while (rs.next()) {
                        RuleOption option = new RuleOption();
                        option.setName(rs.getString(1));
                        Log.info("Name " + option.getName());
                        option.setValue(rs.getString(2));
                        option.setRequired(rs.getBoolean(3));
                        option.setType(rs.getString(4));
                        savedOptions.add(option);
                    }
                    //rule.setSavedOptions(savedOptions);
                    pstmt.close();
                    rs.close();


                }


            } catch (SQLException sqle) {
                Log.error(sqle);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }

        }
    }

    public int getLastOrderId() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        int count = -1;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_LAST_ORDERID);
            rs = pstmt.executeQuery();
            if (rs.next()) {

                count = rs.getInt(1);
            }
            else {
                count = 0;
            }
        } catch (SQLException sqle) {
            Log.error(sqle);
            // Result set probably empty
            return 0;
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return count;

    }

    public int getCount() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        int count = -1;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(COUNT);
            rs = pstmt.executeQuery();
            rs.next();
            count = rs.getInt(1);

        } catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return count;

    }

    public boolean addRule(Rule rule) {
        return addRule(rule, null);
    }

    public boolean addRule(Rule rule, Integer order) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_RULE);
            if (order == null) {
                order = getLastOrderId() + 1;
                pstmt.setInt(1, order);
            } else {

                pstmt.setInt(1, order);
            }
            rule.setOrder(order);
            pstmt.setString(2, rule.getPackeType().toString());
            pstmt.setString(3, rule.getDestination());
            pstmt.setString(4, rule.getSource());
            pstmt.setString(5, rule.getClass().getName());
            if (rule.isDisabled()) {
                pstmt.setByte(6, new Byte("1"));
            }
            else {
                pstmt.setByte(6, new Byte("0"));
            }
            if (rule.doLog()) {
                pstmt.setByte(7, new Byte("1"));
            }
            else {
                pstmt.setByte(7, new Byte("0"));
            }
            pstmt.setString(8, rule.getDescription());
            pstmt.setString(9, rule.getSourceType().toString());
            pstmt.setString(10, rule.getDestType().toString());
            pstmt.execute();

            rules.clear();

        } catch (SQLException sqle) {
            Log.error(sqle);
            return false;
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
            updateCluster();

        }
        return true;
    }


    public boolean deleteRule(String ruleId) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_RULE);
            pstmt.setString(1, ruleId);
            pstmt.execute();

            rules.remove(getRuleById(new Integer(ruleId)));

        } catch (SQLException sqle) {
            Log.error(sqle);
            return false;
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
            updateCluster();
        }
        return true;
    }

    public void moveRuleOrder(int ruleId, int order) {
        Rule rule = getRuleById(ruleId);
//See if there is a gap that we can just update the rule with
        Rule orderIWant = getRuleByOrderId(order);
        if (orderIWant == null) {
            updateRule(rule, order);
        }
        //No gap. Move all rules >= to the order.
        else {
            List<Rule> rules = getRules();

            for (int i = rules.size(); i > 0; i--) {
                Rule moveRule = rules.get(i);
                if (new Integer(moveRule.getOrder()) >= order) {
                    updateRule(moveRule, order + 1);
                } else break;
            }
            updateRule(rule, order);
        }
    }

    public Rule getRuleByOrderId(int order) {
        Rule rule = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_RULE_BY_ORDER_ID);
            pstmt.setInt(1, order);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                rule.setOrder(rs.getInt(1));
                String ruleType = rs.getString(2);
                if (ruleType.equals(Reject.class.getName())) {
                    rule = new Reject();
                }
                else if (ruleType.equals(Pass.class.getName())) {
                    rule = new Pass();
                }
                else if (ruleType.equals(Drop.class.getName())) {
                    rule = new Drop();
                }

                rule.setRuleId(rs.getString(3));
                rule.setPacketType(Rule.PacketType.valueOf(rs.getString(4)));
                rule.setDestination(rs.getString(5));
                rule.setSource(rs.getString(6));
                rule.isDisabled(rs.getBoolean(7));
                rule.doLog(rs.getBoolean(8));
                rule.setDescription(rs.getString(9));
                rule.setSourceType(Rule.SourceDestType.valueOf(rs.getString(10)));
                rule.setDestType(Rule.SourceDestType.valueOf(rs.getString(11)));

            }


        } catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        return rule;
    }

    public Rule getRuleById(int id) {
        Rule rule = null;
        String ruleId = Integer.toString(id);
        for (Rule cRule : rules) {
            if (cRule.getRuleId().equals(ruleId)) {
                rule = cRule;
                break;
            }
        }
        return rule;
    }

    public boolean updateRule(Rule rule) {
        return updateRule(rule, rule.getOrder());
    }

    public boolean updateRule(Rule rule, Integer order) {
        //SET type=?,tojid=?,fromjid=?,rulef=?,disabled=?,log=?,description=?,ruleorder=?,sourcetype=?,desttype=? WHERE id=?";
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_RULE);

            pstmt.setString(1, rule.getPackeType().toString());
            pstmt.setString(2, rule.getDestination());
            pstmt.setString(3, rule.getSource());
            pstmt.setString(4, rule.getClass().getName());
            if (rule.isDisabled())
                pstmt.setByte(5, new Byte("1"));
            else
                pstmt.setByte(5, new Byte("0"));
            if (rule.doLog())
                pstmt.setByte(6, new Byte("1"));
            else
                pstmt.setByte(6, new Byte("0"));
            pstmt.setString(7, rule.getDescription());
            pstmt.setInt(8, order);

            pstmt.setString(9, rule.getSourceType().toString());
            pstmt.setString(10, rule.getDestType().toString());
            pstmt.setInt(11, new Integer(rule.getRuleId()));
            pstmt.executeUpdate();

            rules.clear();

        } catch (SQLException sqle) {
            Log.error(sqle);
            return false;
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
            updateCluster();
        }
        return true;

    }

    /*
       Moving a rule one up or down so we can just swap

    */
    public boolean moveOne(Rule src, Rule dest) {
        int srcOrder = src.getOrder();
        int destOrder = dest.getOrder();

        dest.setOrder(srcOrder);
        src.setOrder(destOrder);
        updateRule(src);
        updateRule(dest);

        return true;
    }

    private void updateCluster() {
        boolean isClustered = ClusterManager.isClusteringEnabled();
        if (isClustered) {
            RulesUpdatedEvent request = new RulesUpdatedEvent();
            CacheFactory.doClusterTask(request);
        }
    }

    public void clear() {
        if (!rules.isEmpty()) {
            rules.clear();
        }
    }
}
