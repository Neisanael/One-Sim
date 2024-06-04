/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package routing.community;

import core.*;
import java.util.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;

/**
 *
 * @author shiyaken
 */
public class PeopleRank implements RoutingDecisionEngine {

    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    protected Centrality centrality;

    private static final double DAMPING_FACTOR = 0.85;

    private Map<DTNHost, Double> peprank;
    private Map<DTNHost, List<DTNHost>> totalHost;

    public PeopleRank(Settings s) {
      /*  if (s.contains(CENTRALITY_ALG_SETTING)) {
            this.centrality = (Centrality) s.createIntializedObject(s.getSetting(CENTRALITY_ALG_SETTING));
        } else {
            this.centrality = new AverageWinCentrality1(s);
        }*/
    }

    public PeopleRank(PeopleRank proto) {
        this.startTimestamps = new HashMap<DTNHost, Double>();
        this.connHistory = new HashMap<DTNHost, List<Duration>>();
        this.peprank = new HashMap<DTNHost, Double>();
        this.totalHost = new HashMap<DTNHost, List<DTNHost>>();
    }

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {

        if (!peprank.containsKey(thisHost)) {
            peprank.put(thisHost, 0.0);
        }
        List<DTNHost> hosts1 = totalHost.getOrDefault(thisHost, new ArrayList<>());
        hosts1.add(peer);
        totalHost.put(thisHost, hosts1);
        List<DTNHost> hosts = totalHost.get(thisHost);
        double rankHost = 0.15 + DAMPING_FACTOR * (peprank.get(thisHost) / hosts.size());
        peprank.put(thisHost, rankHost);

        System.out.println("node " + thisHost + " total rank " + peprank.get(thisHost));

    }

    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        // double time = startTimestamps.get(peer);
        double time = cek(thisHost, peer);
        double etime = SimClock.getTime();

        // Find or create the connection history list
        List<Duration> history;
        if (!connHistory.containsKey(peer)) {
            history = new LinkedList<Duration>();
            connHistory.put(peer, history);
        } else {
            history = connHistory.get(peer);
        }
        // add this connection to the list
        if (etime - time > 0) {
            history.add(new Duration(time, etime));
        }

        startTimestamps.remove(peer);
    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        DTNHost myHost = con.getOtherNode(peer);
        // Map.Entry<DTNHost, Double> firstEntry = peprank.entrySet().iterator().next();
        // System.out.println(firstEntry.getKey() + " and the key " +
        // firstEntry.getValue());
        PeopleRank de = this.getOtherDecisionEngine(peer);

        this.startTimestamps.put(peer, SimClock.getTime());
        de.startTimestamps.put(myHost, SimClock.getTime());
    }

    @Override
    public boolean newMessage(Message m) {
        return true;
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        return m.getTo() == aHost;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        return m.getTo() != thisHost;
    }

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
        if (m.getTo() == otherHost) {
            return true; // Message should be sent directly to the destination
        }
        System.out.println("");
        System.out.println("node " + otherHost + " total rank " + peprank.get(otherHost));
        System.out.println("node " + thisHost + " total rank " + peprank.get(thisHost));
        if (peprank.get(otherHost) >= peprank.get(thisHost)) {
            return true;
        }
        return false;

    }

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        return true;
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        return m.getTo() == hostReportingOld;
    }

    @Override
    public void update(DTNHost thisHost) {
    }

    @Override
    public RoutingDecisionEngine replicate() {
        return new PeopleRank(this);
    }

    private PeopleRank getOtherDecisionEngine(DTNHost h) {
        MessageRouter otherRouter = h.getRouter();
        // System.out.println(otherRouter);
        assert otherRouter instanceof DecisionEngineRouter : "This router only works with other routers of same type";

        return (PeopleRank) ((DecisionEngineRouter) otherRouter).getDecisionEngine();
    }

    protected double getGlobalCentrality() {
        return this.centrality.getGlobalCentrality(connHistory);
    }

    public double cek(DTNHost thisHost, DTNHost peer) {
        if (startTimestamps.containsKey(thisHost)) {
            startTimestamps.get(peer);
        }
        return 0;
    }
}
