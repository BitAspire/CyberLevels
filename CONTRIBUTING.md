# Contributing to CyberLevels

Thank you for your interest in contributing to **CyberLevels**! We welcome community contributions, whether you are fixing a bug, adding a new feature, improving documentation, or optimizing code.

By contributing to this repository, you help make CyberLevels better for thousands of Minecraft server owners.

---

## 📜 Licensing & CLA

By submitting a Pull Request (PR) or code contribution to this repository:
1. You agree that your contributions will be licensed under the project's [BitAspire Community & Public License (BCPL) v1.0](LICENSE.md).
2. You confirm that all submitted code is your own work or properly attributed under compatible open-source/source-available licenses.
3. You grant **BitAspire** / **ZeroToil LLC** the perpetual right to include, modify, and distribute your contribution as part of the CyberLevels plugin.

---

## 🛠️ Getting Started

### Prerequisites

To build and test CyberLevels locally, you will need:
- **JDK 8 or higher** (JDK 17 or JDK 21 recommended for building modern target bytecode).
- **Gradle** (The project includes the Gradle wrapper `./gradlew`).
- A local **Spigot**, **Paper**, or **Folia** test server.

### Repository Setup

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   
```bash
  
   git clone [https://github.com/YOUR_USERNAME/CyberLevels.git](https://github.com/YOUR_USERNAME/CyberLevels.git)
   cd CyberLevels
   

```

3. **Build** the plugin using Gradle:

```bash
  
   ./gradlew build
   

```

The output compiled `.jar` file will be located in `build/libs/`.

---

## 🔄 How to Contribute

### 1. Reporting Bugs & Support

* **Discord:** For quick support, general questions, or minor bug reports, please join our [BitAspire Discord](https://discord.gg/DC4Gqj3y5V).
* **GitHub Issues:** If you encounter a reproducible bug, check existing issues before creating a new one. Include:
* Plugin version (`/clv about`).
* Server software & Minecraft version (e.g., Paper 1.20.4).
* Error logs / stack traces (use [Pastebin](https://pastebin.com/) or similar).
* Steps to reproduce the issue.

### 2. Suggesting Features

* Discuss proposed features on Discord or via GitHub Issues before starting significant work. This ensures your idea fits the plugin's roadmap and avoids duplicated effort.

### 3. Submitting Pull Requests (PRs)

Follow these steps when preparing a Pull Request:

1. **Create a new branch** from `main` (or the active development branch):

```bash
  
   git checkout -b feature/my-cool-feature
   # or
   git checkout -b fix/issue-description
   

```

2. **Follow Coding Standards:**
* Follow standard Java naming conventions and style guidelines.
* Keep code clean, readable, and well-commented where necessary.
* Maintain compatibility across supported Minecraft versions and platforms (Spigot, Paper, Folia).
* Ensure soft-dependency hooks (e.g., PlaceholderAPI, Vault) fail gracefully if the target plugin is absent.


3. **Test Your Changes:**
* Test your build thoroughly on a test server before opening a PR.
* Ensure existing features, commands, and events are not broken.


4. **Commit & Push:**
* Write clear, descriptive commit messages.
* Push your branch to your fork:

```bash
  
     git push origin feature/my-cool-feature
     

```

5. **Open the Pull Request:**
* Select `main` as the base repository target.
* Provide a clear title and description of what your PR changes or fixes.
* Link any relevant issues solved by the PR (e.g., `Fixes #12`).

---

## 💬 Code Style Guidelines

* **Formatting:** Standard 4-space indentation for Java files.
* **Async Execution:** Mind Folia and Paper async requirements. Avoid blocking the main server thread with I/O or database queries.
* **Backward Compatibility:** Keep legacy API methods intact (use `@Deprecated` annotation if replacing older methods/events like `XPChangeEvent`).

---

## 💙 Recognition

All accepted contributors will be credited in release notes and contributor lists where applicable. Thank you for helping build CyberLevels!
