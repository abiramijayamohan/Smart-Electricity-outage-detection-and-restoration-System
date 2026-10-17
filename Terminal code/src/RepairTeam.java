public class RepairTeam {

    private int teamId;
    private String teamName;
    private boolean available;

    public RepairTeam(int teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.available = true;
    }

    public int getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public boolean isAvailable() {
        return available;
    }

    public void assignTeam() {
        available = false;
    }

    public void releaseTeam() {
        available = true;
    }

    public void displayTeam() {
        String status;

        if (available) {
            status = "Available";
        } else {
            status = "Busy";
        }

        System.out.println(
                "Team " + teamId +
                        " - " + teamName +
                        " | " + status
        );
    }
}