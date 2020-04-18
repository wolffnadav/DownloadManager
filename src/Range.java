import java.io.Serializable;

public class Range implements Serializable {
    private long start;
    private long end;

    Range(long start, long end) {
        this.start = start;
        this.end = end;
    }


    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;


    }

    public void setEnd(long end) {
        this.end = end;
    }

    public boolean compareStart(Range range) {
        return this.getStart() <= range.getStart();
    }

    public boolean compareEnd(Range range) {
        return this.getEnd() <= range.getEnd();
    }
}
