public class Substation {

    private String id;
    private String name;
    private boolean active;

    public Substation(String id, String name) {
        this.id = id;
        this.name = name;
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public void failSource() {
        active = false;
    }

    public void restoreSource() {
        active = true;
    }

}