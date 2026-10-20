# Intelligent Surplus Food Redistribution System

A web app that connects Cape Town businesses that have surplus food (hotels, bakeries, grocers) with
community organisations that need it (food banks, shelters, community kitchens). A **matching engine**
scores every suitable recipient on eight factors and recommends the best ones. Recipients accept or decline,
volunteer drivers are assigned, and every step is recorded.

**Built with only:** Java 17+ · MySQL 8 · HTML · CSS.
There's no JavaScript and no framework. The only library is the official MySQL JDBC driver.

**Features:** a sign-in page with four user roles, a brown, green, red, blue and yellow colour scheme,
and placeholder images that you can replace with your own photos.

---

## 1. Install these once

| What | Where | Notes |
|---|---|---|
| **JDK 17 or newer** | <https://adoptium.net> (Temurin 21 LTS) | Tick "Set JAVA_HOME" in the installer |
| **MySQL Server 8** | <https://dev.mysql.com/downloads/installer/> | Choose "Server only" or "Developer Default". **Remember the root password you set.** |
| **VS Code** | <https://code.visualstudio.com> | |
| **Extension Pack for Java** | VS Code → Extensions → search `vscjava.vscode-java-pack` | VS Code also offers to install it when you open the folder |

You do **not** need to install Maven. VS Code's Java extension downloads the MySQL driver for you.

## 2. Set your MySQL password

Open `src/main/resources/application.properties` and change this line to your MySQL root password:

```properties
db.password=root
```

You don't have to create the database yourself. The app creates `food_redistribution`, its tables
and the demo data the first time it starts.

## 3. Run it in VS Code

1. **File → Open Folder…** and choose this `food-redistribution` folder.
2. Wait until the status bar says the Java project is ready. The first time, it downloads the MySQL driver.
3. Open `src/main/java/za/org/foodredistribution/App.java` and click **Run** above `main`.
   You can also press **F5**, or go to **Run and Debug** and choose "Run Food Redistribution".
4. When the terminal shows `Open http://localhost:8080`, open that address in your browser.
5. Sign in with one of the demo accounts below. They are also listed on the login page.

To stop the app, click the red stop button in VS Code.

> Have Maven installed? You can also run `mvn compile exec:java` from the terminal.

## 4. Sign in and roles

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
- Users can change their own password under **My account**. Administrators create, disable and
  enable accounts under **Admin → User accounts**.
- If the database has no users at all, the app creates `admin` / `admin123`.
  **Change the demo passwords, or set `demo.seed=false`, before real use.**
- Sessions last 8 hours from the last click, and everyone signs in again after the app restarts.

## 5. Try it out

The demo data includes donors, recipients, drivers, six months of history, and live donations at different stages.

- **Dashboard:** sign in as `coordinator` and open **DON-…-001** (prepared meals). The matching engine ranks recipients and explains each score.
  Accept or decline a recommendation and watch it re-rank.
- **Transport:** once a recipient accepts a donation that needs a driver, assign a driver,
  then click *Driver accepted*, *Confirm pickup* and *Confirm delivery*.
- **Admin:** approve the pending *Gugulethu Seniors Club*. The dairy donation that had no match gets one straight away.
  You can also change the matching weights. They must add up to 100.
- **Reports:** see the impact numbers, and compare the engine with "first come, first served" on the same donations.

To **reset the demo**, run `DROP DATABASE food_redistribution;` in MySQL Workbench and start the app again.
To start with an **empty** system, set `demo.seed=false`.

---

## Colours

All colours are defined at the top of `src/main/resources/static/css/styles.css`. Change a value
there and the whole site follows.

| Colour | Variable | Used for |
|---|---|---|
| Brown | `--brown`, `--brown-dark` | Brand colour: sidebar, buttons, headings |
| Green | `--green` | Success: completed, delivered, high match scores, recipients |
| Red | `--red` | Danger: expired, critical urgency, cancel, sign out, administrators |
| Blue | `--blue` | Information: transport, in progress, chart "listed" bars, coordinators |
| Yellow | `--yellow` | Attention: the active menu item, pending, awaiting a decision, donors |

## Replacing the images

Every picture is in `src/main/resources/static/images/`. Each placeholder says "PLACEHOLDER IMAGE"
and shows its own file name and the recommended size. To use your own photo, save it over the
placeholder with the **same file name**. `static/images/README.txt` lists every image:

| File | Shown on | Size |
|---|---|---|
| `logo.png` | Sidebar, login page, browser tab | 256 × 256 |
| `login-hero.png` | Left half of the login page | 1600 × 1100 |
| `dashboard-banner.png` | Top of the dashboard | 1600 × 420 |
| `categories/*.png` (7 files) | Donation lists and the donation page, one per food category | 800 × 500 |

---

## How the matching engine works

`service/MatchingEngine.java` scores each recipient need in the same food category from 0 to 100 on:

| Factor | Default weight | Rule |
|---|---|---|
| Expiry urgency | 25 | The sooner the food expires, the higher the score |
| Recipient demand | 20 | Critical 100, High 85, Medium 65, Low 40 |
| Food suitability | 15 | Whether the recipient's storage keeps this food safe. Colder storage can hold warmer food; refrigerated food can never go to ambient storage. |
| Storage capacity | 10 | Room for the quantity offered |
| Quantity fit | 10 | Share of the donation the recipient can take |
| Distance | 5 | Haversine distance from donor to recipient (100 − 4 × km, minimum 20) |
| Transport | 5 | Whether an active volunteer vehicle with enough capacity is available (and refrigerated if needed) |
| Collection window | 10 | How many of the recipient's opening hours fall before the donor's collection deadline |

The final score is the weighted average. Recipients whose storage is unsafe for the food, or who cannot
take any of it, are never recommended.

**Donation lifecycle:** Listed → Recipient Recommended (or *Matching* when nobody fits) → Accepted /
Transport Required → Transport Assigned → Collected → Completed. A donation can also end as Expired or Cancelled.
A background check marks overdue donations as **Expired** every minute.

---

## Project structure

```
food-redistribution/
├── pom.xml                         Maven build (one dependency: mysql-connector-j)
├── .vscode/                        Run configuration and recommended extension
└── src/main/
    ├── java/za/org/foodredistribution/
    │   ├── App.java                Start here: connects to MySQL, starts the web server on port 8080
    │   ├── AppConfig.java          Reads application.properties
    │   ├── DemoData.java           Cape Town demo data and demo accounts (only for an empty database)
    │   ├── auth/                   Login: users, roles, password hashing, sessions
    │   ├── db/                     Database (the only JDBC code) and Row
    │   ├── model/                  Records: Donation, RecipientNeed, Match, TransportAssignment, …
    │   ├── repository/             One class per table, plain SQL
    │   ├── service/                MatchingEngine, DonationService, TransportService, AdminService, ReportService
    │   └── web/                    Built-in Java HTTP server: Router, Template (mini HTML templates), controllers/
    └── resources/
        ├── application.properties  Port and MySQL settings
        ├── db/schema.sql           MySQL tables (run automatically on start-up)
        ├── templates/              HTML pages ({{placeholders}} are filled in by Template.java); login.html is the sign-in page
        ├── static/css/styles.css   All styling and the colour scheme
        └── static/images/          Replaceable pictures (see README.txt in that folder)
```

You can edit the HTML templates and CSS while the app runs. Refresh the browser to see changes.
Changes to Java code need a restart.

## Troubleshooting

| Message | Fix |
|---|---|
| `Access denied for user 'root'` | Fix `db.password` (and `db.user`) in `application.properties` |
| `Communications link failure` | MySQL is not running. On Windows, start the **MySQL80** service (Services app or MySQL Workbench). |
| Can't sign in with the demo accounts | They are only created when the `users` table is empty. Run `DROP DATABASE food_redistribution;` to start over. |
| `Address already in use` | Something else is using port 8080. Change `server.port` in `application.properties`. |
| Red errors on `import` lines in VS Code | Wait for the Java extension to finish loading, or run **Java: Clean Java Language Server Workspace** from the Command Palette |
| `java: release version 17 not supported` | Install JDK 17 or newer, then select it with **Java: Configure Java Runtime** |

## What changed from the original project

The original was a Replit pnpm monorepo: React + TypeScript frontend, Express API with in-memory data,
Drizzle/PostgreSQL schema and a separate, unused Java file. It has been rebuilt as one Java application:

- The React pages became server-rendered HTML templates with the same sections, and the Tailwind styling became plain CSS with the same colours.
- The Express API and in-memory arrays became Java services and repositories backed by MySQL.
- The PostgreSQL schema became MySQL. JSONB factor scores became normal columns, and the transporter, need and audit tables were filled out.
- The original `MatchingEngine.java` grew into the real engine. Storage safety, transport availability and collection windows are now calculated instead of fixed.
- Reports are now calculated from the database instead of being hard-coded.
- These Replit- and Node-only parts were removed: `.replit`, `replit.md`, `.agents`, pnpm workspace files, `node_modules`,
  `lib/api-*` code generation, `mockup-sandbox` and the Vite config.
- A mismatch in the original was fixed: the "new donation" form used different category names from the
  matching data, so new donations never matched anyone.
