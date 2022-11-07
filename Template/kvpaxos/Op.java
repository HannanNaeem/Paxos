package kvpaxos;
import java.io.Serializable;

/**
 * You may find this class useful, free to use it.
 */
public class Op implements Serializable{
    static final long serialVersionUID=33L;
    String op;
    int ClientSeq;
    String client_id;
    String key;
    Integer value;


    // PUT CONST
    public Op(String op, int ClientSeq, String key, Integer value, String client_id){
        this.client_id = client_id;
        this.op = op;
        this.ClientSeq = ClientSeq;
        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        Op other = Op.class.cast(o);

        if (this.op == other.op && this.ClientSeq == other.ClientSeq && this.client_id == other.client_id && this.key == other.key && this.value == other.value)
            return true;

        return false;
    }

    public String toString() { 
        return "KV_OBJ -- OP: " + this.op + " SeqC: " + this.ClientSeq + " client_id: "  + this.client_id + " KEY: " + this.key + " Val: " + this.value;
    }
}
