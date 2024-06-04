/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;

/**
 * Implementation of Spray and wait router as depicted in
 * <I>Spray and Wait: An Efficient Routing Scheme for Intermittently
 * Connected Mobile Networks</I> by Thrasyvoulos Spyropoulus et al.
 *
 */
public class SprayAndFocusRouter extends ActiveRouter {
	/** identifier for the initial number of copies setting ({@value}) */
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value}) */
	// public static final String BINARY_MODE = "binaryMode";
	/** SprayAndWait router's settings name space ({@value}) */
	public static final String SPRAYANDWAIT_NS = "SprayAndFocusRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";
	protected int initialNrofCopies;
	// protected boolean isBinary;
	/** delivery predictability initialization constant */
	public static final double P_INIT = 0.75;
	/** delivery predictability transitivity scaling constant default value */
	public static final double DEFAULT_BETA = 0.25;
	/** delivery predictability aging constant */
	public static final double GAMMA = 0.98;
	/** Prophet router's setting namespace ({@value}) */
	public static final String PROPHET_NS = "ProphetRouter";
	/**
	 * Number of seconds in time unit -setting id ({@value}).
	 * How many seconds one time unit is when calculating aging of
	 * delivery predictions. Should be tweaked for the scenario.
	 */
	public static final String SECONDS_IN_UNIT_S = "secondsInTimeUnit";
	/**
	 * Transitivity scaling constant (beta) -setting id ({@value}).
	 * Default value for setting is {@link #DEFAULT_BETA}.
	 */
	public static final String BETA_S = "beta";
	/** the value of nrof seconds in time unit -setting */
	private int secondsInTimeUnit;
	/** value of beta setting */
	private double beta;
	/** delivery predictabilities */
	private Map<DTNHost, Double> preds;
	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;

	public SprayAndFocusRouter(Settings s) {
		super(s);
		Settings sprayandwaitSettings = new Settings(SPRAYANDWAIT_NS);
		secondsInTimeUnit = sprayandwaitSettings.getInt(SECONDS_IN_UNIT_S);
		initialNrofCopies = sprayandwaitSettings.getInt(NROF_COPIES);
		// isBinary = sprayandwaitSettings.getBoolean(BINARY_MODE);
		if (sprayandwaitSettings.contains(BETA_S)) {
			beta = sprayandwaitSettings.getDouble(BETA_S);
		} else {
			beta = DEFAULT_BETA;
		}
		initPreds();
	}

	/**
	 * Copy constructor.
	 * 
	 * @param r The router prototype where setting values are copied from
	 */
	protected SprayAndFocusRouter(SprayAndFocusRouter r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		System.out.println(initialNrofCopies);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		// this.isBinary = r.isBinary;
		this.beta = r.beta;
		initPreds();
	}

	private void initPreds() {
		this.preds = new HashMap<DTNHost, Double>();
	}

	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) /
				secondsInTimeUnit;
		if (timeDiff == 0) {
			return;
		}
		double mult = Math.pow(GAMMA, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue() * mult);
		}
		this.lastAgeUpdate = SimClock.getTime();
	}

	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		} else {
			return 0;
		}
	}

	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
	}

	private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof SprayAndFocusRouter : "SnF only works with other routers of same type";
		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds = ((SprayAndFocusRouter) otherRouter).getDeliveryPreds();
		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}
			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + (1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}

	private void updateDeliveryPredFor(DTNHost host) {
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		preds.put(host, newValue);
	}

	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();
		Collection<Message> msgCollection = getMessageCollection();
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			SprayAndFocusRouter othRouter = (SprayAndFocusRouter) other.getRouter();
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				tryAllMessagesToAllConnections();
				if (othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m, con));
				}
			}
		}
		if (messages.size() == 0) {
			return null;
		}
		// sort the message-connection tuples
		Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages); // try to send messages
	}

	private class TupleComparator implements Comparator<Tuple<Message, Connection>> {
		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((SprayAndFocusRouter) tuple1.getValue().getOtherNode(getHost()).getRouter()).getPredFor(
					tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((SprayAndFocusRouter) tuple2.getValue().getOtherNode(getHost()).getRouter()).getPredFor(
					tuple2.getKey().getTo());
			// bigger probability should come first
			if (p2 - p1 == 0) {
				/* equal probabilities -> let queue mode decide */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			} else if (p2 - p1 < 0) {
				return -1;
			} else {
				return 1;
			}
		}
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
		}
	}

	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}

	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
		assert nrofCopies != null : "Not a SnW message: " + msg;
		// if (isBinary) {
		if (nrofCopies > 1) {
			/* in binary S'n'W the receiving node gets ceil(n/2) copies */
			nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
		} else {
			double predFromTo = getPredsFor(from); // p(a,b)
			double predDest = getPredsFor(msg.getTo());// p(b,d)
			nrofCopies = (int) Math.ceil(nrofCopies * predFromTo / (predFromTo + predDest));
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return msg;
	}

	public double getPredsFor(DTNHost host) {
		ageDeliveryPreds();
		if (preds.containsKey(host)) {
			return preds.get(host);
		} else {
			return 0;
		}
	}

	@Override
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		return true;
	}

	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring
		}
		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		tryOtherMessages();
	}

	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();
		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have nrof copies property!";
			if (nrofCopies > 1) {
				list.add(m);
			}
		}
		return list;
	}

	/**
	 * Called just before a transfer is finalized (by
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message.
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrof copies left is reduced by one.
	 */
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);
		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		/* reduce the amount of copies left */
		nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
		if (nrofCopies > 1) {
			nrofCopies /= 2;
		} else {
			nrofCopies--;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	}

	@Override
	public SprayAndFocusRouter replicate() {
		return new SprayAndFocusRouter(this);
	}

	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(preds.size() +
				" delivery prediction(s)");
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();
			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
					host, value)));
		}
		top.addMoreInfo(ri);
		return top;
	}
}
