import java.util.*;

public class DistributionNetwork {

    private HashMap<String, ConsumerArea> areas;

    private HashMap<String, Substation> substations;

    private HashMap<String, ArrayList<FeederLine>> graph;

    private ArrayList<FaultReport> faultReports;

    private PriorityQueue<FaultReport> repairQueue;

    private RestorationPolicy restorationPolicy;

    private HashSet<FeederLine> activeBackupLinks;


    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    public DistributionNetwork() {

        areas = new HashMap<>();

        substations = new HashMap<>();

        graph = new HashMap<>();

        faultReports = new ArrayList<>();

        activeBackupLinks = new HashSet<>();


        restorationPolicy =
                new RestorationPolicy() {

                    @Override
                    public Comparator<FaultReport>
                    getComparator() {

                        return (a, b) -> {

                            // ==========================================
                            // 1. CRITICAL AREAS ALWAYS HAVE PRIORITY
                            // ==========================================

                            boolean aCritical =
                                    a.getCriticalAreasAffected()
                                            > 0;

                            boolean bCritical =
                                    b.getCriticalAreasAffected()
                                            > 0;


                            if (aCritical != bCritical) {

                                return Boolean.compare(
                                        bCritical,
                                        aCritical
                                );
                            }


                            // ==========================================
                            // 2. AMONG CRITICAL REPORTS
                            //
                            //    COMPARE CRITICAL CONSUMERS ONLY
                            //
                            //    General consumers are NOT considered
                            //    when comparing critical reports.
                            // ==========================================

                            if (aCritical) {

                                if (a.getCriticalConsumersAffected()
                                        != b.getCriticalConsumersAffected()) {

                                    return Integer.compare(
                                            b.getCriticalConsumersAffected(),
                                            a.getCriticalConsumersAffected()
                                    );
                                }
                            }


                            // ==========================================
                            // 3. AMONG GENERAL REPORTS
                            //
                            //    COMPARE GENERAL CONSUMERS
                            //    FROM HIGHEST TO LOWEST
                            // ==========================================

                            else {

                                if (a.getGeneralConsumersAffected()
                                        != b.getGeneralConsumersAffected()) {

                                    return Integer.compare(
                                            b.getGeneralConsumersAffected(),
                                            a.getGeneralConsumersAffected()
                                    );
                                }
                            }


                            // ==========================================
                            // 4. IF PRIORITY IS STILL EQUAL
                            //
                            //    LOWER REPORT ID FIRST
                            // ==========================================

                            return Integer.compare(
                                    a.getReportId(),
                                    b.getReportId()
                            );
                        };
                    }
                };


        repairQueue =
                new PriorityQueue<>(
                        restorationPolicy.getComparator()
                );
    }


    // ==========================================
    // ADD SUBSTATION
    // ==========================================

    public void addSubstation(
            Substation substation) {

        substations.put(
                substation.getId(),
                substation
        );


        graph.put(
                substation.getId(),
                new ArrayList<>()
        );
    }


    // ==========================================
    // ADD CONSUMER AREA
    // ==========================================

    public void addArea(
            ConsumerArea area) {

        areas.put(
                area.getName(),
                area
        );


        graph.put(
                area.getName(),
                new ArrayList<>()
        );
    }


    // ==========================================
    // ADD FEEDER LINE
    // ==========================================

    public void addLine(
            FeederLine line) {

        graph.get(
                line.getFrom()
        ).add(line);


        graph.get(
                line.getTo()
        ).add(line);
    }


    // ==========================================
    // ACTIVATE APPROVED BACKUP LINK
    // ==========================================

    public void activateBackupLink(
            FeederLine backupLine) {

        if (!backupLine.isBackupLink()) {

            System.out.println(
                    "This is not an approved backup link."
            );

            return;
        }


        if (activeBackupLinks.contains(
                backupLine)) {

            System.out.println(
                    "This backup link is already active."
            );

            return;
        }


        if (!backupLine.isWorking()) {

            System.out.println(
                    "This backup link is currently failed and cannot be activated."
            );

            return;
        }


        activeBackupLinks.add(
                backupLine
        );


        System.out.println(
                "\nApproved backup link activated:"
        );


        System.out.println(
                backupLine.getFrom()
                        + " -> "
                        + backupLine.getTo()
        );


        System.out.println(
                "\nRecalculating network connectivity..."
        );


        displayAffectedAreas();
    }


    // ==========================================
    // CHECK IF BACKUP LINK IS ACTIVE
    // ==========================================

    public boolean isBackupLinkActive(
            FeederLine backupLine) {

        return activeBackupLinks.contains(
                backupLine
        );
    }


    // ==========================================
    // FIND CONNECTED NODES USING BFS
    // ==========================================

    public HashSet<String>
    findConnectedNodes() {

        HashSet<String> visited =
                new HashSet<>();


        Queue<String> queue =
                new LinkedList<>();


        // Start BFS from all active substations
        for (Substation substation :
                substations.values()) {

            if (substation.isActive()) {

                queue.add(
                        substation.getId()
                );

                visited.add(
                        substation.getId()
                );
            }
        }


        // BFS traversal
        while (!queue.isEmpty()) {

            String current =
                    queue.poll();


            for (FeederLine line :
                    graph.get(current)) {

                // Failed lines cannot provide connectivity
                if (!line.isWorking()) {

                    continue;
                }


                // Backup links can only be used
                // after operator activation
                if (line.isBackupLink()
                        && !activeBackupLinks.contains(line)) {

                    continue;
                }


                String next;


                // Graph is treated as bidirectional
                if (line.getFrom().equals(current)) {

                    next = line.getTo();

                } else {

                    next = line.getFrom();
                }


                if (!visited.contains(next)) {

                    visited.add(next);

                    queue.add(next);
                }
            }
        }


        return visited;
    }


    // ==========================================
    // DISPLAY AFFECTED AREAS
    // ==========================================

    public void displayAffectedAreas() {

        HashSet<String> connected =
                findConnectedNodes();


        System.out.println(
                "\nAffected Consumer Areas:"
        );


        boolean found = false;


        for (String areaName :
                areas.keySet()) {

            if (!connected.contains(areaName)) {

                System.out.println(
                        "- " + areaName
                );

                found = true;
            }
        }


        if (!found) {

            System.out.println(
                    "No affected areas."
            );
        }
    }


    // ==========================================
    // CREATE FAULT REPORT
    // ==========================================

    public void createFaultReport(
            FeederLine failedLine,
            int repairMinutes) {

        HashSet<String> connected =
                findConnectedNodes();


        int criticalAreas =
                0;


        int criticalConsumers =
                0;


        int generalConsumers =
                0;


        // ==========================================
        // CALCULATE AFFECTED CONSUMERS
        // SEPARATELY FOR CRITICAL AND GENERAL AREAS
        // ==========================================

        for (ConsumerArea area :
                areas.values()) {

            if (!connected.contains(
                    area.getName())) {


                if (area.restorationPriority()
                        == 2) {

                    criticalAreas++;


                    criticalConsumers =
                            criticalConsumers
                                    + area.getConsumers();

                } else {

                    generalConsumers =
                            generalConsumers
                                    + area.getConsumers();
                }
            }
        }


        int totalConsumers =
                criticalConsumers
                        + generalConsumers;


        int reportId =
                faultReports.size() + 1;


        FaultReport report =
                new FaultReport(
                        reportId,
                        failedLine,
                        repairMinutes,
                        criticalAreas,
                        criticalConsumers,
                        generalConsumers
                );


        addFaultReport(report);


        System.out.println(
                "\nFault Report Created!"
        );


        System.out.println(
                "Report ID: FR-"
                        + String.format(
                        "%03d",
                        reportId
                )
        );


        System.out.println(
                "Critical Areas Affected: "
                        + criticalAreas
        );


        System.out.println(
                "Consumers Affected: "
                        + totalConsumers
        );
    }


    // ==========================================
    // ADD FAULT REPORT
    // ==========================================

    public void addFaultReport(
            FaultReport report) {

        faultReports.add(
                report
        );


        repairQueue.add(
                report
        );
    }


    // ==========================================
    // FIND FAULT REPORT BY ID
    // ==========================================

    public FaultReport
    getFaultReportById(
            int reportId) {

        for (FaultReport report :
                faultReports) {

            if (report.getReportId()
                    == reportId) {

                return report;
            }
        }


        return null;
    }


    // ==========================================
    // DISPLAY REPAIR PRIORITY QUEUE
    // ==========================================

    public void displayRepairQueue() {

        System.out.println(
                "\n--- Repair Priority Queue ---"
        );


        if (repairQueue.isEmpty()) {

            System.out.println(
                    "No pending repair jobs."
            );

            return;
        }


        PriorityQueue<FaultReport>
                tempQueue =
                new PriorityQueue<>(
                        repairQueue
                );


        while (!tempQueue.isEmpty()) {

            FaultReport report =
                    tempQueue.poll();


            System.out.println(
                    "Report ID: "
                            + report.getReportId()
                            + " | Critical Areas: "
                            + report.getCriticalAreasAffected()
                            + " | Total Consumers: "
                            + report.getConsumersAffected()
            );
        }
    }


    // ==========================================
    // ASSIGN REPAIR TEAM
    // ==========================================

    public void assignRepairTeam(
            FaultReport report,
            RepairTeam team) {

        if (report.isCompleted()) {

            System.out.println(
                    "This repair is already completed."
            );

            return;
        }


        if (!team.isAvailable()) {

            System.out.println(
                    team.getTeamName()
                            + " is already busy."
            );

            return;
        }


        report.assignTeam(
                team
        );


        System.out.println(
                team.getTeamName()
                        + " assigned to Fault Report "
                        + report.getReportId()
        );
    }


    // ==========================================
    // COMPLETE REPAIR
    // ==========================================

    public void completeRepair(
            FaultReport report) {

        if (report.isCompleted()) {

            System.out.println(
                    "Repair is already completed."
            );

            return;
        }


        if (report.getAssignedTeam()
                == null) {

            System.out.println(
                    "No repair team assigned."
            );

            return;
        }


        report.completeRepair();


        repairQueue.remove(
                report
        );


        System.out.println(
                "\nRepair completed successfully."
        );


        System.out.println(
                "Line "
                        + report.getFailedLine().getFrom()
                        + " -> "
                        + report.getFailedLine().getTo()
                        + " : WORKING"
        );


        System.out.println(
                report.getAssignedTeam().getTeamName()
                        + " : AVAILABLE"
        );


        System.out.println(
                "\nRecalculating network connectivity..."
        );


        displayAffectedAreas();
    }


    // ==========================================
    // DISPLAY APPROVED BACKUP LINKS
    // ==========================================

    public void displayApprovedBackupLinks() {

        System.out.println(
                "\n--- Approved Alternate Connections ---"
        );


        boolean found = false;


        HashSet<String>
                displayedConnections =
                new HashSet<>();


        for (ArrayList<FeederLine> lines :
                graph.values()) {

            for (FeederLine line :
                    lines) {

                if (!line.isBackupLink()) {

                    continue;
                }


                String node1 =
                        line.getFrom();

                String node2 =
                        line.getTo();


                String connectionKey;


                if (node1.compareTo(node2)
                        < 0) {

                    connectionKey =
                            node1
                                    + " -> "
                                    + node2;

                } else {

                    connectionKey =
                            node2
                                    + " -> "
                                    + node1;
                }


                if (displayedConnections
                        .contains(connectionKey)) {

                    continue;
                }


                displayedConnections.add(
                        connectionKey
                );


                String status;


                if (!line.isWorking()) {

                    status = "FAILED";

                } else if (
                        activeBackupLinks.contains(
                                line)) {

                    status = "ACTIVE";

                } else {

                    status = "AVAILABLE";
                }


                System.out.println(
                        line.getFrom()
                                + " -> "
                                + line.getTo()
                                + " | "
                                + status
                );


                found = true;
            }
        }


        if (!found) {

            System.out.println(
                    "No approved alternate connections."
            );
        }
    }


    // ==========================================
    // DISPLAY NETWORK
    // ==========================================

    public void displayNetwork() {

        System.out.println(
                "\n--- Distribution Network ---"
        );


        for (String node :
                graph.keySet()) {

            System.out.println(
                    "\n" + node + ":"
            );


            for (FeederLine line :
                    graph.get(node)) {

                String next;


                if (line.getFrom().equals(node)) {

                    next = line.getTo();

                } else {

                    next = line.getFrom();
                }


                String status;


                if (!line.isWorking()) {

                    status = "FAILED";

                } else if (
                        line.isBackupLink()
                                && activeBackupLinks.contains(
                                line)) {

                    status = "BACKUP ACTIVE";

                } else if (
                        line.isBackupLink()) {

                    status = "BACKUP AVAILABLE";

                } else {

                    status = "WORKING";
                }


                System.out.println(
                        "  -> "
                                + next
                                + " | "
                                + status
                );
            }
        }
    }
}