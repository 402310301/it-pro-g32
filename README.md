# Intelligent Surplus Food Redistribution System

A web app that connects Cape Town businesses that have surplus food (hotels, bakeries, groceries) with
community organisations that need it (food banks, shelters, community kitchens). A matching engine
scores every suitable recipient on eight factors and recommends the best ones. Recipients accept or decline,
volunteer drivers are assigned, and every step is recorded.

Built with only: Java 17+ · MySQL 8 · HTML · CSS.
There's no JavaScript and no framework. The only library is the official MySQL JDBC driver.

# 1. Install these once

| What | Where | Notes |

| JDK 17 or newer | <https://adoptium.net> (Temurin 21 LTS) | Tick "Set JAVA_HOME" in the installer |
| MySQL Server 8 | <https://dev.mysql.com/downloads/installer/> | Choose "Server only" or "Developer Default". Remember the root password you set. |
| VS Code | <https://code.visualstudio.com> | |
| Extension Pack for Java | VS Code → Extensions → search `vscjava.vscode-java-pack` | VS Code also offers to install it when you open the folder |

You need to install Maven. VS Code's Java extension downloads the MySQL driver for you.

# 2. Set your MySQL password

Open `src/main/resources/application.properties` and change this line to your MySQL root password:

```properties
db.password=root
```

You don't have to create the database yourself. The app creates `food_redistribution`, its tables
and the demo data the first time it starts.

# 3. Run it in VS Code

1. File → Open Folder…and choose this `it-pro-g32` folder.
2. Wait until the status bar says the Java project is ready. The first time, it downloads the MySQL driver.
3. Open `src/main/java/za/org/foodredistribution/App.java` and click "Run" above `main`.
   You can also press F5, or go to Run and Debug and choose "Run Food Redistribution".
4. When the terminal shows `Open http://localhost:8080`, open that address in your browser.
5. Sign in with one of the demo accounts below. They are also listed on the login page.

To stop the app, click the red stop button in VS Code.

> Install Maven with,`mvn compile exec:java` from the terminal.

# 4. Sign in and roles

| Username | Password | Role | What they can do |
|---|---|---|---|
| `admin` | `admin123` | Administrator | Everything: users, organisations, drivers, matching weights, audit trail |
| `coordinator` | `coord123` | Coordinator | Everything except Admin: matching, transport, reports |
| `hotel` | `donor123` | Donor (Table Bay Harvest Hotel) | List surplus food for their own organisation, cancel it and confirm handover |
| `bakery` | `donor123` | Donor (Cape Town Central Bakery) | Same as above |
| `kitchen` | `recipient123` | Recipient (Ubuntu Community Kitchen) | Register their own needs, and accept or decline food recommended to them |
| `foodbank` | `recipient123` | Recipient (Khayelitsha Food Bank) | Same as above |

- Passwords are stored as salted PBKDF2 hashes in the `users` table, never as plain text.
- Each user sees only the menu items and buttons their role allows. The server also checks
  permissions, so typing a URL by hand returns "Not allowed".
- Users can change their own password under My account. Administrators create, disable and
  enable accounts under Admin → User accounts.
- If the database has no users at all, the app creates `admin` / `admin123`.
  Change the demo passwords, or set `demo.seed=false`, before real use.
- Sessions last 8 hours from the last click, and everyone signs in again after the app restarts.

# 5. Try it out

The demo data includes donors, recipients, drivers, six months of history, and live donations at different stages.

- Dashboard: sign in as `coordinator` and open DON-…-001 (prepared meals). The matching engine ranks recipients and explains each score.
  Accept or decline a recommendation and watch it re-rank.
- Transport: once a recipient accepts a donation that needs a driver, assign a driver,
  then click "Driver accepted", "Confirm pickup" and "Confirm delivery".
- Admin: approve the pending "Gugulethu Seniors Club". The dairy donation that had no match gets one straight away.
  You can also change the matching weights. They must add up to 100.
- Reports: see the impact numbers, and compare the engine with "first come, first served" on the same donations.

To reset the demo, run `DROP DATABASE food_redistribution;` in MySQL Workbench and start the app again.
To start with an empty system, set `demo.seed=false`.

## Project structure

```
food-redistribution/
├── pom.xml                         Maven build
├── .vscode/                        Run configuration and recommended extension
└── src/main/
    ├── java/za/org/foodredistribution/
    │   ├── App.java                Start here: connects to MySQL, starts the web server on port 8080
    │   ├── AppConfig.java          Reads application.properties
    │   ├── DemoData.java           Cape Town demo data and demo accounts
    │   ├── auth/                   Login: users, roles, password hashing, sessions
    │   ├── db/                     Database and Row
    │   ├── model/                  Records: Donation, RecipientNeed, Match, TransportAssignment, …
    │   ├── repository/             One class per table, plain SQL
    │   ├── service/                MatchingEngine, DonationService, TransportService, AdminService, ReportService
    │   └── web/                    Built-in Java HTTP server: Router, Template, controllers/
    └── resources/
        ├── application.properties  Port and MySQL settings
        ├── db/schema.sql           MySQL tables (run automatically on start-up)
        ├── templates/              HTML pages; login.html is the sign-in page
        ├── static/css/styles.css   All styling and the colour scheme
        └── static/images/          Pictures
```

You can edit the HTML templates and CSS while the app runs. Refresh the browser to see changes.
Changes to Java code need a restart.

# END
