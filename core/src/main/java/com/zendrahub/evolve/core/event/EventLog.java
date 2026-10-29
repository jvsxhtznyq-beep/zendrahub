package com.zendrahub.evolve.core.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * The world's history: an append-only log with stable ids. Memory is bounded:
 * once {@code capacity} events are held, the oldest are dropped (settlements keep
 * their own long-term memories of what mattered to them).
 */
public final class EventLog {
    public static final int DEFAULT_CAPACITY = 5000;

    private final ArrayDeque<SimEvent> events = new ArrayDeque<>();
    private final int capacity;
    private long nextId = 1;
    private long totalRecorded;

    public EventLog() {
        this(DEFAULT_CAPACITY);
    }

    public EventLog(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public SimEvent record(int day, EventType type, int settlementId, int regionId, int cellX, int cellZ,
                           String message, long causeId) {
        SimEvent e = new SimEvent(nextId++, day, type, settlementId, regionId, cellX, cellZ, message, causeId);
        append(e);
        return e;
    }

    /** Restores a saved event, keeping its id. */
    public void restore(SimEvent e) {
        append(e);
        nextId = Math.max(nextId, e.id() + 1);
    }

    private void append(SimEvent e) {
        events.addLast(e);
        totalRecorded++;
        while (events.size() > capacity) {
            events.removeFirst();
        }
    }

    public long nextId() {
        return nextId;
    }

    public void setNextId(long nextId) {
        this.nextId = Math.max(this.nextId, nextId);
    }

    public long totalRecorded() {
        return totalRecorded;
    }

    public void setTotalRecorded(long totalRecorded) {
        this.totalRecorded = totalRecorded;
    }

    public int size() {
        return events.size();
    }

    public int capacity() {
        return capacity;
    }

    /** All retained events, oldest first. */
    public List<SimEvent> all() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /** The newest {@code n} events, oldest first. */
    public List<SimEvent> recent(int n) {
        List<SimEvent> out = new ArrayList<>(Math.min(n, events.size()));
        Iterator<SimEvent> it = events.descendingIterator();
        while (it.hasNext() && out.size() < n) {
            out.add(it.next());
        }
        Collections.reverse(out);
        return out;
    }

    public List<SimEvent> filter(Predicate<SimEvent> p) {
        List<SimEvent> out = new ArrayList<>();
        for (SimEvent e : events) {
            if (p.test(e)) {
                out.add(e);
            }
        }
        return out;
    }

    public List<SimEvent> ofType(EventType type) {
        return filter(e -> e.type() == type);
    }

    public SimEvent byId(long id) {
        for (Iterator<SimEvent> it = events.descendingIterator(); it.hasNext(); ) {
            SimEvent e = it.next();
            if (e.id() == id) {
                return e;
            }
            if (e.id() < id) {
                break;
            }
        }
        return null;
    }

    /** Follows cause links back from an event; the result starts with the root cause. */
    public List<SimEvent> causalChain(long eventId) {
        List<SimEvent> chain = new ArrayList<>();
        SimEvent e = byId(eventId);
        while (e != null && chain.size() < 64) {
            chain.add(e);
            e = e.causeId() >= 0 ? byId(e.causeId()) : null;
        }
        Collections.reverse(chain);
        return chain;
    }
}
