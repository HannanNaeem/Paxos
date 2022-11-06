package paxos;
import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the response message for each RMI call.
 * Hint: You may need a boolean variable to indicate ack of acceptors and also you may need proposal number and value.
 * Hint: Make it more generic such that you can use it for each RMI call.
 */
public class Response implements Serializable {
    static final long serialVersionUID=2L;
    // your data here
    int prop;
    int max_a_prop;
    int initiator;
    Object max_a_val;
    boolean accept;

    int min_done;

    // Your constructor and methods here

    Response(int prop, int max_a_prop, int initiator, Object max_a_val, boolean accept, int min) {
        this.prop = prop;
        this.max_a_prop = max_a_prop;
        this.max_a_val = max_a_val;
        this.accept = accept;
        this.min_done = min;
        this.initiator = initiator;
    }

    Response(int prop, boolean accept, int min) {
        this.prop = prop;
        this.accept = accept;
        this.min_done = min;
    }


    public String toString() {
        return "*RESPONSE* PROP: " + this.prop + " MAX_PROP: " + this.max_a_prop + " MAX_INITIATOR: " + this.initiator + " MAX_VAL: " + this.max_a_val;
    }
}
