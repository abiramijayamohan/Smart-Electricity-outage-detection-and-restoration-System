public class ConsumerArea {

    private String name;
    private int consumers;

    public ConsumerArea(String name, int consumers) {
        this.name = name;
        this.consumers = consumers;
    }

    public String getName() {
        return name;
    }

    public int getConsumers() {
        return consumers;
    }

    public int restorationPriority() {
        return 1;
    }

    public void displayDetails() {
        System.out.println("Area: " + name);
        System.out.println("Consumers: " + consumers);
    }
}