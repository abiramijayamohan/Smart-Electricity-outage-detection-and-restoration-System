public class FaultReport {

    private int reportId;
    private FeederLine failedLine;
    private int repairMinutes;

    private int criticalAreasAffected;

    // Number of consumers belonging to affected critical areas
    private int criticalConsumersAffected;

    // Number of consumers belonging to affected general areas
    private int generalConsumersAffected;

    // Total consumers affected
    private int consumersAffected;

    private RepairTeam assignedTeam;
    private boolean completed;


    public FaultReport(
            int reportId,
            FeederLine failedLine,
            int repairMinutes,
            int criticalAreasAffected,
            int criticalConsumersAffected,
            int generalConsumersAffected) {

        this.reportId = reportId;

        this.failedLine = failedLine;

        this.repairMinutes = repairMinutes;

        this.criticalAreasAffected =
                criticalAreasAffected;

        this.criticalConsumersAffected =
                criticalConsumersAffected;

        this.generalConsumersAffected =
                generalConsumersAffected;

        this.consumersAffected =
                criticalConsumersAffected
                        + generalConsumersAffected;

        this.assignedTeam = null;

        this.completed = false;
    }


    /*
     * This constructor is kept for compatibility
     * with the previous version of the class.
     */
    public FaultReport(
            int reportId,
            FeederLine failedLine,
            int repairMinutes,
            int criticalAreasAffected,
            int consumersAffected) {

        this.reportId = reportId;

        this.failedLine = failedLine;

        this.repairMinutes = repairMinutes;

        this.criticalAreasAffected =
                criticalAreasAffected;

        this.criticalConsumersAffected =
                0;

        this.generalConsumersAffected =
                consumersAffected;

        this.consumersAffected =
                consumersAffected;

        this.assignedTeam = null;

        this.completed = false;
    }


    public int getReportId() {

        return reportId;
    }


    public FeederLine getFailedLine() {

        return failedLine;
    }


    public int getRepairMinutes() {

        return repairMinutes;
    }


    public int getCriticalAreasAffected() {

        return criticalAreasAffected;
    }


    public int getCriticalConsumersAffected() {

        return criticalConsumersAffected;
    }


    public int getGeneralConsumersAffected() {

        return generalConsumersAffected;
    }


    public int getConsumersAffected() {

        return consumersAffected;
    }


    public RepairTeam getAssignedTeam() {

        return assignedTeam;
    }


    public boolean isCompleted() {

        return completed;
    }


    public void assignTeam(
            RepairTeam team) {

        assignedTeam = team;

        team.assignTeam();
    }


    public void completeRepair() {

        failedLine.restoreLine();

        completed = true;


        if (assignedTeam != null) {

            assignedTeam.releaseTeam();
        }
    }


    public void displayReport() {

        System.out.println(
                "Fault Report ID: "
                        + reportId
        );


        System.out.println(
                "Failed Line: "
                        + failedLine.getFrom()
                        + " -> "
                        + failedLine.getTo()
        );


        System.out.println(
                "Repair Time: "
                        + repairMinutes
                        + " minutes"
        );


        System.out.println(
                "Critical Areas Affected: "
                        + criticalAreasAffected
        );


        System.out.println(
                "Critical Consumers Affected: "
                        + criticalConsumersAffected
        );


        System.out.println(
                "General Consumers Affected: "
                        + generalConsumersAffected
        );


        System.out.println(
                "Consumers Affected: "
                        + consumersAffected
        );


        if (assignedTeam != null) {

            System.out.println(
                    "Assigned Team: "
                            + assignedTeam.getTeamName()
            );

        } else {

            System.out.println(
                    "Assigned Team: None"
            );
        }


        System.out.println(
                "Status: "
                        + (completed
                        ? "Completed"
                        : "Pending")
        );
    }
}