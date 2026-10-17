public class GeneralArea extends ConsumerArea {

    public GeneralArea(String name, int consumers) {
        super(name, consumers);
    }

    @Override
    public int restorationPriority() {
        return 1;
    }
}