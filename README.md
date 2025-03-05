# Project Overview

This project is part of the COSC 322 course at UBC Okanagan, where we are developing an AI agent to play the Game of Amazons. Our AI will use the Monte Carlo Tree Search (MCTS) algorithm to make intelligent moves against opponents in a tournament setting.

# Project Structure
```
├── src/                                   # Source code directory
│   ├── docs/                              # Documentation
│   │   ├── COSC 322 Progress Report.md    # Progress report notes
│   │   └── COSC 322 Progress Report.pdf   # Progress report PDF
│   │   
│   ├── main/                              # Main source code
│   │   ├── java/                          # Java source files
│   │   │   └── ubc/cosc322/               # Main package
│   │   │       ├── BasePlayer.java        # Player abstract class
│   │   │       ├── Main.java              # Main entry point
│   │   │       ├── MonteCarloPlayer.java  # Monte carlo player
│   │   │       ├── MoveActionFactory.java # Generates all possible moves
│   │   │       ├── MoveAction.java        # Store queen & arrow
│   │   │       └── RandomPlayer.java      # Random moving player
│   │   │
│   │   └── resources/                     # Resource files (configs, properties)
│   │       ├── config.properties          # Configuration file for game settings
│   │       └── images/                    # Images for GitHub repo
│   │
│   └── test/                              # Test source code
│       └── java/                          # Test Java files
│           └── ubc/cosc322/               # Test package
│
├── target/                                # Compiled output (GITIGNORED)
│
├── .gitignore                             # Git ignore file
├── pom.xml                                # Maven project configuration
└── README.md                              # Project overview
```
