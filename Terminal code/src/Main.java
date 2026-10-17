import java.util.Scanner;

class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        // ==========================================
        // CREATE DISTRIBUTION NETWORK
        // ==========================================

        DistributionNetwork network =
                new DistributionNetwork();


        // ==========================================
        // CREATE SUBSTATIONS
        // ==========================================

        Substation s1 =
                new Substation(
                        "S1",
                        "Main Substation"
                );

        Substation s2 =
                new Substation(
                        "S2",
                        "Backup Substation"
                );

        network.addSubstation(s1);
        network.addSubstation(s2);


        // ==========================================
        // CREATE CONSUMER AREAS
        // ==========================================

        ConsumerArea areaA =
                new GeneralArea("Area A", 120);

        ConsumerArea areaB =
                new GeneralArea("Area B", 180);

        ConsumerArea hospital =
                new CriticalArea("City Hospital", 80);

        ConsumerArea areaD =
                new GeneralArea("Area D", 150);

        ConsumerArea areaE =
                new GeneralArea("Area E", 200);

        ConsumerArea emergency =
                new CriticalArea("Emergency Centre", 60);

        ConsumerArea areaG =
                new GeneralArea("Area G", 100);

        ConsumerArea areaH =
                new GeneralArea("Area H", 170);

        ConsumerArea areaI =
                new GeneralArea("Area I", 140);

        ConsumerArea areaJ =
                new GeneralArea("Area J", 220);


        network.addArea(areaA);
        network.addArea(areaB);
        network.addArea(hospital);
        network.addArea(areaD);
        network.addArea(areaE);
        network.addArea(emergency);
        network.addArea(areaG);
        network.addArea(areaH);
        network.addArea(areaI);
        network.addArea(areaJ);


        // ==========================================
        // CREATE FEEDER LINES
        // ==========================================

        FeederLine line1 =
                new FeederLine(
                        "S1",
                        "Area A",
                        false
                );

        FeederLine line2 =
                new FeederLine(
                        "Area A",
                        "Area B",
                        false
                );

        FeederLine line3 =
                new FeederLine(
                        "Area B",
                        "City Hospital",
                        false
                );

        FeederLine line4 =
                new FeederLine(
                        "Area B",
                        "Area D",
                        false
                );

        FeederLine line5 =
                new FeederLine(
                        "Area D",
                        "Area E",
                        false
                );

        FeederLine line6 =
                new FeederLine(
                        "S2",
                        "Emergency Centre",
                        false
                );

        FeederLine line7 =
                new FeederLine(
                        "Emergency Centre",
                        "Area G",
                        false
                );

        FeederLine line8 =
                new FeederLine(
                        "Area G",
                        "Area H",
                        false
                );

        FeederLine line9 =
                new FeederLine(
                        "Area H",
                        "Area I",
                        false
                );

        FeederLine line10 =
                new FeederLine(
                        "Area I",
                        "Area J",
                        false
                );


        // ==========================================
        // APPROVED BACKUP LINKS
        // ==========================================

        FeederLine backup1 =
                new FeederLine(
                        "Area D",
                        "Area G",
                        true
                );

        // Hospital backup route
        // This bypasses the failed Area B -> City Hospital line
        FeederLine backup2 =
                new FeederLine(
                        "Area H",
                        "City Hospital",
                        true
                );


        // ==========================================
        // ADD LINES TO NETWORK
        // ==========================================

        network.addLine(line1);
        network.addLine(line2);
        network.addLine(line3);
        network.addLine(line4);
        network.addLine(line5);

        network.addLine(line6);
        network.addLine(line7);
        network.addLine(line8);
        network.addLine(line9);
        network.addLine(line10);

        network.addLine(backup1);
        network.addLine(backup2);


        // ==========================================
        // CREATE REPAIR TEAMS
        // ==========================================

        RepairTeam team1 =
                new RepairTeam(
                        1,
                        "Team Alpha"
                );

        RepairTeam team2 =
                new RepairTeam(
                        2,
                        "Team Beta"
                );

        RepairTeam team3 =
                new RepairTeam(
                        3,
                        "Team Gamma"
                );


        // ==========================================
        // MAIN MENU
        // ==========================================

        int choice = 0;

        while (choice != 9) {

            System.out.println();
            System.out.println("========================================");
            System.out.println(" SMART ELECTRICITY OUTAGE SYSTEM");
            System.out.println("========================================");

            System.out.println("1. Display Network");
            System.out.println("2. Detect Affected Areas");
            System.out.println("3. Fail a Feeder Line");
            System.out.println("4. View Repair Priority");
            System.out.println("5. Assign Repair Team");
            System.out.println("6. Complete Repair");
            System.out.println("7. View Approved Alternate Connections");
            System.out.println("8. Activate Approved Backup Connection");
            System.out.println("9. Exit");

            System.out.print("\nEnter your choice: ");

            choice = scanner.nextInt();
            scanner.nextLine();


            // ======================================
            // OPTION 1 - DISPLAY NETWORK
            // ======================================

            if (choice == 1) {

                network.displayNetwork();
            }


            // ======================================
            // OPTION 2 - DETECT AFFECTED AREAS
            // ======================================

            else if (choice == 2) {

                network.displayAffectedAreas();
            }


            // ======================================
            // OPTION 3 - FAIL FEEDER LINE
            // ======================================

            else if (choice == 3) {

                System.out.println(
                        "\nAvailable feeder lines:"
                );

                System.out.println("1. S1 -> Area A");
                System.out.println("2. Area A -> Area B");
                System.out.println("3. Area B -> City Hospital");
                System.out.println("4. Area B -> Area D");
                System.out.println("5. Area D -> Area E");
                System.out.println("6. S2 -> Emergency Centre");
                System.out.println("7. Emergency Centre -> Area G");
                System.out.println("8. Area G -> Area H");
                System.out.println("9. Area H -> Area I");
                System.out.println("10. Area I -> Area J");

                System.out.print(
                        "\nSelect line to fail: "
                );

                int lineChoice =
                        scanner.nextInt();

                scanner.nextLine();

                System.out.print(
                        "Enter estimated repair time (minutes): "
                );

                int repairTime =
                        scanner.nextInt();

                scanner.nextLine();


                FeederLine selectedLine = null;


                if (lineChoice == 1) {

                    selectedLine = line1;

                } else if (lineChoice == 2) {

                    selectedLine = line2;

                } else if (lineChoice == 3) {

                    selectedLine = line3;

                } else if (lineChoice == 4) {

                    selectedLine = line4;

                } else if (lineChoice == 5) {

                    selectedLine = line5;

                } else if (lineChoice == 6) {

                    selectedLine = line6;

                } else if (lineChoice == 7) {

                    selectedLine = line7;

                } else if (lineChoice == 8) {

                    selectedLine = line8;

                } else if (lineChoice == 9) {

                    selectedLine = line9;

                } else if (lineChoice == 10) {

                    selectedLine = line10;
                }


                if (selectedLine == null) {

                    System.out.println(
                            "Invalid line selection."
                    );

                } else if (!selectedLine.isWorking()) {

                    System.out.println(
                            "This line has already failed."
                    );

                } else {

                    // Fail the selected line
                    selectedLine.failLine();

                    System.out.println(
                            "\nLine "
                                    + selectedLine.getFrom()
                                    + " -> "
                                    + selectedLine.getTo()
                                    + " has FAILED."
                    );

                    System.out.println(
                            "Estimated repair time: "
                                    + repairTime
                                    + " minutes."
                    );


                    // Detect affected areas
                    network.displayAffectedAreas();


                    // Create fault report
                    network.createFaultReport(
                            selectedLine,
                            repairTime
                    );
                }
            }


            // ======================================
            // OPTION 4 - VIEW REPAIR PRIORITY
            // ======================================

            else if (choice == 4) {

                network.displayRepairQueue();
            }


            // ======================================
            // OPTION 5 - ASSIGN REPAIR TEAM
            // ======================================

            else if (choice == 5) {

                System.out.println(
                        "\n--- Assign Repair Team ---"
                );


                // Show current repair priority first
                network.displayRepairQueue();


                System.out.print(
                        "\nEnter Fault Report ID: "
                );

                int reportId =
                        scanner.nextInt();

                scanner.nextLine();


                // Find the fault report
                FaultReport report =
                        network.getFaultReportById(reportId);


                if (report == null) {

                    System.out.println(
                            "\nFault Report not found."
                    );

                    System.out.println(
                            "Please enter the Report ID exactly as shown above."
                    );

                } else if (report.isCompleted()) {

                    System.out.println(
                            "\nThis repair has already been completed."
                    );

                } else {

                    System.out.println(
                            "\nSelected Fault Report:"
                    );

                    System.out.println(
                            "Report ID: FR-"
                                    + String.format(
                                    "%03d",
                                    report.getReportId()
                            )
                    );

                    System.out.println(
                            "Critical Areas Affected: "
                                    + report.getCriticalAreasAffected()
                    );

                    System.out.println(
                            "Consumers Affected: "
                                    + report.getConsumersAffected()
                    );


                    System.out.println(
                            "\nAvailable Repair Teams:"
                    );

                    System.out.println(
                            "1. Team Alpha"
                    );

                    System.out.println(
                            "2. Team Beta"
                    );

                    System.out.println(
                            "3. Team Gamma"
                    );


                    System.out.print(
                            "\nSelect team: "
                    );

                    int teamChoice =
                            scanner.nextInt();

                    scanner.nextLine();


                    RepairTeam selectedTeam = null;


                    if (teamChoice == 1) {

                        selectedTeam = team1;

                    } else if (teamChoice == 2) {

                        selectedTeam = team2;

                    } else if (teamChoice == 3) {

                        selectedTeam = team3;

                    } else {

                        System.out.println(
                                "Invalid team selection."
                        );
                    }


                    // Assign the selected team
                    if (selectedTeam != null) {

                        network.assignRepairTeam(
                                report,
                                selectedTeam
                        );
                    }
                }
            }


            // ======================================
            // OPTION 6 - COMPLETE REPAIR
            // ======================================

            else if (choice == 6) {

                System.out.println(
                        "\n--- Complete Repair ---"
                );


                // Show current repair priority first
                network.displayRepairQueue();


                System.out.print(
                        "\nEnter Fault Report ID: "
                );

                int reportId =
                        scanner.nextInt();

                scanner.nextLine();


                FaultReport report =
                        network.getFaultReportById(reportId);


                if (report == null) {

                    System.out.println(
                            "\nFault Report not found."
                    );

                    System.out.println(
                            "Please enter the Report ID exactly as shown above."
                    );

                } else {

                    // Complete the repair
                    network.completeRepair(report);
                }
            }


            // ======================================
            // OPTION 7 - VIEW APPROVED BACKUP LINKS
            // ======================================

            else if (choice == 7) {

                network.displayApprovedBackupLinks();
            }


            // ======================================
            // OPTION 8 - ACTIVATE APPROVED BACKUP
            // ======================================

            else if (choice == 8) {

                System.out.println(
                        "\n--- Activate Approved Backup Connection ---"
                );

                System.out.println(
                        "1. Area D -> Area G"
                );

                System.out.println(
                        "2. Area H -> City Hospital (Hospital Backup Route)"
                );

                System.out.print(
                        "\nSelect backup connection: "
                );

                int backupChoice = scanner.nextInt();
                scanner.nextLine();

                if (backupChoice == 1) {

                    network.activateBackupLink(backup1);

                }

                else if (backupChoice == 2) {

                    network.activateBackupLink(backup2);

                }

                else {

                    System.out.println(
                            "Invalid backup selection."
                    );
                }
            }


            // ======================================
            // INVALID OPTION
            // ======================================

            else if (choice != 9) {

                System.out.println(
                        "\nInvalid choice. Please try again."
                );
            }
        }


        // ==========================================
        // EXIT
        // ==========================================

        System.out.println(
                "\nThank you for using the system."
        );

        scanner.close();
    }
}