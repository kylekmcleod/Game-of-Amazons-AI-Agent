# Project Overview

### Game of Amazons AI Agent
This project is part of the COSC 322 course at UBC Okanagan, where we are developing an AI agent to play the [Game of Amazons](https://en.wikipedia.org/wiki/Game_of_the_Amazons). Our AI will use the Monte Carlo Tree Search (MCTS) algorithm to make moves against opponents. All implementation is in Java.

### Algorithm
This project uses a [Monte Carlo Search Tree](https://en.wikipedia.org/wiki/Monte_Carlo_tree_search) in order to find optimal moves. It builds a search tree by simulating multiple random games from a given state and uses the results to estimate the best move. The algorithm consists of four main steps:

- Selection - Navigate the search tree using an Upper Confidence Bound until a promising node is found.
- Expansion - Add one or more child nodes to explore new possible moves.
- Simulation - Play out a random game from the expanded node to estimate its outcome.
- Backpropagation - Propagate the simulation results back up the tree to refine move evaluations.
  
# Monte Carlo Configuration
The bot can be fine-tuned to run optimally on different systems. The configuration variables can be found at the top of the ```MonteCarloPlayer.java``` class and can be adjusted to balance performance and accuracy.
```java
// MCTS parameters:
// These can be adjusted to improve the bot's performance.
private static final int MAX_DEPTH = 1;
private static final long MAX_TIME = 10 * 2800;
private static final long MAX_MEMORY = 4L * 1024 * 1024 * 1024;
private static int MOVE_CHOICES = 20;
private static int INCREASE_MOVE_CHOICES = 3;

// Heuristic weights:
// Higher values mean the bot will prioritize that heuristic more.
private static final double MOBILITY_WEIGHT = 0.3;
private static final double BLOCKING_WEIGHT = 1.0;
```
# Project Structure
```
├── src/                                   # Source code directory
│   ├── docs/                              # Documentation
│   │   ├── COSC 322 Progress Report.md    # Progress report notes
│   │   ├── COSC 322 Progress Report.pdf   # Progress report PDF
│   │   └── Amazons_Strategy.pdf           # Amazons strategy Booklet
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
│   │   └── resources/                     # Resource files
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
