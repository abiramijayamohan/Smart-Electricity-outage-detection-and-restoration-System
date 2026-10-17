public class CriticalArea extends ConsumerArea {

    public CriticalArea(String name, int consumers) {
        super(name, consumers);
    }

    @Override
    public int restorationPriority() {
        return 2;
    }
}