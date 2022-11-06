package paxos;
import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the request message for each RMI call.
 * Hint: You may need the sequence number for each paxos instance and also you may need proposal number and value.
 * Hint: Make it more generic such that you can use it for each RMI call.
 * Hint: Easier to make each variable public
 */
public class Request implements Serializable {
    static final long serialVersionUID=1L;
    // Your data here
    int seq;
    int prop;
    int initiator;
    Object value;

    int min_done;

    // Your constructor and methods here

    Request(int seq, int prop, int initiator, Object val, int min) {
        this.seq = seq;
        this.prop = prop;
        this.initiator = initiator;
        this.value = val;
        this.min_done = min;
    }

    public String toString() {
        return "*REQUEST* PROP: " + this.prop + " INITIATOR: " + this.initiator + " value: " + this.value;
    }
}
