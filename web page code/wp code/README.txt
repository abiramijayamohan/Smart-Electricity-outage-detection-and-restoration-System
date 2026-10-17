SMART ELECTRICITY OUTAGE MONITORING SYSTEM - SQLITE BACKEND

REQUIREMENTS
- IntelliJ IDEA
- JDK 17 or later
- Maven (IntelliJ can import the included pom.xml and download dependencies)
- Internet for the first Maven dependency download

OPEN IN INTELLIJ
1. Extract this folder to a location such as Desktop.
2. In IntelliJ, choose File > Open and select the SmartElectricitySQLite folder (the folder containing pom.xml).
3. If prompted, choose Open as Project / Load Maven Project.
4. Set Project SDK to JDK 17 or later: File > Project Structure > Project SDK.
5. Wait for Maven to download sqlite-jdbc.
6. Open src/main/java/Main.java and run Main.main().
7. In the Run console, look for "SQLite connected: outage_monitor.db" and "URL: http://localhost:8080".
8. Open http://localhost:8080 in your browser.

WHAT IS SAVED
- Outage report form entries
- Feeder line faults triggered on the map
- Repair team dispatch and expected completion minutes
- Repair completion and whether it finished within the given time
- Event history, which is reloaded into the existing Live Log area

DATABASE FILE
The app creates outage_monitor.db in the working directory used by IntelliJ. Keep the app running while using the webpage. Do not open the page directly as a file; use http://localhost:8080.

IMPORTANT
The webpage layout, CSS, map, and line animations have been kept from the supplied Main.java. Database calls are added behind the existing actions. If port 8080 is already in use, stop the other app or change the port in Main.java.
