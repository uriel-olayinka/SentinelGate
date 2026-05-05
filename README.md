# SentinelGate: AI-Powered Fraud Analysis Engine

## Overview
SentinelGate is an advanced, AI-enhanced fraud detection system built in Java. It is designed to act as a smart filter between raw transaction data and human analysts. The engine takes in transaction data, evaluates it against deterministic business rules, mathematically scores the overall contextual risk, and leverages Generative AI (Llama 3.1) to provide clear, actionable insights.

## Architecture & Design Decisions
* **Object-Oriented Data Models:** Utilizes inheritance and polymorphism to cleanly separate different transaction types (e.g., `BankTransaction` vs. `EcommerceOrder`), tracking specific attributes like shipping addresses or prior card declines without cluttering the base class.
* **Scalable Rule Engine:** Built on the Open/Closed Principle using a `FraudRule` interface. The core `FraudEngine` iterates through registered rules blindly, allowing new fraud detection parameters to be added seamlessly without modifying existing engine logic.
* **Dynamic Risk Scoring:** Contextually evaluates risk rather than isolating it. The `RiskScorer` applies a mathematical "co-occurrence bonus" when multiple rules trigger on a single transaction, safely capping the maximum overall risk score at 9 (CRITICAL).
* **Generative AI Integration:** Uses the OpenRouter API (Llama 3.1) to act as a conversational assistant. The engine injects specific account histories into dynamic hidden prompts, ensuring the LLM provides concise (strictly under 30 words) and highly relevant explanations. Includes an interactive chat loop for analyst follow-ups.
* **Immutable Auditing:** Automatically logs analyst decisions (Reviewed/Dismissed) and notes to an isolated JSON-Lines file (`sentinelgate_audit.jsonl`).
* **Test-Driven Architecture:** Uses manual test doubles and the "Extract and Override" pattern to intercept HTTP network calls. This ensures the comprehensive JUnit 5 test suite runs flawlessly in isolated environments without requiring a live internet connection.

## Setup Instructions
1. Ensure you have **Java** (JDK 25 recommended) and **Maven** installed.
2. Open the project in your preferred IDE (e.g., IntelliJ IDEA).
3. **CRITICAL STEP:** You must configure an environment variable for the AI to work.
    * In IntelliJ, go to **Edit Configurations** for your `Main` run profile.
    * Add a new Environment Variable named `OPENROUTER_API_KEY`.
    * Paste your valid OpenRouter API key as the value.

## How to Run

### 1. Live Interactive Mode
Run the `Main.java` class without any arguments.
* The system will load `transactions.csv`, run the rule engine, rank the flags by risk score, and initiate the interactive Command Line Interface.
* You can mark transactions as `[R]`eviewed, `[D]`ismissed, `[S]`kip, or type a custom question to consult the AI assistant.

### 2. Audit Replay & Stats Mode
Run the `Main.java` class with the `--stats` program argument.
* **IntelliJ:** Edit Configurations -> Add `--stats` to the "Program arguments" box.
* This mode bypasses the standard engine and launches the `AuditReplayer`. It aggregates historical session data from `sentinelgate_audit.jsonl`, providing a high-level statistical breakdown and surfacing any previously "Dismissed" flags for management re-examination.