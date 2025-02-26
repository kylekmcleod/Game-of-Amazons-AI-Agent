# Project Overview

This project is part of the COSC 322 course at UBC Okanagan, where we are developing an AI agent to play the Game of Amazons. Our AI will use the Monte Carlo Tree Search (MCTS) algorithm to make intelligent moves against opponents in a tournament setting.

# Project Structure
```
├── src/                                   # Source code directory
│   ├── docs/                              # Documentation
│   │   ├── progress_report.md             # Progress report notes.
│   │   └── user_guide.md                  # User guide for the game.
│   │   
│   ├── main/                              # Main source code
│   │   ├── java/                          # Java source files
│   │   │   └── ubc/cosc322/               # Main package
│   │   │       ├── COSC322Test.java       # Main game player class
│   │   │       ├── MCTSNode.java          # Class representing a node in the tree
│   │   │       ├── MCTS.java              # Class implementing the MCTS algorithm
│   │   │       └── LocalTest.java         # Class to test locally
│   │   │
│   │   └── resources/                     # Resource files (configs, properties)
│   │       ├── config.properties          # Configuration file for game settings
│   │       └── images/                    # Images for GitHub repo
│   │
│   └── test/                              # Test source code
│       └── java/                          # Test Java files
│           └── ubc/cosc322/               # Test package
│               ├── MCTSNodeTest.java      # Unit tests for MCTSNode
│               └── MCTest.java            # Unit tests for MCTS algorithm
│
├── target/                                # Compiled output (GITIGNORED)
│
├── .gitignore                             # Git ignore file
├── pom.xml                                # Maven project configuration
└── README.md                              # Project overview
```
