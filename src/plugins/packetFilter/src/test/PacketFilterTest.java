import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.PacketFilter;
import org.jivesoftware.openfire.plugin.rules.Drop;
import org.jivesoftware.openfire.plugin.rules.Rule;
import org.jivesoftware.openfire.plugin.rules.RuleManager;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

public class PacketFilterTest {

    private PacketFilter packetFilter;
    private RuleManager rmp;

    @BeforeClass
    public void setup() {
        //Create a packetFilter
        packetFilter = PacketFilter.getInstance();
        rmp = new MockRuleManager();
        packetFilter.setRuleManager(rmp);

    }


    @Test(groups = {"default"}, expectedExceptions = PacketRejectedException.class)
    public void testDropMessage() throws PacketRejectedException {
//Create some rules
        Rule drop = new Drop();
        drop.setSource("bart@localhost");
        drop.setSourceType("User");

        drop.setDestination("Any");
        drop.setDestType("Any");

        drop.isDisabled(false);
        drop.setPacketType(Rule.PacketType.Message);
        drop.doLog(false);

        rmp.addRule(drop);
        Message message = new Message();
        message.setFrom("bart@localhost");
        message.setTo("lisa@localhost");
        Rule rule = packetFilter.findMatch(message);
        assert (rule != null);
        rule.doAction(message);
    }

    @Test(groups = {"default"}, expectedExceptions = PacketRejectedException.class)
    public void testDropIQ() throws PacketRejectedException {
//Create some rules
        Rule drop = new Drop();
        drop.setSource("bart@localhost");
        drop.setSourceType("User");

        drop.setDestination("Any");
        drop.setDestType("Any");

        drop.isDisabled(false);
        drop.setPacketType(Rule.PacketType.Iq);
        drop.doLog(false);
        rmp.addRule(drop);
        IQ iq = new IQ();
        iq.setFrom("bart@localhost");
        iq.setTo("lisa@localhost");
        Rule rule = packetFilter.findMatch(iq);
        assert (rule != null);
        rule.doAction(iq);
    }

    @Test(groups = {"default"}, expectedExceptions = PacketRejectedException.class)
    public void testDropPresence() throws PacketRejectedException {
//Create some rules
        Rule drop = new Drop();
        drop.setSource("bart@localhost");
        drop.setSourceType("User");
        drop.setDestination("Any");
        drop.setDestType("Any");
        drop.isDisabled(false);
        drop.setPacketType(Rule.PacketType.Presence);
        drop.doLog(false);
        rmp.addRule(drop);
        Presence presence = new Presence();
        presence.setFrom("bart@localhost");
        presence.setTo("lisa@localhost");
        Rule rule = packetFilter.findMatch(presence);
        assert (rule != null);
        rule.doAction(presence);
    }

    @Test(groups = {"default"}, expectedExceptions = PacketRejectedException.class)
    public void testDropAnyType() throws PacketRejectedException {
//Create some rules
        Rule drop = new Drop();
        drop.setSource("bart@localhost");
        drop.setSourceType(Rule.SourceDestType.User.toString());
        drop.setDestination("Any");
        drop.setDestType(Rule.SourceDestType.Any.toString());
        drop.isDisabled(false);
        drop.setPacketType(Rule.PacketType.Any);
        drop.doLog(false);
        rmp.addRule(drop);
        Presence presence = new Presence();
        presence.setFrom("bart@localhost");
        presence.setTo("lisa@localhost");
        Rule rule = packetFilter.findMatch(presence);
        assert (rule != null);
        rule.doAction(presence);
    }

    /*@Test(groups = {"default"}, expectedExceptions = PacketRejectedException.class)
    public void testGroupDrop() throws PacketRejectedException {
        Group 
    } */


}
