public class FeederLine {

    private String from;
    private String to;
    private boolean working;
    private boolean backupLink;

    public FeederLine(String from, String to, boolean backupLink) {
        this.from = from;
        this.to = to;
        this.working = true;
        this.backupLink = backupLink;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public boolean isWorking() {
        return working;
    }

    public boolean isBackupLink() {
        return backupLink;
    }

    public void failLine() {
        working = false;
    }

    public void restoreLine() {
        working = true;
    }

    public void displayLine() {
        String status;

        if (working) {
            status = "WORKING";
        } else {
            status = "FAILED";
        }

        System.out.println(
                from + " -> " + to +
                        " | " + status +
                        " | Backup: " + backupLink
        );
    }
}