# StudyTracker

A command-line tracker for assignments, quizzes, exams, and projects. Written in plain Java 17 with
no runtime dependencies, a layered design, and a JUnit 5 test suite that runs on every push.

![build](https://github.com/vantrook08/studytracker/actions/workflows/build.yml/badge.svg)

```
StudyTracker - type 'help' for commands, 'quit' to exit.

OVERDUE (1):
  ID  TYPE        COURSE     TITLE                         DUE                   STATUS
   5  Quiz        CS 314     Quiz 5                        Fri Oct 16, 2:00 PM   OVERDUE by 5 days

Due in the next 7 days (3):
  ID  TYPE        COURSE     TITLE                         DUE                   STATUS
   3  Quiz        M 408D     Quiz 6                        Thu Oct 22, 9:30 AM   tomorrow
   2  Exam        CS 314     Midterm 2                     Thu Oct 22, 7:00 PM   tomorrow
   1  Assignment  CS 314     Assignment 4: Linked Lists    Fri Oct 23, 11:59 PM  in 2 days

> score 5 46/50
Recorded 92% for #5 Quiz 5 (marked done).

> stats
COURSE       OPEN   DONE   OVERDUE  AVG SCORE
CS 314          2      1         0        92%
M 408D          1      0         0          -
UGS 303         1      0         0          -
```

## Features

- **Natural due dates**: `fri`, `tomorrow 2pm`, `thu at 7pm`, `10/27 11:59pm`, `2026-11-03 14:00`.
  No time means 11:59 PM.
- **Dashboard on launch** showing anything overdue and everything due this week.
- **Grades** as percents or points (`score 4 46/50`), with per-course averages.
- **Forgiving input**: `cs314` and `CS 314` are the same course, `e` means exam, typos re-prompt instead of crashing.
- **Safe storage**: a plain CSV you can open in Excel, written atomically so a crash can't corrupt it.

## Running it

### In Eclipse
1. **File > Import > Maven > Existing Maven Projects**, then pick this folder.
2. Open `src/main/java/com/studytracker/App.java`, right-click > **Run As > Java Application**.
   Type commands into the Console view.
3. To run the tests: right-click `src/test/java` > **Run As > JUnit Test**.

### From a terminal
```bash
mvn package                               # compiles, runs tests, builds the jar
java -jar target/studytracker.jar         # interactive mode
java -jar target/studytracker.jar upcoming 3   # run one command and exit
```

Your data is saved to `~/.studytracker/tasks.csv`. To use a different file, add
`-Dstudytracker.data=path/to/file.csv` before `-jar`.

## Commands

| Command | What it does |
|---|---|
| `add` | Add an item (asks for course, title, type, due date) |
| `list [all] [course]` | Open items, soonest first. `all` includes finished ones |
| `upcoming [days]` | Items due in the next N days (default 7) |
| `overdue` | Unfinished items past their due date |
| `start <id...>` / `done <id...>` / `reopen <id...>` | Change status, e.g. `done 3 4 5` |
| `score <id> <score>` | Record a grade: `score 4 92` or `score 4 46/50` |
| `delete <id>` | Remove an item (asks to confirm) |
| `stats` | Per-course open / done / overdue / average |

## How it's built

```
src/main/java/com/studytracker/
├── App.java            wires everything together
├── cli/                reading input and printing output
│   ├── CommandLineInterface.java
│   ├── DueDateParser.java
│   └── TaskFormatter.java
├── service/            the rules: what's overdue, upcoming, averages
│   └── TaskService.java
├── storage/            saving and loading
│   ├── TaskRepository.java        (interface)
│   ├── InMemoryTaskRepository.java
│   ├── CsvTaskRepository.java
│   └── Csv.java
└── model/              the data itself
    ├── Task.java
    ├── TaskType.java
    ├── Status.java
    └── CourseSummary.java   (a record)
```

Each layer only talks to the one below it. A few decisions worth calling out:

- **Storage is behind an interface.** `TaskService` depends on `TaskRepository`, not on CSV.
  Tests use the in-memory version; swapping in SQLite later wouldn't touch the service or CLI.
- **Time is injected.** `TaskService` takes a `java.time.Clock`, so tests freeze "now" at a fixed
  moment and overdue/upcoming logic is tested deterministically.
- **Corrupt data fails loudly.** If a line in the CSV can't be parsed, the app refuses to start
  rather than skipping it, because skipping would cause the next save to silently delete it.
- **Writes are atomic.** Saves go to a temp file that's then moved into place.
- **Tasks validate themselves.** The `Task` constructor rejects blank titles, negative scores, and
  so on, so an invalid task can't exist anywhere in the program.

## Roadmap

- [ ] `edit` command (change title, course, or due date)
- [ ] Weighted course grades (e.g. exams 50%, homework 30%, quizzes 20%)
- [ ] Recurring items ("Quiz every Friday until Dec 4")
- [ ] Export to `.ics` so items show up in Google Calendar
- [ ] JavaFX desktop GUI on top of the same `TaskService`

## Troubleshooting

- **Eclipse shows "no JREs installed that are strictly compatible with JavaSE-17"**: this is just a
  warning when you only have a newer JDK (like 21). It still builds and runs fine.
- **Tests don't show up in Eclipse**: right-click the project > **Maven > Update Project**.
