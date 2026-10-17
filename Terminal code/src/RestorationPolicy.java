import java.util.Comparator;

public abstract class RestorationPolicy {

    public abstract Comparator<FaultReport> getComparator();
}