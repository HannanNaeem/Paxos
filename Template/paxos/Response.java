package paxos;
import java.io.Serializable;

import paxos.Paxos.retStatus;

/**
 * Please fill in the data structure you use to represent the response message for each RMI call.
 * Hint: You may need a boolean variable to indicate ack of acceptors and also you may need proposal number and value.
 * Hint: Make it more generic such that you can use it for each RMI call.
 */
public class Response implements Serializable {
    static final long serialVersionUID=2L;
    // your data here
    int prop;
    int max_prop;
    int initiator;
    Object max_val;
    boolean isDecided;
    boolean accept;

    // Your constructor and methods here

    Response(int prop, int max_prop, int initiator, Object max_val, boolean accept, boolean isDecided) {
        this.prop = prop;
        this.max_prop = max_prop;
        this.initiator = initiator;
        this.max_val = max_val;
        this.accept = accept;
        this.isDecided = isDecided;
    }

    Response(int prop, boolean accept, boolean isDecided) {
        this.prop = prop;
        this.accept = accept;
        this.isDecided = isDecided;
    }

    public String toString() {
        return "*RESPONSE* PROP: " + this.prop + " ISDECIDED:" + this.isDecided + " MAX_PROP: " + this.max_prop + " MAX_INITIATOR: " + this.initiator + " MAX_VAL: " + this.max_val;
    }
}
